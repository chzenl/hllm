package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class BookDetailsParserTest {

    private static BookDetails parse(String body) {
        return BookDetailsParser.parse(Jsoup.parse("<html><body>" + body + "</body></html>", "https://bookbot.nl/g/1"));
    }

    @Test
    void readsDefinitionList() {
        BookDetails d = parse("""
                <nav><a href="/p/language/21">Nederlands</a><a href="/p/language/4">Duits</a></nav>
                <dl><dt>Jaar van uitgave</dt><dd>1998</dd>
                    <dt>Taal</dt><dd><a href="/p/language/4">Duits</a></dd></dl>
                """);
        assertEquals("1998", d.year());
        assertEquals("Duits", d.language());
        assertEquals("4", d.languageId());
    }

    @Test
    void readsTableRows() {
        BookDetails d = parse("<table><tr><th>Jaar</th><td>2004</td></tr><tr><th>Taal:</th><td>Engels</td></tr></table>");
        assertEquals("2004", d.year());
        assertEquals("Engels", d.language());
    }

    @Test
    void readsLabelInsideParent() {
        BookDetails d = parse("<div><span>Uitgavejaar:</span> 1987</div><div><b>Taal:</b> Frans</div>");
        assertEquals("1987", d.year());
        assertEquals("Frans", d.language());
    }

    @Test
    void readsInlineText() {
        BookDetails d = parse("<p>Paperback, Jaar van uitgave: 2011, Taal: Nederlands, 320 pagina's</p>");
        assertEquals("2011", d.year());
        assertEquals("Nederlands", d.language());
    }

    @Test
    void readsRealBookbotPage() throws Exception {
        Document doc;
        try (InputStream in = getClass().getResourceAsStream("/product-9044579.html")) {
            doc = Jsoup.parse(in, "UTF-8", "https://bookbot.nl/g/9044579/b/22518515");
        }
        BookDetails d = BookDetailsParser.parse(doc);
        assertEquals("1955", d.year());
        assertEquals("Duits", d.language());
        assertEquals("4", d.languageId());
        assertEquals("€ 3,99", d.price());
        assertEquals("https://rezised-images.knhbt.cz/300x300/83015870.webp", d.imageUrl());

        // Searching an ISBN can land directly on a book page; that page is then the only result.
        List<Book> books = SearchResultParser.parse(doc);
        assertEquals(1, books.size());
        Book b = books.get(0);
        assertEquals("9044579", b.id());
        assertEquals("Verfahren der Chemie-Industrie 1", b.title());
        assertEquals("Fritz Tegeder", b.author());
        assertEquals("1955", b.year());
        assertEquals("https://bookbot.nl/g/9044579/b/22518515", b.url());
    }

    @Test
    void readsBookbotFields() {
        // Field names as on bookbot.nl book pages.
        BookDetails d = parse("""
                <div><span>Taal</span><span>Engels, Nederlands</span></div>
                <div><span>Uitgever</span><span>Marco Polo</span></div>
                <div><span>Jaar van publicatie</span><span>2016</span></div>
                <div><span>ISBN13</span><span>9783829738644</span></div>
                """);
        assertEquals("2016", d.year());
        assertEquals("Engels, Nederlands", d.language());
    }

    @Test
    void readsBookbotFieldsAsText() {
        BookDetails d = parse("<p>Taal: Engels, Nederlands Uitgever: Marco Polo Jaar van publicatie: 2016</p>");
        assertEquals("2016", d.year());
        assertEquals("Engels, Nederlands", d.language());
    }

    @Test
    void readsJsonLd() {
        BookDetails d = parse("""
                <script type="application/ld+json">{"@type":"Book","datePublished":"1975-01-01","inLanguage":"de"}</script>
                """);
        assertEquals("1975", d.year());
        assertEquals("de", d.language());
    }

    @Test
    void matchesTitles() {
        assertTrue(BookbotClient.titleMatches("Allgemeine Chemie für Studenten", "chemie"));
        assertTrue(BookbotClient.titleMatches("Élémentaire chimie", "elementaire CHIMIE"));
        assertFalse(BookbotClient.titleMatches("Physik", "chemie"));
    }
}
