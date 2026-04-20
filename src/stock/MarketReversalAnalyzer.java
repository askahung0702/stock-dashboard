package stock;

import java.util.ArrayList;
import java.util.List;

import stock.common.NumberParser;

public class MarketReversalAnalyzer {

    public MarketReversalSignal analyze(MarketRegime regime, MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        double score = 45D;
        List<String> reasons = new ArrayList<String>();

        if (breadth != null) {
            if (breadth.getBreadthDeteriorationDays() >= 1) {
                double deteriorationImpact = Math.min(24D, breadth.getBreadthDeteriorationDays() * 7D);
                score += deteriorationImpact;
                reasons.add("跌破 MA20 比例連續 " + breadth.getBreadthDeteriorationDays() + " 天惡化");
            }
            if (breadth.getAdr() < 0.95D) {
                score += Math.min(16D, (0.95D - breadth.getAdr()) * 55D);
                reasons.add("ADR 低於 0.95，盤面下跌家數偏多");
            } else if (breadth.getAdr() >= 1.1D) {
                score -= Math.min(12D, (breadth.getAdr() - 1.1D) * 35D);
            }
            if (breadth.getAboveMa20Pct() < 48D) {
                score += Math.min(18D, (48D - breadth.getAboveMa20Pct()) * 0.8D);
                reasons.add("站上 MA20 比例偏低");
            } else if (breadth.getAboveMa20Pct() >= 58D) {
                score -= Math.min(12D, (breadth.getAboveMa20Pct() - 58D) * 0.5D);
            }
            if (breadth.getBelowMa20Pct() >= 52D) {
                score += Math.min(12D, (breadth.getBelowMa20Pct() - 52D) * 0.5D + 4D);
                reasons.add("跌破 MA20 個股比例偏高");
            }
            if (breadth.getScoreUpPct() >= 45D) {
                score -= Math.min(8D, (breadth.getScoreUpPct() - 45D) * 0.25D);
            }
        }

        if (index != null && index.isAvailable()) {
            if (index.getMacdHistogram() < 0D) {
                score += Math.min(12D, Math.abs(index.getMacdHistogram()) * 4D + 4D);
                reasons.add("加權指數 MACD 柱體轉負");
            } else {
                score -= Math.min(8D, index.getMacdHistogram() * 3D);
            }
            if ("價跌量增".equals(index.getDivergenceLabel())) {
                score += 12D;
                reasons.add("指數出現價跌量增");
            } else if ("價漲量縮".equals(index.getDivergenceLabel())) {
                score += 6D;
                reasons.add("指數價漲量縮，推升力道不足");
            } else if ("放量推進".equals(index.getDivergenceLabel())) {
                score -= 6D;
            }
            if ("空頭慣性".equals(index.getTrendLabel())) {
                score += 12D;
                reasons.add("大盤仍處空頭慣性");
            } else if ("多頭慣性".equals(index.getTrendLabel())) {
                score -= 10D;
            }
        }

        if (regime != null) {
            if (regime == MarketRegime.PANIC_SELLOFF) {
                score += 18D;
                reasons.add("大盤處於恐慌殺盤");
            } else if (regime == MarketRegime.BEAR_CORRECTION) {
                score += 10D;
                reasons.add("大盤進入空頭修正");
            } else if (regime == MarketRegime.BULL_TREND) {
                score -= 12D;
            }
        }

        score = NumberParser.clamp(score, 0D, 100D);
        String label;
        if (score >= 70D) {
            label = "空方反轉風險高";
        } else if (score >= 55D) {
            label = "轉弱警戒";
        } else if (score >= 35D) {
            label = "震盪觀察";
        } else {
            label = "多頭延續";
        }

        boolean riskRising = score >= 55D;
        String reason = reasons.isEmpty() ? "目前未見明顯大盤反轉壓力，結構仍以延續或震盪為主。"
                : joinTopReasons(reasons, riskRising);
        return new MarketReversalSignal(score, label, reason, riskRising);
    }

    private String joinTopReasons(List<String> reasons, boolean riskRising) {
        StringBuilder sb = new StringBuilder();
        sb.append(riskRising ? "大盤轉弱訊號增加：" : "大盤仍以震盪為主，但需留意：");
        int limit = Math.min(3, reasons.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }
}
