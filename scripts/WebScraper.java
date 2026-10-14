import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small, dependency-free web scraper (JDK 21+ only).
 *
 * <p>Fetches a page, extracts its title, meta description, headings, links and images,
 * and optionally follows links breadth-first. It honours robots.txt, waits between
 * requests, and prints the results as JSON.
 *
 * <pre>
 * Usage:
 *   java scripts/WebScraper.java &lt;start-url&gt; [options]
 *
 * Options:
 *   --depth N       how many link hops to follow from the start page (default 0 = start page only)
 *   --max-pages N   stop after scraping N pages (default 20)
 *   --delay MS      wait this many milliseconds between requests (default 1000)
 *   --all-hosts     also follow links to other hosts (default: stay on the start host)
 *   --output FILE   write the JSON to FILE instead of stdout
 *
 * Example:
 *   java scripts/WebScraper.java https://example.com --depth 1 --max-pages 10 --output pages.json
 * </pre>
 *
 * <p>HTML is parsed with regular expressions, which is fine for pulling out common elements
 * but not a full HTML parser. Pages rendered by JavaScript will only show their initial HTML.
 */
public class WebScraper {

    static final String USER_AGENT = "WebScraper/1.0 (Java HttpClient)";

    static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|noscript|template)\\b[^>]*>.*?</\\1\\s*>");
    static final Pattern TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>");
    static final Pattern BASE = Pattern.compile("(?is)<base\\b([^>]*)>");
    static final Pattern META = Pattern.compile("(?is)<meta\\b([^>]*)>");
    static final Pattern HEADING = Pattern.compile("(?is)<h([1-6])\\b[^>]*>(.*?)</h\\1\\s*>");
    static final Pattern ANCHOR = Pattern.compile("(?is)<a\\s([^>]*)>(.*?)</a\\s*>");
    static final Pattern IMG = Pattern.compile("(?is)<img\\b([^>]*)>");
    static final Pattern BODY = Pattern.compile("(?is)<body\\b[^>]*>(.*)</body\\s*>");
    static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    static final Pattern WHITESPACE = Pattern.compile("\\s+");

    static final Map<String, String> NAMED_ENTITIES = Map.of(
            "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'",
            "nbsp", " ", "copy", "\u00a9", "reg", "\u00ae", "mdash", "\u2014", "ndash", "\u2013");

    record Heading(int level, String text) {}

    record Link(String url, String text) {}

    record Image(String src, String alt) {}

    record Page(String url, int status, String title, String description, List<Heading> headings,
                List<Link> links, List<Image> images, int wordCount, String error) {

        static Page failed(String url, int status, String error) {
            return new Page(url, status, null, null, List.of(), List.of(), List.of(), 0, error);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", url);
            m.put("status", status);
            if (error != null) {
                m.put("error", error);
                return m;
            }
            m.put("title", title);
            m.put("description", description);
            m.put("wordCount", wordCount);
            m.put("headings", headings.stream().map(h -> {
                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("level", h.level());
                hm.put("text", h.text());
                return hm;
            }).toList());
            m.put("links", links.stream().map(l -> {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("url", l.url());
                lm.put("text", l.text());
                return lm;
            }).toList());
            m.put("images", images.stream().map(i -> {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("src", i.src());
                im.put("alt", i.alt());
                return im;
            }).toList());
            return m;
        }
    }

    record Options(String startUrl, int maxDepth, int maxPages, long delayMs, boolean sameHost, Path output) {

        static Options parse(String[] args) {
            String url = null;
            int depth = 0;
            int maxPages = 20;
            long delay = 1000;
            boolean sameHost = true;
            Path output = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--depth" -> depth = Integer.parseInt(value(args, ++i, a));
                    case "--max-pages" -> maxPages = Integer.parseInt(value(args, ++i, a));
                    case "--delay" -> delay = Long.parseLong(value(args, ++i, a));
                    case "--all-hosts" -> sameHost = false;
                    case "--output" -> output = Path.of(value(args, ++i, a));
                    case "-h", "--help" -> usageAndExit(0);
                    default -> {
                        if (a.startsWith("--") || url != null) {
                            System.err.println("Unexpected argument: " + a);
                            usageAndExit(2);
                        }
                        url = a;
                    }
                }
            }
            if (url == null) {
                usageAndExit(2);
            }
            if (depth < 0 || maxPages < 1 || delay < 0) {
                System.err.println("--depth and --delay must be >= 0, --max-pages must be >= 1");
                usageAndExit(2);
            }
            return new Options(url, depth, maxPages, delay, sameHost, output);
        }

        private static String value(String[] args, int i, String flag) {
            if (i >= args.length) {
                System.err.println("Missing value for " + flag);
                usageAndExit(2);
            }
            return args[i];
        }

        private static void usageAndExit(int code) {
            System.err.println("""
                    Usage: java scripts/WebScraper.java <start-url> [options]
                      --depth N       link hops to follow from the start page (default 0)
                      --max-pages N   stop after N pages (default 20)
                      --delay MS      milliseconds to wait between requests (default 1000)
                      --all-hosts     also follow links to other hosts
                      --output FILE   write JSON to FILE instead of stdout""");
            System.exit(code);
        }
    }

    private final Options options;
    private final HttpClient client;
    private final RobotsRules robots;

    WebScraper(Options options) {
        this.options = options;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.robots = new RobotsRules(client);
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        List<Page> pages = new WebScraper(options).crawl();

        List<Object> out = new ArrayList<>();
        pages.forEach(p -> out.add(p.toMap()));
        String json = Json.write(out);

        if (options.output() != null) {
            Files.writeString(options.output(), json + System.lineSeparator());
            log("Wrote " + pages.size() + " page(s) to " + options.output());
        } else {
            System.out.println(json);
        }
    }

    /** Breadth-first crawl from the start URL, bounded by depth and page count. */
    List<Page> crawl() throws InterruptedException {
        String start = normalize(options.startUrl(), null);
        if (start == null) {
            throw new IllegalArgumentException("Start URL must be an absolute http(s) URL: " + options.startUrl());
        }
        String startHost = hostOf(start);

        Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(Map.entry(start, 0));
        seen.add(start);

        List<Page> results = new ArrayList<>();
        boolean firstRequest = true;
        while (!queue.isEmpty() && results.size() < options.maxPages()) {
            Map.Entry<String, Integer> next = queue.poll();
            String url = next.getKey();
            int depth = next.getValue();

            if (!robots.isAllowed(url)) {
                log("Skipping (disallowed by robots.txt): " + url);
                continue;
            }
            if (!firstRequest) {
                Thread.sleep(options.delayMs());
            }
            firstRequest = false;

            log("[" + (results.size() + 1) + "/" + options.maxPages() + "] depth " + depth + ": " + url);
            Page page = fetch(url);
            results.add(page);
            seen.add(page.url()); // final URL after redirects

            if (depth >= options.maxDepth()) {
                continue;
            }
            for (Link link : page.links()) {
                if (options.sameHost() && !startHost.equalsIgnoreCase(hostOf(link.url()))) {
                    continue;
                }
                if (seen.add(link.url())) {
                    queue.add(Map.entry(link.url(), depth + 1));
                }
            }
        }
        return results;
    }

    Page fetch(String url) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String finalUrl = response.uri().toString();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).contains("html")) {
                return Page.failed(finalUrl, response.statusCode(), "Not HTML (Content-Type: " + contentType + ")");
            }
            return parse(finalUrl, response.statusCode(), response.body());
        } catch (IOException | RuntimeException e) {
            // One bad page (timeout, TLS error, odd charset) shouldn't stop the whole crawl.
            return Page.failed(url, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static Page parse(String url, int status, String html) {
        String cleaned = SCRIPT_STYLE.matcher(COMMENT.matcher(html).replaceAll(" ")).replaceAll(" ");

        URI base = URI.create(url);
        Matcher baseTag = BASE.matcher(cleaned);
        if (baseTag.find()) {
            String href = normalize(attributes(baseTag.group(1)).get("href"), base);
            if (href != null) {
                base = URI.create(href);
            }
        }

        Matcher titleMatch = TITLE.matcher(cleaned);
        String title = titleMatch.find() ? text(titleMatch.group(1)) : null;

        String description = null;
        String ogDescription = null;
        Matcher meta = META.matcher(cleaned);
        while (meta.find()) {
            Map<String, String> attrs = attributes(meta.group(1));
            String name = attrs.getOrDefault("name", attrs.getOrDefault("property", "")).toLowerCase(Locale.ROOT);
            String content = attrs.get("content");
            if (content == null) {
                continue;
            }
            if (name.equals("description") && description == null) {
                description = text(content);
            } else if (name.equals("og:description") && ogDescription == null) {
                ogDescription = text(content);
            }
        }
        if (description == null) {
            description = ogDescription;
        }

        List<Heading> headings = new ArrayList<>();
        Matcher h = HEADING.matcher(cleaned);
        while (h.find()) {
            String text = text(h.group(2));
            if (!text.isEmpty()) {
                headings.add(new Heading(Integer.parseInt(h.group(1)), text));
            }
        }

        Map<String, Link> links = new LinkedHashMap<>();
        Matcher a = ANCHOR.matcher(cleaned);
        while (a.find()) {
            String href = normalize(attributes(a.group(1)).get("href"), base);
            if (href != null) {
                links.putIfAbsent(href, new Link(href, text(a.group(2))));
            }
        }

        Map<String, Image> images = new LinkedHashMap<>();
        Matcher img = IMG.matcher(cleaned);
        while (img.find()) {
            Map<String, String> attrs = attributes(img.group(1));
            String src = normalize(attrs.getOrDefault("src", attrs.get("data-src")), base);
            if (src != null) {
                String alt = attrs.get("alt");
                images.putIfAbsent(src, new Image(src, alt == null ? null : text(alt)));
            }
        }

        Matcher body = BODY.matcher(cleaned);
        String bodyText = text(body.find() ? body.group(1) : cleaned);
        int wordCount = bodyText.isEmpty() ? 0 : WHITESPACE.split(bodyText).length;

        return new Page(url, status, title, description, headings,
                new ArrayList<>(links.values()), new ArrayList<>(images.values()), wordCount, null);
    }

    /** Resolves {@code raw} against {@code base} and returns a fragment-free http(s) URL, or null. */
    static String normalize(String raw, URI base) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = decodeEntities(raw.trim()).replace(" ", "%20");
            URI uri = base == null ? new URI(cleaned) : base.resolve(cleaned);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getRawAuthority() == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT)
                    + path + query;
        } catch (Exception e) {
            return null; // malformed href; skip it
        }
    }

    static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Map<String, String> attributes(String tagBody) {
        Map<String, String> attrs = new HashMap<>();
        Matcher m = ATTR.matcher(tagBody);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
            attrs.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), value);
        }
        return attrs;
    }

    /** Strips tags, decodes entities and collapses whitespace. */
    static String text(String html) {
        String noTags = TAG.matcher(html).replaceAll(" ");
        return WHITESPACE.matcher(decodeEntities(noTags)).replaceAll(" ").trim();
    }

    static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String entity = m.group(1);
            String replacement = m.group();
            try {
                if (entity.startsWith("#x") || entity.startsWith("#X")) {
                    replacement = codePoint(Integer.parseInt(entity.substring(2), 16), replacement);
                } else if (entity.startsWith("#")) {
                    replacement = codePoint(Integer.parseInt(entity.substring(1)), replacement);
                } else {
                    replacement = NAMED_ENTITIES.getOrDefault(entity, replacement);
                }
            } catch (NumberFormatException ignored) {
                // leave the entity as-is
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String codePoint(int cp, String fallback) {
        return Character.isValidCodePoint(cp) ? Character.toString(cp) : fallback;
    }

    static void log(String message) {
        System.err.println(message);
    }

    /** Minimal robots.txt support: Disallow rules (with * and $) from the "User-agent: *" group. */
    static final class RobotsRules {
        private final HttpClient client;
        private final Map<String, List<Pattern>> rulesByOrigin = new HashMap<>();

        RobotsRules(HttpClient client) {
            this.client = client;
        }

        boolean isAllowed(String url) {
            URI uri = URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            List<Pattern> rules = rulesByOrigin.computeIfAbsent(origin, this::load);
            String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return rules.stream().noneMatch(rule -> rule.matcher(path).lookingAt());
        }

        private List<Pattern> load(String origin) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 ? parse(response.body()) : List.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            } catch (IOException | RuntimeException e) {
                return List.of(); // no readable robots.txt means no restrictions
            }
        }

        static List<Pattern> parse(String robotsTxt) {
            List<Pattern> rules = new ArrayList<>();
            boolean groupApplies = false;
            boolean readingAgents = false;
            for (String line : robotsTxt.split("\\r?\\n")) {
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (key.equals("user-agent")) {
                    if (!readingAgents) {
                        groupApplies = false; // a new group starts
                    }
                    readingAgents = true;
                    groupApplies |= value.equals("*");
                } else {
                    readingAgents = false;
                    if (groupApplies && key.equals("disallow") && !value.isEmpty()) {
                        rules.add(toPattern(value));
                    }
                }
            }
            return rules;
        }

        private static Pattern toPattern(String rule) {
            boolean anchored = rule.endsWith("$");
            String body = anchored ? rule.substring(0, rule.length() - 1) : rule;
            StringBuilder regex = new StringBuilder();
            for (String part : body.split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(part));
            }
            if (anchored) {
                regex.append("$");
            }
            return Pattern.compile(regex.toString());
        }
    }

    /** Tiny JSON writer for maps, lists, strings, numbers and booleans. */
    static final class Json {
        static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            write(sb, value, 0);
            return sb.toString();
        }

        private static void write(StringBuilder sb, Object value, int indent) {
            String pad = "  ".repeat(indent);
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s) {
                quote(sb, s);
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<?, ?> e = it.next();
                    sb.append(pad).append("  ");
                    quote(sb, String.valueOf(e.getKey()));
                    sb.append(": ");
                    write(sb, e.getValue(), indent + 1);
                    sb.append(it.hasNext() ? ",\n" : "\n");
                }
                sb.append(pad).append('}');
            } else if (value instanceof Collection<?> list) {
                if (list.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append("[\n");
                Iterator<?> it = list.iterator();
                while (it.hasNext()) {
                    sb.append(pad).append("  ");
                    write(sb, it.next(), indent + 1);
                    sb.append(it.hasNext() ? ",\n" : "\n");
                }
                sb.append(pad).append(']');
            } else {
                quote(sb, value.toString());
            }
        }

        private static void quote(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
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
            sb.append('"');
        }
    }
}
