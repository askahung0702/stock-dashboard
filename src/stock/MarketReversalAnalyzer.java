package stock;

import java.util.ArrayList;
import java.util.List;

import stock.common.NumberParser;

public class MarketReversalAnalyzer {

    public MarketReversalSignal analyze(MarketRegime regime, MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        double score = 30D;
        List<String> reasons = new ArrayList<String>();

        if (breadth != null) {
            double breadthImpact = 0D;
            if (breadth.getBreadthDeteriorationDays() >= 1) {
                double deteriorationImpact = Math.min(14D, breadth.getBreadthDeteriorationDays() * 4D);
                breadthImpact += deteriorationImpact;
                reasons.add("跌破 MA20 比例連續 " + breadth.getBreadthDeteriorationDays() + " 天惡化");
            }
            if (breadth.getAdr() < 0.95D) {
                breadthImpact += Math.min(12D, (0.95D - breadth.getAdr()) * 35D);
                reasons.add("ADR 低於 0.95，盤面下跌家數偏多");
            } else if (breadth.getAdr() >= 1.1D) {
                score -= Math.min(12D, (breadth.getAdr() - 1.1D) * 35D);
            }
            if (breadth.getAboveMa20Pct() < 48D) {
                breadthImpact += Math.min(14D, (48D - breadth.getAboveMa20Pct()) * 0.45D);
                reasons.add("站上 MA20 比例偏低");
            } else if (breadth.getAboveMa20Pct() >= 58D) {
                score -= Math.min(12D, (breadth.getAboveMa20Pct() - 58D) * 0.5D);
            }
            // belowMa20Pct is the complement of aboveMa20Pct. Do not score it again.
            score += Math.min(30D, breadthImpact);
            if (breadth.getScoreUpPct() >= 45D) {
                score -= Math.min(8D, (breadth.getScoreUpPct() - 45D) * 0.25D);
            }
        }

        if (index != null && index.isAvailable()) {
            double indexImpact = 0D;
            if (index.getMacdHistogram() < 0D) {
                indexImpact += 8D;
                reasons.add("加權指數 MACD 柱體轉負");
            } else {
                score -= Math.min(8D, index.getMacdHistogram() * 3D);
            }
            if ("價跌量增".equals(index.getDivergenceLabel())) {
                indexImpact += 10D;
                reasons.add("指數出現價跌量增");
            } else if ("價漲量縮".equals(index.getDivergenceLabel())) {
                indexImpact += 5D;
                reasons.add("指數價漲量縮，推升力道不足");
            } else if ("放量推進".equals(index.getDivergenceLabel())) {
                score -= 6D;
            }
            if ("空頭慣性".equals(index.getTrendLabel())) {
                indexImpact += 9D;
                reasons.add("大盤仍處空頭慣性");
            } else if ("多頭慣性".equals(index.getTrendLabel())) {
                score -= 10D;
            }
            score += Math.min(22D, indexImpact);
        }

        if (regime != null) {
            if (regime == MarketRegime.PANIC_SELLOFF) {
                score += 15D;
                reasons.add("大盤處於恐慌殺盤");
            } else if (regime == MarketRegime.BEAR_CORRECTION) {
                score += 8D;
                reasons.add("大盤進入空頭修正");
            } else if (regime == MarketRegime.BULL_TREND) {
                score -= 12D;
            }
        }

        score = NumberParser.clamp(score, 0D, 100D);
        String label;
        if (score >= 75D) {
            label = "趨勢惡化：高";
        } else if (score >= 60D) {
            label = "趨勢惡化：警戒";
        } else if (score >= 35D) {
            label = "趨勢惡化：觀察";
        } else {
            label = "趨勢結構穩定";
        }

        boolean riskRising = score >= 60D;
        String reason = reasons.isEmpty() ? "目前未見明顯大盤趨勢惡化，結構仍以延續或震盪為主。"
                : joinTopReasons(reasons, riskRising);
        return new MarketReversalSignal(score, label, reason, riskRising);
    }

    private String joinTopReasons(List<String> reasons, boolean riskRising) {
        StringBuilder sb = new StringBuilder();
        sb.append(riskRising ? "大盤趨勢惡化訊號增加：" : "大盤仍以震盪為主，但需留意：");
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
