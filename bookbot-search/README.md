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
- **Alleen op voorraad**: hide sold-out books
- Columns for title, author, year, language, price and availability; the details panel also shows
  publisher and ISBN
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

### Filters, year and language

**Zoekterm in titel** and **Alleen op voorraad** are applied by the app. Because they can remove
many books, the app reads up to 5 result pages per click and stops once it has 20 books;
**Volgende** continues from there.

The search results carry no publication year, so the app opens each result's own page
(`/g/<id>/b/<copy>`, six at a time, cached) and reads the year from it.

## How results are read

bookbot.nl is a Next.js site: every page embeds its data as JSON in `<script id="__NEXT_DATA__">`.
For search pages the app reads `props.pageProps.componentProps.items` (title, author, price in cents,
stock, language ids, publisher, ISBN, cover id) and `pagination`; for book pages it reads `year` and
`languageIds`. If that data is missing it falls back to reading the HTML with
[jsoup](https://jsoup.org). Tests in `src/test/resources` use trimmed copies of real pages, so a
change on bookbot's side can be checked quickly. Please use the app for personal use and keep your
number of requests reasonable.
