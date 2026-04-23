package stock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;

public class TaiexFuturesPriceService {

    private static final String SYMBOL = "IX0126.TW";
    private static final String NAME = "台指期";
    private static final String SOURCE = "Yahoo Chart";
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(TAIPEI_ZONE);
    private final HttpTextFetcher fetcher = new HttpTextFetcher();

    public TaiexFuturesPriceSnapshot fetchLatest() {
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + SYMBOL + "?range=5d&interval=1m";
            String jsonText = fetcher.fetchJson(url, 15000, 2);
            JSONObject root = (JSONObject) new JSONParser().parse(jsonText);
            JSONObject chart = (JSONObject) root.get("chart");
            JSONArray results = chart == null ? null : (JSONArray) chart.get("result");
            if (results == null || results.isEmpty()) {
                return TaiexFuturesPriceSnapshot.unavailable(SOURCE, "chart api has no result");
            }
            JSONObject result = (JSONObject) results.get(0);
            JSONObject meta = (JSONObject) result.get("meta");
            JSONArray timestamps = (JSONArray) result.get("timestamp");
            JSONObject indicators = (JSONObject) result.get("indicators");
            JSONArray quoteArray = indicators == null ? null : (JSONArray) indicators.get("quote");
            JSONObject quote = quoteArray == null || quoteArray.isEmpty() ? null : (JSONObject) quoteArray.get(0);
            JSONArray closes = quote == null ? null : (JSONArray) quote.get("close");
            JSONArray volumes = quote == null ? null : (JSONArray) quote.get("volume");
            if (timestamps == null || closes == null || closes.isEmpty()) {
                return TaiexFuturesPriceSnapshot.unavailable(SOURCE, "quote series missing");
            }

            int latestIndex = latestNumericIndex(closes);
            if (latestIndex < 0) {
                return TaiexFuturesPriceSnapshot.unavailable(SOURCE, "latest close missing");
            }
            double current = toDouble(closes.get(latestIndex));
            double previousClose = toDouble(meta == null ? null : meta.get("chartPreviousClose"));
            if (previousClose <= 0D) {
                previousClose = previousNumericClose(closes, latestIndex);
            }
            long volume = volumes != null && latestIndex < volumes.size() ? Math.round(toDouble(volumes.get(latestIndex))) : 0L;
            double change = previousClose > 0D ? current - previousClose : 0D;
            double changePct = previousClose > 0D ? change * 100D / previousClose : 0D;
            String marketTime = "";
            if (latestIndex < timestamps.size()) {
                marketTime = TIME_FORMAT.format(Instant.ofEpochSecond(((Number) timestamps.get(latestIndex)).longValue()));
            }
            return new TaiexFuturesPriceSnapshot(true, SYMBOL, NAME, SOURCE, "", current, previousClose, change,
                    changePct, volume, marketTime);
        } catch (Exception ex) {
            return TaiexFuturesPriceSnapshot.unavailable(SOURCE, ex.getMessage());
        }
    }

    private int latestNumericIndex(JSONArray values) {
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) instanceof Number) {
                return i;
            }
        }
        return -1;
    }

    private double previousNumericClose(JSONArray values, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            if (values.get(i) instanceof Number) {
                return toDouble(values.get(i));
            }
        }
        return 0D;
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }
}
