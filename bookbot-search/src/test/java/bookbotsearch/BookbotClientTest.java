package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Runs a search against a local fake bookbot to check URLs, the title filter, details and paging. */
class BookbotClientTest {

    private HttpServer server;
    private String base;
    private final List<String> requested = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/p/q/", ex -> {
            requested.add(ex.getRequestURI().toString());
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/1_3_4")) {
                reply(ex, card(1, "Chemie für Anfänger") + card(2, "Chemistry basics") + card(3, "Organische Chemie"));
            } else if (path.endsWith("/page/2")) {
                reply(ex, card(4, "Chemie heute") + card(5, "Kochbuch"));
            } else {
                reply(ex, "<p>Geen resultaten</p>");
            }
        });
        server.createContext("/g/", ex -> {
            String id = ex.getRequestURI().getPath().substring(3);
            reply(ex, "<dl><dt>Jaar van uitgave</dt><dd>199" + id + "</dd><dt>Taal</dt><dd>Duits</dd></dl>");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String card(int id, String title) {
        return "<div class='card'><a class='title' href='" + base + "/g/" + id + "'>" + title + "</a>"
                + "<span class='price'>€ 5,00</span></div>";
    }

    private static void reply(HttpExchange ex, String body) throws IOException {
        byte[] bytes = ("<html><body>" + body + "</body></html>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void searchesWithLanguagesAndFiltersTitles() throws Exception {
        BookbotClient client = new BookbotClient(base + "/p/q/{query}/page/{page}",
                base + "/p/q/{query}/language/{languages}/page/{page}", base + "/p/language/{languages}");
        List<Book> streamed = new ArrayList<>();
        BookbotClient.Batch batch = client.searchBatch("chemie",
                EnumSet.of(Language.GERMAN, Language.ENGLISH, Language.CZECH), true, 1, streamed::add);

        assertEquals(List.of("/p/q/chemie/language/1_3_4", "/p/q/chemie/language/1_3_4/page/2",
                "/p/q/chemie/language/1_3_4/page/3"), requested);
        // "Chemistry basics" and "Kochbuch" lack the word "chemie" in the title.
        assertEquals(List.of("Chemie für Anfänger", "Organische Chemie", "Chemie heute"),
                batch.books().stream().map(Book::title).toList());
        assertEquals(batch.books(), streamed);
        assertEquals("1991", batch.books().get(0).year());
        assertEquals("Duits", batch.books().get(0).language());
        assertEquals(2, batch.pagesScanned());
        assertEquals(2, batch.titleSkipped());
        assertFalse(batch.more());
    }
}
