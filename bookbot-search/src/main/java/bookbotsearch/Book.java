package bookbotsearch;

/**
 * A single book found on bookbot.nl. Empty strings are used for unknown fields.
 *
 * <p>{@code year} and {@code language} come from the book's own page (see {@link BookDetails});
 * {@code languageId} is bookbot's numeric language id when the page links to it.
 */
public record Book(String id, String title, String author, String price, String url, String imageUrl,
        String year, String language, String languageId) {

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
    }

    public Book(String id, String title, String author, String price, String url, String imageUrl) {
        this(id, title, author, price, url, imageUrl, "", "", "");
    }

    /** Returns a copy with the details from the book's own page filled in where they are known. */
    public Book withDetails(BookDetails d) {
        return new Book(id, title, author,
                price.isEmpty() ? d.price() : price, url,
                imageUrl.isEmpty() ? d.imageUrl() : imageUrl,
                d.year().isEmpty() ? year : d.year(),
                d.language().isEmpty() ? language : d.language(),
                d.languageId().isEmpty() ? languageId : d.languageId());
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
