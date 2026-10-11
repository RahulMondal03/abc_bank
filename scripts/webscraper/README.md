# WebScraper

A standalone, dependency-free Java web scraper (JDK 21, `java.net.http`). It is not part of the Spring Boot build.

For each page it extracts the title, meta description, h1–h3 headings, links (resolved to absolute URLs), images and word count.
It can optionally crawl same-host links breadth-first, honours `robots.txt` `Disallow` rules for `User-agent: *`,
and waits between requests.

## Usage

```bash
# Scrape a single page (plain-text output)
java scripts/webscraper/WebScraper.java https://books.toscrape.com

# Crawl same-host links one level deep, up to 20 pages, save as JSON
java scripts/webscraper/WebScraper.java https://books.toscrape.com --depth 1 --max-pages 20 --json --out result.json
```

| Option            | Default | Description                                      |
|-------------------|---------|--------------------------------------------------|
| `--depth <n>`     | `0`     | Crawl depth for same-host links (0 = only start) |
| `--max-pages <n>` | `10`    | Maximum number of pages to fetch                 |
| `--delay <ms>`    | `1000`  | Delay between requests                           |
| `--json`          | off     | Emit JSON instead of plain text                  |
| `--out <file>`    | stdout  | Write output to a file                           |

Progress messages go to stderr, so stdout can be piped (e.g. `... --json | jq`).

> Parsing uses regular expressions, which is fine for typical pages but not a full HTML parser.
> Only scrape sites you are permitted to, and respect their terms of service.
