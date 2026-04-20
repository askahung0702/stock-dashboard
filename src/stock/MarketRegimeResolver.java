package stock;

import java.util.List;
import java.util.Map;

import stock.StockHistoryDatabase.Snapshot;

public class MarketRegimeResolver {

    public static MarketRegime resolve(List<stock.vo.StockAnalysisResultVO> results,
            Map<String, Snapshot> historicalSnapshots, String currentDate, double watchThreshold,
            double likelyThreshold) {
        if (results == null || results.isEmpty()) {
            return MarketRegime.RANGE_BOUND;
        }

        MarketBreadthAnalyzer breadthAnalyzer = new MarketBreadthAnalyzer();
        MarketBreadthSnapshot breadth = breadthAnalyzer.analyzeResults(results, historicalSnapshots, currentDate,
                watchThreshold, likelyThreshold);

        double regimeScore = breadth.getAboveMa20Pct() * 0.24D + breadth.getAdr() * 18D + breadth.getScoreUpPct() * 0.18D
                + breadth.getQualifiedPct() * 0.15D + breadth.getBuyReadyPct() * 0.10D
                + breadth.getAverageSelectionScore() * 0.13D
                - Math.max(0D, breadth.getAverageNewsRiskScore() - 55D) * 0.30D
                - Math.max(0D, breadth.getBreadthDeteriorationDays() - 1D) * 4D;

        if (breadth.getAboveMa20Pct() < 18D || breadth.getAdr() < 0.62D || regimeScore < 35D) {
            return MarketRegime.PANIC_SELLOFF;
        }
        if (breadth.getAboveMa20Pct() < 32D || breadth.getAdr() < 0.9D || breadth.getScoreUpPct() < 32D
                || breadth.getBreadthDeteriorationDays() >= 3 || breadth.getAverageSelectionScore() < 58D) {
            return MarketRegime.BEAR_CORRECTION;
        }
        if (breadth.getAboveMa20Pct() >= 58D && breadth.getAdr() >= 1.15D && breadth.getScoreUpPct() >= 46D
                && breadth.getLikelyPct() >= 6D && breadth.getAverageSelectionScore() >= 66D) {
            return MarketRegime.BULL_TREND;
        }
        return MarketRegime.RANGE_BOUND;
    }
}
