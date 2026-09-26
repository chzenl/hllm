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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Swing front end for searching second-hand books on bookbot.nl. */
public final class BookbotSearchApp extends JFrame {

    private final BookbotClient client = new BookbotClient();
    private final Map<String, ImageIcon> coverCache = new ConcurrentHashMap<>();

    private final JTextField queryField = new JTextField(30);
    private final JComboBox<Language> languageBox = new JComboBox<>(Language.values());
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
    private final JButton openButton = new JButton("Openen op bookbot.nl");

    private String currentQuery = "";
    private Language currentLanguage = Language.ALL;
    private boolean hasSearched;
    private int currentPage = 1;
    private SwingWorker<List<Book>, Void> searchWorker;
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
        languageBox.setToolTipText("Taal van de boeken");
        topButtons.add(new JLabel("Taal:"));
        topButtons.add(languageBox);
        topButtons.add(searchButton);
        topButtons.add(settingsButton);
        top.add(topButtons, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.getColumnModel().getColumn(0).setPreferredWidth(380);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
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

        searchButton.addActionListener(e -> startSearch(queryField.getText(), selectedLanguage(), 1));
        queryField.addActionListener(e -> startSearch(queryField.getText(), selectedLanguage(), 1));
        languageBox.addActionListener(e -> {
            if (hasSearched) {
                startSearch(queryField.getText(), selectedLanguage(), 1);
            }
        });
        prevButton.addActionListener(e -> startSearch(currentQuery, currentLanguage, currentPage - 1));
        nextButton.addActionListener(e -> startSearch(currentQuery, currentLanguage, currentPage + 1));
        openButton.addActionListener(e -> {
            Book b = selectedBook();
            if (b != null) {
                openInBrowser(b.url());
            }
        });
        openSearchButton.addActionListener(e -> {
            if (hasSearched) {
                openInBrowser(client.searchUrl(currentQuery, currentPage, currentLanguage));
            } else if (canSearch(queryField.getText().strip(), selectedLanguage())) {
                openInBrowser(client.searchUrl(queryField.getText(), 1, selectedLanguage()));
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
        for (JLabel l : new JLabel[] {titleLabel, authorLabel, priceLabel}) {
            l.setAlignmentX(LEFT_ALIGNMENT);
        }
        openButton.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(coverLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(authorLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(priceLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(openButton);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private Language selectedLanguage() {
        Language language = (Language) languageBox.getSelectedItem();
        return language == null ? Language.ALL : language;
    }

    /** A search needs terms, except when a language is chosen: then an empty query browses that language. */
    private static boolean canSearch(String query, Language language) {
        return !query.isEmpty() || language != Language.ALL;
    }

    private static String describe(String query, Language language) {
        String what = query.isEmpty() ? "alle boeken" : "\"" + query + "\"";
        return language == Language.ALL ? what : what + " in het " + language;
    }

    private void startSearch(String query, Language language, int page) {
        String q = query == null ? "" : query.strip();
        if (!canSearch(q, language) || page < 1) {
            return;
        }
        if (searchWorker != null) {
            searchWorker.cancel(true);
        }
        currentQuery = q;
        currentLanguage = language;
        currentPage = page;
        hasSearched = true;
        updateButtons(true);
        String description = describe(q, language);
        statusLabel.setText("Zoeken naar " + description + " (pagina " + page + ")…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<List<Book>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Book> doInBackground() throws Exception {
                return client.search(q, page, language);
            }

            @Override
            protected void done() {
                if (isCancelled() || searchWorker != this) {
                    return;
                }
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<Book> books = get();
                    tableModel.setBooks(books);
                    statusLabel.setText(books.isEmpty()
                            ? "Geen resultaten gevonden voor " + description + "."
                            : books.size() + " resultaten voor " + description + " (pagina " + page + ")");
                    if (!books.isEmpty()) {
                        table.setRowSelectionInterval(0, 0);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    tableModel.setBooks(List.of());
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("Fout: " + cause.getMessage());
                    JOptionPane.showMessageDialog(BookbotSearchApp.this,
                            html("Zoeken mislukt: " + cause.getMessage()
                                    + "\n\nURL: " + client.searchUrl(q, page, language), 420),
                            "Fout", JOptionPane.ERROR_MESSAGE);
                }
                updateButtons(false);
            }
        };
        searchWorker = worker;
        worker.execute();
    }

    private void updateButtons(boolean busy) {
        searchButton.setEnabled(!busy);
        prevButton.setEnabled(!busy && hasSearched && currentPage > 1);
        nextButton.setEnabled(!busy && hasSearched && tableModel.getRowCount() > 0);
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
            coverLabel.setIcon(null);
            coverLabel.setText("Geen boek geselecteerd");
            return;
        }
        titleLabel.setText(html(book.title()));
        authorLabel.setText(book.author().isEmpty() ? " " : html(book.author()));
        priceLabel.setText(book.price().isEmpty() ? "Prijs onbekend" : book.price());
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
        JTextField urlField = new JTextField(client.getSearchUrlTemplate(), 45);
        JTextField languageUrlField = new JTextField(client.getLanguageSearchUrlTemplate(), 45);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel intro = new JLabel("<html>Gebruik <b>{query}</b> voor de zoektermen, <b>{page}</b> voor het "
                + "paginanummer en <b>{language}</b> voor de taalcode.</html>");
        JLabel plainLabel = new JLabel("Zoek-URL (alle talen):");
        JLabel languageLabel = new JLabel("Zoek-URL met taalfilter:");
        for (JComponent c : new JComponent[] {
                intro, plainLabel, urlField, languageLabel, languageUrlField}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        panel.add(intro);
        panel.add(Box.createVerticalStrut(8));
        panel.add(plainLabel);
        panel.add(urlField);
        panel.add(Box.createVerticalStrut(8));
        panel.add(languageLabel);
        panel.add(languageUrlField);
        int choice = JOptionPane.showOptionDialog(this, panel, "Instellingen",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[] {"Opslaan", "Standaard", "Annuleren"}, "Opslaan");
        try {
            if (choice == 0) {
                // Validate both before changing either, so a bad value leaves the settings untouched.
                new BookbotClient(urlField.getText(), languageUrlField.getText());
                client.setSearchUrlTemplate(urlField.getText());
                client.setLanguageSearchUrlTemplate(languageUrlField.getText());
            } else if (choice == 1) {
                client.setSearchUrlTemplate(BookbotClient.DEFAULT_SEARCH_URL);
                client.setLanguageSearchUrlTemplate(BookbotClient.DEFAULT_LANGUAGE_SEARCH_URL);
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
        private static final String[] COLUMNS = {"Titel", "Auteur", "Prijs"};
        private List<Book> books = new ArrayList<>();

        void setBooks(List<Book> books) {
            this.books = new ArrayList<>(books);
            fireTableDataChanged();
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
                app.startSearch(app.queryField.getText(), Language.ALL, 1);
            }
        });
    }
}
