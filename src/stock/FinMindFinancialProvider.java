package stock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.vo.BalanceSheetRecordVO;
import stock.vo.CashFlowRecordVO;
import stock.vo.EpsRecordVO;
import stock.vo.IncomeStatementRecordVO;
import stock.vo.MonthlyRevenueVO;
import stock.vo.TaiwanStockVO;

public class FinMindFinancialProvider implements FinancialDataProvider {

    private static final String API_URL = "https://api.finmindtrade.com/api/v4/data";
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int TIMEOUT_MS = 20000;

    private final String token;
    private final JSONParser parser = new JSONParser();

    public FinMindFinancialProvider() {
        this.token = resolveToken();
    }

    public String providerName() {
        return "FinMind";
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("stock.finmind.enabled", "true")) && token.length() > 0;
    }

    public FinancialDataBundle fetch(TaiwanStockVO stock) throws Exception {
        if (!isEnabled()) {
            return FinancialDataBundle.empty(providerName());
        }

        String code = stock.getCode();
        String startDate = LocalDate.now().minusYears(3).withDayOfMonth(1).format(API_DATE_FORMATTER);
        List<MonthlyRevenueVO> revenues = fetchMonthlyRevenues(code, startDate);
        JSONArray financialRows = fetchData("TaiwanStockFinancialStatements", code, startDate);
        JSONArray balanceRows = fetchData("TaiwanStockBalanceSheet", code, startDate);
        JSONArray cashFlowRows = fetchData("TaiwanStockCashFlowsStatement", code, startDate);

        List<EpsRecordVO> epsRecords = parseEpsRecords(financialRows);
        List<IncomeStatementRecordVO> incomeRecords = parseIncomeRecords(financialRows);
        List<BalanceSheetRecordVO> balanceRecords = parseBalanceRecords(balanceRows);
        List<CashFlowRecordVO> cashFlowRecords = parseCashFlowRecords(cashFlowRows);

        return new FinancialDataBundle(providerName(), revenues, epsRecords, incomeRecords, balanceRecords,
                cashFlowRecords);
    }

    private List<MonthlyRevenueVO> fetchMonthlyRevenues(String code, String startDate) throws Exception {
        JSONArray rows = fetchData("TaiwanStockMonthRevenue", code, startDate);
        List<RevenuePoint> points = new ArrayList<RevenuePoint>();
        Map<String, Long> revenueByPeriod = new LinkedHashMap<String, Long>();
        for (Object object : rows) {
            JSONObject row = (JSONObject) object;
            int year = intValue(row.get("revenue_year"));
            int month = intValue(row.get("revenue_month"));
            long revenue = longValue(row.get("revenue"));
            if (year <= 0 || month <= 0 || revenue == 0L) {
                continue;
            }
            String period = formatMonthPeriod(year, month);
            points.add(new RevenuePoint(year, month, period, revenue));
            revenueByPeriod.put(period, Long.valueOf(revenue));
        }

        Collections.sort(points, new Comparator<RevenuePoint>() {
            public int compare(RevenuePoint left, RevenuePoint right) {
                int yearCompare = Integer.compare(left.year, right.year);
                return yearCompare != 0 ? yearCompare : Integer.compare(left.month, right.month);
            }
        });

        List<MonthlyRevenueVO> records = new ArrayList<MonthlyRevenueVO>();
        for (int i = 0; i < points.size(); i++) {
            RevenuePoint point = points.get(i);
            long previousMonthRevenue = i > 0 ? points.get(i - 1).revenue : 0L;
            long lastYearRevenue = valueForPeriod(revenueByPeriod, point.year - 1, point.month);
            long accumulatedRevenue = sumYearToMonth(revenueByPeriod, point.year, point.month);
            long lastYearAccumulatedRevenue = sumYearToMonth(revenueByPeriod, point.year - 1, point.month);
            double monthOverMonthPct = pctChange(point.revenue, previousMonthRevenue);
            double yearOverYearPct = pctChange(point.revenue, lastYearRevenue);
            double accumulatedYearOverYearPct = pctChange(accumulatedRevenue, lastYearAccumulatedRevenue);
            records.add(new MonthlyRevenueVO(point.period, point.revenue, monthOverMonthPct, lastYearRevenue,
                    yearOverYearPct, accumulatedRevenue, lastYearAccumulatedRevenue, accumulatedYearOverYearPct));
        }
        Collections.reverse(records);
        return records;
    }

    private List<EpsRecordVO> parseEpsRecords(JSONArray rows) {
        Map<String, QuarterMetrics> byPeriod = groupQuarterRows(rows);
        List<QuarterMetrics> metrics = sortedQuarterMetrics(byPeriod);
        List<EpsRecordVO> records = new ArrayList<EpsRecordVO>();
        for (QuarterMetrics metric : metrics) {
            if (!metric.hasEps) {
                continue;
            }
            QuarterMetrics previous = previousQuarter(byPeriod, metric.year, metric.quarter);
            QuarterMetrics lastYear = byPeriod.get(formatQuarterPeriod(metric.year - 1, metric.quarter));
            double quarterOverQuarterPct = previous == null || !previous.hasEps ? 0D
                    : pctChange(metric.eps, previous.eps);
            double yearOverYearPct = lastYear == null || !lastYear.hasEps ? 0D : pctChange(metric.eps, lastYear.eps);
            records.add(new EpsRecordVO(metric.period, metric.eps, quarterOverQuarterPct, yearOverYearPct, 0D));
        }
        return records;
    }

    private List<IncomeStatementRecordVO> parseIncomeRecords(JSONArray rows) {
        Map<String, QuarterMetrics> byPeriod = groupQuarterRows(rows);
        List<QuarterMetrics> metrics = sortedQuarterMetrics(byPeriod);
        List<IncomeStatementRecordVO> records = new ArrayList<IncomeStatementRecordVO>();
        for (QuarterMetrics metric : metrics) {
            if (metric.hasRevenue || metric.hasGrossProfit || metric.hasOperatingIncome || metric.hasNetIncome) {
                records.add(new IncomeStatementRecordVO(metric.period, metric.revenue, metric.grossProfit,
                        metric.operatingIncome, metric.netIncome));
            }
        }
        return records;
    }

    private List<BalanceSheetRecordVO> parseBalanceRecords(JSONArray rows) {
        Map<String, QuarterMetrics> byPeriod = groupQuarterRows(rows);
        List<QuarterMetrics> metrics = sortedQuarterMetrics(byPeriod);
        List<BalanceSheetRecordVO> records = new ArrayList<BalanceSheetRecordVO>();
        for (QuarterMetrics metric : metrics) {
            if (metric.hasTotalAssets || metric.hasTotalLiabilities || metric.hasEquity
                    || metric.hasCurrentAssets || metric.hasCurrentLiabilities) {
                records.add(new BalanceSheetRecordVO(metric.period, metric.totalAssets, metric.totalLiabilities,
                        metric.equity, metric.currentAssets, metric.currentLiabilities));
            }
        }
        return records;
    }

    private List<CashFlowRecordVO> parseCashFlowRecords(JSONArray rows) {
        Map<String, QuarterMetrics> byPeriod = groupQuarterRows(rows);
        List<QuarterMetrics> metrics = sortedQuarterMetrics(byPeriod);
        List<CashFlowRecordVO> records = new ArrayList<CashFlowRecordVO>();
        for (QuarterMetrics metric : metrics) {
            if (!metric.hasOperatingCashFlow && !metric.hasFreeCashFlow && !metric.hasInvestingCashFlow) {
                continue;
            }
            long freeCashFlow = metric.hasFreeCashFlow ? metric.freeCashFlow
                    : metric.hasInvestingCashFlow ? metric.operatingCashFlow + metric.investingCashFlow : 0L;
            records.add(new CashFlowRecordVO(metric.period, metric.operatingCashFlow, freeCashFlow));
        }
        return records;
    }

    private Map<String, QuarterMetrics> groupQuarterRows(JSONArray rows) {
        Map<String, QuarterMetrics> byPeriod = new LinkedHashMap<String, QuarterMetrics>();
        for (Object object : rows) {
            JSONObject row = (JSONObject) object;
            LocalDate date = parseDate(safeText(row.get("date")));
            if (date == null) {
                continue;
            }
            String period = formatQuarterPeriod(date);
            QuarterMetrics metric = byPeriod.get(period);
            if (metric == null) {
                metric = new QuarterMetrics(date.getYear(), quarterOf(date), period);
                byPeriod.put(period, metric);
            }
            applyMetric(metric, safeText(row.get("type")), safeText(row.get("origin_name")), numberValue(row.get("value")));
        }
        return byPeriod;
    }

    private void applyMetric(QuarterMetrics metric, String type, String originName, double value) {
        String key = (type + " " + originName).toLowerCase();
        String zh = originName;
        if (containsAny(key, "eps") || containsAny(zh, "基本每股盈餘", "每股盈餘")) {
            metric.eps = value;
            metric.hasEps = true;
        } else if (containsAny(zh, "營業收入合計", "營業收入")) {
            metric.revenue = Math.round(value);
            metric.hasRevenue = true;
        } else if (containsAny(zh, "營業毛利", "毛利")) {
            metric.grossProfit = Math.round(value);
            metric.hasGrossProfit = true;
        } else if (containsAny(zh, "營業利益", "營業損益")) {
            metric.operatingIncome = Math.round(value);
            metric.hasOperatingIncome = true;
        } else if (containsAny(zh, "本期淨利", "本期稅後淨利", "稅後淨利")) {
            metric.netIncome = Math.round(value);
            metric.hasNetIncome = true;
        } else if (containsAny(zh, "資產總計", "資產總額", "資產合計")) {
            metric.totalAssets = Math.round(value);
            metric.hasTotalAssets = true;
        } else if (containsAny(zh, "負債總計", "負債總額", "負債合計")) {
            metric.totalLiabilities = Math.round(value);
            metric.hasTotalLiabilities = true;
        } else if (containsAny(zh, "權益總計", "權益總額", "權益合計", "股東權益")) {
            metric.equity = Math.round(value);
            metric.hasEquity = true;
        } else if (!zh.contains("非流動") && containsAny(zh, "流動資產合計", "流動資產總計", "流動資產")) {
            metric.currentAssets = Math.round(value);
            metric.hasCurrentAssets = true;
        } else if (!zh.contains("非流動") && containsAny(zh, "流動負債合計", "流動負債總計", "流動負債")) {
            metric.currentLiabilities = Math.round(value);
            metric.hasCurrentLiabilities = true;
        } else if (containsAny(zh, "營業活動之淨現金流入", "營業活動之淨現金流出", "營業活動現金流量",
                "營業活動之現金流量")) {
            metric.operatingCashFlow = Math.round(value);
            metric.hasOperatingCashFlow = true;
        } else if (containsAny(zh, "投資活動之淨現金流入", "投資活動之淨現金流出", "投資活動之現金流量")) {
            metric.investingCashFlow = Math.round(value);
            metric.hasInvestingCashFlow = true;
        } else if (containsAny(zh, "自由現金流")) {
            metric.freeCashFlow = Math.round(value);
            metric.hasFreeCashFlow = true;
        }
    }

    private JSONArray fetchData(String dataset, String code, String startDate) throws Exception {
        String url = System.getProperty("stock.finmind.apiUrl", API_URL)
                + "?dataset=" + encode(dataset)
                + "&data_id=" + encode(code)
                + "&start_date=" + encode(startDate);
        String body = executeJson(url);
        JSONObject root = (JSONObject) parser.parse(body);
        Object data = root.get("data");
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        return new JSONArray();
    }

    private String executeJson(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readAll(stream);
            if (status >= 200 && status < 300) {
                return body;
            }
            throw new Exception("FinMind HTTP " + status + ": " + shortBody(body));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
        try {
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private List<QuarterMetrics> sortedQuarterMetrics(Map<String, QuarterMetrics> byPeriod) {
        List<QuarterMetrics> metrics = new ArrayList<QuarterMetrics>(byPeriod.values());
        Collections.sort(metrics, new Comparator<QuarterMetrics>() {
            public int compare(QuarterMetrics left, QuarterMetrics right) {
                int yearCompare = Integer.compare(right.year, left.year);
                return yearCompare != 0 ? yearCompare : Integer.compare(right.quarter, left.quarter);
            }
        });
        return metrics;
    }

    private QuarterMetrics previousQuarter(Map<String, QuarterMetrics> byPeriod, int year, int quarter) {
        int previousYear = quarter == 1 ? year - 1 : year;
        int previousQuarter = quarter == 1 ? 4 : quarter - 1;
        return byPeriod.get(formatQuarterPeriod(previousYear, previousQuarter));
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, API_DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatQuarterPeriod(LocalDate date) {
        return formatQuarterPeriod(date.getYear(), quarterOf(date));
    }

    private String formatQuarterPeriod(int year, int quarter) {
        return String.format("%04d Q%d", Integer.valueOf(year), Integer.valueOf(quarter));
    }

    private int quarterOf(LocalDate date) {
        return ((date.getMonthValue() - 1) / 3) + 1;
    }

    private String formatMonthPeriod(int year, int month) {
        return String.format("%04d/%02d", Integer.valueOf(year), Integer.valueOf(month));
    }

    private long valueForPeriod(Map<String, Long> revenueByPeriod, int year, int month) {
        Long value = revenueByPeriod.get(formatMonthPeriod(year, month));
        return value == null ? 0L : value.longValue();
    }

    private long sumYearToMonth(Map<String, Long> revenueByPeriod, int year, int month) {
        long total = 0L;
        for (int i = 1; i <= month; i++) {
            total += valueForPeriod(revenueByPeriod, year, i);
        }
        return total;
    }

    private double pctChange(double value, double base) {
        if (base == 0D) {
            return 0D;
        }
        return (value - base) * 100D / Math.abs(base);
    }

    private double numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(safeText(value).replace(",", ""));
        } catch (Exception ex) {
            return 0D;
        }
    }

    private long longValue(Object value) {
        return Math.round(numberValue(value));
    }

    private int intValue(Object value) {
        return (int) Math.round(numberValue(value));
    }

    private String safeText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean containsAny(String text, String... patterns) {
        String source = text == null ? "" : text;
        for (String pattern : patterns) {
            if (source.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String encode(String text) throws Exception {
        return URLEncoder.encode(text, "UTF-8");
    }

    private String shortBody(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }

    private String resolveToken() {
        String property = System.getProperty("stock.finmind.token");
        if (property != null && property.trim().length() > 0) {
            return property.trim();
        }
        String env = System.getenv("STOCK_FINMIND_TOKEN");
        return env == null ? "" : env.trim();
    }

    private static class RevenuePoint {
        private final int year;
        private final int month;
        private final String period;
        private final long revenue;

        private RevenuePoint(int year, int month, String period, long revenue) {
            this.year = year;
            this.month = month;
            this.period = period;
            this.revenue = revenue;
        }
    }

    private static class QuarterMetrics {
        private final int year;
        private final int quarter;
        private final String period;
        private double eps;
        private boolean hasEps;
        private long revenue;
        private boolean hasRevenue;
        private long grossProfit;
        private boolean hasGrossProfit;
        private long operatingIncome;
        private boolean hasOperatingIncome;
        private long netIncome;
        private boolean hasNetIncome;
        private long totalAssets;
        private boolean hasTotalAssets;
        private long totalLiabilities;
        private boolean hasTotalLiabilities;
        private long equity;
        private boolean hasEquity;
        private long currentAssets;
        private boolean hasCurrentAssets;
        private long currentLiabilities;
        private boolean hasCurrentLiabilities;
        private long operatingCashFlow;
        private boolean hasOperatingCashFlow;
        private long investingCashFlow;
        private boolean hasInvestingCashFlow;
        private long freeCashFlow;
        private boolean hasFreeCashFlow;

        private QuarterMetrics(int year, int quarter, String period) {
            this.year = year;
            this.quarter = quarter;
            this.period = period;
        }
    }
}
