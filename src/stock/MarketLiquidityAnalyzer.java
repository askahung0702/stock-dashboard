package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;
import stock.common.NumberParser;

public class MarketLiquidityAnalyzer {

    private static final int LOOKBACK_DAYS = 15;

    public MarketLiquiditySnapshot analyze(Map<String, Snapshot> historicalSnapshots, String currentDate,
            List<SnapshotRow> currentRows) {
        String date = safe(currentDate);
        if (currentRows == null || currentRows.isEmpty()) {
            return MarketLiquiditySnapshot.unavailable(date, "沒有當日全市場快照，無法彙總融資與量能。");
        }

        DailyAggregate current = aggregate(date, currentRows);
        if (current.marginBalance <= 0L || current.marketVolumeLots <= 0D) {
            return MarketLiquiditySnapshot.unavailable(date, "當日融資餘額或成交量估算不足。");
        }

        List<DailyAggregate> history = buildHistory(historicalSnapshots, date);
        double avgMargin = averageMargin(history);
        double avgVolume = averageVolume(history);
        double avgTurnover = averageTurnover(history);
        int comparisonDays = history.size();

        double marginVsAvgPct = pctChange(avgMargin, current.marginBalance);
        double previousMargin = current.marginBalance - current.marginBalanceDelta;
        double marginDeltaPct = previousMargin > 0D ? current.marginBalanceDelta * 100D / previousMargin : 0D;
        double volumeRatio = avgVolume > 0D ? current.marketVolumeLots / avgVolume : 0D;
        double turnoverRatio = avgTurnover > 0D ? current.marketTurnoverBillion / avgTurnover : 0D;

        List<String> alerts = new ArrayList<String>();
        double score = 50D;

        if (volumeRatio >= 1.25D) {
            score += 18D;
        } else if (volumeRatio >= 1.15D) {
            score += 12D;
        } else if (volumeRatio <= 0.75D) {
            score -= 18D;
        } else if (volumeRatio <= 0.85D) {
            score -= 10D;
        }

        if (marginDeltaPct <= -0.6D && volumeRatio >= 1.0D) {
            score += 8D;
            alerts.add("大盤量能不弱且融資餘額下降，籌碼槓桿有降溫跡象。");
        }
        if (marginDeltaPct >= 0.6D && volumeRatio < 1.0D) {
            score -= 18D;
            alerts.add("量能未放大但融資餘額增加，偏向散戶槓桿升溫，需留意轉弱風險。");
        }
        if (marginDeltaPct >= 0.9D && volumeRatio >= 1.15D) {
            score -= 6D;
            alerts.add("放量同時融資明顯增加，轉強中帶有追價槓桿升溫。");
        }
        if (marginVsAvgPct >= 6D && volumeRatio < 0.95D) {
            score -= 12D;
            alerts.add("融資水位高於 15 日均值但大盤量能偏弱，承接力需要確認。");
        }
        if (volumeRatio <= 0.85D && marginDeltaPct > 0D) {
            score -= 8D;
            alerts.add("量縮但融資仍增加，容易形成弱勢盤中的槓桿壓力。");
        }
        if (comparisonDays < 8) {
            alerts.add("融資/量能比較天數少於 8 天，警示只作初步參考。");
        }
        if (alerts.isEmpty()) {
            alerts.add("融資水位與大盤量能相對 15 日均值沒有明顯異常。");
        }

        score = NumberParser.clamp(score, 0D, 100D);
        String label = resolveLabel(score, volumeRatio, marginDeltaPct);
        String text = buildText(current, comparisonDays, volumeRatio, marginVsAvgPct, marginDeltaPct);

        return new MarketLiquiditySnapshot(true, date, comparisonDays, current.marginBalance,
                current.marginBalanceDelta, avgMargin, marginVsAvgPct, marginDeltaPct, current.marketVolumeLots,
                avgVolume, volumeRatio, current.marketTurnoverBillion, avgTurnover, turnoverRatio, score, label, text,
                alerts);
    }

    private List<DailyAggregate> buildHistory(Map<String, Snapshot> snapshots, String currentDate) {
        List<DailyAggregate> history = new ArrayList<DailyAggregate>();
        if (snapshots == null || snapshots.isEmpty()) {
            return history;
        }
        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        for (int i = dates.size() - 1; i >= 0 && history.size() < LOOKBACK_DAYS; i--) {
            String date = dates.get(i);
            if (currentDate.length() > 0 && date.compareTo(currentDate) >= 0) {
                continue;
            }
            Snapshot snapshot = snapshots.get(date);
            if (snapshot == null || snapshot.rows == null || snapshot.rows.isEmpty()) {
                continue;
            }
            DailyAggregate aggregate = aggregate(date, snapshot.rows);
            if (aggregate.marginBalance > 0L && aggregate.marketVolumeLots > 0D) {
                history.add(aggregate);
            }
        }
        return history;
    }

    private DailyAggregate aggregate(String date, List<SnapshotRow> rows) {
        DailyAggregate aggregate = new DailyAggregate();
        aggregate.date = safe(date);
        if (rows == null) {
            return aggregate;
        }
        for (SnapshotRow row : rows) {
            if (row == null) {
                continue;
            }
            if (row.marginBalance > 0L) {
                aggregate.marginBalance += row.marginBalance;
                aggregate.marginBalanceDelta += row.marginBalanceDelta;
            }
            if (row.averageLots20 > 0D && row.volumeRatio > 0D) {
                aggregate.marketVolumeLots += row.averageLots20 * row.volumeRatio;
            }
            if (row.averageTradeValue20Billion > 0D && row.volumeRatio > 0D) {
                aggregate.marketTurnoverBillion += row.averageTradeValue20Billion * row.volumeRatio;
            }
        }
        return aggregate;
    }

    private double averageMargin(List<DailyAggregate> history) {
        if (history.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (DailyAggregate aggregate : history) {
            total += aggregate.marginBalance;
        }
        return total / history.size();
    }

    private double averageVolume(List<DailyAggregate> history) {
        if (history.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (DailyAggregate aggregate : history) {
            total += aggregate.marketVolumeLots;
        }
        return total / history.size();
    }

    private double averageTurnover(List<DailyAggregate> history) {
        if (history.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (DailyAggregate aggregate : history) {
            total += aggregate.marketTurnoverBillion;
        }
        return total / history.size();
    }

    private double pctChange(double previous, double current) {
        return previous > 0D ? (current - previous) * 100D / previous : 0D;
    }

    private String resolveLabel(double score, double volumeRatio, double marginDeltaPct) {
        if (volumeRatio >= 1.15D && marginDeltaPct <= 0.4D && score >= 60D) {
            return "量能轉強";
        }
        if (volumeRatio >= 1.15D && marginDeltaPct > 0.4D) {
            return "量增槓桿升溫";
        }
        if (volumeRatio <= 0.85D && marginDeltaPct > 0D) {
            return "量縮融資升溫";
        }
        if (score <= 38D) {
            return "量能轉弱";
        }
        if (score >= 62D) {
            return "健康轉強";
        }
        return "中性觀察";
    }

    private String buildText(DailyAggregate current, int comparisonDays, double volumeRatio, double marginVsAvgPct,
            double marginDeltaPct) {
        return "融資餘額 " + formatLots(current.marginBalance) + " 張，日增減 "
                + signedLots(current.marginBalanceDelta) + " 張；大盤估算成交量為 "
                + formatLots(Math.round(current.marketVolumeLots)) + " 張，為近 " + comparisonDays + " 日均量 "
                + format(volumeRatio) + " 倍；融資水位較均值 " + signed(format(marginVsAvgPct)) + "%，單日變化 "
                + signed(format(marginDeltaPct)) + "%。";
    }

    private String signedLots(long value) {
        if (value == 0L) {
            return "0";
        }
        return (value > 0L ? "+" : "-") + formatLots(Math.abs(value));
    }

    private String signed(String value) {
        if (value == null || value.startsWith("-") || "0.00".equals(value) || "0".equals(value)) {
            return value;
        }
        return "+" + value;
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(value));
    }

    private String formatLots(long value) {
        return String.format(java.util.Locale.US, "%,d", Long.valueOf(value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class DailyAggregate {
        private String date = "";
        private long marginBalance;
        private long marginBalanceDelta;
        private double marketVolumeLots;
        private double marketTurnoverBillion;
    }
}
