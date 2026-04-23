package stock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import stock.common.HttpTextFetcher;

public class TaifexFuturesService {

    private static final String SOURCE = "TAIFEX 三大法人區分各期貨契約";
    private static final String URL = "https://www.taifex.com.tw/cht/3/futContractsDate?doQuery=1&queryType=1";
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})");
    private static final Pattern TX_FOREIGN_PATTERN = Pattern.compile(
            "(?:臺股期貨|台股期貨)\\s+.*?外資(?:及陸資)?\\s+((?:[-]?[0-9,]+\\s+){10,18})",
            Pattern.CASE_INSENSITIVE);
    private final HttpTextFetcher fetcher = new HttpTextFetcher();

    public TaifexFuturesSnapshot fetchTaiwanIndexFuturesForeignPosition() {
        try {
            String text = fetcher.fetchPageText(URL);
            Matcher matcher = TX_FOREIGN_PATTERN.matcher(text);
            if (!matcher.find()) {
                return TaifexFuturesSnapshot.unavailable(SOURCE, "找不到臺股期貨外資資料列");
            }
            List<Long> values = parseNumbers(matcher.group(1));
            if (values.size() < 11) {
                return TaifexFuturesSnapshot.unavailable(SOURCE, "臺股期貨外資欄位數不足: " + values.size());
            }
            String date = "";
            Matcher dateMatcher = DATE_PATTERN.matcher(text);
            if (dateMatcher.find()) {
                date = dateMatcher.group(1);
            }

            // TAIFEX row order: trading long/short/net, then open-interest long/short/net.
            long tradingLong = values.get(0).longValue();
            long tradingShort = values.get(2).longValue();
            long tradingNet = values.get(4).longValue();
            long oiLong = values.get(6).longValue();
            long oiShort = values.get(8).longValue();
            long oiNet = values.get(10).longValue();
            return new TaifexFuturesSnapshot(true, SOURCE, date, "臺股期貨", "外資", "", oiLong, oiShort, oiNet,
                    tradingLong, tradingShort, tradingNet);
        } catch (Exception ex) {
            return TaifexFuturesSnapshot.unavailable(SOURCE, ex.getMessage());
        }
    }

    private List<Long> parseNumbers(String text) {
        List<Long> values = new ArrayList<Long>();
        Matcher matcher = Pattern.compile("-?[0-9,]+").matcher(text == null ? "" : text);
        while (matcher.find()) {
            values.add(Long.valueOf(matcher.group().replace(",", "")));
        }
        return values;
    }
}
