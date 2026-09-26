package bookbotsearch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Reads publication year and language from a book's own page ({@code /g/<id>}).
 *
 * <p>The exact markup of bookbot's product pages is not known, so this looks in several places,
 * most reliable first: labelled rows such as "Jaar van uitgave: 1998" or "Taal | Duits" (in
 * table, definition-list or plain element form), links to {@code /p/language/<id>}, and
 * schema.org data ({@code datePublished}, {@code inLanguage}).
 */
public final class BookDetailsParser {

    private static final Pattern YEAR_LABEL = Pattern.compile(
            "(?i)^\\s*(jaar van uitgave|uitgavejaar|jaar van publicatie|publicatiejaar|jaar|verschenen|"
                    + "year of publication|publication year|year published|year|published|"
                    + "erscheinungsjahr|jahr|rok vydani|rok vydání|rok)\\s*:?\\s*$");
    private static final Pattern LANGUAGE_LABEL = Pattern.compile(
            "(?i)^\\s*(taal|language|sprache|jazyk)\\s*:?\\s*$");
    private static final Pattern YEAR_INLINE = Pattern.compile(
            "(?i)(jaar van uitgave|uitgavejaar|publicatiejaar|year of publication|publication year|"
                    + "erscheinungsjahr|rok vydání)\\s*:?\\s*((?:1[4-9]|20)\\d{2})");
    private static final Pattern LANGUAGE_INLINE = Pattern.compile(
            "(?i)\\b(taal|language|sprache|jazyk)\\s*:\\s*([\\p{L}]+)");
    private static final Pattern YEAR = Pattern.compile("\\b((?:1[4-9]|20)\\d{2})\\b");
    private static final Pattern LANGUAGE_LINK = Pattern.compile("/p/language/(\\d+)(?:[/?#]|$)");
    private static final Pattern LD_YEAR = Pattern.compile("\"datePublished\"\\s*:\\s*\"?((?:1[4-9]|20)\\d{2})");
    private static final Pattern LD_LANGUAGE = Pattern.compile("\"inLanguage\"\\s*:\\s*\"([^\"]+)\"");

    private BookDetailsParser() {
    }

    public static BookDetails parse(Document doc) {
        String year = "";
        String language = "";
        String languageId = "";

        // 1. Labelled values: <dt>Taal</dt><dd>Duits</dd>, <th>Jaar</th><td>1998</td>, <span>Taal:</span> <a>Duits</a> ...
        for (Element el : doc.body() == null ? doc.select("*") : doc.body().select("*")) {
            String own = el.ownText();
            if (own.isEmpty() || own.length() > 40) {
                continue;
            }
            if (year.isEmpty() && YEAR_LABEL.matcher(own).matches()) {
                year = firstYear(valueOf(el));
            }
            if (language.isEmpty() && LANGUAGE_LABEL.matcher(own).matches()) {
                Element value = valueElement(el);
                if (value != null) {
                    language = value.text();
                    Element link = value.is("a[href]") ? value : value.selectFirst("a[href]");
                    languageId = link == null ? "" : languageLinkId(link);
                }
            }
        }

        // 2. Inline text such as "Jaar van uitgave: 1998" or "Taal: Duits".
        String text = doc.body() == null ? "" : doc.body().text();
        if (year.isEmpty()) {
            Matcher m = YEAR_INLINE.matcher(text);
            if (m.find()) {
                year = m.group(2);
            }
        }
        if (language.isEmpty()) {
            Matcher m = LANGUAGE_INLINE.matcher(text);
            if (m.find()) {
                language = m.group(2);
            }
        }

        // 3. A single link to a language page (several links usually means a navigation menu).
        if (languageId.isEmpty()) {
            Map<String, String> links = new LinkedHashMap<>();
            for (Element a : doc.select("a[href*=/p/language/]")) {
                String id = languageLinkId(a);
                if (!id.isEmpty()) {
                    links.putIfAbsent(id, a.text());
                }
            }
            if (links.size() == 1) {
                Map.Entry<String, String> only = links.entrySet().iterator().next();
                languageId = only.getKey();
                if (language.isEmpty()) {
                    language = only.getValue();
                }
            }
        }

        // 4. schema.org data (microdata or JSON-LD).
        if (year.isEmpty()) {
            Element el = doc.selectFirst("[itemprop=datePublished]");
            if (el != null) {
                year = firstYear(el.hasAttr("content") ? el.attr("content") : el.text());
            }
        }
        if (language.isEmpty()) {
            Element el = doc.selectFirst("[itemprop=inLanguage]");
            if (el != null) {
                language = el.hasAttr("content") ? el.attr("content") : el.text();
            }
        }
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            Matcher m;
            if (year.isEmpty() && (m = LD_YEAR.matcher(json)).find()) {
                year = m.group(1);
            }
            if (language.isEmpty() && (m = LD_LANGUAGE.matcher(json)).find()) {
                language = m.group(1);
            }
        }

        String price = SearchResultParser.findPrice(
                text(doc.selectFirst("[itemprop=price], [class*=price], [class*=Price]")));
        String image = attr(doc.selectFirst("meta[property=og:image]"), "content");
        return new BookDetails(year, language, languageId, price, image);
    }

    /** The element holding the value for a label element. */
    private static Element valueElement(Element label) {
        Element next = label.nextElementSibling();
        if (next != null && !next.text().isBlank()) {
            return next;
        }
        // <div><span>Taal:</span> Duits</div> — the value is the parent's own text.
        Element parent = label.parent();
        if (parent != null && !parent.ownText().isBlank()) {
            Element holder = parent.shallowClone();
            holder.text(parent.ownText());
            return holder;
        }
        // <div><div>Taal</div></div><div>Duits</div> — label wrapped in its own container.
        if (parent != null && parent.children().size() == 1) {
            Element parentNext = parent.nextElementSibling();
            if (parentNext != null && !parentNext.text().isBlank()) {
                return parentNext;
            }
        }
        return null;
    }

    private static String valueOf(Element label) {
        Element value = valueElement(label);
        return value == null ? "" : value.text();
    }

    private static String firstYear(String s) {
        Matcher m = YEAR.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String languageLinkId(Element a) {
        Matcher m = LANGUAGE_LINK.matcher(a.attr("href"));
        return m.find() ? m.group(1) : "";
    }

    private static String text(Element el) {
        return el == null ? "" : el.hasAttr("content") ? "€ " + el.attr("content") : el.text();
    }

    private static String attr(Element el, String name) {
        return el == null ? "" : el.attr(name);
    }
}
