package stock;

import org.json.simple.JSONObject;

public class StockMarketDataOnlyAnalysis {

    private static final String KEY_FUTURES_PRICE = "marketFuturesPrice";
    private static final String KEY_FUTURES_POSITION = "marketFuturesPosition";

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 && args[0] != null ? args[0].trim().toLowerCase() : "market-futures";
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        StockHistoryDatabase database = new StockHistoryDatabase();
        String date = analyzer.currentDateStamp();

        if ("market-futures".equals(mode) || "futures-price".equals(mode) || "taiex-futures".equals(mode)) {
            JSONObject payload = priceToJson(new TaiexFuturesPriceService().fetchLatest());
            database.upsertDailyMarketData(date, "market-futures", KEY_FUTURES_PRICE, payload);
            database.upsertDailyRunStatus(date, "market-futures",
                    Boolean.TRUE.equals(payload.get("available")) ? "completed" : "unavailable", 1,
                    String.valueOf(payload.get("errorMessage")));
            System.out.println("Market futures price saved: " + payload.toJSONString());
            return;
        }

        if ("futures-position".equals(mode) || "taifex-position".equals(mode) || "foreign-futures".equals(mode)) {
            JSONObject payload = positionToJson(new TaifexFuturesService().fetchTaiwanIndexFuturesForeignPosition());
            database.upsertDailyMarketData(date, "futures-position", KEY_FUTURES_POSITION, payload);
            database.upsertDailyRunStatus(date, "futures-position",
                    Boolean.TRUE.equals(payload.get("available")) ? "completed" : "unavailable", 1,
                    String.valueOf(payload.get("errorMessage")));
            System.out.println("Futures position saved: " + payload.toJSONString());
            return;
        }

        throw new IllegalArgumentException("Unknown market data mode: " + mode);
    }

    @SuppressWarnings("unchecked")
    private static JSONObject priceToJson(TaiexFuturesPriceSnapshot snapshot) {
        JSONObject obj = new JSONObject();
        obj.put("available", Boolean.valueOf(snapshot.isAvailable()));
        obj.put("symbol", snapshot.getSymbol());
        obj.put("name", snapshot.getName());
        obj.put("source", snapshot.getSource());
        obj.put("errorMessage", snapshot.getErrorMessage());
        obj.put("currentPrice", Double.valueOf(round1(snapshot.getCurrentPrice())));
        obj.put("previousClose", Double.valueOf(round1(snapshot.getPreviousClose())));
        obj.put("change", Double.valueOf(round1(snapshot.getChange())));
        obj.put("changePct", Double.valueOf(round1(snapshot.getChangePct())));
        obj.put("volume", Long.valueOf(snapshot.getVolume()));
        obj.put("marketTime", snapshot.getMarketTime());
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject positionToJson(TaifexFuturesSnapshot snapshot) {
        JSONObject obj = new JSONObject();
        obj.put("available", Boolean.valueOf(snapshot.isAvailable()));
        obj.put("source", snapshot.getSource());
        obj.put("dataDate", snapshot.getDataDate());
        obj.put("productName", snapshot.getProductName());
        obj.put("identityName", snapshot.getIdentityName());
        obj.put("errorMessage", snapshot.getErrorMessage());
        obj.put("foreignOpenInterestLongLots", Long.valueOf(snapshot.getForeignOpenInterestLongLots()));
        obj.put("foreignOpenInterestShortLots", Long.valueOf(snapshot.getForeignOpenInterestShortLots()));
        obj.put("foreignOpenInterestNetLots", Long.valueOf(snapshot.getForeignOpenInterestNetLots()));
        obj.put("foreignTradingLongLots", Long.valueOf(snapshot.getForeignTradingLongLots()));
        obj.put("foreignTradingShortLots", Long.valueOf(snapshot.getForeignTradingShortLots()));
        obj.put("foreignTradingNetLots", Long.valueOf(snapshot.getForeignTradingNetLots()));
        return obj;
    }

    private static double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }
}
