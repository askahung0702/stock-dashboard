package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;
import stock.vo.StockAnalysisResultVO;

public class MarketBreadthAnalyzer {

    public MarketBreadthSnapshot analyzeRows(List<SnapshotRow> currentRows, Map<String, SnapshotRow> prevRows,
            Map<String, Snapshot> historicalSnapshots, String currentDate, double watchThreshold, double likelyThreshold) {
        if (currentRows == null) {
            currentRows = Collections.emptyList();
        }

        int total = currentRows.size();
        int advancingCount = 0;
        int decliningCount = 0;
        int unchangedCount = 0;
        int aboveMa20Count = 0;
        int aboveMa18Count = 0;
        int belowMa20Count = 0;
        int likelyCount = 0;
        int qualifiedCount = 0;
        int buyReadyCount = 0;
        int scoreUpCount = 0;
        double averageSelectionScore = 0D;
        double averageNewsRiskScore = 0D;

        for (SnapshotRow row : currentRows) {
            double selectionScore = selectionScoreOf(row);
            averageSelectionScore += selectionScore;
            averageNewsRiskScore += row.newsRiskScore;

            if (row.price >= row.movingAverage20 && row.movingAverage20 > 0D) {
                aboveMa20Count++;
            } else {
                belowMa20Count++;
            }
            if (row.price >= row.movingAverage18 && row.movingAverage18 > 0D) {
                aboveMa18Count++;
            }
            if (row.selectionQualified) {
                qualifiedCount++;
            }
            if (row.selectionQualified && selectionScore >= likelyThreshold) {
                likelyCount++;
            }
            if (buyPointScoreOf(row) >= 75D) {
                buyReadyCount++;
            }

            SnapshotRow prev = prevRows == null ? null : prevRows.get(row.code);
            if (prev != null) {
                if (row.price > prev.price) {
                    advancingCount++;
                } else if (row.price < prev.price) {
                    decliningCount++;
                } else {
                    unchangedCount++;
                }
                if (selectionScore > selectionScoreOf(prev)) {
                    scoreUpCount++;
                }
            }
        }

        if (prevRows == null || prevRows.isEmpty()) {
            unchangedCount = total;
        } else {
            unchangedCount += Math.max(0, total - advancingCount - decliningCount - unchangedCount);
        }

        averageSelectionScore = total > 0 ? averageSelectionScore / total : 0D;
        averageNewsRiskScore = total > 0 ? averageNewsRiskScore / total : 0D;

        double aboveMa20Pct = percent(aboveMa20Count, total);
        double aboveMa18Pct = percent(aboveMa18Count, total);
        double belowMa20Pct = percent(belowMa20Count, total);
        double likelyPct = percent(likelyCount, total);
        double qualifiedPct = percent(qualifiedCount, total);
        double buyReadyPct = percent(buyReadyCount, total);
        double scoreUpPct = percent(scoreUpCount, total);
        double adr = decliningCount > 0 ? (double) advancingCount / decliningCount : (advancingCount > 0 ? 9.99D : 1D);
        int breadthDeteriorationDays = computeBreadthDeteriorationDays(currentRows, historicalSnapshots, currentDate);

        return new MarketBreadthSnapshot(total, advancingCount, decliningCount, unchangedCount, aboveMa20Count,
                aboveMa18Count, belowMa20Count, likelyCount, qualifiedCount, buyReadyCount, scoreUpCount,
                breadthDeteriorationDays, adr, aboveMa20Pct, aboveMa18Pct, belowMa20Pct, likelyPct, qualifiedPct,
                buyReadyPct, scoreUpPct, averageSelectionScore, averageNewsRiskScore);
    }

    public MarketBreadthSnapshot analyzeResults(List<StockAnalysisResultVO> results, Map<String, Snapshot> historicalSnapshots,
            String currentDate, double watchThreshold, double likelyThreshold) {
        List<SnapshotRow> rows = new ArrayList<SnapshotRow>();
        if (results != null) {
            for (StockAnalysisResultVO result : results) {
                SnapshotRow row = new SnapshotRow();
                row.code = result.getStock().getCode();
                row.selectionScore = result.getSelectionScore();
                row.score = result.getScore();
                row.buyPointScore = result.getBuyPointScore();
                row.price = result.getCurrentPrice();
                row.movingAverage18 = result.getMovingAverage18();
                row.movingAverage20 = result.getMovingAverage20();
                row.selectionQualified = result.isSelectionQualified();
                row.newsRiskScore = result.getNewsRiskScore();
                rows.add(row);
            }
        }
        return analyzeRows(rows, previousRows(historicalSnapshots, currentDate), historicalSnapshots, currentDate,
                watchThreshold, likelyThreshold);
    }

    private int computeBreadthDeteriorationDays(List<SnapshotRow> currentRows, Map<String, Snapshot> historicalSnapshots,
            String currentDate) {
        List<Double> belowSeries = new ArrayList<Double>();
        belowSeries.add(Double.valueOf(percentBelowMa20(currentRows)));

        if (historicalSnapshots == null || historicalSnapshots.isEmpty()) {
            return 0;
        }

        List<String> dates = new ArrayList<String>(historicalSnapshots.keySet());
        Collections.sort(dates);
        int currentIndex = dates.indexOf(currentDate);
        if (currentIndex < 0) {
            currentIndex = dates.size();
        }
        for (int i = currentIndex - 1; i >= 0 && belowSeries.size() < 4; i--) {
            Snapshot snapshot = historicalSnapshots.get(dates.get(i));
            if (snapshot != null) {
                belowSeries.add(Double.valueOf(percentBelowMa20(snapshot.rows)));
            }
        }

        int risingDays = 0;
        for (int i = 0; i < belowSeries.size() - 1; i++) {
            double newer = belowSeries.get(i).doubleValue();
            double older = belowSeries.get(i + 1).doubleValue();
            if (newer > older + 0.8D) {
                risingDays++;
            } else {
                break;
            }
        }
        return risingDays;
    }

    private double percentBelowMa20(List<SnapshotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0D;
        }
        int below = 0;
        for (SnapshotRow row : rows) {
            if (row.price < row.movingAverage20 || row.movingAverage20 <= 0D) {
                below++;
            }
        }
        return percent(below, rows.size());
    }

    private Map<String, SnapshotRow> previousRows(Map<String, Snapshot> historicalSnapshots, String currentDate) {
        java.util.Map<String, SnapshotRow> rows = new java.util.HashMap<String, SnapshotRow>();
        if (historicalSnapshots == null || historicalSnapshots.isEmpty()) {
            return rows;
        }
        List<String> dates = new ArrayList<String>(historicalSnapshots.keySet());
        Collections.sort(dates);
        String previous = null;
        for (String date : dates) {
            if (date.equals(currentDate)) {
                break;
            }
            previous = date;
        }
        if (previous == null) {
            return rows;
        }
        Snapshot snapshot = historicalSnapshots.get(previous);
        if (snapshot == null) {
            return rows;
        }
        for (SnapshotRow row : snapshot.rows) {
            rows.put(row.code, row);
        }
        return rows;
    }

    private double percent(int count, int total) {
        return total > 0 ? count * 100D / total : 0D;
    }

    private double selectionScoreOf(SnapshotRow row) {
        return row.selectionScore > 0D ? row.selectionScore : row.score;
    }

    private double buyPointScoreOf(SnapshotRow row) {
        return row.buyPointScore > 0D ? row.buyPointScore : selectionScoreOf(row);
    }
}
