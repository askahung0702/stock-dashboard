package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketLiquiditySnapshot {

    private final boolean available;
    private final String date;
    private final int comparisonDays;
    private final long totalMarginBalance;
    private final long totalMarginBalanceDelta;
    private final double marginBalance15DayAverage;
    private final double marginBalanceVs15DayPct;
    private final double marginBalanceDeltaPct;
    private final double marketVolumeLots;
    private final double marketVolume15DayAverageLots;
    private final double marketVolumeRatio15Day;
    private final double marketTurnoverBillion;
    private final double marketTurnover15DayAverageBillion;
    private final double marketTurnoverRatio15Day;
    private final double signalScore;
    private final String signalLabel;
    private final String signalText;
    private final List<String> alerts;

    public MarketLiquiditySnapshot(boolean available, String date, int comparisonDays, long totalMarginBalance,
            long totalMarginBalanceDelta, double marginBalance15DayAverage, double marginBalanceVs15DayPct,
            double marginBalanceDeltaPct, double marketVolumeLots, double marketVolume15DayAverageLots,
            double marketVolumeRatio15Day, double marketTurnoverBillion, double marketTurnover15DayAverageBillion,
            double marketTurnoverRatio15Day, double signalScore, String signalLabel, String signalText,
            List<String> alerts) {
        this.available = available;
        this.date = date == null ? "" : date;
        this.comparisonDays = comparisonDays;
        this.totalMarginBalance = totalMarginBalance;
        this.totalMarginBalanceDelta = totalMarginBalanceDelta;
        this.marginBalance15DayAverage = marginBalance15DayAverage;
        this.marginBalanceVs15DayPct = marginBalanceVs15DayPct;
        this.marginBalanceDeltaPct = marginBalanceDeltaPct;
        this.marketVolumeLots = marketVolumeLots;
        this.marketVolume15DayAverageLots = marketVolume15DayAverageLots;
        this.marketVolumeRatio15Day = marketVolumeRatio15Day;
        this.marketTurnoverBillion = marketTurnoverBillion;
        this.marketTurnover15DayAverageBillion = marketTurnover15DayAverageBillion;
        this.marketTurnoverRatio15Day = marketTurnoverRatio15Day;
        this.signalScore = signalScore;
        this.signalLabel = signalLabel == null ? "" : signalLabel;
        this.signalText = signalText == null ? "" : signalText;
        this.alerts = alerts == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(alerts));
    }

    public static MarketLiquiditySnapshot unavailable(String date, String reason) {
        List<String> alerts = new ArrayList<String>();
        if (reason != null && reason.length() > 0) {
            alerts.add(reason);
        }
        return new MarketLiquiditySnapshot(false, date, 0, 0L, 0L, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 50D,
                "資料不足", "融資與量能資料不足，暫不納入大盤警示。", alerts);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDate() {
        return date;
    }

    public int getComparisonDays() {
        return comparisonDays;
    }

    public long getTotalMarginBalance() {
        return totalMarginBalance;
    }

    public long getTotalMarginBalanceDelta() {
        return totalMarginBalanceDelta;
    }

    public double getMarginBalance15DayAverage() {
        return marginBalance15DayAverage;
    }

    public double getMarginBalanceVs15DayPct() {
        return marginBalanceVs15DayPct;
    }

    public double getMarginBalanceDeltaPct() {
        return marginBalanceDeltaPct;
    }

    public double getMarketVolumeLots() {
        return marketVolumeLots;
    }

    public double getMarketVolume15DayAverageLots() {
        return marketVolume15DayAverageLots;
    }

    public double getMarketVolumeRatio15Day() {
        return marketVolumeRatio15Day;
    }

    public double getMarketTurnoverBillion() {
        return marketTurnoverBillion;
    }

    public double getMarketTurnover15DayAverageBillion() {
        return marketTurnover15DayAverageBillion;
    }

    public double getMarketTurnoverRatio15Day() {
        return marketTurnoverRatio15Day;
    }

    public double getSignalScore() {
        return signalScore;
    }

    public String getSignalLabel() {
        return signalLabel;
    }

    public String getSignalText() {
        return signalText;
    }

    public List<String> getAlerts() {
        return alerts;
    }
}
