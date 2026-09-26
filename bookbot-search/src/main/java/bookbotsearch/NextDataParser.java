package bookbotsearch;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads search results from the page data bookbot's Next.js site embeds in every listing page
 * ({@code <script id="__NEXT_DATA__">}, under {@code props.pageProps.componentProps.items}).
 *
 * <p>This is far more reliable than the HTML: each item has the exact title, author, price in
 * cents, stock status, language ids, publisher and ISBN. It has no publication year.
 */
public final class NextDataParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IMAGE_URL = "https://rezised-images.knhbt.cz/300x300/%s.jpg";

    private NextDataParser() {
    }

    /** The books on a listing page, or null when the page has no usable page data. */
    public static List<Book> parse(Document doc) {
        JsonNode items = componentProps(doc).path("items");
        if (!items.isArray()) {
            return null;
        }
        String origin = origin(doc.location());
        List<Book> books = new ArrayList<>();
        for (JsonNode item : items) {
            String groupId = text(item, "grandmothers_id");
            String title = text(item, "grandmothers_title");
            if (groupId.isEmpty() || title.isEmpty()) {
                continue;
            }
            String copyId = text(item, "highlight_id");
            String url = origin + "/g/" + groupId + (copyId.isEmpty() ? "" : "/b/" + copyId);
            String image = text(item, "highlight_image_id");

            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (JsonNode id : item.path("language_ids")) {
                ids.add(id.asText());
                Language l = Language.byId(id.asText());
                if (l != null) {
                    names.add(l.toString());
                }
            }

            JsonNode stock = item.get("is_in_stock");
            String availability = stock == null || stock.isNull() ? ""
                    : stock.asBoolean() ? Book.IN_STOCK : Book.SOLD_OUT;

            books.add(new Book(groupId, title, text(item, "authors_name"), euros(item.path("highlight_price_eur")),
                    url, image.isEmpty() ? "" : String.format(IMAGE_URL, image),
                    "", String.join(", ", names), String.join("_", ids),
                    availability, text(item, "publisher"), text(item, "isbn")));
        }
        return books;
    }

    /** The last result page according to the page data, or 0 when unknown. */
    public static int lastPage(Document doc) {
        return componentProps(doc).path("pagination").path("last_page").asInt(0);
    }

    private static JsonNode componentProps(Document doc) {
        Element script = doc.getElementById("__NEXT_DATA__");
        if (script == null) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(script.data()).path("props").path("pageProps").path("componentProps");
        } catch (Exception e) {
            return JSON.missingNode();
        }
    }

    /** Price in cents to "€ 1,39". */
    private static String euros(JsonNode cents) {
        if (!cents.isNumber()) {
            return "";
        }
        long c = cents.asLong();
        return String.format(Locale.ROOT, "€ %d,%02d", c / 100, c % 100);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText().strip();
    }

    private static String origin(String location) {
        try {
            URI uri = URI.create(location);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getAuthority();
            }
        } catch (IllegalArgumentException ignored) {
            // fall back to the Dutch site
        }
        return "https://bookbot.nl";
    }
}
