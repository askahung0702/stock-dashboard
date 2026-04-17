package stock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import stock.common.NumberParser;
import stock.vo.StockAnalysisResultVO;

public class NewsThemeReferenceAnalyzer {

    private static final String CONFIG_PATH = "config/theme_baskets.csv";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Map<String, List<String>> THEME_CONTEXT_HINTS = buildThemeContextHints();

    private final List<ThemeRule> themeRules;
    private final List<IndustryRule> industryRules;

    public NewsThemeReferenceAnalyzer() {
        this.themeRules = loadThemeRules();
        this.industryRules = buildIndustryRules();
    }

    public void apply(List<StockAnalysisResultVO> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (StockAnalysisResultVO result : results) {
            ReferenceInsight insight = analyze(result);
            result.setThemeReferenceScore(insight.referenceScore);
            result.setThemeReferenceTheme(insight.referenceTheme);
            result.setThemeReferenceIndustry(insight.referenceIndustry);
            result.setThemeReferenceReason(insight.reason);
            applyReferenceToTheme(result, insight);
        }
    }

    public ReferenceInsight analyze(StockAnalysisResultVO result) {
        if (result == null || result.getStock() == null) {
            return ReferenceInsight.empty();
        }

        String code = safe(result.getStock().getCode());
        String stockName = safe(result.getStock().getName());
        String currentIndustry = safe(result.getIndustry());
        String newsSummary = safe(result.getNewsSummary());
        String identityText = stockName + " " + currentIndustry;

        String bestTheme = "";
        double bestThemeScore = 0D;
        String bestThemeReason = "";
        for (ThemeRule rule : themeRules) {
            double score = 0D;
            List<String> evidence = new ArrayList<String>();
            boolean basketHit = rule.codes.contains(code);
            int identityHits = countHits(identityText, rule.keywords);
            int newsHits = countHits(newsSummary, rule.keywords);
            boolean contextAligned = isThemeContextAligned(rule.name, stockName, currentIndustry, newsSummary,
                    identityHits, newsHits);
            boolean strongNewsEvidence = newsHits >= 2;
            boolean mixedEvidence = newsHits >= 1 && identityHits >= 1;
            boolean strongIdentityEvidence = identityHits >= 2;
            if ((!basketHit || !contextAligned) && !strongNewsEvidence && !mixedEvidence && !strongIdentityEvidence) {
                continue;
            }

            if (basketHit && contextAligned) {
                score += 24D;
                evidence.add("題材籃子成分股");
            }
            if (identityHits > 0) {
                score += Math.min(14D, identityHits * 7D);
                evidence.add("名稱/產業命中 " + joinMatched(identityText, rule.keywords));
            }
            if (strongNewsEvidence) {
                score += Math.min(28D, newsHits * 10D);
                evidence.add("新聞命中 " + joinMatched(newsSummary, rule.keywords));
            } else if (mixedEvidence) {
                score += 8D;
                evidence.add("新聞輔助確認 " + joinMatched(newsSummary, rule.keywords));
            }

            double normalized = score <= 0D ? 0D : NumberParser.clamp(28D + score, 0D, 100D);
            if (normalized > bestThemeScore) {
                bestThemeScore = normalized;
                bestTheme = rule.name;
                bestThemeReason = join(evidence, "；");
            }
        }

        String bestIndustry = "";
        double bestIndustryScore = 0D;
        String bestIndustryReason = "";
        for (IndustryRule rule : industryRules) {
            int industryHits = countHits(currentIndustry, rule.keywords);
            int newsHits = countHits(newsSummary, rule.keywords);
            boolean strongIndustryNews = newsHits >= 2;
            boolean mixedIndustryEvidence = newsHits >= 1 && industryHits >= 1;
            boolean directIndustryEvidence = industryHits >= 2;
            if (!strongIndustryNews && !mixedIndustryEvidence && !directIndustryEvidence) {
                continue;
            }
            double score = NumberParser.clamp(30D + industryHits * 10D + newsHits * 12D, 0D, 100D);
            List<String> evidence = new ArrayList<String>();
            if (industryHits > 0) {
                evidence.add("產業欄位命中 " + joinMatched(currentIndustry, rule.keywords));
            }
            if (newsHits > 0) {
                evidence.add("新聞命中 " + joinMatched(newsSummary, rule.keywords));
            }
            if (score > bestIndustryScore) {
                bestIndustryScore = score;
                bestIndustry = rule.name;
                bestIndustryReason = join(evidence, "；");
            }
        }

        if (bestThemeScore <= 0D && bestIndustryScore <= 0D) {
            return ReferenceInsight.empty();
        }

        List<String> reasons = new ArrayList<String>();
        if (bestThemeScore > 0D) {
            reasons.add("新聞題材推論 " + bestTheme + " " + format(bestThemeScore) + " 分"
                    + (bestThemeReason.length() > 0 ? "（" + bestThemeReason + "）" : ""));
        }
        if (bestIndustryScore > 0D) {
            reasons.add("產業參考 " + bestIndustry + " " + format(bestIndustryScore) + " 分"
                    + (bestIndustryReason.length() > 0 ? "（" + bestIndustryReason + "）" : ""));
        }

        return new ReferenceInsight(bestTheme, bestThemeScore, bestIndustry, join(reasons, "；"));
    }

    private void applyReferenceToTheme(StockAnalysisResultVO result, ReferenceInsight insight) {
        if (result == null || insight.referenceTheme.length() == 0 || insight.referenceScore < 68D) {
            return;
        }

        double originalThemeScore = result.getThemeScore();
        String originalTheme = safe(result.getPrimaryTheme());
        double boost = Math.min(14D, Math.max(0D, insight.referenceScore - 55D) * 0.45D);
        double supplementedScore = NumberParser.clamp(Math.max(originalThemeScore, originalThemeScore + boost), 0D,
                100D);
        result.setThemeScore(supplementedScore);

        if (originalTheme.length() == 0 || "一般".equals(originalTheme) || originalThemeScore <= 58D) {
            result.setPrimaryTheme(insight.referenceTheme);
        }
        result.setThemeTags(mergeTags(result.getThemeTags(), insight.referenceTheme));
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
                if (parts.length < 3) {
                    continue;
                }
                String name = safe(parts[0]);
                if (name.length() == 0) {
                    continue;
                }
                List<String> keywords = splitCsv(parts[1]);
                keywords.addAll(enhancedThemeKeywords(name));
                rules.add(new ThemeRule(name, distinct(keywords), splitCsv(parts[2])));
            }
        } catch (Exception ignored) {
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
        if ("矽光子".equals(theme)) {
            return Arrays.asList("cpo", "矽晶光", "光收發", "光纖", "光主動元件", "矽基光", "光通訊模組");
        }
        if ("AI".equals(theme)) {
            return Arrays.asList("ai pc", "ai伺服器", "gb200", "資料中心", "加速器", "訓練", "推論");
        }
        if ("低軌衛星".equals(theme)) {
            return Arrays.asList("leo", "衛星通訊", "衛星地面設備", "太空通訊");
        }
        if ("CoWoS".equals(theme)) {
            return Arrays.asList("先進封裝", "cowos", "扇出型封裝", "封裝測試");
        }
        if ("散熱".equals(theme)) {
            return Arrays.asList("液冷", "散熱模組", "熱管理", "均熱片");
        }
        if ("BBU".equals(theme)) {
            return Arrays.asList("備援電池", "鋰電", "儲能櫃", "電源備援");
        }
        if ("機器人".equals(theme)) {
            return Arrays.asList("人形機器人", "自動化設備", "協作機器人");
        }
        if ("重電".equals(theme)) {
            return Arrays.asList("電網", "變壓器", "配電盤", "輸配電");
        }
        if ("軍工".equals(theme)) {
            return Arrays.asList("航太", "軍規", "無人載具", "國防");
        }
        return new ArrayList<String>();
    }

    private boolean isThemeContextAligned(String theme, String stockName, String currentIndustry, String newsSummary,
            int identityHits, int newsHits) {
        if (identityHits > 0 || newsHits > 0) {
            return true;
        }
        List<String> hints = THEME_CONTEXT_HINTS.get(theme);
        if (hints == null || hints.isEmpty()) {
            return true;
        }
        String combined = safe(stockName) + " " + safe(currentIndustry) + " " + safe(newsSummary);
        return countHits(combined, hints) > 0;
    }

    private static Map<String, List<String>> buildThemeContextHints() {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        map.put("矽光子", Arrays.asList("光", "通訊", "網通", "半導體", "電子", "cpo", "光收發", "光模組", "光纖"));
        map.put("AI", Arrays.asList("電子", "半導體", "網通", "伺服器", "電腦", "資料中心", "軟體", "雲端"));
        map.put("低軌衛星", Arrays.asList("通訊", "網通", "電子", "航太", "衛星", "太空", "雷達"));
        map.put("CoWoS", Arrays.asList("半導體", "封裝", "電子", "測試", "晶圓"));
        map.put("散熱", Arrays.asList("電子", "電腦", "伺服器", "散熱", "熱"));
        map.put("BBU", Arrays.asList("電池", "電子", "電源", "儲能", "伺服器"));
        map.put("機器人", Arrays.asList("機械", "電機", "電子", "自動化", "感測"));
        map.put("重電", Arrays.asList("電機", "電力", "能源", "重電", "電網", "配電"));
        map.put("軍工", Arrays.asList("航太", "軍工", "國防", "機械", "電子"));
        return Collections.unmodifiableMap(map);
    }

    private List<IndustryRule> buildIndustryRules() {
        List<IndustryRule> rules = new ArrayList<IndustryRule>();
        rules.add(new IndustryRule("矽光子/光通訊", Arrays.asList("矽光子", "cpo", "光通訊", "光模組", "光收發", "矽晶光")));
        rules.add(new IndustryRule("AI伺服器/資料中心", Arrays.asList("ai", "伺服器", "資料中心", "gpu", "asic", "加速器")));
        rules.add(new IndustryRule("衛星通訊", Arrays.asList("低軌", "衛星", "leo", "太空", "衛星通訊")));
        rules.add(new IndustryRule("先進封裝", Arrays.asList("cowos", "先進封裝", "封裝", "chiplet")));
        rules.add(new IndustryRule("散熱", Arrays.asList("散熱", "液冷", "熱管理", "均熱板", "熱管")));
        rules.add(new IndustryRule("電池/儲能", Arrays.asList("bbu", "備援電池", "鋰電池", "儲能")));
        rules.add(new IndustryRule("機器人/自動化", Arrays.asList("機器人", "自動化", "協作機器人", "人形機器人")));
        rules.add(new IndustryRule("重電/電網", Arrays.asList("重電", "電網", "變壓器", "配電")));
        rules.add(new IndustryRule("軍工/航太", Arrays.asList("軍工", "國防", "航太", "雷達", "無人機")));
        return rules;
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

    private String mergeTags(String current, String extra) {
        LinkedHashSet<String> tokens = new LinkedHashSet<String>();
        addTokens(tokens, current);
        addTokens(tokens, extra);
        return join(new ArrayList<String>(tokens), "、");
    }

    private void addTokens(Set<String> tokens, String text) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        for (String token : text.split("[、,;；]")) {
            String trimmed = safe(token);
            if (trimmed.length() > 0) {
                tokens.add(trimmed);
            }
        }
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

    private String format(double value) {
        return String.format("%.2f", Double.valueOf(value));
    }

    private static class ThemeRule {
        private final String name;
        private final List<String> keywords;
        private final List<String> codes;

        private ThemeRule(String name, List<String> keywords, List<String> codes) {
            this.name = name;
            this.keywords = keywords;
            this.codes = codes;
        }
    }

    private static class IndustryRule {
        private final String name;
        private final List<String> keywords;

        private IndustryRule(String name, List<String> keywords) {
            this.name = name;
            this.keywords = keywords;
        }
    }

    public static class ReferenceInsight {
        public final String referenceTheme;
        public final double referenceScore;
        public final String referenceIndustry;
        public final String reason;

        public ReferenceInsight(String referenceTheme, double referenceScore, String referenceIndustry, String reason) {
            this.referenceTheme = referenceTheme == null ? "" : referenceTheme;
            this.referenceScore = referenceScore;
            this.referenceIndustry = referenceIndustry == null ? "" : referenceIndustry;
            this.reason = reason == null ? "" : reason;
        }

        public static ReferenceInsight empty() {
            return new ReferenceInsight("", 0D, "", "");
        }
    }
}
