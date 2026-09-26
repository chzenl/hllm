package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
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
    void readsJsonLd() {
        BookDetails d = parse("""
                <script type="application/ld+json">{"@type":"Book","datePublished":"1975-01-01","inLanguage":"de"}</script>
                """);
        assertEquals("1975", d.year());
        assertEquals("de", d.language());
    }

    @Test
    void matchesLanguages() {
        Book german = new Book("1", "t", "", "", "", "").withDetails(new BookDetails("", "Duits", "", "", ""));
        Book byId = new Book("2", "t", "", "", "", "").withDetails(new BookDetails("", "?", "4", "", ""));
        Book czech = new Book("3", "t", "", "", "", "").withDetails(new BookDetails("", "Němčina", "", "", ""));
        Book iso = new Book("4", "t", "", "", "", "").withDetails(new BookDetails("", "de", "", "", ""));
        Book english = new Book("5", "t", "", "", "", "").withDetails(new BookDetails("", "English", "", "", ""));
        assertTrue(Language.GERMAN.matches(german));
        assertTrue(Language.GERMAN.matches(byId));
        assertTrue(Language.GERMAN.matches(czech));
        assertTrue(Language.GERMAN.matches(iso));
        assertFalse(Language.GERMAN.matches(english));
        assertTrue(Language.ALL.matches(english));
    }

    @Test
    void matchesTitles() {
        assertTrue(BookbotClient.titleMatches("Allgemeine Chemie für Studenten", "chemie"));
        assertTrue(BookbotClient.titleMatches("Élémentaire chimie", "elementaire CHIMIE"));
        assertFalse(BookbotClient.titleMatches("Physik", "chemie"));
    }
}
