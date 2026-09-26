package bookbotsearch;

/**
 * A single book found on bookbot.nl. Empty strings are used for unknown fields.
 *
 * <p>{@code year} usually comes from the book's own page (see {@link BookDetails}); {@code languageId}
 * holds bookbot's numeric language id(s), joined by "_" when there are several.
 */
public record Book(String id, String title, String author, String price, String url, String imageUrl,
        String year, String language, String languageId, String availability, String publisher, String isbn) {

    public static final String IN_STOCK = "Op voorraad";
    public static final String SOLD_OUT = "Uitverkocht";

    public Book {
        id = nz(id);
        title = nz(title);
        author = nz(author);
        price = nz(price);
        url = nz(url);
        imageUrl = nz(imageUrl);
        year = nz(year);
        language = nz(language);
        languageId = nz(languageId);
        availability = nz(availability);
        publisher = nz(publisher);
        isbn = nz(isbn);
    }

    public Book(String id, String title, String author, String price, String url, String imageUrl) {
        this(id, title, author, price, url, imageUrl, "", "", "", "", "", "");
    }

    public boolean isSoldOut() {
        return SOLD_OUT.equals(availability);
    }

    /** Returns a copy with the details from the book's own page filled in where this book lacks them. */
    public Book withDetails(BookDetails d) {
        return new Book(id, title, author,
                price.isEmpty() ? d.price() : price, url,
                imageUrl.isEmpty() ? d.imageUrl() : imageUrl,
                year.isEmpty() ? d.year() : year,
                language.isEmpty() ? d.language() : language,
                languageId.isEmpty() ? d.languageId() : languageId,
                availability, publisher, isbn);
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
