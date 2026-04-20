package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;

public class MarketWeaknessAnalyzer {

    public MarketWeaknessReport analyze(Map<String, Snapshot> historicalSnapshots, String currentDate,
            List<SnapshotRow> currentRows, MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        boolean breadthDivergence = isBreadthDivergence(historicalSnapshots, currentDate, breadth, index);
        String breadthDivergenceReason = breadthDivergence
                ? "指數逼近近 20 日高點，但站上 MA20 的個股比例未同步擴散，疑似權值股撐盤。"
                : "目前未見明顯寬度背離。";

        boolean momentumBreakdown = isMomentumBreakdown(historicalSnapshots, currentDate, breadth, index);
        String momentumBreakdownReason = momentumBreakdown
                ? "指數跌破 MA20 或 MA20 斜率轉負，且 20 日平均 ADR 偏弱，動能防守線正在鬆動。"
                : "大盤動能尚未出現明確破位。";

        boolean newLowExpansion = isNewLowExpansion(historicalSnapshots, currentDate, currentRows);
        String newLowExpansionReason = newLowExpansion
                ? "近 3 個交易日創 60 日新低家數持續大於創 60 日新高家數，弱勢股正在擴散。"
                : "創高／創低家數未見明顯惡化。";

        boolean volatilityExpansion = isVolatilityExpansion(index);
        String volatilityExpansionReason = volatilityExpansion
                ? "指數量價背離或波動率放大，市場從穩定上攻轉向高波動震盪。"
                : "量價與波動率仍在可控範圍。";

        List<String> alerts = new ArrayList<String>();
        if (breadthDivergence) {
            alerts.add(breadthDivergenceReason);
        }
        if (momentumBreakdown) {
            alerts.add(momentumBreakdownReason);
        }
        if (newLowExpansion) {
            alerts.add(newLowExpansionReason);
        }
        if (volatilityExpansion) {
            alerts.add(volatilityExpansionReason);
        }
        if (alerts.isEmpty()) {
            alerts.add("目前未見結構性轉弱警示，盤勢仍以震盪或延續為主。");
        }

        return new MarketWeaknessReport(breadthDivergence, breadthDivergenceReason, momentumBreakdown,
                momentumBreakdownReason, newLowExpansion, newLowExpansionReason, volatilityExpansion,
                volatilityExpansionReason, alerts);
    }

    private boolean isBreadthDivergence(Map<String, Snapshot> historicalSnapshots, String currentDate,
            MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        if (breadth == null || index == null || !index.isAvailable() || !index.isRecent20High()) {
            return false;
        }
        double previousPeakBreadth = maxAboveMa20Pct(historicalSnapshots, currentDate, 10);
        return previousPeakBreadth > 0D && breadth.getAboveMa20Pct() + 4D < previousPeakBreadth;
    }

    private boolean isMomentumBreakdown(Map<String, Snapshot> historicalSnapshots, String currentDate,
            MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        if (index == null || !index.isAvailable()) {
            return false;
        }
        boolean indexBreak = index.getCurrentPrice() < index.getMovingAverage20() && index.getMa20Slope() < 0D;
        double averageAdr20 = averageAdr(historicalSnapshots, currentDate, 20);
        boolean adrBreak = averageAdr20 > 0D && averageAdr20 < 1D;
        boolean breadthBreak = breadth != null && breadth.getBreadthDeteriorationDays() >= 2;
        return indexBreak || adrBreak || breadthBreak;
    }

    private boolean isNewLowExpansion(Map<String, Snapshot> historicalSnapshots, String currentDate,
            List<SnapshotRow> currentRows) {
        if (currentRows == null || currentRows.isEmpty()) {
            return false;
        }
        List<String> dates = sortedDates(historicalSnapshots);
        if (dates.isEmpty()) {
            return false;
        }
        int index = dates.indexOf(currentDate);
        if (index < 0) {
            return false;
        }
        for (int offset = 0; offset < 3; offset++) {
            int dateIndex = index - offset;
            if (dateIndex < 0) {
                return false;
            }
            String date = dates.get(dateIndex);
            Snapshot snapshot = historicalSnapshots.get(date);
            if (snapshot == null || compareNewLowsToNewHighs(historicalSnapshots, dates, dateIndex, snapshot.rows) <= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isVolatilityExpansion(MarketIndexSnapshot index) {
        if (index == null || !index.isAvailable()) {
            return false;
        }
        boolean divergenceRisk = "價跌量增".equals(index.getDivergenceLabel()) || "價漲量縮".equals(index.getDivergenceLabel());
        boolean atrExpansion = index.getAtr20Pct() > 0D && index.getAtr60Pct() > 0D
                && index.getAtr20Pct() >= index.getAtr60Pct() * 1.2D;
        return divergenceRisk || atrExpansion;
    }

    private double maxAboveMa20Pct(Map<String, Snapshot> historicalSnapshots, String currentDate, int lookbackDays) {
        List<String> dates = sortedDates(historicalSnapshots);
        if (dates.isEmpty()) {
            return 0D;
        }
        int currentIndex = dates.indexOf(currentDate);
        if (currentIndex < 0) {
            return 0D;
        }
        double max = 0D;
        for (int i = Math.max(0, currentIndex - lookbackDays); i < currentIndex; i++) {
            Snapshot snapshot = historicalSnapshots.get(dates.get(i));
            if (snapshot != null) {
                max = Math.max(max, percentAboveMa20(snapshot.rows));
            }
        }
        return max;
    }

    private double averageAdr(Map<String, Snapshot> historicalSnapshots, String currentDate, int lookbackDays) {
        List<String> dates = sortedDates(historicalSnapshots);
        if (dates.isEmpty()) {
            return 0D;
        }
        int currentIndex = dates.indexOf(currentDate);
        if (currentIndex < 0) {
            return 0D;
        }
        int from = Math.max(1, currentIndex - lookbackDays + 1);
        double total = 0D;
        int count = 0;
        for (int i = from; i <= currentIndex; i++) {
            Snapshot current = historicalSnapshots.get(dates.get(i));
            Snapshot previous = historicalSnapshots.get(dates.get(i - 1));
            if (current == null || previous == null) {
                continue;
            }
            double adr = adrBetween(current.rows, previous.rows);
            if (adr > 0D) {
                total += adr;
                count++;
            }
        }
        return count > 0 ? total / count : 0D;
    }

    private int compareNewLowsToNewHighs(Map<String, Snapshot> historicalSnapshots, List<String> dates, int dateIndex,
            List<SnapshotRow> rows) {
        int newHighCount = 0;
        int newLowCount = 0;
        for (SnapshotRow row : rows) {
            List<Double> prices = recentPricesForCode(historicalSnapshots, dates, dateIndex, row.code, 60);
            if (prices.size() < 20) {
                continue;
            }
            double currentPrice = row.price;
            double max = Collections.max(prices);
            double min = Collections.min(prices);
            if (currentPrice >= max * 0.999D) {
                newHighCount++;
            }
            if (currentPrice <= min * 1.001D) {
                newLowCount++;
            }
        }
        return newLowCount - newHighCount;
    }

    private List<Double> recentPricesForCode(Map<String, Snapshot> historicalSnapshots, List<String> dates, int dateIndex,
            String code, int lookbackDays) {
        List<Double> prices = new ArrayList<Double>();
        int from = Math.max(0, dateIndex - lookbackDays + 1);
        for (int i = from; i <= dateIndex; i++) {
            Snapshot snapshot = historicalSnapshots.get(dates.get(i));
            if (snapshot == null) {
                continue;
            }
            for (SnapshotRow row : snapshot.rows) {
                if (code.equals(row.code) && row.price > 0D) {
                    prices.add(Double.valueOf(row.price));
                    break;
                }
            }
        }
        return prices;
    }

    private double percentAboveMa20(List<SnapshotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0D;
        }
        int above = 0;
        for (SnapshotRow row : rows) {
            if (row.movingAverage20 > 0D && row.price >= row.movingAverage20) {
                above++;
            }
        }
        return above * 100D / rows.size();
    }

    private double adrBetween(List<SnapshotRow> currentRows, List<SnapshotRow> previousRows) {
        if (currentRows == null || previousRows == null || currentRows.isEmpty() || previousRows.isEmpty()) {
            return 0D;
        }
        java.util.Map<String, Double> prevPrice = new java.util.HashMap<String, Double>();
        for (SnapshotRow row : previousRows) {
            prevPrice.put(row.code, Double.valueOf(row.price));
        }
        int advancing = 0;
        int declining = 0;
        for (SnapshotRow row : currentRows) {
            Double previous = prevPrice.get(row.code);
            if (previous == null) {
                continue;
            }
            if (row.price > previous.doubleValue()) {
                advancing++;
            } else if (row.price < previous.doubleValue()) {
                declining++;
            }
        }
        if (declining == 0) {
            return advancing > 0 ? 9.99D : 1D;
        }
        return (double) advancing / declining;
    }

    private List<String> sortedDates(Map<String, Snapshot> historicalSnapshots) {
        if (historicalSnapshots == null || historicalSnapshots.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> dates = new ArrayList<String>(historicalSnapshots.keySet());
        Collections.sort(dates);
        return dates;
    }
}
