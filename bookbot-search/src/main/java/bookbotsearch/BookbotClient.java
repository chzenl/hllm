package bookbotsearch;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Fetches search results and book pages from bookbot.nl. */
public final class BookbotClient {

    /**
     * Default search URL, as used by bookbot.nl itself (e.g. {@code https://bookbot.nl/p/q/chemie}).
     * {@code {query}} is replaced by the search terms and {@code {page}} by the 1-based page number.
     * Override with {@code -Dbookbot.searchUrl=...} or from the settings dialog.
     */
    public static final String DEFAULT_SEARCH_URL = "https://bookbot.nl/p/q/{query}?page={page}";

    /**
     * Search URL with a language filter (e.g. {@code https://bookbot.nl/p/q/chemie/language/1_3_4}).
     * {@code {languages}} is replaced by bookbot's language ids joined by "_" (see {@link Language#ids}).
     * Override with {@code -Dbookbot.languageSearchUrl=...}.
     */
    public static final String DEFAULT_LANGUAGE_SEARCH_URL =
            "https://bookbot.nl/p/q/{query}/language/{languages}?page={page}";

    /**
     * URL for browsing all books in the chosen languages, used when the search field is empty.
     * Override with {@code -Dbookbot.languageBrowseUrl=...}.
     */
    public static final String DEFAULT_LANGUAGE_BROWSE_URL = "https://bookbot.nl/p/language/{languages}?page={page}";

    /** A batch stops once it has this many matching books... */
    static final int TARGET_RESULTS = 20;
    /** ...or after this many search result pages, so a rare language cannot trigger endless requests. */
    static final int MAX_PAGES_PER_BATCH = 5;
    private static final int DETAIL_THREADS = 6;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36 BookbotSearch/1.0";

    /** Result of {@link #searchBatch}. {@code titleSkipped} counts books left out by the title filter. */
    public record Batch(List<Book> books, int nextPage, boolean more, int pagesScanned, int titleSkipped) {
    }

    private final Map<String, BookDetails> detailsCache = new ConcurrentHashMap<>();
    private final ExecutorService detailPool = Executors.newFixedThreadPool(DETAIL_THREADS, r -> {
        Thread t = new Thread(r, "bookbot-details");
        t.setDaemon(true);
        return t;
    });

    private volatile String searchUrlTemplate;
    private volatile String languageSearchUrlTemplate;
    private volatile String languageBrowseUrlTemplate;

    public BookbotClient() {
        this(System.getProperty("bookbot.searchUrl", DEFAULT_SEARCH_URL),
                System.getProperty("bookbot.languageSearchUrl", DEFAULT_LANGUAGE_SEARCH_URL),
                System.getProperty("bookbot.languageBrowseUrl", DEFAULT_LANGUAGE_BROWSE_URL));
    }

    public BookbotClient(String searchUrl, String languageSearchUrl, String languageBrowseUrl) {
        setUrlTemplates(searchUrl, languageSearchUrl, languageBrowseUrl);
    }

    public String getSearchUrlTemplate() {
        return searchUrlTemplate;
    }

    public String getLanguageSearchUrlTemplate() {
        return languageSearchUrlTemplate;
    }

    public String getLanguageBrowseUrlTemplate() {
        return languageBrowseUrlTemplate;
    }

    /** Sets all three URL templates, or none of them if one is invalid. */
    public synchronized void setUrlTemplates(String searchUrl, String languageSearchUrl, String languageBrowseUrl) {
        require(searchUrl, "{query}");
        require(languageSearchUrl, "{query}", "{languages}");
        require(languageBrowseUrl, "{languages}");
        this.searchUrlTemplate = searchUrl.strip();
        this.languageSearchUrlTemplate = languageSearchUrl.strip();
        this.languageBrowseUrlTemplate = languageBrowseUrl.strip();
    }

    private static void require(String template, String... placeholders) {
        for (String p : placeholders) {
            if (template == null || !template.contains(p)) {
                throw new IllegalArgumentException("URL moet " + p + " bevatten: " + template);
            }
        }
    }

    /**
     * The page to fetch. Without languages this is the plain search; with languages the filtered
     * search, or the language page when there are no search terms.
     */
    public String searchUrl(String query, int page, Set<Language> languages) {
        String q = query == null ? "" : query.strip();
        boolean filtered = languages != null && !languages.isEmpty();
        String template = !filtered ? searchUrlTemplate
                : q.isEmpty() ? languageBrowseUrlTemplate : languageSearchUrlTemplate;
        // The query is a path segment on bookbot, so spaces become %20 rather than +.
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8).replace("+", "%20");
        String url = template.replace("{query}", encoded)
                .replace("{page}", Integer.toString(page))
                .replace("{languages}", filtered ? Language.ids(languages) : "");
        // Keep page 1 on the same address bookbot itself uses.
        return page == 1 ? url.replaceFirst("[?&]page=1$", "") : url;
    }

    /** One page of results as bookbot returns them, without details or filtering. */
    public List<Book> search(String query, int page, Set<Language> languages) throws IOException {
        return SearchResultParser.parse(fetch(searchUrl(query, page, languages)));
    }

    /** Year, language etc. from the book's own page. Cached; failures give {@link BookDetails#EMPTY}. */
    public BookDetails details(Book book) {
        BookDetails cached = detailsCache.get(book.id());
        if (cached != null) {
            return cached;
        }
        try {
            BookDetails d = BookDetailsParser.parse(fetch(book.url()));
            detailsCache.put(book.id(), d);
            return d;
        } catch (IOException e) {
            return BookDetails.EMPTY;
        }
    }

    /**
     * Searches from {@code startPage} on, fetches every book's page for its year and language, and
     * passes each book to {@code onBook} in bookbot's order. With {@code titleOnly}, books whose title
     * lacks a search word are skipped and more result pages are read, until {@value #TARGET_RESULTS}
     * books were found or {@value #MAX_PAGES_PER_BATCH} pages were read.
     */
    public Batch searchBatch(String query, Set<Language> languages, boolean titleOnly, int startPage,
            Consumer<Book> onBook) throws IOException, InterruptedException {
        List<Book> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int page = startPage;
        int pages = 0;
        int skipped = 0;
        boolean more = true;

        while (found.size() < TARGET_RESULTS && pages < MAX_PAGES_PER_BATCH) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            List<Book> fresh = new ArrayList<>();
            for (Book b : search(query, page, languages)) {
                if (seen.add(b.id())) {
                    fresh.add(b);
                }
            }
            if (fresh.isEmpty()) {
                // No results, or the page parameter is ignored and we got the same books again.
                more = false;
                break;
            }
            page++;
            pages++;

            List<Future<Book>> withDetails = new ArrayList<>();
            for (Book b : fresh) {
                if (!titleOnly || titleMatches(b.title(), query)) {
                    withDetails.add(detailPool.submit(() -> b.withDetails(details(b))));
                } else {
                    skipped++;
                }
            }
            try {
                for (Future<Book> f : withDetails) {
                    Book b = f.get();
                    found.add(b);
                    onBook.accept(b);
                }
            } catch (InterruptedException e) {
                withDetails.forEach(f -> f.cancel(true));
                throw e;
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            }
        }
        return new Batch(found, page, more, pages, skipped);
    }

    /** True when every word of the query occurs in the title, ignoring case and accents. */
    static boolean titleMatches(String title, String query) {
        String t = normalize(title);
        for (String word : normalize(query).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !t.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Lower case without accents, so "Élémentaire" and "elementaire" compare equal. */
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Document fetch(String url) throws IOException {
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.8")
                .timeout(20_000)
                .followRedirects(true)
                .ignoreContentType(true)
                .execute();
        return response.parse();
    }
}
