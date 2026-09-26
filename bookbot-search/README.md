# Bookbot Zoeker

Desktop app (Java Swing) for searching second-hand books on [bookbot.nl](https://bookbot.nl).

## Features

- Search by title, author or ISBN (press Enter or click **Zoeken**)
- Filter by book language with the **Taal** dropdown (Nederlands, Engels, Duits, Frans, Spaans,
  Italiaans, Tsjechisch). With a language chosen and an empty search field you browse all books in
  that language.
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

## Search URL

The app requests `https://bookbot.nl/zoeken?q={query}&page={page}` by default. If bookbot uses a
different search address, change it under **Instellingen…** (use `{query}` and `{page}` as
placeholders) or pass it at startup:

```bash
java -Dbookbot.searchUrl="https://bookbot.nl/zoeken?q={query}&page={page}" -jar target/bookbot-search-1.0.0.jar
```

### Year, language and the language filter

Bookbot's search results do not show year or language, so the app opens each result's own page
(`/g/<id>`, six at a time, cached) and reads "Jaar van uitgave" and "Taal" from it. The language
filter is applied to those values, because bookbot's language pages (`/p/language/<id>`) ignore
search terms. When a filter or the title option removes many books, the app reads up to 5 result
pages per click, stopping once it has 20 books; **Volgende** continues from there.

With a language chosen and no search terms the app browses
`https://bookbot.nl/p/language/{language}?page={page}` (override with `-Dbookbot.languageBrowseUrl=...`
or under **Instellingen…**).

## How results are read

bookbot.nl has no public API, so the app reads the HTML of the search page with
[jsoup](https://jsoup.org). It finds each link to a product page (`/g/<id>`) and reads title,
author, price and cover from the card around it. The parser does not depend on exact CSS class
names, but a big redesign of the site may still require changes in `SearchResultParser`.
Please use the app for personal use and keep your number of requests reasonable.
