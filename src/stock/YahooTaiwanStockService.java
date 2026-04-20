package stock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.BalanceSheetRecordVO;
import stock.vo.BrokerTradingSummaryVO;
import stock.vo.CashFlowRecordVO;
import stock.vo.EpsRecordVO;
import stock.vo.EventRiskVO;
import stock.vo.IncomeStatementRecordVO;
import stock.vo.InstitutionalTradingDailyVO;
import stock.vo.MonthlyRevenueVO;
import stock.vo.NewsSignalVO;
import stock.vo.ProfileSnapshotVO;
import stock.vo.TaiwanStockVO;
import stock.vo.TechnicalSnapshotVO;

public class YahooTaiwanStockService {

    private static final Pattern REVENUE_ROW_PATTERN = Pattern.compile(
            "(\\d{4}/\\d{2})\\s+([\\d,\\-]+)\\s+([+\\-]?[\\d.]+%|--)\\s+([\\d,\\-]+)\\s+([+\\-]?[\\d.]+%|--)\\s+([\\d,\\-]+)\\s+([\\d,\\-]+)\\s+([+\\-]?[\\d.]+%|--)");
    private static final Pattern INSTITUTIONAL_DAILY_PATTERN = Pattern.compile(
            "(\\d{4}/\\d{2}/\\d{2})\\s+([\\-\\d,]+)\\s+([\\-\\d,]+)\\s+([\\-\\d,]+)\\s+([\\-\\d,]+)\\s+([+\\-]?[\\d.]+%)\\s+([+\\-]?[\\d.]+%)\\s+([\\d,]+)");
    private static final Pattern EPS_ROW_PATTERN = Pattern.compile(
            "(\\d{4}\\sQ[1-4])\\s+([\\-]?[\\d.]+)\\s+([+\\-]?[\\d.,]+%|-)\\s+([+\\-]?[\\d.,]+%|-)\\s+([\\-]?[\\d.]+)");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(20\\d{2}\\sQ[1-4])");
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("-?[\\d,]+");
    private static final Pattern BROKER_DATE_PATTERN = Pattern.compile("主力進出\\s+資料時間：(\\d{4}/\\d{2}/\\d{2})");
    private static final Pattern BROKER_NET_PATTERN = Pattern.compile("主力買賣超\\(張\\)\\s+([\\-\\d,]+)");
    private static final Pattern BROKER_BUY_PATTERN = Pattern.compile("主力買超\\(張\\)\\s+([\\-\\d,]+)");
    private static final Pattern BROKER_SELL_PATTERN = Pattern.compile("主力賣超\\(張\\)\\s+([\\-\\d,]+)");
    private static final Pattern BROKER_RATIO_PATTERN = Pattern.compile("買賣超佔成交量\\s+([+\\-]?[\\d.]+%)");
    private static final Pattern PROFILE_PE_PATTERN = Pattern.compile("([\\d.\\-]+)\\s*\\(([\\d.\\-]+)\\)本益比 \\(同業平均\\)");
    private static final Pattern PROFILE_QUOTE_PATTERN = Pattern.compile(
            "加入自選股\\s+([^\\s]+)\\(([+\\-]?[\\d.]+%)\\)(?:收盤|開盤|盤中|市價)");
    private static final Pattern PROFILE_VOLUME_PATTERN = Pattern.compile("([\\d,]+)成交量");
    private static final Pattern PROFILE_TURNOVER_PATTERN = Pattern.compile("成交金額\\(億\\)([\\d.]+)");
    private static final Pattern PROFILE_INDUSTRY_PATTERN = Pattern.compile("產業類別\\s+([^\\s]+)");
    private static final Pattern PROFILE_CAPITAL_PATTERN = Pattern.compile("股本\\s+([\\d,]+)");
    private static final Pattern PROFILE_SHARES_PATTERN = Pattern.compile("已發行普通股數\\s+([\\d,]+)");
    private static final Pattern PROFILE_MARKET_CAP_PATTERN = Pattern.compile("市值 \\(百萬\\)\\s+([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern PROFILE_MARKET_TYPE_PATTERN = Pattern.compile("市場別\\s+([^\\s]+)");
    private static final Pattern PROFILE_GROSS_MARGIN_PATTERN = Pattern.compile("營業毛利率\\s+([+\\-]?[\\d.]+%)");
    private static final Pattern PROFILE_OPERATING_MARGIN_PATTERN = Pattern.compile("營業利益率\\s+([+\\-]?[\\d.]+%)");
    private static final Pattern PROFILE_ROA_PATTERN = Pattern.compile("資產報酬率\\s+([+\\-]?[\\d.]+%)");
    private static final Pattern PROFILE_ROE_PATTERN = Pattern.compile("股東權益報酬率\\s+([+\\-]?[\\d.]+%)");
    private static final Pattern PROFILE_BOOK_VALUE_PATTERN = Pattern.compile("每股淨值\\s+([+\\-]?[\\d.]+)");
    private static final Pattern PROFILE_SHAREHOLDER_MEETING_PATTERN = Pattern.compile("股東常會\\s+(\\d{4}/\\d{2}/\\d{2}|-)");
    private static final Pattern PROFILE_DIVIDEND_PAYOUT_PATTERN = Pattern.compile("現金股利發放日\\s+(\\d{4}/\\d{2}/\\d{2}|-)");
    private static final Pattern PROFILE_EX_DIVIDEND_PATTERN = Pattern.compile("除息日期\\s+(\\d{4}/\\d{2}/\\d{2}|-)");
    private static final Pattern NEWS_BLOCK_PATTERN = Pattern.compile(
            "(\\d{4}/\\d{2}/\\d{2}|\\d+\\s*(?:小時|分鐘)前)\\s+(.{8,120}?)(?=(?:\\d{4}/\\d{2}/\\d{2}|\\d+\\s*(?:小時|分鐘)前|我的自選股|看更多|更多新聞|相關新聞|$))");
    private static final Pattern ANNOUNCEMENT_BLOCK_PATTERN = Pattern.compile(
            "(\\d{4}/\\d{2}/\\d{2})\\s+(.{10,140}?)(?=(?:\\d{4}/\\d{2}/\\d{2}|我的自選股|看更多|更多公告|相關公告|$))");
    private static final String[] EVENT_RISK_KEYWORDS = { "減資", "訴訟", "停止交易", "違約", "減損", "下市", "私募", "現增",
            "主管機關", "處置", "澄清", "重大災害", "重編", "虧損" };
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    public List<MonthlyRevenueVO> fetchMonthlyRevenues(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/revenue");
        String section = slice(text, "年度/月份", "我的自選股");
        Matcher matcher = REVENUE_ROW_PATTERN.matcher(section);

        List<MonthlyRevenueVO> revenues = new ArrayList<MonthlyRevenueVO>();
        while (matcher.find()) {
            revenues.add(new MonthlyRevenueVO(matcher.group(1), NumberParser.parseLong(matcher.group(2)),
                    NumberParser.parseDouble(matcher.group(3)), NumberParser.parseLong(matcher.group(4)),
                    NumberParser.parseDouble(matcher.group(5)), NumberParser.parseLong(matcher.group(6)),
                    NumberParser.parseLong(matcher.group(7)), NumberParser.parseDouble(matcher.group(8))));
        }
        return revenues;
    }

    public List<InstitutionalTradingDailyVO> fetchInstitutionalTrading(TaiwanStockVO stock) throws Exception {
        String text = fetcher
                .fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/institutional-trading");
        String section = slice(text, "法人逐日買賣超", "我的自選股");
        Matcher matcher = INSTITUTIONAL_DAILY_PATTERN.matcher(section);

        List<InstitutionalTradingDailyVO> records = new ArrayList<InstitutionalTradingDailyVO>();
        while (matcher.find()) {
            records.add(new InstitutionalTradingDailyVO(matcher.group(1), NumberParser.parseLong(matcher.group(2)),
                    NumberParser.parseLong(matcher.group(3)), NumberParser.parseLong(matcher.group(4)),
                    NumberParser.parseLong(matcher.group(5)), NumberParser.parseDouble(matcher.group(6)),
                    NumberParser.parseDouble(matcher.group(7)), NumberParser.parseLong(matcher.group(8))));
        }
        return records;
    }

    public BrokerTradingSummaryVO fetchBrokerTradingSummary(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/broker-trading");
        return new BrokerTradingSummaryVO(findFirst(text, BROKER_DATE_PATTERN),
                NumberParser.parseLong(findFirst(text, BROKER_NET_PATTERN)),
                NumberParser.parseLong(findFirst(text, BROKER_BUY_PATTERN)),
                NumberParser.parseLong(findFirst(text, BROKER_SELL_PATTERN)),
                NumberParser.parseDouble(findFirst(text, BROKER_RATIO_PATTERN)));
    }

    public List<EpsRecordVO> fetchEpsRecords(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/eps");
        String section = slice(text, "年度/季別", "單季每股盈餘 單季每股盈餘 =");
        Matcher matcher = EPS_ROW_PATTERN.matcher(section);

        List<EpsRecordVO> records = new ArrayList<EpsRecordVO>();
        while (matcher.find()) {
            records.add(new EpsRecordVO(matcher.group(1), NumberParser.parseDouble(matcher.group(2)),
                    NumberParser.parseDouble(matcher.group(3)), NumberParser.parseDouble(matcher.group(4)),
                    NumberParser.parseDouble(matcher.group(5))));
        }
        return records;
    }

    public List<CashFlowRecordVO> fetchCashFlowRecords(TaiwanStockVO stock) throws Exception {
        String text = fetcher
                .fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/cash-flow-statement");
        String section = slice(text, "年度/月份", "自由現金流 【營業現金流】+【投資現金流】");

        List<String> periods = findAll(section, PERIOD_PATTERN);
        String operatingSegment = between(section, "營業現金流", "投資現金流");
        String freeSegment = between(section, "自由現金流", "淨現金流");
        List<Long> operatingValues = extractLongValues(operatingSegment);
        List<Long> freeValues = extractLongValues(freeSegment);

        int size = Math.min(periods.size(), Math.min(operatingValues.size(), freeValues.size()));
        List<CashFlowRecordVO> records = new ArrayList<CashFlowRecordVO>();
        for (int i = 0; i < size; i++) {
            records.add(new CashFlowRecordVO(periods.get(i), operatingValues.get(i).longValue(),
                    freeValues.get(i).longValue()));
        }
        return records;
    }

    public ProfileSnapshotVO fetchProfileSnapshot(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/profile");

        double yahooPageCurrentPrice = parseCurrentPriceFromQuoteText(text);
        Matcher peMatcher = PROFILE_PE_PATTERN.matcher(text);
        double displayedPe = 0D;
        double peerAveragePe = 0D;
        if (peMatcher.find()) {
            displayedPe = NumberParser.parseDouble(peMatcher.group(1));
            peerAveragePe = NumberParser.parseDouble(peMatcher.group(2));
        }

        return new ProfileSnapshotVO(yahooPageCurrentPrice, findOptional(text, PROFILE_INDUSTRY_PATTERN),
                findOptional(text, PROFILE_MARKET_TYPE_PATTERN),
                NumberParser.parseLong(findOptional(text, PROFILE_CAPITAL_PATTERN)),
                NumberParser.parseLong(findOptional(text, PROFILE_SHARES_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_MARKET_CAP_PATTERN)), displayedPe, peerAveragePe,
                NumberParser.parseLong(findOptional(text, PROFILE_VOLUME_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_TURNOVER_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_GROSS_MARGIN_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_OPERATING_MARGIN_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_ROA_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_ROE_PATTERN)),
                NumberParser.parseDouble(findOptional(text, PROFILE_BOOK_VALUE_PATTERN)),
                findOptional(text, PROFILE_SHAREHOLDER_MEETING_PATTERN),
                findOptional(text, PROFILE_DIVIDEND_PAYOUT_PATTERN), findOptional(text, PROFILE_EX_DIVIDEND_PATTERN));
    }

    public List<IncomeStatementRecordVO> fetchIncomeStatementRecords(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/income-statement");
        String section = slice(text, "年度/月份", "我的自選股");

        List<String> periods = findAll(section, PERIOD_PATTERN);
        List<Long> revenues = extractLongValues(between(section, "營業收入", "營業毛利"));
        List<Long> grossProfits = extractLongValues(between(section, "營業毛利", "營業費用"));
        List<Long> operatingIncomes = extractLongValues(between(section, "營業利益", "稅後淨利"));
        List<Long> netIncomes = extractLongValues(after(section, "稅後淨利"));

        int size = minSize(periods.size(), revenues.size(), grossProfits.size(), operatingIncomes.size(), netIncomes.size());
        List<IncomeStatementRecordVO> records = new ArrayList<IncomeStatementRecordVO>();
        for (int i = 0; i < size; i++) {
            records.add(new IncomeStatementRecordVO(periods.get(i), revenues.get(i).longValue(),
                    grossProfits.get(i).longValue(), operatingIncomes.get(i).longValue(), netIncomes.get(i).longValue()));
        }
        return records;
    }

    public List<BalanceSheetRecordVO> fetchBalanceSheetRecords(TaiwanStockVO stock) throws Exception {
        String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/balance-sheet");
        String section = slice(text, "年度/季度", "我的自選股");

        List<String> periods = findAll(section, PERIOD_PATTERN);
        List<Long> totalAssets = extractLongValues(between(section, "總資產", "總負債"));
        List<Long> totalLiabilities = extractLongValues(between(section, "總負債", "股東權益（淨值）"));
        List<Long> equityValues = extractLongValues(between(section, "股東權益（淨值）", "流動資產"));
        List<Long> currentAssets = extractLongValues(between(section, "流動資產", "流動負債"));
        List<Long> currentLiabilities = extractLongValues(after(section, "流動負債"));

        int size = minSize(periods.size(), totalAssets.size(), totalLiabilities.size(), equityValues.size(),
                currentAssets.size(), currentLiabilities.size());
        List<BalanceSheetRecordVO> records = new ArrayList<BalanceSheetRecordVO>();
        for (int i = 0; i < size; i++) {
            records.add(new BalanceSheetRecordVO(periods.get(i), totalAssets.get(i).longValue(),
                    totalLiabilities.get(i).longValue(), equityValues.get(i).longValue(),
                    currentAssets.get(i).longValue(), currentLiabilities.get(i).longValue()));
        }
        return records;
    }

    public EventRiskVO fetchEventRisk(TaiwanStockVO stock, ProfileSnapshotVO profile) throws Exception {
        double penalty = 0D;
        List<String> reasons = new ArrayList<String>();

        penalty += applyDateRisk(profile.getExDividendDate(), 14, "近期除息", reasons, 1D);
        penalty += applyDateRisk(profile.getShareholderMeetingDate(), 21, "近期股東會", reasons, 1D);
        penalty += applyDateRisk(profile.getCashDividendPayoutDate(), 14, "近期股利發放", reasons, 0.5D);

        try {
            String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/announcement");
            String section = slice(text, "個股公告", "我的自選股");
            section = section.substring(0, Math.min(section.length(), 1500));
            int matchedKeywords = 0;
            for (String keyword : EVENT_RISK_KEYWORDS) {
                if (section.contains(keyword)) {
                    penalty += 1.5D;
                    reasons.add("公告含關鍵字: " + keyword);
                    matchedKeywords++;
                    if (matchedKeywords >= 2) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            reasons.add("公告頁暫時無法讀取，事件風險只用日期資訊估計");
        }

        if (reasons.isEmpty()) {
            reasons.add("近期未看到明顯事件風險關鍵字");
        }
        return new EventRiskVO(NumberParser.clamp(penalty, 0D, 5D), join(reasons, "；"));
    }

    public NewsSignalVO fetchNewsSignal(TaiwanStockVO stock) throws Exception {
        String section = "";
        boolean hasNewsPage = false;
        try {
            String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/news");
            section = slice(text, "相關新聞", "我的自選股");
            if (section == null || section.length() == 0 || section.equals(text)) {
                section = text;
            }
            hasNewsPage = true;
        } catch (Exception ignored) {
            section = "";
        }

        NewsSignalVO signal = new NewsSignalVO();
        List<NewsSignalVO.NewsItem> items = new ArrayList<NewsSignalVO.NewsItem>();
        if (section.length() > 0) {
            items.addAll(parseNewsItems(section, stock));
        }

        List<NewsSignalVO.NewsItem> announcementItems = parseAnnouncementItems(stock);
        if (!announcementItems.isEmpty()) {
            items.addAll(announcementItems);
        }

        if (items.isEmpty()) {
            List<String> snippets = parseNewsFallbackSnippets(section, stock);
            for (String snippet : snippets) {
                items.add(new NewsSignalVO.NewsItem(snippet, "MEDIA", "Yahoo個股新聞", "", "", 65D, 35D));
            }
        }
        signal.setItems(items);
        signal.setHasNewsPage(hasNewsPage || signal.getHeadlineCount() > 0 || signal.getItems().size() > 0);
        return signal;
    }

    public TechnicalSnapshotVO fetchTechnicalSnapshot(TaiwanStockVO stock) throws Exception {
        String jsonText = fetcher.fetchJson(
                "https://query1.finance.yahoo.com/v8/finance/chart/" + stock.getYahooSymbol() + "?range=1y&interval=1d");

        JSONObject root = (JSONObject) parser.parse(jsonText);
        JSONObject chart = (JSONObject) root.get("chart");
        JSONArray results = (JSONArray) chart.get("result");
        if (results == null || results.isEmpty()) {
            throw new Exception("chart api has no result");
        }

        JSONObject result = (JSONObject) results.get(0);
        JSONObject meta = (JSONObject) result.get("meta");
        JSONObject indicators = (JSONObject) result.get("indicators");
        JSONArray quoteArray = (JSONArray) indicators.get("quote");
        JSONObject quote = (JSONObject) quoteArray.get(0);

        List<Double> closes = extractDoubleSeries((JSONArray) quote.get("close"));
        List<Double> highs = extractDoubleSeries((JSONArray) quote.get("high"));
        List<Double> lows = extractDoubleSeries((JSONArray) quote.get("low"));
        List<Long> volumes = extractLongSeries((JSONArray) quote.get("volume"));
        if (closes.size() < 60) {
            throw new Exception("not enough chart data");
        }

        double currentPrice = toDouble(meta.get("regularMarketPrice"));
        if (currentPrice <= 0D) {
            currentPrice = closes.get(closes.size() - 1).doubleValue();
        }

        double movingAverage18 = averageLast(closes, 18);
        double movingAverage20 = averageLast(closes, 20);
        double movingAverage54 = averageLast(closes, 54);
        double movingAverage60 = averageLast(closes, 60);
        double movingAverage120 = averageLast(closes, 120);
        double return18DayPct = percentChange(valueDaysAgo(closes, 18), currentPrice);
        double return20DayPct = percentChange(valueDaysAgo(closes, 20), currentPrice);
        double return54DayPct = percentChange(valueDaysAgo(closes, 54), currentPrice);
        double return60DayPct = percentChange(valueDaysAgo(closes, 60), currentPrice);
        long currentVolume = volumes.isEmpty() ? 0L : volumes.get(volumes.size() - 1).longValue();
        double averageVolume20 = volumes.isEmpty() ? 0D : averageLastLong(volumes, 20);
        double averageTradeValue20Billion = averageTradeValueLast(closes, volumes, 20);
        double averageLots20 = averageVolume20 / 1000D;
        double volatility20Pct = volatilityLast(closes, 20);
        double atr20 = averageTrueRange(highs.isEmpty() ? closes : highs, lows.isEmpty() ? closes : lows, closes, 20);
        double drawdownFromHigh60Pct = percentChange(maxLast(highs.isEmpty() ? closes : highs, 60), currentPrice);
        double rsi14 = computeRsi14(closes);
        double[] kd = computeStochasticKD(closes, highs.isEmpty() ? closes : highs, lows.isEmpty() ? closes : lows);

        return new TechnicalSnapshotVO(currentPrice, movingAverage18, movingAverage20, movingAverage54,
                movingAverage60, movingAverage120, return18DayPct, return20DayPct, return54DayPct,
                return60DayPct, currentVolume, averageVolume20, averageTradeValue20Billion, averageLots20,
                volatility20Pct, atr20, drawdownFromHigh60Pct, rsi14, kd[0], kd[1]);
    }

    private double parseCurrentPriceFromQuoteText(String text) {
        Matcher matcher = PROFILE_QUOTE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0D;
        }

        String combined = matcher.group(1);
        double percentChange = NumberParser.parseDouble(matcher.group(2));
        return extractCurrentPrice(combined, percentChange);
    }

    private String findFirst(String text, Pattern pattern) throws Exception {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new Exception("Unable to parse Yahoo page with pattern: " + pattern.pattern());
        }
        return matcher.group(1);
    }

    private String findOptional(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private List<NewsSignalVO.NewsItem> parseNewsItems(String text, TaiwanStockVO stock) {
        List<NewsSignalVO.NewsItem> items = new ArrayList<NewsSignalVO.NewsItem>();
        LinkedHashSet<String> dedup = new LinkedHashSet<String>();
        Matcher matcher = NEWS_BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            String publishedHint = safe(matcher.group(1));
            String headline = cleanHeadline(matcher.group(2), stock);
            if (headline.length() == 0 || dedup.contains(headline)) {
                continue;
            }
            dedup.add(headline);
            items.add(new NewsSignalVO.NewsItem(headline, "MEDIA", "Yahoo個股新聞", publishedHint, "", 65D,
                    estimateFreshnessScore(publishedHint)));
            if (items.size() >= 8) {
                break;
            }
        }
        return items;
    }

    private List<NewsSignalVO.NewsItem> parseAnnouncementItems(TaiwanStockVO stock) {
        List<NewsSignalVO.NewsItem> items = new ArrayList<NewsSignalVO.NewsItem>();
        try {
            String text = fetcher.fetchPageText("https://tw.stock.yahoo.com/quote/" + stock.getYahooSymbol() + "/announcement");
            String section = slice(text, "個股公告", "我的自選股");
            if (section == null || section.length() == 0 || section.equals(text)) {
                section = text;
            }
            LinkedHashSet<String> dedup = new LinkedHashSet<String>();
            Matcher matcher = ANNOUNCEMENT_BLOCK_PATTERN.matcher(section);
            while (matcher.find()) {
                String publishedHint = safe(matcher.group(1));
                String headline = cleanAnnouncementHeadline(matcher.group(2), stock);
                if (headline.length() == 0 || dedup.contains(headline)) {
                    continue;
                }
                dedup.add(headline);
                items.add(new NewsSignalVO.NewsItem(headline, "OFFICIAL", "公司公告", publishedHint, "", 95D,
                        estimateFreshnessScore(publishedHint)));
                if (items.size() >= 4) {
                    break;
                }
            }
        } catch (Exception ignored) {
            return items;
        }
        return items;
    }

    private List<String> parseNewsFallbackSnippets(String text, TaiwanStockVO stock) {
        List<String> snippets = new ArrayList<String>();
        if (text == null || text.trim().length() == 0) {
            return snippets;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] parts = normalized.split("[。！？；]");
        for (String part : parts) {
            String snippet = cleanHeadline(part, stock);
            if (snippet.length() == 0 || snippets.contains(snippet)) {
                continue;
            }
            snippets.add(snippet);
            if (snippets.size() >= 6) {
                break;
            }
        }
        return snippets;
    }

    private String cleanHeadline(String raw, TaiwanStockVO stock) {
        String headline = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        headline = headline.replace("Yahoo奇摩股市", "").replace("Yahoo奇摩新聞", "").trim();
        if (headline.length() < 8 || headline.length() > 90) {
            return "";
        }
        if (headline.contains("加入自選股") || headline.contains("看更多") || headline.contains("我的自選股")) {
            return "";
        }
        String code = stock == null ? "" : stock.getCode();
        String name = stock == null ? "" : stock.getName();
        if (headline.contains(code) || headline.contains(name)) {
            return headline;
        }
        String lowered = headline.toLowerCase();
        if (lowered.contains("ai") || headline.contains("矽光子") || headline.contains("衛星")
                || headline.contains("半導體") || headline.contains("伺服器") || headline.contains("封裝")
                || headline.contains("機器人") || headline.contains("cpo") || headline.contains("光通訊")
                || headline.contains("光模組") || headline.contains("低軌") || headline.contains("散熱")
                || headline.contains("電池") || headline.contains("重電")) {
            return headline;
        }
        return "";
    }

    private String cleanAnnouncementHeadline(String raw, TaiwanStockVO stock) {
        String headline = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        headline = headline.replace("公告", "").replace("個股公告", "").trim();
        if (headline.length() < 8 || headline.length() > 100) {
            return "";
        }
        if (headline.contains("看更多") || headline.contains("我的自選股")) {
            return "";
        }
        String code = stock == null ? "" : stock.getCode();
        String name = stock == null ? "" : stock.getName();
        if (headline.contains(code) || headline.contains(name)) {
            return headline;
        }
        if (headline.contains("董事會") || headline.contains("股利") || headline.contains("法說")
                || headline.contains("庫藏股") || headline.contains("重訊") || headline.contains("營收")
                || headline.contains("決議") || headline.contains("處分") || headline.contains("增資")
                || headline.contains("減資") || headline.contains("合約") || headline.contains("財報")) {
            return headline;
        }
        return "";
    }

    private double estimateFreshnessScore(String publishedHint) {
        String hint = safe(publishedHint);
        if (hint.length() == 0) {
            return 0D;
        }
        if (hint.contains("分鐘前")) {
            long minutes = extractLeadingNumber(hint);
            return clamp(100D - minutes * 0.6D, 82D, 100D);
        }
        if (hint.contains("小時前")) {
            long hours = extractLeadingNumber(hint);
            return clamp(96D - hours * 4D, 64D, 96D);
        }
        if (hint.matches("\\d{4}/\\d{2}/\\d{2}")) {
            try {
                LocalDate date = LocalDate.parse(hint, DATE_FORMATTER);
                long days = Math.max(0L, ChronoUnit.DAYS.between(date, LocalDate.now()));
                return clamp(92D - days * 9D, 18D, 92D);
            } catch (Exception ignored) {
                return 40D;
            }
        }
        return 35D;
    }

    private long extractLeadingNumber(String text) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return 0L;
        }
        return NumberParser.parseLong(matcher.group(1));
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String slice(String text, String startToken, String endToken) {
        int start = text.indexOf(startToken);
        if (start < 0) {
            return text;
        }

        int end = text.indexOf(endToken, start);
        if (end < 0) {
            return text.substring(start);
        }
        return text.substring(start, end);
    }

    private String between(String text, String startToken, String endToken) {
        int start = text.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        start += startToken.length();

        int end = text.indexOf(endToken, start);
        if (end < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, end).trim();
    }

    private String after(String text, String startToken) {
        int start = text.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        return text.substring(start + startToken.length()).trim();
    }

    private List<String> findAll(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        List<String> values = new ArrayList<String>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private List<Long> extractLongValues(String text) {
        Matcher matcher = NUMBER_TOKEN_PATTERN.matcher(text);
        List<Long> values = new ArrayList<Long>();
        while (matcher.find()) {
            values.add(Long.valueOf(NumberParser.parseLong(matcher.group())));
        }
        return values;
    }

    private List<Double> extractDoubleSeries(JSONArray values) {
        List<Double> series = new ArrayList<Double>();
        if (values == null) {
            return series;
        }
        for (Object value : values) {
            if (value != null) {
                series.add(Double.valueOf(toDouble(value)));
            }
        }
        return series;
    }

    private List<Long> extractLongSeries(JSONArray values) {
        List<Long> series = new ArrayList<Long>();
        if (values == null) {
            return series;
        }
        for (Object value : values) {
            if (value != null) {
                series.add(Long.valueOf(Math.round(toDouble(value))));
            }
        }
        return series;
    }

    private double averageLast(List<Double> values, int count) {
        int size = Math.min(count, values.size());
        double total = 0D;
        for (int i = values.size() - size; i < values.size(); i++) {
            total += values.get(i).doubleValue();
        }
        return size == 0 ? 0D : total / size;
    }

    private double averageLastLong(List<Long> values, int count) {
        int size = Math.min(count, values.size());
        double total = 0D;
        for (int i = values.size() - size; i < values.size(); i++) {
            total += values.get(i).longValue();
        }
        return size == 0 ? 0D : total / size;
    }

    private double averageTradeValueLast(List<Double> closes, List<Long> volumes, int count) {
        int size = Math.min(count, Math.min(closes.size(), volumes.size()));
        double total = 0D;
        for (int i = closes.size() - size, j = volumes.size() - size; i < closes.size() && j < volumes.size(); i++, j++) {
            total += closes.get(i).doubleValue() * volumes.get(j).longValue();
        }
        return size == 0 ? 0D : total / size / 100000000D;
    }

    private double volatilityLast(List<Double> closes, int count) {
        int size = Math.min(count + 1, closes.size());
        if (size <= 1) {
            return 0D;
        }

        List<Double> returns = new ArrayList<Double>();
        for (int i = closes.size() - size + 1; i < closes.size(); i++) {
            double previous = closes.get(i - 1).doubleValue();
            double current = closes.get(i).doubleValue();
            if (previous != 0D) {
                returns.add(Double.valueOf((current - previous) * 100D / previous));
            }
        }
        if (returns.isEmpty()) {
            return 0D;
        }

        double mean = 0D;
        for (Double dailyReturn : returns) {
            mean += dailyReturn.doubleValue();
        }
        mean /= returns.size();

        double variance = 0D;
        for (Double dailyReturn : returns) {
            double difference = dailyReturn.doubleValue() - mean;
            variance += difference * difference;
        }
        return Math.sqrt(variance / returns.size());
    }

    private double averageTrueRange(List<Double> highs, List<Double> lows, List<Double> closes, int count) {
        int size = Math.min(highs.size(), Math.min(lows.size(), closes.size()));
        if (size <= 1) {
            return 0D;
        }

        int start = Math.max(1, size - count);
        double total = 0D;
        int samples = 0;
        for (int i = start; i < size; i++) {
            double high = highs.get(i).doubleValue();
            double low = lows.get(i).doubleValue();
            double prevClose = closes.get(i - 1).doubleValue();
            double trueRange = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            total += trueRange;
            samples++;
        }
        return samples == 0 ? 0D : total / samples;
    }

    private double maxLast(List<Double> values, int count) {
        int size = Math.min(count, values.size());
        double max = 0D;
        for (int i = values.size() - size; i < values.size(); i++) {
            max = Math.max(max, values.get(i).doubleValue());
        }
        return max;
    }

    private double valueDaysAgo(List<Double> values, int days) {
        if (values.isEmpty()) {
            return 0D;
        }
        int index = Math.max(0, values.size() - days);
        return values.get(index).doubleValue();
    }

    private double percentChange(double base, double current) {
        if (base == 0D) {
            return 0D;
        }
        return (current - base) * 100D / base;
    }

    private double extractCurrentPrice(String combinedText, double displayedPercentChange) {
        if (combinedText == null) {
            return 0D;
        }

        String normalized = combinedText.replace(",", "").trim();
        if (normalized.length() < 2) {
            return 0D;
        }

        double bestPrice = 0D;
        double bestError = Double.MAX_VALUE;
        for (int i = 1; i < normalized.length(); i++) {
            String priceText = normalized.substring(0, i);
            String changeText = normalized.substring(i);
            if (!isParsableNumber(priceText) || !isParsableNumber(changeText)) {
                continue;
            }

            double currentPrice = Double.parseDouble(priceText);
            double change = Double.parseDouble(changeText);
            double previousClose = currentPrice - change;
            if (currentPrice <= 0D || previousClose <= 0D) {
                continue;
            }

            double calculatedPercent = change * 100D / previousClose;
            double error = Math.abs(calculatedPercent - displayedPercentChange);
            if (error < bestError) {
                bestError = error;
                bestPrice = currentPrice;
            }
        }

        return bestError <= 0.2D ? bestPrice : 0D;
    }

    private boolean isParsableNumber(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        if ("+".equals(value) || "-".equals(value)) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private double applyDateRisk(String dateText, int withinDays, String label, List<String> reasons, double penalty) {
        if (dateText == null || dateText.trim().length() == 0 || "-".equals(dateText.trim())) {
            return 0D;
        }
        try {
            LocalDate eventDate = LocalDate.parse(dateText.trim(), DATE_FORMATTER);
            long days = ChronoUnit.DAYS.between(LocalDate.now(), eventDate);
            if (days >= 0L && days <= withinDays) {
                reasons.add(label + " " + dateText);
                return penalty;
            }
        } catch (Exception ex) {
            return 0D;
        }
        return 0D;
    }

    private int minSize(int... sizes) {
        int min = Integer.MAX_VALUE;
        for (int size : sizes) {
            min = Math.min(min, size);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(delimiter);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    // ── Technical Indicators ──────────────────────────────────────────────────

    private double computeRsi14(List<Double> closes) {
        int period = 14;
        int n = closes.size();
        if (n < period + 1) {
            return 50D;
        }
        // Use last 2*period values for initial + smoothing
        int initStart = Math.max(1, n - period * 2);
        double avgGain = 0D, avgLoss = 0D;
        int initEnd = initStart + period;
        if (initEnd > n) {
            initEnd = n;
        }
        for (int i = initStart; i < initEnd; i++) {
            double change = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            if (change > 0D) {
                avgGain += change;
            } else {
                avgLoss += -change;
            }
        }
        int initCount = initEnd - initStart;
        if (initCount > 0) {
            avgGain /= initCount;
            avgLoss /= initCount;
        }
        for (int i = initEnd; i < n; i++) {
            double change = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            double gain = change > 0D ? change : 0D;
            double loss = change < 0D ? -change : 0D;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }
        if (avgLoss == 0D) {
            return 100D;
        }
        double rs = avgGain / avgLoss;
        return 100D - 100D / (1D + rs);
    }

    private double[] computeStochasticKD(List<Double> closes, List<Double> highs, List<Double> lows) {
        int period = 9;
        int smoothK = 3;
        int n = closes.size();
        // Need (period - 1) + (smoothK * 2 - 1) data points to compute K and D
        int numRaw = smoothK * 2 - 1;  // 5 raw %K values needed
        if (n < period + numRaw - 1) {
            return new double[]{ 50D, 50D };
        }
        // Compute numRaw raw %K values ending at today (n-1)
        double[] rawK = new double[numRaw];
        for (int i = 0; i < numRaw; i++) {
            int idx = n - numRaw + i;
            int rangeStart = Math.max(0, idx - period + 1);
            double highest = Double.NEGATIVE_INFINITY, lowest = Double.POSITIVE_INFINITY;
            for (int j = rangeStart; j <= idx; j++) {
                highest = Math.max(highest, highs.get(j).doubleValue());
                lowest = Math.min(lowest, lows.get(j).doubleValue());
            }
            rawK[i] = (highest == lowest) ? 50D : (closes.get(idx).doubleValue() - lowest) / (highest - lowest) * 100D;
        }
        // kSeries[i] = 3-period SMA of rawK[i..i+2]; gives smoothK values for D calculation
        double[] kSeries = new double[smoothK];
        for (int i = 0; i < smoothK; i++) {
            double sum = 0D;
            for (int j = 0; j < smoothK; j++) {
                sum += rawK[i + j];
            }
            kSeries[i] = sum / smoothK;
        }
        double K = kSeries[smoothK - 1];  // latest K
        double D = 0D;
        for (int i = 0; i < smoothK; i++) {
            D += kSeries[i];
        }
        D /= smoothK;
        return new double[]{ K, D };
    }
}
