package bookbotsearch;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Book languages to filter on, with the numeric ids bookbot uses in its {@code /p/language/<id>} URLs. */
public enum Language {
    ALL("Alle talen", ""),
    DUTCH("Nederlands", "21", "nederlands", "dutch", "niederlandisch", "nizozemstina", "nl", "nld", "dut"),
    ENGLISH("Engels", "3", "engels", "english", "englisch", "anglictina", "en", "eng"),
    GERMAN("Duits", "4", "duits", "german", "deutsch", "nemcina", "de", "deu", "ger"),
    FRENCH("Frans", "5", "frans", "french", "franzosisch", "francais", "francouzstina", "fr", "fra", "fre"),
    SPANISH("Spaans", "7", "spaans", "spanish", "spanisch", "espanol", "spanelstina", "es", "spa"),
    ITALIAN("Italiaans", "8", "italiaans", "italian", "italienisch", "italiano", "italstina", "it", "ita"),
    CZECH("Tsjechisch", "1", "tsjechisch", "czech", "tschechisch", "cestina", "cs", "ces", "cze");

    private final String label;
    private final String id;
    private final Set<String> names;

    Language(String label, String id, String... names) {
        this.label = label;
        this.id = id;
        this.names = Set.of(names);
    }

    public String id() {
        return id;
    }

    /**
     * Whether a book is in this language. Uses bookbot's language id when the book page linked to one,
     * otherwise the language name (in Dutch, English, German, Czech or as an ISO code).
     */
    public boolean matches(Book book) {
        if (this == ALL) {
            return true;
        }
        if (!book.languageId().isEmpty()) {
            return book.languageId().equals(id);
        }
        String value = normalize(book.language());
        for (String word : value.split("[^a-z]+")) {
            if (names.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Lower case without accents, so "Němčina" and "nemcina" compare equal. */
    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return label;
    }
}
