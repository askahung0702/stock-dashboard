package stock;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;

public class MarketIndexService {

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public MarketIndexSnapshot fetchTaiwanWeightedIndex() {
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
            JSONObject meta = (JSONObject) result.get("meta");
            JSONObject indicators = (JSONObject) result.get("indicators");
            JSONArray quoteArray = indicators == null ? null : (JSONArray) indicators.get("quote");
            if (quoteArray == null || quoteArray.isEmpty()) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart", "quote series missing");
            }
            JSONObject quote = (JSONObject) quoteArray.get(0);
            List<Double> closes = extractDoubleSeries((JSONArray) quote.get("close"));
            List<Double> highs = extractDoubleSeries((JSONArray) quote.get("high"));
            List<Double> lows = extractDoubleSeries((JSONArray) quote.get("low"));
            List<Long> volumes = extractLongSeries((JSONArray) quote.get("volume"));
            if (closes.size() < 60) {
                return MarketIndexSnapshot.unavailable(symbol, name, "Yahoo Chart", "not enough chart data");
            }

            double currentPrice = toDouble(meta == null ? null : meta.get("regularMarketPrice"));
            if (currentPrice <= 0D) {
                currentPrice = closes.get(closes.size() - 1).doubleValue();
            }
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

            return new MarketIndexSnapshot(true, symbol, name, "Yahoo Chart", "", currentPrice, movingAverage20,
                    movingAverage60, return20DayPct, volumeRatio, macdSeries[0], macdSeries[1], macdSeries[2],
                    ma20Slope, recent20High, atr20Pct, atr60Pct, trendLabel, divergenceLabel);
        } catch (Exception ex) {
            return MarketIndexSnapshot.unavailable("^TWII", "加權指數", "Yahoo Chart", ex.getMessage());
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
