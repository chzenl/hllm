package bookbotsearch;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Swing front end for searching second-hand books on bookbot.nl. */
public final class BookbotSearchApp extends JFrame {

    private final BookbotClient client = new BookbotClient();
    private final Map<String, ImageIcon> coverCache = new ConcurrentHashMap<>();

    private final JTextField queryField = new JTextField(30);
    private final JButton languageButton = new JButton();
    private final JPopupMenu languageMenu = new JPopupMenu();
    private final Map<Language, JCheckBoxMenuItem> languageItems = new EnumMap<>(Language.class);
    private final JCheckBox titleOnlyBox = new JCheckBox("Zoekterm in titel", true);
    private final JButton searchButton = new JButton("Zoeken");
    private final JButton prevButton = new JButton("◀ Vorige");
    private final JButton nextButton = new JButton("Volgende ▶");
    private final JLabel statusLabel = new JLabel("Voer een titel, auteur of ISBN in.");
    private final BookTableModel tableModel = new BookTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel coverLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel titleLabel = new JLabel();
    private final JLabel authorLabel = new JLabel();
    private final JLabel priceLabel = new JLabel();
    private final JLabel yearLabel = new JLabel();
    private final JLabel languageLabel = new JLabel();
    private final JButton openButton = new JButton("Openen op bookbot.nl");

    private String currentQuery = "";
    private Set<Language> currentLanguages = EnumSet.noneOf(Language.class);
    /** Remembers the chosen languages between runs. */
    private final Preferences prefs = Preferences.userNodeForPackage(BookbotSearchApp.class);
    private static final String PREF_LANGUAGES = "languages";
    private static final String DEFAULT_LANGUAGES = "DUTCH,ENGLISH,GERMAN,CZECH";
    private boolean currentTitleOnly;
    private boolean hasSearched;
    /** Bookbot result page each batch shown so far started at; the last entry is the batch on screen. */
    private final List<Integer> batchStarts = new ArrayList<>();
    private BookbotClient.Batch lastBatch;
    private SwingWorker<BookbotClient.Batch, Book> searchWorker;
    private SwingWorker<ImageIcon, Void> coverWorker;

    public BookbotSearchApp() {
        super("Bookbot Zoeker");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(buildContent());
        setMinimumSize(new Dimension(760, 480));
        setSize(1000, 640);
        setLocationRelativeTo(null);
        updateDetails(null);
        updateButtons(false);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Zoeken:"), BorderLayout.WEST);
        top.add(queryField, BorderLayout.CENTER);
        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton settingsButton = new JButton("Instellingen…");
        buildLanguageMenu();
        topButtons.add(languageButton);
        titleOnlyBox.setToolTipText("Alleen boeken tonen waarvan de titel alle zoekwoorden bevat");
        topButtons.add(titleOnlyBox);
        topButtons.add(searchButton);
        topButtons.add(settingsButton);
        top.add(topButtons, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        int[] widths = {360, 180, 55, 90, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetails(selectedBook());
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectedBook() != null) {
                    openInBrowser(selectedBook().url());
                }
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), buildDetailsPanel());
        split.setResizeWeight(0.7);
        root.add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.CENTER);
        JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton openSearchButton = new JButton("Zoekpagina in browser");
        paging.add(openSearchButton);
        paging.add(prevButton);
        paging.add(nextButton);
        bottom.add(paging, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> newSearch());
        queryField.addActionListener(e -> newSearch());
        titleOnlyBox.addActionListener(e -> {
            if (hasSearched) {
                newSearch();
            }
        });
        prevButton.addActionListener(e -> {
            if (batchStarts.size() > 1) {
                batchStarts.remove(batchStarts.size() - 1);
                int start = batchStarts.remove(batchStarts.size() - 1);
                startBatch(start);
            }
        });
        nextButton.addActionListener(e -> {
            if (lastBatch != null && lastBatch.more()) {
                startBatch(lastBatch.nextPage());
            }
        });
        openButton.addActionListener(e -> {
            Book b = selectedBook();
            if (b != null) {
                openInBrowser(b.url());
            }
        });
        openSearchButton.addActionListener(e -> {
            if (hasSearched && !batchStarts.isEmpty()) {
                openInBrowser(client.searchUrl(currentQuery, batchStarts.get(batchStarts.size() - 1), currentLanguages));
            } else if (canSearch(queryField.getText().strip(), selectedLanguages())) {
                openInBrowser(client.searchUrl(queryField.getText(), 1, selectedLanguages()));
            }
        });
        settingsButton.addActionListener(e -> showSettings());

        return root;
    }

    private JPanel buildDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        panel.setMinimumSize(new Dimension(220, 0));

        coverLabel.setPreferredSize(new Dimension(200, 280));
        coverLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        coverLabel.setAlignmentX(LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        priceLabel.setFont(priceLabel.getFont().deriveFont(Font.BOLD, 14f));
        for (JLabel l : new JLabel[] {titleLabel, authorLabel, priceLabel, yearLabel, languageLabel}) {
            l.setAlignmentX(LEFT_ALIGNMENT);
        }
        openButton.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(coverLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(authorLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(yearLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(languageLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(priceLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(openButton);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /** A popup with a checkbox per language; it stays open while ticking and searches again when closed. */
    private void buildLanguageMenu() {
        JMenuItem all = new JMenuItem("Alle talen");
        all.addActionListener(e -> languageItems.values().forEach(i -> i.setSelected(false)));
        languageMenu.add(all);
        languageMenu.addSeparator();
        for (Language l : Language.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(l.toString(), savedLanguages().contains(l));
            item.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", Boolean.TRUE);
            item.addActionListener(e -> updateLanguageButton());
            languageItems.put(l, item);
            languageMenu.add(item);
        }
        languageMenu.addPopupMenuListener(new PopupMenuListener() {
            private Set<Language> before;

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                before = selectedLanguages();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                updateLanguageButton();
                if (hasSearched && !selectedLanguages().equals(before)) {
                    SwingUtilities.invokeLater(BookbotSearchApp.this::newSearch);
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        languageButton.setToolTipText("Taal van de boeken (meerdere mogelijk)");
        languageButton.addActionListener(e -> languageMenu.show(languageButton, 0, languageButton.getHeight()));
        updateLanguageButton();
    }

    private Set<Language> savedLanguages() {
        Set<Language> languages = EnumSet.noneOf(Language.class);
        String saved;
        try {
            saved = prefs.get(PREF_LANGUAGES, DEFAULT_LANGUAGES);
        } catch (RuntimeException e) {
            saved = DEFAULT_LANGUAGES;
        }
        for (String name : saved.split(",")) {
            try {
                languages.add(Language.valueOf(name.strip()));
            } catch (IllegalArgumentException ignored) {
                // empty string or a language that no longer exists
            }
        }
        return languages;
    }

    private void updateLanguageButton() {
        Set<Language> languages = selectedLanguages();
        try {
            prefs.put(PREF_LANGUAGES, String.join(",", languages.stream().map(Language::name).toList()));
        } catch (RuntimeException ignored) {
            // preferences unavailable; the choice just isn't remembered
        }
        languageButton.setText("Taal: " + (languages.isEmpty() ? "alle" : languageList(languages, ", ")) + " ▾");
    }

    private Set<Language> selectedLanguages() {
        Set<Language> languages = EnumSet.noneOf(Language.class);
        languageItems.forEach((l, item) -> {
            if (item.isSelected()) {
                languages.add(l);
            }
        });
        return languages;
    }

    private static String languageList(Set<Language> languages, String lastSeparator) {
        List<String> names = languages.stream().map(Language::toString).toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + lastSeparator + names.get(names.size() - 1);
    }

    /** A search needs terms, except when languages are chosen: then an empty query browses those languages. */
    private static boolean canSearch(String query, Set<Language> languages) {
        return !query.isEmpty() || !languages.isEmpty();
    }

    private static String describe(String query, Set<Language> languages) {
        String what = query.isEmpty() ? "alle boeken" : "\"" + query + "\"";
        return languages.isEmpty() ? what : what + " in het " + languageList(languages, " of ");
    }

    /** Starts a search with the current contents of the search bar. */
    private void newSearch() {
        String q = queryField.getText().strip();
        Set<Language> languages = selectedLanguages();
        if (!canSearch(q, languages)) {
            return;
        }
        currentQuery = q;
        currentLanguages = languages;
        currentTitleOnly = titleOnlyBox.isSelected() && !q.isEmpty();
        hasSearched = true;
        batchStarts.clear();
        startBatch(1);
    }

    /** Loads one batch of results, starting at bookbot result page {@code startPage}. */
    private void startBatch(int startPage) {
        if (searchWorker != null) {
            searchWorker.cancel(true);
        }
        String q = currentQuery;
        Set<Language> languages = currentLanguages;
        boolean titleOnly = currentTitleOnly;
        batchStarts.add(startPage);
        int batchNumber = batchStarts.size();
        String description = describe(q, languages);

        lastBatch = null;
        tableModel.setBooks(List.of());
        updateButtons(true);
        setStatus("Zoeken naar " + description + "… (boekgegevens worden opgehaald)");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<BookbotClient.Batch, Book> worker = new SwingWorker<>() {
            @Override
            protected BookbotClient.Batch doInBackground() throws Exception {
                return client.searchBatch(q, languages, titleOnly, startPage, this::publish);
            }

            @Override
            protected void process(List<Book> chunk) {
                if (isCancelled() || searchWorker != this) {
                    return;
                }
                boolean first = tableModel.getRowCount() == 0;
                chunk.forEach(tableModel::addBook);
                if (first) {
                    table.setRowSelectionInterval(0, 0);
                }
                setStatus(tableModel.getRowCount() + " gevonden voor " + description + ", zoeken gaat door…");
            }

            @Override
            protected void done() {
                if (isCancelled() || searchWorker != this) {
                    return;
                }
                setCursor(Cursor.getDefaultCursor());
                try {
                    BookbotClient.Batch batch = get();
                    lastBatch = batch;
                    // process() normally showed every book already; only reload if some were missed.
                    if (tableModel.getRowCount() != batch.books().size()) {
                        tableModel.setBooks(batch.books());
                    }
                    if (!batch.books().isEmpty() && table.getSelectedRow() < 0) {
                        table.setRowSelectionInterval(0, 0);
                    }
                    setStatus(summary(batch, description, batchNumber));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    setStatus("Fout: " + cause.getMessage());
                    JOptionPane.showMessageDialog(BookbotSearchApp.this,
                            html("Zoeken mislukt: " + cause.getMessage()
                                    + "\n\nURL: " + client.searchUrl(q, startPage, languages), 420),
                            "Fout", JOptionPane.ERROR_MESSAGE);
                }
                updateButtons(false);
            }
        };
        searchWorker = worker;
        worker.execute();
    }

    private static String summary(BookbotClient.Batch batch, String description, int batchNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append(batch.books().isEmpty() ? "Geen resultaten" : batch.books().size() + " resultaten")
                .append(" voor ").append(description)
                .append(" (deel ").append(batchNumber).append(", ")
                .append(batch.pagesScanned()).append(batch.pagesScanned() == 1 ? " pagina" : " pagina's")
                .append(" van bookbot doorzocht)");
        if (batch.titleSkipped() > 0) {
            sb.append(" – ").append(batch.titleSkipped()).append(" zonder zoekterm in de titel weggelaten");
        }
        if (batch.books().isEmpty() && batch.more()) {
            sb.append(" – klik op Volgende om verder te zoeken");
        }
        return sb.toString();
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
        statusLabel.setToolTipText(text);
    }

    private void updateButtons(boolean busy) {
        searchButton.setEnabled(!busy);
        prevButton.setEnabled(!busy && batchStarts.size() > 1);
        nextButton.setEnabled(!busy && lastBatch != null && lastBatch.more());
    }

    private Book selectedBook() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : tableModel.get(table.convertRowIndexToModel(viewRow));
    }

    private void updateDetails(Book book) {
        openButton.setEnabled(book != null);
        if (book == null) {
            titleLabel.setText(" ");
            authorLabel.setText(" ");
            priceLabel.setText(" ");
            yearLabel.setText(" ");
            languageLabel.setText(" ");
            coverLabel.setIcon(null);
            coverLabel.setText("Geen boek geselecteerd");
            return;
        }
        titleLabel.setText(html(book.title()));
        authorLabel.setText(book.author().isEmpty() ? " " : html(book.author()));
        priceLabel.setText(book.price().isEmpty() ? "Prijs onbekend" : book.price());
        yearLabel.setText("Jaar van uitgave: " + (book.year().isEmpty() ? "onbekend" : book.year()));
        languageLabel.setText("Taal: " + (book.language().isEmpty() ? "onbekend" : book.language()));
        loadCover(book);
    }

    private void loadCover(Book book) {
        if (coverWorker != null) {
            coverWorker.cancel(true);
        }
        coverLabel.setIcon(null);
        if (book.imageUrl().isEmpty()) {
            coverLabel.setText("Geen afbeelding");
            return;
        }
        ImageIcon cached = coverCache.get(book.imageUrl());
        if (cached != null) {
            coverLabel.setText("");
            coverLabel.setIcon(cached);
            return;
        }
        coverLabel.setText("Afbeelding laden…");
        SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage img = ImageIO.read(new URL(book.imageUrl()));
                if (img == null) {
                    return null;
                }
                double scale = Math.min(200.0 / img.getWidth(), 280.0 / img.getHeight());
                scale = Math.min(scale, 1.0);
                Image scaled = img.getScaledInstance(
                        (int) (img.getWidth() * scale), (int) (img.getHeight() * scale), Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            @Override
            protected void done() {
                if (isCancelled() || coverWorker != this) {
                    return;
                }
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        coverCache.put(book.imageUrl(), icon);
                        coverLabel.setText("");
                        coverLabel.setIcon(icon);
                    } else {
                        coverLabel.setText("Geen afbeelding");
                    }
                } catch (Exception ex) {
                    coverLabel.setText("Afbeelding niet beschikbaar");
                }
            }
        };
        coverWorker = worker;
        worker.execute();
    }

    private void showSettings() {
        JTextField searchField = new JTextField(client.getSearchUrlTemplate(), 45);
        JTextField languageSearchField = new JTextField(client.getLanguageSearchUrlTemplate(), 45);
        JTextField browseField = new JTextField(client.getLanguageBrowseUrlTemplate(), 45);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JComponent[] rows = {
            new JLabel("<html>Gebruik <b>{query}</b> voor de zoektermen, <b>{page}</b> voor het paginanummer en "
                    + "<b>{languages}</b> voor de taalcodes (bijv. 1_3_4).</html>"),
            new JLabel("Zoek-URL:"), searchField,
            new JLabel("Zoek-URL met taalfilter:"), languageSearchField,
            new JLabel("URL om per taal te bladeren (bij een leeg zoekveld):"), browseField,
        };
        for (JComponent c : rows) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            if (c instanceof JLabel && c != rows[0]) {
                panel.add(Box.createVerticalStrut(8));
            }
            panel.add(c);
        }
        int choice = JOptionPane.showOptionDialog(this, panel, "Instellingen",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[] {"Opslaan", "Standaard", "Annuleren"}, "Opslaan");
        try {
            if (choice == 0) {
                client.setUrlTemplates(searchField.getText(), languageSearchField.getText(), browseField.getText());
            } else if (choice == 1) {
                client.setUrlTemplates(BookbotClient.DEFAULT_SEARCH_URL,
                        BookbotClient.DEFAULT_LANGUAGE_SEARCH_URL, BookbotClient.DEFAULT_LANGUAGE_BROWSE_URL);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ongeldige URL", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through to showing the URL
        }
        JOptionPane.showInputDialog(this, "Kopieer deze link:", url);
    }

    private static String html(String text) {
        return html(text, 190);
    }

    private static String html(String text, int width) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + width + "px'>" + escaped + "</div></html>";
    }

    /** Table model backing the results list. */
    private static final class BookTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Titel", "Auteur", "Jaar", "Taal", "Prijs"};
        private List<Book> books = new ArrayList<>();

        void setBooks(List<Book> books) {
            this.books = new ArrayList<>(books);
            fireTableDataChanged();
        }

        void addBook(Book book) {
            books.add(book);
            fireTableRowsInserted(books.size() - 1, books.size() - 1);
        }

        Book get(int row) {
            return books.get(row);
        }

        @Override
        public int getRowCount() {
            return books.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Book b = books.get(row);
            return switch (column) {
                case 0 -> b.title();
                case 1 -> b.author();
                case 2 -> b.year();
                case 3 -> b.language();
                default -> b.price();
            };
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the default look and feel
        }
        SwingUtilities.invokeLater(() -> {
            BookbotSearchApp app = new BookbotSearchApp();
            app.setVisible(true);
            if (args.length > 0) {
                app.queryField.setText(String.join(" ", args));
                app.newSearch();
            }
        });
    }
}
