package bookbotsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Extracts books from a bookbot.nl HTML page.
 *
 * <p>Bookbot product pages live at {@code /g/<id>}. Rather than depending on exact CSS class
 * names (which change whenever the shop is restyled), the parser finds every link to a product
 * page, determines the smallest "card" element that belongs to that product only, and then
 * reads title, author, price and cover image from that card using a set of heuristics.
 */
public final class SearchResultParser {

    private static final Pattern PRODUCT_PATH = Pattern.compile("/g/(\\d+)(?:[/?#]|$)");
    private static final Pattern PRICE = Pattern.compile(
            "€\\s*\\d{1,5}(?:[.,]\\d{1,2})?|\\d{1,5}(?:[.,]\\d{1,2})?\\s*€");
    private static final Set<String> NOISE = Set.of(
            "in winkelwagen", "toevoegen", "bekijk", "meer", "details", "kopen", "nu kopen");

    private static final String TITLE_CSS = String.join(", ",
            "[itemprop=name]",
            "[class*=title]:not([class*=author]):not([class*=Author])",
            "[class*=Title]:not([class*=author]):not([class*=Author])",
            "[class*=name]:not([class*=author]):not([class*=Author])");

    private SearchResultParser() {
    }

    /** Parses a search results page (or a single product page, e.g. after an ISBN redirect). */
    public static List<Book> parse(Document doc) {
        Matcher self = PRODUCT_PATH.matcher(doc.location() == null ? "" : doc.location());
        if (self.find() && productLinks(doc).isEmpty()) {
            return List.of(parseProductPage(doc, self.group(1)));
        }

        Map<String, List<Element>> linksById = productLinks(doc);
        List<Book> books = new ArrayList<>();
        for (Map.Entry<String, List<Element>> e : linksById.entrySet()) {
            Book book = parseCard(e.getKey(), e.getValue());
            if (!book.title().isEmpty()) {
                books.add(book);
            }
        }
        return books;
    }

    private static Map<String, List<Element>> productLinks(Document doc) {
        Map<String, List<Element>> result = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            String id = productId(a);
            if (id != null) {
                result.computeIfAbsent(id, k -> new ArrayList<>()).add(a);
            }
        }
        return result;
    }

    private static String productId(Element a) {
        String href = a.absUrl("href");
        if (href.isEmpty()) {
            href = a.attr("href");
        }
        if (href.startsWith("http") && !href.matches("https?://([^/]+\\.)?bookbot\\.[a-z]+(/.*)?")
                && !sameHost(href, a.baseUri())) {
            return null;
        }
        Matcher m = PRODUCT_PATH.matcher(href);
        return m.find() ? m.group(1) : null;
    }

    private static boolean sameHost(String url, String pageUrl) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null && host.equalsIgnoreCase(java.net.URI.create(pageUrl).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Book parseCard(String id, List<Element> links) {
        Element card = findCard(links.get(0), id);
        String url = links.get(0).absUrl("href");
        if (url.isEmpty()) {
            url = "https://bookbot.nl/g/" + id;
        }

        String title = firstText(card, TITLE_CSS);
        if (title.isEmpty()) {
            title = firstText(card, "h1, h2, h3, h4, h5, h6");
        }
        if (title.isEmpty()) {
            title = bestLinkText(links);
        }
        if (title.isEmpty()) {
            title = attr(card.selectFirst("img[alt]"), "alt");
        }
        if (title.isEmpty()) {
            title = attr(links.get(0), "title");
        }

        String author = firstText(card, "[class*=author], [class*=Author], [class*=auteur], [itemprop=author]");
        if (author.isEmpty()) {
            author = firstText(card, "a[href*=/a/], a[href*=author], a[href*=auteur]");
        }
        if (author.isEmpty() && title.contains(" - ")) {
            int i = title.lastIndexOf(" - ");
            author = title.substring(i + 3);
            title = title.substring(0, i);
        }
        if (!author.isEmpty() && title.endsWith(author) && !title.equals(author)) {
            title = title.substring(0, title.length() - author.length()).strip();
        }

        String price = firstPrice(card.select("[class*=price], [class*=Price], [class*=prijs], [itemprop=price]"));
        if (price.isEmpty()) {
            price = findPrice(card.text());
        }

        return new Book(id, clean(title), clean(author), price, url, imageUrl(card));
    }

    /** Walks up from the link while the ancestor still belongs to this product only. */
    private static Element findCard(Element link, String id) {
        Element card = link;
        Element parent = link.parent();
        while (parent != null && !parent.tagName().equals("body")) {
            Set<String> ids = new TreeSet<>();
            for (Element a : parent.select("a[href]")) {
                String other = productId(a);
                if (other != null) {
                    ids.add(other);
                }
            }
            if (ids.size() > 1 || !ids.contains(id)) {
                break;
            }
            card = parent;
            parent = parent.parent();
        }
        return card;
    }

    private static Book parseProductPage(Document doc, String id) {
        String title = meta(doc, "og:title");
        if (title.isEmpty()) {
            title = doc.title();
        }
        title = title.replaceFirst("\\s*-\\s*bookbot\\.[a-z]+\\s*$", "");
        String author = "";
        int i = title.lastIndexOf(" - ");
        if (i > 0) {
            author = title.substring(i + 3);
            title = title.substring(0, i);
        }
        String price = firstPrice(doc.select("[class*=price], [class*=Price], [itemprop=price]"));
        String url = doc.location() == null || doc.location().isEmpty()
                ? "https://bookbot.nl/g/" + id : doc.location();
        return new Book(id, clean(title), clean(author), price, url, meta(doc, "og:image"))
                .withDetails(BookDetailsParser.parse(doc));
    }

    private static String firstText(Element root, String css) {
        for (Element el : root.select(css)) {
            String t = el.text().strip();
            if (!t.isEmpty() && !isNoise(t) && findPrice(t).isEmpty()) {
                return t;
            }
        }
        return "";
    }

    private static String bestLinkText(List<Element> links) {
        String best = "";
        for (Element a : links) {
            String t = a.text().strip();
            if (!isNoise(t) && findPrice(t).isEmpty() && t.length() > best.length()) {
                best = t;
            }
        }
        return best;
    }

    private static String firstPrice(Elements elements) {
        for (Element el : elements) {
            String content = el.attr("content");
            if (!content.isEmpty() && content.matches("\\d+(?:[.,]\\d+)?")) {
                return "€ " + content.replace('.', ',');
            }
            String p = findPrice(el.text());
            if (!p.isEmpty()) {
                return p;
            }
        }
        return "";
    }

    static String findPrice(String text) {
        Matcher m = PRICE.matcher(text);
        if (!m.find()) {
            return "";
        }
        String amount = m.group().replace("€", "").strip();
        return "€ " + amount;
    }

    private static String imageUrl(Element card) {
        Element img = card.selectFirst("img");
        if (img == null) {
            return "";
        }
        for (String key : new String[] {"src", "data-src", "data-lazy-src"}) {
            String v = img.absUrl(key);
            if (!v.isEmpty() && !v.startsWith("data:")) {
                return v;
            }
        }
        String srcset = img.attr("srcset");
        if (!srcset.isEmpty()) {
            return srcset.split(",")[0].strip().split("\\s+")[0];
        }
        return "";
    }

    private static String meta(Document doc, String property) {
        return attr(doc.selectFirst("meta[property=" + property + "], meta[name=" + property + "]"), "content");
    }

    private static String attr(Element el, String name) {
        return el == null ? "" : el.attr(name).strip();
    }

    private static boolean isNoise(String t) {
        return t.isEmpty() || NOISE.contains(t.toLowerCase());
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }
}
