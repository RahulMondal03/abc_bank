import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone, dependency-free web scraper.
 *
 * Fetches a page, extracts its title, meta description, headings, links and images,
 * and can optionally crawl same-host links breadth-first up to a given depth.
 * Honours robots.txt Disallow rules for "User-agent: *" and waits between requests.
 *
 * Run (Java 11+ single-file source launch):
 *   java scripts/webscraper/WebScraper.java https://example.com
 *   java scripts/webscraper/WebScraper.java https://example.com --depth 1 --max-pages 20 --json --out result.json
 */
public class WebScraper {

    private static final String USER_AGENT = "AbcBankWebScraper/1.0 (+https://github.com/RahulMondal03/abc_bank)";

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TAG = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("<h([1-3])[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANCHOR = Pattern.compile("<a\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG = Pattern.compile("<img\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_STYLE = Pattern.compile("<(script|style|noscript)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private final HttpClient client;
    private final long delayMillis;
    private final Map<String, List<String>> robotsCache = new HashMap<>();

    public WebScraper(Duration timeout, long delayMillis) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.delayMillis = delayMillis;
    }

    /** Result of scraping a single page. */
    public record PageResult(String url, int status, String title, String description,
                             List<String> headings, List<String> links, List<String> images,
                             int wordCount, String error) {
    }

    // ---------------------------------------------------------------- crawling

    public List<PageResult> crawl(String startUrl, int maxDepth, int maxPages) {
        URI start = URI.create(startUrl);
        List<PageResult> results = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(normalize(start.toString()), 0));

        while (!queue.isEmpty() && results.size() < maxPages) {
            Map.Entry<String, Integer> next = queue.poll();
            String url = next.getKey();
            int depth = next.getValue();
            if (!visited.add(url)) {
                continue;
            }
            if (!isAllowedByRobots(URI.create(url))) {
                System.err.println("[skip] disallowed by robots.txt: " + url);
                continue;
            }

            System.err.println("[fetch] (depth " + depth + ") " + url);
            PageResult page = scrape(url);
            results.add(page);

            if (depth < maxDepth && page.error() == null) {
                for (String link : page.links()) {
                    URI linkUri = URI.create(link);
                    if (start.getHost() != null && start.getHost().equalsIgnoreCase(linkUri.getHost())
                            && !visited.contains(link)) {
                        queue.add(Map.entry(link, depth + 1));
                    }
                }
            }
            sleepQuietly(delayMillis);
        }
        return results;
    }

    // ---------------------------------------------------------------- scraping

    public PageResult scrape(String url) {
        try {
            HttpResponse<String> response = fetch(URI.create(url));
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.isEmpty() && !contentType.toLowerCase().contains("html")) {
                return new PageResult(url, response.statusCode(), null, null, List.of(), List.of(), List.of(), 0,
                        "Not an HTML page (" + contentType + ")");
            }
            return parse(response.uri(), response.statusCode(), response.body());
        } catch (IOException | IllegalArgumentException e) {
            return new PageResult(url, -1, null, null, List.of(), List.of(), List.of(), 0, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PageResult(url, -1, null, null, List.of(), List.of(), List.of(), 0, "Interrupted");
        }
    }

    private HttpResponse<String> fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static PageResult parse(URI base, int status, String html) {
        String cleaned = COMMENT.matcher(html).replaceAll("");
        cleaned = SCRIPT_STYLE.matcher(cleaned).replaceAll("");

        String title = firstGroup(TITLE, cleaned);

        String description = null;
        Matcher meta = META_TAG.matcher(cleaned);
        while (meta.find()) {
            String tag = meta.group();
            String name = attr(tag, "name");
            if (name == null) {
                name = attr(tag, "property");
            }
            if (name != null && (name.equalsIgnoreCase("description") || name.equalsIgnoreCase("og:description"))) {
                description = attr(tag, "content");
                break;
            }
        }

        List<String> headings = new ArrayList<>();
        Matcher h = HEADING.matcher(cleaned);
        while (h.find()) {
            String text = toText(h.group(2));
            if (!text.isEmpty()) {
                headings.add("h" + h.group(1) + ": " + text);
            }
        }

        Set<String> links = new LinkedHashSet<>();
        Matcher a = ANCHOR.matcher(cleaned);
        while (a.find()) {
            String resolved = resolve(base, attr(a.group(), "href"));
            if (resolved != null && resolved.startsWith("http")) {
                links.add(resolved);
            }
        }

        Set<String> images = new LinkedHashSet<>();
        Matcher img = IMG.matcher(cleaned);
        while (img.find()) {
            String resolved = resolve(base, attr(img.group(), "src"));
            if (resolved != null) {
                images.add(resolved);
            }
        }

        String bodyText = toText(cleaned);
        int wordCount = bodyText.isEmpty() ? 0 : bodyText.split("\\s+").length;

        return new PageResult(base.toString(), status, title == null ? null : toText(title),
                description == null ? null : decodeEntities(description).trim(),
                headings, new ArrayList<>(links), new ArrayList<>(images), wordCount, null);
    }

    // ---------------------------------------------------------------- robots.txt

    private boolean isAllowedByRobots(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        List<String> disallowed = robotsCache.computeIfAbsent(origin, this::loadDisallowRules);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        for (String rule : disallowed) {
            if (!rule.isEmpty() && path.startsWith(rule)) {
                return false;
            }
        }
        return true;
    }

    private List<String> loadDisallowRules(String origin) {
        List<String> rules = new ArrayList<>();
        try {
            HttpResponse<String> response = fetch(URI.create(origin + "/robots.txt"));
            if (response.statusCode() != 200) {
                return rules;
            }
            boolean appliesToUs = false;
            for (String rawLine : response.body().split("\\R")) {
                String line = rawLine.replaceAll("#.*", "").trim();
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                if (key.equals("user-agent")) {
                    appliesToUs = value.equals("*");
                } else if (appliesToUs && key.equals("disallow")) {
                    rules.add(value);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // No readable robots.txt: treat everything as allowed.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return rules;
    }

    // ---------------------------------------------------------------- helpers

    private static String firstGroup(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) {
            return null;
        }
        for (int g = 2; g <= 4; g++) {
            if (m.group(g) != null) {
                return m.group(g);
            }
        }
        return null;
    }

    private static String resolve(URI base, String href) {
        if (href == null) {
            return null;
        }
        href = decodeEntities(href).trim();
        if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")
                || href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("data:")) {
            return null;
        }
        try {
            return normalize(base.resolve(href.replace(" ", "%20")).toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Drops the fragment so "page#a" and "page#b" count as one URL. */
    private static String normalize(String url) {
        try {
            URI u = new URI(url);
            return new URI(u.getScheme(), u.getAuthority(), u.getPath(), u.getQuery(), null).toString();
        } catch (URISyntaxException e) {
            int hash = url.indexOf('#');
            return hash >= 0 ? url.substring(0, hash) : url;
        }
    }

    private static String toText(String html) {
        return decodeEntities(TAG.matcher(html).replaceAll(" ")).replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        return s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- output

    static String toJson(List<PageResult> pages) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < pages.size(); i++) {
            PageResult p = pages.get(i);
            sb.append("  {\n")
                    .append("    \"url\": ").append(jsonString(p.url())).append(",\n")
                    .append("    \"status\": ").append(p.status()).append(",\n")
                    .append("    \"title\": ").append(jsonString(p.title())).append(",\n")
                    .append("    \"description\": ").append(jsonString(p.description())).append(",\n")
                    .append("    \"wordCount\": ").append(p.wordCount()).append(",\n")
                    .append("    \"headings\": ").append(jsonArray(p.headings())).append(",\n")
                    .append("    \"links\": ").append(jsonArray(p.links())).append(",\n")
                    .append("    \"images\": ").append(jsonArray(p.images())).append(",\n")
                    .append("    \"error\": ").append(jsonString(p.error())).append("\n")
                    .append("  }").append(i < pages.size() - 1 ? "," : "").append("\n");
        }
        return sb.append("]\n").toString();
    }

    private static String jsonArray(List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String v : values) {
            quoted.add(jsonString(v));
        }
        return "[" + String.join(", ", quoted) + "]";
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static String toText(List<PageResult> pages) {
        StringBuilder sb = new StringBuilder();
        for (PageResult p : pages) {
            sb.append("==================================================\n")
                    .append("URL:         ").append(p.url()).append('\n')
                    .append("Status:      ").append(p.status()).append('\n');
            if (p.error() != null) {
                sb.append("Error:       ").append(p.error()).append("\n\n");
                continue;
            }
            sb.append("Title:       ").append(p.title()).append('\n')
                    .append("Description: ").append(p.description()).append('\n')
                    .append("Word count:  ").append(p.wordCount()).append('\n')
                    .append("Headings (").append(p.headings().size()).append("):\n");
            p.headings().forEach(hd -> sb.append("  - ").append(hd).append('\n'));
            sb.append("Links (").append(p.links().size()).append("):\n");
            p.links().forEach(l -> sb.append("  - ").append(l).append('\n'));
            sb.append("Images (").append(p.images().size()).append("):\n");
            p.images().forEach(im -> sb.append("  - ").append(im).append('\n'));
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- CLI

    private static void usage() {
        System.err.println("""
                Usage: java WebScraper.java <url> [options]

                Options:
                  --depth <n>       Crawl same-host links up to depth n (default 0 = only the given page)
                  --max-pages <n>   Stop after n pages (default 10)
                  --delay <ms>      Delay between requests in milliseconds (default 1000)
                  --json            Output JSON instead of plain text
                  --out <file>      Write output to a file instead of stdout
                  -h, --help        Show this help
                """);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            usage();
            System.exit(args.length == 0 ? 1 : 0);
        }

        String url = args[0];
        int depth = 0;
        int maxPages = 10;
        long delay = 1000;
        boolean json = false;
        Path out = null;

        try {
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--depth" -> depth = Integer.parseInt(args[++i]);
                    case "--max-pages" -> maxPages = Integer.parseInt(args[++i]);
                    case "--delay" -> delay = Long.parseLong(args[++i]);
                    case "--json" -> json = true;
                    case "--out" -> out = Path.of(args[++i]);
                    case "-h", "--help" -> {
                        usage();
                        return;
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            usage();
            System.exit(1);
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        WebScraper scraper = new WebScraper(Duration.ofSeconds(10), delay);
        List<PageResult> pages = scraper.crawl(url, depth, maxPages);
        String output = json ? toJson(pages) : toText(pages);

        if (out != null) {
            Files.writeString(out, output, StandardCharsets.UTF_8);
            System.err.println("Wrote " + pages.size() + " page(s) to " + out.toAbsolutePath());
        } else {
            System.out.print(output);
        }
    }
}
