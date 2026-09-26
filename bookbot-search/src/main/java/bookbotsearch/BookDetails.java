package bookbotsearch;

/** Information read from a book's own page ({@code /g/<id>}). Empty strings are used for unknown fields. */
public record BookDetails(String year, String language, String languageId, String price, String imageUrl) {

    public static final BookDetails EMPTY = new BookDetails("", "", "", "", "");

    public BookDetails {
        year = year == null ? "" : year.strip();
        language = language == null ? "" : language.strip();
        languageId = languageId == null ? "" : languageId.strip();
        price = price == null ? "" : price.strip();
        imageUrl = imageUrl == null ? "" : imageUrl.strip();
    }
}
