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
import stock.vo.NewsSignalVO;
import stock.vo.TaiwanStockVO;

public class ThemeBasketRepository {

    private static final String DEFAULT_CONFIG_PATH = "config/theme_baskets.csv";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Map<String, List<String>> THEME_CONTEXT_HINTS = buildThemeContextHints();

    private volatile List<ThemeBasket> cached = new ArrayList<ThemeBasket>();

    public ThemeBasketRepository() {
        reload();
    }

    public synchronized void reload() {
        cached = loadBaskets();
    }

    public ThemeMatch match(TaiwanStockVO stock, String industry, NewsSignalVO newsSignal) {
        String code = stock == null ? "" : safe(stock.getCode());
        String stockName = stock == null ? "" : safe(stock.getName());
        String industryText = safe(industry);
        String newsText = newsSignal == null ? "" : safe(newsSignal.getSummaryText());

        Set<String> matchedNames = new LinkedHashSet<String>();
        double bestScore = 50D;
        String primaryTheme = "";

        for (ThemeBasket basket : cached) {
            double score = 50D;
            int keywordHits = countKeywordHits(basket.keywords, stockName + " " + industryText + " " + newsText);
            boolean contextAligned = isThemeContextAligned(basket.name, stockName, industryText, newsText, keywordHits);
            if (basket.codes.contains(code) && contextAligned) {
                score += 22D;
            }

            if (keywordHits > 0) {
                score += Math.min(24D, keywordHits * 8D);
            }

            int headlineHits = 0;
            if (newsSignal != null) {
                for (String headline : newsSignal.getHeadlines()) {
                    if (containsAny(headline, basket.keywords)) {
                        headlineHits++;
                    }
                }
            }
            if (headlineHits > 0) {
                score += Math.min(18D, headlineHits * 6D);
            }

            score = NumberParser.clamp(score, 0D, 100D);
            if (score >= 60D) {
                matchedNames.add(basket.name);
            }
            if (score > bestScore) {
                bestScore = score;
                primaryTheme = basket.name;
            }
        }

        if (matchedNames.isEmpty() && primaryTheme.length() == 0) {
            primaryTheme = "一般";
        }
        return new ThemeMatch(primaryTheme, join(matchedNames, "、"), NumberParser.clamp(bestScore, 0D, 100D));
    }

    private List<ThemeBasket> loadBaskets() {
        List<ThemeBasket> baskets = new ArrayList<ThemeBasket>();
        File file = new File(DEFAULT_CONFIG_PATH);
        if (!file.exists()) {
            return baskets;
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

                ThemeBasket basket = new ThemeBasket();
                basket.name = safe(parts[0]);
                basket.keywords = splitCsv(parts[1]);
                basket.codes = splitCsv(parts[2]);
                if (basket.name.length() > 0) {
                    baskets.add(basket);
                }
            }
        } catch (Exception ex) {
            return new ArrayList<ThemeBasket>();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
        return baskets;
    }

    private List<String> splitCsv(String text) {
        List<String> values = new ArrayList<String>();
        if (text == null) {
            return values;
        }
        for (String token : text.split(",")) {
            String trimmed = safe(token);
            if (trimmed.length() > 0) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private int countKeywordHits(List<String> keywords, String text) {
        int hits = 0;
        for (String keyword : keywords) {
            if (contains(text, keyword)) {
                hits++;
            }
        }
        return hits;
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
        String source = text == null ? "" : text.toLowerCase(Locale.ENGLISH);
        String target = keyword.toLowerCase(Locale.ENGLISH);
        return source.contains(target);
    }

    private boolean isThemeContextAligned(String themeName, String stockName, String industryText, String newsText,
            int keywordHits) {
        if (keywordHits > 0) {
            return true;
        }
        List<String> hints = THEME_CONTEXT_HINTS.get(themeName);
        if (hints == null || hints.isEmpty()) {
            return true;
        }
        String combined = (stockName == null ? "" : stockName) + " " + (industryText == null ? "" : industryText)
                + " " + (newsText == null ? "" : newsText);
        return containsAny(combined, hints);
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

    private String join(Set<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static class ThemeBasket {
        private String name = "";
        private List<String> keywords = new ArrayList<String>();
        private List<String> codes = new ArrayList<String>();
    }

    public static class ThemeMatch {
        public final String primaryTheme;
        public final String themeTags;
        public final double themeScore;

        public ThemeMatch(String primaryTheme, String themeTags, double themeScore) {
            this.primaryTheme = primaryTheme == null ? "" : primaryTheme;
            this.themeTags = themeTags == null ? "" : themeTags;
            this.themeScore = themeScore;
        }
    }
}
