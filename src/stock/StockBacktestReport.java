package stock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;

public class StockBacktestReport {

    private static final int[] DEFAULT_HORIZONS = new int[] { 1, 3, 5, 10 };
    private static final double LIKELY_THRESHOLD = 72D;
    private static final double WATCHLIST_THRESHOLD = 58D;
    private static final double MIN_LIKELY_FINANCIAL_SCORE = 12D;
    private static final double LIKELY_MIN_VOLUME_RATIO = 0.8D;
    private static final double LIKELY_MAX_VOLUME_RATIO = 2.5D;
    private static final double BUY_FEE_PCT = parseDoubleProperty("stock.backtest.buyFeePct", 0.1425D);
    private static final double SELL_FEE_PCT = parseDoubleProperty("stock.backtest.sellFeePct", 0.1425D);
    private static final double SELL_TAX_PCT = parseDoubleProperty("stock.backtest.sellTaxPct", 0.3000D);
    private static final double BUY_SLIPPAGE_PCT = parseDoubleProperty("stock.backtest.buySlippagePct", 0.1500D);
    private static final double SELL_SLIPPAGE_PCT = parseDoubleProperty("stock.backtest.sellSlippagePct", 0.1500D);
    private static final double DEFAULT_STOP_LOSS_PCT = parseDoubleProperty("stock.backtest.defaultStopLossPct", 6.0D);
    private static final double MIN_STOP_LOSS_PCT = 3.0D;
    private static final double MAX_STOP_LOSS_PCT = 14.0D;
    private static final double DEFAULT_TARGET_PCT = parseDoubleProperty("stock.backtest.defaultTargetPct", 12.0D);

    public static void main(String[] args) throws Exception {
        StockBacktestReport report = new StockBacktestReport();
        String path = report.writeDefaultReport();
        if (path.length() == 0) {
            System.out.println("Backtest skipped: not enough history snapshots.");
            return;
        }
        System.out.println("Backtest summary: " + new File(path).getAbsolutePath());
    }

    public String writeDefaultReport() throws Exception {
        return writeSummaryReport(DEFAULT_HORIZONS, new File("history", "backtest_summary.csv").getPath());
    }

    public String writeSummaryReport(int[] horizons, String fileName) throws Exception {
        StockHistoryDatabase database = new StockHistoryDatabase();
        Map<String, Snapshot> snapshots = database.loadSnapshots();
        if (snapshots.size() < 3) {
            return "";
        }

        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        List<Map<String, SnapshotRow>> rowMaps = buildRowMaps(dates, snapshots);

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        try {
            writer.write('\uFEFF');
            writer.println(
                    "horizon_days,cohort,sample_count,price_win_rate_pct,net_win_rate_pct,avg_price_return_pct,avg_net_return_pct,median_net_return_pct,avg_friction_impact_pct,avg_max_drawdown_close_pct,avg_max_runup_close_pct,stop_hit_rate_pct,target_hit_rate_pct,avg_holding_days,avg_selection_score,avg_legacy_score");

            for (int horizon : horizons) {
                Map<String, CohortStats> stats = initCohorts();
                accumulateHorizonStats(stats, snapshots, dates, rowMaps, horizon);
                writeHorizon(writer, horizon, stats);
            }
        } finally {
            writer.close();
        }
        return fileName;
    }

    private Map<String, CohortStats> initCohorts() {
        Map<String, CohortStats> stats = new LinkedHashMap<String, CohortStats>();
        stats.put("ALL", new CohortStats());
        stats.put("LIKELY", new CohortStats());
        stats.put("WATCHLIST", new CohortStats());
        stats.put("QUALIFIED", new CohortStats());
        stats.put("QUALITY_70", new CohortStats());
        stats.put("MOMENTUM_70", new CohortStats());
        stats.put("SECTOR_60", new CohortStats());
        stats.put("TREND_65", new CohortStats());
        stats.put("STRUCTURE_70", new CohortStats());
        stats.put("RR_60", new CohortStats());
        stats.put("WINRATE_FOCUS", new CohortStats());
        stats.put("BUYPOINT_75", new CohortStats());
        stats.put("BUYPOINT_A", new CohortStats());
        return stats;
    }

    private void accumulateHorizonStats(Map<String, CohortStats> stats, Map<String, Snapshot> snapshots,
            List<String> dates, List<Map<String, SnapshotRow>> rowMaps, int horizon) {
        for (int signalIndex = 0; signalIndex + horizon + 1 < dates.size(); signalIndex++) {
            Snapshot start = snapshots.get(dates.get(signalIndex));
            if (start == null) {
                continue;
            }

            for (SnapshotRow row : start.rows) {
                TradeSimulation trade = simulateTrade(row, rowMaps, signalIndex, horizon);
                if (trade == null) {
                    continue;
                }

                double selectionScore = selectionScoreOf(row);
                double qualityScore = qualityScoreOf(row);
                double momentumScore = momentumScoreOf(row);
                double sectorScore = row.sectorScore;
                double trendScore = row.trendPersistenceScore;
                double structureScore = row.structureScore;
                double riskRewardScore = row.riskRewardScore;

                stats.get("ALL").add(trade, selectionScore, row.score);
                if (isLikely(row)) {
                    stats.get("LIKELY").add(trade, selectionScore, row.score);
                }
                if (!isLikely(row) && selectionScore >= WATCHLIST_THRESHOLD && isQualified(row)) {
                    stats.get("WATCHLIST").add(trade, selectionScore, row.score);
                }
                if (isQualified(row)) {
                    stats.get("QUALIFIED").add(trade, selectionScore, row.score);
                }
                if (qualityScore >= 70D) {
                    stats.get("QUALITY_70").add(trade, selectionScore, row.score);
                }
                if (momentumScore >= 70D) {
                    stats.get("MOMENTUM_70").add(trade, selectionScore, row.score);
                }
                if (sectorScore >= 60D) {
                    stats.get("SECTOR_60").add(trade, selectionScore, row.score);
                }
                if (trendScore >= 65D) {
                    stats.get("TREND_65").add(trade, selectionScore, row.score);
                }
                if (structureScore >= 70D) {
                    stats.get("STRUCTURE_70").add(trade, selectionScore, row.score);
                }
                if (riskRewardScore >= 60D) {
                    stats.get("RR_60").add(trade, selectionScore, row.score);
                }
                if (qualityScore >= 70D && trendScore >= 65D && structureScore >= 70D && riskRewardScore >= 60D) {
                    stats.get("WINRATE_FOCUS").add(trade, selectionScore, row.score);
                }
                if (buyPointScoreOf(row) >= 75D) {
                    stats.get("BUYPOINT_75").add(trade, selectionScore, row.score);
                }
                if (buyPointScoreOf(row) >= 85D) {
                    stats.get("BUYPOINT_A").add(trade, selectionScore, row.score);
                }
            }
        }
    }

    private TradeSimulation simulateTrade(SnapshotRow signalRow, List<Map<String, SnapshotRow>> rowMaps, int signalIndex,
            int horizon) {
        int entryIndex = signalIndex + 1;
        int plannedExitIndex = signalIndex + 1 + horizon;
        if (plannedExitIndex >= rowMaps.size()) {
            return null;
        }

        SnapshotRow entryRow = rowMaps.get(entryIndex).get(signalRow.code);
        if (!isTradeable(entryRow)) {
            return null;
        }

        double entryMarketPrice = entryRow.price;
        double stopPrice = resolveStopPrice(signalRow, entryMarketPrice);
        double targetPrice = resolveTargetPrice(signalRow, entryMarketPrice, stopPrice);
        if (stopPrice > 0D && entryMarketPrice <= stopPrice) {
            return null;
        }
        if (targetPrice > 0D && entryMarketPrice >= targetPrice) {
            return null;
        }

        TradeSimulation trade = new TradeSimulation();
        trade.holdingDays = horizon;
        trade.maxDrawdownClosePct = 0D;
        trade.maxRunupClosePct = 0D;

        int actualExitIndex = plannedExitIndex;
        double exitMarketPrice = 0D;
        for (int idx = entryIndex; idx <= plannedExitIndex; idx++) {
            SnapshotRow observedRow = rowMaps.get(idx).get(signalRow.code);
            if (!isTradeable(observedRow)) {
                return null;
            }

            double closeReturnPct = percentageChange(entryMarketPrice, observedRow.price);
            trade.maxDrawdownClosePct = Math.min(trade.maxDrawdownClosePct, closeReturnPct);
            trade.maxRunupClosePct = Math.max(trade.maxRunupClosePct, closeReturnPct);

            if (stopPrice > 0D && observedRow.price <= stopPrice) {
                trade.stopHit = true;
                actualExitIndex = idx;
                exitMarketPrice = observedRow.price;
                break;
            }
            if (targetPrice > 0D && observedRow.price >= targetPrice) {
                trade.targetHit = true;
                actualExitIndex = idx;
                exitMarketPrice = targetPrice;
                break;
            }
        }

        if (exitMarketPrice <= 0D) {
            SnapshotRow exitRow = rowMaps.get(plannedExitIndex).get(signalRow.code);
            if (!isTradeable(exitRow)) {
                return null;
            }
            exitMarketPrice = exitRow.price;
        }

        double entryExecutedPrice = entryMarketPrice * (1D + BUY_SLIPPAGE_PCT / 100D);
        double exitExecutedPrice = exitMarketPrice * (1D - SELL_SLIPPAGE_PCT / 100D);
        trade.holdingDays = Math.max(0, actualExitIndex - entryIndex);
        trade.priceReturnPct = percentageChange(entryMarketPrice, exitMarketPrice);
        trade.netReturnPct = computeNetReturnPct(entryExecutedPrice, exitExecutedPrice);
        trade.frictionImpactPct = trade.priceReturnPct - trade.netReturnPct;
        return trade;
    }

    private List<Map<String, SnapshotRow>> buildRowMaps(List<String> dates, Map<String, Snapshot> snapshots) {
        List<Map<String, SnapshotRow>> rowMaps = new ArrayList<Map<String, SnapshotRow>>();
        for (String date : dates) {
            Snapshot snapshot = snapshots.get(date);
            Map<String, SnapshotRow> rowsByCode = new LinkedHashMap<String, SnapshotRow>();
            if (snapshot != null) {
                for (SnapshotRow row : snapshot.rows) {
                    rowsByCode.put(row.code, row);
                }
            }
            rowMaps.add(rowsByCode);
        }
        return rowMaps;
    }

    private double resolveStopPrice(SnapshotRow row, double entryPrice) {
        if (row.suggestedStopPrice > 0D && row.suggestedStopPrice < entryPrice) {
            return row.suggestedStopPrice;
        }

        double stopLossPct = positiveOrZero(row.suggestedStopPct);
        if (stopLossPct <= 0D) {
            stopLossPct = clampRange(row.volatility20Pct * 1.1D, MIN_STOP_LOSS_PCT, MAX_STOP_LOSS_PCT);
        }
        if (stopLossPct <= 0D) {
            stopLossPct = DEFAULT_STOP_LOSS_PCT;
        }
        return entryPrice * (1D - stopLossPct / 100D);
    }

    private double resolveTargetPrice(SnapshotRow row, double entryPrice, double stopPrice) {
        if (row.suggestedTargetPrice > entryPrice) {
            return row.suggestedTargetPrice;
        }

        double stopLossPct = percentageDistance(entryPrice, stopPrice);
        double targetPct = positiveOrZero(row.upsidePotentialPct);
        if (targetPct <= 0D && row.riskRewardRatio > 0D && stopLossPct > 0D) {
            targetPct = stopLossPct * row.riskRewardRatio;
        }
        if (targetPct <= 0D && stopLossPct > 0D) {
            targetPct = stopLossPct * 1.8D;
        }
        if (targetPct <= 0D) {
            targetPct = DEFAULT_TARGET_PCT;
        }
        return entryPrice * (1D + Math.max(targetPct, 4D) / 100D);
    }

    private double computeNetReturnPct(double entryExecutedPrice, double exitExecutedPrice) {
        if (entryExecutedPrice <= 0D || exitExecutedPrice <= 0D) {
            return 0D;
        }
        double entryCashCost = entryExecutedPrice * (1D + BUY_FEE_PCT / 100D);
        double exitCashIn = exitExecutedPrice * (1D - (SELL_FEE_PCT + SELL_TAX_PCT) / 100D);
        return (exitCashIn / entryCashCost - 1D) * 100D;
    }

    private double selectionScoreOf(SnapshotRow row) {
        return row.selectionScore > 0D ? row.selectionScore : row.score;
    }

    private double qualityScoreOf(SnapshotRow row) {
        if (row.qualityScore > 0D) {
            return row.qualityScore;
        }
        return clamp((row.revenueScore / 30D) * 35D + (row.financialQualityScore / 20D) * 35D
                + (row.valuationScore / 20D) * 20D + (row.liquidityScore / 15D) * 10D);
    }

    private double momentumScoreOf(SnapshotRow row) {
        if (row.momentumScore > 0D) {
            return row.momentumScore;
        }
        double score = (row.chipsScore / 30D) * 40D + (row.technicalScore / 20D) * 30D;
        if (row.volumeRatio >= 0.8D && row.volumeRatio <= 2.5D) {
            score += 15D;
        } else if (row.volumeRatio >= 0.6D && row.volumeRatio < 0.8D) {
            score += 8D;
        }
        if (row.return20DayPct >= 0D && row.return20DayPct <= 20D) {
            score += 10D;
        } else if (row.return20DayPct > 20D && row.return20DayPct <= 35D) {
            score += 6D;
        }
        return clamp(score);
    }

    private boolean isQualified(SnapshotRow row) {
        return row.selectionQualified || (row.liquidityScore >= 4D && row.financialQualityScore >= 8D);
    }

    private boolean isLikely(SnapshotRow row) {
        return selectionScoreOf(row) >= LIKELY_THRESHOLD && isQualified(row)
                && row.financialQualityScore >= MIN_LIKELY_FINANCIAL_SCORE
                && row.volumeRatio >= LIKELY_MIN_VOLUME_RATIO && row.volumeRatio <= LIKELY_MAX_VOLUME_RATIO;
    }

    private boolean isTradeable(SnapshotRow row) {
        return row != null && row.price > 0D;
    }

    private double buyPointScoreOf(SnapshotRow row) {
        return row.buyPointScore > 0D ? row.buyPointScore : selectionScoreOf(row);
    }

    private double percentageChange(double basePrice, double targetPrice) {
        if (basePrice <= 0D || targetPrice <= 0D) {
            return 0D;
        }
        return (targetPrice - basePrice) * 100D / basePrice;
    }

    private double percentageDistance(double basePrice, double comparePrice) {
        if (basePrice <= 0D || comparePrice <= 0D) {
            return 0D;
        }
        return Math.abs(basePrice - comparePrice) * 100D / basePrice;
    }

    private double positiveOrZero(double value) {
        return value > 0D ? value : 0D;
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(100D, value));
    }

    private double clampRange(double value, double min, double max) {
        if (value <= 0D) {
            return 0D;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format("%.3f", Double.valueOf(value));
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static double parseDoubleProperty(String key, double defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static class TradeSimulation {
        private double priceReturnPct;
        private double netReturnPct;
        private double frictionImpactPct;
        private double maxDrawdownClosePct;
        private double maxRunupClosePct;
        private int holdingDays;
        private boolean stopHit;
        private boolean targetHit;
    }

    private static class CohortStats {
        private int count;
        private int priceWins;
        private int netWins;
        private int stopHits;
        private int targetHits;
        private double priceReturnSum;
        private double netReturnSum;
        private double frictionImpactSum;
        private double drawdownSum;
        private double runupSum;
        private double holdingDaysSum;
        private double selectionScoreSum;
        private double legacyScoreSum;
        private final List<Double> netReturns = new ArrayList<Double>();

        private void add(TradeSimulation trade, double selectionScore, double legacyScore) {
            count++;
            priceReturnSum += trade.priceReturnPct;
            netReturnSum += trade.netReturnPct;
            frictionImpactSum += trade.frictionImpactPct;
            drawdownSum += trade.maxDrawdownClosePct;
            runupSum += trade.maxRunupClosePct;
            holdingDaysSum += trade.holdingDays;
            selectionScoreSum += selectionScore;
            legacyScoreSum += legacyScore;
            if (trade.priceReturnPct > 0D) {
                priceWins++;
            }
            if (trade.netReturnPct > 0D) {
                netWins++;
            }
            if (trade.stopHit) {
                stopHits++;
            }
            if (trade.targetHit) {
                targetHits++;
            }
            netReturns.add(Double.valueOf(trade.netReturnPct));
        }

        private double priceWinRatePct() {
            return count == 0 ? 0D : priceWins * 100D / count;
        }

        private double netWinRatePct() {
            return count == 0 ? 0D : netWins * 100D / count;
        }

        private double averagePriceReturnPct() {
            return count == 0 ? 0D : priceReturnSum / count;
        }

        private double averageNetReturnPct() {
            return count == 0 ? 0D : netReturnSum / count;
        }

        private double averageFrictionImpactPct() {
            return count == 0 ? 0D : frictionImpactSum / count;
        }

        private double averageMaxDrawdownPct() {
            return count == 0 ? 0D : drawdownSum / count;
        }

        private double averageMaxRunupPct() {
            return count == 0 ? 0D : runupSum / count;
        }

        private double stopHitRatePct() {
            return count == 0 ? 0D : stopHits * 100D / count;
        }

        private double targetHitRatePct() {
            return count == 0 ? 0D : targetHits * 100D / count;
        }

        private double averageHoldingDays() {
            return count == 0 ? 0D : holdingDaysSum / count;
        }

        private double averageSelectionScore() {
            return count == 0 ? 0D : selectionScoreSum / count;
        }

        private double averageLegacyScore() {
            return count == 0 ? 0D : legacyScoreSum / count;
        }

        private double medianNetReturnPct() {
            if (netReturns.isEmpty()) {
                return 0D;
            }
            Collections.sort(netReturns);
            int middle = netReturns.size() / 2;
            if ((netReturns.size() & 1) == 1) {
                return netReturns.get(middle).doubleValue();
            }
            return (netReturns.get(middle - 1).doubleValue() + netReturns.get(middle).doubleValue()) / 2D;
        }
    }

    private void writeHorizon(PrintWriter writer, int horizon, Map<String, CohortStats> stats) {
        for (Map.Entry<String, CohortStats> entry : stats.entrySet()) {
            CohortStats stat = entry.getValue();
            if (stat.count == 0) {
                continue;
            }
            writer.println(Integer.toString(horizon) + "," + csv(entry.getKey()) + "," + stat.count + ","
                    + format(stat.priceWinRatePct()) + "," + format(stat.netWinRatePct()) + ","
                    + format(stat.averagePriceReturnPct()) + "," + format(stat.averageNetReturnPct()) + ","
                    + format(stat.medianNetReturnPct()) + "," + format(stat.averageFrictionImpactPct()) + ","
                    + format(stat.averageMaxDrawdownPct()) + "," + format(stat.averageMaxRunupPct()) + ","
                    + format(stat.stopHitRatePct()) + "," + format(stat.targetHitRatePct()) + ","
                    + format(stat.averageHoldingDays()) + "," + format(stat.averageSelectionScore()) + ","
                    + format(stat.averageLegacyScore()));
        }
    }
}
