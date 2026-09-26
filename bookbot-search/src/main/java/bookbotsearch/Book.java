package bookbotsearch;

/** A single book found on bookbot.nl. Empty strings are used for unknown fields. */
public record Book(String id, String title, String author, String price, String url, String imageUrl) {

    public Book {
        id = nz(id);
        title = nz(title);
        author = nz(author);
        price = nz(price);
        url = nz(url);
        imageUrl = nz(imageUrl);
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
