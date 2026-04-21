package stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import stock.common.HttpTextFetcher;
import stock.vo.TaiwanStockVO;

public class MarketThemeRadar {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String THEME_CONFIG_PATH = "config/theme_baskets.csv";
    private static final String AUTO_THEME_CONFIG_PATH = "config/theme_baskets_auto.csv";
    private static final String DAILY_SNAPSHOT_DIR = "daily_snapshots";
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{4})(?:\\.(?:TW|TWO))?(?!\\d)");
    private static final Pattern CJK_TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z0-9][\\u4e00-\\u9fa5A-Za-z0-9\\-]{1,14}");
    private static final int MAX_AUTO_THEMES = parseIntProperty("stock.marketThemeRadar.maxAutoThemes", 12);
    private static final int MAX_TITLES_PER_SOURCE = parseIntProperty("stock.marketThemeRadar.maxTitlesPerSource", 45);
    private static final int MIN_TERM_SCORE = parseIntProperty("stock.marketThemeRadar.minTermScore", 3);
    private static final Set<String> STOP_WORDS = buildStopWords();

    private final HttpTextFetcher fetcher = new HttpTextFetcher();

    public Report refresh(List<TaiwanStockVO> stocks, String dateStamp) {
        List<TaiwanStockVO> safeStocks = stocks == null ? new ArrayList<TaiwanStockVO>() : stocks;
        Map<String, TaiwanStockVO> byCode = buildStockByCode(safeStocks);
        List<StockNameEntry> stockNames = buildStockNameEntries(safeStocks);
        List<ThemeRule> manualThemes = loadManualThemes();
        List<Article> articles = collectArticles();
        Map<String, HotTheme> themes = aggregateThemes(articles, manualThemes, byCode, stockNames);
        List<HotTheme> ordered = orderThemes(themes);
        writeAutoThemeConfig(ordered);
        writeReports(dateStamp, articles, ordered);
        return new Report(articles, ordered);
    }

    private List<Article> collectArticles() {
        List<Article> articles = new ArrayList<Article>();
        articles.addAll(fetchYahooMarketArticles());
        articles.addAll(fetchCnyesRssArticles());
        articles.addAll(fetchMoneyDjArticles());
        return distinctArticles(articles);
    }

    private List<Article> fetchYahooMarketArticles() {
        List<Article> articles = new ArrayList<Article>();
        try {
            String html = fetcher.fetchPageHtml("https://tw.stock.yahoo.com/tw-market");
            Document document = Jsoup.parse(html, "https://tw.stock.yahoo.com/tw-market");
            Elements anchors = document.select("a[href]");
            for (Element anchor : anchors) {
                String title = safe(anchor.text());
                String url = safe(anchor.absUrl("href"));
                if (title.length() < 8 || !url.startsWith("https://tw.stock.yahoo.com/news/")) {
                    continue;
                }
                articles.add(new Article("Yahoo", title, url));
                if (articles.size() >= MAX_TITLES_PER_SOURCE) {
                    break;
                }
            }
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar Yahoo skipped: " + ex.getMessage());
        }
        return articles;
    }

    private List<Article> fetchCnyesRssArticles() {
        List<Article> articles = fetchCnyesHtmlArticles("https://anuenews.cnyes.com/news/cat/tw_stock");
        if (!articles.isEmpty()) {
            return articles;
        }
        return fetchCnyesHtmlArticles("https://news.cnyes.com/news/cat/tw_stock");
    }

    private List<Article> fetchCnyesHtmlArticles(String pageUrl) {
        List<Article> articles = new ArrayList<Article>();
        try {
            String html = fetcher.fetchPageHtml(pageUrl);
            Document document = Jsoup.parse(html, pageUrl);
            Elements anchors = document.select("a[href]");
            LinkedHashSet<String> seenTitles = new LinkedHashSet<String>();
            for (Element anchor : anchors) {
                String title = safe(anchor.text());
                String url = safe(anchor.absUrl("href"));
                if (title.length() < 8 || !isCnyesNewsUrl(url) || seenTitles.contains(title)) {
                    continue;
                }
                seenTitles.add(title);
                articles.add(new Article("鉅亨", title, url));
                if (articles.size() >= MAX_TITLES_PER_SOURCE) {
                    break;
                }
            }
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar Cnyes skipped: " + pageUrl + " | " + ex.getMessage());
        }
        return articles;
    }

    private List<Article> fetchMoneyDjArticles() {
        List<Article> articles = new ArrayList<Article>();
        try {
            String html = fetcher.fetchPageHtml("https://www.moneydj.com/kmdj/news/newsreallist.aspx?a=MB010000");
            Document document = Jsoup.parse(html, "https://www.moneydj.com/kmdj/news/newsreallist.aspx?a=MB010000");
            Elements anchors = document.select("a[href]");
            for (Element anchor : anchors) {
                String title = safe(anchor.text());
                String url = safe(anchor.absUrl("href"));
                if (title.length() < 8 || !looksLikeFinanceTitle(title)) {
                    continue;
                }
                articles.add(new Article("MoneyDJ", title, url));
                if (articles.size() >= MAX_TITLES_PER_SOURCE) {
                    break;
                }
            }
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar MoneyDJ skipped: " + ex.getMessage());
        }
        return articles;
    }

    private Map<String, HotTheme> aggregateThemes(List<Article> articles, List<ThemeRule> manualThemes,
            Map<String, TaiwanStockVO> byCode, List<StockNameEntry> stockNames) {
        Map<String, HotTheme> themes = new LinkedHashMap<String, HotTheme>();
        for (Article article : articles) {
            List<String> mentionedCodes = resolveMentionedCodes(article.title, byCode, stockNames);
            Set<String> articleTerms = new LinkedHashSet<String>();
            articleTerms.addAll(resolveManualThemeHits(article.title, manualThemes));
            articleTerms.addAll(extractHotTerms(article.title));
            for (String term : articleTerms) {
                HotTheme theme = themes.get(term);
                if (theme == null) {
                    theme = new HotTheme(term);
                    themes.put(term, theme);
                }
                theme.score += 1 + Math.min(3, mentionedCodes.size());
                theme.sources.add(article.source);
                theme.titles.add(article.title);
                theme.urls.add(article.url);
                theme.codes.addAll(mentionedCodes);
            }
        }
        return themes;
    }

    private List<String> resolveManualThemeHits(String title, List<ThemeRule> manualThemes) {
        List<String> hits = new ArrayList<String>();
        for (ThemeRule rule : manualThemes) {
            if (contains(title, rule.name) || containsAny(title, rule.keywords)) {
                hits.add(rule.name);
            }
        }
        return hits;
    }

    private List<String> extractHotTerms(String title) {
        List<String> terms = new ArrayList<String>();
        Matcher matcher = CJK_TOKEN_PATTERN.matcher(safe(title));
        while (matcher.find()) {
            String token = normalizeTerm(matcher.group());
            if (token.length() < 2 || STOP_WORDS.contains(token.toLowerCase(Locale.ENGLISH))) {
                continue;
            }
            if (isMostlyNumber(token) || isStockCodeLike(token)) {
                continue;
            }
            if (!terms.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private List<String> resolveMentionedCodes(String title, Map<String, TaiwanStockVO> byCode,
            List<StockNameEntry> stockNames) {
        LinkedHashSet<String> codes = new LinkedHashSet<String>();
        Matcher matcher = STOCK_CODE_PATTERN.matcher(title);
        while (matcher.find()) {
            String code = safe(matcher.group(1));
            if (byCode.containsKey(code)) {
                codes.add(code);
            }
        }
        for (StockNameEntry entry : stockNames) {
            if (contains(title, entry.name)) {
                codes.add(entry.code);
            }
        }
        return new ArrayList<String>(codes);
    }

    private List<HotTheme> orderThemes(Map<String, HotTheme> themes) {
        List<HotTheme> ordered = new ArrayList<HotTheme>();
        for (HotTheme theme : themes.values()) {
            if (theme.score < MIN_TERM_SCORE || theme.titles.size() < 2) {
                continue;
            }
            ordered.add(theme);
        }
        Collections.sort(ordered, new Comparator<HotTheme>() {
            public int compare(HotTheme left, HotTheme right) {
                int scoreCompare = Integer.compare(right.score, left.score);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                int sourceCompare = Integer.compare(right.sources.size(), left.sources.size());
                if (sourceCompare != 0) {
                    return sourceCompare;
                }
                return left.name.compareTo(right.name);
            }
        });
        if (ordered.size() > MAX_AUTO_THEMES) {
            return new ArrayList<HotTheme>(ordered.subList(0, MAX_AUTO_THEMES));
        }
        return ordered;
    }

    private void writeAutoThemeConfig(List<HotTheme> themes) {
        PrintWriter writer = null;
        try {
            File file = new File(AUTO_THEME_CONFIG_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8)));
            writer.println("# auto-generated by MarketThemeRadar; manual themes remain in config/theme_baskets.csv");
            writer.println("# theme|keywords(comma-separated)|codes(comma-separated)");
            for (HotTheme theme : themes) {
                writer.println(csvField("AUTO:" + theme.name) + "|" + csvKeywords(theme) + "|"
                        + join(new ArrayList<String>(theme.codes), ","));
            }
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar cannot write auto theme config: " + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void writeReports(String dateStamp, List<Article> articles, List<HotTheme> themes) {
        File dir = new File(DAILY_SNAPSHOT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        writeCsvReport(new File(dir, "market_theme_radar_" + safeDate(dateStamp) + ".csv"), themes);
        writeJsonReport(new File(dir, "market_theme_radar_" + safeDate(dateStamp) + ".json"), articles, themes);
    }

    private void writeCsvReport(File file, List<HotTheme> themes) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8)));
            writer.write('\uFEFF');
            writer.println("theme,score,source_count,article_count,codes,sources,evidence_titles");
            for (HotTheme theme : themes) {
                writer.println(csv(theme.name) + "," + theme.score + "," + theme.sources.size() + ","
                        + theme.titles.size() + "," + csv(join(new ArrayList<String>(theme.codes), "；")) + ","
                        + csv(join(new ArrayList<String>(theme.sources), "；")) + ","
                        + csv(join(limit(new ArrayList<String>(theme.titles), 6), "；")));
            }
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar cannot write csv report: " + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeJsonReport(File file, List<Article> articles, List<HotTheme> themes) {
        PrintWriter writer = null;
        try {
            JSONObject root = new JSONObject();
            root.put("generatedAt", Long.valueOf(System.currentTimeMillis()));
            root.put("articleCount", Integer.valueOf(articles.size()));
            JSONArray themeArray = new JSONArray();
            for (HotTheme theme : themes) {
                themeArray.add(theme.toJson());
            }
            root.put("themes", themeArray);
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8)));
            writer.print(root.toJSONString());
        } catch (Exception ex) {
            System.out.println("MarketThemeRadar cannot write json report: " + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private List<ThemeRule> loadManualThemes() {
        List<ThemeRule> rules = new ArrayList<ThemeRule>();
        File file = new File(THEME_CONFIG_PATH);
        if (!file.exists()) {
            return rules;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|", -1);
                if (parts.length < 2) {
                    continue;
                }
                rules.add(new ThemeRule(safe(parts[0]), splitCsv(parts[1])));
            }
        } catch (Exception ex) {
            return new ArrayList<ThemeRule>();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
        return rules;
    }

    private Map<String, TaiwanStockVO> buildStockByCode(List<TaiwanStockVO> stocks) {
        Map<String, TaiwanStockVO> byCode = new HashMap<String, TaiwanStockVO>();
        for (TaiwanStockVO stock : stocks) {
            if (stock != null && safe(stock.getCode()).length() > 0) {
                byCode.put(stock.getCode(), stock);
            }
        }
        return byCode;
    }

    private List<StockNameEntry> buildStockNameEntries(List<TaiwanStockVO> stocks) {
        List<StockNameEntry> entries = new ArrayList<StockNameEntry>();
        for (TaiwanStockVO stock : stocks) {
            if (stock == null || safe(stock.getName()).length() < 2) {
                continue;
            }
            entries.add(new StockNameEntry(stock.getCode(), stock.getName()));
        }
        Collections.sort(entries, new Comparator<StockNameEntry>() {
            public int compare(StockNameEntry left, StockNameEntry right) {
                return Integer.compare(right.name.length(), left.name.length());
            }
        });
        return entries;
    }

    private List<Article> distinctArticles(List<Article> articles) {
        List<Article> distinct = new ArrayList<Article>();
        Set<String> seen = new HashSet<String>();
        for (Article article : articles) {
            String key = normalizeTerm(article.title);
            if (key.length() == 0 || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            distinct.add(article);
        }
        return distinct;
    }

    private boolean looksLikeFinanceTitle(String title) {
        return containsAny(title, list("股", "台股", "營收", "法人", "半導體", "AI", "電子", "產業", "公司", "財報",
                "訂單", "漲", "跌", "獲利", "市場"));
    }

    private boolean isCnyesNewsUrl(String url) {
        String value = safe(url).toLowerCase(Locale.ENGLISH);
        return value.startsWith("https://anuenews.cnyes.com/news/")
                || value.startsWith("https://news.cnyes.com/news/");
    }

    private List<String> splitCsv(String text) {
        List<String> values = new ArrayList<String>();
        if (text == null) {
            return values;
        }
        for (String token : text.split(",")) {
            String value = safe(token);
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private String csvKeywords(HotTheme theme) {
        LinkedHashSet<String> keywords = new LinkedHashSet<String>();
        keywords.add(theme.name);
        keywords.addAll(extractHotTerms(join(limit(new ArrayList<String>(theme.titles), 4), " ")));
        return join(new ArrayList<String>(keywords), ",");
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (contains(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String text, String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return false;
        }
        return safe(text).toLowerCase(Locale.ENGLISH).contains(keyword.toLowerCase(Locale.ENGLISH));
    }

    private boolean isMostlyNumber(String text) {
        int numberCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                numberCount++;
            }
        }
        return numberCount > 0 && numberCount * 2 >= text.length();
    }

    private boolean isStockCodeLike(String text) {
        return text.matches("\\d{4}(\\.TW|\\.TWO)?");
    }

    private String normalizeTerm(String text) {
        return safe(text).replaceAll("[，。！？、；：「」『』\\[\\]()（）【】]", "").trim();
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeDate(String dateStamp) {
        String value = safe(dateStamp);
        return value.length() == 0 ? "latest" : value;
    }

    private String csv(String value) {
        String text = safe(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private String csvField(String value) {
        return safe(value).replace("|", " ").replace(",", " ");
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private List<String> limit(List<String> values, int max) {
        if (values.size() <= max) {
            return values;
        }
        return new ArrayList<String>(values.subList(0, max));
    }

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private static int parseIntProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Set<String> buildStopWords() {
        Set<String> words = new HashSet<String>();
        words.addAll(list("今日", "最新", "新聞", "台股", "市場", "法人", "盤中", "盤後", "個股", "公司", "股價", "漲跌",
                "今年", "明年", "營收", "獲利", "投資", "指出", "表示", "持續", "看好", "受惠", "億元", "萬元", "董事會",
                "公告", "交易", "股票", "族群", "產業", "概念股", "熱門", "焦點", "台北", "中央社", "鉅亨", "MoneyDJ",
                "Yahoo", "TW", "TWO"));
        return words;
    }

    public static class Report {
        public final List<Article> articles;
        public final List<HotTheme> themes;

        public Report(List<Article> articles, List<HotTheme> themes) {
            this.articles = articles == null ? new ArrayList<Article>() : articles;
            this.themes = themes == null ? new ArrayList<HotTheme>() : themes;
        }

        @SuppressWarnings("unchecked")
        public JSONObject toJson() {
            JSONObject root = new JSONObject();
            root.put("articleCount", Integer.valueOf(articles.size()));
            JSONArray themeArray = new JSONArray();
            for (HotTheme theme : themes) {
                themeArray.add(theme.toJson());
            }
            root.put("themes", themeArray);
            return root;
        }
    }

    public static class Article {
        public final String source;
        public final String title;
        public final String url;

        public Article(String source, String title, String url) {
            this.source = source == null ? "" : source;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }
    }

    public static class HotTheme {
        public final String name;
        public int score = 0;
        public final LinkedHashSet<String> sources = new LinkedHashSet<String>();
        public final LinkedHashSet<String> titles = new LinkedHashSet<String>();
        public final LinkedHashSet<String> urls = new LinkedHashSet<String>();
        public final LinkedHashSet<String> codes = new LinkedHashSet<String>();

        public HotTheme(String name) {
            this.name = name == null ? "" : name;
        }

        @SuppressWarnings("unchecked")
        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            object.put("name", name);
            object.put("score", Integer.valueOf(score));
            object.put("sourceCount", Integer.valueOf(sources.size()));
            object.put("articleCount", Integer.valueOf(titles.size()));
            object.put("sources", new JSONArray());
            ((JSONArray) object.get("sources")).addAll(sources);
            object.put("codes", new JSONArray());
            ((JSONArray) object.get("codes")).addAll(codes);
            object.put("titles", new JSONArray());
            ((JSONArray) object.get("titles")).addAll(titles);
            return object;
        }
    }

    private static class ThemeRule {
        private final String name;
        private final List<String> keywords;

        private ThemeRule(String name, List<String> keywords) {
            this.name = name == null ? "" : name;
            this.keywords = keywords == null ? new ArrayList<String>() : keywords;
        }
    }

    private static class StockNameEntry {
        private final String code;
        private final String name;

        private StockNameEntry(String code, String name) {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
        }
    }
}
