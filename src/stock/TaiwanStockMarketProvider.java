package stock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.TaiwanStockVO;

public class TaiwanStockMarketProvider {

    private static final String TWSE_STOCK_LIST_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap03_L";
    private static final String TPEX_STOCK_LIST_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public List<TaiwanStockVO> loadAllStocks() throws Exception {
        Map<String, TaiwanStockVO> allStocks = new LinkedHashMap<String, TaiwanStockVO>();
        loadTwseStocks(allStocks);
        loadTpexStocks(allStocks);
        return new ArrayList<TaiwanStockVO>(allStocks.values());
    }

    private void loadTwseStocks(Map<String, TaiwanStockVO> allStocks) throws Exception {
        JSONArray array = parseJsonArrayWithRetry(TWSE_STOCK_LIST_URL);
        for (Object item : array) {
            JSONObject json = (JSONObject) item;
            String code = stringValue(json, "公司代號");
            String name = stringValue(json, "公司簡稱");
            if (NumberParser.isFourDigitStockCode(code)) {
                allStocks.put(code + ".TW", new TaiwanStockVO(code, name, "TWSE", ".TW"));
            }
        }
    }

    private void loadTpexStocks(Map<String, TaiwanStockVO> allStocks) throws Exception {
        JSONArray array = parseJsonArrayWithRetry(TPEX_STOCK_LIST_URL);
        for (Object item : array) {
            JSONObject json = (JSONObject) item;
            String code = stringValue(json, "SecuritiesCompanyCode");
            String name = stringValue(json, "CompanyName");
            if (NumberParser.isFourDigitStockCode(code)) {
                allStocks.put(code + ".TWO", new TaiwanStockVO(code, name, "TPEX", ".TWO"));
            }
        }
    }

    private String stringValue(JSONObject json, String key) {
        Object value = json.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private JSONArray parseJsonArrayWithRetry(String url) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return (JSONArray) parser.parse(fetcher.fetchJson(url));
            } catch (ParseException ex) {
                lastException = ex;
                Thread.sleep(300L * attempt);
            }
        }
        throw lastException == null ? new Exception("Unable to load json array: " + url) : lastException;
    }
}
