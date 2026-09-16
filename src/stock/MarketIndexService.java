package stock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;

public class MarketIndexService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE_STAMP_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public MarketIndexSnapshot fetchTaiwanWeightedIndex() {
        return fetchTaiwanWeightedIndex(null);
    }

    public MarketIndexSnapshot fetchTaiwanWeightedIndex(String targetDateStamp) {
        try {
            String symbol = "^TWII";
            String name = "加權指數";
            String requestSymbol = "%5ETWII";
            String jsonText = fetcher.fetchJson(
                    "https://query1.finance.yahoo.com/v8/finance/chart/" + requestSymbol + "?range=1y&interval=1d");
            JSONObject root = (JSONObject) parser.parse(jsonText);
            JSONObject chart = (JSONObject) root.get("chart");
            JSONArray results = chart == null ? null : (JSONArray) chart.get("result");
            if (results == null || results.isEmpty()) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart", "chart api has no result");
            }

            JSONObject result = (JSONObject) results.get(0);
            JSONArray timestamps = (JSONArray) result.get("timestamp");
            JSONObject indicators = (JSONObject) result.get("indicators");
            JSONArray quoteArray = indicators == null ? null : (JSONArray) indicators.get("quote");
            if (quoteArray == null || quoteArray.isEmpty()) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart", "quote series missing");
            }
            JSONObject quote = (JSONObject) quoteArray.get(0);
            List<DailyBar> bars = extractDailyBars(timestamps, (JSONArray) quote.get("close"),
                    (JSONArray) quote.get("high"), (JSONArray) quote.get("low"), (JSONArray) quote.get("volume"),
                    parseTargetDate(targetDateStamp));
            if (bars.size() < 60) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart", "not enough chart data");
            }

            DailyBar latestBar = bars.get(bars.size() - 1);
            String dataDate = latestBar.date.format(DATE_STAMP_FORMAT);
            if (targetDateStamp != null && targetDateStamp.trim().length() > 0
                    && !normalizeDate(targetDateStamp).equals(dataDate)) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart",
                        "latest index date " + dataDate + " does not match snapshot " + normalizeDate(targetDateStamp));
            }

            List<Double> closes = new ArrayList<Double>();
            List<Double> highs = new ArrayList<Double>();
            List<Double> lows = new ArrayList<Double>();
            List<Long> volumes = new ArrayList<Long>();
            for (DailyBar bar : bars) {
                closes.add(Double.valueOf(bar.close));
                highs.add(Double.valueOf(bar.high));
                lows.add(Double.valueOf(bar.low));
                volumes.add(Long.valueOf(bar.volume));
            }

            double currentPrice = latestBar.close;
            double movingAverage20 = averageLast(closes, 20);
            double movingAverage60 = averageLast(closes, 60);
            double return20DayPct = percentChange(valueDaysAgo(closes, 20), currentPrice);
            double averageVolume20 = volumes.isEmpty() ? 0D : averageLastLong(volumes, 20);
            long currentVolume = volumes.isEmpty() ? 0L : volumes.get(volumes.size() - 1).longValue();
            double volumeRatio = averageVolume20 > 0D ? currentVolume / averageVolume20 : 0D;
            double[] macdSeries = computeMacd(closes);
            double ma20Slope = computeMa20Slope(closes);
            boolean recent20High = currentPrice >= maxLast(closes, 20) * 0.999D;
            List<Double> highSeries = highs.size() == closes.size() ? highs : closes;
            List<Double> lowSeries = lows.size() == closes.size() ? lows : closes;
            double atr20 = averageTrueRange(highSeries, lowSeries, closes, 20);
            double atr60 = averageTrueRange(highSeries, lowSeries, closes, 60);
            double atr20Pct = currentPrice > 0D ? atr20 * 100D / currentPrice : 0D;
            double atr60Pct = currentPrice > 0D ? atr60 * 100D / currentPrice : 0D;
            String trendLabel = resolveTrendLabel(currentPrice, movingAverage20, movingAverage60, macdSeries[2]);
            String divergenceLabel = resolveDivergenceLabel(return20DayPct, volumeRatio);

            return new MarketIndexSnapshot(true, symbol, name, "Yahoo Chart", "", dataDate, currentPrice, movingAverage20,
                    movingAverage60, return20DayPct, volumeRatio, macdSeries[0], macdSeries[1], macdSeries[2],
                    ma20Slope, recent20High, atr20Pct, atr60Pct, trendLabel, divergenceLabel);
        } catch (Exception ex) {
            return MarketIndexSnapshot.unavailable("^TWII", "加權指數", "Yahoo Chart", ex.getMessage());
        }
    }

    private List<DailyBar> extractDailyBars(JSONArray timestamps, JSONArray closes, JSONArray highs, JSONArray lows,
            JSONArray volumes, LocalDate targetDate) {
        List<DailyBar> bars = new ArrayList<DailyBar>();
        if (timestamps == null || closes == null) {
            return bars;
        }
        int size = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < size; i++) {
            if (!(timestamps.get(i) instanceof Number) || !(closes.get(i) instanceof Number)) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(((Number) timestamps.get(i)).longValue())
                    .atZone(TAIPEI_ZONE).toLocalDate();
            if (targetDate != null && date.isAfter(targetDate)) {
                continue;
            }
            double close = toDouble(closes.get(i));
            if (close <= 0D) {
                continue;
            }
            double high = numericAt(highs, i, close);
            double low = numericAt(lows, i, close);
            long volume = longAt(volumes, i);
            bars.add(new DailyBar(date, close, high, low, volume));
        }
        return bars;
    }

    private LocalDate parseTargetDate(String value) {
        String normalized = normalizeDate(value);
        if (normalized.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, DATE_STAMP_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeDate(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private double numericAt(JSONArray values, int index, double fallback) {
        if (values == null || index >= values.size() || !(values.get(index) instanceof Number)) {
            return fallback;
        }
        double value = toDouble(values.get(index));
        return value > 0D ? value : fallback;
    }

    private long longAt(JSONArray values, int index) {
        if (values == null || index >= values.size() || !(values.get(index) instanceof Number)) {
            return 0L;
        }
        return ((Number) values.get(index)).longValue();
    }

    private static final class DailyBar {
        private final LocalDate date;
        private final double close;
        private final double high;
        private final double low;
        private final long volume;

        private DailyBar(LocalDate date, double close, double high, double low, long volume) {
            this.date = date;
            this.close = close;
            this.high = high;
            this.low = low;
            this.volume = volume;
        }
    }

    private String resolveTrendLabel(double price, double movingAverage20, double movingAverage60, double macdHistogram) {
        if (price >= movingAverage20 && movingAverage20 >= movingAverage60 && macdHistogram >= 0D) {
            return "多頭慣性";
        }
        if (price < movingAverage20 && movingAverage20 < movingAverage60 && macdHistogram < 0D) {
            return "空頭慣性";
        }
        if (price >= movingAverage20 && macdHistogram >= 0D) {
            return "高檔震盪";
        }
        return "區間整理";
    }

    private String resolveDivergenceLabel(double return20DayPct, double volumeRatio) {
        if (return20DayPct > 0D && volumeRatio < 0.9D) {
            return "價漲量縮";
        }
        if (return20DayPct < 0D && volumeRatio > 1.2D) {
            return "價跌量增";
        }
        if (volumeRatio > 1.35D) {
            return "放量推進";
        }
        return "量價正常";
    }

    private double[] computeMacd(List<Double> closes) {
        List<Double> ema12 = computeEmaSeries(closes, 12);
        List<Double> ema26 = computeEmaSeries(closes, 26);
        List<Double> macdSeries = new ArrayList<Double>();
        for (int i = 0; i < closes.size(); i++) {
            macdSeries.add(Double.valueOf(ema12.get(i).doubleValue() - ema26.get(i).doubleValue()));
        }
        List<Double> signalSeries = computeEmaSeries(macdSeries, 9);
        double macd = macdSeries.get(macdSeries.size() - 1).doubleValue();
        double signal = signalSeries.get(signalSeries.size() - 1).doubleValue();
        return new double[] { macd, signal, macd - signal };
    }

    private double computeMa20Slope(List<Double> closes) {
        if (closes == null || closes.size() < 25) {
            return 0D;
        }
        double currentMa20 = averageWindow(closes, closes.size() - 20, closes.size());
        double previousMa20 = averageWindow(closes, closes.size() - 25, closes.size() - 5);
        return currentMa20 - previousMa20;
    }

    private List<Double> computeEmaSeries(List<Double> values, int period) {
        List<Double> ema = new ArrayList<Double>();
        if (values == null || values.isEmpty()) {
            return ema;
        }
        double multiplier = 2D / (period + 1D);
        double previous = values.get(0).doubleValue();
        for (Double value : values) {
            previous = (value.doubleValue() - previous) * multiplier + previous;
            ema.add(Double.valueOf(previous));
        }
        return ema;
    }

    private List<Double> extractDoubleSeries(JSONArray array) {
        List<Double> values = new ArrayList<Double>();
        if (array == null) {
            return values;
        }
        for (Object item : array) {
            double value = toDouble(item);
            if (value > 0D) {
                values.add(Double.valueOf(value));
            }
        }
        return values;
    }

    private List<Long> extractLongSeries(JSONArray array) {
        List<Long> values = new ArrayList<Long>();
        if (array == null) {
            return values;
        }
        for (Object item : array) {
            long value = toLong(item);
            if (value > 0L) {
                values.add(Long.valueOf(value));
            }
        }
        return values;
    }

    private double averageLast(List<Double> values, int period) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        int from = Math.max(0, values.size() - period);
        double sum = 0D;
        int count = 0;
        for (int i = from; i < values.size(); i++) {
            sum += values.get(i).doubleValue();
            count++;
        }
        return count > 0 ? sum / count : 0D;
    }

    private double averageWindow(List<Double> values, int fromInclusive, int toExclusive) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        int from = Math.max(0, fromInclusive);
        int to = Math.min(values.size(), toExclusive);
        if (from >= to) {
            return 0D;
        }
        double sum = 0D;
        int count = 0;
        for (int i = from; i < to; i++) {
            sum += values.get(i).doubleValue();
            count++;
        }
        return count > 0 ? sum / count : 0D;
    }

    private double averageLastLong(List<Long> values, int period) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        int from = Math.max(0, values.size() - period);
        double sum = 0D;
        int count = 0;
        for (int i = from; i < values.size(); i++) {
            sum += values.get(i).longValue();
            count++;
        }
        return count > 0 ? sum / count : 0D;
    }

    private double valueDaysAgo(List<Double> values, int days) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        int index = Math.max(0, values.size() - days - 1);
        return values.get(index).doubleValue();
    }

    private double maxLast(List<Double> values, int period) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        int from = Math.max(0, values.size() - period);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = from; i < values.size(); i++) {
            max = Math.max(max, values.get(i).doubleValue());
        }
        return max == Double.NEGATIVE_INFINITY ? 0D : max;
    }

    private double averageTrueRange(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        if (closes == null || closes.size() < 2) {
            return 0D;
        }
        int size = Math.min(closes.size(), Math.min(highs.size(), lows.size()));
        if (size < 2) {
            return 0D;
        }
        int from = Math.max(1, size - period);
        double total = 0D;
        int count = 0;
        for (int i = from; i < size; i++) {
            double high = highs.get(i).doubleValue();
            double low = lows.get(i).doubleValue();
            double prevClose = closes.get(i - 1).doubleValue();
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            total += tr;
            count++;
        }
        return count > 0 ? total / count : 0D;
    }

    private double percentChange(double previous, double current) {
        if (previous <= 0D || current <= 0D) {
            return 0D;
        }
        return (current - previous) * 100D / previous;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return NumberParser.parseDouble(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return NumberParser.parseLong(String.valueOf(value));
    }
}
