package bookbotsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Search results from a (trimmed) real bookbot.nl search page. */
class SearchPageTest {

    private static Document page() throws Exception {
        try (InputStream in = SearchPageTest.class.getResourceAsStream("/search-chemie-4_21.html")) {
            return Jsoup.parse(in, "UTF-8", "https://bookbot.nl/p/q/chemie/language/4_21");
        }
    }

    @Test
    void readsPageData() throws Exception {
        Document doc = page();
        List<Book> books = SearchResultParser.parse(doc);
        assertEquals(3, books.size());

        Book soldOut = books.get(0);
        assertEquals("9012420", soldOut.id());
        assertEquals("Chemie", soldOut.title());
        assertEquals("Gernot Klemmer", soldOut.author());
        assertEquals("€ 1,99", soldOut.price());
        assertEquals(Book.SOLD_OUT, soldOut.availability());
        assertTrue(soldOut.isSoldOut());
        assertEquals("https://bookbot.nl/g/9012420/b/19903682", soldOut.url());
        assertEquals("https://rezised-images.knhbt.cz/300x300/73541532.jpg", soldOut.imageUrl());
        assertEquals("Duits", soldOut.language());
        assertEquals("Diesterweg Sauerländer", soldOut.publisher());
        assertEquals("9783425036656", soldOut.isbn());

        Book tafelwerk = books.get(1);
        assertEquals("Das große Tafelwerk", tafelwerk.title());
        assertEquals("€ 1,39", tafelwerk.price());
        assertEquals(Book.IN_STOCK, tafelwerk.availability());

        assertEquals(107, NextDataParser.lastPage(doc));
    }

    @Test
    void fallsBackToHtmlCards() throws Exception {
        Document doc = page();
        doc.getElementById("__NEXT_DATA__").remove();
        List<Book> books = SearchResultParser.parse(doc);
        assertEquals(List.of("Chemie", "Das große Tafelwerk",
                "Lehrbuch der analytischen und präparativen anorganischen Chemie"),
                books.stream().map(Book::title).toList());
        assertEquals(List.of("Gernot Klemmer", "Willi Wörstenfeld", "Gerhart Jander"),
                books.stream().map(Book::author).toList());
        assertEquals("", books.get(0).price());
        assertEquals("€ 1,39", books.get(1).price());
        assertEquals("€ 3,59", books.get(2).price());
        assertEquals(Book.SOLD_OUT, books.get(0).availability());
        assertEquals(Book.IN_STOCK, books.get(2).availability());
        assertEquals("https://rezised-images.knhbt.cz/300x300/84196187.jpg", books.get(2).imageUrl());
    }
}
