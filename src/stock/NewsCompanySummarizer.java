package stock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import stock.common.NumberParser;
import stock.vo.StockAnalysisResultVO;

public class NewsCompanySummarizer {

    public Summary summarize(StockAnalysisResultVO result) {
        Summary summary = new Summary();
        summary.companySummary = buildCompanySummary(result);
        summary.recentNewsBrief = buildRecentNewsBrief(result);
        summary.transformationHint = buildTransformationHint(result);
        summary.practicalAdvice = buildPracticalAdvice(result);
        summary.adviceConfidence = buildAdviceConfidence(result);
        return summary;
    }

    private String buildCompanySummary(StockAnalysisResultVO result) {
        List<String> parts = new ArrayList<String>();
        String industry = emptyIfBlank(result.getIndustry(), "未明確分類");
        parts.add("屬於" + industry + "。");

        List<String> themes = resolveThemes(result);
        if (!themes.isEmpty()) {
            parts.add("目前市場主要把它放在" + join(themes, "、") + "主軸下觀察。");
        }

        if (result.getAverageThreeMonthRevenueYoY() > 10D) {
            parts.add("近三月營收動能偏強。");
        } else if (result.getAverageThreeMonthRevenueYoY() > 0D) {
            parts.add("近三月營收維持正成長。");
        } else if (result.getAverageThreeMonthRevenueYoY() < -10D) {
            parts.add("近三月營收仍在修正。");
        }

        if (result.getPositiveEpsQuarters() >= 3 && result.getTrailingFourQuarterEps() > 0D) {
            parts.add("獲利體質目前仍屬正向。");
        } else if (result.getTurnaroundScore() >= 60D) {
            parts.add("現階段更像翻轉觀察而非成熟穩定獲利。");
        }
        return trimToLength(join(parts, " "), 140);
    }

    private String buildRecentNewsBrief(StockAnalysisResultVO result) {
        List<String> parts = new ArrayList<String>();
        if (result.getEventDirection() != null && result.getEventDirection().length() > 0) {
            if ("正向催化".equals(result.getEventDirection())) {
                parts.add("近期消息偏正向催化");
            } else if ("負向風險".equals(result.getEventDirection())) {
                parts.add("近期消息帶有風險");
            }
        }
        if (result.getEventTypeSummary() != null && result.getEventTypeSummary().length() > 0) {
            parts.add("重點在" + trimToLength(result.getEventTypeSummary(), 24));
        }
        String digest = emptyIfBlank(result.getNewsDigest(), result.getNewsSummary());
        if (digest.length() > 0) {
            parts.add(trimToLength(cleanupText(digest), 90));
        } else if (result.getNewsScore() >= 60D) {
            parts.add("近期新聞熱度偏高，但摘要內容有限");
        } else {
            parts.add("近期公開新聞量有限，仍以數據面判讀為主");
        }
        return trimToLength(join(parts, "；"), 140);
    }

    private String buildTransformationHint(StockAnalysisResultVO result) {
        boolean positiveTurnaround = isPositiveTurnaround(result);
        List<String> themes = resolveThemes(result);
        String turnaroundReason = cleanupText(result.getTurnaroundReason());
        if (positiveTurnaround) {
            String driver = !themes.isEmpty() ? join(themes, "、")
                    : emptyIfBlank(result.getTurnaroundLabel(), "新產品/新訂單");
            if (turnaroundReason.length() > 0 && !"尚未明確".equals(result.getTurnaroundLabel())) {
                return trimToLength("有轉型或升級跡象，方向偏" + driver + "；" + turnaroundReason, 140);
            }
            return trimToLength("有轉型或升級跡象，方向偏" + driver + "，但仍需持續用營收與獲利驗證。", 140);
        }
        if (result.getOneOffRiskScore() >= 55D || containsAny(turnaroundReason, "一次性", "業外", "尚未明確")) {
            return trimToLength("目前未看到穩定轉型證據，較像事件或單次題材驅動；需再觀察營收、EPS 是否持續改善。", 140);
        }
        if (!themes.isEmpty()) {
            return trimToLength("目前較像原產業循環中搭配" + join(themes, "、")
                    + "題材加分，是否算真正轉型仍要看後續營收與毛利率能否跟上。", 140);
        }
        return "目前沒有足夠證據判定正在明確轉型，先以原本產業循環與技術面解讀為主。";
    }

    private String buildPracticalAdvice(StockAnalysisResultVO result) {
        List<String> parts = new ArrayList<String>();
        if (result.isHardExclude() || "暫不出手".equals(result.getPostCloseCategory()) || result.getSellSignalScore() >= 72D) {
            parts.add("先避開");
            if (result.getSellSignalLabel() != null && result.getSellSignalLabel().length() > 0) {
                parts.add("目前偏" + result.getSellSignalLabel());
            } else {
                parts.add("等待結構修復");
            }
        } else if (result.getBuyPointScore() >= 85D && result.isSelectionQualified()
                && ("平台突破".equals(result.getStructureLabel()) || "回踩承接".equals(result.getStructureLabel()))) {
            parts.add("可分批布局");
            parts.add("但仍要守好停損");
        } else if (result.getBuyPointScore() >= 75D && result.isSelectionQualified()) {
            parts.add("可列入觀察");
            parts.add("等量價再確認後介入");
        } else if (result.getSelectionScore() >= 72D) {
            parts.add("基本面與趨勢可留意");
            parts.add("較適合等拉回或重新站穩再介入");
        } else {
            parts.add("先放觀察");
            parts.add("等待營收、籌碼或技術面同步轉強");
        }

        if (result.getNewsRiskScore() > 65D || "負向風險".equals(result.getEventDirection())) {
            parts.add("消息面風險偏高");
        } else if ("追高風險".equals(result.getStructureLabel()) || result.getReturn20DayPct() > 22D) {
            parts.add("不宜追價");
        }

        if ("BEAR_CORRECTION".equals(result.getMarketRegime()) || "PANIC_SELLOFF".equals(result.getMarketRegime())
                || "空頭修正".equals(result.getMarketRegime()) || "恐慌急殺".equals(result.getMarketRegime())) {
            parts.add("目前大盤偏防守，部位宜縮小");
        } else if (result.isReducePositionSize()) {
            parts.add("波動偏大，部位需縮減");
        }

        return trimToLength(join(parts, "；"), 140);
    }

    private double buildAdviceConfidence(StockAnalysisResultVO result) {
        double confidence = result.getDataConfidence() * 0.48D + result.getQualityScore() * 0.12D
                + result.getStructureScore() * 0.12D + result.getRiskRewardScore() * 0.10D
                + result.getNewsSourceCredibilityScore() * 0.08D + result.getNewsFreshnessScore() * 0.05D
                + (result.isSelectionQualified() ? 5D : 0D);
        if (result.getNewsSourceCount() <= 0 && emptyIfBlank(result.getNewsDigest(), "").length() == 0) {
            confidence -= 6D;
        }
        if ("負向風險".equals(result.getEventDirection()) && result.getNewsRiskScore() >= 65D) {
            confidence -= 5D;
        }
        return NumberParser.clamp(confidence, 25D, 98D);
    }

    private boolean isPositiveTurnaround(StockAnalysisResultVO result) {
        String label = emptyIfBlank(result.getTurnaroundLabel(), "");
        if (containsAny(label, "高品質翻轉", "轉虧為盈", "業績翻轉", "業績成長")) {
            return true;
        }
        return result.getTurnaroundScore() >= 62D || result.getRevenueGrowthSignalScore() >= 68D
                || result.getEarningsTurnaroundSignalScore() >= 55D;
    }

    private List<String> resolveThemes(StockAnalysisResultVO result) {
        Set<String> themes = new LinkedHashSet<String>();
        String primaryTheme = emptyIfBlank(result.getPrimaryTheme(), "");
        if (primaryTheme.length() > 0 && !"一般".equals(primaryTheme)) {
            themes.add(primaryTheme);
        }
        String themeTags = emptyIfBlank(result.getThemeTags(), "");
        if (themeTags.length() > 0) {
            String[] tokens = themeTags.split("[、,;；]");
            for (String token : tokens) {
                String trimmed = emptyIfBlank(token, "");
                if (trimmed.length() > 0 && !"一般".equals(trimmed)) {
                    themes.add(trimmed);
                }
            }
        }
        return new ArrayList<String>(themes);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.length() == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && keyword.length() > 0 && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String cleanupText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').replace("  ", " ").trim();
    }

    private String trimToLength(String text, int maxLength) {
        String cleaned = cleanupText(text);
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String emptyIfBlank(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
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

    public static class Summary {
        public String companySummary = "";
        public String recentNewsBrief = "";
        public String transformationHint = "";
        public String practicalAdvice = "";
        public double adviceConfidence;
    }
}
