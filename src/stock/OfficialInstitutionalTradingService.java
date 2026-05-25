package stock;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.InstitutionalTradingDailyVO;

public class OfficialInstitutionalTradingService {

    private static final String TWSE_T86_URL =
            "https://www.twse.com.tw/rwd/zh/fund/T86?date=%s&selectType=ALLBUT0999&response=json";

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public Map<String, InstitutionalTradingDailyVO> loadDailyTrading(String tradeDate) {
        Map<String, InstitutionalTradingDailyVO> rowsByCode = new HashMap<String, InstitutionalTradingDailyVO>();
        if (tradeDate == null || !tradeDate.matches("\\d{8}")) {
            return rowsByCode;
        }
        loadTwse(rowsByCode, tradeDate);
        return rowsByCode;
    }

    private void loadTwse(Map<String, InstitutionalTradingDailyVO> rowsByCode, String tradeDate) {
        try {
            JSONObject root = (JSONObject) parser.parse(fetcher.fetchJson(String.format(TWSE_T86_URL, tradeDate),
                    15000, 2));
            JSONArray data = (JSONArray) root.get("data");
            if (data == null) {
                return;
            }
            for (Object rowObj : data) {
                if (!(rowObj instanceof JSONArray)) {
                    continue;
                }
                JSONArray fields = (JSONArray) rowObj;
                String code = textAt(fields, 0);
                if (!NumberParser.isFourDigitStockCode(code)) {
                    continue;
                }
                rowsByCode.put(code, parseTwseRow(fields, tradeDate));
            }
            System.out.println("TWSE T86 institutional trading loaded: " + rowsByCode.size());
        } catch (Exception ex) {
            System.out.println("TWSE T86 institutional trading unavailable: " + ex.getMessage());
        }
    }

    private InstitutionalTradingDailyVO parseTwseRow(JSONArray fields, String tradeDate) {
        long foreignNetLots = sharesToLots(longAt(fields, 4));
        long trustNetLots = sharesToLots(longAt(fields, 10));
        long dealerNetLots = sharesToLots(longAt(fields, 11));
        long totalNetLots = sharesToLots(longAt(fields, 18));
        return new InstitutionalTradingDailyVO(toDateText(tradeDate), foreignNetLots, trustNetLots, dealerNetLots,
                totalNetLots, 0D, 0D, 0L);
    }

    private String toDateText(String date) {
        return date == null || !date.matches("\\d{8}") ? "" : date.substring(0, 4) + "/" + date.substring(4, 6)
                + "/" + date.substring(6, 8);
    }

    private long sharesToLots(long shares) {
        return Math.round(shares / 1000D);
    }

    private long longAt(JSONArray fields, int index) {
        return NumberParser.parseLong(textAt(fields, index));
    }

    private String textAt(JSONArray fields, int index) {
        if (fields == null || index < 0 || index >= fields.size()) {
            return "";
        }
        Object value = fields.get(index);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
