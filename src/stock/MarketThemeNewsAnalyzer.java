package stock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.StockAnalysisResultVO;

public class MarketThemeNewsAnalyzer {

    private static final String MARKET_URL = "https://tw.stock.yahoo.com/tw-market";
    private static final String CONFIG_PATH = "config/theme_baskets.csv";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_ARTICLES = parseIntProperty("stock.marketTheme.maxArticles", 8);
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{4})(?:\\.(?:TW|TWO))?(?!\\d)");

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final List<ThemeRule> themeRules;

    public MarketThemeNewsAnalyzer() {
        this.themeRules = loadThemeRules();
    }

    public ReferenceBundle analyze(List<StockAnalysisResultVO> results) {
        if (results == null || results.isEmpty()) {
            return ReferenceBundle.empty();
        }

        Map<String, StockAnalysisResultVO> resultsByCode = new LinkedHashMap<String, StockAnalysisResultVO>();
        List<StockNameEntry> stockNames = new ArrayList<StockNameEntry>();
        for (StockAnalysisResultVO result : results) {
            if (result == null || result.getStock() == null) {
                continue;
            }
            String code = safe(result.getStock().getCode());
            if (code.length() == 0) {
                continue;
            }
            resultsByCode.put(code, result);
            String name = safe(result.getStock().getName());
            if (name.length() >= 2) {
                stockNames.add(new StockNameEntry(code, name));
            }
            result.setMarketThemeReferenceScore(0D);
            result.setMarketThemeReferenceTheme("");
            result.setMarketThemeReferenceReason("");
        }
        Collections.sort(stockNames, new Comparator<StockNameEntry>() {
            public int compare(StockNameEntry left, StockNameEntry right) {
                int lengthCompare = Integer.compare(right.name.length(), left.name.length());
                if (lengthCompare != 0) {
                    return lengthCompare;
                }
                return left.name.compareTo(right.name);
            }
        });

        List<ArticleReference> articles = new ArrayList<ArticleReference>();
        try {
            articles = fetchMarketArticles(resultsByCode, stockNames);
        } catch (Exception ex) {
            return ReferenceBundle.empty();
        }

        Map<String, CandidateReference> candidates = aggregateCandidates(articles, resultsByCode);
        applyResults(resultsByCode, candidates);
        return new ReferenceBundle(articles, new ArrayList<CandidateReference>(candidates.values()));
    }

    private List<ArticleReference> fetchMarketArticles(Map<String, StockAnalysisResultVO> resultsByCode,
            List<StockNameEntry> stockNames) throws Exception {
        String marketHtml = fetcher.fetchPageHtml(MARKET_URL);
        Document marketDocument = Jsoup.parse(marketHtml, MARKET_URL);
        List<String> urls = extractArticleUrls(marketDocument);
        List<ArticleReference> articles = new ArrayList<ArticleReference>();
        for (String url : urls) {
            ArticleReference article = analyzeArticle(url, resultsByCode, stockNames);
            if (article != null) {
                articles.add(article);
            }
        }
        return articles;
    }

    private List<String> extractArticleUrls(Document marketDocument) {
        LinkedHashSet<String> urls = new LinkedHashSet<String>();
        Elements anchors = marketDocument.select("a[href]");
        for (Element anchor : anchors) {
            String url = safe(anchor.absUrl("href"));
            if (url.length() == 0) {
                url = safe(anchor.attr("href"));
            }
            if (!isMarketNewsUrl(url)) {
                continue;
            }
            String text = safe(anchor.text());
            if (text.length() < 12 && !url.endsWith(".html")) {
                continue;
            }
            urls.add(url);
            if (urls.size() >= MAX_ARTICLES) {
                break;
            }
        }
        return new ArrayList<String>(urls);
    }

    private ArticleReference analyzeArticle(String url, Map<String, StockAnalysisResultVO> resultsByCode,
            List<StockNameEntry> stockNames) {
        try {
            String articleHtml = fetcher.fetchPageHtml(url);
            Document document = Jsoup.parse(articleHtml, url);
            String title = resolveArticleTitle(document);
            String body = resolveArticleBody(document);
            ThemeEvidence themeEvidence = resolveThemeEvidence(title, body);
            if (themeEvidence.score < 60D || themeEvidence.theme.length() == 0) {
                return null;
            }

            Map<String, Mention> mentions = resolveMentions(title, body, resultsByCode, stockNames);
            if (mentions.isEmpty()) {
                return null;
            }

            String publishedHint = resolvePublishedHint(document);
            List<Mention> orderedMentions = new ArrayList<Mention>(mentions.values());
            Collections.sort(orderedMentions, new Comparator<Mention>() {
                public int compare(Mention left, Mention right) {
                    int mentionCompare = Integer.compare(right.weight, left.weight);
                    if (mentionCompare != 0) {
                        return mentionCompare;
                    }
                    return left.code.compareTo(right.code);
                }
            });
            return new ArticleReference(url, title, publishedHint, themeEvidence.theme, themeEvidence.score,
                    themeEvidence.reason, orderedMentions);
        } catch (Exception ex) {
            return null;
        }
    }

    private ThemeEvidence resolveThemeEvidence(String title, String body) {
        String titleText = safe(title);
        String bodyText = safe(body);
        String combined = titleText + " " + bodyText;
        ThemeEvidence best = ThemeEvidence.empty();
        for (ThemeRule rule : themeRules) {
            int titleHits = countHits(titleText, rule.keywords);
            int bodyHits = countHits(combined, rule.keywords);
            boolean themeNameHit = contains(combined, rule.name);
            if (!themeNameHit && titleHits <= 0 && bodyHits <= 1) {
                continue;
            }
            double score = 0D;
            List<String> evidence = new ArrayList<String>();
            if (themeNameHit) {
                score += 22D;
                evidence.add("題材名命中");
            }
            if (titleHits > 0) {
                score += Math.min(32D, titleHits * 14D);
                evidence.add("標題命中 " + joinMatched(titleText, rule.keywords));
            }
            if (bodyHits > 0) {
                score += Math.min(26D, bodyHits * 6D);
                evidence.add("內文命中 " + joinMatched(combined, rule.keywords));
            }
            score = NumberParser.clamp(28D + score, 0D, 100D);
            if (score > best.score) {
                best = new ThemeEvidence(rule.name, score, join(evidence, "；"));
            }
        }
        return best;
    }

    private Map<String, Mention> resolveMentions(String title, String body, Map<String, StockAnalysisResultVO> resultsByCode,
            List<StockNameEntry> stockNames) {
        String titleText = safe(title);
        String bodyText = safe(body);
        String combined = titleText + " " + bodyText;
        LinkedHashMap<String, Mention> mentions = new LinkedHashMap<String, Mention>();

        Matcher matcher = STOCK_CODE_PATTERN.matcher(combined);
        while (matcher.find()) {
            String code = safe(matcher.group(1));
            StockAnalysisResultVO result = resultsByCode.get(code);
            if (result == null) {
                continue;
            }
            Mention mention = mentions.computeIfAbsent(code,
                    key -> new Mention(code, result.getStock().getName(), result.getStock().getMarket(), result.getIndustry()));
            mention.weight += 2;
            mention.explicitCode = true;
        }

        for (StockNameEntry entry : stockNames) {
            boolean titleHit = contains(titleText, entry.name);
            boolean bodyHit = entry.name.length() >= 3 && contains(bodyText, entry.name);
            if (!titleHit && !bodyHit) {
                continue;
            }
            StockAnalysisResultVO result = resultsByCode.get(entry.code);
            if (result == null) {
                continue;
            }
            Mention mention = mentions.computeIfAbsent(entry.code,
                    key -> new Mention(entry.code, result.getStock().getName(), result.getStock().getMarket(), result.getIndustry()));
            mention.weight += titleHit ? 2 : 1;
            mention.nameHit = true;
        }

        return mentions;
    }

    private Map<String, CandidateReference> aggregateCandidates(List<ArticleReference> articles,
            Map<String, StockAnalysisResultVO> resultsByCode) {
        Map<String, CandidateReference> aggregated = new LinkedHashMap<String, CandidateReference>();
        for (ArticleReference article : articles) {
            for (Mention mention : article.mentions) {
                StockAnalysisResultVO result = resultsByCode.get(mention.code);
                if (result == null) {
                    continue;
                }
                CandidateReference candidate = aggregated.get(mention.code);
                if (candidate == null) {
                    candidate = new CandidateReference(mention.code, mention.name, mention.market, result.getIndustry());
                    aggregated.put(mention.code, candidate);
                }
                double articleCandidateScore = NumberParser.clamp(article.themeScore * 0.72D + mention.weight * 8D, 0D, 100D);
                if (articleCandidateScore > candidate.referenceScore) {
                    candidate.referenceScore = articleCandidateScore;
                    candidate.referenceTheme = article.theme;
                }
                candidate.articleCount++;
                candidate.mentionCount += mention.weight;
                candidate.articleTitles.add(article.title);
                candidate.articleUrls.add(article.url);
                if (candidate.reason.length() == 0) {
                    candidate.reason = article.title + "（" + article.theme + "）";
                } else if (!candidate.reason.contains(article.title)) {
                    candidate.reason += "；" + article.title + "（" + article.theme + "）";
                }
            }
        }

        List<CandidateReference> ordered = new ArrayList<CandidateReference>(aggregated.values());
        Collections.sort(ordered, new Comparator<CandidateReference>() {
            public int compare(CandidateReference left, CandidateReference right) {
                int scoreCompare = Double.compare(right.referenceScore, left.referenceScore);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return left.code.compareTo(right.code);
            }
        });
        LinkedHashMap<String, CandidateReference> result = new LinkedHashMap<String, CandidateReference>();
        for (CandidateReference candidate : ordered) {
            result.put(candidate.code, candidate);
        }
        return result;
    }

    private void applyResults(Map<String, StockAnalysisResultVO> resultsByCode, Map<String, CandidateReference> candidates) {
        for (CandidateReference candidate : candidates.values()) {
            StockAnalysisResultVO result = resultsByCode.get(candidate.code);
            if (result == null) {
                continue;
            }
            result.setMarketThemeReferenceScore(candidate.referenceScore);
            result.setMarketThemeReferenceTheme(candidate.referenceTheme);
            result.setMarketThemeReferenceReason(candidate.reason);
        }
    }

    private String resolveArticleTitle(Document document) {
        String title = safe(document.select("meta[property=og:title]").attr("content"));
        if (title.length() == 0) {
            title = safe(document.select("article h1").text());
        }
        if (title.length() == 0) {
            title = safe(document.select("h1").text());
        }
        if (title.length() == 0) {
            title = safe(document.title());
        }
        return title.replace(" - Yahoo奇摩股市", "").trim();
    }

    private String resolveArticleBody(Document document) {
        Elements paragraphs = document.select("article p, .caas-body p, main p, [class*=article] p");
        List<String> texts = new ArrayList<String>();
        for (Element paragraph : paragraphs) {
            String text = safe(paragraph.text());
            if (text.length() >= 18) {
                texts.add(text);
            }
        }
        String body = join(texts, " ");
        if (body.length() == 0) {
            body = safe(document.body() == null ? "" : document.body().text());
        }
        return body;
    }

    private String resolvePublishedHint(Document document) {
        String value = safe(document.select("meta[property=article:published_time]").attr("content"));
        if (value.length() == 0) {
            value = safe(document.select("time").attr("datetime"));
        }
        if (value.length() == 0) {
            value = safe(document.select("time").text());
        }
        return value;
    }

    private List<ThemeRule> loadThemeRules() {
        List<ThemeRule> rules = new ArrayList<ThemeRule>();
        File file = new File(CONFIG_PATH);
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
                String name = safe(parts[0]);
                if (name.length() == 0) {
                    continue;
                }
                List<String> keywords = splitCsv(parts[1]);
                keywords.addAll(enhancedThemeKeywords(name));
                rules.add(new ThemeRule(name, distinct(keywords)));
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

    private List<String> enhancedThemeKeywords(String theme) {
        List<String> keywords = new ArrayList<String>();
        if ("矽光子".equals(theme)) {
            keywords.add("cpo");
            keywords.add("矽晶光");
            keywords.add("光收發");
            keywords.add("共同封裝光學");
            keywords.add("光通訊模組");
        } else if ("AI".equals(theme)) {
            keywords.add("ai pc");
            keywords.add("ai伺服器");
            keywords.add("資料中心");
            keywords.add("推論");
            keywords.add("訓練");
        } else if ("低軌衛星".equals(theme)) {
            keywords.add("leo");
            keywords.add("衛星通訊");
            keywords.add("衛星地面設備");
            keywords.add("太空通訊");
        } else if ("CoWoS".equals(theme)) {
            keywords.add("先進封裝");
            keywords.add("扇出型封裝");
            keywords.add("封裝測試");
            keywords.add("chiplet");
        } else if ("散熱".equals(theme)) {
            keywords.add("液冷");
            keywords.add("散熱模組");
            keywords.add("熱管理");
            keywords.add("均熱片");
        }
        return keywords;
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

    private List<String> distinct(List<String> values) {
        Set<String> unique = new LinkedHashSet<String>();
        unique.addAll(values);
        return new ArrayList<String>(unique);
    }

    private int countHits(String text, List<String> keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (contains(text, keyword)) {
                hits++;
            }
        }
        return hits;
    }

    private String joinMatched(String text, List<String> keywords) {
        List<String> matched = new ArrayList<String>();
        for (String keyword : keywords) {
            if (contains(text, keyword) && !matched.contains(keyword)) {
                matched.add(keyword);
                if (matched.size() >= 4) {
                    break;
                }
            }
        }
        return join(matched, "、");
    }

    private boolean isMarketNewsUrl(String url) {
        return url.startsWith("https://tw.stock.yahoo.com/news/") && url.endsWith(".html");
    }

    private boolean contains(String text, String keyword) {
        if (keyword == null || keyword.length() == 0) {
            return false;
        }
        String source = text == null ? "" : text.toLowerCase(Locale.ENGLISH);
        String target = keyword.toLowerCase(Locale.ENGLISH);
        return source.contains(target);
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
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

    private static int parseIntProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    public static class ReferenceBundle {
        public final List<ArticleReference> articles;
        public final List<CandidateReference> candidates;

        public ReferenceBundle(List<ArticleReference> articles, List<CandidateReference> candidates) {
            this.articles = articles == null ? new ArrayList<ArticleReference>() : articles;
            this.candidates = candidates == null ? new ArrayList<CandidateReference>() : candidates;
        }

        public static ReferenceBundle empty() {
            return new ReferenceBundle(new ArrayList<ArticleReference>(), new ArrayList<CandidateReference>());
        }
    }

    public static class ArticleReference {
        public final String url;
        public final String title;
        public final String publishedHint;
        public final String theme;
        public final double themeScore;
        public final String evidence;
        public final List<Mention> mentions;

        public ArticleReference(String url, String title, String publishedHint, String theme, double themeScore,
                String evidence, List<Mention> mentions) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
            this.publishedHint = publishedHint == null ? "" : publishedHint;
            this.theme = theme == null ? "" : theme;
            this.themeScore = themeScore;
            this.evidence = evidence == null ? "" : evidence;
            this.mentions = mentions == null ? new ArrayList<Mention>() : mentions;
        }
    }

    public static class CandidateReference {
        public final String code;
        public final String name;
        public final String market;
        public final String industry;
        public String referenceTheme = "";
        public double referenceScore = 0D;
        public int articleCount = 0;
        public int mentionCount = 0;
        public final LinkedHashSet<String> articleTitles = new LinkedHashSet<String>();
        public final LinkedHashSet<String> articleUrls = new LinkedHashSet<String>();
        public String reason = "";

        public CandidateReference(String code, String name, String market, String industry) {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
            this.market = market == null ? "" : market;
            this.industry = industry == null ? "" : industry;
        }
    }

    public static class Mention {
        public final String code;
        public final String name;
        public final String market;
        public final String industry;
        public int weight = 0;
        public boolean explicitCode = false;
        public boolean nameHit = false;

        public Mention(String code, String name, String market, String industry) {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
            this.market = market == null ? "" : market;
            this.industry = industry == null ? "" : industry;
        }
    }

    private static class ThemeEvidence {
        private final String theme;
        private final double score;
        private final String reason;

        private ThemeEvidence(String theme, double score, String reason) {
            this.theme = theme == null ? "" : theme;
            this.score = score;
            this.reason = reason == null ? "" : reason;
        }

        private static ThemeEvidence empty() {
            return new ThemeEvidence("", 0D, "");
        }
    }

    private static class ThemeRule {
        private final String name;
        private final List<String> keywords;

        private ThemeRule(String name, List<String> keywords) {
            this.name = name;
            this.keywords = keywords;
        }
    }

    private static class StockNameEntry {
        private final String code;
        private final String name;

        private StockNameEntry(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
