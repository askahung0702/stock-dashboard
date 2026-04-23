package stock;

public class TaiexFuturesPriceSnapshot {

    private final boolean available;
    private final String symbol;
    private final String name;
    private final String source;
    private final String errorMessage;
    private final double currentPrice;
    private final double previousClose;
    private final double change;
    private final double changePct;
    private final long volume;
    private final String marketTime;

    public TaiexFuturesPriceSnapshot(boolean available, String symbol, String name, String source, String errorMessage,
            double currentPrice, double previousClose, double change, double changePct, long volume, String marketTime) {
        this.available = available;
        this.symbol = symbol;
        this.name = name;
        this.source = source;
        this.errorMessage = errorMessage;
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.change = change;
        this.changePct = changePct;
        this.volume = volume;
        this.marketTime = marketTime;
    }

    public static TaiexFuturesPriceSnapshot unavailable(String source, String errorMessage) {
        return new TaiexFuturesPriceSnapshot(false, "IX0126.TW", "台指期", source, errorMessage, 0D, 0D, 0D, 0D, 0L, "");
    }

    public boolean isAvailable() { return available; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getSource() { return source; }
    public String getErrorMessage() { return errorMessage; }
    public double getCurrentPrice() { return currentPrice; }
    public double getPreviousClose() { return previousClose; }
    public double getChange() { return change; }
    public double getChangePct() { return changePct; }
    public long getVolume() { return volume; }
    public String getMarketTime() { return marketTime; }
}
