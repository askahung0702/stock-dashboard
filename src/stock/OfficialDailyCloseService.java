package stock;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.TaiwanStockVO;

public class OfficialDailyCloseService {

    private static final String TWSE_DAILY_CLOSE_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    private static final String TPEX_QUOTES_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public Map<String, Double> loadClosePrices(List<TaiwanStockVO> stocks, String tradeDate) {
        Map<String, Double> pricesByCode = new HashMap<String, Double>();
        loadTwseClosePrices(pricesByCode, tradeDate);
        loadTpexClosePrices(pricesByCode, tradeDate);
        loadMisClosePrices(pricesByCode, stocks, tradeDate);
        return pricesByCode;
    }

    private void loadTwseClosePrices(Map<String, Double> pricesByCode, String tradeDate) {
        try {
            JSONArray array = (JSONArray) parser.parse(fetcher.fetchJson(TWSE_DAILY_CLOSE_URL, 15000, 2));
            for (Object item : array) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject row = (JSONObject) item;
                String code = text(row.get("Code"));
                String rowDate = normalizeTradeDate(row.get("Date"));
                if (tradeDate != null && tradeDate.length() > 0 && !tradeDate.equals(rowDate)) {
                    continue;
                }
                double close = parsePrice(row.get("ClosingPrice"));
                if (NumberParser.isFourDigitStockCode(code) && close > 0D) {
                    pricesByCode.put(code, Double.valueOf(close));
                }
            }
            System.out.println("Official TWSE close prices loaded: " + pricesByCode.size());
        } catch (Exception ex) {
            System.out.println("Official TWSE close prices unavailable: " + ex.getMessage());
        }
    }

    private void loadTpexClosePrices(Map<String, Double> pricesByCode, String tradeDate) {
        int before = pricesByCode.size();
        try {
            JSONArray array = (JSONArray) parser.parse(fetcher.fetchJson(TPEX_QUOTES_URL, 15000, 2));
            for (Object item : array) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject row = (JSONObject) item;
                String code = firstText(row, "SecuritiesCompanyCode", "Code", "SecuritiesCode");
                String rowDate = normalizeTradeDate(firstText(row, "Date", "TradeDate", "TradingDate"));
                if (rowDate.length() > 0 && tradeDate != null && tradeDate.length() > 0 && !tradeDate.equals(rowDate)) {
                    continue;
                }
                double close = firstPrice(row, "Close", "ClosingPrice", "ClosePrice", "LatestPrice", "Last");
                if (NumberParser.isFourDigitStockCode(code) && close > 0D) {
                    pricesByCode.put(code, Double.valueOf(close));
                }
            }
            System.out.println("Official TPEX close prices loaded: " + (pricesByCode.size() - before));
        } catch (Exception ex) {
            System.out.println("Official TPEX close prices unavailable: " + ex.getMessage());
        }
    }

    private void loadMisClosePrices(Map<String, Double> pricesByCode, List<TaiwanStockVO> stocks, String tradeDate) {
        if (stocks == null || stocks.isEmpty() || tradeDate == null || tradeDate.length() == 0) {
            return;
        }
        int loaded = 0;
        int batchSize = 80;
        for (int start = 0; start < stocks.size(); start += batchSize) {
            try {
                StringBuilder channels = new StringBuilder();
                int end = Math.min(stocks.size(), start + batchSize);
                for (int i = start; i < end; i++) {
                    TaiwanStockVO stock = stocks.get(i);
                    if (stock == null || !NumberParser.isFourDigitStockCode(stock.getCode())) {
                        continue;
                    }
                    if (channels.length() > 0) {
                        channels.append("|");
                    }
                    channels.append("TPEX".equals(stock.getMarket()) ? "otc_" : "tse_")
                            .append(stock.getCode())
                            .append(".tw");
                }
                if (channels.length() == 0) {
                    continue;
                }
                String url = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?json=1&delay=0&ex_ch="
                        + URLEncoder.encode(channels.toString(), "UTF-8") + "&_=" + System.currentTimeMillis();
                JSONObject root = (JSONObject) parser.parse(fetcher.fetchJson(url, 15000, 2));
                JSONArray rows = (JSONArray) root.get("msgArray");
                if (rows == null) {
                    continue;
                }
                for (Object item : rows) {
                    if (!(item instanceof JSONObject)) {
                        continue;
                    }
                    JSONObject row = (JSONObject) item;
                    String code = text(row.get("c"));
                    String rowDate = text(row.get("d"));
                    double close = firstPrice(row, "z", "pz", "oz");
                    if (NumberParser.isFourDigitStockCode(code) && tradeDate.equals(rowDate) && close > 0D) {
                        pricesByCode.put(code, Double.valueOf(close));
                        loaded++;
                    }
                }
            } catch (Exception ex) {
                System.out.println("TWSE MIS close batch unavailable: " + ex.getMessage());
            }
        }
        System.out.println("TWSE MIS close prices loaded: " + loaded);
    }

    private String firstText(JSONObject row, String... keys) {
        for (String key : keys) {
            String value = text(row.get(key));
            if (value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private double firstPrice(JSONObject row, String... keys) {
        for (String key : keys) {
            double value = parsePrice(row.get(key));
            if (value > 0D) {
                return value;
            }
        }
        return 0D;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double parsePrice(Object value) {
        String text = text(value).replace(",", "");
        if (text.length() == 0 || "--".equals(text) || "-".equals(text)
                || !text.matches("[-+]?\\d+(\\.\\d+)?")) {
            return 0D;
        }
        return NumberParser.parseDouble(text);
    }

    private String normalizeTradeDate(Object value) {
        String text = text(value).replace("/", "").replace("-", "");
        if (text.length() == 7 && text.matches("\\d+")) {
            int rocYear = Integer.parseInt(text.substring(0, 3));
            return Integer.toString(rocYear + 1911) + text.substring(3);
        }
        if (text.length() == 8 && text.matches("\\d+")) {
            return text;
        }
        return "";
    }
}
