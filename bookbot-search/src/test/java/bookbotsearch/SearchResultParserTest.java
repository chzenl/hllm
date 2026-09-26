package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class SearchResultParserTest {

    @Test
    void parsesProductCards() {
        String html = """
                <html><body>
                <header><a href="/g/999">Aanbevolen</a><a href="/winkelwagen">Winkelwagen</a></header>
                <div class="grid">
                  <div class="product-card">
                    <a href="/g/6072249"><img src="https://img.bookbot.nl/6072249.jpg" alt="Ave Mary"></a>
                    <a href="/g/6072249" class="product-title">Ave Mary</a>
                    <div class="product-author">Michela Murgia</div>
                    <span class="price">€ 4,50</span>
                    <a href="/g/6072249">In winkelwagen</a>
                  </div>
                  <div class="product-card">
                    <a href="/g/159570"><img data-src="/img/159570.jpg" alt=""></a>
                    <h3><a href="/g/159570">Pouze pro V.I.P.</a></h3>
                    <p>Leonie Fox</p>
                    <p>6,90 €</p>
                  </div>
                </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://bookbot.nl/zoeken?q=test");
        List<Book> books = SearchResultParser.parse(doc);

        assertEquals(3, books.size());
        Book first = books.get(1);
        assertEquals("6072249", first.id());
        assertEquals("Ave Mary", first.title());
        assertEquals("Michela Murgia", first.author());
        assertEquals("€ 4,50", first.price());
        assertEquals("https://bookbot.nl/g/6072249", first.url());
        assertEquals("https://img.bookbot.nl/6072249.jpg", first.imageUrl());

        Book second = books.get(2);
        assertEquals("Pouze pro V.I.P.", second.title());
        assertEquals("€ 6,90", second.price());
        assertEquals("https://bookbot.nl/img/159570.jpg", second.imageUrl());
    }

    @Test
    void parsesProductPageAfterRedirect() {
        String html = """
                <html><head>
                <title>Ave Mary - Michela Murgia - bookbot.nl</title>
                <meta property="og:image" content="https://img.bookbot.nl/x.jpg">
                </head><body><span class="price">€ 4,50</span></body></html>
                """;
        Document doc = Jsoup.parse(html, "https://bookbot.nl/g/6072249");
        List<Book> books = SearchResultParser.parse(doc);

        assertEquals(1, books.size());
        assertEquals("Ave Mary", books.get(0).title());
        assertEquals("Michela Murgia", books.get(0).author());
        assertEquals("€ 4,50", books.get(0).price());
        assertEquals("https://img.bookbot.nl/x.jpg", books.get(0).imageUrl());
    }

    @Test
    void ignoresForeignLinks() {
        Document doc = Jsoup.parse("<a href='https://example.com/g/1'>X</a>", "https://bookbot.nl/");
        assertEquals(0, SearchResultParser.parse(doc).size());
    }

    @Test
    void buildsSearchUrl() {
        BookbotClient client = new BookbotClient(BookbotClient.DEFAULT_SEARCH_URL,
                BookbotClient.DEFAULT_LANGUAGE_SEARCH_URL, BookbotClient.DEFAULT_LANGUAGE_BROWSE_URL);
        Set<Language> none = EnumSet.noneOf(Language.class);
        assertEquals("https://bookbot.nl/p/q/chemie", client.searchUrl("chemie", 1, none));
        assertEquals("https://bookbot.nl/p/q/harry%20potter/page/2", client.searchUrl(" harry potter ", 2, none));
        // The URL from bookbot.nl for Czech, English and German.
        assertEquals("https://bookbot.nl/p/q/chemie/language/1_3_4",
                client.searchUrl("chemie", 1, EnumSet.of(Language.GERMAN, Language.CZECH, Language.ENGLISH)));
        assertEquals("https://bookbot.nl/p/q/chemie/language/1_3_4_21/page/3",
                client.searchUrl("chemie", 3, EnumSet.of(Language.DUTCH, Language.GERMAN, Language.CZECH, Language.ENGLISH)));
        // The URL from bookbot.nl for page 2 in German and Dutch.
        assertEquals("https://bookbot.nl/p/q/chemie/language/4_21/page/2",
                client.searchUrl("chemie", 2, EnumSet.of(Language.DUTCH, Language.GERMAN)));
        // Without terms, chosen languages browse the language page.
        assertEquals("https://bookbot.nl/p/language/4", client.searchUrl("", 1, EnumSet.of(Language.GERMAN)));
    }
}
