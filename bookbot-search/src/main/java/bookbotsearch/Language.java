package bookbotsearch;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/** Book languages bookbot can filter on, with the numeric ids it uses in URLs such as {@code /language/1_3_4}. */
public enum Language {
    DUTCH("Nederlands", 21),
    ENGLISH("Engels", 3),
    GERMAN("Duits", 4),
    FRENCH("Frans", 5),
    SPANISH("Spaans", 7),
    ITALIAN("Italiaans", 8),
    CZECH("Tsjechisch", 1);

    private final String label;
    private final int id;

    Language(String label, int id) {
        this.label = label;
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** The language with bookbot id {@code id}, or null if it is not one of these. */
    public static Language byId(String id) {
        for (Language l : values()) {
            if (Integer.toString(l.id).equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** Bookbot's notation for several languages: ids in ascending order joined by "_", e.g. "1_3_4". */
    public static String ids(Set<Language> languages) {
        return languages.stream()
                .sorted(Comparator.comparingInt(Language::id))
                .map(l -> Integer.toString(l.id))
                .collect(Collectors.joining("_"));
    }

    @Override
    public String toString() {
        return label;
    }
}
