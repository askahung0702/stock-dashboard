package stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.NumberParser;

public class LowFrequencyDataCache {

    public static class Entry {
        public String code = "";
        public String sourceUpdatedAt = "";   // actual data period, e.g. "2026/03" or "2026 Q1"
        public String cacheRefreshedAt = "";  // last Yahoo fetch date, e.g. "20260421"
        public String latestRevenuePeriod = "";
        public String latestFinancialPeriod = "";
        public String latestRevenueSource = "";
        public String latestFinancialSource = "";
        public String industry = "";
        public String marketType = "";
        public String shareholderMeetingDate = "";
        public String cashDividendPayoutDate = "";
        public String exDividendDate = "";
        public long capital;
        public long sharesOutstanding;
        public long latestVolumeLots;
        public double marketCapMillions;
        public double displayedPe;
        public double peerAveragePe;
        public double latestTurnoverBillion;
        public double grossMarginPct;
        public double operatingMarginPct;
        public double returnOnAssetsPct;
        public double returnOnEquityPct;
        public double bookValue;
        public double latestRevenueYoY;
        public double averageThreeMonthRevenueYoY;
        public double accumulatedRevenueYoY;
        public int positiveRevenueMonths;
        public double trailingFourQuarterEps;
        public double latestQuarterEps;
        public double previousQuarterEps;
        public double latestQuarterEpsYoYPct;
        public int positiveEpsQuarters;
        public long latestOperatingCashFlow;
        public long previousOperatingCashFlow;
        public long latestFreeCashFlow;
        public long previousFreeCashFlow;
        public int positiveOperatingCashFlowQuarters;
        public int positiveFreeCashFlowQuarters;
        public long latestOperatingIncome;
        public long previousOperatingIncome;
        public long latestNetIncome;
        public long previousNetIncome;
        public double debtRatioPct;
        public double currentRatio;
        public double nonOperatingRatioPct;
    }

    private final File cacheFile;
    private final Map<String, Entry> entries = new HashMap<String, Entry>();

    public static LowFrequencyDataCache loadDefault() {
        return load(new File("history", "low_frequency_cache.json"));
    }

    public static LowFrequencyDataCache load(File file) {
        LowFrequencyDataCache cache = new LowFrequencyDataCache(file);
        cache.loadFromDisk();
        return cache;
    }

    private LowFrequencyDataCache(File cacheFile) {
        this.cacheFile = cacheFile;
    }

    public Entry get(String code) {
        return entries.get(code == null ? "" : code.trim());
    }

    public void upsert(Entry entry) {
        if (entry == null || entry.code == null || entry.code.trim().length() == 0) {
            return;
        }
        entries.put(entry.code.trim(), entry);
    }

    public void save() {
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JSONObject root = new JSONObject();
            JSONObject entriesObject = new JSONObject();
            for (Map.Entry<String, Entry> mapEntry : entries.entrySet()) {
                entriesObject.put(mapEntry.getKey(), toJson(mapEntry.getValue()));
            }
            root.put("entries", entriesObject);

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(cacheFile), "UTF-8"));
            try {
                writer.write(root.toJSONString());
            } finally {
                writer.close();
            }
        } catch (Exception ex) {
            System.out.println("Cannot write low frequency cache: " + ex.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), "UTF-8"));
            Object parsed = new JSONParser().parse(reader);
            if (!(parsed instanceof JSONObject)) {
                return;
            }
            JSONObject root = (JSONObject) parsed;
            Object entriesValue = root.get("entries");
            if (!(entriesValue instanceof JSONObject)) {
                return;
            }
            JSONObject entriesObject = (JSONObject) entriesValue;
            for (Object keyObject : entriesObject.keySet()) {
                String code = keyObject == null ? "" : keyObject.toString();
                Object rowObject = entriesObject.get(keyObject);
                if (!(rowObject instanceof JSONObject)) {
                    continue;
                }
                Entry entry = fromJson(code, (JSONObject) rowObject);
                if (entry.code.length() > 0) {
                    entries.put(entry.code, entry);
                }
            }
        } catch (Exception ex) {
            return;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
                // ignore close failures
            }
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject toJson(Entry entry) {
        JSONObject obj = new JSONObject();
        obj.put("code", safeText(entry.code));
        obj.put("sourceUpdatedAt", safeText(entry.sourceUpdatedAt));
        obj.put("cacheRefreshedAt", safeText(entry.cacheRefreshedAt));
        obj.put("latestRevenuePeriod", safeText(entry.latestRevenuePeriod));
        obj.put("latestFinancialPeriod", safeText(entry.latestFinancialPeriod));
        obj.put("latestRevenueSource", safeText(entry.latestRevenueSource));
        obj.put("latestFinancialSource", safeText(entry.latestFinancialSource));
        obj.put("industry", safeText(entry.industry));
        obj.put("marketType", safeText(entry.marketType));
        obj.put("shareholderMeetingDate", safeText(entry.shareholderMeetingDate));
        obj.put("cashDividendPayoutDate", safeText(entry.cashDividendPayoutDate));
        obj.put("exDividendDate", safeText(entry.exDividendDate));
        obj.put("capital", Long.valueOf(entry.capital));
        obj.put("sharesOutstanding", Long.valueOf(entry.sharesOutstanding));
        obj.put("latestVolumeLots", Long.valueOf(entry.latestVolumeLots));
        obj.put("marketCapMillions", Double.valueOf(entry.marketCapMillions));
        obj.put("displayedPe", Double.valueOf(entry.displayedPe));
        obj.put("peerAveragePe", Double.valueOf(entry.peerAveragePe));
        obj.put("latestTurnoverBillion", Double.valueOf(entry.latestTurnoverBillion));
        obj.put("grossMarginPct", Double.valueOf(entry.grossMarginPct));
        obj.put("operatingMarginPct", Double.valueOf(entry.operatingMarginPct));
        obj.put("returnOnAssetsPct", Double.valueOf(entry.returnOnAssetsPct));
        obj.put("returnOnEquityPct", Double.valueOf(entry.returnOnEquityPct));
        obj.put("bookValue", Double.valueOf(entry.bookValue));
        obj.put("latestRevenueYoY", Double.valueOf(entry.latestRevenueYoY));
        obj.put("averageThreeMonthRevenueYoY", Double.valueOf(entry.averageThreeMonthRevenueYoY));
        obj.put("accumulatedRevenueYoY", Double.valueOf(entry.accumulatedRevenueYoY));
        obj.put("positiveRevenueMonths", Long.valueOf(entry.positiveRevenueMonths));
        obj.put("trailingFourQuarterEps", Double.valueOf(entry.trailingFourQuarterEps));
        obj.put("latestQuarterEps", Double.valueOf(entry.latestQuarterEps));
        obj.put("previousQuarterEps", Double.valueOf(entry.previousQuarterEps));
        obj.put("latestQuarterEpsYoYPct", Double.valueOf(entry.latestQuarterEpsYoYPct));
        obj.put("positiveEpsQuarters", Long.valueOf(entry.positiveEpsQuarters));
        obj.put("latestOperatingCashFlow", Long.valueOf(entry.latestOperatingCashFlow));
        obj.put("previousOperatingCashFlow", Long.valueOf(entry.previousOperatingCashFlow));
        obj.put("latestFreeCashFlow", Long.valueOf(entry.latestFreeCashFlow));
        obj.put("previousFreeCashFlow", Long.valueOf(entry.previousFreeCashFlow));
        obj.put("positiveOperatingCashFlowQuarters", Long.valueOf(entry.positiveOperatingCashFlowQuarters));
        obj.put("positiveFreeCashFlowQuarters", Long.valueOf(entry.positiveFreeCashFlowQuarters));
        obj.put("latestOperatingIncome", Long.valueOf(entry.latestOperatingIncome));
        obj.put("previousOperatingIncome", Long.valueOf(entry.previousOperatingIncome));
        obj.put("latestNetIncome", Long.valueOf(entry.latestNetIncome));
        obj.put("previousNetIncome", Long.valueOf(entry.previousNetIncome));
        obj.put("debtRatioPct", Double.valueOf(entry.debtRatioPct));
        obj.put("currentRatio", Double.valueOf(entry.currentRatio));
        obj.put("nonOperatingRatioPct", Double.valueOf(entry.nonOperatingRatioPct));
        return obj;
    }

    private Entry fromJson(String code, JSONObject obj) {
        Entry entry = new Entry();
        entry.code = safeText(code);
        entry.sourceUpdatedAt = safeText(obj.get("sourceUpdatedAt"));
        entry.cacheRefreshedAt = safeText(obj.get("cacheRefreshedAt"));
        entry.latestRevenuePeriod = safeText(obj.get("latestRevenuePeriod"));
        entry.latestFinancialPeriod = safeText(obj.get("latestFinancialPeriod"));
        entry.latestRevenueSource = safeText(obj.get("latestRevenueSource"));
        entry.latestFinancialSource = safeText(obj.get("latestFinancialSource"));
        entry.industry = safeText(obj.get("industry"));
        entry.marketType = safeText(obj.get("marketType"));
        entry.shareholderMeetingDate = safeText(obj.get("shareholderMeetingDate"));
        entry.cashDividendPayoutDate = safeText(obj.get("cashDividendPayoutDate"));
        entry.exDividendDate = safeText(obj.get("exDividendDate"));
        entry.capital = longValue(obj.get("capital"));
        entry.sharesOutstanding = longValue(obj.get("sharesOutstanding"));
        entry.latestVolumeLots = longValue(obj.get("latestVolumeLots"));
        entry.marketCapMillions = numberValue(obj.get("marketCapMillions"));
        entry.displayedPe = numberValue(obj.get("displayedPe"));
        entry.peerAveragePe = numberValue(obj.get("peerAveragePe"));
        entry.latestTurnoverBillion = numberValue(obj.get("latestTurnoverBillion"));
        entry.grossMarginPct = numberValue(obj.get("grossMarginPct"));
        entry.operatingMarginPct = numberValue(obj.get("operatingMarginPct"));
        entry.returnOnAssetsPct = numberValue(obj.get("returnOnAssetsPct"));
        entry.returnOnEquityPct = numberValue(obj.get("returnOnEquityPct"));
        entry.bookValue = numberValue(obj.get("bookValue"));
        entry.latestRevenueYoY = numberValue(obj.get("latestRevenueYoY"));
        entry.averageThreeMonthRevenueYoY = numberValue(obj.get("averageThreeMonthRevenueYoY"));
        entry.accumulatedRevenueYoY = numberValue(obj.get("accumulatedRevenueYoY"));
        entry.positiveRevenueMonths = intValue(obj.get("positiveRevenueMonths"));
        entry.trailingFourQuarterEps = numberValue(obj.get("trailingFourQuarterEps"));
        entry.latestQuarterEps = numberValue(obj.get("latestQuarterEps"));
        entry.previousQuarterEps = numberValue(obj.get("previousQuarterEps"));
        entry.latestQuarterEpsYoYPct = numberValue(obj.get("latestQuarterEpsYoYPct"));
        entry.positiveEpsQuarters = intValue(obj.get("positiveEpsQuarters"));
        entry.latestOperatingCashFlow = longValue(obj.get("latestOperatingCashFlow"));
        entry.previousOperatingCashFlow = longValue(obj.get("previousOperatingCashFlow"));
        entry.latestFreeCashFlow = longValue(obj.get("latestFreeCashFlow"));
        entry.previousFreeCashFlow = longValue(obj.get("previousFreeCashFlow"));
        entry.positiveOperatingCashFlowQuarters = intValue(obj.get("positiveOperatingCashFlowQuarters"));
        entry.positiveFreeCashFlowQuarters = intValue(obj.get("positiveFreeCashFlowQuarters"));
        entry.latestOperatingIncome = longValue(obj.get("latestOperatingIncome"));
        entry.previousOperatingIncome = longValue(obj.get("previousOperatingIncome"));
        entry.latestNetIncome = longValue(obj.get("latestNetIncome"));
        entry.previousNetIncome = longValue(obj.get("previousNetIncome"));
        entry.debtRatioPct = numberValue(obj.get("debtRatioPct"));
        entry.currentRatio = numberValue(obj.get("currentRatio"));
        entry.nonOperatingRatioPct = numberValue(obj.get("nonOperatingRatioPct"));
        return entry;
    }

    private String safeText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private double numberValue(Object value) {
        return value == null ? 0D : NumberParser.parseDouble(String.valueOf(value));
    }

    private long longValue(Object value) {
        return value == null ? 0L : NumberParser.parseLong(String.valueOf(value));
    }

    private int intValue(Object value) {
        return (int) longValue(value);
    }
}
