package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Runs a search against a local fake bookbot to check language/title filtering and paging. */
class BookbotClientTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/zoeken", ex -> {
            String q = ex.getRequestURI().getQuery();
            if (q.contains("page=1")) {
                reply(ex, card(1, "Chemie für Anfänger") + card(2, "Chemistry basics") + card(3, "Organische Chemie"));
            } else if (q.contains("page=2")) {
                reply(ex, card(4, "Chemie heute") + card(5, "Kochbuch"));
            } else {
                reply(ex, "<p>Geen resultaten</p>");
            }
        });
        server.createContext("/g/", ex -> {
            String id = ex.getRequestURI().getPath().substring(3);
            String lang = switch (id) {
                case "1", "4", "5" -> "Duits";
                case "2" -> "Engels";
                default -> "Nederlands";
            };
            reply(ex, "<dl><dt>Jaar van uitgave</dt><dd>199" + id + "</dd><dt>Taal</dt><dd>" + lang + "</dd></dl>");
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
    void filtersByLanguageAndTitleAcrossPages() throws Exception {
        BookbotClient client = new BookbotClient(base + "/zoeken?q={query}&page={page}", base + "/p/{language}");
        List<Book> streamed = new ArrayList<>();
        BookbotClient.Batch batch = client.searchBatch("chemie", Language.GERMAN, true, 1, streamed::add);

        // 1 and 4 are German with "chemie" in the title; 2 is English, 3 is Dutch, 5 has no "chemie".
        assertEquals(List.of("Chemie für Anfänger", "Chemie heute"), batch.books().stream().map(Book::title).toList());
        assertEquals(batch.books(), streamed);
        assertEquals("1991", batch.books().get(0).year());
        assertEquals("Duits", batch.books().get(0).language());
        assertEquals(2, batch.pagesScanned());
        assertFalse(batch.more());
    }
}
