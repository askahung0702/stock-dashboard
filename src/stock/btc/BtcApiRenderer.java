package stock.btc;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.HttpTextFetcher;

public class BtcApiRenderer {
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DERIBIT_EXPIRY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyy")
            .toFormatter(Locale.ENGLISH);
    private static final double TREND_WEIGHT = 0.42D;
    private static final double LIQUIDITY_WEIGHT = 0.24D;
    private static final double POSITIONING_WEIGHT = 0.22D;
    private static final double ONCHAIN_WEIGHT = 0.12D;
    private static final int FAST_FETCH_TIMEOUT_MS = 4000;
    private static final int FAST_FETCH_RETRIES = 1;
    private static final long FUTURE_WAIT_MS = 6500L;

    private final HttpTextFetcher fetcher = new HttpTextFetcher();
    private final JSONParser parser = new JSONParser();

    @SuppressWarnings("unchecked")
    public String renderLatestJson() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        PriceData price;
        MacroData macro;
        PositioningData positioning;
        OnChainData onchain;
        try {
            Future<PriceData> priceFuture = executor.submit(() -> fetchPriceData());
            Future<List<SeriesPoint>> dffFuture = executor.submit(() -> fetchFredSeries("DFF"));
            Future<List<SeriesPoint>> dxyFuture = executor.submit(() -> fetchFredSeries("DTWEXBGS"));
            Future<List<SeriesPoint>> m2Future = executor.submit(() -> fetchFredSeries("M2SL"));
            Future<PositioningData> positioningFuture = executor.submit(() -> fetchDeribit());
            Future<OnChainData> onchainFuture = executor.submit(() -> fetchNode());

            price = await(priceFuture, unavailablePrice("Yahoo Finance BTC-USD 逾時或失敗"));
            List<SeriesPoint> dff = await(dffFuture, Collections.<SeriesPoint>emptyList());
            List<SeriesPoint> dxy = await(dxyFuture, Collections.<SeriesPoint>emptyList());
            List<SeriesPoint> m2 = await(m2Future, Collections.<SeriesPoint>emptyList());
            macro = buildMacroData(dff, dxy, m2);
            positioning = await(positioningFuture, unavailablePositioning("Deribit Futures 逾時或失敗"));
            onchain = await(onchainFuture, unavailableOnchain("Bitcoin Core RPC 未連線或逾時"));
        } finally {
            executor.shutdownNow();
        }

        List<SignalItem> signals = new ArrayList<SignalItem>();
        ScoreCard trend = scoreTrend(price, signals);
        ScoreCard liquidity = scoreLiquidity(macro, signals);
        ScoreCard positioningScore = scorePositioning(positioning, signals);
        ScoreCard onchainScore = scoreOnchain(onchain, signals);

        double weighted = trend.score * TREND_WEIGHT + liquidity.score * LIQUIDITY_WEIGHT + positioningScore.score * POSITIONING_WEIGHT;
        double totalWeight = TREND_WEIGHT + LIQUIDITY_WEIGHT + POSITIONING_WEIGHT;
        if (onchain.available) {
            weighted += onchainScore.score * ONCHAIN_WEIGHT;
            totalWeight += ONCHAIN_WEIGHT;
        }
        double marketScore = totalWeight > 0D ? weighted / totalWeight : 50D;
        double confidenceScore = 55D + (positioning.available ? 15D : 0D) + (onchain.available ? 15D : 0D) + (macro.complete ? 15D : 0D);
        confidenceScore = clamp(confidenceScore, 0D, 100D);
        String marketState = marketState(marketScore);
        String turningPoint = turningPoint(price, macro, positioning, onchain, marketScore);
        String turningPointText = turningPointText(turningPoint);
        String today = LocalDate.now(TAIPEI_ZONE).format(DATE_FORMATTER);

        JSONObject root = new JSONObject();
        root.put("generatedAt", today);
        root.put("marketScore", round1(marketScore));
        root.put("marketState", marketState);
        root.put("turningPoint", turningPoint);
        root.put("turningPointText", turningPointText);
        root.put("summary", "BTC 儀表板以趨勢、流動性、期貨定位、鏈上壓力四層交叉驗證，避免只看 K 線。資料完整度 " + round1(confidenceScore) + " 分。");
        root.put("confidenceScore", round1(confidenceScore));
        root.put("price", priceJson(price));
        root.put("macro", macroJson(macro));
        root.put("positioning", positioningJson(positioning));
        root.put("onchain", onchainJson(onchain));
        root.put("scores", scoresJson(trend, liquidity, positioningScore, onchainScore));
        root.put("signals", signalsJson(signals));
        root.put("priceHistory", priceHistoryJson(price));
        root.put("sources", sourcesJson(price, macro, positioning, onchain));

        BtcHistoryStore store = new BtcHistoryStore();
        if (price.available) {
            store.upsertSnapshot(today, snapshotJson(today, marketScore, marketState, turningPoint, confidenceScore,
                    price, macro, positioning, onchain, trend, liquidity, positioningScore, onchainScore));
        }
        Map<String, JSONObject> snapshots = store.loadSnapshots();
        root.put("historySummary", historySummaryJson(store, snapshots));
        root.put("stateHistory", stateHistoryJson(store, snapshots));
        root.put("turningHistory", turningHistoryJson(store, snapshots));
        root.put("backtest", backtestJson(store, snapshots));
        return root.toJSONString();
    }

    private PriceData fetchPriceData() throws Exception {
        String jsonText = fetcher.fetchJson("https://query1.finance.yahoo.com/v8/finance/chart/BTC-USD?range=1y&interval=1d",
                FAST_FETCH_TIMEOUT_MS, FAST_FETCH_RETRIES);
        JSONObject root = (JSONObject) parser.parse(jsonText);
        JSONObject chart = (JSONObject) root.get("chart");
        JSONArray results = chart == null ? null : (JSONArray) chart.get("result");
        if (results == null || results.isEmpty()) throw new Exception("BTC chart api has no result");
        JSONObject result = (JSONObject) results.get(0);
        JSONArray timestamps = (JSONArray) result.get("timestamp");
        JSONObject meta = (JSONObject) result.get("meta");
        JSONObject indicators = (JSONObject) result.get("indicators");
        JSONArray quoteArray = indicators == null ? null : (JSONArray) indicators.get("quote");
        if (quoteArray == null || quoteArray.isEmpty()) throw new Exception("BTC chart quote missing");
        JSONObject quote = (JSONObject) quoteArray.get(0);
        JSONArray closeArray = (JSONArray) quote.get("close");
        JSONArray highArray = (JSONArray) quote.get("high");
        JSONArray volumeArray = (JSONArray) quote.get("volume");

        PriceData price = new PriceData();
        for (int i = 0; timestamps != null && i < timestamps.size(); i++) {
            Long epoch = asLong(timestamps.get(i));
            Double close = asDouble(valueAt(closeArray, i));
            if (epoch == null || close == null || close.doubleValue() <= 0D) continue;
            PricePoint point = new PricePoint();
            point.date = Instant.ofEpochSecond(epoch.longValue()).atZone(ZoneId.of("UTC")).toLocalDate();
            point.close = close.doubleValue();
            point.high = positive(asDouble(valueAt(highArray, i)), point.close);
            point.volume = Math.max(0L, asLongValue(valueAt(volumeArray, i)));
            price.history.add(point);
        }
        if (price.history.size() < 210) throw new Exception("BTC history not enough");
        Collections.sort(price.history, new Comparator<PricePoint>() {
            public int compare(PricePoint a, PricePoint b) { return a.date.compareTo(b.date); }
        });
        PricePoint latest = price.history.get(price.history.size() - 1);
        price.currentPrice = positive(asDouble(meta == null ? null : meta.get("regularMarketPrice")), latest.close);
        price.latestDate = latest.date.format(DATE_FORMATTER);
        price.sma20 = avgClose(price.history, 20);
        price.sma50 = avgClose(price.history, 50);
        price.sma200 = avgClose(price.history, 200);
        price.change7dPct = pct(closeAgo(price.history, 7), price.currentPrice);
        price.change30dPct = pct(closeAgo(price.history, 30), price.currentPrice);
        price.change90dPct = pct(closeAgo(price.history, 90), price.currentPrice);
        price.drawdownFrom90dHighPct = pct(maxHigh(price.history, 90), price.currentPrice);
        price.volumeRatio20 = avgVolume(price.history, 20) > 0D ? latest.volume / avgVolume(price.history, 20) : 0D;
        price.priceAboveSma20 = price.currentPrice >= price.sma20;
        price.priceAboveSma50 = price.currentPrice >= price.sma50;
        price.priceAboveSma200 = price.currentPrice >= price.sma200;
        price.sma20Above50 = price.sma20 >= price.sma50;
        price.sma50Above200 = price.sma50 >= price.sma200;
        price.available = true;
        price.statusMessage = "Yahoo Finance BTC-USD";
        return price;
    }

    private List<SeriesPoint> fetchFredSeries(String id) throws Exception {
        String csv = fetcher.fetchJson("https://fred.stlouisfed.org/graph/fredgraph.csv?id=" + id,
                FAST_FETCH_TIMEOUT_MS, FAST_FETCH_RETRIES);
        BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(csv.getBytes(Charset.forName("UTF-8"))), Charset.forName("UTF-8")));
        try {
            List<SeriesPoint> points = new ArrayList<SeriesPoint>();
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length < 2 || ".".equals(parts[1].trim())) continue;
                try {
                    SeriesPoint p = new SeriesPoint();
                    p.date = LocalDate.parse(parts[0].trim(), DATE_FORMATTER);
                    p.value = Double.parseDouble(parts[1].trim());
                    points.add(p);
                } catch (Exception ignored) {}
            }
            if (points.isEmpty()) throw new Exception("FRED empty: " + id);
            return points;
        } finally { reader.close(); }
    }

    private MacroData buildMacroData(List<SeriesPoint> dff, List<SeriesPoint> dxy, List<SeriesPoint> m2) {
        MacroData data = new MacroData();
        data.fedFundsAvailable = !dff.isEmpty();
        data.dxyAvailable = !dxy.isEmpty();
        data.m2Available = !m2.isEmpty();
        if (data.fedFundsAvailable) {
            data.fedFundsLatest = lastValue(dff);
            data.fedFunds3mDelta = data.fedFundsLatest - obsAgo(dff, 63);
            data.fedFundsDate = lastDate(dff);
        }
        if (data.dxyAvailable) {
            data.dxyLatest = lastValue(dxy);
            data.dxy20dPct = pct(obsAgo(dxy, 20), data.dxyLatest);
            data.dxy60dPct = pct(obsAgo(dxy, 60), data.dxyLatest);
            data.dxyDate = lastDate(dxy);
        }
        if (data.m2Available) {
            data.m2LatestTrillion = lastValue(m2) / 1000D;
            data.m2YoYPct = pct(obsAgo(m2, 12), lastValue(m2));
            data.m26mPct = pct(obsAgo(m2, 6), lastValue(m2));
            data.m2Date = lastDate(m2);
        }
        data.complete = data.fedFundsAvailable && data.dxyAvailable && data.m2Available;
        if (data.complete) data.statusMessage = "FRED 宏觀資料完整";
        else if (data.fedFundsAvailable || data.dxyAvailable || data.m2Available) data.statusMessage = "FRED 宏觀資料部分缺漏";
        else data.statusMessage = "FRED 宏觀資料逾時或失敗";
        return data;
    }
    private PositioningData fetchDeribit() {
        PositioningData data = new PositioningData();
        try {
            String jsonText = fetcher.fetchJson(
                    "https://www.deribit.com/api/v2/public/get_book_summary_by_currency?currency=BTC&kind=future",
                    FAST_FETCH_TIMEOUT_MS, FAST_FETCH_RETRIES);
            JSONObject root = (JSONObject) parser.parse(jsonText);
            JSONArray result = (JSONArray) root.get("result");
            if (result == null || result.isEmpty()) { data.statusMessage = "Deribit 無資料"; return data; }
            List<FutureContract> contracts = new ArrayList<FutureContract>();
            for (Object item : result) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject json = (JSONObject) item;
                String name = text(json.get("instrument_name"));
                if (name.contains("PERPETUAL")) continue;
                LocalDate expiry = expiry(name);
                if (expiry == null) continue;
                long days = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("UTC")), expiry);
                if (days < 0L || days > 120L) continue;
                FutureContract c = new FutureContract();
                c.name = name; c.expiry = expiry; c.days = days;
                c.mark = asDoubleValue(json.get("mark_price"));
                c.spot = positive(asDouble(json.get("underlying_price")),
                        asDoubleValue(json.get("estimated_delivery_price")));
                c.openInterest = asDoubleValue(json.get("open_interest"));
                c.volumeUsd = asDoubleValue(json.get("volume_usd"));
                if (c.mark <= 0D || c.spot <= 0D) continue;
                c.basisPct = (c.mark - c.spot) * 100D / c.spot * 365D / Math.max(1L, c.days);
                contracts.add(c);
            }
            if (contracts.isEmpty()) { data.statusMessage = "找不到近月期貨"; return data; }
            Collections.sort(contracts, new Comparator<FutureContract>() {
                public int compare(FutureContract a, FutureContract b) { return Long.compare(a.days, b.days); }
            });
            FutureContract ref = contracts.get(0);
            double oi = 0D, vol = 0D;
            for (FutureContract c : contracts) { oi += c.openInterest; vol += c.volumeUsd; }
            data.available = true;
            data.referenceContract = ref.name;
            data.referenceDate = ref.expiry.format(DATE_FORMATTER);
            data.annualizedBasisPct = ref.basisPct;
            data.openInterestBtc = oi;
            data.volumeUsd = vol;
            data.statusMessage = "近月年化基差與總未平倉量";
            return data;
        } catch (Exception ex) {
            data.statusMessage = shortMsg(ex);
            return data;
        }
    }

    private OnChainData fetchNode() {
        OnChainData data = new OnChainData();
        String rpcUrl = text(System.getenv("BTC_NODE_RPC_URL"));
        if (rpcUrl.length() == 0) { data.statusMessage = "未設定 BTC_NODE_RPC_URL"; return data; }
        try {
            String user = text(System.getenv("BTC_NODE_RPC_USER"));
            String password = text(System.getenv("BTC_NODE_RPC_PASSWORD"));
            JSONObject blockchain = rpc(rpcUrl, user, password, "getblockchaininfo");
            JSONObject mempool = rpc(rpcUrl, user, password, "getmempoolinfo");
            data.available = true;
            data.referenceDate = LocalDate.now(TAIPEI_ZONE).format(DATE_FORMATTER);
            data.blocks = asLongValue(blockchain.get("blocks"));
            data.headers = asLongValue(blockchain.get("headers"));
            data.verificationProgressPct = asDoubleValue(blockchain.get("verificationprogress")) * 100D;
            data.mempoolCount = asLongValue(mempool.get("size"));
            data.mempoolBytesMb = asDoubleValue(mempool.get("bytes")) / 1024D / 1024D;
            data.mempoolMinFeeSatVb = asDoubleValue(mempool.get("mempoolminfee")) * 100000000D / 1000D;
            data.statusMessage = "來自本地 Bitcoin Core";
            return data;
        } catch (Exception ex) {
            data.statusMessage = shortMsg(ex);
            return data;
        }
    }

    private ScoreCard scoreTrend(PriceData p, List<SignalItem> signals) {
        if (!p.available) return new ScoreCard("trend", "趨勢結構", 50D, "價格、均線、量能是否形成右側結構", p.statusMessage);
        double s = 50D;
        if (p.priceAboveSma20) { s += 8D; signals.add(sig("趨勢", "站上 20 日均線", fmtPrice(p.currentPrice), "positive", "短線轉強")); } else s -= 8D;
        if (p.priceAboveSma50) { s += 10D; signals.add(sig("趨勢", "站上 50 日均線", fmtPrice(p.sma50), "positive", "中期結構健康")); } else { s -= 10D; signals.add(sig("趨勢", "跌破 50 日均線", fmtPrice(p.sma50), "negative", "反彈還不能當反轉")); }
        if (p.priceAboveSma200) s += 12D; else s -= 12D;
        if (p.sma20Above50) s += 8D; else s -= 6D;
        if (p.sma50Above200) s += 7D; else s -= 6D;
        if (p.change30dPct >= 3D && p.change30dPct <= 18D) { s += 7D; signals.add(sig("趨勢", "30 日動能健康", fmtPct(p.change30dPct), "positive", "有動能但還沒過熱")); }
        else if (p.change30dPct > 25D) { s -= 9D; signals.add(sig("趨勢", "30 日過熱", fmtPct(p.change30dPct), "negative", "追價勝率下降")); }
        else if (p.change30dPct < -12D) s -= 8D;
        if (p.drawdownFrom90dHighPct >= -10D && p.drawdownFrom90dHighPct <= 2D) s += 8D; else if (p.drawdownFrom90dHighPct < -20D) s -= 10D;
        if (p.volumeRatio20 >= 1.05D && p.volumeRatio20 <= 2.30D) { s += 7D; signals.add(sig("趨勢", "量能配合", fmtRatio(p.volumeRatio20), "positive", "轉折需要基本量能確認")); }
        else if (p.volumeRatio20 > 3D) s -= 5D;
        return new ScoreCard("trend", "趨勢結構", clamp(s, 0D, 100D), "價格、均線、量能是否形成右側結構", p.priceAboveSma50 ? "價格站上 50 日均線。" : "價格仍在 50 日均線下方。");
    }

    private ScoreCard scoreLiquidity(MacroData m, List<SignalItem> signals) {
        if (!m.fedFundsAvailable && !m.dxyAvailable && !m.m2Available) {
            return new ScoreCard("liquidity", "宏觀流動性", 50D, "利率、美元與貨幣供給是否配合", m.statusMessage);
        }
        double s = 50D;
        if (m.fedFundsAvailable) {
            if (m.fedFunds3mDelta <= -0.25D) { s += 14D; signals.add(sig("流動性", "Fed 3 個月變化", fmtSigned(m.fedFunds3mDelta), "positive", "資金價格下降有利風險資產")); }
            else if (m.fedFunds3mDelta >= 0.25D) { s -= 14D; signals.add(sig("流動性", "Fed 3 個月變化", fmtSigned(m.fedFunds3mDelta), "negative", "資金環境偏緊")); }
        }
        if (m.dxyAvailable) {
            if (m.dxy20dPct <= -1.5D) { s += 12D; signals.add(sig("流動性", "美元 20 日變化", fmtPct(m.dxy20dPct), "positive", "美元轉弱通常對 BTC 友善")); }
            else if (m.dxy20dPct >= 1.5D) { s -= 12D; signals.add(sig("流動性", "美元 20 日變化", fmtPct(m.dxy20dPct), "negative", "美元偏強是逆風")); }
            if (m.dxy60dPct <= -3D) s += 8D; else if (m.dxy60dPct >= 3D) s -= 8D;
        }
        if (m.m2Available) {
            if (m.m2YoYPct >= 4D) { s += 10D; signals.add(sig("流動性", "M2 年增", fmtPct(m.m2YoYPct), "positive", "中期流動性回升")); }
            else if (m.m2YoYPct <= 0D) { s -= 10D; signals.add(sig("流動性", "M2 年增", fmtPct(m.m2YoYPct), "negative", "缺流動性的趨勢不耐走")); }
            if (m.m26mPct > 1.5D) s += 6D; else if (m.m26mPct < -1D) s -= 6D;
        }
        return new ScoreCard("liquidity", "宏觀流動性", clamp(s, 0D, 100D), "利率、美元與貨幣供給是否配合",
                m.dxyAvailable ? (m.dxy20dPct <= 0D ? "美元沒有明顯逆風。" : "美元仍偏強。") : m.statusMessage);
    }

    private ScoreCard scorePositioning(PositioningData p, List<SignalItem> signals) {
        if (!p.available) return new ScoreCard("positioning", "期貨定位", 50D, "看期貨正價差與槓桿是否健康", p.statusMessage);
        double s = 50D;
        if (p.annualizedBasisPct >= 5D && p.annualizedBasisPct <= 15D) { s += 15D; signals.add(sig("定位", "年化基差健康", fmtPct(p.annualizedBasisPct), "positive", "有追價但不算瘋狂")); }
        else if (p.annualizedBasisPct > 15D && p.annualizedBasisPct <= 25D) { s += 6D; signals.add(sig("定位", "年化基差偏熱", fmtPct(p.annualizedBasisPct), "neutral", "偏多但需留意過熱")); }
        else if (p.annualizedBasisPct > 25D) { s -= 12D; signals.add(sig("定位", "年化基差過熱", fmtPct(p.annualizedBasisPct), "negative", "槓桿擁擠")); }
        else if (p.annualizedBasisPct < 0D) { s -= 10D; signals.add(sig("定位", "年化基差倒掛", fmtPct(p.annualizedBasisPct), "negative", "風險偏好偏弱")); }
        if (p.openInterestBtc >= 100000D) s += 5D;
        if (p.volumeUsd >= 500000000D) s += 4D;
        return new ScoreCard("positioning", "期貨定位", clamp(s, 0D, 100D), "看機構槓桿是否願意以健康溢價承接", "參考合約 " + p.referenceContract + "，年化基差 " + fmtPct(p.annualizedBasisPct));
    }

    private ScoreCard scoreOnchain(OnChainData o, List<SignalItem> signals) {
        if (!o.available) return new ScoreCard("onchain", "鏈上壓力", 50D, "鏈上壓力用來避免在極熱區追價", o.statusMessage);
        double s = 50D;
        if (o.mempoolMinFeeSatVb <= 5D) { s += 10D; signals.add(sig("鏈上", "mempool fee 低", fmtFee(o.mempoolMinFeeSatVb), "positive", "不是極端 FOMO 區")); }
        else if (o.mempoolMinFeeSatVb <= 15D) s += 4D;
        else if (o.mempoolMinFeeSatVb >= 40D) { s -= 12D; signals.add(sig("鏈上", "mempool fee 高", fmtFee(o.mempoolMinFeeSatVb), "negative", "鏈上開始擁塞")); }
        if (o.mempoolBytesMb >= 250D) s -= 8D;
        if (o.verificationProgressPct >= 99.95D) s += 2D;
        return new ScoreCard("onchain", "鏈上壓力", clamp(s, 0D, 100D), "看鏈上是否已進入擁塞尖峰", "mempool min fee " + fmtFee(o.mempoolMinFeeSatVb));
    }
    private String marketState(double score) {
        if (score >= 68D) return "Risk-on";
        if (score >= 58D) return "Constructive";
        if (score >= 45D) return "Neutral";
        if (score >= 35D) return "Caution";
        return "Risk-off";
    }

    private String turningPoint(PriceData p, MacroData m, PositioningData pos, OnChainData on, double score) {
        if (!p.available) return "等待確認";
        boolean breakout = p.priceAboveSma20 && p.priceAboveSma50 && p.sma20Above50 && p.change30dPct >= 0D && p.change30dPct <= 18D && p.drawdownFrom90dHighPct >= -8D && p.drawdownFrom90dHighPct <= 2D && p.volumeRatio20 >= 1.05D && p.volumeRatio20 <= 2.30D;
        boolean overheating = p.change30dPct > 25D && p.volumeRatio20 > 1.6D && pos.available && pos.annualizedBasisPct > 18D;
        boolean breakdown = !p.priceAboveSma50 && p.change30dPct < -10D && (m.dxy20dPct > 1D || score < 45D);
        boolean recovery = p.priceAboveSma20 && !p.priceAboveSma200 && m.dxy20dPct < 0D && m.fedFunds3mDelta <= 0D;
        boolean chainFroth = on.available && on.mempoolMinFeeSatVb >= 40D;
        if (breakout && score >= 60D) return "上行轉折確認";
        if (overheating || chainFroth) return "過熱風險升高";
        if (breakdown) return "下行破位風險";
        if (recovery && score >= 52D) return "早期回升建構中";
        return "等待確認";
    }

    private String turningPointText(String state) {
        if ("上行轉折確認".equals(state)) return "價格結構、量能與期貨正價差同步配合，較適合等回踩而不是追長紅。";
        if ("過熱風險升高".equals(state)) return "不是說一定反轉，但追價勝率會下降，操作要從進攻轉為等拉回。";
        if ("下行破位風險".equals(state)) return "趨勢還沒修復，先別把反彈誤判成反轉。";
        if ("早期回升建構中".equals(state)) return "右側初期通常最難熬，但也常是風險報酬比較好的位置。";
        return "訊號混合時，最好的決策通常是等下一個明確確認。";
    }

    @SuppressWarnings("unchecked") private JSONObject priceJson(PriceData p) { JSONObject o = new JSONObject(); o.put("currentPrice", round2(p.currentPrice)); o.put("latestDate", p.latestDate); o.put("sma20", round2(p.sma20)); o.put("sma50", round2(p.sma50)); o.put("sma200", round2(p.sma200)); o.put("change7dPct", round2(p.change7dPct)); o.put("change30dPct", round2(p.change30dPct)); o.put("change90dPct", round2(p.change90dPct)); o.put("drawdownFrom90dHighPct", round2(p.drawdownFrom90dHighPct)); o.put("volumeRatio20", round2(p.volumeRatio20)); o.put("priceAboveSma20", Boolean.valueOf(p.priceAboveSma20)); o.put("priceAboveSma50", Boolean.valueOf(p.priceAboveSma50)); o.put("priceAboveSma200", Boolean.valueOf(p.priceAboveSma200)); return o; }
    @SuppressWarnings("unchecked") private JSONObject macroJson(MacroData m) { JSONObject o = new JSONObject(); o.put("fedFundsLatest", round2(m.fedFundsLatest)); o.put("fedFunds3mDelta", round2(m.fedFunds3mDelta)); o.put("fedFundsDate", m.fedFundsDate); o.put("dxyLatest", round2(m.dxyLatest)); o.put("dxy20dPct", round2(m.dxy20dPct)); o.put("dxy60dPct", round2(m.dxy60dPct)); o.put("dxyDate", m.dxyDate); o.put("m2LatestTrillion", round2(m.m2LatestTrillion)); o.put("m2YoYPct", round2(m.m2YoYPct)); o.put("m26mPct", round2(m.m26mPct)); o.put("m2Date", m.m2Date); o.put("complete", Boolean.valueOf(m.complete)); o.put("statusMessage", m.statusMessage); return o; }
    @SuppressWarnings("unchecked") private JSONObject positioningJson(PositioningData p) { JSONObject o = new JSONObject(); o.put("available", Boolean.valueOf(p.available)); o.put("referenceContract", p.referenceContract); o.put("referenceDate", p.referenceDate); o.put("annualizedBasisPct", round2(p.annualizedBasisPct)); o.put("openInterestBtc", round2(p.openInterestBtc)); o.put("volumeUsd", round2(p.volumeUsd)); o.put("statusMessage", p.statusMessage); return o; }
    @SuppressWarnings("unchecked") private JSONObject onchainJson(OnChainData o) { JSONObject j = new JSONObject(); j.put("available", Boolean.valueOf(o.available)); j.put("referenceDate", o.referenceDate); j.put("blocks", Long.valueOf(o.blocks)); j.put("headers", Long.valueOf(o.headers)); j.put("verificationProgressPct", round2(o.verificationProgressPct)); j.put("mempoolCount", Long.valueOf(o.mempoolCount)); j.put("mempoolBytesMb", round2(o.mempoolBytesMb)); j.put("mempoolMinFeeSatVb", round2(o.mempoolMinFeeSatVb)); j.put("statusMessage", o.statusMessage); return j; }
    @SuppressWarnings("unchecked") private JSONArray scoresJson(ScoreCard... cards) { JSONArray a = new JSONArray(); for (ScoreCard c : cards) { JSONObject o = new JSONObject(); o.put("id", c.id); o.put("label", c.label); o.put("score", round1(c.score)); o.put("description", c.description); o.put("summary", c.summary); a.add(o); } return a; }
    @SuppressWarnings("unchecked") private JSONArray signalsJson(List<SignalItem> items) { JSONArray a = new JSONArray(); for (SignalItem i : items) { JSONObject o = new JSONObject(); o.put("group", i.group); o.put("label", i.label); o.put("value", i.value); o.put("tone", i.tone); o.put("note", i.note); a.add(o); } return a; }
    @SuppressWarnings("unchecked") private JSONArray priceHistoryJson(PriceData p) { JSONArray a = new JSONArray(); int start = Math.max(0, p.history.size() - 180); for (int i = start; i < p.history.size(); i++) { PricePoint pt = p.history.get(i); JSONObject o = new JSONObject(); o.put("date", pt.date.format(DATE_FORMATTER)); o.put("close", round2(pt.close)); a.add(o); } return a; }
    @SuppressWarnings("unchecked") private JSONArray sourcesJson(PriceData p, MacroData m, PositioningData pos, OnChainData on) { JSONArray a = new JSONArray(); a.add(source("Yahoo Finance BTC-USD 日線", p.available ? "ok" : "warn", p.available ? "價格/均線/量能" : p.statusMessage, p.latestDate)); a.add(source("FRED DFF", m.fedFundsAvailable ? "ok" : "warn", m.fedFundsAvailable ? "資金價格" : m.statusMessage, m.fedFundsDate)); a.add(source("FRED DTWEXBGS", m.dxyAvailable ? "ok" : "warn", m.dxyAvailable ? "美元強弱" : m.statusMessage, m.dxyDate)); a.add(source("FRED M2SL", m.m2Available ? "ok" : "warn", m.m2Available ? "貨幣供給" : m.statusMessage, m.m2Date)); a.add(source("Deribit Futures", pos.available ? "ok" : "warn", pos.available ? "期貨年化基差/未平倉量" : pos.statusMessage, pos.referenceDate)); a.add(source("Bitcoin Core RPC", on.available ? "ok" : "warn", on.available ? "鏈上擁塞/驗證進度" : on.statusMessage, on.referenceDate)); return a; }
    @SuppressWarnings("unchecked") private JSONObject source(String name, String status, String detail, String asOf) { JSONObject o = new JSONObject(); o.put("name", name); o.put("status", status); o.put("detail", detail); o.put("asOf", asOf == null ? "" : asOf); return o; }
    @SuppressWarnings("unchecked") private JSONObject snapshotJson(String date, double marketScore, String marketState, String turningPoint, double confidenceScore, PriceData price, MacroData macro, PositioningData positioning, OnChainData onchain, ScoreCard trend, ScoreCard liquidity, ScoreCard positioningScore, ScoreCard onchainScore) {
        JSONObject o = new JSONObject();
        o.put("date", date);
        o.put("marketScore", round1(marketScore));
        o.put("marketState", marketState);
        o.put("turningPoint", turningPoint);
        o.put("confidenceScore", round1(confidenceScore));
        o.put("price", round2(price.currentPrice));
        o.put("change30dPct", round2(price.change30dPct));
        o.put("drawdownFrom90dHighPct", round2(price.drawdownFrom90dHighPct));
        o.put("volumeRatio20", round2(price.volumeRatio20));
        o.put("dxy20dPct", round2(macro.dxy20dPct));
        o.put("fedFunds3mDelta", round2(macro.fedFunds3mDelta));
        o.put("m2YoYPct", round2(macro.m2YoYPct));
        o.put("annualizedBasisPct", round2(positioning.annualizedBasisPct));
        o.put("mempoolMinFeeSatVb", round2(onchain.mempoolMinFeeSatVb));
        o.put("trendScore", round1(trend.score));
        o.put("liquidityScore", round1(liquidity.score));
        o.put("positioningScore", round1(positioningScore.score));
        o.put("onchainScore", round1(onchainScore.score));
        return o;
    }
    @SuppressWarnings("unchecked") private JSONObject historySummaryJson(BtcHistoryStore store, Map<String, JSONObject> snapshots) {
        List<String> dates = store.sortedDates(snapshots);
        JSONObject o = new JSONObject();
        o.put("count", Long.valueOf(dates.size()));
        o.put("firstDate", dates.isEmpty() ? "" : dates.get(0));
        o.put("lastDate", dates.isEmpty() ? "" : dates.get(dates.size() - 1));
        int riskOn = 0, caution = 0, confirmed = 0, overheat = 0;
        for (String date : dates) {
            JSONObject s = snapshots.get(date);
            String state = text(s.get("marketState"));
            String turning = text(s.get("turningPoint"));
            if ("Risk-on".equals(state) || "Constructive".equals(state)) riskOn++;
            if ("Caution".equals(state) || "Risk-off".equals(state)) caution++;
            if ("上行轉折確認".equals(turning)) confirmed++;
            if ("過熱風險升高".equals(turning)) overheat++;
        }
        o.put("riskOnDays", Long.valueOf(riskOn));
        o.put("cautionDays", Long.valueOf(caution));
        o.put("confirmedTurns", Long.valueOf(confirmed));
        o.put("overheatWarnings", Long.valueOf(overheat));
        return o;
    }
    @SuppressWarnings("unchecked") private JSONArray stateHistoryJson(BtcHistoryStore store, Map<String, JSONObject> snapshots) {
        List<String> dates = store.sortedDates(snapshots);
        JSONArray array = new JSONArray();
        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i);
            JSONObject s = snapshots.get(date);
            JSONObject o = new JSONObject();
            o.put("date", date);
            o.put("marketScore", round1(asDoubleValue(s.get("marketScore"))));
            o.put("marketState", text(s.get("marketState")));
            o.put("turningPoint", text(s.get("turningPoint")));
            o.put("price", round2(asDoubleValue(s.get("price"))));
            o.put("trendScore", round1(asDoubleValue(s.get("trendScore"))));
            o.put("liquidityScore", round1(asDoubleValue(s.get("liquidityScore"))));
            o.put("positioningScore", round1(asDoubleValue(s.get("positioningScore"))));
            o.put("onchainScore", round1(asDoubleValue(s.get("onchainScore"))));
            o.put("return7dPct", round2(futureReturnPct(dates, snapshots, i, 7)));
            o.put("return30dPct", round2(futureReturnPct(dates, snapshots, i, 30)));
            array.add(o);
        }
        return array;
    }
    @SuppressWarnings("unchecked") private JSONArray turningHistoryJson(BtcHistoryStore store, Map<String, JSONObject> snapshots) {
        List<String> dates = store.sortedDates(snapshots);
        JSONArray array = new JSONArray();
        int added = 0;
        for (int i = dates.size() - 1; i >= 0 && added < 24; i--) {
            String date = dates.get(i);
            JSONObject s = snapshots.get(date);
            String turning = text(s.get("turningPoint"));
            if (turning.length() == 0 || "等待確認".equals(turning)) continue;
            JSONObject o = new JSONObject();
            o.put("date", date);
            o.put("turningPoint", turning);
            o.put("marketState", text(s.get("marketState")));
            o.put("marketScore", round1(asDoubleValue(s.get("marketScore"))));
            o.put("price", round2(asDoubleValue(s.get("price"))));
            o.put("return7dPct", round2(futureReturnPct(dates, snapshots, i, 7)));
            o.put("return30dPct", round2(futureReturnPct(dates, snapshots, i, 30)));
            array.add(o);
            added++;
        }
        return array;
    }
    @SuppressWarnings("unchecked") private JSONArray backtestJson(BtcHistoryStore store, Map<String, JSONObject> snapshots) {
        List<String> dates = store.sortedDates(snapshots);
        String[] labels = new String[] { "上行轉折確認", "早期回升建構中", "過熱風險升高", "下行破位風險", "等待確認" };
        JSONArray array = new JSONArray();
        for (String label : labels) {
            int count = 0, pos7 = 0, pos30 = 0, valid7 = 0, valid30 = 0;
            double sum7 = 0D, sum30 = 0D;
            for (int i = 0; i < dates.size(); i++) {
                JSONObject s = snapshots.get(dates.get(i));
                if (!label.equals(text(s.get("turningPoint")))) continue;
                count++;
                double r7 = futureReturnPct(dates, snapshots, i, 7);
                double r30 = futureReturnPct(dates, snapshots, i, 30);
                if (!Double.isNaN(r7)) { valid7++; sum7 += r7; if (r7 > 0D) pos7++; }
                if (!Double.isNaN(r30)) { valid30++; sum30 += r30; if (r30 > 0D) pos30++; }
            }
            if (count == 0) continue;
            JSONObject o = new JSONObject();
            o.put("label", label);
            o.put("count", Long.valueOf(count));
            o.put("avg7dPct", valid7 == 0 ? null : Double.valueOf(round2(sum7 / valid7)));
            o.put("avg30dPct", valid30 == 0 ? null : Double.valueOf(round2(sum30 / valid30)));
            o.put("positive7Rate", valid7 == 0 ? null : Double.valueOf(round1(pos7 * 100D / valid7)));
            o.put("positive30Rate", valid30 == 0 ? null : Double.valueOf(round1(pos30 * 100D / valid30)));
            array.add(o);
        }
        return array;
    }

    private <T> T await(Future<T> future, T fallback) {
        try {
            return future.get(FUTURE_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            future.cancel(true);
            return fallback;
        }
    }

    private PriceData unavailablePrice(String message) {
        PriceData price = new PriceData();
        price.statusMessage = message;
        return price;
    }

    private PositioningData unavailablePositioning(String message) {
        PositioningData data = new PositioningData();
        data.statusMessage = message;
        return data;
    }

    private OnChainData unavailableOnchain(String message) {
        OnChainData data = new OnChainData();
        data.statusMessage = message;
        return data;
    }

    private double futureReturnPct(List<String> dates, Map<String, JSONObject> snapshots, int index, int daysForward) {
        int futureIndex = index + daysForward;
        if (index < 0 || futureIndex >= dates.size()) return Double.NaN;
        double currentPrice = asDoubleValue(snapshots.get(dates.get(index)).get("price"));
        double futurePrice = asDoubleValue(snapshots.get(dates.get(futureIndex)).get("price"));
        return currentPrice <= 0D || futurePrice <= 0D ? Double.NaN : pct(currentPrice, futurePrice);
    }

    private JSONObject rpc(String url, String user, String password, String method) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("Content-Type", "application/json");
            if (user.length() > 0 || password.length() > 0) {
                String auth = java.util.Base64.getEncoder().encodeToString((user + ":" + password).getBytes(Charset.forName("UTF-8")));
                c.setRequestProperty("Authorization", "Basic " + auth);
            }
            String payload = "{\"jsonrpc\":\"1.0\",\"id\":\"btc\",\"method\":\"" + method + "\",\"params\":[]}";
            DataOutputStream out = new DataOutputStream(c.getOutputStream()); try { out.write(payload.getBytes(Charset.forName("UTF-8"))); } finally { out.close(); }
            int status = c.getResponseCode(); String body = readAll(status >= 400 ? c.getErrorStream() : c.getInputStream());
            if (status < 200 || status >= 300) throw new Exception("RPC " + method + " HTTP " + status + ": " + body);
            JSONObject root = (JSONObject) parser.parse(body); return (JSONObject) root.get("result");
        } finally { if (c != null) c.disconnect(); }
    }

    private String readAll(InputStream stream) throws Exception { if (stream == null) return ""; BufferedReader r = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8"))); try { StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line); return b.toString(); } finally { r.close(); } }
    private LocalDate expiry(String name) { String[] p = name.split("-"); if (p.length < 2) return null; try { return LocalDate.parse(p[1], DERIBIT_EXPIRY_FORMATTER); } catch (DateTimeParseException ex) { return null; } }
    private Object valueAt(JSONArray a, int i) { return a == null || i < 0 || i >= a.size() ? null : a.get(i); }
    private double avgClose(List<PricePoint> h, int d) { int s = Math.max(0, h.size() - d); double sum = 0D; int c = 0; for (int i = s; i < h.size(); i++) { sum += h.get(i).close; c++; } return c == 0 ? 0D : sum / c; }
    private double avgVolume(List<PricePoint> h, int d) { int s = Math.max(0, h.size() - d); double sum = 0D; int c = 0; for (int i = s; i < h.size(); i++) { sum += h.get(i).volume; c++; } return c == 0 ? 0D : sum / c; }
    private double closeAgo(List<PricePoint> h, int d) { int idx = Math.max(0, h.size() - 1 - d); return h.get(idx).close; }
    private double maxHigh(List<PricePoint> h, int d) { int s = Math.max(0, h.size() - d); double max = 0D; for (int i = s; i < h.size(); i++) max = Math.max(max, h.get(i).high); return max; }
    private double lastValue(List<SeriesPoint> p) { return p.isEmpty() ? 0D : p.get(p.size() - 1).value; }
    private String lastDate(List<SeriesPoint> p) { return p.isEmpty() ? "" : p.get(p.size() - 1).date.format(DATE_FORMATTER); }
    private double obsAgo(List<SeriesPoint> p, int ago) { return p.isEmpty() ? 0D : p.get(Math.max(0, p.size() - 1 - ago)).value; }
    private double pct(double prev, double curr) { return prev == 0D || curr == 0D ? 0D : (curr - prev) * 100D / prev; }
    private double round1(double v) { return Math.round(v * 10D) / 10D; }
    private double round2(double v) { return Math.round(v * 100D) / 100D; }
    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private double positive(Double v, double fallback) { return v == null || v.doubleValue() <= 0D ? fallback : v.doubleValue(); }
    private Double asDouble(Object v) { if (v == null) return null; if (v instanceof Number) return Double.valueOf(((Number) v).doubleValue()); try { return Double.valueOf(Double.parseDouble(v.toString().trim())); } catch (Exception ex) { return null; } }
    private double asDoubleValue(Object v) { Double n = asDouble(v); return n == null ? 0D : n.doubleValue(); }
    private Long asLong(Object v) { if (v == null) return null; if (v instanceof Number) return Long.valueOf(((Number) v).longValue()); try { return Long.valueOf(Long.parseLong(v.toString().trim())); } catch (Exception ex) { return null; } }
    private long asLongValue(Object v) { Long n = asLong(v); return n == null ? 0L : n.longValue(); }
    private String text(Object v) { return v == null ? "" : v.toString().trim(); }
    private String shortMsg(Exception ex) { return ex == null || ex.getMessage() == null ? "錯誤" : ex.getMessage().trim(); }
    private String fmtPrice(double v) { return "$" + String.format(Locale.US, "%,.0f", Double.valueOf(v)); }
    private String fmtPct(double v) { return String.format(Locale.US, "%+.1f%%", Double.valueOf(v)); }
    private String fmtSigned(double v) { return String.format(Locale.US, "%+.2f", Double.valueOf(v)); }
    private String fmtRatio(double v) { return String.format(Locale.US, "%.2fx", Double.valueOf(v)); }
    private String fmtFee(double v) { return String.format(Locale.US, "%.1f sat/vB", Double.valueOf(v)); }
    private SignalItem sig(String group, String label, String value, String tone, String note) { SignalItem s = new SignalItem(); s.group = group; s.label = label; s.value = value; s.tone = tone; s.note = note; return s; }

    private static class PriceData { boolean available; String statusMessage = ""; String latestDate = ""; double currentPrice; double sma20; double sma50; double sma200; double change7dPct; double change30dPct; double change90dPct; double drawdownFrom90dHighPct; double volumeRatio20; boolean priceAboveSma20; boolean priceAboveSma50; boolean priceAboveSma200; boolean sma20Above50; boolean sma50Above200; List<PricePoint> history = new ArrayList<PricePoint>(); }
    private static class PricePoint { LocalDate date; double close; double high; long volume; }
    private static class SeriesPoint { LocalDate date; double value; }
    private static class MacroData { boolean fedFundsAvailable; boolean dxyAvailable; boolean m2Available; double fedFundsLatest; double fedFunds3mDelta; String fedFundsDate = ""; double dxyLatest; double dxy20dPct; double dxy60dPct; String dxyDate = ""; double m2LatestTrillion; double m2YoYPct; double m26mPct; String m2Date = ""; boolean complete; String statusMessage = ""; }
    private static class PositioningData { boolean available; String referenceContract = ""; String referenceDate = ""; double annualizedBasisPct; double openInterestBtc; double volumeUsd; String statusMessage = ""; }
    private static class FutureContract { String name; LocalDate expiry; long days; double mark; double spot; double basisPct; double openInterest; double volumeUsd; }
    private static class OnChainData { boolean available; String referenceDate = ""; long blocks; long headers; double verificationProgressPct; long mempoolCount; double mempoolBytesMb; double mempoolMinFeeSatVb; String statusMessage = ""; }
    private static class ScoreCard { String id; String label; double score; String description; String summary; ScoreCard(String id, String label, double score, String description, String summary) { this.id = id; this.label = label; this.score = score; this.description = description; this.summary = summary; } }
    private static class SignalItem { String group; String label; String value; String tone; String note; }
}




