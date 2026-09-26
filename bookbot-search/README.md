# Bookbot Zoeker

Desktop app (Java Swing) for searching second-hand books on [bookbot.nl](https://bookbot.nl).

## Features

- Search by title, author or ISBN (press Enter or click **Zoeken**)
- Filter on one or more book languages with the **Taal** button (Nederlands, Engels, Duits, Frans,
  Spaans, Italiaans, Tsjechisch). Nederlands, Engels, Duits and Tsjechisch are selected at first;
  the app remembers your last choice. With languages chosen and an empty search field you browse
  all books in those languages.
- **Zoekterm in titel** (on by default): only show books whose title contains every search word.
  Turn it off to also find books by author or ISBN.
- Publication year and language are shown for every book
- Details panel with the cover image, title, author and price
- Double-click a result, or click **Openen op bookbot.nl**, to open the book page in your browser
- Page through results with **Vorige** / **Volgende**
- **Zoekpagina in browser** opens the same search on the website
- A search by ISBN that redirects straight to a product page returns that single book

## Requirements

- Java 17 or newer
- Maven 3.8+ (only needed to build)

## Build and run

```bash
cd bookbot-search
mvn package
java -jar target/bookbot-search-1.0.0.jar
# or start with a search right away:
java -jar target/bookbot-search-1.0.0.jar "harry potter"
```

## Search URLs

The app uses the same addresses as bookbot.nl itself:

| What | Default URL |
| --- | --- |
| Search | `https://bookbot.nl/p/q/{query}/page/{page}` |
| Search in languages | `https://bookbot.nl/p/q/{query}/language/{languages}/page/{page}` |
| Browse languages (empty search) | `https://bookbot.nl/p/language/{languages}/page/{page}` |

`{languages}` is bookbot's language ids in ascending order joined by `_`, for example `1_3_4_21`
for Tsjechisch (1), Engels (3), Duits (4) and Nederlands (21). Other ids: Frans 5, Spaans 7,
Italiaans 8. Page 2 of German and Dutch results for "chemie" is
`https://bookbot.nl/p/q/chemie/language/4_21/page/2`; for page 1 the `/page/1` part is left out.

The URLs can be changed under **Instellingen…** or at startup with `-Dbookbot.searchUrl=...`,
`-Dbookbot.languageSearchUrl=...` and `-Dbookbot.languageBrowseUrl=...`.

### Title filter, year and language

**Zoekterm in titel** is applied by the app: books whose title does not contain every search word
are left out. Because that can remove many books, the app reads up to 5 result pages per click and
stops once it has 20 books; **Volgende** continues from there.

Search results do not show year or language, so the app opens each result's own page
(`/g/<id>`, six at a time, cached) and reads "Jaar van uitgave" and "Taal" from it.

## How results are read

bookbot.nl has no public API, so the app reads the HTML of the search page with
[jsoup](https://jsoup.org). It finds each link to a product page (`/g/<id>`) and reads title,
author, price and cover from the card around it. The parser does not depend on exact CSS class
names, but a big redesign of the site may still require changes in `SearchResultParser`.
Please use the app for personal use and keep your number of requests reasonable.
