package bookbotsearch;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     * Default search URL. {@code {query}} is replaced by the URL-encoded search terms and
     * {@code {page}} by the 1-based page number. Override with {@code -Dbookbot.searchUrl=...}
     * or from the settings dialog if bookbot changes its URL scheme.
     */
    public static final String DEFAULT_SEARCH_URL = "https://bookbot.nl/zoeken?q={query}&page={page}";

    /**
     * URL for browsing all books in one language, used when a language is chosen and the search field
     * is empty. {@code {language}} is bookbot's numeric language id (see {@link Language}). Bookbot's
     * language pages ignore search terms, so searches with terms always use the search URL and are
     * filtered by language afterwards. Override with {@code -Dbookbot.languageBrowseUrl=...}.
     */
    public static final String DEFAULT_LANGUAGE_BROWSE_URL = "https://bookbot.nl/p/language/{language}?page={page}";

    /** A batch stops once it has this many matching books... */
    static final int TARGET_RESULTS = 20;
    /** ...or after this many search result pages, so a rare language cannot trigger endless requests. */
    static final int MAX_PAGES_PER_BATCH = 5;
    private static final int DETAIL_THREADS = 6;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36 BookbotSearch/1.0";

    /** Result of {@link #searchBatch}. */
    public record Batch(List<Book> books, int nextPage, boolean more, int pagesScanned, int unknownLanguage) {
    }

    private final Map<String, BookDetails> detailsCache = new ConcurrentHashMap<>();
    private final ExecutorService detailPool = Executors.newFixedThreadPool(DETAIL_THREADS, r -> {
        Thread t = new Thread(r, "bookbot-details");
        t.setDaemon(true);
        return t;
    });

    private volatile String searchUrlTemplate;
    private volatile String languageBrowseUrlTemplate;

    public BookbotClient() {
        this(System.getProperty("bookbot.searchUrl", DEFAULT_SEARCH_URL),
                System.getProperty("bookbot.languageBrowseUrl", DEFAULT_LANGUAGE_BROWSE_URL));
    }

    public BookbotClient(String searchUrlTemplate, String languageBrowseUrlTemplate) {
        setSearchUrlTemplate(searchUrlTemplate);
        setLanguageBrowseUrlTemplate(languageBrowseUrlTemplate);
    }

    public String getSearchUrlTemplate() {
        return searchUrlTemplate;
    }

    public void setSearchUrlTemplate(String template) {
        if (template == null || !template.contains("{query}")) {
            throw new IllegalArgumentException("Search URL must contain {query}");
        }
        this.searchUrlTemplate = template.strip();
    }

    public String getLanguageBrowseUrlTemplate() {
        return languageBrowseUrlTemplate;
    }

    public void setLanguageBrowseUrlTemplate(String template) {
        if (template == null || !template.contains("{language}")) {
            throw new IllegalArgumentException("Language URL must contain {language}");
        }
        this.languageBrowseUrlTemplate = template.strip();
    }

    /** The page that is fetched: the search page, or the language page when there are no search terms. */
    public String searchUrl(String query, int page, Language language) {
        String q = query == null ? "" : query.strip();
        boolean browse = q.isEmpty() && language != null && language != Language.ALL;
        String template = browse ? languageBrowseUrlTemplate : searchUrlTemplate;
        return template.replace("{query}", URLEncoder.encode(q, StandardCharsets.UTF_8))
                .replace("{page}", Integer.toString(page))
                .replace("{language}", browse ? language.id() : "");
    }

    /** One page of results as bookbot returns them, without details or filtering. */
    public List<Book> search(String query, int page, Language language) throws IOException {
        return SearchResultParser.parse(fetch(searchUrl(query, page, language)));
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
     * passes each book that matches to {@code onBook} in bookbot's order. Keeps reading result pages
     * until {@value #TARGET_RESULTS} books matched or {@value #MAX_PAGES_PER_BATCH} pages were read.
     *
     * @param titleOnly only keep books whose title contains every search word
     */
    public Batch searchBatch(String query, Language language, boolean titleOnly, int startPage,
            Consumer<Book> onBook) throws IOException, InterruptedException {
        List<Book> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int page = startPage;
        int pages = 0;
        int unknown = 0;
        boolean more = true;

        while (found.size() < TARGET_RESULTS && pages < MAX_PAGES_PER_BATCH) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            List<Book> raw = search(query, page, language);
            List<Book> fresh = new ArrayList<>();
            for (Book b : raw) {
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
                }
            }
            try {
                for (Future<Book> f : withDetails) {
                    Book b = f.get();
                    if (language.matches(b)) {
                        found.add(b);
                        onBook.accept(b);
                    } else if (b.language().isEmpty() && b.languageId().isEmpty()) {
                        unknown++;
                    }
                }
            } catch (InterruptedException e) {
                withDetails.forEach(f -> f.cancel(true));
                throw e;
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            }
        }
        return new Batch(found, page, more, pages, unknown);
    }

    /** True when every word of the query occurs in the title, ignoring case and accents. */
    static boolean titleMatches(String title, String query) {
        String t = Language.normalize(title);
        for (String word : Language.normalize(query).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && !t.contains(word)) {
                return false;
            }
        }
        return true;
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
