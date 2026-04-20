package stock;

public class MarketIndexSnapshot {

    private final boolean available;
    private final String symbol;
    private final String name;
    private final String source;
    private final String errorMessage;
    private final double currentPrice;
    private final double movingAverage20;
    private final double movingAverage60;
    private final double return20DayPct;
    private final double volumeRatio;
    private final double macd;
    private final double macdSignal;
    private final double macdHistogram;
    private final double ma20Slope;
    private final boolean recent20High;
    private final double atr20Pct;
    private final double atr60Pct;
    private final String trendLabel;
    private final String divergenceLabel;

    public MarketIndexSnapshot(boolean available, String symbol, String name, String source, String errorMessage,
            double currentPrice, double movingAverage20, double movingAverage60, double return20DayPct,
            double volumeRatio, double macd, double macdSignal, double macdHistogram, double ma20Slope,
            boolean recent20High, double atr20Pct, double atr60Pct, String trendLabel, String divergenceLabel) {
        this.available = available;
        this.symbol = symbol;
        this.name = name;
        this.source = source;
        this.errorMessage = errorMessage;
        this.currentPrice = currentPrice;
        this.movingAverage20 = movingAverage20;
        this.movingAverage60 = movingAverage60;
        this.return20DayPct = return20DayPct;
        this.volumeRatio = volumeRatio;
        this.macd = macd;
        this.macdSignal = macdSignal;
        this.macdHistogram = macdHistogram;
        this.ma20Slope = ma20Slope;
        this.recent20High = recent20High;
        this.atr20Pct = atr20Pct;
        this.atr60Pct = atr60Pct;
        this.trendLabel = trendLabel;
        this.divergenceLabel = divergenceLabel;
    }

    public static MarketIndexSnapshot unavailable(String symbol, String name, String source, String errorMessage) {
        return new MarketIndexSnapshot(false, symbol, name, source, errorMessage, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                0D, false, 0D, 0D, "資料不足", "未提供");
    }

    public boolean isAvailable() {
        return available;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMovingAverage20() {
        return movingAverage20;
    }

    public double getMovingAverage60() {
        return movingAverage60;
    }

    public double getReturn20DayPct() {
        return return20DayPct;
    }

    public double getVolumeRatio() {
        return volumeRatio;
    }

    public double getMacd() {
        return macd;
    }

    public double getMacdSignal() {
        return macdSignal;
    }

    public double getMacdHistogram() {
        return macdHistogram;
    }

    public double getMa20Slope() {
        return ma20Slope;
    }

    public boolean isRecent20High() {
        return recent20High;
    }

    public double getAtr20Pct() {
        return atr20Pct;
    }

    public double getAtr60Pct() {
        return atr60Pct;
    }

    public String getTrendLabel() {
        return trendLabel;
    }

    public String getDivergenceLabel() {
        return divergenceLabel;
    }
}
