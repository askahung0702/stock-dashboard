package stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.vo.BalanceSheetRecordVO;
import stock.vo.CashFlowRecordVO;
import stock.vo.EpsRecordVO;
import stock.vo.IncomeStatementRecordVO;
import stock.vo.MonthlyRevenueVO;
import stock.vo.TaiwanStockVO;

public class TwseOpenApiFinancialProvider implements FinancialDataProvider {

    private static final String REVENUE_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap05_L";
    private static final String[] INCOME_URLS = {
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_ci",
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_basi",
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_fh",
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_ins",
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_bd",
            "https://openapi.twse.com.tw/v1/opendata/t187ap06_L_mim" };
    private static final String[] BALANCE_URLS = {
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_ci",
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_basi",
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_fh",
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_ins",
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_bd",
            "https://openapi.twse.com.tw/v1/opendata/t187ap07_L_mim" };

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    private Map<String, MonthlyRevenueVO> revenuesByCode;
    private Map<String, IncomeStatementRecordVO> incomeByCode;
    private Map<String, EpsRecordVO> epsByCode;
    private Map<String, BalanceSheetRecordVO> balanceByCode;

    public String providerName() {
        return "TWSE OpenAPI";
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("stock.twseOpenApi.financial.enabled", "true"));
    }

    public FinancialDataBundle fetch(TaiwanStockVO stock) throws Exception {
        if (!isEnabled() || stock == null || !"TWSE".equalsIgnoreCase(stock.getMarket())) {
            return FinancialDataBundle.empty(providerName());
        }
        ensureLoaded();
        String code = stock.getCode();
        List<MonthlyRevenueVO> revenues = new ArrayList<MonthlyRevenueVO>();
        List<EpsRecordVO> epsRecords = new ArrayList<EpsRecordVO>();
        List<IncomeStatementRecordVO> incomeRecords = new ArrayList<IncomeStatementRecordVO>();
        List<BalanceSheetRecordVO> balanceRecords = new ArrayList<BalanceSheetRecordVO>();
        MonthlyRevenueVO revenue = revenuesByCode.get(code);
        EpsRecordVO eps = epsByCode.get(code);
        IncomeStatementRecordVO income = incomeByCode.get(code);
        BalanceSheetRecordVO balance = balanceByCode.get(code);
        if (revenue != null) {
            revenues.add(revenue);
        }
        if (eps != null) {
            epsRecords.add(eps);
        }
        if (income != null) {
            incomeRecords.add(income);
        }
        if (balance != null) {
            balanceRecords.add(balance);
        }
        return new FinancialDataBundle(providerName(), revenues, epsRecords, incomeRecords, balanceRecords,
                new ArrayList<CashFlowRecordVO>());
    }

    private synchronized void ensureLoaded() throws Exception {
        if (revenuesByCode != null && incomeByCode != null && balanceByCode != null && epsByCode != null) {
            return;
        }
        revenuesByCode = loadRevenueRows();
        incomeByCode = new HashMap<String, IncomeStatementRecordVO>();
        epsByCode = new HashMap<String, EpsRecordVO>();
        for (String url : INCOME_URLS) {
            loadIncomeRows(url, incomeByCode, epsByCode);
        }
        balanceByCode = new HashMap<String, BalanceSheetRecordVO>();
        for (String url : BALANCE_URLS) {
            loadBalanceRows(url, balanceByCode);
        }
        System.out.println("TWSE OpenAPI financial loaded: revenue=" + revenuesByCode.size()
                + " income=" + incomeByCode.size() + " balance=" + balanceByCode.size());
    }

    private Map<String, MonthlyRevenueVO> loadRevenueRows() throws Exception {
        Map<String, MonthlyRevenueVO> rowsByCode = new HashMap<String, MonthlyRevenueVO>();
        JSONArray rows = fetchArray(REVENUE_URL);
        for (Object object : rows) {
            if (!(object instanceof JSONObject)) {
                continue;
            }
            JSONObject row = (JSONObject) object;
            String code = text(row.get("公司代號"));
            String period = monthPeriod(text(row.get("資料年月")));
            if (code.length() == 0 || period.length() == 0) {
                continue;
            }
            rowsByCode.put(code, new MonthlyRevenueVO(period, longValue(row.get("營業收入-當月營收")),
                    doubleValue(row.get("營業收入-上月比較增減(%)")), longValue(row.get("營業收入-去年當月營收")),
                    doubleValue(row.get("營業收入-去年同月增減(%)")), longValue(row.get("累計營業收入-當月累計營收")),
                    longValue(row.get("累計營業收入-去年累計營收")),
                    doubleValue(row.get("累計營業收入-前期比較增減(%)"))));
        }
        return rowsByCode;
    }

    private void loadIncomeRows(String url, Map<String, IncomeStatementRecordVO> incomeRows,
            Map<String, EpsRecordVO> epsRows) throws Exception {
        JSONArray rows = fetchArray(url);
        for (Object object : rows) {
            if (!(object instanceof JSONObject)) {
                continue;
            }
            JSONObject row = (JSONObject) object;
            String code = text(row.get("公司代號"));
            String period = quarterPeriod(text(row.get("年度")), text(row.get("季別")));
            if (code.length() == 0 || period.length() == 0) {
                continue;
            }
            long netIncome = firstLong(row, "淨利（淨損）歸屬於母公司業主", "本期淨利（淨損）");
            incomeRows.put(code, new IncomeStatementRecordVO(period, longValue(row.get("營業收入")),
                    firstLong(row, "營業毛利（毛損）淨額", "營業毛利（毛損）"), longValue(row.get("營業利益（損失）")),
                    netIncome));
            double eps = doubleValue(row.get("基本每股盈餘（元）"));
            if (eps != 0D) {
                epsRows.put(code, new EpsRecordVO(period, eps, 0D, 0D, 0D));
            }
        }
    }

    private void loadBalanceRows(String url, Map<String, BalanceSheetRecordVO> balanceRows) throws Exception {
        JSONArray rows = fetchArray(url);
        for (Object object : rows) {
            if (!(object instanceof JSONObject)) {
                continue;
            }
            JSONObject row = (JSONObject) object;
            String code = text(row.get("公司代號"));
            String period = quarterPeriod(text(row.get("年度")), text(row.get("季別")));
            if (code.length() == 0 || period.length() == 0) {
                continue;
            }
            balanceRows.put(code, new BalanceSheetRecordVO(period, longValue(row.get("資產總額")),
                    longValue(row.get("負債總額")), firstLong(row, "權益總額", "歸屬於母公司業主之權益合計"),
                    longValue(row.get("流動資產")), longValue(row.get("流動負債"))));
        }
    }

    private JSONArray fetchArray(String url) throws Exception {
        return (JSONArray) parser.parse(fetcher.fetchJson(url, 20000, 2));
    }

    private String monthPeriod(String rocYm) {
        String text = text(rocYm);
        if (!text.matches("\\d{5}")) {
            return "";
        }
        int year = Integer.parseInt(text.substring(0, 3)) + 1911;
        return year + "/" + text.substring(3, 5);
    }

    private String quarterPeriod(String rocYear, String quarter) {
        try {
            int year = Integer.parseInt(text(rocYear)) + 1911;
            int q = Integer.parseInt(text(quarter));
            if (q < 1 || q > 4) {
                return "";
            }
            return year + " Q" + q;
        } catch (Exception ex) {
            return "";
        }
    }

    private long firstLong(JSONObject row, String... keys) {
        for (String key : keys) {
            long value = longValue(row.get(key));
            if (value != 0L) {
                return value;
            }
        }
        return 0L;
    }

    private long longValue(Object value) {
        String text = text(value).replace(",", "");
        if (text.length() == 0 || "-".equals(text) || "--".equals(text)) {
            return 0L;
        }
        try {
            return Math.round(Double.parseDouble(text));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double doubleValue(Object value) {
        String text = text(value).replace(",", "").replace("%", "");
        if (text.length() == 0 || "-".equals(text) || "--".equals(text)) {
            return 0D;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return 0D;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
