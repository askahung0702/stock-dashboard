package stock.vo;

public class TechnicalSnapshotVO {

    private final double currentPrice;
    private final double movingAverage18;
    private final double movingAverage20;
    private final double movingAverage54;
    private final double movingAverage60;
    private final double movingAverage120;
    private final double return18DayPct;
    private final double return20DayPct;
    private final double return54DayPct;
    private final double return60DayPct;
    private final long currentVolume;
    private final double averageVolume20;
    private final double averageTradeValue20Billion;
    private final double averageLots20;
    private final double volatility20Pct;
    private final double atr20;
    private final double drawdownFromHigh60Pct;
    private final double rsi14;
    private final double stochasticK;
    private final double stochasticD;
    private final double ma20Slope;  // MA20(today) - MA20(5 days ago), positive = rising

    public TechnicalSnapshotVO(double currentPrice, double movingAverage18, double movingAverage20,
            double movingAverage54, double movingAverage60, double movingAverage120, double return18DayPct,
            double return20DayPct, double return54DayPct, double return60DayPct, long currentVolume,
            double averageVolume20, double averageTradeValue20Billion, double averageLots20, double volatility20Pct,
            double atr20, double drawdownFromHigh60Pct, double rsi14, double stochasticK, double stochasticD,
            double ma20Slope) {
        this.currentPrice = currentPrice;
        this.movingAverage18 = movingAverage18;
        this.movingAverage20 = movingAverage20;
        this.movingAverage54 = movingAverage54;
        this.movingAverage60 = movingAverage60;
        this.movingAverage120 = movingAverage120;
        this.return18DayPct = return18DayPct;
        this.return20DayPct = return20DayPct;
        this.return54DayPct = return54DayPct;
        this.return60DayPct = return60DayPct;
        this.currentVolume = currentVolume;
        this.averageVolume20 = averageVolume20;
        this.averageTradeValue20Billion = averageTradeValue20Billion;
        this.averageLots20 = averageLots20;
        this.volatility20Pct = volatility20Pct;
        this.atr20 = atr20;
        this.drawdownFromHigh60Pct = drawdownFromHigh60Pct;
        this.rsi14 = rsi14;
        this.stochasticK = stochasticK;
        this.stochasticD = stochasticD;
        this.ma20Slope = ma20Slope;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMovingAverage18() {
        return movingAverage18;
    }

    public double getMovingAverage20() {
        return movingAverage20;
    }

    public double getMovingAverage54() {
        return movingAverage54;
    }

    public double getMovingAverage60() {
        return movingAverage60;
    }

    public double getMovingAverage120() {
        return movingAverage120;
    }

    public double getReturn18DayPct() {
        return return18DayPct;
    }

    public double getReturn20DayPct() {
        return return20DayPct;
    }

    public double getReturn54DayPct() {
        return return54DayPct;
    }

    public double getReturn60DayPct() {
        return return60DayPct;
    }

    public long getCurrentVolume() {
        return currentVolume;
    }

    public double getAverageVolume20() {
        return averageVolume20;
    }

    public double getAverageTradeValue20Billion() {
        return averageTradeValue20Billion;
    }

    public double getAverageLots20() {
        return averageLots20;
    }

    public double getVolatility20Pct() {
        return volatility20Pct;
    }

    public double getAtr20() {
        return atr20;
    }

    public double getDrawdownFromHigh60Pct() {
        return drawdownFromHigh60Pct;
    }

    public double getRsi14() {
        return rsi14;
    }

    public double getStochasticK() {
        return stochasticK;
    }

    public double getStochasticD() {
        return stochasticD;
    }

    public double getMa20Slope() {
        return ma20Slope;
    }
}
