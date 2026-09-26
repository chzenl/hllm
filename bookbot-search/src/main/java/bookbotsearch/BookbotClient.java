package bookbotsearch;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Fetches search result pages from bookbot.nl. */
public final class BookbotClient {

    /**
     * Default search URL. {@code {query}} is replaced by the URL-encoded search terms and
     * {@code {page}} by the 1-based page number. Override with {@code -Dbookbot.searchUrl=...}
     * or from the settings dialog if bookbot changes its URL scheme.
     */
    public static final String DEFAULT_SEARCH_URL = "https://bookbot.nl/zoeken?q={query}&page={page}";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36 BookbotSearch/1.0";

    private volatile String searchUrlTemplate;

    public BookbotClient() {
        this(System.getProperty("bookbot.searchUrl", DEFAULT_SEARCH_URL));
    }

    public BookbotClient(String searchUrlTemplate) {
        setSearchUrlTemplate(searchUrlTemplate);
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

    public String searchUrl(String query, int page) {
        String encoded = URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
        return searchUrlTemplate.replace("{query}", encoded).replace("{page}", Integer.toString(page));
    }

    public List<Book> search(String query, int page) throws IOException {
        Connection.Response response = Jsoup.connect(searchUrl(query, page))
                .userAgent(USER_AGENT)
                .header("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.8")
                .timeout(20_000)
                .followRedirects(true)
                .ignoreContentType(true)
                .execute();
        Document doc = response.parse();
        return SearchResultParser.parse(doc);
    }
}
