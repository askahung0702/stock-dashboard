package stock;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import stock.vo.StockAnalysisResultVO;
import stock.vo.TaiwanStockVO;
import stock.vo.TechnicalSnapshotVO;

public class TaiwanStockAnalyzer {

    private static final int DEFAULT_TOP_COUNT = 30;
    private static final double LIKELY_THRESHOLD = 72D;
    private static final double WATCHLIST_THRESHOLD = 58D;
    private static final double VOLUME_SURGE_RATIO_THRESHOLD = 1.8D;
    private static final double RAW_SCORE_MAX = 135D;
    private static final double MIN_LIQUIDITY_SCORE = 4D;
    private static final double MIN_SELECTION_FINANCIAL_SCORE = 8D;
    private static final double MIN_LIKELY_FINANCIAL_SCORE = 12D;
    private static final double LIKELY_MIN_VOLUME_RATIO = 0.8D;
    private static final double LIKELY_MAX_VOLUME_RATIO = 2.5D;
    private static final double BUYPOINT_THRESHOLD = 75D;
    private static final double HIGH_CONVICTION_QUALITY_SCORE = 70D;
    private static final double HIGH_CONVICTION_TREND_SCORE = 65D;
    private static final double HIGH_CONVICTION_STRUCTURE_SCORE = 70D;
    private static final double HIGH_CONVICTION_RISK_REWARD_SCORE = 60D;
    private static final double MOMENTUM_ATTACK_SELECTION_SCORE = 76D;
    private static final double MOMENTUM_ATTACK_SCORE = 72D;
    private static final double SWING_QUALITY_SCORE = 72D;
    private static final double SWING_RISK_REWARD_RATIO = 1.4D;
    private static final double CATALYST_WATCH_SCORE = 68D;
    private static final String POST_CLOSE_HIGH_CONVICTION = "高勝率候選";
    private static final String POST_CLOSE_MOMENTUM_ATTACK = "短線主攻";
    private static final String POST_CLOSE_SWING_POSITION = "波段布局";
    private static final String POST_CLOSE_CATALYST_WATCH = "催化觀察";
    private static final String POST_CLOSE_GENERAL_WATCH = "一般觀察";
    private static final String POST_CLOSE_STAND_ASIDE = "暫不出手";
    private static final String SIGNAL_NEXT_DAY_CONTINUATION = "隔日續強";
    private static final String SIGNAL_MULTI_DAY_CONTINUATION = "3-5日延續";
    private static final String SIGNAL_SWING_WINDOW = "5-10日波段";
    private static final String SIGNAL_PENDING = "待確認";
    private static final String ENTRY_RULE_NEXT_CLOSE = "T+1 close";
    private static final String EXIT_RULE_STOP_TARGET_OR_HORIZON = "stop/target/or horizon";
    private static final String VALIDATION_MODE_DAILY_CLOSE = "daily close-to-close";
    private static final int MIN_BACKTEST_SAMPLE_COUNT = 80;
    private static final long PER_STOCK_PAUSE_MS = parseLongProperty("stock.analyzer.perStockPauseMs", 600L);
    private static final long THROTTLE_COOLDOWN_MS = parseLongProperty("stock.analyzer.throttleCooldownMs", 30000L);
    private static final boolean CLOSE_DEFER_NEWS = parseBooleanProperty("stock.close.deferNews", true);
    private static final boolean CLOSE_DEFER_EVENT_RISK = parseBooleanProperty("stock.close.deferEventRisk", true);
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE_STAMP_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DAILY_SNAPSHOT_DIRECTORY_NAME = "daily_snapshots";
    private static final String ANALYSIS_VERSION = "stage-cache-v1";
    private static final String[] POSITIVE_NEWS_KEYWORDS = { "營收", "訂單", "擴產", "漲價", "法說", "合作", "量產", "受惠",
            "成長", "上修", "創高", "布局", "AI", "矽光子", "低軌", "衛星", "CoWoS", "散熱", "機器人" };
    private static final String[] NEGATIVE_NEWS_KEYWORDS = { "下修", "虧損", "減資", "處分", "停工", "裁員", "違約", "訴訟",
            "跌停", "示警", "衰退", "急凍", "砍單", "延後", "風險", "調查" };
    private static final String[] CAUTION_NEWS_KEYWORDS = { "增資", "轉單", "震盪", "觀望", "波動", "修正", "不確定", "保守" };
    private static final String[] POSITIVE_EVENT_KEYWORDS = { "庫藏股", "買回", "法說", "上修", "接單", "訂單", "量產", "擴產",
            "漲價", "股利", "配息", "除息", "合作", "受惠", "獲利", "增溫" };
    private static final String[] NEGATIVE_EVENT_KEYWORDS = { "現增", "私募", "訴訟", "處分資產", "減資", "虧損", "下修", "罰款",
            "調查", "停工", "事故", "違約", "重整", "撤單" };
    private static final String[] NEUTRAL_EVENT_KEYWORDS = { "董事會", "澄清", "說明", "公告", "決議", "召開", "更正" };

    private final TaiwanStockMarketProvider marketProvider = new TaiwanStockMarketProvider();
    private final YahooTaiwanStockService yahooService = new YahooTaiwanStockService();
    private final StockHistoryDatabase historyDatabase = new StockHistoryDatabase();
    private final ThemeBasketRepository themeBasketRepository = new ThemeBasketRepository();
    private final NewsThemeReferenceAnalyzer newsThemeReferenceAnalyzer = new NewsThemeReferenceAnalyzer();
    private final MarketThemeNewsAnalyzer marketThemeNewsAnalyzer = new MarketThemeNewsAnalyzer();
    private final MarketThemeRadar marketThemeRadar = new MarketThemeRadar();
    private final NewsCompanySummarizer newsCompanySummarizer = new NewsCompanySummarizer();
    private final LowFrequencyDataCache lowFrequencyDataCache = LowFrequencyDataCache.loadDefault();
    private final ScoringConfig scoringConfig = ScoringConfig.loadDefault();
    private MarketThemeNewsAnalyzer.ReferenceBundle lastMarketThemeReferenceBundle = MarketThemeNewsAnalyzer.ReferenceBundle.empty();
    private String lastNewsOnlyReferenceDate = "";
    private MarketRegime activeMarketRegime = MarketRegime.RANGE_BOUND;
    private ScoringStrategy activeScoringStrategy = new ConfiguredScoringStrategy(scoringConfig.getQualification(),
            scoringConfig.getProfile(activeMarketRegime));
    private String runStage = "full";
    private Map<String, StockHistoryDatabase.SnapshotRow> sameDayCloseRawRowsByCode = new HashMap<String, StockHistoryDatabase.SnapshotRow>();

    public List<StockAnalysisResultVO> analyze(int maxStocks) throws Exception {
        List<TaiwanStockVO> allStocks = marketProvider.loadAllStocks();
        if (maxStocks > 0 && maxStocks < allStocks.size()) {
            allStocks = new ArrayList<TaiwanStockVO>(allStocks.subList(0, maxStocks));
        }
        sameDayCloseRawRowsByCode = loadSameDayCloseRawRows();
        markStageRunStatus("running", 0, sameDayCloseRawRowsByCode.isEmpty() ? "close raw unavailable"
                : "reusing close raw " + sameDayCloseRawRowsByCode.size());

        List<StockAnalysisResultVO> results = new ArrayList<StockAnalysisResultVO>();
        int index = 0;
        for (TaiwanStockVO stock : allStocks) {
            index++;
            System.out.println(
                    "Analyzing " + index + "/" + allStocks.size() + " " + stock.getYahooSymbol() + " " + stock.getName());
            try {
                StockAnalysisResultVO result = analyzeOneStock(stock);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception ex) {
                System.out.println("Skip " + stock.getYahooSymbol() + " because " + ex.getMessage());
                if (isThrottleLikeFailure(ex)) {
                    System.out.println("Cooling down " + THROTTLE_COOLDOWN_MS
                            + " ms after Yahoo throttle-like failure.");
                    Thread.sleep(THROTTLE_COOLDOWN_MS);
                }
            }
            Thread.sleep(PER_STOCK_PAUSE_MS);
        }

        lastMarketThemeReferenceBundle = marketThemeNewsAnalyzer.analyze(results);
        applyMarketThemeNewsMetadata(results);
        newsThemeReferenceAnalyzer.apply(results);
        finalizeCompositeScores(results, loadHistoricalSnapshotsSafely());
        Collections.sort(results, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int selectionCompare = Double.compare(right.getSelectionScore(), left.getSelectionScore());
                if (selectionCompare != 0) {
                    return selectionCompare;
                }
                return Double.compare(right.getScore(), left.getScore());
            }
        });
        markStageRunStatus("analyzed", results.size(), "analysis finished");

        return results;
    }

    public List<StockAnalysisResultVO> analyzeNewsOnly(int maxStocks) throws Exception {
        List<TaiwanStockVO> allStocks = marketProvider.loadAllStocks();
        if (maxStocks > 0 && maxStocks < allStocks.size()) {
            allStocks = new ArrayList<TaiwanStockVO>(allStocks.subList(0, maxStocks));
        }
        refreshMarketThemeRadarSafely(allStocks);

        LatestSnapshotContext snapshotContext = loadLatestSnapshotContext();
        lastNewsOnlyReferenceDate = snapshotContext.date;

        List<StockAnalysisResultVO> results = new ArrayList<StockAnalysisResultVO>();
        int index = 0;
        for (TaiwanStockVO stock : allStocks) {
            index++;
            System.out.println("Collecting news " + index + "/" + allStocks.size() + " " + stock.getYahooSymbol() + " "
                    + stock.getName());
            try {
                NewsSignalVO newsSignal = yahooService.fetchNewsSignal(stock);
                if (newsSignal == null || newsSignal.getHeadlineCount() <= 0
                        || emptyIfBlank(newsSignal.getSummaryText(), "").length() == 0) {
                    Thread.sleep(PER_STOCK_PAUSE_MS);
                    continue;
                }
                StockAnalysisResultVO result = new StockAnalysisResultVO();
                result.setStock(stock);
                StockHistoryDatabase.SnapshotRow snapshotRow = snapshotContext.rowsByCode.get(stock.getCode());
                if (snapshotRow != null) {
                    hydrateNewsOnlyResult(result, snapshotRow);
                }
                applyNewsSignalMetadata(result, newsSignal);
                applyThemeBasketMetadata(result, newsSignal);
                EventSignalProfile eventSignalProfile = inferEventSignalProfile(newsSignal, new EventRiskVO(0D, ""));
                double newsScore = scoreNewsSignal(newsSignal);
                double newsRiskScore = scoreNewsRisk(newsSignal, 0D);
                if ("正向催化".equals(eventSignalProfile.direction)) {
                    newsScore = NumberParser.clamp(newsScore + Math.min(8D, eventSignalProfile.confidence * 0.08D), 0D,
                            100D);
                } else if ("負向風險".equals(eventSignalProfile.direction)) {
                    newsRiskScore = NumberParser.clamp(newsRiskScore + Math.min(10D, eventSignalProfile.confidence * 0.10D),
                            0D, 100D);
                }
                result.setNewsScore(newsScore);
                result.setNewsRiskScore(newsRiskScore);
                result.setEventDirection(eventSignalProfile.direction);
                result.setEventConfidence(eventSignalProfile.confidence);
                result.setEventFreshnessDays(eventSignalProfile.freshnessDays);
                result.setEventTypeSummary(eventSignalProfile.typeSummary);
                results.add(result);
            } catch (Exception ex) {
                System.out.println("Skip news " + stock.getYahooSymbol() + " because " + ex.getMessage());
                if (isThrottleLikeFailure(ex)) {
                    System.out.println("Cooling down " + THROTTLE_COOLDOWN_MS
                            + " ms after Yahoo throttle-like failure.");
                    Thread.sleep(THROTTLE_COOLDOWN_MS);
                }
            }
            Thread.sleep(PER_STOCK_PAUSE_MS);
        }

        lastMarketThemeReferenceBundle = marketThemeNewsAnalyzer.analyze(results);
        applyMarketThemeNewsMetadata(results);
        newsThemeReferenceAnalyzer.apply(results);
        for (StockAnalysisResultVO result : results) {
            result.setAnalysisNote(buildNewsOnlyAnalysisNote(result, snapshotContext.date));
            applyNarrativeSummary(result);
        }
        Collections.sort(results, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int newsCompare = Double.compare(computeNewsOnlyPriority(right), computeNewsOnlyPriority(left));
                if (newsCompare != 0) {
                    return newsCompare;
                }
                return left.getStock().getCode().compareTo(right.getStock().getCode());
            }
        });
        return results;
    }

    public void writeCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        writer.write('\uFEFF');
        writer.println(
                "code,name,market,industry,score,raw_score,selection_score,momentum_score,quality_score,sector_score,theme_score,primary_theme,theme_tags,trend_persistence_score,trend_persistence_days,news_score,news_risk_score,relative_strength_score,industry_return_strength,industry_volume_strength,industry_flow_strength,event_direction,event_confidence,event_freshness_days,event_type_summary,news_summary,news_digest,news_source_summary,latest_news_published_hint,news_source_credibility_score,news_freshness_score,news_source_count,news_official_source_count,news_media_source_count,company_summary,recent_news_brief,transformation_hint,practical_advice,advice_confidence,structure_score,structure_label,risk_reward_score,risk_reward_ratio,turnaround_score,revenue_growth_signal_score,earnings_turnaround_signal_score,profitability_turnaround_signal_score,one_off_risk_score,turnaround_label,turnaround_reason,suggested_stop_price,suggested_stop_pct,suggested_target_price,upside_potential_pct,buy_point_score,buy_point_label,buy_point_reason,signal_type,signal_horizon_days,entry_rule,exit_rule,validation_mode,hard_exclude,hard_exclude_reason,data_quality_grade,winrate_priority_score,expected_return_score,max_drawdown_penalty,backtest_cohort,post_close_priority_score,post_close_category,post_close_action,post_close_reason,data_confidence,data_confidence_reason,selection_qualified,eligibility_reason,revenue_score,chips_score,liquidity_score,valuation_score,technical_score,financial_quality_score,event_risk_penalty,current_price,latest_revenue_yoy_pct,avg_3m_revenue_yoy_pct,accumulated_revenue_yoy_pct,positive_revenue_months,latest_institutional_net_lots,latest_institutional_net_ratio_pct,five_day_institutional_net_lots,five_day_institutional_net_ratio_pct,latest_foreign_net_lots,broker_net_lots,broker_net_ratio_pct,snapshot_stage,tech_ready,market_ready,institutional_ready,broker_ready,financial_ready,news_ready,analysis_version,source_updated_at,trailing_eps,trailing_pe,peer_average_pe,latest_quarter_eps,latest_quarter_eps_yoy_pct,positive_eps_quarters,latest_operating_cash_flow,latest_free_cash_flow,positive_operating_cash_flow_quarters,positive_free_cash_flow_quarters,ma18,ma20,ma54,ma60,ma120,return_18d_pct,return_20d_pct,return_54d_pct,return_60d_pct,volume_ratio,avg_lots_20,avg_trade_value_20_billion,volatility_20_pct,drawdown_from_high60_pct,gross_margin_pct,operating_margin_pct,roa_pct,roe_pct,debt_ratio_pct,current_ratio,non_operating_ratio_pct,note,score_reason,revenue_reason,chips_reason,liquidity_reason,valuation_reason,technical_reason,financial_quality_reason,event_risk_reason");

        for (StockAnalysisResultVO result : results) {
            writer.println(csv(result.getStock().getCode()) + "," + csv(result.getStock().getName()) + ","
                    + csv(result.getStock().getMarket()) + "," + csv(result.getIndustry()) + ","
                    + format(result.getScore()) + "," + format(result.getRawScore()) + ","
                    + format(result.getSelectionScore()) + "," + format(result.getMomentumScore()) + ","
                    + format(result.getQualityScore()) + "," + format(result.getSectorScore()) + ","
                    + format(result.getThemeScore()) + "," + csv(result.getPrimaryTheme()) + ","
                    + csv(result.getThemeTags()) + ","
                    + format(result.getTrendPersistenceScore()) + "," + result.getTrendPersistenceDays() + ","
                    + format(result.getNewsScore()) + "," + format(result.getNewsRiskScore()) + ","
                    + format(result.getRelativeStrengthScore()) + ","
                    + format(result.getIndustryReturnStrength()) + "," + format(result.getIndustryVolumeStrength())
                    + "," + format(result.getIndustryFlowStrength()) + "," + csv(result.getEventDirection()) + ","
                    + format(result.getEventConfidence()) + "," + result.getEventFreshnessDays() + ","
                    + csv(result.getEventTypeSummary()) + ","
                    + csv(result.getNewsSummary()) + "," + csv(result.getNewsDigest()) + ","
                    + csv(result.getNewsSourceSummary()) + "," + csv(result.getLatestNewsPublishedHint()) + ","
                    + format(result.getNewsSourceCredibilityScore()) + ","
                    + format(result.getNewsFreshnessScore()) + "," + result.getNewsSourceCount() + ","
                    + result.getNewsOfficialSourceCount() + "," + result.getNewsMediaSourceCount() + ","
                    + csv(result.getCompanySummary()) + "," + csv(result.getRecentNewsBrief()) + ","
                    + csv(result.getTransformationHint()) + "," + csv(result.getPracticalAdvice()) + ","
                    + format(result.getAdviceConfidence()) + ","
                    + format(result.getStructureScore()) + "," + csv(result.getStructureLabel()) + ","
                    + format(result.getRiskRewardScore()) + "," + format(result.getRiskRewardRatio()) + ","
                    + format(result.getTurnaroundScore()) + ","
                    + format(result.getRevenueGrowthSignalScore()) + ","
                    + format(result.getEarningsTurnaroundSignalScore()) + ","
                    + format(result.getProfitabilityTurnaroundSignalScore()) + ","
                    + format(result.getOneOffRiskScore()) + "," + csv(result.getTurnaroundLabel()) + ","
                    + csv(result.getTurnaroundReason()) + ","
                    + format(result.getSuggestedStopPrice()) + "," + format(result.getSuggestedStopPct()) + ","
                    + format(result.getSuggestedTargetPrice()) + "," + format(result.getUpsidePotentialPct()) + ","
                    + format(result.getBuyPointScore()) + ","
                    + csv(result.getBuyPointLabel()) + "," + csv(result.getBuyPointReason()) + ","
                    + csv(result.getSignalType()) + "," + result.getSignalHorizonDays() + ","
                    + csv(result.getEntryRule()) + "," + csv(result.getExitRule()) + ","
                    + csv(result.getValidationMode()) + ","
                    + csv(result.isHardExclude() ? "Y" : "N") + "," + csv(result.getHardExcludeReason()) + ","
                    + csv(result.getDataQualityGrade()) + ","
                    + format(result.getWinratePriorityScore()) + "," + format(result.getExpectedReturnScore()) + ","
                    + format(result.getMaxDrawdownPenalty()) + "," + csv(result.getBacktestCohort()) + ","
                    + format(result.getPostClosePriorityScore()) + "," + csv(result.getPostCloseCategory()) + ","
                    + csv(result.getPostCloseAction()) + "," + csv(result.getPostCloseReason()) + ","
                    + format(result.getDataConfidence()) + "," + csv(result.getDataConfidenceReason()) + ","
                    + csv(result.isSelectionQualified() ? "Y" : "N") + "," + csv(result.getEligibilityReason()) + ","
                    + format(result.getRevenueScore()) + ","
                    + format(result.getChipsScore()) + "," + format(result.getLiquidityScore()) + ","
                    + format(result.getValuationScore()) + "," + format(result.getTechnicalScore()) + ","
                    + format(result.getFinancialQualityScore()) + "," + format(result.getEventRiskPenalty()) + ","
                    + format(result.getCurrentPrice()) + ","
                    + format(result.getLatestRevenueYoY()) + "," + format(result.getAverageThreeMonthRevenueYoY())
                    + "," + format(result.getAccumulatedRevenueYoY()) + "," + result.getPositiveRevenueMonths() + ","
                    + result.getLatestInstitutionalNetLots() + "," + format(result.getLatestInstitutionalNetRatioPct())
                    + "," + result.getFiveDayInstitutionalNetLots() + ","
                    + format(result.getFiveDayInstitutionalNetRatioPct()) + "," + result.getLatestForeignNetLots()
                    + "," + result.getBrokerNetLots() + "," + format(result.getBrokerNetRatioPct()) + ","
                    + csv(result.getSnapshotStage()) + ","
                    + csv(result.isTechReady() ? "Y" : "N") + "," + csv(result.isMarketReady() ? "Y" : "N") + ","
                    + csv(result.isInstitutionalReady() ? "Y" : "N") + ","
                    + csv(result.isBrokerReady() ? "Y" : "N") + ","
                    + csv(result.isFinancialReady() ? "Y" : "N") + ","
                    + csv(result.isNewsReady() ? "Y" : "N") + ","
                    + csv(result.getAnalysisVersion()) + "," + csv(result.getSourceUpdatedAt()) + ","
                    + format(result.getTrailingFourQuarterEps()) + "," + format(result.getTrailingPe()) + ","
                    + format(result.getPeerAveragePe()) + "," + format(result.getLatestQuarterEps()) + ","
                    + format(result.getLatestQuarterEpsYoYPct()) + "," + result.getPositiveEpsQuarters() + ","
                    + result.getLatestOperatingCashFlow() + "," + result.getLatestFreeCashFlow() + ","
                    + result.getPositiveOperatingCashFlowQuarters() + "," + result.getPositiveFreeCashFlowQuarters()
                    + "," + format(result.getMovingAverage18()) + "," + format(result.getMovingAverage20()) + ","
                    + format(result.getMovingAverage54()) + "," + format(result.getMovingAverage60()) + ","
                    + format(result.getMovingAverage120()) + "," + format(result.getReturn18DayPct()) + ","
                    + format(result.getReturn20DayPct()) + "," + format(result.getReturn54DayPct()) + ","
                    + format(result.getReturn60DayPct()) + "," + format(result.getVolumeRatio()) + ","
                    + format(result.getAverageLots20()) + "," + format(result.getAverageTradeValue20Billion()) + ","
                    + format(result.getVolatility20Pct()) + "," + format(result.getDrawdownFromHigh60Pct()) + ","
                    + format(result.getGrossMarginPct()) + "," + format(result.getOperatingMarginPct()) + ","
                    + format(result.getReturnOnAssetsPct()) + "," + format(result.getReturnOnEquityPct()) + ","
                    + format(result.getDebtRatioPct()) + "," + format(result.getCurrentRatio()) + ","
                    + format(result.getNonOperatingRatioPct()) + "," + csv(result.getAnalysisNote()) + ","
                    + csv(result.getScoreReason()) + "," + csv(result.getRevenueReason()) + ","
                    + csv(result.getChipsReason()) + "," + csv(result.getLiquidityReason()) + ","
                    + csv(result.getValuationReason()) + "," + csv(result.getTechnicalReason()) + ","
                    + csv(result.getFinancialQualityReason()) + "," + csv(result.getEventRiskReason()));
        }
        writer.close();
    }

    public void writeLikelyCandidatesCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterLikelyCandidates(results), fileName);
    }

    public void writeHighConvictionCandidatesCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterByPostCloseCategory(results, POST_CLOSE_HIGH_CONVICTION), fileName);
    }

    public void writeMomentumAttackCandidatesCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterByPostCloseCategory(results, POST_CLOSE_MOMENTUM_ATTACK), fileName);
    }

    public void writeSwingPositionCandidatesCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterByPostCloseCategory(results, POST_CLOSE_SWING_POSITION), fileName);
    }

    public void writeCatalystWatchCandidatesCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterByPostCloseCategory(results, POST_CLOSE_CATALYST_WATCH), fileName);
    }

    public void writeLikelyVolumeSurgeCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterLikelyVolumeSurge(results), fileName);
    }

    public void writeNonLikelyVolumeSurgeCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        writeCsv(filterNonLikelyVolumeSurge(results), fileName);
    }

    public void writeThemeReferenceCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        writer.write('\uFEFF');
        writer.println(
                "code,name,market,industry,current_theme,current_theme_score,reference_theme,reference_theme_score,reference_industry,selection_score,buy_point_score,news_score,news_summary,reference_reason");
        for (StockAnalysisResultVO result : results) {
            if (result.getThemeReferenceScore() <= 0D && result.getThemeReferenceTheme().length() == 0
                    && result.getThemeReferenceIndustry().length() == 0) {
                continue;
            }
            writer.println(csv(result.getStock().getCode()) + "," + csv(result.getStock().getName()) + ","
                    + csv(result.getStock().getMarket()) + "," + csv(result.getIndustry()) + ","
                    + csv(result.getPrimaryTheme()) + "," + format(result.getThemeScore()) + ","
                    + csv(result.getThemeReferenceTheme()) + "," + format(result.getThemeReferenceScore()) + ","
                    + csv(result.getThemeReferenceIndustry()) + "," + format(result.getSelectionScore()) + ","
                    + format(result.getBuyPointScore()) + "," + format(result.getNewsScore()) + ","
                    + csv(result.getNewsSummary()) + "," + csv(result.getThemeReferenceReason()));
        }
        writer.close();
    }

    public void writeThemeMarketReferenceCsv(String fileName) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        writer.write('\uFEFF');
        writer.println("theme,theme_score,article_title,published_hint,url,mentioned_stocks,evidence");
        for (MarketThemeNewsAnalyzer.ArticleReference article : lastMarketThemeReferenceBundle.articles) {
            writer.println(csv(article.theme) + "," + format(article.themeScore) + "," + csv(article.title) + ","
                    + csv(article.publishedHint) + "," + csv(article.url) + ","
                    + csv(joinMentionStocks(article.mentions)) + "," + csv(article.evidence));
        }
        writer.close();
    }

    public void writeThemeMarketCandidatesCsv(String fileName) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        writer.write('\uFEFF');
        writer.println("code,name,market,industry,reference_theme,reference_score,article_count,mention_count,evidence_titles,article_urls");
        for (MarketThemeNewsAnalyzer.CandidateReference candidate : lastMarketThemeReferenceBundle.candidates) {
            writer.println(csv(candidate.code) + "," + csv(candidate.name) + "," + csv(candidate.market) + ","
                    + csv(candidate.industry) + "," + csv(candidate.referenceTheme) + ","
                    + format(candidate.referenceScore) + "," + candidate.articleCount + "," + candidate.mentionCount
                    + "," + csv(join(new ArrayList<String>(candidate.articleTitles), "；")) + ","
                    + csv(join(new ArrayList<String>(candidate.articleUrls), "；")));
        }
        writer.close();
    }

    public void writeNewsOnlyCsv(List<StockAnalysisResultVO> results, String fileName) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")));
        writer.write('\uFEFF');
        writer.println(
                "code,name,market,industry,reference_snapshot_date,news_priority_score,signal_type,signal_horizon_days,entry_rule,exit_rule,validation_mode,hard_exclude,hard_exclude_reason,data_quality_grade,winrate_priority_score,expected_return_score,max_drawdown_penalty,backtest_cohort,post_close_category,post_close_action,selection_score,buy_point_score,current_price,news_score,news_risk_score,relative_strength_score,event_direction,event_confidence,event_freshness_days,event_type_summary,news_source_summary,latest_news_published_hint,news_source_credibility_score,news_freshness_score,news_source_count,theme_reference_theme,theme_reference_score,market_theme,market_theme_score,news_summary,news_digest,theme_reference_reason,market_theme_reference_reason");
        for (StockAnalysisResultVO result : results) {
            writer.println(csv(result.getStock().getCode()) + "," + csv(result.getStock().getName()) + ","
                    + csv(result.getStock().getMarket()) + "," + csv(result.getIndustry()) + ","
                    + csv(lastNewsOnlyReferenceDate) + "," + format(computeNewsOnlyPriority(result)) + ","
                    + csv(result.getSignalType()) + "," + result.getSignalHorizonDays() + ","
                    + csv(result.getEntryRule()) + "," + csv(result.getExitRule()) + ","
                    + csv(result.getValidationMode()) + "," + csv(result.isHardExclude() ? "Y" : "N") + ","
                    + csv(result.getHardExcludeReason()) + "," + csv(result.getDataQualityGrade()) + ","
                    + format(result.getWinratePriorityScore()) + "," + format(result.getExpectedReturnScore()) + ","
                    + format(result.getMaxDrawdownPenalty()) + "," + csv(result.getBacktestCohort()) + ","
                    + csv(result.getPostCloseCategory()) + "," + csv(result.getPostCloseAction()) + ","
                    + format(result.getSelectionScore()) + "," + format(result.getBuyPointScore()) + ","
                    + format(result.getCurrentPrice()) + "," + format(result.getNewsScore()) + ","
                    + format(result.getNewsRiskScore()) + "," + format(result.getRelativeStrengthScore()) + ","
                    + csv(result.getEventDirection()) + "," + format(result.getEventConfidence()) + ","
                    + result.getEventFreshnessDays() + "," + csv(result.getEventTypeSummary()) + ","
                    + csv(result.getNewsSourceSummary()) + ","
                    + csv(result.getLatestNewsPublishedHint()) + ","
                    + format(result.getNewsSourceCredibilityScore()) + ","
                    + format(result.getNewsFreshnessScore()) + "," + result.getNewsSourceCount() + ","
                    + csv(result.getThemeReferenceTheme()) + "," + format(result.getThemeReferenceScore()) + ","
                    + csv(result.getMarketThemeReferenceTheme()) + ","
                    + format(result.getMarketThemeReferenceScore()) + "," + csv(result.getNewsSummary()) + ","
                    + csv(result.getNewsDigest()) + "," + csv(result.getThemeReferenceReason()) + ","
                    + csv(result.getMarketThemeReferenceReason()));
        }
        writer.close();
    }

    public void printLikelyCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> likelyCandidates = filterLikelyCandidates(results);
        List<StockAnalysisResultVO> watchlistCandidates = filterWatchlistCandidates(results);

        System.out.println("");
        if (likelyCandidates.isEmpty()) {
            System.out.println("比較有可能的股票: 目前沒有同時符合分數、財報品質、量比與流動性條件的標的");
            System.out.println("改看完整排行與觀察名單，挑接近門檻的股票持續追蹤。");
        } else {
            printCandidateSection("比較有可能的股票 (Likely 條件)", likelyCandidates,
                    DEFAULT_TOP_COUNT);
        }

        System.out.println("");
        if (watchlistCandidates.isEmpty()) {
            System.out.println("觀察名單: 目前沒有分數介於 " + format(WATCHLIST_THRESHOLD) + " 到 "
                    + format(LIKELY_THRESHOLD) + " 的標的");
        } else {
            printCandidateSection("觀察名單 (接近 Likely，但尚未達標)", watchlistCandidates, DEFAULT_TOP_COUNT);
        }
    }

    public void printPostCloseCandidates(List<StockAnalysisResultVO> results) {
        System.out.println("");
        printCandidateSection("收盤後高勝率候選", filterByPostCloseCategory(results, POST_CLOSE_HIGH_CONVICTION),
                DEFAULT_TOP_COUNT);
        System.out.println("");
        printCandidateSection("收盤後短線主攻", filterByPostCloseCategory(results, POST_CLOSE_MOMENTUM_ATTACK),
                DEFAULT_TOP_COUNT);
        System.out.println("");
        printCandidateSection("收盤後波段布局", filterByPostCloseCategory(results, POST_CLOSE_SWING_POSITION),
                DEFAULT_TOP_COUNT);
        System.out.println("");
        printCandidateSection("收盤後催化觀察", filterByPostCloseCategory(results, POST_CLOSE_CATALYST_WATCH),
                DEFAULT_TOP_COUNT);
    }

    public void printTopCandidates(List<StockAnalysisResultVO> results) {
        System.out.println("");
        printCandidateSection("完整排行 Top " + Math.min(DEFAULT_TOP_COUNT, results.size()), results, DEFAULT_TOP_COUNT);
    }

    public void printMarketDistribution(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> likelyCandidates = filterLikelyCandidates(results);
        int totalTwse = countByMarket(results, "TWSE");
        int totalTpex = countByMarket(results, "TPEX");
        int likelyTwse = countByMarket(likelyCandidates, "TWSE");
        int likelyTpex = countByMarket(likelyCandidates, "TPEX");

        System.out.println("");
        System.out.println("市場分布");
        System.out.println(String.format("全部股票: TWSE %d (%.1f%%) / TPEX %d (%.1f%%)", Integer.valueOf(totalTwse),
                Double.valueOf(percent(totalTwse, results.size())), Integer.valueOf(totalTpex),
                Double.valueOf(percent(totalTpex, results.size()))));
        System.out.println(String.format("比較有可能: TWSE %d (%.1f%%) / TPEX %d (%.1f%%)",
                Integer.valueOf(likelyTwse), Double.valueOf(percent(likelyTwse, likelyCandidates.size())),
                Integer.valueOf(likelyTpex), Double.valueOf(percent(likelyTpex, likelyCandidates.size()))));
        System.out.println("TPEX 比例偏高通常不是抓錯資料，而是目前評分偏重成長率、籌碼比例和趨勢，小型成長股較容易高分。");
    }

    public String writeHistorySnapshot(List<StockAnalysisResultVO> results) throws Exception {
        File historyDirectory = ensureHistoryDirectory();
        String fileName = new File(historyDirectory, "stock_candidates_" + currentDateStamp() + ".csv")
                .getPath();
        writeCsv(results, fileName);
        return new File(fileName).getAbsolutePath();
    }

    public String writeHistoryDatabase(List<StockAnalysisResultVO> results) throws Exception {
        return historyDatabase.upsertSnapshot(currentDateStamp(), results);
    }

    public String currentDateStamp() {
        return LocalDate.now(TAIPEI_ZONE).format(DATE_STAMP_FORMATTER);
    }

    public String buildDatedFileName(String prefix) {
        File snapshotDirectory = ensureDailySnapshotDirectory();
        return new File(snapshotDirectory, prefix + "_" + currentDateStamp() + ".csv").getPath();
    }

    public String buildDatedHtmlFileName(String prefix) {
        return prefix + "_" + currentDateStamp() + ".html";
    }

    public List<StockAnalysisResultVO> getLikelyCandidates(List<StockAnalysisResultVO> results) {
        return filterLikelyCandidates(results);
    }

    public List<StockAnalysisResultVO> getWatchlistCandidates(List<StockAnalysisResultVO> results) {
        return filterWatchlistCandidates(results);
    }

    public List<StockAnalysisResultVO> getLikelyVolumeSurgeCandidates(List<StockAnalysisResultVO> results) {
        return filterLikelyVolumeSurge(results);
    }

    public List<StockAnalysisResultVO> getNonLikelyVolumeSurgeCandidates(List<StockAnalysisResultVO> results) {
        return filterNonLikelyVolumeSurge(results);
    }

    public List<StockAnalysisResultVO> getHighConvictionCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseCategory(results, POST_CLOSE_HIGH_CONVICTION);
    }

    public List<StockAnalysisResultVO> getMomentumAttackCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseCategory(results, POST_CLOSE_MOMENTUM_ATTACK);
    }

    public List<StockAnalysisResultVO> getSwingPositionCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseCategory(results, POST_CLOSE_SWING_POSITION);
    }

    public List<StockAnalysisResultVO> getCatalystWatchCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseCategory(results, POST_CLOSE_CATALYST_WATCH);
    }

    public double getLikelyThreshold() {
        return activeLikelyThreshold();
    }

    public double getWatchlistThreshold() {
        return WATCHLIST_THRESHOLD;
    }

    public double getVolumeSurgeThreshold() {
        return VOLUME_SURGE_RATIO_THRESHOLD;
    }

    public String getLastNewsOnlyReferenceDate() {
        return lastNewsOnlyReferenceDate;
    }

    public String writePerformanceReport(List<StockAnalysisResultVO> currentResults) throws Exception {
        Map<String, StockHistoryDatabase.Snapshot> snapshotsByDate = historyDatabase.loadSnapshots();
        if (snapshotsByDate.size() < 2) {
            return "";
        }

        List<String> dates = new ArrayList<String>(snapshotsByDate.keySet());
        Collections.sort(dates);
        String currentDate = currentDateStamp();
        int currentIndex = dates.indexOf(currentDate);
        if (currentIndex < 0) {
            currentIndex = dates.size() - 1;
        }
        if (currentIndex <= 0) {
            return "";
        }
        String previousDate = dates.get(currentIndex - 1);
        StockHistoryDatabase.Snapshot previousSnapshot = snapshotsByDate.get(previousDate);
        if (previousSnapshot == null || previousSnapshot.rows.isEmpty()) {
            return "";
        }

        Map<String, StockAnalysisResultVO> currentByCode = new HashMap<String, StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : currentResults) {
            currentByCode.put(result.getStock().getCode(), result);
        }

        File historyDirectory = ensureHistoryDirectory();
        String reportPath = new File(historyDirectory, "performance_since_last_snapshot.csv").getPath();
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reportPath), "UTF-8")));
        writer.write('\uFEFF');
        writer.println("snapshot_file,code,name,market,snapshot_score,snapshot_price,current_price,return_pct,was_likely_candidate");

        for (StockHistoryDatabase.SnapshotRow snapshotRow : previousSnapshot.rows) {
            StockAnalysisResultVO current = currentByCode.get(snapshotRow.code);
            if (current == null || snapshotRow.price <= 0D) {
                continue;
            }
            double returnPct = (current.getCurrentPrice() - snapshotRow.price) * 100D / snapshotRow.price;
            writer.println(csv("stock_candidates_" + previousDate + ".csv") + "," + csv(snapshotRow.code) + ","
                    + csv(snapshotRow.name) + "," + csv(snapshotRow.market) + ","
                    + format(snapshotRow.score) + "," + format(snapshotRow.price) + ","
                    + format(current.getCurrentPrice()) + "," + format(returnPct) + ","
                    + csv(snapshotRow.likely ? "Y" : "N"));
        }
        writer.close();
        return new File(reportPath).getAbsolutePath();
    }

    public String resolveOutputPath(String fileName) {
        return new File(fileName).getAbsolutePath();
    }

    private Map<String, StockHistoryDatabase.Snapshot> loadHistoricalSnapshotsSafely() {
        try {
            return historyDatabase.loadSnapshots();
        } catch (Exception ex) {
            System.out.println("History snapshot context unavailable: " + ex.getMessage());
            return new HashMap<String, StockHistoryDatabase.Snapshot>();
        }
    }

    private Map<Integer, Map<String, BacktestSummaryRow>> loadBacktestSummarySafely() {
        Map<Integer, Map<String, BacktestSummaryRow>> summaryByHorizon = new HashMap<Integer, Map<String, BacktestSummaryRow>>();
        File summaryFile = new File("history", "backtest_summary.csv");
        if (!summaryFile.exists() || summaryFile.length() == 0L) {
            return summaryByHorizon;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(summaryFile), "UTF-8"));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return summaryByHorizon;
            }
            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> indexes = buildHeaderIndexes(headers);
            String line = null;
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.isEmpty()) {
                    continue;
                }
                int horizon = (int) NumberParser.parseDouble(valueAt(fields, indexes, "horizon_days"));
                String cohort = valueAt(fields, indexes, "cohort");
                if (horizon <= 0 || cohort.length() == 0) {
                    continue;
                }
                BacktestSummaryRow row = new BacktestSummaryRow();
                row.horizonDays = horizon;
                row.cohort = cohort;
                row.sampleCount = (int) NumberParser.parseDouble(valueAt(fields, indexes, "sample_count"));
                row.netWinRatePct = NumberParser.parseDouble(valueAt(fields, indexes, "net_win_rate_pct"));
                row.avgNetReturnPct = NumberParser.parseDouble(valueAt(fields, indexes, "avg_net_return_pct"));
                row.avgMaxDrawdownClosePct = NumberParser
                        .parseDouble(valueAt(fields, indexes, "avg_max_drawdown_close_pct"));
                row.avgHoldingDays = NumberParser.parseDouble(valueAt(fields, indexes, "avg_holding_days"));
                Map<String, BacktestSummaryRow> rows = summaryByHorizon.get(Integer.valueOf(horizon));
                if (rows == null) {
                    rows = new HashMap<String, BacktestSummaryRow>();
                    summaryByHorizon.put(Integer.valueOf(horizon), rows);
                }
                rows.put(cohort, row);
            }
        } catch (Exception ex) {
            System.out.println("Backtest summary unavailable: " + ex.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                }
            }
        }
        return summaryByHorizon;
    }

    private void applyBacktestCalibration(StockAnalysisResultVO result,
            Map<Integer, Map<String, BacktestSummaryRow>> summaryByHorizon) {
        BacktestSummaryRow summary = selectBacktestSummary(result, summaryByHorizon);
        if (summary == null) {
            result.setBacktestCohort("N/A");
            result.setWinratePriorityScore(0D);
            result.setExpectedReturnScore(0D);
            result.setMaxDrawdownPenalty(0D);
            return;
        }
        result.setBacktestCohort(summary.cohort);
        result.setWinratePriorityScore(scoreBacktestWinrate(summary.netWinRatePct, summary.sampleCount));
        result.setExpectedReturnScore(scoreBacktestReturn(summary.avgNetReturnPct, summary.sampleCount));
        result.setMaxDrawdownPenalty(scoreBacktestDrawdown(summary.avgMaxDrawdownClosePct));
        double calibrated = result.getPostClosePriorityScore() * 0.72D
                + result.getWinratePriorityScore() * 0.18D
                + result.getExpectedReturnScore() * 0.10D
                - result.getMaxDrawdownPenalty();
        if (result.isHardExclude()) {
            calibrated -= 6D;
        }
        result.setPostClosePriorityScore(NumberParser.clamp(calibrated, 0D, 100D));
    }

    private BacktestSummaryRow selectBacktestSummary(StockAnalysisResultVO result,
            Map<Integer, Map<String, BacktestSummaryRow>> summaryByHorizon) {
        int horizon = resolveBacktestHorizon(result);
        Map<String, BacktestSummaryRow> rows = summaryByHorizon.get(Integer.valueOf(horizon));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<String> candidates = new ArrayList<String>();
        if (POST_CLOSE_HIGH_CONVICTION.equals(result.getPostCloseCategory())) {
            candidates.add("WINRATE_FOCUS");
            candidates.add("QUALITY_70");
            candidates.add("LIKELY");
            candidates.add("BUYPOINT_A");
        } else if (POST_CLOSE_MOMENTUM_ATTACK.equals(result.getPostCloseCategory())) {
            candidates.add(result.getBuyPointScore() >= 85D ? "BUYPOINT_A" : "BUYPOINT_75");
            candidates.add("LIKELY");
            candidates.add("MOMENTUM_70");
        } else if (POST_CLOSE_SWING_POSITION.equals(result.getPostCloseCategory())) {
            candidates.add("QUALITY_70");
            candidates.add("QUALIFIED");
            candidates.add("LIKELY");
        } else if (POST_CLOSE_CATALYST_WATCH.equals(result.getPostCloseCategory())) {
            candidates.add("WATCHLIST");
            candidates.add("BUYPOINT_75");
            candidates.add("QUALIFIED");
        } else if (POST_CLOSE_GENERAL_WATCH.equals(result.getPostCloseCategory())) {
            candidates.add("WATCHLIST");
            candidates.add("QUALIFIED");
        } else {
            candidates.add("ALL");
        }
        candidates.add("ALL");

        BacktestSummaryRow bestFallback = null;
        for (String cohort : candidates) {
            BacktestSummaryRow row = rows.get(cohort);
            if (row == null) {
                continue;
            }
            if (row.sampleCount >= MIN_BACKTEST_SAMPLE_COUNT) {
                return row;
            }
            if (bestFallback == null || row.sampleCount > bestFallback.sampleCount) {
                bestFallback = row;
            }
        }
        return bestFallback;
    }

    private int resolveBacktestHorizon(StockAnalysisResultVO result) {
        int horizon = result.getSignalHorizonDays();
        if (horizon <= 1) {
            return 1;
        }
        if (horizon <= 3) {
            return 3;
        }
        if (horizon <= 5) {
            return 5;
        }
        return 10;
    }

    private double scoreBacktestWinrate(double netWinRatePct, int sampleCount) {
        double confidenceBoost = sampleCount >= 500 ? 4D : sampleCount >= MIN_BACKTEST_SAMPLE_COUNT ? 2D : 0D;
        return NumberParser.clamp((netWinRatePct - 25D) * 1.8D + 40D + confidenceBoost, 0D, 100D);
    }

    private double scoreBacktestReturn(double avgNetReturnPct, int sampleCount) {
        double sampleBoost = sampleCount >= 500 ? 2D : 0D;
        return NumberParser.clamp(50D + avgNetReturnPct * 12D + sampleBoost, 0D, 100D);
    }

    private double scoreBacktestDrawdown(double avgMaxDrawdownClosePct) {
        return NumberParser.clamp(Math.abs(Math.min(avgMaxDrawdownClosePct, 0D)) * 1.5D, 0D, 18D);
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(stripBom(current.toString()));
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(stripBom(current.toString()));
        return fields;
    }

    private Map<String, Integer> buildHeaderIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<String, Integer>();
        for (int i = 0; i < headers.size(); i++) {
            indexes.put(headers.get(i), Integer.valueOf(i));
        }
        return indexes;
    }

    private String valueAt(List<String> fields, Map<String, Integer> indexes, String header) {
        Integer index = indexes.get(header);
        if (index == null) {
            return "";
        }
        int position = index.intValue();
        if (position < 0 || position >= fields.size()) {
            return "";
        }
        return fields.get(position);
    }

    private String stripBom(String value) {
        if (value != null && value.length() > 0 && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value == null ? "" : value;
    }

    private void finalizeCompositeScores(List<StockAnalysisResultVO> results,
            Map<String, StockHistoryDatabase.Snapshot> historicalSnapshots) {
        if (results == null || results.isEmpty()) {
            return;
        }

        Map<String, List<StockHistoryDatabase.SnapshotRow>> recentHistoryByCode = buildRecentHistoryByCode(historicalSnapshots,
                6);
        IndustryMetricsSnapshot industryMetrics = IndustryMetricsSnapshot.build(results);
        MarketRegime resolvedRegime = MarketRegimeResolver.resolve(results, historicalSnapshots, currentDateStamp(),
                WATCHLIST_THRESHOLD, activeLikelyThreshold());
        activeMarketRegime = resolvedRegime;
        activeScoringStrategy = new ConfiguredScoringStrategy(scoringConfig.getQualification(),
                scoringConfig.getProfile(resolvedRegime));

        for (StockAnalysisResultVO result : results) {
            applyIndustryRelativeScoring(result, industryMetrics);
            boolean selectionQualified = isSelectionQualified(result.getLiquidityScore(),
                    result.getFinancialQualityScore(), result.getVolumeRatio(), result.getDataConfidence());
            result.setSelectionQualified(selectionQualified);
            result.setMarketRegime(resolvedRegime.getLabel());
        }

        Map<String, TrendProfile> trendProfiles = new HashMap<String, TrendProfile>();
        for (StockAnalysisResultVO result : results) {
            TrendProfile profile = evaluateTrendProfile(result, recentHistoryByCode.get(result.getStock().getCode()));
            trendProfiles.put(result.getStock().getCode(), profile);
            result.setTrendPersistenceScore(profile.score);
            result.setTrendPersistenceDays(profile.days);
        }

        Map<String, Double> sectorScores = buildSectorScores(results);
        Map<String, RelativeStrengthAggregate> relativeStrengthByIndustry = buildRelativeStrengthAggregates(results);
        Map<Integer, Map<String, BacktestSummaryRow>> backtestSummaryByHorizon = loadBacktestSummarySafely();

        for (StockAnalysisResultVO result : results) {
            double sectorScore = sectorScores.getOrDefault(normalizeIndustryKey(result.getIndustry()), 50D);
            result.setSectorScore(sectorScore);
            applyRelativeStrengthScores(result, relativeStrengthByIndustry.get(normalizeIndustryKey(result.getIndustry())));

            double baseSelectionScore = scoreSelectionProfile(result.getRawScore(), result.getQualityScore(),
                    result.getMomentumScore(), result.getVolumeRatio(), result.getEventRiskPenalty(),
                    result.isSelectionQualified());
            double finalSelectionScore = scoreSelectionComposite(baseSelectionScore, result.getTrendPersistenceScore(),
                    sectorScore, result.getNewsRiskScore());
            result.setSelectionScore(finalSelectionScore);

            StructureProfile structureProfile = buildStructureProfile(result.getCurrentPrice(), result.getMovingAverage20(),
                    result.getMovingAverage60(), result.getMovingAverage120(), result.getVolumeRatio(),
                    result.getDrawdownFromHigh60Pct(), result.getReturn20DayPct(), result.getRsi14(),
                    result.getStochasticK(), result.getStochasticD());
            RiskRewardProfile riskRewardProfile = buildRiskRewardProfile(result.getCurrentPrice(),
                    result.getMovingAverage20(), result.getMovingAverage60(), result.getMovingAverage120(),
                    result.getDrawdownFromHigh60Pct(), result.getVolatility20Pct(), result.getAtr20(),
                    structureProfile.score, finalSelectionScore);

            double baseBuyPointScore = scoreBuyPointProfile(finalSelectionScore, result.getMomentumScore(),
                    result.getQualityScore(), result.getCurrentPrice(), result.getMovingAverage20(),
                    result.getMovingAverage60(), result.getMovingAverage120(), result.getReturn20DayPct(),
                    result.getVolumeRatio(), result.getDrawdownFromHigh60Pct(), result.getRsi14(),
                    result.getStochasticK(), result.getStochasticD(), result.getEventRiskPenalty(),
                    result.isSelectionQualified(), result.getFinancialQualityScore());
            double finalBuyPointScore = scoreBuyPointComposite(baseBuyPointScore, structureProfile.score,
                    result.getTrendPersistenceScore(), riskRewardProfile.score, sectorScore, result.getNewsScore(),
                    result.getNewsRiskScore());

            result.setStructureScore(structureProfile.score);
            result.setStructureLabel(structureProfile.label);
            result.setRiskRewardScore(riskRewardProfile.score);
            result.setRiskRewardRatio(riskRewardProfile.riskRewardRatio);
            result.setSuggestedStopPrice(riskRewardProfile.stopLossPrice);
            result.setSuggestedStopPct(riskRewardProfile.stopLossPct);
            result.setSuggestedTrailingStopPrice(riskRewardProfile.trailingStopPrice);
            result.setSuggestedTargetPrice(riskRewardProfile.targetPrice);
            result.setUpsidePotentialPct(riskRewardProfile.upsidePotentialPct);
            result.setSellSignalScore(riskRewardProfile.sellSignalScore);
            result.setSellSignalLabel(riskRewardProfile.sellSignalLabel);
            result.setReducePositionSize(riskRewardProfile.reducePositionSize);
            result.setBuyPointScore(finalBuyPointScore);
            result.setBuyPointLabel(resolveBuyPointLabel(finalBuyPointScore, result.isSelectionQualified(),
                    result.getFinancialQualityScore(), result.getDataConfidence(), structureProfile.score,
                    riskRewardProfile.score, finalSelectionScore));
            result.setBuyPointReason(buildBuyPointReason(result, finalBuyPointScore, structureProfile,
                    riskRewardProfile));
            applyPostCloseDecisionProfile(result);
            applyBacktestCalibration(result, backtestSummaryByHorizon);
            result.setAnalysisNote(buildAnalysisNote(result));
            result.setScoreReason(buildScoreReason(result));
            applyNarrativeSummary(result);
        }
    }

    private Map<String, List<StockHistoryDatabase.SnapshotRow>> buildRecentHistoryByCode(
            Map<String, StockHistoryDatabase.Snapshot> historicalSnapshots, int lookbackSnapshots) {
        Map<String, List<StockHistoryDatabase.SnapshotRow>> historyByCode = new HashMap<String, List<StockHistoryDatabase.SnapshotRow>>();
        if (historicalSnapshots == null || historicalSnapshots.isEmpty()) {
            return historyByCode;
        }

        List<String> dates = new ArrayList<String>(historicalSnapshots.keySet());
        Collections.sort(dates);
        String currentDate = currentDateStamp();
        List<String> filteredDates = new ArrayList<String>();
        for (String date : dates) {
            if (!currentDate.equals(date)) {
                filteredDates.add(date);
            }
        }

        int startIndex = Math.max(0, filteredDates.size() - lookbackSnapshots);
        for (int i = startIndex; i < filteredDates.size(); i++) {
            StockHistoryDatabase.Snapshot snapshot = historicalSnapshots.get(filteredDates.get(i));
            if (snapshot == null) {
                continue;
            }
            for (StockHistoryDatabase.SnapshotRow row : snapshot.rows) {
                List<StockHistoryDatabase.SnapshotRow> series = historyByCode.get(row.code);
                if (series == null) {
                    series = new ArrayList<StockHistoryDatabase.SnapshotRow>();
                    historyByCode.put(row.code, series);
                }
                series.add(row);
            }
        }
        return historyByCode;
    }

    private TrendProfile evaluateTrendProfile(StockAnalysisResultVO result,
            List<StockHistoryDatabase.SnapshotRow> historyRows) {
        double currentSelectionScore = scoreSelectionProfile(result.getRawScore(), result.getQualityScore(),
                result.getMomentumScore(), result.getVolumeRatio(), result.getEventRiskPenalty(),
                result.isSelectionQualified());
        if (historyRows == null) {
            historyRows = new ArrayList<StockHistoryDatabase.SnapshotRow>();
        }

        int consecutiveDays = currentSelectionScore >= WATCHLIST_THRESHOLD ? 1 : 0;
        for (int i = historyRows.size() - 1; i >= 0 && consecutiveDays > 0; i--) {
            double previousSelectionScore = selectionScoreOf(historyRows.get(i));
            if (previousSelectionScore >= WATCHLIST_THRESHOLD) {
                consecutiveDays++;
            } else {
                break;
            }
        }

        double previousScore = historyRows.isEmpty() ? 0D : selectionScoreOf(historyRows.get(historyRows.size() - 1));
        int recentCount = Math.min(3, historyRows.size());
        double recentAverage = 0D;
        for (int i = historyRows.size() - recentCount; i < historyRows.size(); i++) {
            if (i >= 0) {
                recentAverage += selectionScoreOf(historyRows.get(i));
            }
        }
        if (recentCount > 0) {
            recentAverage /= recentCount;
        }

        double likelyThreshold = activeLikelyThreshold();
        int strongDays = 0;
        for (int i = Math.max(0, historyRows.size() - 5); i < historyRows.size(); i++) {
            if (selectionScoreOf(historyRows.get(i)) >= likelyThreshold) {
                strongDays++;
            }
        }

        double score = currentSelectionScore >= likelyThreshold ? 28D
                : currentSelectionScore >= WATCHLIST_THRESHOLD ? 18D : 8D;
        if (consecutiveDays >= 5) {
            score += 28D;
        } else if (consecutiveDays >= 4) {
            score += 23D;
        } else if (consecutiveDays >= 3) {
            score += 18D;
        } else if (consecutiveDays >= 2) {
            score += 10D;
        } else if (consecutiveDays == 1 && currentSelectionScore >= likelyThreshold) {
            score += 4D;
        }

        if (previousScore > 0D) {
            double delta = currentSelectionScore - previousScore;
            if (delta >= 10D) {
                score += 16D;
            } else if (delta >= 5D) {
                score += 10D;
            } else if (delta >= 0D) {
                score += 6D;
            } else if (delta <= -10D) {
                score -= 14D;
            } else if (delta <= -5D) {
                score -= 8D;
            }
        }

        if (recentAverage > 0D) {
            if (currentSelectionScore >= recentAverage + 5D) {
                score += 12D;
            } else if (currentSelectionScore >= recentAverage) {
                score += 8D;
            } else if (currentSelectionScore < recentAverage - 8D) {
                score -= 8D;
            }
        }

        if (strongDays >= 3) {
            score += 10D;
        } else if (strongDays >= 1) {
            score += 4D;
        } else if (historyRows.isEmpty() && currentSelectionScore >= activeLikelyThreshold()) {
            score += 6D;
        }

        if (!result.isSelectionQualified()) {
            score -= 10D;
        }

        return new TrendProfile(NumberParser.clamp(score, 0D, 100D), consecutiveDays);
    }

    private Map<String, Double> buildSectorScores(List<StockAnalysisResultVO> results) {
        Map<String, SectorAggregate> aggregates = new HashMap<String, SectorAggregate>();
        for (StockAnalysisResultVO result : results) {
            String industryKey = normalizeIndustryKey(result.getIndustry());
            SectorAggregate aggregate = aggregates.get(industryKey);
            if (aggregate == null) {
                aggregate = new SectorAggregate();
                aggregates.put(industryKey, aggregate);
            }
            double baseSelectionScore = scoreSelectionProfile(result.getRawScore(), result.getQualityScore(),
                    result.getMomentumScore(), result.getVolumeRatio(), result.getEventRiskPenalty(),
                    result.isSelectionQualified());
            aggregate.count++;
            aggregate.selectionScoreSum += baseSelectionScore;
            aggregate.qualityScoreSum += result.getQualityScore();
            aggregate.momentumScoreSum += result.getMomentumScore();
            if (result.isSelectionQualified()) {
                aggregate.qualifiedCount++;
            }
            if (baseSelectionScore >= WATCHLIST_THRESHOLD) {
                aggregate.breadthCount++;
            }
            if (baseSelectionScore >= activeLikelyThreshold()) {
                aggregate.strongCount++;
            }
        }

        Map<String, Double> sectorScores = new HashMap<String, Double>();
        for (Map.Entry<String, SectorAggregate> entry : aggregates.entrySet()) {
            SectorAggregate aggregate = entry.getValue();
            if (aggregate.count == 0) {
                sectorScores.put(entry.getKey(), Double.valueOf(50D));
                continue;
            }
            double averageSelectionScore = aggregate.selectionScoreSum / aggregate.count;
            double averageQualityScore = aggregate.qualityScoreSum / aggregate.count;
            double averageMomentumScore = aggregate.momentumScoreSum / aggregate.count;
            double breadthPct = aggregate.breadthCount * 100D / aggregate.count;
            double strongPct = aggregate.strongCount * 100D / aggregate.count;
            double qualifiedPct = aggregate.qualifiedCount * 100D / aggregate.count;
            double rawSectorScore = averageSelectionScore * 0.35D + averageQualityScore * 0.20D
                    + averageMomentumScore * 0.15D + breadthPct * 0.15D + strongPct * 0.10D
                    + qualifiedPct * 0.05D;
            double sampleWeight = Math.min(1D, aggregate.count / 6D);
            double adjustedSectorScore = 50D + (rawSectorScore - 50D) * sampleWeight;
            sectorScores.put(entry.getKey(), Double.valueOf(NumberParser.clamp(adjustedSectorScore, 0D, 100D)));
        }
        return sectorScores;
    }

    private Map<String, RelativeStrengthAggregate> buildRelativeStrengthAggregates(List<StockAnalysisResultVO> results) {
        Map<String, RelativeStrengthAggregate> aggregates = new HashMap<String, RelativeStrengthAggregate>();
        for (StockAnalysisResultVO result : results) {
            String industryKey = normalizeIndustryKey(result.getIndustry());
            RelativeStrengthAggregate aggregate = aggregates.get(industryKey);
            if (aggregate == null) {
                aggregate = new RelativeStrengthAggregate();
                aggregates.put(industryKey, aggregate);
            }
            aggregate.count++;
            aggregate.return20DayPctSum += result.getReturn20DayPct();
            aggregate.volumeRatioSum += result.getVolumeRatio();
            aggregate.flowRatioSum += result.getFiveDayInstitutionalNetRatioPct();
        }

        for (RelativeStrengthAggregate aggregate : aggregates.values()) {
            if (aggregate.count <= 0) {
                continue;
            }
            aggregate.return20DayPctAverage = aggregate.return20DayPctSum / aggregate.count;
            aggregate.volumeRatioAverage = aggregate.volumeRatioSum / aggregate.count;
            aggregate.flowRatioAverage = aggregate.flowRatioSum / aggregate.count;
        }
        return aggregates;
    }

    private void applyRelativeStrengthScores(StockAnalysisResultVO result, RelativeStrengthAggregate aggregate) {
        if (result == null || aggregate == null || aggregate.count <= 0) {
            result.setIndustryReturnStrength(50D);
            result.setIndustryVolumeStrength(50D);
            result.setIndustryFlowStrength(50D);
            result.setRelativeStrengthScore(50D);
            return;
        }
        double returnStrength = NumberParser.clamp(
                50D + (result.getReturn20DayPct() - aggregate.return20DayPctAverage) * 2.0D, 0D, 100D);
        double volumeStrength = NumberParser.clamp(
                50D + (result.getVolumeRatio() - aggregate.volumeRatioAverage) * 22D, 0D, 100D);
        double flowStrength = NumberParser.clamp(
                50D + (result.getFiveDayInstitutionalNetRatioPct() - aggregate.flowRatioAverage) * 3.0D, 0D, 100D);
        double relativeStrength = NumberParser.clamp(
                returnStrength * 0.45D + volumeStrength * 0.25D + flowStrength * 0.30D, 0D, 100D);
        result.setIndustryReturnStrength(returnStrength);
        result.setIndustryVolumeStrength(volumeStrength);
        result.setIndustryFlowStrength(flowStrength);
        result.setRelativeStrengthScore(relativeStrength);
    }

    private String normalizeIndustryKey(String industry) {
        String normalized = industry == null ? "" : industry.trim();
        if (normalized.length() == 0) {
            return "其他";
        }
        if (normalized.startsWith("櫃") && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private double selectionScoreOf(StockHistoryDatabase.SnapshotRow row) {
        return row.selectionScore > 0D ? row.selectionScore : row.score;
    }

    private List<StockAnalysisResultVO> filterByMinimumScore(List<StockAnalysisResultVO> results, double threshold) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (result.getSelectionScore() >= threshold && isLikelyCandidate(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterByScoreRange(List<StockAnalysisResultVO> results, double minInclusive,
            double maxExclusive) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (result.getSelectionScore() >= minInclusive && result.getSelectionScore() < maxExclusive
                    && isSelectionQualified(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterLikelyCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseSort(filterByMinimumScore(results, activeLikelyThreshold()));
    }

    private List<StockAnalysisResultVO> filterWatchlistCandidates(List<StockAnalysisResultVO> results) {
        return filterByPostCloseSort(filterByScoreRange(results, WATCHLIST_THRESHOLD, activeLikelyThreshold()));
    }

    private List<StockAnalysisResultVO> filterByPostCloseCategory(List<StockAnalysisResultVO> results, String category) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (category.equals(result.getPostCloseCategory())) {
                filtered.add(result);
            }
        }
        return filterByPostCloseSort(filtered);
    }

    private List<StockAnalysisResultVO> filterByPostCloseSort(List<StockAnalysisResultVO> results) {
        Collections.sort(results, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int priorityCompare = Double.compare(right.getPostClosePriorityScore(), left.getPostClosePriorityScore());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Double.compare(right.getSelectionScore(), left.getSelectionScore());
            }
        });
        lowFrequencyDataCache.save();
        return results;
    }

    public void setRunStage(String runStage) {
        if (runStage == null || runStage.trim().length() == 0) {
            this.runStage = "full";
            return;
        }
        this.runStage = runStage.trim().toLowerCase();
    }

    public void writeStageSnapshots(List<StockAnalysisResultVO> results) throws Exception {
        String currentDate = currentDateStamp();
        historyDatabase.upsertDailyStockRaw(currentDate, runStage, results);
        historyDatabase.upsertDailyStockAnalysis(currentDate, runStage, results);
    }

    public void markRunStatus(String status, int rowCount, String note) {
        markStageRunStatus(status, rowCount, note);
    }

    private Map<String, StockHistoryDatabase.SnapshotRow> loadSameDayCloseRawRows() {
        Map<String, StockHistoryDatabase.SnapshotRow> rowsByCode = new HashMap<String, StockHistoryDatabase.SnapshotRow>();
        if (!"full".equals(runStage)) {
            return rowsByCode;
        }
        try {
            StockHistoryDatabase.Snapshot snapshot = historyDatabase.loadDailyStockRaw(currentDateStamp(), "close");
            if (snapshot == null || snapshot.rows == null) {
                return rowsByCode;
            }
            for (StockHistoryDatabase.SnapshotRow row : snapshot.rows) {
                if (row != null && row.code != null && row.code.length() > 0) {
                    rowsByCode.put(row.code, row);
                }
            }
        } catch (Exception ex) {
            System.out.println("Cannot load same-day close raw cache: " + ex.getMessage());
        }
        return rowsByCode;
    }

    private void markStageRunStatus(String status, int rowCount, String note) {
        try {
            historyDatabase.upsertDailyRunStatus(currentDateStamp(), runStage, status, rowCount, note);
        } catch (Exception ex) {
            System.out.println("Cannot update run status: " + ex.getMessage());
        }
    }

    private void refreshMarketThemeRadarSafely(List<TaiwanStockVO> allStocks) {
        if (!Boolean.parseBoolean(System.getProperty("stock.marketThemeRadar.enabled", "true"))) {
            return;
        }
        try {
            MarketThemeRadar.Report report = marketThemeRadar.refresh(allStocks, currentDateStamp());
            themeBasketRepository.reload();
            historyDatabase.upsertDailyMarketData(currentDateStamp(), runStage, "marketThemeRadar", report.toJson());
            System.out.println("Market theme radar: " + report.articles.size() + " articles, "
                    + report.themes.size() + " auto themes.");
        } catch (Exception ex) {
            System.out.println("Market theme radar skipped: " + ex.getMessage());
        }
    }

    private void applyThemeBasketMetadata(StockAnalysisResultVO result, NewsSignalVO newsSignal) {
        if (result == null || result.getStock() == null) {
            return;
        }
        ThemeBasketRepository.ThemeMatch themeMatch = themeBasketRepository.match(result.getStock(),
                emptyIfBlank(result.getIndustry(), ""), newsSignal);
        if (themeMatch == null) {
            return;
        }
        if (themeMatch.primaryTheme.length() > 0 && !"一般".equals(themeMatch.primaryTheme)
                && themeMatch.themeScore >= result.getThemeScore()) {
            result.setPrimaryTheme(themeMatch.primaryTheme);
            result.setThemeScore(themeMatch.themeScore);
        }
        if (themeMatch.themeTags.length() > 0) {
            result.setThemeTags(themeMatch.themeTags);
        }
    }

    private List<StockAnalysisResultVO> filterLikelyVolumeSurge(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (isLikelyCandidate(result) && hasVolumeSurge(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterNonLikelyVolumeSurge(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (!isLikelyCandidate(result) && hasVolumeSurge(result) && isSelectionQualified(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private void printCandidateSection(String title, List<StockAnalysisResultVO> results, int limit) {
        int topCount = Math.min(limit, results.size());
        System.out.println(title + "，共 " + results.size() + " 檔");
        for (int i = 0; i < topCount; i++) {
            StockAnalysisResultVO result = results.get(i);
            System.out.println(String.format(
                    "%02d. %s %s %s selection=%.2f legacy=%.2f postClose=%.2f [%s/%s] (mom %.1f / qual %.1f / rev %.1f / chip %.1f / liq %.1f / val %.1f / tech %.1f / fin %.1f / risk -%.1f) | %s | %s",
                    Integer.valueOf(i + 1), result.getStock().getCode(), result.getStock().getName(),
                    result.getStock().getMarket(), Double.valueOf(result.getSelectionScore()),
                    Double.valueOf(result.getScore()), Double.valueOf(result.getPostClosePriorityScore()),
                    emptyIfBlank(result.getPostCloseCategory(), POST_CLOSE_GENERAL_WATCH),
                    emptyIfBlank(result.getPostCloseAction(), "觀察"),
                    Double.valueOf(result.getMomentumScore()), Double.valueOf(result.getQualityScore()),
                    Double.valueOf(result.getRevenueScore()), Double.valueOf(result.getChipsScore()),
                    Double.valueOf(result.getLiquidityScore()), Double.valueOf(result.getValuationScore()),
                    Double.valueOf(result.getTechnicalScore()), Double.valueOf(result.getFinancialQualityScore()),
                    Double.valueOf(result.getEventRiskPenalty()),
                    emptyIfBlank(result.getPostCloseReason(), result.getAnalysisNote()), result.getScoreReason()));
        }
    }

    private boolean isSelectionQualified(StockAnalysisResultVO result) {
        return isSelectionQualified(result.getLiquidityScore(), result.getFinancialQualityScore(),
                result.getVolumeRatio(), result.getDataConfidence());
    }

    private boolean isLikelyCandidate(StockAnalysisResultVO result) {
        return result.getSelectionScore() >= activeLikelyThreshold()
                && result.getFinancialQualityScore() >= activeLikelyMinFinancialScore()
                && isVolumeRangeHealthy(result.getVolumeRatio()) && isSelectionQualified(result);
    }

    private boolean hasVolumeSurge(StockAnalysisResultVO result) {
        return result.getVolumeRatio() >= VOLUME_SURGE_RATIO_THRESHOLD;
    }

    private File ensureHistoryDirectory() {
        File historyDirectory = new File("history");
        if (!historyDirectory.exists()) {
            historyDirectory.mkdirs();
        }
        return historyDirectory;
    }

    private File ensureDailySnapshotDirectory() {
        File snapshotDirectory = new File(DAILY_SNAPSHOT_DIRECTORY_NAME);
        if (!snapshotDirectory.exists()) {
            snapshotDirectory.mkdirs();
        }
        return snapshotDirectory;
    }

    private int countByMarket(List<StockAnalysisResultVO> results, String market) {
        int count = 0;
        for (StockAnalysisResultVO result : results) {
            if (market.equals(result.getStock().getMarket())) {
                count++;
            }
        }
        return count;
    }

    private double percent(int value, int total) {
        if (total <= 0) {
            return 0D;
        }
        return (value * 100D) / total;
    }

    private StockAnalysisResultVO analyzeOneStock(TaiwanStockVO stock) throws Exception {
        LowFrequencyDataCache.Entry cacheEntry = lowFrequencyDataCache.get(stock.getCode());
        StockHistoryDatabase.SnapshotRow stagedRawRow = sameDayCloseRawRowsByCode.get(stock.getCode());
        boolean reuseCloseRaw = "full".equals(runStage) && stagedRawRow != null;
        boolean intradayCloseStage = "intraday-close".equals(runStage);
        boolean closeStage = "close".equals(runStage) || intradayCloseStage;
        boolean deferChips = intradayCloseStage && parseBooleanProperty("stock.intraday.deferChips", true);
        boolean deferNews = closeStage && CLOSE_DEFER_NEWS;
        boolean deferEventRisk = closeStage && CLOSE_DEFER_EVENT_RISK;
        boolean hasRevenueCache = hasRevenueCache(cacheEntry);
        boolean hasFinancialCache = hasFinancialCache(cacheEntry);
        boolean hasProfileCache = hasProfileCache(cacheEntry);
        boolean refreshRevenue = shouldRefreshRevenue(cacheEntry);
        boolean refreshFinancial = shouldRefreshFinancial(cacheEntry);
        boolean refreshProfile = shouldRefreshProfile(cacheEntry, refreshFinancial);

        List<MonthlyRevenueVO> revenues = (refreshRevenue || !hasRevenueCache)
                ? fetchOptional("monthly revenue", stock, new FetchSupplier<List<MonthlyRevenueVO>>() {
                    public List<MonthlyRevenueVO> get() throws Exception {
                        return yahooService.fetchMonthlyRevenues(stock);
                    }
                }, new ArrayList<MonthlyRevenueVO>())
                : new ArrayList<MonthlyRevenueVO>();
        List<InstitutionalTradingDailyVO> institutionalDaily = (reuseCloseRaw || deferChips) ? new ArrayList<InstitutionalTradingDailyVO>()
                : yahooService.fetchInstitutionalTrading(stock);
        TechnicalSnapshotVO technical = reuseCloseRaw ? null : yahooService.fetchTechnicalSnapshot(stock);
        BrokerTradingSummaryVO brokerSummary = deferChips ? new BrokerTradingSummaryVO("", 0L, 0L, 0L, 0D) : reuseCloseRaw
                ? new BrokerTradingSummaryVO(currentDateStamp(), 0L, 0L, stagedRawRow.brokerNetLots,
                        stagedRawRow.brokerNetRatioPct)
                : fetchOptional("broker trading", stock, new FetchSupplier<BrokerTradingSummaryVO>() {
                    public BrokerTradingSummaryVO get() throws Exception {
                        return yahooService.fetchBrokerTradingSummary(stock);
                    }
                }, new BrokerTradingSummaryVO("", 0L, 0L, 0L, 0D));
        ProfileSnapshotVO profile = (refreshProfile || !hasProfileCache)
                ? fetchOptional("profile", stock, new FetchSupplier<ProfileSnapshotVO>() {
                    public ProfileSnapshotVO get() throws Exception {
                        return yahooService.fetchProfileSnapshot(stock);
                    }
                }, emptyProfileSnapshot())
                : profileFromCache(cacheEntry);
        List<EpsRecordVO> epsRecords = (refreshFinancial || !hasFinancialCache)
                ? fetchOptional("eps", stock, new FetchSupplier<List<EpsRecordVO>>() {
                    public List<EpsRecordVO> get() throws Exception {
                        return yahooService.fetchEpsRecords(stock);
                    }
                }, new ArrayList<EpsRecordVO>())
                : new ArrayList<EpsRecordVO>();
        List<CashFlowRecordVO> cashFlowRecords = (refreshFinancial || !hasFinancialCache)
                ? fetchOptional("cash flow", stock, new FetchSupplier<List<CashFlowRecordVO>>() {
                    public List<CashFlowRecordVO> get() throws Exception {
                        return yahooService.fetchCashFlowRecords(stock);
                    }
                }, new ArrayList<CashFlowRecordVO>())
                : new ArrayList<CashFlowRecordVO>();
        List<IncomeStatementRecordVO> incomeRecords = (refreshFinancial || !hasFinancialCache)
                ? fetchOptional("income statement", stock, new FetchSupplier<List<IncomeStatementRecordVO>>() {
                    public List<IncomeStatementRecordVO> get() throws Exception {
                        return yahooService.fetchIncomeStatementRecords(stock);
                    }
                }, new ArrayList<IncomeStatementRecordVO>())
                : new ArrayList<IncomeStatementRecordVO>();
        List<BalanceSheetRecordVO> balanceRecords = (refreshFinancial || !hasFinancialCache)
                ? fetchOptional("balance sheet", stock, new FetchSupplier<List<BalanceSheetRecordVO>>() {
                    public List<BalanceSheetRecordVO> get() throws Exception {
                        return yahooService.fetchBalanceSheetRecords(stock);
                    }
                }, new ArrayList<BalanceSheetRecordVO>())
                : new ArrayList<BalanceSheetRecordVO>();
        EventRiskVO eventRisk = deferEventRisk ? new EventRiskVO(0D, "盤後初版先略過事件風險，夜間完整版補齊")
                : fetchOptional("event risk", stock, new FetchSupplier<EventRiskVO>() {
                    public EventRiskVO get() throws Exception {
                        return yahooService.fetchEventRisk(stock, profile);
                    }
                }, new EventRiskVO(0D, "事件風險資料暫時不足"));
        NewsSignalVO newsSignal = deferNews ? new NewsSignalVO()
                : fetchOptional("news", stock, new FetchSupplier<NewsSignalVO>() {
                    public NewsSignalVO get() throws Exception {
                        return yahooService.fetchNewsSignal(stock);
                    }
                }, new NewsSignalVO());

        if ((revenues.isEmpty() && !hasRevenueCache) || (!reuseCloseRaw && !deferChips && institutionalDaily.isEmpty())) {
            throw new Exception("missing core Yahoo data");
        }

        StockAnalysisResultVO result = new StockAnalysisResultVO();
        result.setStock(stock);
        result.setRevenues(revenues);
        result.setInstitutionalDaily(institutionalDaily);
        result.setBrokerSummary(brokerSummary);

        fillMetrics(result, epsRecords, cashFlowRecords, technical, profile, incomeRecords, balanceRecords, eventRisk,
                newsSignal, cacheEntry, stagedRawRow);
        updateLowFrequencyCache(stock, cacheEntry, result, profile, revenues, epsRecords, incomeRecords, balanceRecords,
                cashFlowRecords);
        return result;
    }

    private void fillMetrics(StockAnalysisResultVO result, List<EpsRecordVO> epsRecords,
            List<CashFlowRecordVO> cashFlowRecords, TechnicalSnapshotVO technical, ProfileSnapshotVO profile,
            List<IncomeStatementRecordVO> incomeRecords, List<BalanceSheetRecordVO> balanceRecords, EventRiskVO eventRisk,
            NewsSignalVO newsSignal, LowFrequencyDataCache.Entry cacheEntry,
            StockHistoryDatabase.SnapshotRow stagedRawRow) {
        List<MonthlyRevenueVO> revenues = result.getRevenues();
        List<InstitutionalTradingDailyVO> institutional = result.getInstitutionalDaily();
        BrokerTradingSummaryVO broker = result.getBrokerSummary();
        boolean reusedSameDayCloseRaw = stagedRawRow != null;

        int revenueWindow = Math.min(3, revenues.size());
        int institutionWindow = Math.min(5, institutional.size());
        int epsWindow = Math.min(4, epsRecords.size());
        int cashFlowWindow = Math.min(4, cashFlowRecords.size());
        boolean hasRevenueData = !revenues.isEmpty() || hasRevenueCache(cacheEntry);
        boolean hasEpsData = !epsRecords.isEmpty() || hasFinancialCache(cacheEntry);
        boolean hasCashFlowData = !cashFlowRecords.isEmpty() || hasFinancialCache(cacheEntry);
        boolean hasIncomeData = !incomeRecords.isEmpty() || hasFinancialCache(cacheEntry);
        boolean hasBalanceData = !balanceRecords.isEmpty() || hasFinancialCache(cacheEntry);
        boolean hasBrokerData = broker.getDataDate() != null && broker.getDataDate().length() > 0;
        if (!hasBrokerData && reusedSameDayCloseRaw) {
            hasBrokerData = stagedRawRow.brokerReady || stagedRawRow.brokerNetLots != 0L
                    || stagedRawRow.brokerNetRatioPct != 0D;
        }
        boolean hasProfileData = profile.getCurrentPrice() > 0D || profile.getIndustry().length() > 0
                || profile.getPeerAveragePe() > 0D || profile.getGrossMarginPct() > 0D
                || profile.getOperatingMarginPct() > 0D || hasProfileCache(cacheEntry);
        boolean usedLowFrequencyCache = (revenues.isEmpty() && hasRevenueCache(cacheEntry))
                || (epsRecords.isEmpty() && hasFinancialCache(cacheEntry))
                || (cashFlowRecords.isEmpty() && hasFinancialCache(cacheEntry))
                || (incomeRecords.isEmpty() && hasFinancialCache(cacheEntry))
                || (balanceRecords.isEmpty() && hasFinancialCache(cacheEntry))
                || ((profile.getIndustry().length() == 0 && profile.getPeerAveragePe() <= 0D
                        && profile.getGrossMarginPct() == 0D && profile.getOperatingMarginPct() == 0D)
                        && hasProfileCache(cacheEntry));

        double latestRevenueYoY = !revenues.isEmpty() ? revenues.get(0).getYearOverYearPct()
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.latestRevenueYoY);
        double averageThreeMonthRevenueYoY = !revenues.isEmpty() ? averageRevenueYoY(revenues, revenueWindow)
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.averageThreeMonthRevenueYoY);
        double accumulatedRevenueYoY = !revenues.isEmpty() ? revenues.get(0).getAccumulatedYearOverYearPct()
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.accumulatedRevenueYoY);
        int positiveRevenueMonths = !revenues.isEmpty() ? countPositiveRevenueMonths(revenues, revenueWindow)
                : cacheEntry == null ? 0 : cacheEntry.positiveRevenueMonths;

        long latestInstitutionalNetLots = !institutional.isEmpty() ? institutional.get(0).getTotalNetLots()
                : reusedSameDayCloseRaw ? stagedRawRow.latestInstitutionalNetLots : 0L;
        double latestInstitutionalNetRatioPct = !institutional.isEmpty()
                ? NumberParser.ratioPercent(latestInstitutionalNetLots, institutional.get(0).getVolume())
                : reusedSameDayCloseRaw ? stagedRawRow.latestInstitutionalNetRatioPct : 0D;
        long fiveDayInstitutionalNetLots = !institutional.isEmpty() ? sumInstitutionalNet(institutional, institutionWindow)
                : reusedSameDayCloseRaw ? stagedRawRow.fiveDayInstitutionalNetLots : 0L;
        double fiveDayInstitutionalNetRatioPct = !institutional.isEmpty()
                ? NumberParser.ratioPercent(fiveDayInstitutionalNetLots, sumVolume(institutional, institutionWindow))
                : reusedSameDayCloseRaw ? stagedRawRow.fiveDayInstitutionalNetRatioPct : 0D;
        long latestForeignNetLots = !institutional.isEmpty() ? institutional.get(0).getForeignNetLots()
                : reusedSameDayCloseRaw ? stagedRawRow.latestForeignNetLots : 0L;
        long brokerNetLots = reusedSameDayCloseRaw ? stagedRawRow.brokerNetLots : broker.getNetLots();
        double brokerNetRatioPct = reusedSameDayCloseRaw ? stagedRawRow.brokerNetRatioPct : broker.getNetVolumeRatioPct();
        if (brokerNetLots < 0L && brokerNetRatioPct > 0D) {
            brokerNetRatioPct = -Math.abs(brokerNetRatioPct);
        } else if (brokerNetLots > 0L && brokerNetRatioPct < 0D) {
            brokerNetRatioPct = Math.abs(brokerNetRatioPct);
        }

        double currentPrice = reusedSameDayCloseRaw ? stagedRawRow.price
                : profile.getCurrentPrice() > 0D ? profile.getCurrentPrice()
                        : technical != null ? technical.getCurrentPrice() : 0D;
        double movingAverage18 = technical != null ? technical.getMovingAverage18()
                : reusedSameDayCloseRaw ? stagedRawRow.movingAverage18 : 0D;
        double movingAverage20 = technical != null ? technical.getMovingAverage20()
                : reusedSameDayCloseRaw ? stagedRawRow.movingAverage20 : 0D;
        double movingAverage54 = technical != null ? technical.getMovingAverage54()
                : reusedSameDayCloseRaw ? stagedRawRow.movingAverage54 : 0D;
        double movingAverage60 = technical != null ? technical.getMovingAverage60()
                : reusedSameDayCloseRaw ? stagedRawRow.movingAverage60 : 0D;
        double movingAverage120 = technical != null ? technical.getMovingAverage120()
                : reusedSameDayCloseRaw ? stagedRawRow.movingAverage120 : 0D;
        double return18DayPct = technical != null ? technical.getReturn18DayPct()
                : reusedSameDayCloseRaw ? stagedRawRow.return18DayPct : 0D;
        double return20DayPct = technical != null ? technical.getReturn20DayPct()
                : reusedSameDayCloseRaw ? stagedRawRow.return20DayPct : 0D;
        double return54DayPct = technical != null ? technical.getReturn54DayPct()
                : reusedSameDayCloseRaw ? stagedRawRow.return54DayPct : 0D;
        double return60DayPct = technical != null ? technical.getReturn60DayPct()
                : reusedSameDayCloseRaw ? stagedRawRow.return60DayPct : 0D;
        double volumeRatio = technical != null
                ? (technical.getAverageVolume20() <= 0D ? 0D : technical.getCurrentVolume() / technical.getAverageVolume20())
                : reusedSameDayCloseRaw ? stagedRawRow.volumeRatio : 0D;
        double averageLots20 = technical != null ? technical.getAverageLots20()
                : reusedSameDayCloseRaw ? stagedRawRow.averageLots20 : 0D;
        double averageTradeValue20Billion = technical != null ? technical.getAverageTradeValue20Billion()
                : reusedSameDayCloseRaw ? stagedRawRow.averageTradeValue20Billion : 0D;
        double volatility20Pct = technical != null ? technical.getVolatility20Pct()
                : reusedSameDayCloseRaw ? stagedRawRow.volatility20Pct : 0D;
        double atr20 = technical != null ? technical.getAtr20() : reusedSameDayCloseRaw ? stagedRawRow.atr20 : 0D;
        double drawdownFromHigh60Pct = technical != null ? technical.getDrawdownFromHigh60Pct()
                : reusedSameDayCloseRaw ? stagedRawRow.drawdownFromHigh60Pct : 0D;
        double ma20Slope = technical != null ? technical.getMa20Slope()
                : reusedSameDayCloseRaw ? stagedRawRow.ma20Slope : 0D;

        double trailingFourQuarterEps = !epsRecords.isEmpty() ? sumTrailingEps(epsRecords, epsWindow)
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.trailingFourQuarterEps);
        double trailingPe = trailingFourQuarterEps > 0D ? currentPrice / trailingFourQuarterEps : 0D;
        double latestQuarterEps = !epsRecords.isEmpty() ? epsRecords.get(0).getEps()
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.latestQuarterEps);
        double previousQuarterEps = epsRecords.size() >= 2 ? epsRecords.get(1).getEps()
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.previousQuarterEps);
        double latestQuarterEpsYoYPct = !epsRecords.isEmpty() ? epsRecords.get(0).getYearOverYearPct()
                : cacheNumber(cacheEntry == null ? 0D : cacheEntry.latestQuarterEpsYoYPct);
        int positiveEpsQuarters = !epsRecords.isEmpty() ? countPositiveEpsQuarters(epsRecords, epsWindow)
                : cacheEntry == null ? 0 : cacheEntry.positiveEpsQuarters;

        long latestOperatingCashFlow = !cashFlowRecords.isEmpty() ? cashFlowRecords.get(0).getOperatingCashFlow()
                : cacheEntry == null ? 0L : cacheEntry.latestOperatingCashFlow;
        long latestFreeCashFlow = !cashFlowRecords.isEmpty() ? cashFlowRecords.get(0).getFreeCashFlow()
                : cacheEntry == null ? 0L : cacheEntry.latestFreeCashFlow;
        long previousOperatingCashFlow = cashFlowRecords.size() >= 2 ? cashFlowRecords.get(1).getOperatingCashFlow()
                : cacheEntry == null ? 0L : cacheEntry.previousOperatingCashFlow;
        long previousFreeCashFlow = cashFlowRecords.size() >= 2 ? cashFlowRecords.get(1).getFreeCashFlow()
                : cacheEntry == null ? 0L : cacheEntry.previousFreeCashFlow;
        int positiveOperatingCashFlowQuarters = !cashFlowRecords.isEmpty()
                ? countPositiveOperatingCashFlowQuarters(cashFlowRecords, cashFlowWindow)
                : cacheEntry == null ? 0 : cacheEntry.positiveOperatingCashFlowQuarters;
        int positiveFreeCashFlowQuarters = !cashFlowRecords.isEmpty()
                ? countPositiveFreeCashFlowQuarters(cashFlowRecords, cashFlowWindow)
                : cacheEntry == null ? 0 : cacheEntry.positiveFreeCashFlowQuarters;

        long latestOperatingIncome = !incomeRecords.isEmpty() ? incomeRecords.get(0).getOperatingIncome()
                : cacheEntry == null ? 0L : cacheEntry.latestOperatingIncome;
        long latestNetIncome = !incomeRecords.isEmpty() ? incomeRecords.get(0).getNetIncome()
                : cacheEntry == null ? 0L : cacheEntry.latestNetIncome;
        long previousOperatingIncome = incomeRecords.size() >= 2 ? incomeRecords.get(1).getOperatingIncome() : 0L;
        if (incomeRecords.size() < 2 && cacheEntry != null) {
            previousOperatingIncome = cacheEntry.previousOperatingIncome;
        }
        long previousNetIncome = incomeRecords.size() >= 2 ? incomeRecords.get(1).getNetIncome()
                : cacheEntry == null ? 0L : cacheEntry.previousNetIncome;
        double nonOperatingRatioPct = !hasIncomeData || latestNetIncome == 0L ? cacheNumber(Double.NaN,
                cacheEntry == null ? Double.NaN : cacheEntry.nonOperatingRatioPct)
                : Math.abs((latestNetIncome - latestOperatingIncome) * 100D / latestNetIncome);

        long latestTotalAssets = !balanceRecords.isEmpty() ? balanceRecords.get(0).getTotalAssets() : 0L;
        long latestTotalLiabilities = !balanceRecords.isEmpty() ? balanceRecords.get(0).getTotalLiabilities() : 0L;
        long latestCurrentAssets = !balanceRecords.isEmpty() ? balanceRecords.get(0).getCurrentAssets() : 0L;
        long latestCurrentLiabilities = !balanceRecords.isEmpty() ? balanceRecords.get(0).getCurrentLiabilities() : 0L;
        double debtRatioPct = !hasBalanceData || latestTotalAssets <= 0L ? cacheNumber(Double.NaN,
                cacheEntry == null ? Double.NaN : cacheEntry.debtRatioPct)
                : NumberParser.ratioPercent(latestTotalLiabilities, latestTotalAssets);
        double currentRatio = !hasBalanceData || latestCurrentLiabilities == 0L ? cacheNumber(Double.NaN,
                cacheEntry == null ? Double.NaN : cacheEntry.currentRatio)
                : latestCurrentAssets * 1D / latestCurrentLiabilities;

        String industry = profile.getIndustry();
        double peerAveragePe = profile.getPeerAveragePe();
        double grossMarginPct = profile.getGrossMarginPct();
        double operatingMarginPct = profile.getOperatingMarginPct();
        double returnOnAssetsPct = profile.getReturnOnAssetsPct();
        double returnOnEquityPct = profile.getReturnOnEquityPct();

        double rsi14 = technical != null ? technical.getRsi14() : reusedSameDayCloseRaw ? stagedRawRow.rsi14 : 0D;
        double stochasticK = technical != null ? technical.getStochasticK()
                : reusedSameDayCloseRaw ? stagedRawRow.stochasticK : 0D;
        double stochasticD = technical != null ? technical.getStochasticD()
                : reusedSameDayCloseRaw ? stagedRawRow.stochasticD : 0D;
        ThemeBasketRepository.ThemeMatch themeMatch = themeBasketRepository.match(result.getStock(), industry,
                newsSignal);
        EventSignalProfile eventSignalProfile = inferEventSignalProfile(newsSignal, eventRisk);
        double newsScore = scoreNewsSignal(newsSignal);
        double newsRiskScore = scoreNewsRisk(newsSignal, eventRisk.getPenalty());
        if ("正向催化".equals(eventSignalProfile.direction)) {
            newsScore = NumberParser.clamp(newsScore + Math.min(8D, eventSignalProfile.confidence * 0.08D), 0D, 100D);
        } else if ("負向風險".equals(eventSignalProfile.direction)) {
            newsRiskScore = NumberParser.clamp(newsRiskScore + Math.min(10D, eventSignalProfile.confidence * 0.10D), 0D,
                    100D);
        }

        // EPS acceleration: latest quarter YoY% minus previous quarter YoY%
        double epsAccelerationPct = 0D;
        if (epsRecords.size() >= 2) {
            epsAccelerationPct = epsRecords.get(0).getYearOverYearPct() - epsRecords.get(1).getYearOverYearPct();
        }
        TurnaroundProfile turnaroundProfile = buildTurnaroundProfile(latestRevenueYoY, averageThreeMonthRevenueYoY,
                accumulatedRevenueYoY, positiveRevenueMonths, latestQuarterEps, previousQuarterEps,
                latestQuarterEpsYoYPct, positiveEpsQuarters, epsAccelerationPct, latestOperatingIncome,
                previousOperatingIncome, latestNetIncome, previousNetIncome, latestOperatingCashFlow,
                previousOperatingCashFlow, latestFreeCashFlow, previousFreeCashFlow, nonOperatingRatioPct);

        // PEG = trailing PE / latest quarter EPS YoY growth rate (only if growth > 0)
        double peg = 0D;
        if (trailingPe > 0D && latestQuarterEpsYoYPct > 0D) {
            peg = trailingPe / latestQuarterEpsYoYPct;
        }

        double revenueScore = scoreRevenue(latestRevenueYoY, averageThreeMonthRevenueYoY, accumulatedRevenueYoY,
                positiveRevenueMonths);
        double chipsScore = scoreChips(fiveDayInstitutionalNetRatioPct, latestInstitutionalNetRatioPct,
                latestForeignNetLots, brokerNetRatioPct, brokerNetLots);
        double liquidityScore = scoreLiquidity(averageTradeValue20Billion, averageLots20, profile.getMarketCapMillions());
        double valuationScore = scoreValuation(trailingPe, trailingFourQuarterEps, peerAveragePe, nonOperatingRatioPct,
                epsAccelerationPct, peg);
        double technicalScore = scoreTechnical(currentPrice, movingAverage20, movingAverage60, movingAverage120,
                return20DayPct, return60DayPct, volumeRatio, volatility20Pct, drawdownFromHigh60Pct, rsi14,
                stochasticK, stochasticD, ma20Slope);
        double financialQualityScore = scoreFinancialQuality(trailingFourQuarterEps, latestQuarterEps,
                latestQuarterEpsYoYPct, positiveEpsQuarters, latestOperatingCashFlow, latestFreeCashFlow,
                positiveOperatingCashFlowQuarters, positiveFreeCashFlowQuarters, grossMarginPct, operatingMarginPct,
                returnOnAssetsPct, returnOnEquityPct, debtRatioPct, currentRatio, nonOperatingRatioPct,
                epsAccelerationPct);
        double tripleConfirmBonus = calcTripleConfirmBonus(latestQuarterEpsYoYPct,
                latestInstitutionalNetRatioPct, technicalScore, ma20Slope, drawdownFromHigh60Pct, volumeRatio);
        double rawScore = NumberParser.clamp(
                revenueScore + chipsScore + liquidityScore + valuationScore + technicalScore + financialQualityScore
                        - eventRisk.getPenalty() + tripleConfirmBonus,
                0D, RAW_SCORE_MAX);
        double score = NumberParser.clamp(rawScore, 0D, 100D);
        double qualityScore = scoreQualityProfile(revenueScore, financialQualityScore, valuationScore, liquidityScore);
        double momentumScore = scoreMomentumProfile(chipsScore, technicalScore, volumeRatio, return20DayPct, rsi14,
                stochasticK, stochasticD);
        double dataConfidence = scoreDataConfidence(hasProfileData, hasEpsData, hasCashFlowData, hasIncomeData,
                hasBalanceData, hasBrokerData);
        boolean selectionQualified = isSelectionQualified(liquidityScore, financialQualityScore, volumeRatio,
                dataConfidence);
        double selectionScore = scoreSelectionProfile(rawScore, qualityScore, momentumScore, volumeRatio,
                eventRisk.getPenalty(), selectionQualified);
        StructureProfile structureProfile = buildStructureProfile(currentPrice, movingAverage20, movingAverage60,
                movingAverage120, volumeRatio, drawdownFromHigh60Pct, return20DayPct, rsi14, stochasticK,
                stochasticD);
        RiskRewardProfile riskRewardProfile = buildRiskRewardProfile(currentPrice, movingAverage20, movingAverage60,
                movingAverage120, drawdownFromHigh60Pct, volatility20Pct, atr20, structureProfile.score,
                selectionScore);
        double buyPointScore = scoreBuyPointComposite(scoreBuyPointProfile(selectionScore, momentumScore, qualityScore, currentPrice,
                movingAverage20, movingAverage60, movingAverage120, return20DayPct, volumeRatio, drawdownFromHigh60Pct,
                rsi14, stochasticK, stochasticD, eventRisk.getPenalty(), selectionQualified,
                financialQualityScore), structureProfile.score, 0D, riskRewardProfile.score, 50D, newsScore,
                newsRiskScore);
        String eligibilityReason = buildEligibilityReason(selectionQualified, liquidityScore, financialQualityScore,
                volumeRatio);
        String dataConfidenceReason = buildDataConfidenceReason(hasProfileData, hasEpsData, hasCashFlowData,
                hasIncomeData, hasBalanceData, hasBrokerData);

        result.setCurrentPrice(currentPrice);
        result.setIndustry(industry);
        result.setLatestRevenueYoY(latestRevenueYoY);
        result.setAverageThreeMonthRevenueYoY(averageThreeMonthRevenueYoY);
        result.setAccumulatedRevenueYoY(accumulatedRevenueYoY);
        result.setPositiveRevenueMonths(positiveRevenueMonths);
        result.setLatestInstitutionalNetLots(latestInstitutionalNetLots);
        result.setLatestInstitutionalNetRatioPct(latestInstitutionalNetRatioPct);
        result.setFiveDayInstitutionalNetLots(fiveDayInstitutionalNetLots);
        result.setFiveDayInstitutionalNetRatioPct(fiveDayInstitutionalNetRatioPct);
        result.setLatestForeignNetLots(latestForeignNetLots);
        result.setBrokerNetLots(brokerNetLots);
        result.setBrokerNetRatioPct(brokerNetRatioPct);
        result.setTrailingFourQuarterEps(trailingFourQuarterEps);
        result.setTrailingPe(trailingPe);
        result.setPeerAveragePe(peerAveragePe);
        result.setLatestQuarterEps(latestQuarterEps);
        result.setLatestQuarterEpsYoYPct(latestQuarterEpsYoYPct);
        result.setPositiveEpsQuarters(positiveEpsQuarters);
        result.setLatestOperatingCashFlow(latestOperatingCashFlow);
        result.setLatestFreeCashFlow(latestFreeCashFlow);
        result.setPositiveOperatingCashFlowQuarters(positiveOperatingCashFlowQuarters);
        result.setPositiveFreeCashFlowQuarters(positiveFreeCashFlowQuarters);
        result.setMovingAverage18(movingAverage18);
        result.setMovingAverage20(movingAverage20);
        result.setMovingAverage54(movingAverage54);
        result.setMovingAverage60(movingAverage60);
        result.setMovingAverage120(movingAverage120);
        result.setReturn18DayPct(return18DayPct);
        result.setReturn20DayPct(return20DayPct);
        result.setReturn54DayPct(return54DayPct);
        result.setReturn60DayPct(return60DayPct);
        result.setVolumeRatio(volumeRatio);
        result.setAverageLots20(averageLots20);
        result.setAverageTradeValue20Billion(averageTradeValue20Billion);
        result.setVolatility20Pct(volatility20Pct);
        result.setAtr20(atr20);
        result.setDrawdownFromHigh60Pct(drawdownFromHigh60Pct);
        result.setGrossMarginPct(grossMarginPct);
        result.setOperatingMarginPct(operatingMarginPct);
        result.setReturnOnAssetsPct(returnOnAssetsPct);
        result.setReturnOnEquityPct(returnOnEquityPct);
        result.setDebtRatioPct(debtRatioPct);
        result.setCurrentRatio(currentRatio);
        result.setNonOperatingRatioPct(nonOperatingRatioPct);
        result.setRsi14(rsi14);
        result.setStochasticK(stochasticK);
        result.setStochasticD(stochasticD);
        result.setMa20Slope(ma20Slope);
        result.setEpsAccelerationPct(epsAccelerationPct);
        result.setPeg(peg);
        result.setRevenueScore(revenueScore);
        result.setChipsScore(chipsScore);
        result.setLiquidityScore(liquidityScore);
        result.setValuationScore(valuationScore);
        result.setTechnicalScore(technicalScore);
        result.setFinancialQualityScore(financialQualityScore);
        result.setRawScore(rawScore);
        result.setSelectionScore(selectionScore);
        result.setMomentumScore(momentumScore);
        result.setQualityScore(qualityScore);
        result.setSectorScore(50D);
        result.setThemeScore(themeMatch.themeScore);
        result.setPrimaryTheme(themeMatch.primaryTheme);
        result.setThemeTags(themeMatch.themeTags);
        result.setTrendPersistenceScore(0D);
        result.setTrendPersistenceDays(0);
        result.setNewsScore(newsScore);
        result.setNewsRiskScore(newsRiskScore);
        applyNewsSignalMetadata(result, newsSignal);
        result.setEventDirection(eventSignalProfile.direction);
        result.setEventConfidence(eventSignalProfile.confidence);
        result.setEventFreshnessDays(eventSignalProfile.freshnessDays);
        result.setEventTypeSummary(eventSignalProfile.typeSummary);
        result.setStructureScore(structureProfile.score);
        result.setStructureLabel(structureProfile.label);
        result.setRiskRewardScore(riskRewardProfile.score);
        result.setRiskRewardRatio(riskRewardProfile.riskRewardRatio);
        result.setTurnaroundScore(turnaroundProfile.score);
        result.setRevenueGrowthSignalScore(turnaroundProfile.revenueGrowthScore);
        result.setEarningsTurnaroundSignalScore(turnaroundProfile.earningsTurnaroundScore);
        result.setProfitabilityTurnaroundSignalScore(turnaroundProfile.profitabilityTurnaroundScore);
        result.setOneOffRiskScore(turnaroundProfile.oneOffRiskScore);
        result.setSuggestedStopPrice(riskRewardProfile.stopLossPrice);
        result.setSuggestedStopPct(riskRewardProfile.stopLossPct);
        result.setSuggestedTrailingStopPrice(riskRewardProfile.trailingStopPrice);
        result.setSuggestedTargetPrice(riskRewardProfile.targetPrice);
        result.setUpsidePotentialPct(riskRewardProfile.upsidePotentialPct);
        result.setSellSignalScore(riskRewardProfile.sellSignalScore);
        result.setSellSignalLabel(riskRewardProfile.sellSignalLabel);
        result.setReducePositionSize(riskRewardProfile.reducePositionSize);
        result.setBuyPointScore(buyPointScore);
        result.setDataConfidence(dataConfidence);
        result.setSelectionQualified(selectionQualified);
        result.setMarketRegime(activeMarketRegime.getLabel());
        result.setEventRiskPenalty(eventRisk.getPenalty());
        result.setEligibilityReason(eligibilityReason);
        result.setBuyPointLabel(resolveBuyPointLabel(buyPointScore, selectionQualified, financialQualityScore,
                dataConfidence, structureProfile.score, riskRewardProfile.score, selectionScore));
        result.setBuyPointReason(buildBuyPointReason(result, buyPointScore, structureProfile, riskRewardProfile));
        result.setDataConfidenceReason(dataConfidenceReason);
        result.setTurnaroundLabel(turnaroundProfile.label);
        result.setTurnaroundReason(turnaroundProfile.reason);
        result.setSnapshotStage(runStage);
        result.setTechReady(technical != null || reusedSameDayCloseRaw);
        result.setMarketReady(true);
        result.setInstitutionalReady(!institutional.isEmpty() || reusedSameDayCloseRaw);
        result.setBrokerReady(hasBrokerData);
        result.setFinancialReady(hasRevenueData && hasProfileData && hasEpsData && hasCashFlowData && hasIncomeData
                && hasBalanceData);
        result.setNewsReady(newsSignal != null
                && (newsSignal.getHasNewsPage() || newsSignal.getSourceCount() > 0
                        || newsSignal.getSummaryText().length() > 0));
        String analysisVersion = usedLowFrequencyCache ? ANALYSIS_VERSION + "-cached" : ANALYSIS_VERSION + "-fresh";
        if (reusedSameDayCloseRaw) {
            analysisVersion += "-reused-close";
        }
        result.setAnalysisVersion(analysisVersion);
        String sourceUpdatedAt = usedLowFrequencyCache && cacheEntry != null
                && emptyIfBlank(cacheEntry.sourceUpdatedAt, "").length() > 0 ? cacheEntry.sourceUpdatedAt
                        : currentDateStamp();
        if (reusedSameDayCloseRaw && stagedRawRow != null
                && emptyIfBlank(stagedRawRow.sourceUpdatedAt, "").length() > 0) {
            sourceUpdatedAt = stagedRawRow.sourceUpdatedAt;
        }
        result.setSourceUpdatedAt(sourceUpdatedAt);
        result.setScore(score);
        applyPostCloseDecisionProfile(result);
        result.setAnalysisNote(buildAnalysisNote(result));
        result.setScoreReason(buildScoreReason(result));
        result.setRevenueReason(buildRevenueReason(result));
        result.setChipsReason(buildChipsReason(result));
        result.setLiquidityReason(buildLiquidityReason(result));
        result.setValuationReason(buildValuationReason(result));
        result.setTechnicalReason(buildTechnicalReason(result));
        result.setFinancialQualityReason(buildFinancialQualityReason(result));
        result.setEventRiskReason(eventRisk.getReason());
    }

    private void applyNewsSignalMetadata(StockAnalysisResultVO result, NewsSignalVO newsSignal) {
        if (result == null) {
            return;
        }
        if (newsSignal == null) {
            result.setNewsSummary("");
            result.setNewsDigest("");
            result.setNewsSourceSummary("");
            result.setLatestNewsPublishedHint("");
            result.setNewsSourceCredibilityScore(0D);
            result.setNewsFreshnessScore(0D);
            result.setNewsSourceCount(0);
            result.setNewsOfficialSourceCount(0);
            result.setNewsMediaSourceCount(0);
            result.setEventDirection("中性待確認");
            result.setEventConfidence(0D);
            result.setEventFreshnessDays(999);
            result.setEventTypeSummary("");
            return;
        }
        result.setNewsSummary(emptyIfBlank(newsSignal.getSummaryText(), ""));
        result.setNewsDigest(buildNewsDigest(newsSignal.getHeadlines(), 3));
        result.setNewsSourceSummary(emptyIfBlank(newsSignal.getSourceSummary(), ""));
        result.setLatestNewsPublishedHint(emptyIfBlank(newsSignal.getLatestPublishedHint(), ""));
        result.setNewsSourceCredibilityScore(newsSignal.getSourceCredibilityScore());
        result.setNewsFreshnessScore(newsSignal.getFreshnessScore());
        result.setNewsSourceCount(newsSignal.getSourceCount());
        result.setNewsOfficialSourceCount(newsSignal.getOfficialSourceCount());
        result.setNewsMediaSourceCount(newsSignal.getMediaSourceCount());
    }

    private void applyEventSignalMetadata(StockAnalysisResultVO result, NewsSignalVO newsSignal, EventRiskVO eventRisk) {
        if (result == null) {
            return;
        }
        EventSignalProfile profile = inferEventSignalProfile(newsSignal, eventRisk);
        result.setEventDirection(profile.direction);
        result.setEventConfidence(profile.confidence);
        result.setEventFreshnessDays(profile.freshnessDays);
        result.setEventTypeSummary(profile.typeSummary);
    }

    private EventSignalProfile inferEventSignalProfile(NewsSignalVO newsSignal, EventRiskVO eventRisk) {
        double positiveScore = 0D;
        double negativeScore = 0D;
        List<String> types = new ArrayList<String>();
        int freshnessDays = 999;
        if (newsSignal != null) {
            for (NewsSignalVO.NewsItem item : newsSignal.getItems()) {
                if (item == null) {
                    continue;
                }
                String title = safeUpper(item.getTitle());
                double weight = 1D + item.getSourceCredibilityScore() / 100D + item.getFreshnessScore() / 120D;
                boolean positive = containsAny(title, POSITIVE_EVENT_KEYWORDS);
                boolean negative = containsAny(title, NEGATIVE_EVENT_KEYWORDS);
                boolean neutral = containsAny(title, NEUTRAL_EVENT_KEYWORDS);
                if (positive) {
                    positiveScore += weight;
                    appendEventTypes(types, title);
                }
                if (negative) {
                    negativeScore += weight;
                    appendEventTypes(types, title);
                }
                if (!positive && !negative && neutral) {
                    appendEventTypes(types, title);
                }
                freshnessDays = Math.min(freshnessDays, estimateFreshnessDays(item.getFreshnessScore(), item.getPublishedHint()));
            }
        }
        String riskReason = eventRisk == null ? "" : safeUpper(eventRisk.getReason());
        if (riskReason.length() > 0 && containsAny(riskReason, NEGATIVE_EVENT_KEYWORDS)) {
            negativeScore += 1.2D + (eventRisk.getPenalty() * 0.6D);
            appendEventTypes(types, riskReason);
        }
        if (riskReason.length() > 0 && containsAny(riskReason, POSITIVE_EVENT_KEYWORDS)) {
            positiveScore += 0.8D;
            appendEventTypes(types, riskReason);
        }

        String direction = "中性待確認";
        double diff = positiveScore - negativeScore;
        if (diff >= 1.2D) {
            direction = "正向催化";
        } else if (diff <= -1.2D) {
            direction = "負向風險";
        }
        double confidence = NumberParser.clamp(40D + Math.abs(diff) * 18D
                + (newsSignal == null ? 0D : Math.min(12D, newsSignal.getOfficialSourceCount() * 6D))
                + (freshnessDays <= 2 ? 8D : freshnessDays <= 5 ? 4D : 0D), 0D, 100D);
        if (freshnessDays == 999) {
            freshnessDays = newsSignal == null || newsSignal.getFreshnessScore() <= 0D ? 999
                    : estimateFreshnessDays(newsSignal.getFreshnessScore(), newsSignal.getLatestPublishedHint());
        }
        return new EventSignalProfile(direction, confidence, freshnessDays,
                types.isEmpty() ? "" : join(unique(types), "；"));
    }

    private void appendEventTypes(List<String> types, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        addIfContains(types, text, "庫藏股", "庫藏股");
        addIfContains(types, text, "法說", "法說");
        addIfContains(types, text, "接單", "接單");
        addIfContains(types, text, "訂單", "訂單");
        addIfContains(types, text, "量產", "量產");
        addIfContains(types, text, "擴產", "擴產");
        addIfContains(types, text, "漲價", "漲價");
        addIfContains(types, text, "股利", "股利");
        addIfContains(types, text, "配息", "配息");
        addIfContains(types, text, "現增", "現增");
        addIfContains(types, text, "私募", "私募");
        addIfContains(types, text, "訴訟", "訴訟");
        addIfContains(types, text, "處分資產", "處分資產");
        addIfContains(types, text, "減資", "減資");
        addIfContains(types, text, "董事會", "董事會");
        addIfContains(types, text, "澄清", "澄清");
    }

    private void addIfContains(List<String> types, String text, String keyword, String label) {
        if (text.contains(keyword) && !types.contains(label)) {
            types.add(label);
        }
    }

    private List<String> unique(List<String> values) {
        List<String> dedup = new ArrayList<String>();
        for (String value : values) {
            if (value == null || value.length() == 0 || dedup.contains(value)) {
                continue;
            }
            dedup.add(value);
        }
        return dedup;
    }

    private int estimateFreshnessDays(double freshnessScore, String publishedHint) {
        String hint = publishedHint == null ? "" : publishedHint;
        if (hint.contains("分鐘") || hint.contains("小時")) {
            return 0;
        }
        if (hint.contains("1天") || hint.contains("昨日") || hint.contains("昨天")) {
            return 1;
        }
        if (freshnessScore >= 85D) {
            return 1;
        }
        if (freshnessScore >= 70D) {
            return 2;
        }
        if (freshnessScore >= 55D) {
            return 4;
        }
        if (freshnessScore > 0D) {
            return 7;
        }
        return 999;
    }

    private String safeUpper(String text) {
        return text == null ? "" : text.trim().toUpperCase();
    }

    private boolean containsAny(String text, String[] keywords) {
        if (text == null || text.length() == 0 || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && keyword.length() > 0 && text.contains(keyword.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private void applyMarketThemeNewsMetadata(List<StockAnalysisResultVO> results) {
        if (results == null || results.isEmpty() || lastMarketThemeReferenceBundle == null
                || lastMarketThemeReferenceBundle.articles.isEmpty()) {
            return;
        }
        Map<String, List<MarketThemeNewsAnalyzer.ArticleReference>> articlesByCode = new HashMap<String, List<MarketThemeNewsAnalyzer.ArticleReference>>();
        for (MarketThemeNewsAnalyzer.ArticleReference article : lastMarketThemeReferenceBundle.articles) {
            if (article == null || article.mentions == null || article.mentions.isEmpty()) {
                continue;
            }
            for (MarketThemeNewsAnalyzer.Mention mention : article.mentions) {
                if (mention == null || mention.code == null || mention.code.length() == 0) {
                    continue;
                }
                List<MarketThemeNewsAnalyzer.ArticleReference> references = articlesByCode.get(mention.code);
                if (references == null) {
                    references = new ArrayList<MarketThemeNewsAnalyzer.ArticleReference>();
                    articlesByCode.put(mention.code, references);
                }
                references.add(article);
            }
        }

        for (StockAnalysisResultVO result : results) {
            if (result == null || result.getStock() == null) {
                continue;
            }
            List<MarketThemeNewsAnalyzer.ArticleReference> references = articlesByCode.get(result.getStock().getCode());
            if (references == null || references.isEmpty()) {
                continue;
            }

            List<String> titles = new ArrayList<String>();
            String latestHint = emptyIfBlank(result.getLatestNewsPublishedHint(), "");
            double latestFreshness = result.getNewsFreshnessScore();
            for (MarketThemeNewsAnalyzer.ArticleReference reference : references) {
                if (reference == null) {
                    continue;
                }
                if (reference.title != null && reference.title.length() > 0 && !titles.contains(reference.title)) {
                    titles.add(reference.title);
                }
                double freshness = estimateNewsFreshness(reference.publishedHint);
                if (freshness > latestFreshness && reference.publishedHint != null
                        && reference.publishedHint.length() > 0) {
                    latestFreshness = freshness;
                    latestHint = reference.publishedHint;
                }
            }
            if (titles.isEmpty()) {
                continue;
            }

            result.setNewsSummary(mergeNewsText(result.getNewsSummary(), titles, 8));
            result.setNewsDigest(mergeNewsText(result.getNewsDigest(), titles, 3));
            result.setNewsSourceSummary(mergeSourceSummary(result.getNewsSourceSummary(), "市場主題新聞 " + titles.size() + "則"));
            result.setLatestNewsPublishedHint(latestHint);

            int existingSourceCount = Math.max(0, result.getNewsSourceCount());
            int newSourceCount = existingSourceCount + 1;
            result.setNewsSourceCredibilityScore(weightedAverage(result.getNewsSourceCredibilityScore(),
                    existingSourceCount, 72D, 1));
            result.setNewsFreshnessScore(weightedAverage(result.getNewsFreshnessScore(), existingSourceCount,
                    latestFreshness, 1));
            result.setNewsSourceCount(newSourceCount);
            result.setNewsMediaSourceCount(result.getNewsMediaSourceCount() + 1);
            result.setNewsScore(NumberParser.clamp(result.getNewsScore() + Math.min(6D, titles.size() * 1.5D) + 2D,
                    0D, 100D));
            result.setNewsRiskScore(NumberParser.clamp(result.getNewsRiskScore() - Math.min(3D, titles.size()), 0D,
                    100D));
        }
    }

    private String buildNewsDigest(List<String> headlines, int maxItems) {
        return mergeNewsText("", headlines, maxItems);
    }

    private String mergeNewsText(String current, List<String> additions, int maxItems) {
        List<String> merged = splitNewsTokens(current);
        if (additions != null) {
            for (String addition : additions) {
                String value = emptyIfBlank(addition, "");
                if (value.length() == 0 || merged.contains(value)) {
                    continue;
                }
                merged.add(value);
                if (merged.size() >= maxItems) {
                    break;
                }
            }
        }
        return join(merged, "；");
    }

    private List<String> splitNewsTokens(String text) {
        List<String> tokens = new ArrayList<String>();
        String normalized = emptyIfBlank(text, "");
        if (normalized.length() == 0) {
            return tokens;
        }
        for (String part : normalized.split("[；\\n]")) {
            String value = emptyIfBlank(part, "");
            if (value.length() == 0 || tokens.contains(value)) {
                continue;
            }
            tokens.add(value);
        }
        return tokens;
    }

    private String mergeSourceSummary(String current, String addition) {
        List<String> tokens = splitNewsTokens(current);
        String value = emptyIfBlank(addition, "");
        if (value.length() > 0 && !tokens.contains(value)) {
            tokens.add(value);
        }
        return join(tokens, "；");
    }

    private double estimateNewsFreshness(String publishedHint) {
        String hint = emptyIfBlank(publishedHint, "");
        if (hint.length() == 0) {
            return 0D;
        }
        if (hint.contains("分鐘前")) {
            return NumberParser.clamp(100D - extractLeadingNumber(hint) * 0.6D, 82D, 100D);
        }
        if (hint.contains("小時前")) {
            return NumberParser.clamp(96D - extractLeadingNumber(hint) * 4D, 64D, 96D);
        }
        if (hint.matches("\\d{4}/\\d{2}/\\d{2}")) {
            try {
                LocalDate publishedDate = LocalDate.parse(hint, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                long days = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(publishedDate, LocalDate.now(TAIPEI_ZONE)));
                return NumberParser.clamp(92D - days * 9D, 18D, 92D);
            } catch (Exception ignored) {
                return 40D;
            }
        }
        if (hint.length() >= 10 && hint.indexOf('T') > 0) {
            try {
                LocalDate publishedDate = LocalDate.parse(hint.substring(0, 10));
                long days = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(publishedDate, LocalDate.now(TAIPEI_ZONE)));
                return NumberParser.clamp(92D - days * 9D, 18D, 92D);
            } catch (Exception ignored) {
                return 40D;
            }
        }
        return 35D;
    }

    private long extractLeadingNumber(String text) {
        String normalized = emptyIfBlank(text, "");
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return 0L;
        }
        return NumberParser.parseLong(digits.toString());
    }

    private double weightedAverage(double leftValue, int leftWeight, double rightValue, int rightWeight) {
        int totalWeight = Math.max(0, leftWeight) + Math.max(0, rightWeight);
        if (totalWeight <= 0) {
            return 0D;
        }
        return (Math.max(0, leftWeight) * leftValue + Math.max(0, rightWeight) * rightValue) / totalWeight;
    }

    private double scoreRevenue(double latestRevenueYoY, double averageThreeMonthRevenueYoY, double accumulatedRevenueYoY,
            int positiveRevenueMonths) {
        double score = 0D;

        // 最新月營收年增（最高 11 分）
        if (latestRevenueYoY > 20D) {
            score += 11D;
        } else if (latestRevenueYoY > 10D) {
            score += 8D;
        } else if (latestRevenueYoY > 0D) {
            score += 6D;
        } else if (latestRevenueYoY > -10D) {
            score += 2D;
        }

        // 近 3 月平均年增（最高 12 分）
        if (averageThreeMonthRevenueYoY > 15D) {
            score += 12D;
        } else if (averageThreeMonthRevenueYoY > 5D) {
            score += 9D;
        } else if (averageThreeMonthRevenueYoY > 0D) {
            score += 6D;
        } else if (averageThreeMonthRevenueYoY > -10D) {
            score += 3D;
        }

        // 累計年增（最高 5 分）
        if (accumulatedRevenueYoY > 10D) {
            score += 5D;
        } else if (accumulatedRevenueYoY > 0D) {
            score += 3D;
        } else if (accumulatedRevenueYoY > -5D) {
            score += 1D;
        }

        // 正成長月數連續性（最高 2 分）
        if (positiveRevenueMonths >= 3) {
            score += 2D;
        } else if (positiveRevenueMonths == 2) {
            score += 1D;
        }

        return NumberParser.clamp(score, 0D, 30D);
    }

    private double scoreChips(double fiveDayInstitutionalNetRatioPct, double latestInstitutionalNetRatioPct,
            long latestForeignNetLots, double brokerNetRatioPct, long brokerNetLots) {
        double score = 0D;

        // 5日法人買賣超比率（最高 12 分）
        if (fiveDayInstitutionalNetRatioPct > 2D) {
            score += 12D;
        } else if (fiveDayInstitutionalNetRatioPct > 0D) {
            score += 9D;
        } else if (fiveDayInstitutionalNetRatioPct > -1D) {
            score += 4D;
        }

        // 最新日法人占比（最高 5 分）
        if (latestInstitutionalNetRatioPct > 1D) {
            score += 5D;
        } else if (latestInstitutionalNetRatioPct > 0D) {
            score += 3D;
        }

        // 外資方向（最高 6 分）
        if (latestForeignNetLots > 0L) {
            score += 6D;
        }

        // 主力比率（最高 6 分）
        if (brokerNetRatioPct > 2D) {
            score += 6D;
        } else if (brokerNetRatioPct > 0D) {
            score += 4D;
        } else if (brokerNetRatioPct > -1D) {
            score += 2D;
        }

        // 主力張數方向（最高 1 分）
        if (brokerNetLots > 0L) {
            score += 1D;
        }

        return NumberParser.clamp(score, 0D, 30D);
    }

    private double scoreLiquidity(double averageTradeValue20Billion, double averageLots20, double marketCapMillions) {
        double score = 0D;

        // 日均成交金額（最高 8 分）
        if (averageTradeValue20Billion >= 20D) {
            score += 8D;
        } else if (averageTradeValue20Billion >= 10D) {
            score += 6D;
        } else if (averageTradeValue20Billion >= 3D) {
            score += 4D;
        } else if (averageTradeValue20Billion >= 1D) {
            score += 2D;
        }

        // 日均張數（最高 4 分）
        if (averageLots20 >= 20000D) {
            score += 4D;
        } else if (averageLots20 >= 5000D) {
            score += 3D;
        } else if (averageLots20 >= 1000D) {
            score += 1D;
        }

        // 市值（最高 3 分）
        if (marketCapMillions >= 100000D) {
            score += 3D;
        } else if (marketCapMillions >= 30000D) {
            score += 2D;
        } else if (marketCapMillions >= 10000D) {
            score += 1D;
        }

        return NumberParser.clamp(score, 0D, 15D);
    }

    private double scoreValuation(double trailingPe, double trailingFourQuarterEps, double peerAveragePe,
            double nonOperatingRatioPct, double epsAccelerationPct, double peg) {
        if (trailingFourQuarterEps <= 0D) {
            return 0D;
        }

        double score = 0D;

        // 相對估值（最高 12 分）
        if (peerAveragePe > 0D) {
            double relativePe = trailingPe / peerAveragePe;
            if (relativePe <= 0.6D) {
                score += 12D;
            } else if (relativePe <= 0.9D) {
                score += 9D;
            } else if (relativePe <= 1.1D) {
                score += 7D;
            } else if (relativePe <= 1.3D) {
                score += 4D;
            } else if (relativePe <= 1.6D) {
                score += 2D;
            }
        } else if (trailingPe <= 12D) {
            score += 12D;
        } else if (trailingPe <= 18D) {
            score += 9D;
        } else if (trailingPe <= 25D) {
            score += 6D;
        } else if (trailingPe <= 35D) {
            score += 3D;
        }

        // PEG（成長調整後估值，最高 4 分）
        if (peg > 0D) {
            if (peg <= 0.5D) {
                score += 4D;
            } else if (peg <= 1.0D) {
                score += 3D;
            } else if (peg <= 1.5D) {
                score += 1D;
            } else if (peg > 2.0D) {
                score -= 1D;
            }
        }

        // EPS 加速成長加分（最高 4 分）
        if (epsAccelerationPct > 20D) {
            score += 4D;
        } else if (epsAccelerationPct > 5D) {
            score += 2D;
        } else if (epsAccelerationPct < -20D) {
            score -= 2D;
        }

        // 非營業比例扣分
        if (nonOperatingRatioPct > 30D) {
            score -= 3D;
        } else if (nonOperatingRatioPct > 15D) {
            score -= 1D;
        }

        return NumberParser.clamp(score, 0D, 20D);
    }

    private double scoreTechnical(double currentPrice, double movingAverage20, double movingAverage60,
            double movingAverage120, double return20DayPct, double return60DayPct, double volumeRatio,
            double volatility20Pct, double drawdownFromHigh60Pct, double rsi14, double stochasticK, double stochasticD,
            double ma20Slope) {
        double score = 0D;

        // 均線多頭排列（最高 9 分）
        if (currentPrice > movingAverage20) {
            score += 3D;
        }
        if (currentPrice > movingAverage60) {
            score += 3D;
        }
        if (currentPrice > movingAverage120) {
            score += 1D;
        }
        if (movingAverage20 > movingAverage60) {
            score += 2D;
        }
        if (movingAverage60 > movingAverage120) {
            score += 1D;
        }

        // MA20 斜率（最高 2 分）：均線本身在上升才是真正多頭
        if (ma20Slope > 0.3D) {
            score += 2D;
        } else if (ma20Slope > 0D) {
            score += 1D;
        } else if (ma20Slope < -0.5D) {
            score -= 1D;  // 均線下彎，扣分
        }

        // 20日/60日漲幅（最高 3 分）
        if (return20DayPct > 8D) {
            score += 2D;
        } else if (return20DayPct > 0D) {
            score += 1D;
        }
        if (return60DayPct > 15D) {
            score += 1D;
        }

        // 量比（最高 1 分）
        if (volumeRatio > 1.2D) {
            score += 1D;
        }

        // 距60日高點 + 爆量突破確認（最高 3 分）
        if (drawdownFromHigh60Pct >= -1.5D && volumeRatio >= 1.5D) {
            score += 3D;  // 接近/突破60日高點且有量
        } else if (drawdownFromHigh60Pct >= -3D && volumeRatio >= 1.3D) {
            score += 2D;  // 相對高位有量
        } else if (drawdownFromHigh60Pct >= -8D) {
            score += 1D;  // 靠近高位但量能普通
        }

        // RSI14（最高 3 分）：超買過熱扣分，黃金區間加分
        if (rsi14 >= 50D && rsi14 < 70D) {
            score += 3D;
        } else if (rsi14 >= 40D && rsi14 < 50D) {
            score += 1D;
        } else if (rsi14 >= 70D && rsi14 < 80D) {
            score += 1D;
        } else if (rsi14 >= 80D) {
            score -= 2D;  // 嚴重超買
        } else if (rsi14 < 30D) {
            score -= 1D;  // 超賣，技術弱勢
        }

        // KD 值（最高 3 分）：K>D 且 K 在健康區間加分
        if (stochasticK > stochasticD && stochasticK >= 50D && stochasticK < 80D) {
            score += 3D;
        } else if (stochasticK > stochasticD && stochasticK < 50D) {
            score += 2D;  // 剛從低位黃金交叉
        } else if (stochasticK >= 80D) {
            score -= 1D;  // KD 超買
        }

        // 波動度（最高 1 分）
        if (volatility20Pct > 0D && volatility20Pct <= 3.5D) {
            score += 1D;
        }

        return NumberParser.clamp(score, 0D, 20D);
    }

    private double calcTripleConfirmBonus(double latestQuarterEpsYoYPct, double latestInstitutionalNetRatioPct,
            double technicalScore, double ma20Slope, double drawdownFromHigh60Pct, double volumeRatio) {
        boolean epsStrong = hasValue(latestQuarterEpsYoYPct) && latestQuarterEpsYoYPct >= 20D;
        boolean instBuying = hasValue(latestInstitutionalNetRatioPct) && latestInstitutionalNetRatioPct >= 0.3D;
        boolean techStrong = technicalScore >= 14D;
        boolean maRising = ma20Slope > 0D;
        boolean nearHighWithVolume = drawdownFromHigh60Pct >= -3D && volumeRatio >= 1.3D;
        int signals = (epsStrong ? 1 : 0) + (instBuying ? 1 : 0) + (techStrong ? 1 : 0);
        if (signals == 3 && (maRising || nearHighWithVolume)) {
            return 5D;  // 三重確認 + 趨勢加成
        }
        if (signals == 3) {
            return 3D;  // 三重確認
        }
        if (signals == 2 && maRising && nearHighWithVolume) {
            return 2D;  // 兩重確認但技術形態強
        }
        return 0D;
    }

    private double scoreFinancialQuality(double trailingFourQuarterEps, double latestQuarterEps,
            double latestQuarterEpsYoYPct, int positiveEpsQuarters, long latestOperatingCashFlow,
            long latestFreeCashFlow, int positiveOperatingCashFlowQuarters, int positiveFreeCashFlowQuarters,
            double grossMarginPct, double operatingMarginPct, double returnOnAssetsPct, double returnOnEquityPct,
            double debtRatioPct, double currentRatio, double nonOperatingRatioPct, double epsAccelerationPct) {
        double score = 0D;

        // EPS 獲利能力（最高 5 分）
        if (trailingFourQuarterEps > 0D) {
            score += 2D;
        }
        if (latestQuarterEps > 0D) {
            score += 1D;
        }
        if (latestQuarterEpsYoYPct > 20D) {
            score += 2D;
        } else if (latestQuarterEpsYoYPct > 0D) {
            score += 1D;
        }

        // EPS 加速成長（最高 3 分）
        if (epsAccelerationPct > 20D) {
            score += 3D;
        } else if (epsAccelerationPct > 5D) {
            score += 2D;
        } else if (epsAccelerationPct > 0D) {
            score += 1D;
        }

        // EPS 連續正成長（最高 2 分）
        if (positiveEpsQuarters >= 4) {
            score += 2D;
        } else if (positiveEpsQuarters >= 3) {
            score += 1D;
        }

        // 現金流（最高 4 分）
        if (latestOperatingCashFlow > 0L) {
            score += 1D;
        }
        if (latestFreeCashFlow > 0L) {
            score += 1D;
        }
        if (positiveOperatingCashFlowQuarters >= 3) {
            score += 1D;
        }
        if (positiveFreeCashFlowQuarters >= 2) {
            score += 1D;
        }

        // 利潤率與回報率（最高 4 分）
        if (grossMarginPct >= 25D) {
            score += 1D;
        }
        if (operatingMarginPct >= 8D) {
            score += 1D;
        }
        if (returnOnAssetsPct >= 3D) {
            score += 1D;
        }
        if (returnOnEquityPct >= 8D) {
            score += 1D;
        }

        // 財務健全度（最高 3 分）
        if (debtRatioPct > 0D && debtRatioPct <= 60D) {
            score += 1D;
        }
        if (currentRatio >= 1.2D) {
            score += 1D;
        }
        if (!Double.isNaN(nonOperatingRatioPct) && nonOperatingRatioPct <= 15D) {
            score += 1D;
        }

        return NumberParser.clamp(score, 0D, 20D);
    }

    private TurnaroundProfile buildTurnaroundProfile(double latestRevenueYoY, double averageThreeMonthRevenueYoY,
            double accumulatedRevenueYoY, int positiveRevenueMonths, double latestQuarterEps, double previousQuarterEps,
            double latestQuarterEpsYoYPct, int positiveEpsQuarters, double epsAccelerationPct,
            long latestOperatingIncome, long previousOperatingIncome, long latestNetIncome, long previousNetIncome,
            long latestOperatingCashFlow, long previousOperatingCashFlow, long latestFreeCashFlow,
            long previousFreeCashFlow, double nonOperatingRatioPct) {
        double revenueGrowthScore = scoreRevenueGrowthSignal(latestRevenueYoY, averageThreeMonthRevenueYoY,
                accumulatedRevenueYoY, positiveRevenueMonths);
        double earningsTurnaroundScore = scoreEarningsTurnaroundSignal(latestQuarterEps, previousQuarterEps,
                latestQuarterEpsYoYPct, positiveEpsQuarters, epsAccelerationPct);
        double profitabilityTurnaroundScore = scoreProfitabilityTurnaroundSignal(latestOperatingIncome,
                previousOperatingIncome, latestNetIncome, previousNetIncome, latestOperatingCashFlow,
                previousOperatingCashFlow, latestFreeCashFlow, previousFreeCashFlow);
        double oneOffRiskScore = scoreOneOffRisk(latestRevenueYoY, averageThreeMonthRevenueYoY, latestQuarterEps,
                latestOperatingIncome, latestNetIncome, latestOperatingCashFlow, latestFreeCashFlow,
                nonOperatingRatioPct);
        double score = NumberParser.clamp(revenueGrowthScore * 0.28D + earningsTurnaroundScore * 0.32D
                + profitabilityTurnaroundScore * 0.30D - oneOffRiskScore * 0.22D + 12D, 0D, 100D);
        String label = resolveTurnaroundLabel(score, revenueGrowthScore, earningsTurnaroundScore,
                profitabilityTurnaroundScore, oneOffRiskScore, latestQuarterEps, previousQuarterEps, latestNetIncome,
                previousNetIncome);
        String reason = buildTurnaroundReason(revenueGrowthScore, earningsTurnaroundScore,
                profitabilityTurnaroundScore, oneOffRiskScore, latestRevenueYoY, averageThreeMonthRevenueYoY,
                latestQuarterEps, previousQuarterEps, latestNetIncome, previousNetIncome, latestOperatingCashFlow,
                latestFreeCashFlow, nonOperatingRatioPct, label);
        return new TurnaroundProfile(score, revenueGrowthScore, earningsTurnaroundScore,
                profitabilityTurnaroundScore, oneOffRiskScore, label, reason);
    }

    private double scoreRevenueGrowthSignal(double latestRevenueYoY, double averageThreeMonthRevenueYoY,
            double accumulatedRevenueYoY, int positiveRevenueMonths) {
        double score = 0D;
        if (latestRevenueYoY > 25D) {
            score += 30D;
        } else if (latestRevenueYoY > 10D) {
            score += 22D;
        } else if (latestRevenueYoY > 0D) {
            score += 14D;
        } else if (latestRevenueYoY > -5D) {
            score += 6D;
        }
        if (averageThreeMonthRevenueYoY > 15D) {
            score += 24D;
        } else if (averageThreeMonthRevenueYoY > 5D) {
            score += 16D;
        } else if (averageThreeMonthRevenueYoY > 0D) {
            score += 10D;
        }
        if (accumulatedRevenueYoY > 10D) {
            score += 12D;
        } else if (accumulatedRevenueYoY > 0D) {
            score += 7D;
        }
        if (positiveRevenueMonths >= 3) {
            score += 14D;
        } else if (positiveRevenueMonths >= 2) {
            score += 10D;
        } else if (positiveRevenueMonths >= 1) {
            score += 5D;
        }
        if (latestRevenueYoY > averageThreeMonthRevenueYoY + 10D) {
            score += 10D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreEarningsTurnaroundSignal(double latestQuarterEps, double previousQuarterEps,
            double latestQuarterEpsYoYPct, int positiveEpsQuarters, double epsAccelerationPct) {
        double score = 0D;
        if (latestQuarterEps > 0D && previousQuarterEps <= 0D) {
            score += 40D;
        } else if (latestQuarterEps > 0D) {
            score += 12D;
        }
        if (latestQuarterEpsYoYPct > 40D) {
            score += 22D;
        } else if (latestQuarterEpsYoYPct > 15D) {
            score += 16D;
        } else if (latestQuarterEpsYoYPct > 0D) {
            score += 10D;
        }
        if (positiveEpsQuarters >= 2) {
            score += 10D;
        } else if (positiveEpsQuarters >= 1) {
            score += 6D;
        }
        if (epsAccelerationPct > 20D) {
            score += 12D;
        } else if (epsAccelerationPct > 5D) {
            score += 6D;
        }
        if (latestQuarterEps <= 0D && latestQuarterEpsYoYPct < 0D) {
            score -= 8D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreProfitabilityTurnaroundSignal(long latestOperatingIncome, long previousOperatingIncome,
            long latestNetIncome, long previousNetIncome, long latestOperatingCashFlow, long previousOperatingCashFlow,
            long latestFreeCashFlow, long previousFreeCashFlow) {
        double score = 0D;
        if (latestNetIncome > 0L && previousNetIncome <= 0L) {
            score += 36D;
        } else if (latestNetIncome > 0L) {
            score += 10D;
        }
        if (latestOperatingIncome > 0L && previousOperatingIncome <= 0L) {
            score += 18D;
        } else if (latestOperatingIncome > 0L) {
            score += 8D;
        }
        if (latestOperatingCashFlow > 0L && previousOperatingCashFlow <= 0L) {
            score += 18D;
        } else if (latestOperatingCashFlow > 0L) {
            score += 8D;
        }
        if (latestFreeCashFlow > 0L && previousFreeCashFlow <= 0L) {
            score += 14D;
        } else if (latestFreeCashFlow > 0L) {
            score += 6D;
        }
        if (latestNetIncome <= 0L && latestOperatingCashFlow <= 0L) {
            score -= 10D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreOneOffRisk(double latestRevenueYoY, double averageThreeMonthRevenueYoY, double latestQuarterEps,
            long latestOperatingIncome, long latestNetIncome, long latestOperatingCashFlow, long latestFreeCashFlow,
            double nonOperatingRatioPct) {
        double score = 0D;
        if (latestNetIncome > 0L && latestOperatingIncome <= 0L) {
            score += 40D;
        }
        if (!Double.isNaN(nonOperatingRatioPct)) {
            if (nonOperatingRatioPct > 35D) {
                score += 30D;
            } else if (nonOperatingRatioPct > 20D) {
                score += 20D;
            } else if (nonOperatingRatioPct > 10D) {
                score += 10D;
            }
        }
        if (latestQuarterEps > 0D && latestOperatingCashFlow <= 0L) {
            score += 18D;
        }
        if (latestQuarterEps > 0D && latestFreeCashFlow <= 0L) {
            score += 12D;
        }
        if (latestNetIncome > 0L && latestRevenueYoY <= 0D && averageThreeMonthRevenueYoY <= 0D) {
            score += 10D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private String resolveTurnaroundLabel(double turnaroundScore, double revenueGrowthScore,
            double earningsTurnaroundScore, double profitabilityTurnaroundScore, double oneOffRiskScore,
            double latestQuarterEps, double previousQuarterEps, long latestNetIncome, long previousNetIncome) {
        if (oneOffRiskScore >= 65D && turnaroundScore < 55D) {
            return "一次性轉盈風險";
        }
        if (turnaroundScore >= 78D && oneOffRiskScore <= 25D
                && (earningsTurnaroundScore >= 65D || profitabilityTurnaroundScore >= 65D)) {
            return "高品質翻轉";
        }
        if ((latestQuarterEps > 0D && previousQuarterEps <= 0D) || (latestNetIncome > 0L && previousNetIncome <= 0L)) {
            return "轉虧為盈";
        }
        if (earningsTurnaroundScore >= 60D || profitabilityTurnaroundScore >= 60D) {
            return "業績翻轉";
        }
        if (revenueGrowthScore >= 60D) {
            return "業績成長";
        }
        if (turnaroundScore >= 45D) {
            return "翻轉觀察";
        }
        return "尚未明確";
    }

    private String buildTurnaroundReason(double revenueGrowthScore, double earningsTurnaroundScore,
            double profitabilityTurnaroundScore, double oneOffRiskScore, double latestRevenueYoY,
            double averageThreeMonthRevenueYoY, double latestQuarterEps, double previousQuarterEps, long latestNetIncome,
            long previousNetIncome, long latestOperatingCashFlow, long latestFreeCashFlow, double nonOperatingRatioPct,
            String label) {
        List<String> parts = new ArrayList<String>();
        parts.add(label + "：營收 " + format(revenueGrowthScore) + " / 業績 " + format(earningsTurnaroundScore)
                + " / 轉盈 " + format(profitabilityTurnaroundScore) + " / 一次性風險 " + format(oneOffRiskScore));
        if (latestRevenueYoY > 0D && averageThreeMonthRevenueYoY > 0D) {
            parts.add("營收維持正成長");
        } else if (latestRevenueYoY > 0D) {
            parts.add("單月營收轉正");
        }
        if (latestQuarterEps > 0D && previousQuarterEps <= 0D) {
            parts.add("EPS 由負轉正");
        } else if (latestQuarterEps > 0D) {
            parts.add("EPS 已回到正值");
        }
        if (latestNetIncome > 0L && previousNetIncome <= 0L) {
            parts.add("淨利由虧轉盈");
        }
        if (latestOperatingCashFlow > 0L) {
            parts.add("營業現金流為正");
        }
        if (latestFreeCashFlow > 0L) {
            parts.add("自由現金流為正");
        }
        if (!Double.isNaN(nonOperatingRatioPct) && nonOperatingRatioPct > 20D) {
            parts.add("非營業比重偏高");
        }
        return join(parts, "；");
    }

    private double scoreQualityProfile(double revenueScore, double financialQualityScore, double valuationScore,
            double liquidityScore) {
        double normalized = (revenueScore / 30D) * 35D + (financialQualityScore / 20D) * 35D
                + (valuationScore / 20D) * 20D + (liquidityScore / 15D) * 10D;
        return NumberParser.clamp(normalized, 0D, 100D);
    }

    private double scoreMomentumProfile(double chipsScore, double technicalScore, double volumeRatio,
            double return20DayPct, double rsi14, double stochasticK, double stochasticD) {
        double score = (chipsScore / 30D) * 40D + (technicalScore / 20D) * 30D;

        if (volumeRatio >= LIKELY_MIN_VOLUME_RATIO && volumeRatio <= LIKELY_MAX_VOLUME_RATIO) {
            score += 15D;
        } else if (volumeRatio >= 0.6D && volumeRatio < LIKELY_MIN_VOLUME_RATIO) {
            score += 8D;
        } else if (volumeRatio > LIKELY_MAX_VOLUME_RATIO && volumeRatio <= 3.5D) {
            score += 6D;
        }

        if (return20DayPct >= 0D && return20DayPct <= 20D) {
            score += 10D;
        } else if (return20DayPct > 20D && return20DayPct <= 35D) {
            score += 6D;
        } else if (return20DayPct < -10D) {
            score -= 4D;
        }

        if (rsi14 >= 50D && rsi14 < 70D) {
            score += 3D;
        } else if (rsi14 >= 70D && rsi14 < 80D) {
            score += 2D;
        } else if (rsi14 >= 80D) {
            score -= 3D;
        }

        if (stochasticK > stochasticD && stochasticK >= 40D && stochasticK < 80D) {
            score += 2D;
        }

        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreNewsSignal(NewsSignalVO newsSignal) {
        if (newsSignal == null || newsSignal.getHeadlineCount() <= 0) {
            return 50D;
        }
        double score = 42D + Math.min(18D, newsSignal.getHeadlineCount() * 3.5D);
        String summary = newsSignal.getSummaryText();
        int positiveHits = countKeywordHits(summary, POSITIVE_NEWS_KEYWORDS);
        int cautionHits = countKeywordHits(summary, CAUTION_NEWS_KEYWORDS);
        int negativeHits = countKeywordHits(summary, NEGATIVE_NEWS_KEYWORDS);
        score += Math.min(24D, positiveHits * 6D);
        score += Math.min(8D, cautionHits * 2D);
        score -= Math.min(22D, negativeHits * 7D);
        score += Math.min(10D, newsSignal.getSourceCount() * 4D);
        score += Math.min(8D, newsSignal.getOfficialSourceCount() * 4D);
        score += Math.max(0D, newsSignal.getSourceCredibilityScore() - 55D) * 0.12D;
        score += Math.max(0D, newsSignal.getFreshnessScore() - 45D) * 0.12D;
        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreNewsRisk(NewsSignalVO newsSignal, double eventRiskPenalty) {
        double score = 35D + eventRiskPenalty * 8D;
        if (newsSignal == null || newsSignal.getHeadlineCount() <= 0) {
            return NumberParser.clamp(score, 0D, 100D);
        }
        String summary = newsSignal.getSummaryText();
        int negativeHits = countKeywordHits(summary, NEGATIVE_NEWS_KEYWORDS);
        int cautionHits = countKeywordHits(summary, CAUTION_NEWS_KEYWORDS);
        int positiveHits = countKeywordHits(summary, POSITIVE_NEWS_KEYWORDS);
        score += negativeHits * 10D + cautionHits * 4D;
        score -= Math.min(12D, positiveHits * 2D);
        score -= Math.min(6D, newsSignal.getOfficialSourceCount() * 2D);
        score -= Math.max(0D, newsSignal.getSourceCredibilityScore() - 60D) * 0.08D;
        if (newsSignal.getFreshnessScore() <= 0D) {
            score += 4D;
        } else if (newsSignal.getFreshnessScore() < 35D) {
            score += 3D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private int countKeywordHits(String text, String[] keywords) {
        String normalized = text == null ? "" : text.toLowerCase();
        int hits = 0;
        for (String keyword : keywords) {
            if (keyword != null && keyword.length() > 0 && normalized.contains(keyword.toLowerCase())) {
                hits++;
            }
        }
        return hits;
    }

    private double scoreSelectionProfile(double rawScore, double qualityScore, double momentumScore, double volumeRatio,
            double eventRiskPenalty, boolean selectionQualified) {
        return activeScoringStrategy.scoreSelectionProfile(rawScore, qualityScore, momentumScore, volumeRatio,
                eventRiskPenalty, selectionQualified);
    }

    private double scoreSelectionComposite(double baseSelectionScore, double trendPersistenceScore, double sectorScore,
            double newsRiskScore) {
        return activeScoringStrategy.scoreSelectionComposite(baseSelectionScore, trendPersistenceScore, sectorScore,
                newsRiskScore);
    }

    private double scoreBuyPointProfile(double selectionScore, double momentumScore, double qualityScore,
            double currentPrice, double movingAverage20, double movingAverage60, double movingAverage120,
            double return20DayPct, double volumeRatio, double drawdownFromHigh60Pct, double rsi14, double stochasticK,
            double stochasticD, double eventRiskPenalty, boolean selectionQualified, double financialQualityScore) {
        double score = selectionScore * 0.25D + momentumScore * 0.20D + qualityScore * 0.10D;

        if (currentPrice > movingAverage20) {
            score += 8D;
        }
        if (currentPrice > movingAverage60) {
            score += 8D;
        }
        if (currentPrice > movingAverage120) {
            score += 4D;
        }
        if (movingAverage20 > movingAverage60) {
            score += 6D;
        }

        if (drawdownFromHigh60Pct >= -10D && drawdownFromHigh60Pct <= -3D) {
            score += 12D;
        } else if (drawdownFromHigh60Pct > -3D && drawdownFromHigh60Pct <= 1D) {
            score += 8D;
        } else if (drawdownFromHigh60Pct >= -18D && drawdownFromHigh60Pct < -10D) {
            score += 4D;
        } else if (drawdownFromHigh60Pct < -25D) {
            score -= 6D;
        }

        if (volumeRatio >= 0.8D && volumeRatio <= 1.8D) {
            score += 10D;
        } else if (volumeRatio > 1.8D && volumeRatio <= 2.8D) {
            score += 8D;
        } else if (volumeRatio > 3D) {
            score -= 6D;
        } else if (volumeRatio < 0.6D) {
            score -= 4D;
        }

        if (return20DayPct >= 0D && return20DayPct <= 12D) {
            score += 8D;
        } else if (return20DayPct > 12D && return20DayPct <= 25D) {
            score += 4D;
        } else if (return20DayPct > 25D) {
            score -= 8D;
        } else if (return20DayPct < -10D) {
            score -= 4D;
        }

        if (rsi14 >= 48D && rsi14 <= 65D) {
            score += 8D;
        } else if (rsi14 > 65D && rsi14 <= 72D) {
            score += 4D;
        } else if (rsi14 >= 78D) {
            score -= 8D;
        } else if (rsi14 < 40D) {
            score -= 4D;
        }

        if (stochasticK > stochasticD && stochasticK >= 40D && stochasticK < 80D) {
            score += 5D;
        } else if (stochasticK > stochasticD && stochasticK < 40D) {
            score += 3D;
        } else if (stochasticK >= 85D) {
            score -= 4D;
        }

        score -= eventRiskPenalty * 3D;
        if (!selectionQualified) {
            score -= 15D;
        }
        if (financialQualityScore < activeLikelyMinFinancialScore()) {
            score -= 8D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private double scoreBuyPointComposite(double baseBuyPointScore, double structureScore,
            double trendPersistenceScore, double riskRewardScore, double sectorScore, double newsScore,
            double newsRiskScore) {
        return activeScoringStrategy.scoreBuyPointComposite(baseBuyPointScore, structureScore, trendPersistenceScore,
                riskRewardScore, sectorScore, newsScore, newsRiskScore);
    }

    private StructureProfile buildStructureProfile(double currentPrice, double movingAverage20, double movingAverage60,
            double movingAverage120, double volumeRatio, double drawdownFromHigh60Pct, double return20DayPct,
            double rsi14, double stochasticK, double stochasticD) {
        double score = 35D;

        if (currentPrice > movingAverage20) {
            score += 12D;
        } else {
            score -= 12D;
        }
        if (currentPrice > movingAverage60) {
            score += 8D;
        }
        if (currentPrice > movingAverage120) {
            score += 4D;
        }
        if (movingAverage20 > movingAverage60) {
            score += 10D;
        }
        if (movingAverage60 > movingAverage120) {
            score += 4D;
        }

        if (drawdownFromHigh60Pct > -3D && drawdownFromHigh60Pct <= 1D) {
            score += 18D;
        } else if (drawdownFromHigh60Pct >= -10D && drawdownFromHigh60Pct <= -3D) {
            score += 15D;
        } else if (drawdownFromHigh60Pct >= -18D && drawdownFromHigh60Pct < -10D) {
            score += 6D;
        } else if (drawdownFromHigh60Pct < -25D) {
            score -= 12D;
        }

        if (volumeRatio >= 1.0D && volumeRatio <= 2.6D) {
            score += 8D;
        } else if (volumeRatio > 3D) {
            score -= 6D;
        } else if (volumeRatio < 0.6D) {
            score -= 5D;
        }

        if (return20DayPct >= 0D && return20DayPct <= 18D) {
            score += 6D;
        } else if (return20DayPct > 25D) {
            score -= 8D;
        }

        if (rsi14 >= 48D && rsi14 <= 68D) {
            score += 6D;
        } else if (rsi14 >= 78D) {
            score -= 8D;
        }

        if (stochasticK > stochasticD && stochasticK < 80D) {
            score += 4D;
        } else if (stochasticK >= 85D) {
            score -= 4D;
        }

        String label = "整理待確認";
        if (currentPrice > movingAverage20 && movingAverage20 > movingAverage60
                && drawdownFromHigh60Pct > -3D && drawdownFromHigh60Pct <= 1D) {
            label = "平台突破";
        } else if (currentPrice > movingAverage20 && currentPrice > movingAverage60
                && drawdownFromHigh60Pct >= -10D && drawdownFromHigh60Pct <= -3D) {
            label = "回踩承接";
        } else if (return20DayPct > 25D || rsi14 >= 78D || volumeRatio > 3D) {
            label = "追高風險";
        } else if (currentPrice < movingAverage20 || drawdownFromHigh60Pct < -18D) {
            label = "結構未完成";
        }

        return new StructureProfile(NumberParser.clamp(score, 0D, 100D), label);
    }

    private RiskRewardProfile buildRiskRewardProfile(double currentPrice, double movingAverage20,
            double movingAverage60, double movingAverage120, double drawdownFromHigh60Pct, double volatility20Pct,
            double atr20, double structureScore, double selectionScore) {
        if (currentPrice <= 0D) {
            return new RiskRewardProfile(0D, 0D, 0D, 0D, 0D, 0D, 0D, 100D, "觀察", false);
        }

        double supportPrice = currentPrice * 0.93D;
        if (currentPrice > movingAverage20 && movingAverage20 > 0D) {
            supportPrice = movingAverage20 * 0.985D;
        } else if (currentPrice > movingAverage60 && movingAverage60 > 0D) {
            supportPrice = movingAverage60 * 0.985D;
        } else if (movingAverage20 > 0D) {
            supportPrice = movingAverage20 * 0.97D;
        } else if (movingAverage60 > 0D) {
            supportPrice = movingAverage60 * 0.97D;
        } else if (movingAverage120 > 0D) {
            supportPrice = movingAverage120 * 0.97D;
        }

        double volatilityStopPct = Math.max(4D, Math.min(12D, volatility20Pct * 2.2D));
        double volatilityStopPrice = currentPrice * (1D - volatilityStopPct / 100D);
        double atrStopPrice = atr20 > 0D ? currentPrice - atr20 * activeScoringStrategy.stopAtrMultiplier() : 0D;
        double stopCandidatePrice = Math.min(supportPrice, volatilityStopPrice);
        if (atrStopPrice > 0D) {
            stopCandidatePrice = Math.min(stopCandidatePrice, atrStopPrice);
        }
        double stopLossPrice = Math.max(currentPrice * 0.82D, stopCandidatePrice);
        double stopLossPct = currentPrice <= stopLossPrice ? 0D : (currentPrice - stopLossPrice) * 100D / currentPrice;
        double trailingStopPrice = atr20 > 0D ? currentPrice - atr20 * activeScoringStrategy.trailingAtrMultiplier()
                : movingAverage20 > 0D ? movingAverage20 * 0.99D : stopLossPrice;
        trailingStopPrice = Math.max(stopLossPrice, trailingStopPrice);

        double upsidePotentialPct = Math.max(6D, Math.abs(Math.min(drawdownFromHigh60Pct, 0D))
                + (structureScore >= 80D ? 10D : structureScore >= 65D ? 7D : 4D));
        if (selectionScore >= 90D) {
            upsidePotentialPct += 2D;
        }
        double targetPrice = currentPrice * (1D + upsidePotentialPct / 100D);
        double riskRewardRatio = stopLossPct > 0D ? upsidePotentialPct / stopLossPct : 0D;

        double score = 10D;
        if (riskRewardRatio >= 3D) {
            score += 60D;
        } else if (riskRewardRatio >= 2D) {
            score += 45D;
        } else if (riskRewardRatio >= 1.5D) {
            score += 30D;
        } else if (riskRewardRatio >= 1D) {
            score += 15D;
        }

        if (stopLossPct >= 3D && stopLossPct <= 8D) {
            score += 20D;
        } else if (stopLossPct > 12D) {
            score -= 15D;
        } else if (stopLossPct > 0D && stopLossPct < 2D) {
            score -= 8D;
        }

        if (upsidePotentialPct >= 10D) {
            score += 12D;
        } else if (upsidePotentialPct >= 6D) {
            score += 6D;
        }

        boolean reducePositionSize = stopLossPct > 10D || (atr20 > 0D && atr20 * 100D / currentPrice > 6D);
        SellSignalProfile sellSignal = buildSellSignalProfile(currentPrice, movingAverage20, trailingStopPrice,
                drawdownFromHigh60Pct, stopLossPct, atr20);
        return new RiskRewardProfile(NumberParser.clamp(score, 0D, 100D), riskRewardRatio, stopLossPrice,
                stopLossPct, trailingStopPrice, targetPrice, upsidePotentialPct, sellSignal.score, sellSignal.label,
                reducePositionSize);
    }

    private double scoreDataConfidence(boolean hasProfileData, boolean hasEpsData, boolean hasCashFlowData,
            boolean hasIncomeData, boolean hasBalanceData, boolean hasBrokerData) {
        double score = 40D; // core revenue/institutional/technical data are mandatory
        if (hasProfileData) {
            score += 15D;
        }
        if (hasEpsData) {
            score += 15D;
        }
        if (hasCashFlowData) {
            score += 10D;
        }
        if (hasIncomeData) {
            score += 8D;
        }
        if (hasBalanceData) {
            score += 8D;
        }
        if (hasBrokerData) {
            score += 4D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private boolean isSelectionQualified(double liquidityScore, double financialQualityScore) {
        return isSelectionQualified(liquidityScore, financialQualityScore, 0D, 0D);
    }

    private boolean isSelectionQualified(double liquidityScore, double financialQualityScore, double volumeRatio,
            double dataConfidence) {
        return activeScoringStrategy.isSelectionQualified(liquidityScore, financialQualityScore, volumeRatio,
                dataConfidence);
    }

    private boolean isVolumeRangeHealthy(double volumeRatio) {
        return volumeRatio >= scoringConfig.getQualification().healthyVolumeMin
                && volumeRatio <= scoringConfig.getQualification().healthyVolumeMax;
    }

    private double activeLikelyThreshold() {
        return activeScoringStrategy != null ? activeScoringStrategy.likelySelectionThreshold() : LIKELY_THRESHOLD;
    }

    private double activeBuyPointThreshold() {
        return activeScoringStrategy != null ? activeScoringStrategy.buyPointThreshold() : BUYPOINT_THRESHOLD;
    }

    private double activeLikelyMinFinancialScore() {
        return activeScoringStrategy != null ? activeScoringStrategy.likelyMinFinancialQualityScore()
                : MIN_LIKELY_FINANCIAL_SCORE;
    }

    private void applyIndustryRelativeScoring(StockAnalysisResultVO result, IndustryMetricsSnapshot metrics) {
        if (result == null || metrics == null) {
            return;
        }

        double grossMarginPctile = metrics.percentile(result.getIndustry(), "grossMargin", result.getGrossMarginPct(), false);
        double operatingMarginPctile = metrics.percentile(result.getIndustry(), "operatingMargin",
                result.getOperatingMarginPct(), false);
        double roaPctile = metrics.percentile(result.getIndustry(), "roa", result.getReturnOnAssetsPct(), false);
        double roePctile = metrics.percentile(result.getIndustry(), "roe", result.getReturnOnEquityPct(), false);
        double pegPctile = metrics.percentile(result.getIndustry(), "peg", result.getPeg(), true);
        double relativePe = result.getPeerAveragePe() > 0D && result.getTrailingPe() > 0D
                ? result.getTrailingPe() / result.getPeerAveragePe() : result.getTrailingPe();
        double relativePePctile = metrics.percentile(result.getIndustry(), "relativePe", relativePe, true);
        double nonOperatingPctile = metrics.percentile(result.getIndustry(), "nonOperating",
                result.getNonOperatingRatioPct(), true);

        double valuationPercentile = averagePercentiles(pegPctile, relativePePctile, nonOperatingPctile);
        double financialPercentile = averagePercentiles(grossMarginPctile, operatingMarginPctile, roaPctile,
                roePctile, nonOperatingPctile);

        result.setGrossMarginIndustryPercentile(grossMarginPctile);
        result.setOperatingMarginIndustryPercentile(operatingMarginPctile);
        result.setRoaIndustryPercentile(roaPctile);
        result.setRoeIndustryPercentile(roePctile);
        result.setPegIndustryPercentile(pegPctile);
        result.setRelativePeIndustryPercentile(relativePePctile);
        result.setNonOperatingIndustryPercentile(nonOperatingPctile);
        result.setValuationIndustryPercentile(valuationPercentile);
        result.setFinancialQualityIndustryPercentile(financialPercentile);

        double valuationScore = NumberParser.clamp(result.getValuationScore() * 0.65D + valuationPercentile * 0.07D, 0D,
                20D);
        double financialQualityScore = NumberParser.clamp(
                result.getFinancialQualityScore() * 0.65D + financialPercentile * 0.07D, 0D, 20D);
        result.setValuationScore(valuationScore);
        result.setFinancialQualityScore(financialQualityScore);
        result.setQualityScore(scoreQualityProfile(result.getRevenueScore(), financialQualityScore, valuationScore,
                result.getLiquidityScore()));
        double rawScore = NumberParser.clamp(result.getRevenueScore() + result.getChipsScore() + result.getLiquidityScore()
                + valuationScore + result.getTechnicalScore() + financialQualityScore - result.getEventRiskPenalty(), 0D,
                RAW_SCORE_MAX);
        result.setRawScore(rawScore);
        result.setScore(NumberParser.clamp(rawScore, 0D, 100D));
    }

    private double averagePercentiles(double... values) {
        double total = 0D;
        int count = 0;
        for (double value : values) {
            if (value > 0D) {
                total += value;
                count++;
            }
        }
        return count == 0 ? 50D : total / count;
    }

    private SellSignalProfile buildSellSignalProfile(double currentPrice, double movingAverage20,
            double trailingStopPrice, double drawdownFromHigh60Pct, double stopLossPct, double atr20) {
        double score = 8D;
        if (currentPrice < movingAverage20 && movingAverage20 > 0D) {
            score += 35D;
        }
        if (currentPrice <= trailingStopPrice && trailingStopPrice > 0D) {
            score += 35D;
        }
        if (drawdownFromHigh60Pct < -12D) {
            score += 15D;
        }
        if (stopLossPct > 10D) {
            score += 10D;
        }
        if (atr20 > 0D && atr20 * 100D / currentPrice > 6D) {
            score += 8D;
        }
        score = NumberParser.clamp(score, 0D, 100D);
        String label = score >= 70D ? "轉弱出場" : score >= 45D ? "保守續抱" : "續抱觀察";
        return new SellSignalProfile(score, label);
    }

    private double averageRevenueYoY(List<MonthlyRevenueVO> revenues, int count) {
        double total = 0D;
        for (int i = 0; i < count; i++) {
            total += revenues.get(i).getYearOverYearPct();
        }
        return count == 0 ? 0D : total / count;
    }

    private int countPositiveRevenueMonths(List<MonthlyRevenueVO> revenues, int count) {
        int positiveCount = 0;
        for (int i = 0; i < count; i++) {
            if (revenues.get(i).getYearOverYearPct() > 0D) {
                positiveCount++;
            }
        }
        return positiveCount;
    }

    private long sumInstitutionalNet(List<InstitutionalTradingDailyVO> daily, int count) {
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += daily.get(i).getTotalNetLots();
        }
        return total;
    }

    private long sumVolume(List<InstitutionalTradingDailyVO> daily, int count) {
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += daily.get(i).getVolume();
        }
        return total;
    }

    private double sumTrailingEps(List<EpsRecordVO> epsRecords, int count) {
        double total = 0D;
        for (int i = 0; i < count; i++) {
            total += epsRecords.get(i).getEps();
        }
        return total;
    }

    private int countPositiveEpsQuarters(List<EpsRecordVO> epsRecords, int count) {
        int positiveCount = 0;
        for (int i = 0; i < count; i++) {
            if (epsRecords.get(i).getEps() > 0D) {
                positiveCount++;
            }
        }
        return positiveCount;
    }

    private int countPositiveOperatingCashFlowQuarters(List<CashFlowRecordVO> cashFlowRecords, int count) {
        int positiveCount = 0;
        for (int i = 0; i < count; i++) {
            if (cashFlowRecords.get(i).getOperatingCashFlow() > 0L) {
                positiveCount++;
            }
        }
        return positiveCount;
    }

    private int countPositiveFreeCashFlowQuarters(List<CashFlowRecordVO> cashFlowRecords, int count) {
        int positiveCount = 0;
        for (int i = 0; i < count; i++) {
            if (cashFlowRecords.get(i).getFreeCashFlow() > 0L) {
                positiveCount++;
            }
        }
        return positiveCount;
    }

    private void applyPostCloseDecisionProfile(StockAnalysisResultVO result) {
        String category = POST_CLOSE_STAND_ASIDE;
        if (isHighConvictionCandidate(result)) {
            category = POST_CLOSE_HIGH_CONVICTION;
        } else if (isMomentumAttackCandidate(result)) {
            category = POST_CLOSE_MOMENTUM_ATTACK;
        } else if (isSwingPositionCandidate(result)) {
            category = POST_CLOSE_SWING_POSITION;
        } else if (isCatalystWatchCandidate(result)) {
            category = POST_CLOSE_CATALYST_WATCH;
        } else if (result.isSelectionQualified() && result.getSelectionScore() >= WATCHLIST_THRESHOLD) {
            category = POST_CLOSE_GENERAL_WATCH;
        }
        result.setDataQualityGrade(resolveDataQualityGrade(result));
        result.setHardExclude(false);
        result.setHardExcludeReason("");
        category = applyHardExclusionIfNeeded(result, category);
        category = applyFreshnessGateIfNeeded(result, category);
        category = applyFakeBreakoutSuppressionIfNeeded(result, category);
        result.setPostCloseCategory(category);
        applySignalProfile(result, category);
        result.setPostCloseAction(resolvePostCloseAction(category));
        result.setPostClosePriorityScore(computePostClosePriorityScore(result, category));
        result.setPostCloseReason(buildPostCloseReason(result, category));
    }

    private void applySignalProfile(StockAnalysisResultVO result, String category) {
        String signalType = SIGNAL_PENDING;
        int horizonDays = 0;
        if (POST_CLOSE_MOMENTUM_ATTACK.equals(category)) {
            signalType = SIGNAL_NEXT_DAY_CONTINUATION;
            horizonDays = 1;
        } else if (POST_CLOSE_HIGH_CONVICTION.equals(category)) {
            signalType = SIGNAL_MULTI_DAY_CONTINUATION;
            horizonDays = 3;
        } else if (POST_CLOSE_CATALYST_WATCH.equals(category)) {
            signalType = SIGNAL_MULTI_DAY_CONTINUATION;
            horizonDays = 5;
        } else if (POST_CLOSE_SWING_POSITION.equals(category)) {
            signalType = SIGNAL_SWING_WINDOW;
            horizonDays = 10;
        } else if (POST_CLOSE_GENERAL_WATCH.equals(category)) {
            signalType = SIGNAL_PENDING;
            horizonDays = 3;
        }
        result.setSignalType(signalType);
        result.setSignalHorizonDays(horizonDays);
        result.setEntryRule(ENTRY_RULE_NEXT_CLOSE);
        result.setExitRule(EXIT_RULE_STOP_TARGET_OR_HORIZON);
        result.setValidationMode(VALIDATION_MODE_DAILY_CLOSE);
    }

    private String applyHardExclusionIfNeeded(StockAnalysisResultVO result, String category) {
        String reason = resolveHardExcludeReason(result, category);
        if (reason.length() == 0) {
            return category;
        }
        result.setHardExclude(true);
        result.setHardExcludeReason(reason);
        if (POST_CLOSE_HIGH_CONVICTION.equals(category) || POST_CLOSE_MOMENTUM_ATTACK.equals(category)
                || POST_CLOSE_SWING_POSITION.equals(category)) {
            if (result.isSelectionQualified() && result.getSelectionScore() >= WATCHLIST_THRESHOLD) {
                return POST_CLOSE_CATALYST_WATCH;
            }
            return POST_CLOSE_GENERAL_WATCH;
        }
        if (POST_CLOSE_GENERAL_WATCH.equals(category) && result.getSelectionScore() < WATCHLIST_THRESHOLD) {
            return POST_CLOSE_STAND_ASIDE;
        }
        return category;
    }

    private String applyFreshnessGateIfNeeded(StockAnalysisResultVO result, String category) {
        if (!POST_CLOSE_MOMENTUM_ATTACK.equals(category)) {
            return category;
        }
        if (hasShortTermFreshCatalyst(result)) {
            return category;
        }
        result.setHardExclude(true);
        result.setHardExcludeReason(appendReason(result.getHardExcludeReason(), "短線新鮮度不足"));
        return POST_CLOSE_CATALYST_WATCH;
    }

    private String applyFakeBreakoutSuppressionIfNeeded(StockAnalysisResultVO result, String category) {
        String suppressionReason = resolveFakeBreakoutSuppressionReason(result);
        if (suppressionReason.length() == 0) {
            return category;
        }
        if (POST_CLOSE_STAND_ASIDE.equals(category) || POST_CLOSE_GENERAL_WATCH.equals(category)
                || POST_CLOSE_CATALYST_WATCH.equals(category)) {
            return category;
        }
        result.setHardExclude(true);
        result.setHardExcludeReason(appendReason(result.getHardExcludeReason(), suppressionReason));
        if (POST_CLOSE_MOMENTUM_ATTACK.equals(category) || POST_CLOSE_HIGH_CONVICTION.equals(category)) {
            return POST_CLOSE_CATALYST_WATCH;
        }
        return POST_CLOSE_GENERAL_WATCH;
    }

    private boolean hasShortTermFreshCatalyst(StockAnalysisResultVO result) {
        if (result.getNewsOfficialSourceCount() >= 1) {
            return true;
        }
        if ("正向催化".equals(result.getEventDirection()) && result.getEventFreshnessDays() <= 2) {
            return true;
        }
        return result.getNewsFreshnessScore() >= 70D;
    }

    private String resolveFakeBreakoutSuppressionReason(StockAnalysisResultVO result) {
        List<String> reasons = new ArrayList<String>();
        boolean nearHigh = result.getDrawdownFromHigh60Pct() >= -3D;
        if (nearHigh && result.getVolumeRatio() >= 2.8D && result.getRiskRewardRatio() > 0D
                && result.getRiskRewardRatio() < 1.3D) {
            reasons.add("接近前高但量比過熱且風報差");
        }
        if (result.getNewsScore() >= 70D && result.getFiveDayInstitutionalNetLots() <= 0L) {
            reasons.add("新聞熱但法人不站買方");
        }
        if (hasValue(result.getNonOperatingRatioPct()) && result.getNonOperatingRatioPct() >= 40D
                && "正向催化".equals(result.getEventDirection()) && result.getNewsSourceCount() <= 1) {
            reasons.add("單次題材且非營業依賴偏高");
        }
        return joinReasons(reasons);
    }

    private String appendReason(String base, String addition) {
        if (addition == null || addition.length() == 0) {
            return base == null ? "" : base;
        }
        if (base == null || base.length() == 0) {
            return addition;
        }
        if (base.contains(addition)) {
            return base;
        }
        return base + "；" + addition;
    }

    private String resolveHardExcludeReason(StockAnalysisResultVO result, String category) {
        List<String> reasons = new ArrayList<String>();
        if ("C".equals(result.getDataQualityGrade()) || "D".equals(result.getDataQualityGrade())) {
            reasons.add("資料品質 " + result.getDataQualityGrade());
        }
        if (result.getDataConfidence() > 0D && result.getDataConfidence() < 70D) {
            reasons.add("資料信心不足");
        }
        if ("追高風險".equals(result.getStructureLabel())) {
            reasons.add("結構屬追高風險");
        }
        if (result.getNewsRiskScore() >= 70D) {
            reasons.add("新聞風險偏高");
        }
        if (hasValue(result.getNonOperatingRatioPct()) && result.getNonOperatingRatioPct() >= 50D) {
            reasons.add("非營業依賴過高");
        }
        if (hasValue(result.getDebtRatioPct()) && result.getDebtRatioPct() > 75D
                && result.getLatestOperatingCashFlow() < 0L) {
            reasons.add("高負債且現金流為負");
        }
        if (result.getVolumeRatio() >= 3.2D) {
            reasons.add("量比過熱");
        }
        if ("負向風險".equals(result.getEventDirection()) && result.getEventConfidence() >= 55D) {
            reasons.add("事件偏負向");
        }
        if (POST_CLOSE_MOMENTUM_ATTACK.equals(category) && result.getNewsOfficialSourceCount() <= 0
                && result.getNewsFreshnessScore() > 0D && result.getNewsFreshnessScore() < 45D) {
            reasons.add("短線催化不夠新");
        }
        return joinReasons(reasons);
    }

    private String resolveDataQualityGrade(StockAnalysisResultVO result) {
        double confidence = result.getDataConfidence();
        if (confidence >= 85D) {
            return "A";
        }
        if (confidence >= 70D) {
            return "B";
        }
        if (confidence >= 55D) {
            return "C";
        }
        return "D";
    }

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "";
        }
        return join(reasons, "；");
    }

    private boolean isHighConvictionCandidate(StockAnalysisResultVO result) {
        return result.isSelectionQualified() && result.getDataConfidence() >= 65D
                && result.getQualityScore() >= HIGH_CONVICTION_QUALITY_SCORE
                && result.getTrendPersistenceScore() >= HIGH_CONVICTION_TREND_SCORE
                && result.getStructureScore() >= HIGH_CONVICTION_STRUCTURE_SCORE
                && result.getRiskRewardScore() >= HIGH_CONVICTION_RISK_REWARD_SCORE
                && result.getBuyPointScore() >= activeBuyPointThreshold() && result.getEventRiskPenalty() <= 2.5D
                && result.getNewsRiskScore() <= 68D && !isStructureRisky(result)
                && result.getVolumeRatio() >= scoringConfig.getQualification().healthyVolumeMin
                && result.getVolumeRatio() <= 2.8D;
    }

    private boolean isMomentumAttackCandidate(StockAnalysisResultVO result) {
        return result.isSelectionQualified() && result.getSelectionScore() >= MOMENTUM_ATTACK_SELECTION_SCORE
                && result.getMomentumScore() >= MOMENTUM_ATTACK_SCORE && result.getBuyPointScore() >= 72D
                && result.getCurrentPrice() > result.getMovingAverage20()
                && result.getCurrentPrice() > result.getMovingAverage60()
                && result.getVolumeRatio() >= 1.0D && result.getVolumeRatio() <= 3.2D
                && result.getReturn20DayPct() >= 0D && result.getReturn20DayPct() <= 25D
                && result.getNewsRiskScore() <= 72D && result.getEventRiskPenalty() <= 3D
                && result.getRelativeStrengthScore() >= 55D
                && !"負向風險".equals(result.getEventDirection())
                && !isStructureRisky(result) && hasCatalystSignal(result);
    }

    private boolean isSwingPositionCandidate(StockAnalysisResultVO result) {
        boolean cleanNonOperating = !hasValue(result.getNonOperatingRatioPct()) || result.getNonOperatingRatioPct() <= 25D;
        return result.isSelectionQualified() && result.getQualityScore() >= SWING_QUALITY_SCORE
                && result.getFinancialQualityScore() >= activeLikelyMinFinancialScore()
                && result.getBuyPointScore() >= 68D && result.getRiskRewardRatio() >= SWING_RISK_REWARD_RATIO
                && result.getTrailingFourQuarterEps() > 0D && result.getLatestFreeCashFlow() > 0L
                && result.getEventRiskPenalty() <= 2.5D && result.getNewsRiskScore() <= 70D
                && !"結構未完成".equals(result.getStructureLabel()) && cleanNonOperating
                && !"負向風險".equals(result.getEventDirection());
    }

    private boolean isCatalystWatchCandidate(StockAnalysisResultVO result) {
        boolean catalystReady = hasCatalystSignal(result) || result.getTurnaroundScore() >= CATALYST_WATCH_SCORE
                || result.getRevenueGrowthSignalScore() >= 65D;
        boolean timingNotReady = "追高風險".equals(result.getStructureLabel()) || result.getBuyPointScore() < 72D
                || result.getRiskRewardRatio() < 1.3D || result.getVolumeRatio() > 2.8D
                || result.getNewsRiskScore() > 65D || result.getFinancialQualityScore() < activeLikelyMinFinancialScore();
        return result.isSelectionQualified() && result.getSelectionScore() >= WATCHLIST_THRESHOLD && catalystReady
                && timingNotReady;
    }

    private boolean hasCatalystSignal(StockAnalysisResultVO result) {
        return result.getThemeScore() >= 60D || result.getNewsScore() >= 65D
                || result.getThemeReferenceScore() >= 62D || result.getMarketThemeReferenceScore() >= 60D
                || result.getTurnaroundScore() >= CATALYST_WATCH_SCORE;
    }

    private boolean isStructureRisky(StockAnalysisResultVO result) {
        return "追高風險".equals(result.getStructureLabel()) || "結構未完成".equals(result.getStructureLabel());
    }

    private String resolvePostCloseAction(String category) {
        if (POST_CLOSE_HIGH_CONVICTION.equals(category)) {
            return "優先研究";
        }
        if (POST_CLOSE_MOMENTUM_ATTACK.equals(category)) {
            return "隔日看續強";
        }
        if (POST_CLOSE_SWING_POSITION.equals(category)) {
            return "可分批布局";
        }
        if (POST_CLOSE_CATALYST_WATCH.equals(category)) {
            return "只觀察不追";
        }
        if (POST_CLOSE_GENERAL_WATCH.equals(category)) {
            return "放進觀察名單";
        }
        return "暫不出手";
    }

    private double computePostClosePriorityScore(StockAnalysisResultVO result, String category) {
        double score = result.getSelectionScore() * 0.28D + result.getBuyPointScore() * 0.22D
                + result.getQualityScore() * 0.18D + result.getMomentumScore() * 0.10D
                + result.getTrendPersistenceScore() * 0.10D + result.getRiskRewardScore() * 0.08D
                + result.getDataConfidence() * 0.04D;
        score += Math.max(0D, result.getRelativeStrengthScore() - 50D) * 0.10D;
        if (POST_CLOSE_HIGH_CONVICTION.equals(category)) {
            score += 8D;
        } else if (POST_CLOSE_MOMENTUM_ATTACK.equals(category)) {
            score += 5D + Math.min(6D, Math.max(result.getThemeScore(), result.getNewsScore()) * 0.06D);
        } else if (POST_CLOSE_SWING_POSITION.equals(category)) {
            score += 4D + result.getQualityScore() * 0.04D;
        } else if (POST_CLOSE_CATALYST_WATCH.equals(category)) {
            score += 2D + Math.min(4D, result.getTurnaroundScore() * 0.04D);
        } else if (POST_CLOSE_STAND_ASIDE.equals(category)) {
            score -= 8D;
        }
        if ("正向催化".equals(result.getEventDirection())) {
            score += Math.min(5D, result.getEventConfidence() * 0.05D);
        } else if ("負向風險".equals(result.getEventDirection())) {
            score -= Math.min(7D, result.getEventConfidence() * 0.07D);
        }
        if (result.isHardExclude()) {
            score -= 12D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private String buildPostCloseReason(StockAnalysisResultVO result, String category) {
        List<String> parts = new ArrayList<String>();
        parts.add(category + "：" + resolvePostCloseAction(category));
        if (result.getSignalType() != null && result.getSignalType().length() > 0) {
            parts.add("訊號 " + result.getSignalType()
                    + (result.getSignalHorizonDays() > 0 ? " / " + result.getSignalHorizonDays() + " 日" : ""));
        }
        if (result.getDataQualityGrade() != null && result.getDataQualityGrade().length() > 0) {
            parts.add("資料品質 " + result.getDataQualityGrade());
        }
        if (result.getRelativeStrengthScore() > 0D) {
            parts.add("相對強勢 " + format(result.getRelativeStrengthScore()));
        }
        if (result.getEventDirection() != null && result.getEventDirection().length() > 0
                && !"中性待確認".equals(result.getEventDirection())) {
            parts.add(result.getEventDirection() + " / 信心 " + format(result.getEventConfidence())
                    + (result.getEventFreshnessDays() < 999 ? " / 新鮮 " + result.getEventFreshnessDays() + " 天" : ""));
        }
        if (result.getBacktestCohort() != null && result.getBacktestCohort().length() > 0
                && !"N/A".equals(result.getBacktestCohort())) {
            parts.add("回測 cohort " + result.getBacktestCohort() + " / 勝率 "
                    + format(result.getWinratePriorityScore()) + " / 報酬 "
                    + format(result.getExpectedReturnScore()));
        }
        if (result.isHardExclude() && result.getHardExcludeReason() != null
                && result.getHardExcludeReason().length() > 0) {
            parts.add("降級原因 " + result.getHardExcludeReason());
        }
        if (POST_CLOSE_HIGH_CONVICTION.equals(category)) {
            parts.add("符合回測高勝率框架");
            parts.add("品質 " + format(result.getQualityScore()) + " / 續航 "
                    + format(result.getTrendPersistenceScore()) + " / 結構 "
                    + format(result.getStructureScore()) + " / 風報 "
                    + format(result.getRiskRewardScore()));
        } else if (POST_CLOSE_MOMENTUM_ATTACK.equals(category)) {
            parts.add("動能 " + format(result.getMomentumScore()) + "、策略 " + format(result.getSelectionScore())
                    + "、買點 " + format(result.getBuyPointScore()));
            parts.add("題材 " + emptyIfBlank(result.getPrimaryTheme(), "一般") + " "
                    + format(result.getThemeScore()) + " / 新聞 " + format(result.getNewsScore()));
        } else if (POST_CLOSE_SWING_POSITION.equals(category)) {
            parts.add("品質 " + format(result.getQualityScore()) + "、財報 "
                    + format(result.getFinancialQualityScore()) + "、風報比 "
                    + format(result.getRiskRewardRatio()));
            parts.add("近四季 EPS " + format(result.getTrailingFourQuarterEps()) + "、自由現金流 "
                    + formatLots(result.getLatestFreeCashFlow()));
        } else if (POST_CLOSE_CATALYST_WATCH.equals(category)) {
            parts.add("催化有，但時機還沒漂亮");
            parts.add("翻轉 " + format(result.getTurnaroundScore()) + " / 題材 "
                    + format(result.getThemeScore()) + " / 風報比 "
                    + format(result.getRiskRewardRatio()));
        } else if (POST_CLOSE_GENERAL_WATCH.equals(category)) {
            parts.add("分數已進觀察區，但還不夠集中");
        } else {
            parts.add("目前沒有形成收盤後優先名單");
        }
        if (result.getStructureLabel() != null && result.getStructureLabel().length() > 0) {
            parts.add("結構：" + result.getStructureLabel());
        }
        if (result.getEventRiskPenalty() > 0D) {
            parts.add("事件風險 " + format(result.getEventRiskPenalty()));
        }
        return join(parts, "；");
    }

    private String buildAnalysisNote(StockAnalysisResultVO result) {
        List<String> notes = new ArrayList<String>();

        if (result.getPostCloseCategory() != null && result.getPostCloseCategory().length() > 0) {
            notes.add(result.getPostCloseCategory() + " / " + emptyIfBlank(result.getPostCloseAction(), "觀察"));
        }
        if (result.getSignalType() != null && result.getSignalType().length() > 0) {
            notes.add("訊號 " + result.getSignalType()
                    + (result.getSignalHorizonDays() > 0 ? " " + result.getSignalHorizonDays() + " 日" : ""));
        }
        if (result.getDataQualityGrade() != null && result.getDataQualityGrade().length() > 0) {
            notes.add("資料品質 " + result.getDataQualityGrade());
        }
        if (result.isHardExclude() && result.getHardExcludeReason() != null
                && result.getHardExcludeReason().length() > 0) {
            notes.add("已降級 " + result.getHardExcludeReason());
        }
        if (result.getRelativeStrengthScore() >= 60D) {
            notes.add("族群相對強勢");
        } else if (result.getRelativeStrengthScore() > 0D && result.getRelativeStrengthScore() <= 40D) {
            notes.add("族群相對落後");
        }
        if (result.getEventDirection() != null && result.getEventDirection().length() > 0
                && !"中性待確認".equals(result.getEventDirection())) {
            notes.add(result.getEventDirection());
        }
        if (result.getBacktestCohort() != null && result.getBacktestCohort().length() > 0
                && !"N/A".equals(result.getBacktestCohort())) {
            notes.add("回測 " + result.getBacktestCohort());
        }

        if (result.getAverageThreeMonthRevenueYoY() > 5D) {
            notes.add("營收動能轉強");
        } else if (result.getAverageThreeMonthRevenueYoY() <= 0D) {
            notes.add("營收動能偏弱");
        }

        if (result.getFiveDayInstitutionalNetLots() > 0L) {
            notes.add("法人偏多");
        } else {
            notes.add("法人不站買方");
        }

        if (result.getTrailingPe() > 0D && result.getPeerAveragePe() > 0D
                && result.getTrailingPe() <= result.getPeerAveragePe()) {
            notes.add("估值低於同業均值");
        } else if (result.getTrailingFourQuarterEps() <= 0D) {
            notes.add("近四季虧損");
        } else if (result.getPeerAveragePe() > 0D && result.getTrailingPe() > result.getPeerAveragePe() * 1.3D) {
            notes.add("本益比高於同業均值");
        }

        if (result.getCurrentPrice() > result.getMovingAverage20() && result.getMovingAverage20() > result.getMovingAverage60()
                && result.getMovingAverage60() > result.getMovingAverage120()) {
            notes.add("長中短均線多頭排列");
        } else if (result.getCurrentPrice() < result.getMovingAverage20()) {
            notes.add("跌破 MA20");
        }

        if (result.getLiquidityScore() < 4D) {
            notes.add("流動性不足");
        }

        if (!result.isSelectionQualified()) {
            notes.add("未達策略入選門檻");
        } else if (isLikelyCandidate(result)) {
            notes.add("策略分達標");
        }

        if (result.getSectorScore() >= 65D) {
            notes.add("族群強度偏強");
        } else if (result.getSectorScore() <= 40D) {
            notes.add("族群偏弱");
        }

        if (result.getPrimaryTheme() != null && result.getPrimaryTheme().length() > 0
                && !"一般".equals(result.getPrimaryTheme()) && result.getThemeScore() >= 60D) {
            notes.add("題材聚焦 " + result.getPrimaryTheme());
        }

        if (result.getTurnaroundScore() >= 75D) {
            notes.add(result.getTurnaroundLabel());
        } else if (result.getTurnaroundScore() >= 55D) {
            notes.add("翻轉觀察");
        }
        if (result.getOneOffRiskScore() >= 60D) {
            notes.add("一次性轉盈風險");
        }

        if (result.getTrendPersistenceScore() >= 70D) {
            notes.add("趨勢持續性佳");
        } else if (result.getTrendPersistenceDays() >= 2) {
            notes.add("高分延續 " + result.getTrendPersistenceDays() + " 天");
        }

        if (result.getStructureLabel() != null && result.getStructureLabel().length() > 0) {
            notes.add(result.getStructureLabel());
        }

        if (result.getBuyPointScore() >= activeBuyPointThreshold()) {
            notes.add("買點條件佳");
        } else if (result.getBuyPointScore() >= 60D) {
            notes.add("可觀察買點");
        }

        if (result.getRiskRewardRatio() >= 2D) {
            notes.add("風報比佳");
        } else if (result.getSuggestedStopPct() > 10D) {
            notes.add("停損距離偏大");
        }

        if (result.getPositiveEpsQuarters() >= 3 && result.getPositiveOperatingCashFlowQuarters() >= 3
                && hasValue(result.getNonOperatingRatioPct()) && result.getNonOperatingRatioPct() <= 15D) {
            notes.add("獲利品質穩健");
        } else if (result.getTrailingFourQuarterEps() <= 0D || result.getLatestFreeCashFlow() <= 0L
                || (hasValue(result.getNonOperatingRatioPct()) && result.getNonOperatingRatioPct() > 30D)) {
            notes.add("獲利品質普通");
        }

        if (result.getEventRiskPenalty() > 0D) {
            notes.add("近期有事件風險");
        }

        if (result.getNewsScore() >= 65D) {
            notes.add("新聞熱度偏高");
        }
        if (result.getNewsSourceCount() >= 2) {
            notes.add("新聞多來源");
        }
        if (result.getLatestNewsPublishedHint() != null && result.getLatestNewsPublishedHint().length() > 0) {
            notes.add("最新新聞 " + result.getLatestNewsPublishedHint());
        }
        if (result.getNewsRiskScore() >= 65D) {
            notes.add("新聞風險偏高");
        }
        if (result.getThemeReferenceScore() >= 62D && result.getThemeReferenceTheme() != null
                && result.getThemeReferenceTheme().length() > 0) {
            notes.add("新聞題材線索 " + result.getThemeReferenceTheme());
        }
        if (result.getMarketThemeReferenceScore() >= 60D && result.getMarketThemeReferenceTheme() != null
                && result.getMarketThemeReferenceTheme().length() > 0) {
            notes.add("市場題材參考 " + result.getMarketThemeReferenceTheme());
        }

        return join(notes, "; ");
    }

    private String buildScoreReason(StockAnalysisResultVO result) {
        double baseSelectionScore = scoreSelectionProfile(result.getRawScore(), result.getQualityScore(),
                result.getMomentumScore(), result.getVolumeRatio(), result.getEventRiskPenalty(),
                result.isSelectionQualified());
        double baseBuyPointScore = scoreBuyPointProfile(result.getSelectionScore(), result.getMomentumScore(),
                result.getQualityScore(), result.getCurrentPrice(), result.getMovingAverage20(),
                result.getMovingAverage60(), result.getMovingAverage120(), result.getReturn20DayPct(),
                result.getVolumeRatio(), result.getDrawdownFromHigh60Pct(), result.getRsi14(),
                result.getStochasticK(), result.getStochasticD(), result.getEventRiskPenalty(),
                result.isSelectionQualified(), result.getFinancialQualityScore());
        String reason = String.format(
                "策略分 %.2f = 基礎策略 %.1f + 趨勢續航 %.1f + 族群強度 %.1f + 校正 5.0 - 新聞風險 %.1f；買點分 %.2f = 基礎買點 %.1f + 結構 %.1f + 續航 %.1f + 風報 %.1f + 族群 %.1f + 新聞 %.1f；翻轉分 %.1f = 營收成長 %.1f + 業績翻轉 %.1f + 轉盈 %.1f - 一次性風險 %.1f；傳統總分 %.2f = 營收 %.1f + 籌碼 %.1f + 流動性 %.1f + 估值 %.1f + 技術 %.1f + 財報 %.1f - 事件風險 %.1f；題材顯示：%s %.1f（僅供參考，不進總分）；新聞參考：%s；資格：%s",
                Double.valueOf(result.getSelectionScore()), Double.valueOf(baseSelectionScore),
                Double.valueOf(result.getTrendPersistenceScore()), Double.valueOf(result.getSectorScore()),
                Double.valueOf(result.getNewsRiskScore()),
                Double.valueOf(result.getBuyPointScore()), Double.valueOf(baseBuyPointScore),
                Double.valueOf(result.getStructureScore()), Double.valueOf(result.getTrendPersistenceScore()),
                Double.valueOf(result.getRiskRewardScore()), Double.valueOf(result.getSectorScore()),
                Double.valueOf(result.getNewsScore()), Double.valueOf(result.getTurnaroundScore()),
                Double.valueOf(result.getRevenueGrowthSignalScore()),
                Double.valueOf(result.getEarningsTurnaroundSignalScore()),
                Double.valueOf(result.getProfitabilityTurnaroundSignalScore()),
                Double.valueOf(result.getOneOffRiskScore()), Double.valueOf(result.getScore()),
                Double.valueOf(result.getRevenueScore()), Double.valueOf(result.getChipsScore()),
                Double.valueOf(result.getLiquidityScore()), Double.valueOf(result.getValuationScore()),
                Double.valueOf(result.getTechnicalScore()), Double.valueOf(result.getFinancialQualityScore()),
                Double.valueOf(result.getEventRiskPenalty()), emptyIfBlank(result.getPrimaryTheme(), "一般"),
                Double.valueOf(result.getThemeScore()),
                emptyIfBlank(result.getThemeReferenceReason(), "無")
                        + (result.getMarketThemeReferenceScore() >= 60D && result.getMarketThemeReferenceTheme() != null
                                && result.getMarketThemeReferenceTheme().length() > 0
                                        ? "；市場題材 " + result.getMarketThemeReferenceTheme() + " "
                                                + format(result.getMarketThemeReferenceScore()) + "（僅供參考）"
                                        : ""),
                result.getEligibilityReason());
        if (result.getPostCloseCategory() != null && result.getPostCloseCategory().length() > 0) {
            reason += "；收盤後分類：" + result.getPostCloseCategory() + " / "
                    + emptyIfBlank(result.getPostCloseAction(), "觀察");
        }
        if (result.getRelativeStrengthScore() > 0D) {
            reason += "；相對強勢 " + format(result.getRelativeStrengthScore()) + "（報酬 "
                    + format(result.getIndustryReturnStrength()) + " / 量比 "
                    + format(result.getIndustryVolumeStrength()) + " / 法人 "
                    + format(result.getIndustryFlowStrength()) + "）";
        }
        if (result.getEventDirection() != null && result.getEventDirection().length() > 0) {
            reason += "；事件訊號：" + result.getEventDirection() + " / 信心 "
                    + format(result.getEventConfidence()) + " / 類型 "
                    + emptyIfBlank(result.getEventTypeSummary(), "未分類");
        }
        return reason;
    }

    private String buildRevenueReason(StockAnalysisResultVO result) {
        return "最新月營收年增 " + format(result.getLatestRevenueYoY()) + "%，近 3 月平均年增 "
                + format(result.getAverageThreeMonthRevenueYoY()) + "%，累計年增 "
                + format(result.getAccumulatedRevenueYoY()) + "%，近 3 月正成長 "
                + result.getPositiveRevenueMonths() + " 個月";
    }

    private String buildChipsReason(StockAnalysisResultVO result) {
        return "5 日法人買賣超 " + formatLots(result.getFiveDayInstitutionalNetLots()) + " 張，占比 "
                + format(result.getFiveDayInstitutionalNetRatioPct()) + "%；單日法人 "
                + formatLots(result.getLatestInstitutionalNetLots()) + " 張，占比 "
                + format(result.getLatestInstitutionalNetRatioPct()) + "%；外資 "
                + formatLots(result.getLatestForeignNetLots()) + " 張；主力 "
                + formatLots(result.getBrokerNetLots()) + " 張，占比 " + format(result.getBrokerNetRatioPct()) + "%";
    }

    private String buildLiquidityReason(StockAnalysisResultVO result) {
        return "近 20 日平均成交張數 " + format(result.getAverageLots20()) + "，平均成交金額 "
                + format(result.getAverageTradeValue20Billion()) + " 億元";
    }

    private String buildValuationReason(StockAnalysisResultVO result) {
        if (result.getTrailingFourQuarterEps() <= 0D) {
            return "近四季 EPS " + format(result.getTrailingFourQuarterEps()) + "，目前不給本益比分數";
        }
        String nonOperatingText = hasValue(result.getNonOperatingRatioPct()) ? format(result.getNonOperatingRatioPct()) + "%"
                : "資料不足";
        String pegText = result.getPeg() > 0D ? "，PEG " + format(result.getPeg()) : "";
        String accelText = result.getEpsAccelerationPct() != 0D
                ? "，EPS 加速 " + (result.getEpsAccelerationPct() > 0D ? "+" : "") + format(result.getEpsAccelerationPct()) + "%"
                : "";
        if (result.getPeerAveragePe() > 0D) {
            return "近四季 EPS " + format(result.getTrailingFourQuarterEps()) + "，本益比 "
                    + format(result.getTrailingPe()) + " 倍，同業平均 " + format(result.getPeerAveragePe())
                    + " 倍" + pegText + accelText + "，非營業依賴 " + nonOperatingText;
        }
        return "近四季 EPS " + format(result.getTrailingFourQuarterEps()) + "，本益比 "
                + format(result.getTrailingPe()) + " 倍" + pegText + accelText + "，非營業依賴 " + nonOperatingText;
    }

    private String buildTechnicalReason(StockAnalysisResultVO result) {
        return "現價 " + format(result.getCurrentPrice()) + "，MA20 " + format(result.getMovingAverage20())
                + "，MA60 " + format(result.getMovingAverage60()) + "，MA120 "
                + format(result.getMovingAverage120()) + "，20 日報酬 " + format(result.getReturn20DayPct())
                + "%，60 日報酬 " + format(result.getReturn60DayPct()) + "%，量比 " + format(result.getVolumeRatio())
                + "，波動度 " + format(result.getVolatility20Pct()) + "%，距 60 日高點 "
                + format(result.getDrawdownFromHigh60Pct()) + "%，RSI14 " + format(result.getRsi14())
                + "，K " + format(result.getStochasticK()) + "，D " + format(result.getStochasticD());
    }

    private String buildFinancialQualityReason(StockAnalysisResultVO result) {
        String debtRatioText = hasValue(result.getDebtRatioPct()) ? format(result.getDebtRatioPct()) + "%" : "資料不足";
        String currentRatioText = hasValue(result.getCurrentRatio()) ? format(result.getCurrentRatio()) : "資料不足";
        String accelText = result.getEpsAccelerationPct() != 0D
                ? "，EPS加速 " + (result.getEpsAccelerationPct() > 0D ? "+" : "") + format(result.getEpsAccelerationPct()) + "%"
                : "";
        return "最新季 EPS " + format(result.getLatestQuarterEps()) + "，年增 "
                + format(result.getLatestQuarterEpsYoYPct()) + "%" + accelText + "，近四季正 EPS "
                + result.getPositiveEpsQuarters() + " 季，營業現金流 "
                + formatLots(result.getLatestOperatingCashFlow()) + "，自由現金流 "
                + formatLots(result.getLatestFreeCashFlow()) + "，毛利率 "
                + format(result.getGrossMarginPct()) + "%，營益率 " + format(result.getOperatingMarginPct())
                + "%，ROA " + format(result.getReturnOnAssetsPct()) + "%，ROE "
                + format(result.getReturnOnEquityPct()) + "%，負債比 " + debtRatioText
                + "，流動比 " + currentRatioText;
    }

    private String resolveBuyPointLabel(double buyPointScore, boolean selectionQualified, double financialQualityScore,
            double dataConfidence, double structureScore, double riskRewardScore, double selectionScore) {
        if (!selectionQualified) {
            return "不建議追";
        }
        if (dataConfidence > 0D && dataConfidence < 65D && buyPointScore >= 70D) {
            return "時機不錯，但資料信心不足";
        }
        if (financialQualityScore < activeLikelyMinFinancialScore() && buyPointScore < activeBuyPointThreshold()) {
            return "基本面可，但時機差";
        }
        if (buyPointScore >= 85D && structureScore >= 75D && riskRewardScore >= 60D) {
            return "A級時機";
        }
        if (buyPointScore >= activeBuyPointThreshold()) {
            return "可觀察，等確認";
        }
        if (selectionScore >= 75D) {
            return "基本面可，但時機差";
        }
        return "不建議追";
    }

    private String buildBuyPointReason(StockAnalysisResultVO result, double buyPointScore,
            StructureProfile structureProfile, RiskRewardProfile riskRewardProfile) {
        List<String> parts = new ArrayList<String>();
        parts.add("買點分 " + format(buyPointScore) + "，策略分 " + format(result.getSelectionScore()) + "，族群 "
                + format(result.getSectorScore()) + "，題材 " + emptyIfBlank(result.getPrimaryTheme(), "一般")
                + " " + format(result.getThemeScore()) + "，趨勢續航 "
                + resolveTrendPersistenceLabel(result.getTrendPersistenceScore(), result.getTrendPersistenceDays()) + " "
                + format(result.getTrendPersistenceScore()) + "，大盤狀態 "
                + emptyIfBlank(result.getMarketRegime(), "區間盤整") + "，翻轉 "
                + emptyIfBlank(result.getTurnaroundLabel(), "尚未明確") + " " + format(result.getTurnaroundScore()));
        parts.add("結構判讀：" + structureProfile.label + "（" + format(structureProfile.score) + " 分）");
        if (result.getCurrentPrice() > result.getMovingAverage20() && result.getCurrentPrice() > result.getMovingAverage60()) {
            parts.add("價位仍在 MA20/MA60 之上");
        } else if (result.getCurrentPrice() < result.getMovingAverage20()) {
            parts.add("現價仍在 MA20 下方，先等站回");
        }
        if (result.getDrawdownFromHigh60Pct() >= -10D && result.getDrawdownFromHigh60Pct() <= -3D) {
            parts.add("屬健康回踩區");
        } else if (result.getDrawdownFromHigh60Pct() > -3D) {
            parts.add("接近前高，適合等帶量確認");
        } else if (result.getDrawdownFromHigh60Pct() < -18D) {
            parts.add("離 60 日高點過遠，容易變弱勢反彈");
        }
        if (result.getVolumeRatio() >= 0.8D && result.getVolumeRatio() <= 1.8D) {
            parts.add("量比溫和，較像可持續買點");
        } else if (result.getVolumeRatio() > 3D) {
            parts.add("量比過熱，避免追高");
        }
        if (result.getRsi14() >= 48D && result.getRsi14() <= 65D) {
            parts.add("RSI 位於健康攻擊區");
        } else if (result.getRsi14() >= 78D) {
            parts.add("RSI 過熱");
        }
        if (result.getStochasticK() > result.getStochasticD()) {
            parts.add("KD 偏多");
        }
        if (result.getReturn20DayPct() > 25D) {
            parts.add("20 日漲幅已大，追價風險升高");
        }
        if (result.getTrendPersistenceDays() >= 2) {
            parts.add("連續高分 " + result.getTrendPersistenceDays() + " 天");
        } else if (result.getTrendPersistenceScore() < 45D) {
            parts.add("續航力仍待確認");
        } else {
            parts.add("續航狀態：" + resolveTrendPersistenceLabel(result.getTrendPersistenceScore(),
                    result.getTrendPersistenceDays()));
        }
        if (result.getNewsScore() >= 65D) {
            parts.add("新聞熱度偏高，可留意題材續航");
        }
        if (result.getNewsRiskScore() >= 65D) {
            parts.add("近期新聞/公告偏風險，部位要保守");
        }
        if (result.getTurnaroundScore() >= 70D) {
            parts.add("基本面有翻轉訊號");
        } else if (result.getRevenueGrowthSignalScore() >= 60D) {
            parts.add("營收成長有支撐");
        }
        if (result.getOneOffRiskScore() >= 60D) {
            parts.add("一次性轉盈風險偏高，先看持續性");
        }
        if (result.getNewsSummary() != null && result.getNewsSummary().length() > 0) {
            parts.add("新聞摘要：" + result.getNewsSummary());
        }
        if (result.getThemeReferenceScore() >= 62D && result.getThemeReferenceReason() != null
                && result.getThemeReferenceReason().length() > 0) {
            parts.add("新聞題材參考：" + result.getThemeReferenceReason());
        }
        if (result.getMarketThemeReferenceScore() >= 60D && result.getMarketThemeReferenceReason() != null
                && result.getMarketThemeReferenceReason().length() > 0) {
            parts.add("市場題材參考：" + result.getMarketThemeReferenceReason());
        }
        parts.add("停損參考 " + format(riskRewardProfile.stopLossPrice) + "（" + format(riskRewardProfile.stopLossPct)
                + "%），ATR 移動停利 " + format(riskRewardProfile.trailingStopPrice) + "，目標價 "
                + format(riskRewardProfile.targetPrice) + "，風報比 " + format(riskRewardProfile.riskRewardRatio)
                + "，賣出訊號 " + riskRewardProfile.sellSignalLabel + " " + format(riskRewardProfile.sellSignalScore));
        if (riskRewardProfile.reducePositionSize) {
            parts.add("波動偏大，建議縮小部位");
        }
        if (!result.isSelectionQualified()) {
            parts.add("尚未達流動性/財報品質基本門檻");
        }
        if (result.getFinancialQualityScore() < activeLikelyMinFinancialScore()) {
            parts.add("財報品質尚未達 Likely 強度");
        }
        if (result.getDataConfidence() > 0D && result.getDataConfidence() < 65D) {
            parts.add("資料完整度偏低，分數可信度需保留");
        }
        if (result.getEventRiskPenalty() > 0D) {
            parts.add("近期仍有事件風險扣分");
        }
        return join(parts, "；");
    }

    private String buildDataConfidenceReason(boolean hasProfileData, boolean hasEpsData, boolean hasCashFlowData,
            boolean hasIncomeData, boolean hasBalanceData, boolean hasBrokerData) {
        List<String> ok = new ArrayList<String>();
        List<String> miss = new ArrayList<String>();
        if (hasProfileData) {
            ok.add("基本資料");
        } else {
            miss.add("基本資料");
        }
        if (hasEpsData) {
            ok.add("EPS");
        } else {
            miss.add("EPS");
        }
        if (hasCashFlowData) {
            ok.add("現金流");
        } else {
            miss.add("現金流");
        }
        if (hasIncomeData) {
            ok.add("損益表");
        } else {
            miss.add("損益表");
        }
        if (hasBalanceData) {
            ok.add("資產負債表");
        } else {
            miss.add("資產負債表");
        }
        if (hasBrokerData) {
            ok.add("主力籌碼");
        } else {
            miss.add("主力籌碼");
        }
        String available = ok.isEmpty() ? "可用資料有限" : "可用：" + join(ok, "、");
        if (miss.isEmpty()) {
            return available + "；資料完整度高";
        }
        return available + "；缺少：" + join(miss, "、");
    }

    private String buildEligibilityReason(boolean selectionQualified, double liquidityScore, double financialQualityScore,
            double volumeRatio) {
        List<String> parts = new ArrayList<String>();
        if (selectionQualified) {
            parts.add("流動性與財報品質已達基本門檻");
        } else {
            if (liquidityScore < MIN_LIQUIDITY_SCORE) {
                parts.add("流動性分數不足 " + format(liquidityScore) + "/" + format(MIN_LIQUIDITY_SCORE));
            }
            if (financialQualityScore < MIN_SELECTION_FINANCIAL_SCORE) {
                parts.add("財報品質分數不足 " + format(financialQualityScore) + "/"
                        + format(MIN_SELECTION_FINANCIAL_SCORE));
            }
        }
        if (financialQualityScore < activeLikelyMinFinancialScore()) {
            parts.add("Likely 需財報品質 >= " + format(activeLikelyMinFinancialScore()));
        }
        if (!isVolumeRangeHealthy(volumeRatio)) {
            parts.add("Likely 偏好量比介於 " + format(LIKELY_MIN_VOLUME_RATIO) + " 到 "
                    + format(LIKELY_MAX_VOLUME_RATIO));
        }
        if (parts.isEmpty()) {
            parts.add("符合策略門檻");
        }
        return join(parts, "；");
    }

    private String joinMentionStocks(List<MarketThemeNewsAnalyzer.Mention> mentions) {
        List<String> labels = new ArrayList<String>();
        for (MarketThemeNewsAnalyzer.Mention mention : mentions) {
            labels.add(mention.code + " " + mention.name);
        }
        return join(labels, "、");
    }

    private String resolveTrendPersistenceLabel(double trendPersistenceScore, int trendPersistenceDays) {
        if (trendPersistenceScore >= 75D || trendPersistenceDays >= 4) {
            return "續航強";
        }
        if (trendPersistenceScore >= 60D || trendPersistenceDays >= 2) {
            return "續航中";
        }
        if (trendPersistenceScore >= 45D) {
            return "剛轉強";
        }
        if (trendPersistenceScore > 0D) {
            return "續航弱";
        }
        return "尚未建立";
    }

    private LatestSnapshotContext loadLatestSnapshotContext() {
        LatestSnapshotContext context = new LatestSnapshotContext();
        try {
            Map<String, StockHistoryDatabase.Snapshot> snapshots = historyDatabase.loadSnapshots();
            if (snapshots == null || snapshots.isEmpty()) {
                return context;
            }
            List<String> dates = new ArrayList<String>(snapshots.keySet());
            Collections.sort(dates);
            String latestDate = dates.get(dates.size() - 1);
            StockHistoryDatabase.Snapshot snapshot = snapshots.get(latestDate);
            if (snapshot == null) {
                return context;
            }
            context.date = latestDate;
            for (StockHistoryDatabase.SnapshotRow row : snapshot.rows) {
                if (row != null && row.code != null && row.code.length() > 0) {
                    context.rowsByCode.put(row.code, row);
                }
            }
        } catch (Exception ex) {
            return context;
        }
        return context;
    }

    private void hydrateNewsOnlyResult(StockAnalysisResultVO result, StockHistoryDatabase.SnapshotRow row) {
        result.setIndustry(row.industry);
        result.setAnalysisNote(row.note);
        result.setScore(row.score);
        result.setRawScore(row.rawScore);
        result.setSelectionScore(row.selectionScore);
        result.setMomentumScore(row.momentumScore);
        result.setQualityScore(row.qualityScore);
        result.setSectorScore(row.sectorScore);
        result.setThemeScore(row.themeScore);
        result.setTrendPersistenceScore(row.trendPersistenceScore);
        result.setTrendPersistenceDays(row.trendPersistenceDays);
        result.setStructureScore(row.structureScore);
        result.setStructureLabel(row.structureLabel);
        result.setRiskRewardScore(row.riskRewardScore);
        result.setRiskRewardRatio(row.riskRewardRatio);
        result.setTurnaroundScore(row.turnaroundScore);
        result.setRevenueGrowthSignalScore(row.revenueGrowthSignalScore);
        result.setEarningsTurnaroundSignalScore(row.earningsTurnaroundSignalScore);
        result.setProfitabilityTurnaroundSignalScore(row.profitabilityTurnaroundSignalScore);
        result.setOneOffRiskScore(row.oneOffRiskScore);
        result.setSuggestedStopPrice(row.suggestedStopPrice);
        result.setSuggestedStopPct(row.suggestedStopPct);
        result.setSuggestedTargetPrice(row.suggestedTargetPrice);
        result.setUpsidePotentialPct(row.upsidePotentialPct);
        result.setBuyPointScore(row.buyPointScore);
        result.setDataConfidence(row.dataConfidence);
        result.setSelectionQualified(row.selectionQualified);
        result.setCurrentPrice(row.price);
        result.setVolumeRatio(row.volumeRatio);
        result.setReturn20DayPct(row.return20DayPct);
        result.setReturn60DayPct(row.return60DayPct);
        result.setMovingAverage20(row.movingAverage20);
        result.setMovingAverage60(row.movingAverage60);
        result.setMovingAverage120(row.movingAverage120);
        result.setAverageLots20(row.averageLots20);
        result.setAverageTradeValue20Billion(row.averageTradeValue20Billion);
        result.setVolatility20Pct(row.volatility20Pct);
        result.setDrawdownFromHigh60Pct(row.drawdownFromHigh60Pct);
        result.setLiquidityScore(row.liquidityScore);
        result.setRevenueScore(row.revenueScore);
        result.setChipsScore(row.chipsScore);
        result.setValuationScore(row.valuationScore);
        result.setTechnicalScore(row.technicalScore);
        result.setFinancialQualityScore(row.financialQualityScore);
        result.setLatestInstitutionalNetLots(row.latestInstitutionalNetLots);
        result.setLatestInstitutionalNetRatioPct(row.latestInstitutionalNetRatioPct);
        result.setFiveDayInstitutionalNetLots(row.fiveDayInstitutionalNetLots);
        result.setFiveDayInstitutionalNetRatioPct(row.fiveDayInstitutionalNetRatioPct);
        result.setLatestForeignNetLots(row.latestForeignNetLots);
        result.setBrokerNetLots(row.brokerNetLots);
        result.setBrokerNetRatioPct(row.brokerNetRatioPct);
        result.setRsi14(row.rsi14);
        result.setStochasticK(row.stochasticK);
        result.setStochasticD(row.stochasticD);
        result.setMa20Slope(row.ma20Slope);
        result.setEpsAccelerationPct(row.epsAccelerationPct);
        result.setPeg(row.peg);
        result.setScoreReason(row.scoreReason);
        result.setRevenueReason(row.revenueReason);
        result.setChipsReason(row.chipsReason);
        result.setLiquidityReason(row.liquidityReason);
        result.setValuationReason(row.valuationReason);
        result.setTechnicalReason(row.technicalReason);
        result.setFinancialQualityReason(row.financialQualityReason);
        result.setEventRiskReason(row.eventRiskReason);
        result.setEligibilityReason(row.eligibilityReason);
        result.setPrimaryTheme(row.primaryTheme);
        result.setThemeTags(row.themeTags);
        result.setNewsSummary(row.newsSummary);
        result.setNewsDigest(row.newsDigest);
        result.setNewsSourceSummary(row.newsSourceSummary);
        result.setLatestNewsPublishedHint(row.latestNewsPublishedHint);
        result.setNewsSourceCredibilityScore(row.newsSourceCredibilityScore);
        result.setNewsFreshnessScore(row.newsFreshnessScore);
        result.setNewsSourceCount(row.newsSourceCount);
        result.setNewsOfficialSourceCount(row.newsOfficialSourceCount);
        result.setNewsMediaSourceCount(row.newsMediaSourceCount);
        result.setCompanySummary(row.companySummary);
        result.setRecentNewsBrief(row.recentNewsBrief);
        result.setTransformationHint(row.transformationHint);
        result.setPracticalAdvice(row.practicalAdvice);
        result.setAdviceConfidence(row.adviceConfidence);
        result.setRelativeStrengthScore(row.relativeStrengthScore);
        result.setIndustryReturnStrength(row.industryReturnStrength);
        result.setIndustryVolumeStrength(row.industryVolumeStrength);
        result.setIndustryFlowStrength(row.industryFlowStrength);
        result.setEventDirection(row.eventDirection);
        result.setEventConfidence(row.eventConfidence);
        result.setEventFreshnessDays(row.eventFreshnessDays);
        result.setEventTypeSummary(row.eventTypeSummary);
        result.setTurnaroundLabel(row.turnaroundLabel);
        result.setTurnaroundReason(row.turnaroundReason);
        result.setBuyPointLabel(row.buyPointLabel);
        result.setBuyPointReason(row.buyPointReason);
        result.setDataConfidenceReason(row.dataConfidenceReason);
        result.setSignalType(row.signalType);
        result.setSignalHorizonDays(row.signalHorizonDays);
        result.setEntryRule(row.entryRule);
        result.setExitRule(row.exitRule);
        result.setValidationMode(row.validationMode);
        result.setHardExclude(row.hardExclude);
        result.setHardExcludeReason(row.hardExcludeReason);
        result.setDataQualityGrade(row.dataQualityGrade);
        result.setWinratePriorityScore(row.winratePriorityScore);
        result.setExpectedReturnScore(row.expectedReturnScore);
        result.setMaxDrawdownPenalty(row.maxDrawdownPenalty);
        result.setBacktestCohort(row.backtestCohort);
        result.setPostClosePriorityScore(row.postClosePriorityScore);
        result.setPostCloseCategory(row.postCloseCategory);
        result.setPostCloseAction(row.postCloseAction);
        result.setPostCloseReason(row.postCloseReason);
        result.setSnapshotStage(row.snapshotStage);
        result.setTechReady(row.techReady);
        result.setMarketReady(row.marketReady);
        result.setInstitutionalReady(row.institutionalReady);
        result.setBrokerReady(row.brokerReady);
        result.setFinancialReady(row.financialReady);
        result.setNewsReady(row.newsReady);
        result.setAnalysisVersion(row.analysisVersion);
        result.setSourceUpdatedAt(row.sourceUpdatedAt);
    }

    private double computeNewsOnlyPriority(StockAnalysisResultVO result) {
        double score = result.getNewsScore() * 0.36D + result.getThemeReferenceScore() * 0.18D
                + result.getMarketThemeReferenceScore() * 0.18D + result.getSelectionScore() * 0.08D
                + result.getBuyPointScore() * 0.06D + result.getPostClosePriorityScore() * 0.08D
                + result.getThemeScore() * 0.06D;
        score += Math.max(0D, result.getRelativeStrengthScore() - 50D) * 0.05D;
        score += Math.min(6D, result.getNewsSourceCount() * 1.8D);
        score += Math.max(0D, result.getNewsFreshnessScore() - 45D) * 0.06D;
        score += Math.max(0D, result.getNewsSourceCredibilityScore() - 55D) * 0.05D;
        if ("正向催化".equals(result.getEventDirection())) {
            score += Math.min(6D, result.getEventConfidence() * 0.06D);
        } else if ("負向風險".equals(result.getEventDirection())) {
            score -= Math.min(8D, result.getEventConfidence() * 0.08D);
        }
        if (result.getNewsRiskScore() > 60D) {
            score -= (result.getNewsRiskScore() - 60D) * 0.18D;
        }
        if (result.getNewsSummary() != null && result.getNewsSummary().length() > 0) {
            score += Math.min(6D, result.getNewsSummary().length() / 50D);
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    private String buildNewsOnlyAnalysisNote(StockAnalysisResultVO result, String referenceDate) {
        List<String> notes = new ArrayList<String>();
        notes.add("新聞模式");
        if (referenceDate != null && referenceDate.length() > 0) {
            notes.add("沿用最近收盤快照 " + referenceDate);
        }
        if (result.getPostCloseCategory() != null && result.getPostCloseCategory().length() > 0) {
            notes.add(result.getPostCloseCategory() + " / " + emptyIfBlank(result.getPostCloseAction(), "觀察"));
        }
        if (result.getThemeReferenceTheme() != null && result.getThemeReferenceTheme().length() > 0) {
            notes.add("個股新聞題材 " + result.getThemeReferenceTheme());
        }
        if (result.getMarketThemeReferenceTheme() != null && result.getMarketThemeReferenceTheme().length() > 0) {
            notes.add("市場新聞題材 " + result.getMarketThemeReferenceTheme());
        }
        if (result.getNewsSourceSummary() != null && result.getNewsSourceSummary().length() > 0) {
            notes.add("來源 " + result.getNewsSourceSummary());
        }
        if (result.getLatestNewsPublishedHint() != null && result.getLatestNewsPublishedHint().length() > 0) {
            notes.add("最新時間 " + result.getLatestNewsPublishedHint());
        }
        if (result.getNewsRiskScore() >= 65D) {
            notes.add("新聞風險偏高");
        } else if (result.getNewsScore() >= 65D) {
            notes.add("新聞熱度偏高");
        }
        return join(notes, "；");
    }

    private void applyNarrativeSummary(StockAnalysisResultVO result) {
        NewsCompanySummarizer.Summary summary = newsCompanySummarizer.summarize(result);
        result.setCompanySummary(summary.companySummary);
        result.setRecentNewsBrief(summary.recentNewsBrief);
        result.setTransformationHint(summary.transformationHint);
        result.setPracticalAdvice(summary.practicalAdvice);
        result.setAdviceConfidence(summary.adviceConfidence);
    }

    private <T> T fetchOptional(String label, TaiwanStockVO stock, FetchSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            System.out.println("Fallback " + stock.getYahooSymbol() + " " + label + " because " + ex.getMessage());
            return fallback;
        }
    }

    private boolean hasRevenueCache(LowFrequencyDataCache.Entry entry) {
        return entry != null && entry.latestRevenuePeriod != null && entry.latestRevenuePeriod.length() > 0;
    }

    private boolean hasFinancialCache(LowFrequencyDataCache.Entry entry) {
        return entry != null && entry.latestFinancialPeriod != null && entry.latestFinancialPeriod.length() > 0;
    }

    private boolean hasProfileCache(LowFrequencyDataCache.Entry entry) {
        return entry != null && ((entry.industry != null && entry.industry.length() > 0) || entry.peerAveragePe > 0D
                || entry.grossMarginPct > 0D || entry.operatingMarginPct > 0D || entry.returnOnAssetsPct != 0D
                || entry.returnOnEquityPct != 0D || entry.bookValue > 0D);
    }

    private boolean shouldRefreshRevenue(LowFrequencyDataCache.Entry entry) {
        if (!hasRevenueCache(entry)) {
            return true;
        }
        if (cacheAgeExceeds(entry, 60)) {
            return true;
        }
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        int day = today.getDayOfMonth();
        // 月營收在次月 10 日前公告，5-9 日尚未有新資料；同天已收過就不重複
        if (day < 10 || day > 15) {
            return false;
        }
        return !isRefreshedToday(entry);
    }

    private boolean shouldRefreshFinancial(LowFrequencyDataCache.Entry entry) {
        if (!hasFinancialCache(entry)) {
            return true;
        }
        if (cacheAgeExceeds(entry, 120)) {
            return true;
        }
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();
        // 財報公告窗口；4 月全月涵蓋（Q4 大型股中下旬才出）；同天已收過就不重複
        boolean inWindow = (month == 3 && day >= 20) || month == 4 || (month == 5 && day <= 20)
                || (month == 8 && day <= 20) || (month == 11 && day <= 20);
        if (!inWindow) {
            return false;
        }
        return !isRefreshedToday(entry);
    }

    private boolean isRefreshedToday(LowFrequencyDataCache.Entry entry) {
        if (entry == null) {
            return false;
        }
        String refreshed = emptyIfBlank(entry.cacheRefreshedAt, "");
        if (refreshed.length() < 8) {
            return false;
        }
        try {
            LocalDate fetchDate = LocalDate.parse(refreshed.substring(0, 8),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return fetchDate.equals(LocalDate.now(TAIPEI_ZONE));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean cacheAgeExceeds(LowFrequencyDataCache.Entry entry, int maxDays) {
        if (entry == null) {
            return true;
        }
        String refreshed = emptyIfBlank(entry.cacheRefreshedAt, "");
        if (refreshed.length() < 8) {
            return true;
        }
        try {
            LocalDate fetchDate = LocalDate.parse(refreshed.substring(0, 8),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return java.time.temporal.ChronoUnit.DAYS.between(fetchDate, LocalDate.now(TAIPEI_ZONE)) > maxDays;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean shouldRefreshProfile(LowFrequencyDataCache.Entry entry, boolean refreshFinancial) {
        if (!hasProfileCache(entry)) {
            return true;
        }
        if (refreshFinancial) {
            return true;
        }
        return LocalDate.now(TAIPEI_ZONE).getDayOfWeek().getValue() == 1;
    }

    private ProfileSnapshotVO profileFromCache(LowFrequencyDataCache.Entry entry) {
        if (entry == null) {
            return emptyProfileSnapshot();
        }
        return new ProfileSnapshotVO(0D, emptyIfBlank(entry.industry, ""), emptyIfBlank(entry.marketType, ""),
                entry.capital, entry.sharesOutstanding, entry.marketCapMillions, entry.displayedPe,
                entry.peerAveragePe, entry.latestVolumeLots, entry.latestTurnoverBillion, entry.grossMarginPct,
                entry.operatingMarginPct, entry.returnOnAssetsPct, entry.returnOnEquityPct, entry.bookValue,
                emptyIfBlank(entry.shareholderMeetingDate, ""), emptyIfBlank(entry.cashDividendPayoutDate, ""),
                emptyIfBlank(entry.exDividendDate, ""));
    }

    private double cacheNumber(double value) {
        return hasValue(value) ? value : 0D;
    }

    private double cacheNumber(double fallback, double cachedValue) {
        return hasValue(cachedValue) ? cachedValue : fallback;
    }

    private void updateLowFrequencyCache(TaiwanStockVO stock, LowFrequencyDataCache.Entry cacheEntry,
            StockAnalysisResultVO result, ProfileSnapshotVO profile, List<MonthlyRevenueVO> revenues,
            List<EpsRecordVO> epsRecords, List<IncomeStatementRecordVO> incomeRecords,
            List<BalanceSheetRecordVO> balanceRecords, List<CashFlowRecordVO> cashFlowRecords) {
        LowFrequencyDataCache.Entry entry = cacheEntry == null ? new LowFrequencyDataCache.Entry() : cacheEntry;
        entry.code = stock.getCode();
        boolean refreshedLowFrequency = (revenues != null && !revenues.isEmpty())
                || (epsRecords != null && !epsRecords.isEmpty())
                || (incomeRecords != null && !incomeRecords.isEmpty())
                || (balanceRecords != null && !balanceRecords.isEmpty())
                || (cashFlowRecords != null && !cashFlowRecords.isEmpty())
                || (profileHasData(profile) && !hasProfileCache(cacheEntry));
        if (refreshedLowFrequency) {
            entry.cacheRefreshedAt = currentDateStamp();
        } else if (emptyIfBlank(entry.cacheRefreshedAt, "").length() == 0) {
            entry.cacheRefreshedAt = currentDateStamp();
        }
        if (revenues != null && !revenues.isEmpty()) {
            entry.latestRevenuePeriod = emptyIfBlank(revenues.get(0).getPeriod(), entry.latestRevenuePeriod);
        }
        String financialPeriod = resolveFinancialPeriod(epsRecords, incomeRecords, balanceRecords, cashFlowRecords);
        if (financialPeriod.length() > 0) {
            entry.latestFinancialPeriod = financialPeriod;
        }
        // sourceUpdatedAt = actual data period for display (revenue period preferred, else financial period)
        if (entry.latestRevenuePeriod.length() > 0) {
            entry.sourceUpdatedAt = entry.latestRevenuePeriod;
        } else if (entry.latestFinancialPeriod.length() > 0) {
            entry.sourceUpdatedAt = entry.latestFinancialPeriod;
        } else if (emptyIfBlank(entry.sourceUpdatedAt, "").length() == 0) {
            entry.sourceUpdatedAt = currentDateStamp();
        }
        if (profile != null) {
            if (profile.getIndustry().length() > 0) {
                entry.industry = profile.getIndustry();
            }
            if (profile.getMarketType().length() > 0) {
                entry.marketType = profile.getMarketType();
            }
            if (profile.getShareholderMeetingDate().length() > 0) {
                entry.shareholderMeetingDate = profile.getShareholderMeetingDate();
            }
            if (profile.getCashDividendPayoutDate().length() > 0) {
                entry.cashDividendPayoutDate = profile.getCashDividendPayoutDate();
            }
            if (profile.getExDividendDate().length() > 0) {
                entry.exDividendDate = profile.getExDividendDate();
            }
            if (profile.getCapital() > 0L) {
                entry.capital = profile.getCapital();
            }
            if (profile.getSharesOutstanding() > 0L) {
                entry.sharesOutstanding = profile.getSharesOutstanding();
            }
            if (profile.getLatestVolumeLots() > 0L) {
                entry.latestVolumeLots = profile.getLatestVolumeLots();
            }
            if (profile.getMarketCapMillions() > 0D) {
                entry.marketCapMillions = profile.getMarketCapMillions();
            }
            if (profile.getDisplayedPe() > 0D) {
                entry.displayedPe = profile.getDisplayedPe();
            }
            if (profile.getPeerAveragePe() > 0D) {
                entry.peerAveragePe = profile.getPeerAveragePe();
            }
            if (profile.getLatestTurnoverBillion() > 0D) {
                entry.latestTurnoverBillion = profile.getLatestTurnoverBillion();
            }
            entry.grossMarginPct = profile.getGrossMarginPct();
            entry.operatingMarginPct = profile.getOperatingMarginPct();
            entry.returnOnAssetsPct = profile.getReturnOnAssetsPct();
            entry.returnOnEquityPct = profile.getReturnOnEquityPct();
            entry.bookValue = profile.getBookValue();
        }
        entry.latestRevenueYoY = result.getLatestRevenueYoY();
        entry.averageThreeMonthRevenueYoY = result.getAverageThreeMonthRevenueYoY();
        entry.accumulatedRevenueYoY = result.getAccumulatedRevenueYoY();
        entry.positiveRevenueMonths = result.getPositiveRevenueMonths();
        entry.trailingFourQuarterEps = result.getTrailingFourQuarterEps();
        entry.latestQuarterEps = result.getLatestQuarterEps();
        entry.previousQuarterEps = inferPreviousQuarterEps(epsRecords, entry.previousQuarterEps);
        entry.latestQuarterEpsYoYPct = result.getLatestQuarterEpsYoYPct();
        entry.positiveEpsQuarters = result.getPositiveEpsQuarters();
        entry.latestOperatingCashFlow = result.getLatestOperatingCashFlow();
        entry.previousOperatingCashFlow = inferPreviousOperatingCashFlow(cashFlowRecords, entry.previousOperatingCashFlow);
        entry.latestFreeCashFlow = result.getLatestFreeCashFlow();
        entry.previousFreeCashFlow = inferPreviousFreeCashFlow(cashFlowRecords, entry.previousFreeCashFlow);
        entry.positiveOperatingCashFlowQuarters = result.getPositiveOperatingCashFlowQuarters();
        entry.positiveFreeCashFlowQuarters = result.getPositiveFreeCashFlowQuarters();
        entry.latestOperatingIncome = inferLatestOperatingIncome(incomeRecords, entry.latestOperatingIncome);
        entry.previousOperatingIncome = inferPreviousOperatingIncome(incomeRecords, entry.previousOperatingIncome);
        entry.latestNetIncome = inferLatestNetIncome(incomeRecords, entry.latestNetIncome);
        entry.previousNetIncome = inferPreviousNetIncome(incomeRecords, entry.previousNetIncome);
        entry.debtRatioPct = result.getDebtRatioPct();
        entry.currentRatio = result.getCurrentRatio();
        entry.nonOperatingRatioPct = result.getNonOperatingRatioPct();
        lowFrequencyDataCache.upsert(entry);
    }

    private String resolveFinancialPeriod(List<EpsRecordVO> epsRecords, List<IncomeStatementRecordVO> incomeRecords,
            List<BalanceSheetRecordVO> balanceRecords, List<CashFlowRecordVO> cashFlowRecords) {
        if (epsRecords != null && !epsRecords.isEmpty()) {
            return emptyIfBlank(epsRecords.get(0).getPeriod(), "");
        }
        if (incomeRecords != null && !incomeRecords.isEmpty()) {
            return emptyIfBlank(incomeRecords.get(0).getPeriod(), "");
        }
        if (balanceRecords != null && !balanceRecords.isEmpty()) {
            return emptyIfBlank(balanceRecords.get(0).getPeriod(), "");
        }
        if (cashFlowRecords != null && !cashFlowRecords.isEmpty()) {
            return emptyIfBlank(cashFlowRecords.get(0).getPeriod(), "");
        }
        return "";
    }

    private double inferPreviousQuarterEps(List<EpsRecordVO> epsRecords, double fallback) {
        return epsRecords != null && epsRecords.size() >= 2 ? epsRecords.get(1).getEps() : fallback;
    }

    private long inferPreviousOperatingCashFlow(List<CashFlowRecordVO> cashFlowRecords, long fallback) {
        return cashFlowRecords != null && cashFlowRecords.size() >= 2 ? cashFlowRecords.get(1).getOperatingCashFlow()
                : fallback;
    }

    private long inferPreviousFreeCashFlow(List<CashFlowRecordVO> cashFlowRecords, long fallback) {
        return cashFlowRecords != null && cashFlowRecords.size() >= 2 ? cashFlowRecords.get(1).getFreeCashFlow()
                : fallback;
    }

    private long inferLatestOperatingIncome(List<IncomeStatementRecordVO> incomeRecords, long fallback) {
        return incomeRecords != null && !incomeRecords.isEmpty() ? incomeRecords.get(0).getOperatingIncome() : fallback;
    }

    private long inferPreviousOperatingIncome(List<IncomeStatementRecordVO> incomeRecords, long fallback) {
        return incomeRecords != null && incomeRecords.size() >= 2 ? incomeRecords.get(1).getOperatingIncome() : fallback;
    }

    private long inferLatestNetIncome(List<IncomeStatementRecordVO> incomeRecords, long fallback) {
        return incomeRecords != null && !incomeRecords.isEmpty() ? incomeRecords.get(0).getNetIncome() : fallback;
    }

    private long inferPreviousNetIncome(List<IncomeStatementRecordVO> incomeRecords, long fallback) {
        return incomeRecords != null && incomeRecords.size() >= 2 ? incomeRecords.get(1).getNetIncome() : fallback;
    }

    private boolean profileHasData(ProfileSnapshotVO profile) {
        return profile != null && (profile.getCurrentPrice() > 0D || profile.getIndustry().length() > 0
                || profile.getPeerAveragePe() > 0D || profile.getGrossMarginPct() != 0D
                || profile.getOperatingMarginPct() != 0D || profile.getReturnOnAssetsPct() != 0D
                || profile.getReturnOnEquityPct() != 0D || profile.getBookValue() > 0D);
    }

    private ProfileSnapshotVO emptyProfileSnapshot() {
        return new ProfileSnapshotVO(0D, "", "", 0L, 0L, 0D, 0D, 0D, 0L, 0D, 0D, 0D, 0D, 0D, 0D, "", "", "");
    }

    private boolean hasValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
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

    private String emptyIfBlank(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String format(double value) {
        return String.format("%.2f", Double.valueOf(value));
    }

    private String formatLots(long value) {
        return String.format("%,d", Long.valueOf(value));
    }

    private boolean isThrottleLikeFailure(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("status=999") || normalized.contains("status=429")
                || normalized.contains("anti-bot") || normalized.contains("captcha")
                || normalized.contains("timed out");
    }

    private static long parseLongProperty(String key, long defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null ? defaultValue : Long.parseLong(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static boolean parseBooleanProperty(String key, boolean defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static class SectorAggregate {
        private int count;
        private int qualifiedCount;
        private int breadthCount;
        private int strongCount;
        private double selectionScoreSum;
        private double qualityScoreSum;
        private double momentumScoreSum;
    }

    private static class TrendProfile {
        private final double score;
        private final int days;

        private TrendProfile(double score, int days) {
            this.score = score;
            this.days = days;
        }
    }

    private static class StructureProfile {
        private final double score;
        private final String label;

        private StructureProfile(double score, String label) {
            this.score = score;
            this.label = label;
        }
    }

    private static class RiskRewardProfile {
        private final double score;
        private final double riskRewardRatio;
        private final double stopLossPrice;
        private final double stopLossPct;
        private final double trailingStopPrice;
        private final double targetPrice;
        private final double upsidePotentialPct;
        private final double sellSignalScore;
        private final String sellSignalLabel;
        private final boolean reducePositionSize;

        private RiskRewardProfile(double score, double riskRewardRatio, double stopLossPrice, double stopLossPct,
                double trailingStopPrice, double targetPrice, double upsidePotentialPct, double sellSignalScore,
                String sellSignalLabel, boolean reducePositionSize) {
            this.score = score;
            this.riskRewardRatio = riskRewardRatio;
            this.stopLossPrice = stopLossPrice;
            this.stopLossPct = stopLossPct;
            this.trailingStopPrice = trailingStopPrice;
            this.targetPrice = targetPrice;
            this.upsidePotentialPct = upsidePotentialPct;
            this.sellSignalScore = sellSignalScore;
            this.sellSignalLabel = sellSignalLabel;
            this.reducePositionSize = reducePositionSize;
        }
    }

    private static class SellSignalProfile {
        private final double score;
        private final String label;

        private SellSignalProfile(double score, String label) {
            this.score = score;
            this.label = label;
        }
    }

    private static class TurnaroundProfile {
        private final double score;
        private final double revenueGrowthScore;
        private final double earningsTurnaroundScore;
        private final double profitabilityTurnaroundScore;
        private final double oneOffRiskScore;
        private final String label;
        private final String reason;

        private TurnaroundProfile(double score, double revenueGrowthScore, double earningsTurnaroundScore,
                double profitabilityTurnaroundScore, double oneOffRiskScore, String label, String reason) {
            this.score = score;
            this.revenueGrowthScore = revenueGrowthScore;
            this.earningsTurnaroundScore = earningsTurnaroundScore;
            this.profitabilityTurnaroundScore = profitabilityTurnaroundScore;
            this.oneOffRiskScore = oneOffRiskScore;
            this.label = label;
            this.reason = reason;
        }
    }

    private static class RelativeStrengthAggregate {
        private int count;
        private double return20DayPctSum;
        private double volumeRatioSum;
        private double flowRatioSum;
        private double return20DayPctAverage;
        private double volumeRatioAverage;
        private double flowRatioAverage;
    }

    private static class EventSignalProfile {
        private final String direction;
        private final double confidence;
        private final int freshnessDays;
        private final String typeSummary;

        private EventSignalProfile(String direction, double confidence, int freshnessDays, String typeSummary) {
            this.direction = direction;
            this.confidence = confidence;
            this.freshnessDays = freshnessDays;
            this.typeSummary = typeSummary;
        }
    }

    private static class BacktestSummaryRow {
        private int horizonDays;
        private String cohort = "";
        private int sampleCount;
        private double netWinRatePct;
        private double avgNetReturnPct;
        private double avgMaxDrawdownClosePct;
        private double avgHoldingDays;
    }

    private static class LatestSnapshotContext {
        private String date = "";
        private Map<String, StockHistoryDatabase.SnapshotRow> rowsByCode = new HashMap<String, StockHistoryDatabase.SnapshotRow>();
    }

    private interface FetchSupplier<T> {

        T get() throws Exception;
    }
}
