package stock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
    private static final DateTimeFormatter DATE_STAMP_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
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

    public TaiexFuturesPriceSnapshot fetchNightClose(String tradeDateStamp) {
        try {
            LocalDate tradeDate = parseTradeDate(tradeDateStamp);
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

            int nightIndex = nightCloseIndex(timestamps, closes, tradeDate);
            if (nightIndex < 0) {
                return TaiexFuturesPriceSnapshot.unavailable(SOURCE,
                        "night close bar missing for " + tradeDate.format(DATE_STAMP_FORMAT));
            }
            double current = toDouble(closes.get(nightIndex));
            double previousClose = previousRegularClose(timestamps, closes, nightIndex, tradeDate);
            if (previousClose <= 0D) {
                previousClose = toDouble(meta == null ? null : meta.get("chartPreviousClose"));
            }
            long volume = volumes != null && nightIndex < volumes.size() ? Math.round(toDouble(volumes.get(nightIndex))) : 0L;
            double change = previousClose > 0D ? current - previousClose : 0D;
            double changePct = previousClose > 0D ? change * 100D / previousClose : 0D;
            String marketTime = TIME_FORMAT.format(Instant.ofEpochSecond(((Number) timestamps.get(nightIndex)).longValue()));
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

    private int nightCloseIndex(JSONArray timestamps, JSONArray closes, LocalDate tradeDate) {
        int bestIndex = -1;
        LocalTime windowStart = LocalTime.of(4, 30);
        LocalTime windowEnd = LocalTime.of(5, 20);
        for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
            if (!(timestamps.get(i) instanceof Number) || !(closes.get(i) instanceof Number)) {
                continue;
            }
            LocalDateTime localTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(((Number) timestamps.get(i)).longValue()), TAIPEI_ZONE);
            if (!tradeDate.equals(localTime.toLocalDate())) {
                continue;
            }
            LocalTime time = localTime.toLocalTime();
            if (!time.isBefore(windowStart) && !time.isAfter(windowEnd)) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double previousRegularClose(JSONArray timestamps, JSONArray closes, int nightIndex, LocalDate tradeDate) {
        long sessionStartEpoch = tradeDate.minusDays(1).atTime(15, 0).atZone(TAIPEI_ZONE).toEpochSecond();
        for (int i = Math.min(nightIndex - 1, timestamps.size() - 1); i >= 0 && i < closes.size(); i--) {
            if (!(timestamps.get(i) instanceof Number) || !(closes.get(i) instanceof Number)) {
                continue;
            }
            long epoch = ((Number) timestamps.get(i)).longValue();
            if (epoch < sessionStartEpoch) {
                return toDouble(closes.get(i));
            }
        }
        return previousNumericClose(closes, nightIndex);
    }

    private LocalDate parseTradeDate(String tradeDateStamp) {
        if (tradeDateStamp != null) {
            String trimmed = tradeDateStamp.trim();
            if (trimmed.length() == 8) {
                try {
                    return LocalDate.parse(trimmed, DATE_STAMP_FORMAT);
                } catch (DateTimeParseException ignored) {
                    // Fall through to Taipei today.
                }
            }
        }
        return LocalDate.now(TAIPEI_ZONE);
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }
}
