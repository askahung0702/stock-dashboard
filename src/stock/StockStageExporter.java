package stock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

public class StockStageExporter {

    private static final String STAGE_CLOSE = "close";
    private static final String STAGE_FULL = "full";
    private static final String STAGE_INTRADAY_CLOSE = "intraday-close";
    private static final String STAGE_NEWS_EVENT = "news-event";

    public static void main(String[] args) throws Exception {
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        String date = analyzer.currentDateStamp();
        StockHistoryDatabase database = new StockHistoryDatabase();

        StockHistoryDatabase.Snapshot full = database.loadDailyStockAnalysis(date, STAGE_FULL);
        StockHistoryDatabase.Snapshot intradayClose = database.loadDailyStockAnalysis(date, STAGE_INTRADAY_CLOSE);
        StockHistoryDatabase.Snapshot close = database.loadDailyStockAnalysis(date, STAGE_CLOSE);
        StockHistoryDatabase.Snapshot newsEvent = database.loadDailyStockAnalysis(date, STAGE_NEWS_EVENT);

        ExportSelection selection = selectSnapshot(System.getenv("STOCK_EXPORT_REQUEST_MODE"), full, intradayClose, close,
                newsEvent);
        if (selection == null || selection.snapshot == null || selection.snapshot.rows.isEmpty()) {
            System.out.println("No staged close/full data found for " + date + ". Export latest DB snapshot only.");
            new StockStaticApiExporter().writeDefaultOutputs(new File("web\\data", "latest.json").getPath(),
                    new File("web\\data", "history.json").getPath());
            writeSnapshotStatus(date, "unknown", "未找到今日 stage，沿用既有快照", false);
            return;
        }

        StockHistoryDatabase.Snapshot exportSnapshot = selection.snapshot;
        String exportStage = selection.stage;
        exportSnapshot.date = date;
        database.upsertSnapshot(exportSnapshot);
        new StockStaticApiExporter().writeDefaultOutputs(new File("web\\data", "latest.json").getPath(),
                new File("web\\data", "history.json").getPath());
        writeSnapshotStatus(date, exportStage, stageLabel(exportStage), false);
        database.upsertDailyRunStatus(date, "export", "completed", exportSnapshot.rows.size(),
                "exported from " + exportStage);
        System.out.println("Stage export completed: " + exportStage + " rows=" + exportSnapshot.rows.size());
    }

    private static ExportSelection selectSnapshot(String requestMode, StockHistoryDatabase.Snapshot full,
            StockHistoryDatabase.Snapshot intradayClose, StockHistoryDatabase.Snapshot close,
            StockHistoryDatabase.Snapshot newsEvent) {
        String mode = requestMode == null ? "" : requestMode.trim().toLowerCase();
        boolean hasFull = full != null && full.rows != null && !full.rows.isEmpty();
        boolean hasIntradayClose = intradayClose != null && intradayClose.rows != null && !intradayClose.rows.isEmpty();
        boolean hasClose = close != null && close.rows != null && !close.rows.isEmpty();
        boolean hasNews = newsEvent != null && newsEvent.rows != null && !newsEvent.rows.isEmpty();

        if ("intraday-close".equals(mode) || "intraday".equals(mode)) {
            return hasIntradayClose ? new ExportSelection(intradayClose, STAGE_INTRADAY_CLOSE)
                    : hasClose ? new ExportSelection(close, STAGE_CLOSE)
                            : hasFull ? new ExportSelection(full, STAGE_FULL) : null;
        }
        if ("market-futures".equals(mode) || "futures-price".equals(mode)) {
            return hasIntradayClose ? new ExportSelection(intradayClose, STAGE_INTRADAY_CLOSE)
                    : hasClose ? new ExportSelection(close, STAGE_CLOSE)
                            : hasFull ? new ExportSelection(full, STAGE_FULL) : null;
        }
        if ("close".equals(mode)) {
            return hasClose ? new ExportSelection(close, STAGE_CLOSE) : hasFull ? new ExportSelection(full, STAGE_FULL) : null;
        }
        if ("news-event".equals(mode) || "news-only".equals(mode)) {
            return hasClose ? new ExportSelection(mergeCloseAndNewsEvent(close, newsEvent),
                    hasNews ? "close+news-event" : STAGE_CLOSE)
                    : hasFull ? new ExportSelection(full, STAGE_FULL) : null;
        }
        if ("full".equals(mode)) {
            return hasFull ? new ExportSelection(full, STAGE_FULL)
                    : hasClose ? new ExportSelection(mergeCloseAndNewsEvent(close, newsEvent),
                            hasNews ? "close+news-event" : STAGE_CLOSE)
                            : null;
        }

        // Manual export keeps the most complete snapshot, but stage-triggered exports above
        // must not let an older same-date full run mask a fresh close/news-event run.
        if (hasFull) {
            return new ExportSelection(full, STAGE_FULL);
        }
        if (hasClose) {
            return new ExportSelection(mergeCloseAndNewsEvent(close, newsEvent),
                    hasNews ? "close+news-event" : STAGE_CLOSE);
        }
        if (hasIntradayClose) {
            return new ExportSelection(intradayClose, STAGE_INTRADAY_CLOSE);
        }
        return null;
    }

    private static StockHistoryDatabase.Snapshot mergeCloseAndNewsEvent(StockHistoryDatabase.Snapshot close,
            StockHistoryDatabase.Snapshot newsEvent) {
        StockHistoryDatabase.Snapshot merged = new StockHistoryDatabase.Snapshot();
        merged.date = close.date;
        Map<String, StockHistoryDatabase.SnapshotRow> newsRows = new HashMap<String, StockHistoryDatabase.SnapshotRow>();
        if (newsEvent != null && newsEvent.rows != null) {
            for (StockHistoryDatabase.SnapshotRow row : newsEvent.rows) {
                newsRows.put(row.code, row);
            }
        }

        for (StockHistoryDatabase.SnapshotRow closeRow : close.rows) {
            StockHistoryDatabase.SnapshotRow row = copyRow(closeRow);
            StockHistoryDatabase.SnapshotRow newsRow = newsRows.get(row.code);
            if (newsRow != null) {
                applyNewsEvent(row, newsRow);
            }
            row.snapshotStage = newsRow == null ? STAGE_CLOSE : "close+news-event";
            merged.rows.add(row);
        }
        return merged;
    }

    private static StockHistoryDatabase.SnapshotRow copyRow(StockHistoryDatabase.SnapshotRow source) {
        // Manual field copy keeps this exporter independent from private database JSON parsers.
        StockHistoryDatabase.SnapshotRow target = new StockHistoryDatabase.SnapshotRow();
        copyCommon(source, target);
        return target;
    }

    private static void copyCommon(StockHistoryDatabase.SnapshotRow s, StockHistoryDatabase.SnapshotRow t) {
        t.date = s.date; t.code = s.code; t.name = s.name; t.market = s.market; t.industry = s.industry; t.note = s.note;
        t.score = s.score; t.rawScore = s.rawScore; t.selectionScore = s.selectionScore; t.momentumScore = s.momentumScore;
        t.qualityScore = s.qualityScore; t.sectorScore = s.sectorScore; t.themeScore = s.themeScore;
        t.trendPersistenceScore = s.trendPersistenceScore; t.trendPersistenceDays = s.trendPersistenceDays;
        t.newsScore = s.newsScore; t.newsRiskScore = s.newsRiskScore; t.relativeStrengthScore = s.relativeStrengthScore;
        t.industryReturnStrength = s.industryReturnStrength; t.industryVolumeStrength = s.industryVolumeStrength;
        t.industryFlowStrength = s.industryFlowStrength; t.eventDirection = s.eventDirection;
        t.eventConfidence = s.eventConfidence; t.eventFreshnessDays = s.eventFreshnessDays; t.eventTypeSummary = s.eventTypeSummary;
        t.structureScore = s.structureScore; t.riskRewardScore = s.riskRewardScore; t.riskRewardRatio = s.riskRewardRatio;
        t.turnaroundScore = s.turnaroundScore; t.revenueGrowthSignalScore = s.revenueGrowthSignalScore;
        t.earningsTurnaroundSignalScore = s.earningsTurnaroundSignalScore; t.profitabilityTurnaroundSignalScore = s.profitabilityTurnaroundSignalScore;
        t.oneOffRiskScore = s.oneOffRiskScore; t.suggestedStopPrice = s.suggestedStopPrice; t.suggestedStopPct = s.suggestedStopPct;
        t.suggestedTrailingStopPrice = s.suggestedTrailingStopPrice; t.suggestedTargetPrice = s.suggestedTargetPrice;
        t.fairValueLow = s.fairValueLow; t.fairValueBase = s.fairValueBase; t.fairValueHigh = s.fairValueHigh;
        t.fairValueConfidence = s.fairValueConfidence; t.fairValueMethod = s.fairValueMethod; t.fairValueReason = s.fairValueReason;
        t.upsidePotentialPct = s.upsidePotentialPct; t.sellSignalScore = s.sellSignalScore; t.sellSignalLabel = s.sellSignalLabel;
        t.reducePositionSize = s.reducePositionSize; t.buyPointScore = s.buyPointScore; t.dataConfidence = s.dataConfidence;
        t.selectionQualified = s.selectionQualified; t.marketRegime = s.marketRegime; t.price = s.price;
        t.movingAverage18 = s.movingAverage18; t.movingAverage20 = s.movingAverage20; t.movingAverage54 = s.movingAverage54;
        t.movingAverage60 = s.movingAverage60; t.movingAverage120 = s.movingAverage120; t.volumeRatio = s.volumeRatio;
        t.return18DayPct = s.return18DayPct; t.return20DayPct = s.return20DayPct; t.return54DayPct = s.return54DayPct;
        t.return60DayPct = s.return60DayPct; t.averageLots20 = s.averageLots20; t.averageTradeValue20Billion = s.averageTradeValue20Billion;
        t.volatility20Pct = s.volatility20Pct; t.atr20 = s.atr20; t.drawdownFromHigh60Pct = s.drawdownFromHigh60Pct;
        t.liquidityScore = s.liquidityScore; t.revenueScore = s.revenueScore; t.chipsScore = s.chipsScore;
        t.valuationScore = s.valuationScore; t.technicalScore = s.technicalScore; t.financialQualityScore = s.financialQualityScore;
        t.valuationIndustryPercentile = s.valuationIndustryPercentile; t.financialQualityIndustryPercentile = s.financialQualityIndustryPercentile;
        t.grossMarginIndustryPercentile = s.grossMarginIndustryPercentile; t.operatingMarginIndustryPercentile = s.operatingMarginIndustryPercentile;
        t.roaIndustryPercentile = s.roaIndustryPercentile; t.roeIndustryPercentile = s.roeIndustryPercentile;
        t.pegIndustryPercentile = s.pegIndustryPercentile; t.relativePeIndustryPercentile = s.relativePeIndustryPercentile;
        t.nonOperatingIndustryPercentile = s.nonOperatingIndustryPercentile; t.latestInstitutionalNetLots = s.latestInstitutionalNetLots;
        t.latestInstitutionalNetRatioPct = s.latestInstitutionalNetRatioPct; t.fiveDayInstitutionalNetLots = s.fiveDayInstitutionalNetLots;
        t.fiveDayInstitutionalNetRatioPct = s.fiveDayInstitutionalNetRatioPct; t.latestForeignNetLots = s.latestForeignNetLots;
        t.brokerNetLots = s.brokerNetLots; t.brokerNetRatioPct = s.brokerNetRatioPct; t.rsi14 = s.rsi14;
        t.stochasticK = s.stochasticK; t.stochasticD = s.stochasticD; t.epsAccelerationPct = s.epsAccelerationPct; t.peg = s.peg;
        t.scoreReason = s.scoreReason; t.revenueReason = s.revenueReason; t.chipsReason = s.chipsReason;
        t.liquidityReason = s.liquidityReason; t.valuationReason = s.valuationReason; t.technicalReason = s.technicalReason;
        t.financialQualityReason = s.financialQualityReason; t.eventRiskReason = s.eventRiskReason; t.eligibilityReason = s.eligibilityReason;
        t.primaryTheme = s.primaryTheme; t.themeTags = s.themeTags; t.launchTags = s.launchTags; t.newsSummary = s.newsSummary; t.newsDigest = s.newsDigest;
        t.newsSourceSummary = s.newsSourceSummary; t.latestNewsPublishedHint = s.latestNewsPublishedHint;
        t.newsSourceCredibilityScore = s.newsSourceCredibilityScore; t.newsFreshnessScore = s.newsFreshnessScore;
        t.newsSourceCount = s.newsSourceCount; t.newsOfficialSourceCount = s.newsOfficialSourceCount; t.newsMediaSourceCount = s.newsMediaSourceCount;
        t.companySummary = s.companySummary; t.recentNewsBrief = s.recentNewsBrief; t.transformationHint = s.transformationHint;
        t.practicalAdvice = s.practicalAdvice; t.adviceConfidence = s.adviceConfidence; t.structureLabel = s.structureLabel;
        t.turnaroundLabel = s.turnaroundLabel; t.turnaroundReason = s.turnaroundReason; t.buyPointLabel = s.buyPointLabel;
        t.buyPointReason = s.buyPointReason; t.dataConfidenceReason = s.dataConfidenceReason; t.signalType = s.signalType;
        t.signalHorizonDays = s.signalHorizonDays; t.entryRule = s.entryRule; t.exitRule = s.exitRule; t.validationMode = s.validationMode;
        t.hardExclude = s.hardExclude; t.hardExcludeReason = s.hardExcludeReason; t.dataQualityGrade = s.dataQualityGrade;
        t.coreConditionCount = s.coreConditionCount;
        t.winratePriorityScore = s.winratePriorityScore; t.expectedReturnScore = s.expectedReturnScore; t.maxDrawdownPenalty = s.maxDrawdownPenalty;
        t.backtestCohort = s.backtestCohort; t.postClosePriorityScore = s.postClosePriorityScore; t.postCloseCategory = s.postCloseCategory;
        t.postCloseAction = s.postCloseAction; t.postCloseReason = s.postCloseReason; t.snapshotStage = s.snapshotStage;
        t.techReady = s.techReady; t.marketReady = s.marketReady; t.institutionalReady = s.institutionalReady;
        t.brokerReady = s.brokerReady; t.financialReady = s.financialReady; t.newsReady = s.newsReady;
        t.analysisVersion = s.analysisVersion; t.sourceUpdatedAt = s.sourceUpdatedAt; t.likely = s.likely;
    }

    private static void applyNewsEvent(StockHistoryDatabase.SnapshotRow target, StockHistoryDatabase.SnapshotRow news) {
        target.newsScore = news.newsScore;
        target.newsRiskScore = news.newsRiskScore;
        target.eventDirection = news.eventDirection;
        target.eventConfidence = news.eventConfidence;
        target.eventFreshnessDays = news.eventFreshnessDays;
        target.eventTypeSummary = news.eventTypeSummary;
        target.newsSummary = news.newsSummary;
        target.newsDigest = news.newsDigest;
        target.newsSourceSummary = news.newsSourceSummary;
        target.latestNewsPublishedHint = news.latestNewsPublishedHint;
        target.newsSourceCredibilityScore = news.newsSourceCredibilityScore;
        target.newsFreshnessScore = news.newsFreshnessScore;
        target.newsSourceCount = news.newsSourceCount;
        target.newsOfficialSourceCount = news.newsOfficialSourceCount;
        target.newsMediaSourceCount = news.newsMediaSourceCount;
        target.companySummary = news.companySummary;
        target.recentNewsBrief = news.recentNewsBrief;
        target.transformationHint = news.transformationHint;
        target.practicalAdvice = news.practicalAdvice;
        target.adviceConfidence = news.adviceConfidence;
        target.newsReady = true;
        target.analysisVersion = appendVersion(target.analysisVersion, "news-event");
    }

    private static String appendVersion(String current, String suffix) {
        String base = current == null || current.length() == 0 ? "stage-cache-v1" : current;
        return base.contains(suffix) ? base : base + "-" + suffix;
    }

    @SuppressWarnings("unchecked")
    private static void writeSnapshotStatus(String date, String stage, String label, boolean limitedRun) throws Exception {
        File statusFile = new File("web\\data", "snapshot_status.json");
        File parent = statusFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        JSONObject result = new JSONObject();
        result.put("date", date);
        result.put("snapshotStage", stage);
        result.put("stageLabel", label);
        result.put("limitedRun", Boolean.valueOf(limitedRun));
        result.put("generatedAt", LocalDateTime.now().toString());
        Writer writer = new OutputStreamWriter(new FileOutputStream(statusFile), "UTF-8");
        try {
            writer.write(result.toJSONString());
        } finally {
            writer.close();
        }
    }

    private static String stageLabel(String stage) {
        if (STAGE_FULL.equals(stage)) {
            return "夜間完整版";
        }
        if (STAGE_INTRADAY_CLOSE.equals(stage)) {
            return "收盤行情初版";
        }
        if ("close+news-event".equals(stage)) {
            return "盤後初版 + 新聞事件";
        }
        if (STAGE_CLOSE.equals(stage)) {
            return "盤後初版";
        }
        return stage;
    }

    private static class ExportSelection {
        private final StockHistoryDatabase.Snapshot snapshot;
        private final String stage;

        private ExportSelection(StockHistoryDatabase.Snapshot snapshot, String stage) {
            this.snapshot = snapshot;
            this.stage = stage;
        }
    }
}
