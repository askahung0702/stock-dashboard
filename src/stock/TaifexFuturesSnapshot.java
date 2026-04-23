package stock;

public class TaifexFuturesSnapshot {

    private final boolean available;
    private final String source;
    private final String dataDate;
    private final String productName;
    private final String identityName;
    private final String errorMessage;
    private final long foreignOpenInterestLongLots;
    private final long foreignOpenInterestShortLots;
    private final long foreignOpenInterestNetLots;
    private final long foreignTradingLongLots;
    private final long foreignTradingShortLots;
    private final long foreignTradingNetLots;

    public TaifexFuturesSnapshot(boolean available, String source, String dataDate, String productName,
            String identityName, String errorMessage, long foreignOpenInterestLongLots,
            long foreignOpenInterestShortLots, long foreignOpenInterestNetLots, long foreignTradingLongLots,
            long foreignTradingShortLots, long foreignTradingNetLots) {
        this.available = available;
        this.source = source;
        this.dataDate = dataDate;
        this.productName = productName;
        this.identityName = identityName;
        this.errorMessage = errorMessage;
        this.foreignOpenInterestLongLots = foreignOpenInterestLongLots;
        this.foreignOpenInterestShortLots = foreignOpenInterestShortLots;
        this.foreignOpenInterestNetLots = foreignOpenInterestNetLots;
        this.foreignTradingLongLots = foreignTradingLongLots;
        this.foreignTradingShortLots = foreignTradingShortLots;
        this.foreignTradingNetLots = foreignTradingNetLots;
    }

    public static TaifexFuturesSnapshot unavailable(String source, String errorMessage) {
        return new TaifexFuturesSnapshot(false, source, "", "臺股期貨", "外資", errorMessage, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public boolean isAvailable() { return available; }
    public String getSource() { return source; }
    public String getDataDate() { return dataDate; }
    public String getProductName() { return productName; }
    public String getIdentityName() { return identityName; }
    public String getErrorMessage() { return errorMessage; }
    public long getForeignOpenInterestLongLots() { return foreignOpenInterestLongLots; }
    public long getForeignOpenInterestShortLots() { return foreignOpenInterestShortLots; }
    public long getForeignOpenInterestNetLots() { return foreignOpenInterestNetLots; }
    public long getForeignTradingLongLots() { return foreignTradingLongLots; }
    public long getForeignTradingShortLots() { return foreignTradingShortLots; }
    public long getForeignTradingNetLots() { return foreignTradingNetLots; }
}
