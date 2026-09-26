package bookbotsearch;

/** Book languages bookbot can filter on, with the numeric ids used in its {@code /p/language/<id>} URLs. */
public enum Language {
    ALL("Alle talen", ""),
    DUTCH("Nederlands", "21"),
    ENGLISH("Engels", "3"),
    GERMAN("Duits", "4"),
    FRENCH("Frans", "5"),
    SPANISH("Spaans", "7"),
    ITALIAN("Italiaans", "8"),
    CZECH("Tsjechisch", "1");

    private final String label;
    private final String id;

    Language(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return label;
    }
}
