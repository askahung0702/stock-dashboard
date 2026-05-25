package stock;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.MarginTradingVO;

public class MarginTradingService {

    private static final String TWSE_URL =
            "https://www.twse.com.tw/rwd/zh/marginTrading/MI_MARGN?date=%s&selectType=ALL&response=json";
    private static final String TWSE_OPENAPI_URL =
            "https://openapi.twse.com.tw/v1/exchangeReport/MI_MARGN";
    private static final String TPEX_URL =
            "https://www.tpex.org.tw/web/stock/margin_trading/margin_balance/margin_bal_result.php?l=zh-tw&d=%s&s=0,asc,0";

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public Map<String, MarginTradingVO> loadMarginTrading(String tradeDate) {
        Map<String, MarginTradingVO> rowsByCode = new HashMap<String, MarginTradingVO>();
        if (tradeDate == null || tradeDate.length() == 0) {
            return rowsByCode;
        }
        loadTwse(rowsByCode, tradeDate);
        loadTpex(rowsByCode, tradeDate);
        return rowsByCode;
    }

    private void loadTwse(Map<String, MarginTradingVO> rowsByCode, String tradeDate) {
        int before = rowsByCode.size();
        if (loadTwseOpenApi(rowsByCode, tradeDate)) {
            System.out.println("TWSE OpenAPI margin trading loaded: " + (rowsByCode.size() - before));
            return;
        }
        try {
            JSONObject root = (JSONObject) parser.parse(fetcher.fetchJson(String.format(TWSE_URL, tradeDate), 15000, 2));
            JSONArray tables = (JSONArray) root.get("tables");
            if (tables == null) {
                return;
            }
            for (Object tableObj : tables) {
                if (!(tableObj instanceof JSONObject)) {
                    continue;
                }
                JSONObject table = (JSONObject) tableObj;
                String title = text(table.get("title"));
                if (title.indexOf("融資融券彙總") < 0) {
                    continue;
                }
                JSONArray data = (JSONArray) table.get("data");
                if (data == null) {
                    continue;
                }
                for (Object rowObj : data) {
                    if (!(rowObj instanceof JSONArray)) {
                        continue;
                    }
                    MarginTradingVO row = parseTwseRow((JSONArray) rowObj, tradeDate);
                    if (NumberParser.isFourDigitStockCode(row.getDataDate().length() > 0 ? codeAt((JSONArray) rowObj) : "")) {
                        rowsByCode.put(codeAt((JSONArray) rowObj), row);
                    }
                }
            }
            System.out.println("TWSE margin trading loaded: " + (rowsByCode.size() - before));
        } catch (Exception ex) {
            System.out.println("TWSE margin trading unavailable: " + ex.getMessage());
        }
    }

    private boolean loadTwseOpenApi(Map<String, MarginTradingVO> rowsByCode, String tradeDate) {
        try {
            JSONArray rows = (JSONArray) parser.parse(fetcher.fetchJson(TWSE_OPENAPI_URL, 15000, 2));
            int loaded = 0;
            for (Object rowObj : rows) {
                if (!(rowObj instanceof JSONObject)) {
                    continue;
                }
                JSONObject json = (JSONObject) rowObj;
                String code = text(json.get("股票代號"));
                if (!NumberParser.isFourDigitStockCode(code)) {
                    continue;
                }
                rowsByCode.put(code, parseTwseOpenApiRow(json, tradeDate));
                loaded++;
            }
            return loaded > 0;
        } catch (Exception ex) {
            System.out.println("TWSE OpenAPI margin trading unavailable: " + ex.getMessage());
            return false;
        }
    }

    private MarginTradingVO parseTwseOpenApiRow(JSONObject json, String tradeDate) {
        MarginTradingVO row = new MarginTradingVO();
        row.setDataDate(tradeDate);
        row.setSource("TWSE OpenAPI MI_MARGN");
        row.setMarginBuy(longValue(json.get("融資買進")));
        row.setMarginSell(longValue(json.get("融資賣出")));
        row.setMarginCashRepay(longValue(json.get("融資現金償還")));
        row.setPreviousMarginBalance(longValue(json.get("融資前日餘額")));
        row.setMarginBalance(longValue(json.get("融資今日餘額")));
        row.setMarginLimit(longValue(json.get("融資限額")));
        row.setMarginUsagePct(usagePct(row.getMarginBalance(), row.getMarginLimit()));
        row.setShortBuy(longValue(json.get("融券買進")));
        row.setShortSell(longValue(json.get("融券賣出")));
        row.setShortRepay(longValue(json.get("融券現券償還")));
        row.setPreviousShortBalance(longValue(json.get("融券前日餘額")));
        row.setShortBalance(longValue(json.get("融券今日餘額")));
        row.setShortLimit(longValue(json.get("融券限額")));
        row.setShortUsagePct(usagePct(row.getShortBalance(), row.getShortLimit()));
        row.setOffsetLots(longValue(json.get("資券互抵")));
        row.setNote(text(json.get("註記")));
        return row;
    }

    private MarginTradingVO parseTwseRow(JSONArray fields, String tradeDate) {
        MarginTradingVO row = new MarginTradingVO();
        row.setDataDate(tradeDate);
        row.setSource("TWSE MI_MARGN");
        row.setMarginBuy(longAt(fields, 2));
        row.setMarginSell(longAt(fields, 3));
        row.setMarginCashRepay(longAt(fields, 4));
        row.setPreviousMarginBalance(longAt(fields, 5));
        row.setMarginBalance(longAt(fields, 6));
        row.setMarginLimit(longAt(fields, 7));
        row.setMarginUsagePct(usagePct(row.getMarginBalance(), row.getMarginLimit()));
        row.setShortBuy(longAt(fields, 8));
        row.setShortSell(longAt(fields, 9));
        row.setShortRepay(longAt(fields, 10));
        row.setPreviousShortBalance(longAt(fields, 11));
        row.setShortBalance(longAt(fields, 12));
        row.setShortLimit(longAt(fields, 13));
        row.setShortUsagePct(usagePct(row.getShortBalance(), row.getShortLimit()));
        row.setOffsetLots(longAt(fields, 14));
        row.setNote(textAt(fields, 15));
        return row;
    }

    private void loadTpex(Map<String, MarginTradingVO> rowsByCode, String tradeDate) {
        int before = rowsByCode.size();
        try {
            JSONObject root = (JSONObject) parser.parse(fetcher.fetchJson(String.format(TPEX_URL, toRocDate(tradeDate)),
                    15000, 2));
            JSONArray tables = (JSONArray) root.get("tables");
            if (tables == null) {
                return;
            }
            for (Object tableObj : tables) {
                if (!(tableObj instanceof JSONObject)) {
                    continue;
                }
                JSONArray data = (JSONArray) ((JSONObject) tableObj).get("data");
                if (data == null) {
                    continue;
                }
                for (Object rowObj : data) {
                    if (!(rowObj instanceof JSONArray)) {
                        continue;
                    }
                    JSONArray fields = (JSONArray) rowObj;
                    String code = codeAt(fields);
                    if (!NumberParser.isFourDigitStockCode(code)) {
                        continue;
                    }
                    rowsByCode.put(code, parseTpexRow(fields, tradeDate));
                }
            }
            System.out.println("TPEX margin trading loaded: " + (rowsByCode.size() - before));
        } catch (Exception ex) {
            System.out.println("TPEX margin trading unavailable: " + ex.getMessage());
        }
    }

    private MarginTradingVO parseTpexRow(JSONArray fields, String tradeDate) {
        MarginTradingVO row = new MarginTradingVO();
        row.setDataDate(tradeDate);
        row.setSource("TPEX margin balance");
        row.setPreviousMarginBalance(longAt(fields, 2));
        row.setMarginBuy(longAt(fields, 3));
        row.setMarginSell(longAt(fields, 4));
        row.setMarginCashRepay(longAt(fields, 5));
        row.setMarginBalance(longAt(fields, 6));
        row.setMarginUsagePct(doubleAt(fields, 8));
        row.setMarginLimit(longAt(fields, 9));
        row.setPreviousShortBalance(longAt(fields, 10));
        row.setShortSell(longAt(fields, 11));
        row.setShortBuy(longAt(fields, 12));
        row.setShortRepay(longAt(fields, 13));
        row.setShortBalance(longAt(fields, 14));
        row.setShortUsagePct(doubleAt(fields, 16));
        row.setShortLimit(longAt(fields, 17));
        row.setOffsetLots(longAt(fields, 18));
        row.setNote(textAt(fields, 19));
        return row;
    }

    private String toRocDate(String date) {
        if (date == null || !date.matches("\\d{8}")) {
            return "";
        }
        int year = Integer.parseInt(date.substring(0, 4)) - 1911;
        return Integer.toString(year) + "/" + date.substring(4, 6) + "/" + date.substring(6, 8);
    }

    private String codeAt(JSONArray fields) {
        return textAt(fields, 0);
    }

    private long longAt(JSONArray fields, int index) {
        return NumberParser.parseLong(textAt(fields, index));
    }

    private long longValue(Object value) {
        return NumberParser.parseLong(text(value));
    }

    private double doubleAt(JSONArray fields, int index) {
        return NumberParser.parseDouble(textAt(fields, index));
    }

    private double usagePct(long balance, long limit) {
        return limit <= 0L ? 0D : balance * 100D / limit;
    }

    private String textAt(JSONArray fields, int index) {
        if (fields == null || index < 0 || index >= fields.size()) {
            return "";
        }
        return text(fields.get(index));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
