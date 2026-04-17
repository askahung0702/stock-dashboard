package stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.NumberParser;
import stock.vo.StockAnalysisResultVO;

public class StockHistoryDatabase {

    private static final long DATABASE_VERSION = 8L;
    private static final double LIKELY_THRESHOLD = 72D;
    private static final double MIN_LIQUIDITY_SCORE = 4D;
    private static final double MIN_SELECTION_FINANCIAL_SCORE = 8D;
    private static final double MIN_LIKELY_FINANCIAL_SCORE = 12D;
    private static final double LIKELY_MIN_VOLUME_RATIO = 0.8D;
    private static final double LIKELY_MAX_VOLUME_RATIO = 2.5D;
    private static final String HISTORY_DIRECTORY_NAME = "history";
    private static final String LEGACY_DATABASE_FILE_NAME = "stock_history_db.json";
    private static final String SQLITE_DATABASE_FILE_NAME = "stock_history_db.sqlite";
    private static final String SNAPSHOT_PREFIX = "stock_candidates_";
    private static final String SNAPSHOT_SUFFIX = ".csv";
    private static final String STORAGE_MODE_PROPERTY = "stock.history.storage";
    private static final String STORAGE_MODE_AUTO = "auto";
    private static final String STORAGE_MODE_JSON = "json";
    private static final String STORAGE_MODE_SQLITE = "sqlite";

    public String upsertSnapshot(String date, List<StockAnalysisResultVO> results) throws Exception {
        File historyDirectory = ensureHistoryDirectory();
        Snapshot snapshot = buildSnapshot(date, results);
        SQLiteStore sqliteStore = resolveSqliteStore(historyDirectory);
        if (sqliteStore != null) {
            seedSqliteIfNeeded(historyDirectory, sqliteStore);
            sqliteStore.upsertSnapshot(snapshot);
            return sqliteStore.getDatabaseFile().getAbsolutePath();
        }
        return upsertSnapshotJson(historyDirectory, snapshot);
    }

    public Map<String, Snapshot> loadSnapshots() throws Exception {
        File historyDirectory = ensureHistoryDirectory();
        SQLiteStore sqliteStore = resolveSqliteStore(historyDirectory);
        if (sqliteStore != null) {
            seedSqliteIfNeeded(historyDirectory, sqliteStore);
            Map<String, Snapshot> sqliteSnapshots = sqliteStore.loadSnapshots();
            boolean changed = importMissingCsvSnapshotsToSqlite(historyDirectory, sqliteSnapshots, sqliteStore);
            if (changed) {
                sqliteSnapshots = sqliteStore.loadSnapshots();
            }
            if (!sqliteSnapshots.isEmpty()) {
                return sqliteSnapshots;
            }
        }
        return loadSnapshotsFromJson(historyDirectory);
    }

    public String getDatabasePath() {
        File historyDirectory = ensureHistoryDirectory();
        SQLiteStore sqliteStore = resolveSqliteStore(historyDirectory);
        if (sqliteStore != null) {
            return sqliteStore.getDatabaseFile().getAbsolutePath();
        }
        return getLegacyDatabaseFile(historyDirectory).getAbsolutePath();
    }

    private Snapshot buildSnapshot(String date, List<StockAnalysisResultVO> results) {
        Snapshot snapshot = new Snapshot();
        snapshot.date = safeText(date);
        for (StockAnalysisResultVO result : results) {
            snapshot.rows.add(toRow(result, snapshot.date));
        }
        return snapshot;
    }

    public Snapshot buildSnapshotForDate(String date, List<StockAnalysisResultVO> results) {
        return buildSnapshot(date, results);
    }

    private String upsertSnapshotJson(File historyDirectory, Snapshot snapshot) throws Exception {
        JSONObject root = loadRoot(historyDirectory);
        JSONObject snapshots = getSnapshotsObject(root);
        snapshots.put(snapshot.date, toSnapshotJson(snapshot));
        root.put("version", Long.valueOf(DATABASE_VERSION));
        root.put("updatedDate", safeText(snapshot.date));
        root.put("source", "StockAnalysis");
        writeRoot(historyDirectory, root);
        return getLegacyDatabaseFile(historyDirectory).getAbsolutePath();
    }

    private Map<String, Snapshot> loadSnapshotsFromJson(File historyDirectory) throws Exception {
        JSONObject root = loadRoot(historyDirectory);
        JSONObject snapshotsObject = getSnapshotsObject(root);
        Map<String, Snapshot> snapshotsByDate = parseSnapshots(snapshotsObject);
        boolean changed = importMissingCsvSnapshots(historyDirectory, snapshotsByDate, snapshotsObject);
        if (changed) {
            root.put("updatedDate", resolveLatestDate(snapshotsByDate));
            writeRoot(historyDirectory, root);
        }
        return snapshotsByDate;
    }

    private JSONObject loadRoot(File historyDirectory) throws Exception {
        File databaseFile = getLegacyDatabaseFile(historyDirectory);
        if (!databaseFile.exists() || databaseFile.length() == 0L) {
            return createEmptyRoot();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(databaseFile), "UTF-8"));
        try {
            Object parsed = new JSONParser().parse(reader);
            if (parsed instanceof JSONObject) {
                JSONObject root = (JSONObject) parsed;
                getSnapshotsObject(root);
                return root;
            }
        } catch (Exception ex) {
            return createEmptyRoot();
        } finally {
            reader.close();
        }
        return createEmptyRoot();
    }

    private void writeRoot(File historyDirectory, JSONObject root) throws Exception {
        File databaseFile = getLegacyDatabaseFile(historyDirectory);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(databaseFile), "UTF-8"));
        try {
            writer.write(root.toJSONString());
        } finally {
            writer.close();
        }
    }

    private JSONObject createEmptyRoot() {
        JSONObject root = new JSONObject();
        root.put("version", Long.valueOf(DATABASE_VERSION));
        root.put("updatedDate", "");
        root.put("source", "StockAnalysis");
        root.put("snapshots", new JSONObject());
        return root;
    }

    private JSONObject getSnapshotsObject(JSONObject root) {
        Object existing = root.get("snapshots");
        if (existing instanceof JSONObject) {
            return (JSONObject) existing;
        }
        JSONObject snapshots = new JSONObject();
        root.put("snapshots", snapshots);
        return snapshots;
    }

    private boolean importMissingCsvSnapshots(File historyDirectory, Map<String, Snapshot> snapshotsByDate,
            JSONObject snapshotsObject) throws Exception {
        File[] files = historyDirectory.listFiles();
        if (files == null || files.length == 0) {
            return false;
        }

        List<File> candidates = new ArrayList<File>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            if (snapshotDateFromFile(file.getName()).length() == 0) {
                continue;
            }
            candidates.add(file);
        }

        Collections.sort(candidates, new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });

        boolean changed = false;
        for (File candidate : candidates) {
            String date = snapshotDateFromFile(candidate.getName());
            if (date.length() == 0 || snapshotsByDate.containsKey(date)) {
                continue;
            }
            Snapshot snapshot = readSnapshotFromCsv(candidate, date);
            snapshotsByDate.put(date, snapshot);
            snapshotsObject.put(date, toSnapshotJson(snapshot));
            changed = true;
        }
        return changed;
    }

    private boolean importMissingCsvSnapshotsToSqlite(File historyDirectory, Map<String, Snapshot> snapshotsByDate,
            SQLiteStore sqliteStore) throws Exception {
        File[] files = historyDirectory.listFiles();
        if (files == null || files.length == 0) {
            return false;
        }

        List<File> candidates = new ArrayList<File>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            if (snapshotDateFromFile(file.getName()).length() == 0) {
                continue;
            }
            candidates.add(file);
        }

        Collections.sort(candidates, new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });

        boolean changed = false;
        for (File candidate : candidates) {
            String date = snapshotDateFromFile(candidate.getName());
            if (date.length() == 0 || snapshotsByDate.containsKey(date) || sqliteStore.hasSnapshot(date)) {
                continue;
            }
            Snapshot snapshot = readSnapshotFromCsv(candidate, date);
            sqliteStore.upsertSnapshot(snapshot);
            snapshotsByDate.put(date, snapshot);
            changed = true;
        }
        return changed;
    }

    private Map<String, Snapshot> parseSnapshots(JSONObject snapshotsObject) {
        Map<String, Snapshot> snapshotsByDate = new HashMap<String, Snapshot>();
        for (Object keyObject : snapshotsObject.keySet()) {
            String date = keyObject == null ? "" : keyObject.toString();
            Object snapshotObject = snapshotsObject.get(keyObject);
            if (!(snapshotObject instanceof JSONObject)) {
                continue;
            }
            Snapshot snapshot = parseSnapshotJson(date, (JSONObject) snapshotObject);
            if (snapshot.rows.isEmpty() && date.length() == 0) {
                continue;
            }
            snapshotsByDate.put(snapshot.date, snapshot);
        }
        return snapshotsByDate;
    }

    private Snapshot parseSnapshotJson(String fallbackDate, JSONObject snapshotObject) {
        Snapshot snapshot = new Snapshot();
        snapshot.date = safeText(snapshotObject.get("date"));
        if (snapshot.date.length() == 0) {
            snapshot.date = safeText(fallbackDate);
        }

        Object rowsObject = snapshotObject.get("rows");
        if (!(rowsObject instanceof JSONArray)) {
            return snapshot;
        }

        JSONArray rows = (JSONArray) rowsObject;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof JSONObject)) {
                continue;
            }
            SnapshotRow row = parseRowJson((JSONObject) rowObject, snapshot.date);
            if (row.code.length() == 0) {
                continue;
            }
            snapshot.rows.add(row);
        }
        return snapshot;
    }

    private SnapshotRow parseRowJson(JSONObject rowObject, String snapshotDate) {
        SnapshotRow row = new SnapshotRow();
        row.date = snapshotDate;
        row.code = safeText(rowObject.get("code"));
        row.name = safeText(rowObject.get("name"));
        row.market = safeText(rowObject.get("market"));
        row.industry = safeText(rowObject.get("industry"));
        row.note = safeText(rowObject.get("note"));
        row.score = numberValue(rowObject.get("score"));
        row.price = numberValue(rowObject.get("price"));
        row.volumeRatio = numberValue(rowObject.get("volumeRatio"));
        row.return20DayPct = numberValue(rowObject.get("return20DayPct"));
        row.rawScore = numberValue(rowObject.get("rawScore"));
        row.selectionScore = numberValue(rowObject.get("selectionScore"));
        row.momentumScore = numberValue(rowObject.get("momentumScore"));
        row.qualityScore = numberValue(rowObject.get("qualityScore"));
        row.sectorScore = numberValue(rowObject.get("sectorScore"));
        row.themeScore = numberValue(rowObject.get("themeScore"));
        row.trendPersistenceScore = numberValue(rowObject.get("trendPersistenceScore"));
        row.trendPersistenceDays = (int) numberValue(rowObject.get("trendPersistenceDays"));
        row.newsScore = numberValue(rowObject.get("newsScore"));
        row.newsRiskScore = numberValue(rowObject.get("newsRiskScore"));
        row.relativeStrengthScore = numberValue(rowObject.get("relativeStrengthScore"));
        row.industryReturnStrength = numberValue(rowObject.get("industryReturnStrength"));
        row.industryVolumeStrength = numberValue(rowObject.get("industryVolumeStrength"));
        row.industryFlowStrength = numberValue(rowObject.get("industryFlowStrength"));
        row.eventDirection = safeText(rowObject.get("eventDirection"));
        row.eventConfidence = numberValue(rowObject.get("eventConfidence"));
        row.eventFreshnessDays = (int) numberValue(rowObject.get("eventFreshnessDays"));
        row.eventTypeSummary = safeText(rowObject.get("eventTypeSummary"));
        row.newsDigest = safeText(rowObject.get("newsDigest"));
        row.newsSourceSummary = safeText(rowObject.get("newsSourceSummary"));
        row.latestNewsPublishedHint = safeText(rowObject.get("latestNewsPublishedHint"));
        row.newsSourceCredibilityScore = numberValue(rowObject.get("newsSourceCredibilityScore"));
        row.newsFreshnessScore = numberValue(rowObject.get("newsFreshnessScore"));
        row.newsSourceCount = (int) numberValue(rowObject.get("newsSourceCount"));
        row.newsOfficialSourceCount = (int) numberValue(rowObject.get("newsOfficialSourceCount"));
        row.newsMediaSourceCount = (int) numberValue(rowObject.get("newsMediaSourceCount"));
        row.structureScore = numberValue(rowObject.get("structureScore"));
        row.riskRewardScore = numberValue(rowObject.get("riskRewardScore"));
        row.riskRewardRatio = numberValue(rowObject.get("riskRewardRatio"));
        row.turnaroundScore = numberValue(rowObject.get("turnaroundScore"));
        row.revenueGrowthSignalScore = numberValue(rowObject.get("revenueGrowthSignalScore"));
        row.earningsTurnaroundSignalScore = numberValue(rowObject.get("earningsTurnaroundSignalScore"));
        row.profitabilityTurnaroundSignalScore = numberValue(rowObject.get("profitabilityTurnaroundSignalScore"));
        row.oneOffRiskScore = numberValue(rowObject.get("oneOffRiskScore"));
        row.suggestedStopPrice = numberValue(rowObject.get("suggestedStopPrice"));
        row.suggestedStopPct = numberValue(rowObject.get("suggestedStopPct"));
        row.suggestedTargetPrice = numberValue(rowObject.get("suggestedTargetPrice"));
        row.upsidePotentialPct = numberValue(rowObject.get("upsidePotentialPct"));
        row.buyPointScore = numberValue(rowObject.get("buyPointScore"));
        row.dataConfidence = numberValue(rowObject.get("dataConfidence"));
        row.selectionQualified = booleanValue(rowObject.get("selectionQualified"));
        row.liquidityScore = numberValue(rowObject.get("liquidityScore"));
        row.revenueScore = numberValue(rowObject.get("revenueScore"));
        row.chipsScore = numberValue(rowObject.get("chipsScore"));
        row.valuationScore = numberValue(rowObject.get("valuationScore"));
        row.technicalScore = numberValue(rowObject.get("technicalScore"));
        row.financialQualityScore = numberValue(rowObject.get("financialQualityScore"));
        row.fiveDayInstitutionalNetRatioPct = numberValue(rowObject.get("fiveDayInstitutionalNetRatioPct"));
        row.brokerNetRatioPct = numberValue(rowObject.get("brokerNetRatioPct"));
        row.rsi14 = numberValue(rowObject.get("rsi14"));
        row.stochasticK = numberValue(rowObject.get("stochasticK"));
        row.stochasticD = numberValue(rowObject.get("stochasticD"));
        row.movingAverage20 = numberValue(rowObject.get("movingAverage20"));
        row.movingAverage60 = numberValue(rowObject.get("movingAverage60"));
        row.movingAverage120 = numberValue(rowObject.get("movingAverage120"));
        row.return60DayPct = numberValue(rowObject.get("return60DayPct"));
        row.averageLots20 = numberValue(rowObject.get("averageLots20"));
        row.averageTradeValue20Billion = numberValue(rowObject.get("averageTradeValue20Billion"));
        row.volatility20Pct = numberValue(rowObject.get("volatility20Pct"));
        row.drawdownFromHigh60Pct = numberValue(rowObject.get("drawdownFromHigh60Pct"));
        row.epsAccelerationPct = numberValue(rowObject.get("epsAccelerationPct"));
        row.peg = numberValue(rowObject.get("peg"));
        row.scoreReason = safeText(rowObject.get("scoreReason"));
        row.revenueReason = safeText(rowObject.get("revenueReason"));
        row.chipsReason = safeText(rowObject.get("chipsReason"));
        row.liquidityReason = safeText(rowObject.get("liquidityReason"));
        row.valuationReason = safeText(rowObject.get("valuationReason"));
        row.technicalReason = safeText(rowObject.get("technicalReason"));
        row.financialQualityReason = safeText(rowObject.get("financialQualityReason"));
        row.eventRiskReason = safeText(rowObject.get("eventRiskReason"));
        row.eligibilityReason = safeText(rowObject.get("eligibilityReason"));
        row.primaryTheme = safeText(rowObject.get("primaryTheme"));
        row.themeTags = safeText(rowObject.get("themeTags"));
        row.newsSummary = safeText(rowObject.get("newsSummary"));
        if (row.newsDigest.length() == 0) {
            row.newsDigest = row.newsSummary;
        }
        row.structureLabel = safeText(rowObject.get("structureLabel"));
        row.turnaroundLabel = safeText(rowObject.get("turnaroundLabel"));
        row.turnaroundReason = safeText(rowObject.get("turnaroundReason"));
        row.buyPointLabel = safeText(rowObject.get("buyPointLabel"));
        row.buyPointReason = safeText(rowObject.get("buyPointReason"));
        row.dataConfidenceReason = safeText(rowObject.get("dataConfidenceReason"));
        row.signalType = safeText(rowObject.get("signalType"));
        row.signalHorizonDays = (int) numberValue(rowObject.get("signalHorizonDays"));
        row.entryRule = safeText(rowObject.get("entryRule"));
        row.exitRule = safeText(rowObject.get("exitRule"));
        row.validationMode = safeText(rowObject.get("validationMode"));
        row.hardExclude = booleanValue(rowObject.get("hardExclude"));
        row.hardExcludeReason = safeText(rowObject.get("hardExcludeReason"));
        row.dataQualityGrade = safeText(rowObject.get("dataQualityGrade"));
        row.winratePriorityScore = numberValue(rowObject.get("winratePriorityScore"));
        row.expectedReturnScore = numberValue(rowObject.get("expectedReturnScore"));
        row.maxDrawdownPenalty = numberValue(rowObject.get("maxDrawdownPenalty"));
        row.backtestCohort = safeText(rowObject.get("backtestCohort"));
        row.postClosePriorityScore = numberValue(rowObject.get("postClosePriorityScore"));
        row.postCloseCategory = safeText(rowObject.get("postCloseCategory"));
        row.postCloseAction = safeText(rowObject.get("postCloseAction"));
        row.postCloseReason = safeText(rowObject.get("postCloseReason"));
        if (row.rawScore <= 0D) {
            row.rawScore = row.score;
        }
        if (row.selectionScore <= 0D) {
            row.selectionScore = row.score;
        }
        if (!row.selectionQualified) {
            row.selectionQualified = isSelectionQualified(row);
        }
        row.likely = rowObject.containsKey("likely") ? booleanValue(rowObject.get("likely")) : isLikelyCandidate(row);
        return row;
    }

    private JSONObject toSnapshotJson(String date, List<StockAnalysisResultVO> results) {
        return toSnapshotJson(buildSnapshot(date, results));
    }

    private JSONObject toSnapshotJson(Snapshot snapshot) {
        JSONObject snapshotObject = new JSONObject();
        snapshotObject.put("date", safeText(snapshot.date));
        snapshotObject.put("rowCount", Long.valueOf(snapshot.rows.size()));
        JSONArray rows = new JSONArray();
        for (SnapshotRow row : snapshot.rows) {
            rows.add(toRowJson(row));
        }
        snapshotObject.put("rows", rows);
        return snapshotObject;
    }

    private SnapshotRow toRow(StockAnalysisResultVO result, String date) {
        SnapshotRow row = new SnapshotRow();
        row.date = date;
        row.code = safeText(result.getStock().getCode());
        row.name = safeText(result.getStock().getName());
        row.market = safeText(result.getStock().getMarket());
        row.industry = safeText(result.getIndustry());
        row.note = safeText(result.getAnalysisNote());
        row.score = safeNumber(result.getScore());
        row.rawScore = safeNumber(result.getRawScore());
        row.selectionScore = safeNumber(result.getSelectionScore());
        row.momentumScore = safeNumber(result.getMomentumScore());
        row.qualityScore = safeNumber(result.getQualityScore());
        row.sectorScore = safeNumber(result.getSectorScore());
        row.themeScore = safeNumber(result.getThemeScore());
        row.trendPersistenceScore = safeNumber(result.getTrendPersistenceScore());
        row.trendPersistenceDays = result.getTrendPersistenceDays();
        row.newsScore = safeNumber(result.getNewsScore());
        row.newsRiskScore = safeNumber(result.getNewsRiskScore());
        row.relativeStrengthScore = safeNumber(result.getRelativeStrengthScore());
        row.industryReturnStrength = safeNumber(result.getIndustryReturnStrength());
        row.industryVolumeStrength = safeNumber(result.getIndustryVolumeStrength());
        row.industryFlowStrength = safeNumber(result.getIndustryFlowStrength());
        row.eventDirection = safeText(result.getEventDirection());
        row.eventConfidence = safeNumber(result.getEventConfidence());
        row.eventFreshnessDays = result.getEventFreshnessDays();
        row.eventTypeSummary = safeText(result.getEventTypeSummary());
        row.structureScore = safeNumber(result.getStructureScore());
        row.riskRewardScore = safeNumber(result.getRiskRewardScore());
        row.riskRewardRatio = safeNumber(result.getRiskRewardRatio());
        row.turnaroundScore = safeNumber(result.getTurnaroundScore());
        row.revenueGrowthSignalScore = safeNumber(result.getRevenueGrowthSignalScore());
        row.earningsTurnaroundSignalScore = safeNumber(result.getEarningsTurnaroundSignalScore());
        row.profitabilityTurnaroundSignalScore = safeNumber(result.getProfitabilityTurnaroundSignalScore());
        row.oneOffRiskScore = safeNumber(result.getOneOffRiskScore());
        row.suggestedStopPrice = safeNumber(result.getSuggestedStopPrice());
        row.suggestedStopPct = safeNumber(result.getSuggestedStopPct());
        row.suggestedTargetPrice = safeNumber(result.getSuggestedTargetPrice());
        row.upsidePotentialPct = safeNumber(result.getUpsidePotentialPct());
        row.buyPointScore = safeNumber(result.getBuyPointScore());
        row.dataConfidence = safeNumber(result.getDataConfidence());
        row.selectionQualified = result.isSelectionQualified();
        row.price = safeNumber(result.getCurrentPrice());
        row.volumeRatio = safeNumber(result.getVolumeRatio());
        row.return20DayPct = safeNumber(result.getReturn20DayPct());
        row.return60DayPct = safeNumber(result.getReturn60DayPct());
        row.movingAverage20 = safeNumber(result.getMovingAverage20());
        row.movingAverage60 = safeNumber(result.getMovingAverage60());
        row.movingAverage120 = safeNumber(result.getMovingAverage120());
        row.averageLots20 = safeNumber(result.getAverageLots20());
        row.averageTradeValue20Billion = safeNumber(result.getAverageTradeValue20Billion());
        row.volatility20Pct = safeNumber(result.getVolatility20Pct());
        row.drawdownFromHigh60Pct = safeNumber(result.getDrawdownFromHigh60Pct());
        row.liquidityScore = safeNumber(result.getLiquidityScore());
        row.revenueScore = safeNumber(result.getRevenueScore());
        row.chipsScore = safeNumber(result.getChipsScore());
        row.valuationScore = safeNumber(result.getValuationScore());
        row.technicalScore = safeNumber(result.getTechnicalScore());
        row.financialQualityScore = safeNumber(result.getFinancialQualityScore());
        row.fiveDayInstitutionalNetRatioPct = safeNumber(result.getFiveDayInstitutionalNetRatioPct());
        row.brokerNetRatioPct = safeNumber(result.getBrokerNetRatioPct());
        row.rsi14 = safeNumber(result.getRsi14());
        row.stochasticK = safeNumber(result.getStochasticK());
        row.stochasticD = safeNumber(result.getStochasticD());
        row.epsAccelerationPct = safeNumber(result.getEpsAccelerationPct());
        row.peg = safeNumber(result.getPeg());
        row.scoreReason = safeText(result.getScoreReason());
        row.revenueReason = safeText(result.getRevenueReason());
        row.chipsReason = safeText(result.getChipsReason());
        row.liquidityReason = safeText(result.getLiquidityReason());
        row.valuationReason = safeText(result.getValuationReason());
        row.technicalReason = safeText(result.getTechnicalReason());
        row.financialQualityReason = safeText(result.getFinancialQualityReason());
        row.eventRiskReason = safeText(result.getEventRiskReason());
        row.eligibilityReason = safeText(result.getEligibilityReason());
        row.primaryTheme = safeText(result.getPrimaryTheme());
        row.themeTags = safeText(result.getThemeTags());
        row.newsSummary = safeText(result.getNewsSummary());
        row.newsDigest = safeText(result.getNewsDigest());
        row.newsSourceSummary = safeText(result.getNewsSourceSummary());
        row.latestNewsPublishedHint = safeText(result.getLatestNewsPublishedHint());
        row.newsSourceCredibilityScore = safeNumber(result.getNewsSourceCredibilityScore());
        row.newsFreshnessScore = safeNumber(result.getNewsFreshnessScore());
        row.newsSourceCount = result.getNewsSourceCount();
        row.newsOfficialSourceCount = result.getNewsOfficialSourceCount();
        row.newsMediaSourceCount = result.getNewsMediaSourceCount();
        row.structureLabel = safeText(result.getStructureLabel());
        row.turnaroundLabel = safeText(result.getTurnaroundLabel());
        row.turnaroundReason = safeText(result.getTurnaroundReason());
        row.buyPointLabel = safeText(result.getBuyPointLabel());
        row.buyPointReason = safeText(result.getBuyPointReason());
        row.dataConfidenceReason = safeText(result.getDataConfidenceReason());
        row.signalType = safeText(result.getSignalType());
        row.signalHorizonDays = result.getSignalHorizonDays();
        row.entryRule = safeText(result.getEntryRule());
        row.exitRule = safeText(result.getExitRule());
        row.validationMode = safeText(result.getValidationMode());
        row.hardExclude = result.isHardExclude();
        row.hardExcludeReason = safeText(result.getHardExcludeReason());
        row.dataQualityGrade = safeText(result.getDataQualityGrade());
        row.winratePriorityScore = safeNumber(result.getWinratePriorityScore());
        row.expectedReturnScore = safeNumber(result.getExpectedReturnScore());
        row.maxDrawdownPenalty = safeNumber(result.getMaxDrawdownPenalty());
        row.backtestCohort = safeText(result.getBacktestCohort());
        row.postClosePriorityScore = safeNumber(result.getPostClosePriorityScore());
        row.postCloseCategory = safeText(result.getPostCloseCategory());
        row.postCloseAction = safeText(result.getPostCloseAction());
        row.postCloseReason = safeText(result.getPostCloseReason());
        row.likely = isLikelyCandidate(row);
        return row;
    }

    private JSONObject toRowJson(SnapshotRow row) {
        JSONObject rowObject = new JSONObject();
        rowObject.put("code", safeText(row.code));
        rowObject.put("name", safeText(row.name));
        rowObject.put("market", safeText(row.market));
        rowObject.put("industry", safeText(row.industry));
        rowObject.put("note", safeText(row.note));
        rowObject.put("score", Double.valueOf(safeNumber(row.score)));
        rowObject.put("rawScore", Double.valueOf(safeNumber(row.rawScore)));
        rowObject.put("selectionScore", Double.valueOf(safeNumber(row.selectionScore)));
        rowObject.put("momentumScore", Double.valueOf(safeNumber(row.momentumScore)));
        rowObject.put("qualityScore", Double.valueOf(safeNumber(row.qualityScore)));
        rowObject.put("sectorScore", Double.valueOf(safeNumber(row.sectorScore)));
        rowObject.put("themeScore", Double.valueOf(safeNumber(row.themeScore)));
        rowObject.put("trendPersistenceScore", Double.valueOf(safeNumber(row.trendPersistenceScore)));
        rowObject.put("trendPersistenceDays", Long.valueOf(row.trendPersistenceDays));
        rowObject.put("newsScore", Double.valueOf(safeNumber(row.newsScore)));
        rowObject.put("newsRiskScore", Double.valueOf(safeNumber(row.newsRiskScore)));
        rowObject.put("relativeStrengthScore", Double.valueOf(safeNumber(row.relativeStrengthScore)));
        rowObject.put("industryReturnStrength", Double.valueOf(safeNumber(row.industryReturnStrength)));
        rowObject.put("industryVolumeStrength", Double.valueOf(safeNumber(row.industryVolumeStrength)));
        rowObject.put("industryFlowStrength", Double.valueOf(safeNumber(row.industryFlowStrength)));
        rowObject.put("eventDirection", safeText(row.eventDirection));
        rowObject.put("eventConfidence", Double.valueOf(safeNumber(row.eventConfidence)));
        rowObject.put("eventFreshnessDays", Integer.valueOf(row.eventFreshnessDays));
        rowObject.put("eventTypeSummary", safeText(row.eventTypeSummary));
        rowObject.put("structureScore", Double.valueOf(safeNumber(row.structureScore)));
        rowObject.put("riskRewardScore", Double.valueOf(safeNumber(row.riskRewardScore)));
        rowObject.put("riskRewardRatio", Double.valueOf(safeNumber(row.riskRewardRatio)));
        rowObject.put("turnaroundScore", Double.valueOf(safeNumber(row.turnaroundScore)));
        rowObject.put("revenueGrowthSignalScore", Double.valueOf(safeNumber(row.revenueGrowthSignalScore)));
        rowObject.put("earningsTurnaroundSignalScore",
                Double.valueOf(safeNumber(row.earningsTurnaroundSignalScore)));
        rowObject.put("profitabilityTurnaroundSignalScore",
                Double.valueOf(safeNumber(row.profitabilityTurnaroundSignalScore)));
        rowObject.put("oneOffRiskScore", Double.valueOf(safeNumber(row.oneOffRiskScore)));
        rowObject.put("suggestedStopPrice", Double.valueOf(safeNumber(row.suggestedStopPrice)));
        rowObject.put("suggestedStopPct", Double.valueOf(safeNumber(row.suggestedStopPct)));
        rowObject.put("suggestedTargetPrice", Double.valueOf(safeNumber(row.suggestedTargetPrice)));
        rowObject.put("upsidePotentialPct", Double.valueOf(safeNumber(row.upsidePotentialPct)));
        rowObject.put("buyPointScore", Double.valueOf(safeNumber(row.buyPointScore)));
        rowObject.put("dataConfidence", Double.valueOf(safeNumber(row.dataConfidence)));
        rowObject.put("selectionQualified", Boolean.valueOf(row.selectionQualified));
        rowObject.put("price", Double.valueOf(safeNumber(row.price)));
        rowObject.put("volumeRatio", Double.valueOf(safeNumber(row.volumeRatio)));
        rowObject.put("return20DayPct", Double.valueOf(safeNumber(row.return20DayPct)));
        rowObject.put("return60DayPct", Double.valueOf(safeNumber(row.return60DayPct)));
        rowObject.put("movingAverage20", Double.valueOf(safeNumber(row.movingAverage20)));
        rowObject.put("movingAverage60", Double.valueOf(safeNumber(row.movingAverage60)));
        rowObject.put("movingAverage120", Double.valueOf(safeNumber(row.movingAverage120)));
        rowObject.put("averageLots20", Double.valueOf(safeNumber(row.averageLots20)));
        rowObject.put("averageTradeValue20Billion", Double.valueOf(safeNumber(row.averageTradeValue20Billion)));
        rowObject.put("volatility20Pct", Double.valueOf(safeNumber(row.volatility20Pct)));
        rowObject.put("drawdownFromHigh60Pct", Double.valueOf(safeNumber(row.drawdownFromHigh60Pct)));
        rowObject.put("liquidityScore", Double.valueOf(safeNumber(row.liquidityScore)));
        rowObject.put("revenueScore", Double.valueOf(safeNumber(row.revenueScore)));
        rowObject.put("chipsScore", Double.valueOf(safeNumber(row.chipsScore)));
        rowObject.put("valuationScore", Double.valueOf(safeNumber(row.valuationScore)));
        rowObject.put("technicalScore", Double.valueOf(safeNumber(row.technicalScore)));
        rowObject.put("financialQualityScore", Double.valueOf(safeNumber(row.financialQualityScore)));
        rowObject.put("fiveDayInstitutionalNetRatioPct",
                Double.valueOf(safeNumber(row.fiveDayInstitutionalNetRatioPct)));
        rowObject.put("brokerNetRatioPct", Double.valueOf(safeNumber(row.brokerNetRatioPct)));
        rowObject.put("rsi14", Double.valueOf(safeNumber(row.rsi14)));
        rowObject.put("stochasticK", Double.valueOf(safeNumber(row.stochasticK)));
        rowObject.put("stochasticD", Double.valueOf(safeNumber(row.stochasticD)));
        rowObject.put("epsAccelerationPct", Double.valueOf(safeNumber(row.epsAccelerationPct)));
        rowObject.put("peg", Double.valueOf(safeNumber(row.peg)));
        rowObject.put("scoreReason", safeText(row.scoreReason));
        rowObject.put("revenueReason", safeText(row.revenueReason));
        rowObject.put("chipsReason", safeText(row.chipsReason));
        rowObject.put("liquidityReason", safeText(row.liquidityReason));
        rowObject.put("valuationReason", safeText(row.valuationReason));
        rowObject.put("technicalReason", safeText(row.technicalReason));
        rowObject.put("financialQualityReason", safeText(row.financialQualityReason));
        rowObject.put("eventRiskReason", safeText(row.eventRiskReason));
        rowObject.put("eligibilityReason", safeText(row.eligibilityReason));
        rowObject.put("primaryTheme", safeText(row.primaryTheme));
        rowObject.put("themeTags", safeText(row.themeTags));
        rowObject.put("newsSummary", safeText(row.newsSummary));
        rowObject.put("newsDigest", safeText(row.newsDigest));
        rowObject.put("newsSourceSummary", safeText(row.newsSourceSummary));
        rowObject.put("latestNewsPublishedHint", safeText(row.latestNewsPublishedHint));
        rowObject.put("newsSourceCredibilityScore", Double.valueOf(safeNumber(row.newsSourceCredibilityScore)));
        rowObject.put("newsFreshnessScore", Double.valueOf(safeNumber(row.newsFreshnessScore)));
        rowObject.put("newsSourceCount", Integer.valueOf(row.newsSourceCount));
        rowObject.put("newsOfficialSourceCount", Integer.valueOf(row.newsOfficialSourceCount));
        rowObject.put("newsMediaSourceCount", Integer.valueOf(row.newsMediaSourceCount));
        rowObject.put("structureLabel", safeText(row.structureLabel));
        rowObject.put("turnaroundLabel", safeText(row.turnaroundLabel));
        rowObject.put("turnaroundReason", safeText(row.turnaroundReason));
        rowObject.put("buyPointLabel", safeText(row.buyPointLabel));
        rowObject.put("buyPointReason", safeText(row.buyPointReason));
        rowObject.put("dataConfidenceReason", safeText(row.dataConfidenceReason));
        rowObject.put("signalType", safeText(row.signalType));
        rowObject.put("signalHorizonDays", Integer.valueOf(row.signalHorizonDays));
        rowObject.put("entryRule", safeText(row.entryRule));
        rowObject.put("exitRule", safeText(row.exitRule));
        rowObject.put("validationMode", safeText(row.validationMode));
        rowObject.put("hardExclude", Boolean.valueOf(row.hardExclude));
        rowObject.put("hardExcludeReason", safeText(row.hardExcludeReason));
        rowObject.put("dataQualityGrade", safeText(row.dataQualityGrade));
        rowObject.put("winratePriorityScore", Double.valueOf(safeNumber(row.winratePriorityScore)));
        rowObject.put("expectedReturnScore", Double.valueOf(safeNumber(row.expectedReturnScore)));
        rowObject.put("maxDrawdownPenalty", Double.valueOf(safeNumber(row.maxDrawdownPenalty)));
        rowObject.put("backtestCohort", safeText(row.backtestCohort));
        rowObject.put("postClosePriorityScore", Double.valueOf(safeNumber(row.postClosePriorityScore)));
        rowObject.put("postCloseCategory", safeText(row.postCloseCategory));
        rowObject.put("postCloseAction", safeText(row.postCloseAction));
        rowObject.put("postCloseReason", safeText(row.postCloseReason));
        rowObject.put("likely", Boolean.valueOf(row.likely));
        return rowObject;
    }

    private Snapshot readSnapshotFromCsv(File file, String date) throws Exception {
        Snapshot snapshot = new Snapshot();
        snapshot.date = date;

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return snapshot;
            }

            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> indexes = buildHeaderIndexes(headers);

            String line = null;
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.isEmpty()) {
                    continue;
                }

                SnapshotRow row = new SnapshotRow();
                row.date = date;
                row.code = valueAt(fields, indexes, "code");
                if (row.code.length() == 0) {
                    continue;
                }
                row.name = valueAt(fields, indexes, "name");
                row.market = valueAt(fields, indexes, "market");
                row.industry = valueAt(fields, indexes, "industry");
                row.note = valueAt(fields, indexes, "note");
                row.score = NumberParser.parseDouble(valueAt(fields, indexes, "score"));
                row.rawScore = NumberParser.parseDouble(valueAt(fields, indexes, "raw_score"));
                row.selectionScore = NumberParser.parseDouble(valueAt(fields, indexes, "selection_score"));
                row.momentumScore = NumberParser.parseDouble(valueAt(fields, indexes, "momentum_score"));
                row.qualityScore = NumberParser.parseDouble(valueAt(fields, indexes, "quality_score"));
                row.sectorScore = NumberParser.parseDouble(valueAt(fields, indexes, "sector_score"));
                row.themeScore = NumberParser.parseDouble(valueAt(fields, indexes, "theme_score"));
                row.trendPersistenceScore = NumberParser.parseDouble(valueAt(fields, indexes, "trend_persistence_score"));
                row.trendPersistenceDays = (int) NumberParser
                        .parseDouble(valueAt(fields, indexes, "trend_persistence_days"));
                row.newsScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_score"));
                row.newsRiskScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_risk_score"));
                row.relativeStrengthScore = NumberParser.parseDouble(valueAt(fields, indexes, "relative_strength_score"));
                row.industryReturnStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_return_strength"));
                row.industryVolumeStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_volume_strength"));
                row.industryFlowStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_flow_strength"));
                row.eventDirection = valueAt(fields, indexes, "event_direction");
                row.eventConfidence = NumberParser.parseDouble(valueAt(fields, indexes, "event_confidence"));
                row.eventFreshnessDays = (int) NumberParser.parseDouble(valueAt(fields, indexes, "event_freshness_days"));
                row.eventTypeSummary = valueAt(fields, indexes, "event_type_summary");
                row.newsSummary = valueAt(fields, indexes, "news_summary");
                row.newsDigest = valueAt(fields, indexes, "news_digest");
                row.newsSourceSummary = valueAt(fields, indexes, "news_source_summary");
                row.latestNewsPublishedHint = valueAt(fields, indexes, "latest_news_published_hint");
                row.newsSourceCredibilityScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_source_credibility_score"));
                row.newsFreshnessScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_freshness_score"));
                row.newsSourceCount = (int) NumberParser.parseDouble(valueAt(fields, indexes, "news_source_count"));
                row.newsOfficialSourceCount = (int) NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_official_source_count"));
                row.newsMediaSourceCount = (int) NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_media_source_count"));
                if (row.newsDigest.length() == 0) {
                    row.newsDigest = row.newsSummary;
                }
                row.structureScore = NumberParser.parseDouble(valueAt(fields, indexes, "structure_score"));
                row.riskRewardScore = NumberParser.parseDouble(valueAt(fields, indexes, "risk_reward_score"));
                row.riskRewardRatio = NumberParser.parseDouble(valueAt(fields, indexes, "risk_reward_ratio"));
                row.turnaroundScore = NumberParser.parseDouble(valueAt(fields, indexes, "turnaround_score"));
                row.revenueGrowthSignalScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "revenue_growth_signal_score"));
                row.earningsTurnaroundSignalScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "earnings_turnaround_signal_score"));
                row.profitabilityTurnaroundSignalScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "profitability_turnaround_signal_score"));
                row.oneOffRiskScore = NumberParser.parseDouble(valueAt(fields, indexes, "one_off_risk_score"));
                row.suggestedStopPrice = NumberParser.parseDouble(valueAt(fields, indexes, "suggested_stop_price"));
                row.suggestedStopPct = NumberParser.parseDouble(valueAt(fields, indexes, "suggested_stop_pct"));
                row.suggestedTargetPrice = NumberParser.parseDouble(valueAt(fields, indexes, "suggested_target_price"));
                row.upsidePotentialPct = NumberParser.parseDouble(valueAt(fields, indexes, "upside_potential_pct"));
                row.buyPointScore = NumberParser.parseDouble(valueAt(fields, indexes, "buy_point_score"));
                row.dataConfidence = NumberParser.parseDouble(valueAt(fields, indexes, "data_confidence"));
                row.selectionQualified = "Y".equalsIgnoreCase(valueAt(fields, indexes, "selection_qualified"))
                        || "true".equalsIgnoreCase(valueAt(fields, indexes, "selection_qualified"));
                row.eligibilityReason = valueAt(fields, indexes, "eligibility_reason");
                row.primaryTheme = valueAt(fields, indexes, "primary_theme");
                row.themeTags = valueAt(fields, indexes, "theme_tags");
                row.structureLabel = valueAt(fields, indexes, "structure_label");
                row.turnaroundLabel = valueAt(fields, indexes, "turnaround_label");
                row.turnaroundReason = valueAt(fields, indexes, "turnaround_reason");
                row.buyPointLabel = valueAt(fields, indexes, "buy_point_label");
                row.buyPointReason = valueAt(fields, indexes, "buy_point_reason");
                row.signalType = valueAt(fields, indexes, "signal_type");
                row.signalHorizonDays = (int) NumberParser.parseDouble(valueAt(fields, indexes, "signal_horizon_days"));
                row.entryRule = valueAt(fields, indexes, "entry_rule");
                row.exitRule = valueAt(fields, indexes, "exit_rule");
                row.validationMode = valueAt(fields, indexes, "validation_mode");
                row.hardExclude = "Y".equalsIgnoreCase(valueAt(fields, indexes, "hard_exclude"))
                        || "true".equalsIgnoreCase(valueAt(fields, indexes, "hard_exclude"));
                row.hardExcludeReason = valueAt(fields, indexes, "hard_exclude_reason");
                row.dataQualityGrade = valueAt(fields, indexes, "data_quality_grade");
                row.winratePriorityScore = NumberParser.parseDouble(valueAt(fields, indexes, "winrate_priority_score"));
                row.expectedReturnScore = NumberParser.parseDouble(valueAt(fields, indexes, "expected_return_score"));
                row.maxDrawdownPenalty = NumberParser.parseDouble(valueAt(fields, indexes, "max_drawdown_penalty"));
                row.backtestCohort = valueAt(fields, indexes, "backtest_cohort");
                row.postClosePriorityScore = NumberParser.parseDouble(valueAt(fields, indexes, "post_close_priority_score"));
                row.postCloseCategory = valueAt(fields, indexes, "post_close_category");
                row.postCloseAction = valueAt(fields, indexes, "post_close_action");
                row.postCloseReason = valueAt(fields, indexes, "post_close_reason");
                row.dataConfidenceReason = valueAt(fields, indexes, "data_confidence_reason");
                row.price = NumberParser.parseDouble(valueAt(fields, indexes, "current_price"));
                row.volumeRatio = NumberParser.parseDouble(valueAt(fields, indexes, "volume_ratio"));
                row.return20DayPct = NumberParser.parseDouble(valueAt(fields, indexes, "return_20d_pct"));
                row.return60DayPct = NumberParser.parseDouble(valueAt(fields, indexes, "return_60d_pct"));
                row.movingAverage20 = NumberParser.parseDouble(valueAt(fields, indexes, "ma20"));
                row.movingAverage60 = NumberParser.parseDouble(valueAt(fields, indexes, "ma60"));
                row.movingAverage120 = NumberParser.parseDouble(valueAt(fields, indexes, "ma120"));
                row.averageLots20 = NumberParser.parseDouble(valueAt(fields, indexes, "avg_lots_20"));
                row.averageTradeValue20Billion = NumberParser.parseDouble(valueAt(fields, indexes,
                        "avg_trade_value_20_billion"));
                row.volatility20Pct = NumberParser.parseDouble(valueAt(fields, indexes, "volatility_20_pct"));
                row.drawdownFromHigh60Pct = NumberParser.parseDouble(valueAt(fields, indexes,
                        "drawdown_from_high60_pct"));
                row.liquidityScore = NumberParser.parseDouble(valueAt(fields, indexes, "liquidity_score"));
                row.revenueScore = NumberParser.parseDouble(valueAt(fields, indexes, "revenue_score"));
                row.chipsScore = NumberParser.parseDouble(valueAt(fields, indexes, "chips_score"));
                row.valuationScore = NumberParser.parseDouble(valueAt(fields, indexes, "valuation_score"));
                row.technicalScore = NumberParser.parseDouble(valueAt(fields, indexes, "technical_score"));
                row.financialQualityScore = NumberParser.parseDouble(valueAt(fields, indexes, "financial_quality_score"));
                row.fiveDayInstitutionalNetRatioPct = NumberParser
                        .parseDouble(valueAt(fields, indexes, "five_day_institutional_net_ratio_pct"));
                row.brokerNetRatioPct = NumberParser.parseDouble(valueAt(fields, indexes, "broker_net_ratio_pct"));
                row.scoreReason = valueAt(fields, indexes, "score_reason");
                row.revenueReason = valueAt(fields, indexes, "revenue_reason");
                row.chipsReason = valueAt(fields, indexes, "chips_reason");
                row.liquidityReason = valueAt(fields, indexes, "liquidity_reason");
                row.valuationReason = valueAt(fields, indexes, "valuation_reason");
                row.technicalReason = valueAt(fields, indexes, "technical_reason");
                row.financialQualityReason = valueAt(fields, indexes, "financial_quality_reason");
                row.eventRiskReason = valueAt(fields, indexes, "event_risk_reason");
                if (row.rawScore <= 0D) {
                    row.rawScore = row.score;
                }
                if (row.selectionScore <= 0D) {
                    row.selectionScore = row.score;
                }
                if (!row.selectionQualified) {
                    row.selectionQualified = isSelectionQualified(row);
                }
                row.likely = isLikelyCandidate(row);
                snapshot.rows.add(row);
            }
        } finally {
            reader.close();
        }
        return snapshot;
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

    private boolean isLikelyCandidate(StockAnalysisResultVO result) {
        return result.getSelectionScore() >= LIKELY_THRESHOLD && result.isSelectionQualified()
                && result.getFinancialQualityScore() >= MIN_LIKELY_FINANCIAL_SCORE
                && isHealthyVolumeRatio(result.getVolumeRatio());
    }

    private boolean isSelectionQualified(SnapshotRow row) {
        return row.liquidityScore >= MIN_LIQUIDITY_SCORE && row.financialQualityScore >= MIN_SELECTION_FINANCIAL_SCORE;
    }

    private boolean isLikelyCandidate(SnapshotRow row) {
        return row.selectionScore >= LIKELY_THRESHOLD && isSelectionQualified(row)
                && row.financialQualityScore >= MIN_LIKELY_FINANCIAL_SCORE
                && isHealthyVolumeRatio(row.volumeRatio);
    }

    private boolean isHealthyVolumeRatio(double volumeRatio) {
        return volumeRatio >= LIKELY_MIN_VOLUME_RATIO && volumeRatio <= LIKELY_MAX_VOLUME_RATIO;
    }

    private double numberValue(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return NumberParser.parseDouble(value.toString());
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private String safeText(Object value) {
        return value == null ? "" : value.toString();
    }

    private double safeNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return value;
    }

    private String resolveLatestDate(Map<String, Snapshot> snapshotsByDate) {
        if (snapshotsByDate.isEmpty()) {
            return "";
        }
        List<String> dates = new ArrayList<String>(snapshotsByDate.keySet());
        Collections.sort(dates);
        return dates.get(dates.size() - 1);
    }

    private String snapshotDateFromFile(String fileName) {
        if (!fileName.startsWith(SNAPSHOT_PREFIX) || !fileName.endsWith(SNAPSHOT_SUFFIX)) {
            return "";
        }
        String date = fileName.substring(SNAPSHOT_PREFIX.length(), fileName.length() - SNAPSHOT_SUFFIX.length());
        return date.matches("\\d{8}") ? date : "";
    }

    private File ensureHistoryDirectory() {
        File historyDirectory = new File(HISTORY_DIRECTORY_NAME);
        if (!historyDirectory.exists()) {
            historyDirectory.mkdirs();
        }
        return historyDirectory;
    }

    private File getLegacyDatabaseFile(File historyDirectory) {
        return new File(historyDirectory, LEGACY_DATABASE_FILE_NAME);
    }

    private File getSqliteDatabaseFile(File historyDirectory) {
        return new File(historyDirectory, SQLITE_DATABASE_FILE_NAME);
    }

    private SQLiteStore resolveSqliteStore(File historyDirectory) {
        if (!shouldUseSqlite()) {
            return null;
        }
        if (!isSqliteDriverAvailable()) {
            return null;
        }
        return new SQLiteStore(getSqliteDatabaseFile(historyDirectory));
    }

    private boolean shouldUseSqlite() {
        String mode = System.getProperty(STORAGE_MODE_PROPERTY, STORAGE_MODE_AUTO);
        if (mode == null) {
            return true;
        }
        String normalized = mode.trim().toLowerCase();
        if (STORAGE_MODE_JSON.equals(normalized)) {
            return false;
        }
        if (STORAGE_MODE_SQLITE.equals(normalized)) {
            return true;
        }
        return true;
    }

    private boolean isSqliteDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    private void seedSqliteIfNeeded(File historyDirectory, SQLiteStore sqliteStore) throws Exception {
        if (sqliteStore.countSnapshots() > 0) {
            return;
        }

        JSONObject root = loadRoot(historyDirectory);
        Map<String, Snapshot> snapshotsByDate = parseSnapshots(getSnapshotsObject(root));
        if (snapshotsByDate.isEmpty()) {
            importMissingCsvSnapshots(historyDirectory, snapshotsByDate, getSnapshotsObject(root));
        }
        if (snapshotsByDate.isEmpty()) {
            return;
        }

        List<String> dates = new ArrayList<String>(snapshotsByDate.keySet());
        Collections.sort(dates);
        for (String date : dates) {
            Snapshot snapshot = snapshotsByDate.get(date);
            if (snapshot != null && !snapshot.rows.isEmpty()) {
                sqliteStore.upsertSnapshot(snapshot);
            }
        }
    }

    private Snapshot parseSnapshotRowJson(String date, String rowJson) throws Exception {
        if (rowJson == null || rowJson.trim().length() == 0) {
            return new Snapshot();
        }
        Object parsed = new JSONParser().parse(rowJson);
        if (!(parsed instanceof JSONObject)) {
            return new Snapshot();
        }
        Snapshot snapshot = new Snapshot();
        snapshot.date = safeText(date);
        SnapshotRow row = parseRowJson((JSONObject) parsed, date);
        if (row.code.length() > 0) {
            snapshot.rows.add(row);
        }
        return snapshot;
    }

    public static class Snapshot {
        public String date = "";
        public List<SnapshotRow> rows = new ArrayList<SnapshotRow>();
    }

    public static class SnapshotRow {
        public String date = "";
        public String code = "";
        public String name = "";
        public String market = "";
        public String industry = "";
        public String note = "";
        public double score;
        public double rawScore;
        public double selectionScore;
        public double momentumScore;
        public double qualityScore;
        public double sectorScore;
        public double themeScore;
        public double trendPersistenceScore;
        public int trendPersistenceDays;
        public double newsScore;
        public double newsRiskScore;
        public double relativeStrengthScore;
        public double industryReturnStrength;
        public double industryVolumeStrength;
        public double industryFlowStrength;
        public String eventDirection = "";
        public double eventConfidence;
        public int eventFreshnessDays;
        public String eventTypeSummary = "";
        public double structureScore;
        public double riskRewardScore;
        public double riskRewardRatio;
        public double turnaroundScore;
        public double revenueGrowthSignalScore;
        public double earningsTurnaroundSignalScore;
        public double profitabilityTurnaroundSignalScore;
        public double oneOffRiskScore;
        public double suggestedStopPrice;
        public double suggestedStopPct;
        public double suggestedTargetPrice;
        public double upsidePotentialPct;
        public double buyPointScore;
        public double dataConfidence;
        public boolean selectionQualified;
        public double price;
        public double movingAverage20;
        public double movingAverage60;
        public double movingAverage120;
        public double volumeRatio;
        public double return20DayPct;
        public double return60DayPct;
        public double averageLots20;
        public double averageTradeValue20Billion;
        public double volatility20Pct;
        public double drawdownFromHigh60Pct;
        public double liquidityScore;
        public double revenueScore;
        public double chipsScore;
        public double valuationScore;
        public double technicalScore;
        public double financialQualityScore;
        public double fiveDayInstitutionalNetRatioPct;
        public double brokerNetRatioPct;
        public double rsi14;
        public double stochasticK;
        public double stochasticD;
        public double epsAccelerationPct;
        public double peg;
        public String scoreReason = "";
        public String revenueReason = "";
        public String chipsReason = "";
        public String liquidityReason = "";
        public String valuationReason = "";
        public String technicalReason = "";
        public String financialQualityReason = "";
        public String eventRiskReason = "";
        public String eligibilityReason = "";
        public String primaryTheme = "";
        public String themeTags = "";
        public String newsSummary = "";
        public String newsDigest = "";
        public String newsSourceSummary = "";
        public String latestNewsPublishedHint = "";
        public double newsSourceCredibilityScore;
        public double newsFreshnessScore;
        public int newsSourceCount;
        public int newsOfficialSourceCount;
        public int newsMediaSourceCount;
        public String structureLabel = "";
        public String turnaroundLabel = "";
        public String turnaroundReason = "";
        public String buyPointLabel = "";
        public String buyPointReason = "";
        public String dataConfidenceReason = "";
        public String signalType = "";
        public int signalHorizonDays;
        public String entryRule = "";
        public String exitRule = "";
        public String validationMode = "";
        public boolean hardExclude;
        public String hardExcludeReason = "";
        public String dataQualityGrade = "";
        public double winratePriorityScore;
        public double expectedReturnScore;
        public double maxDrawdownPenalty;
        public String backtestCohort = "";
        public double postClosePriorityScore;
        public String postCloseCategory = "";
        public String postCloseAction = "";
        public String postCloseReason = "";
        public boolean likely;
    }

    private class SQLiteStore {
        private final File databaseFile;

        private SQLiteStore(File databaseFile) {
            this.databaseFile = databaseFile;
        }

        private File getDatabaseFile() {
            return databaseFile;
        }

        private Connection openConnection() throws Exception {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            connection.setAutoCommit(false);
            ensureSchema(connection);
            return connection;
        }

        private void ensureSchema(Connection connection) throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("create table if not exists metadata (meta_key text primary key, meta_value text not null)");
                statement.executeUpdate("create table if not exists snapshots (snapshot_date text primary key, row_count integer not null, updated_at text not null)");
                statement.executeUpdate("create table if not exists snapshot_rows (snapshot_date text not null, code text not null, sort_order integer not null, name text, market text, industry text, score real, selection_score real, price real, volume_ratio real, likely integer not null, row_json text not null, primary key (snapshot_date, code))");
                statement.executeUpdate("create index if not exists idx_snapshot_rows_code_date on snapshot_rows(code, snapshot_date)");
            } finally {
                statement.close();
            }
        }

        private int countSnapshots() throws Exception {
            Connection connection = openConnection();
            try {
                Statement statement = connection.createStatement();
                try {
                    ResultSet resultSet = statement.executeQuery("select count(*) from snapshots");
                    try {
                        if (resultSet.next()) {
                            return resultSet.getInt(1);
                        }
                        return 0;
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    statement.close();
                    connection.commit();
                }
            } finally {
                connection.close();
            }
        }

        private boolean hasSnapshot(String date) throws Exception {
            Connection connection = openConnection();
            try {
                PreparedStatement statement = connection
                        .prepareStatement("select 1 from snapshots where snapshot_date = ? limit 1");
                try {
                    statement.setString(1, date);
                    ResultSet resultSet = statement.executeQuery();
                    try {
                        return resultSet.next();
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    statement.close();
                    connection.commit();
                }
            } finally {
                connection.close();
            }
        }

        private void upsertSnapshot(Snapshot snapshot) throws Exception {
            if (snapshot == null || snapshot.date.length() == 0) {
                return;
            }
            Connection connection = openConnection();
            try {
                PreparedStatement deleteRows = connection
                        .prepareStatement("delete from snapshot_rows where snapshot_date = ?");
                PreparedStatement deleteSnapshot = connection
                        .prepareStatement("delete from snapshots where snapshot_date = ?");
                PreparedStatement insertSnapshot = connection.prepareStatement(
                        "insert into snapshots(snapshot_date, row_count, updated_at) values(?,?,?)");
                PreparedStatement insertRow = connection.prepareStatement(
                        "insert into snapshot_rows(snapshot_date, code, sort_order, name, market, industry, score, selection_score, price, volume_ratio, likely, row_json) values(?,?,?,?,?,?,?,?,?,?,?,?)");
                PreparedStatement upsertMeta = connection.prepareStatement(
                        "insert into metadata(meta_key, meta_value) values(?, ?) on conflict(meta_key) do update set meta_value = excluded.meta_value");
                try {
                    deleteRows.setString(1, snapshot.date);
                    deleteRows.executeUpdate();
                    deleteSnapshot.setString(1, snapshot.date);
                    deleteSnapshot.executeUpdate();

                    insertSnapshot.setString(1, snapshot.date);
                    insertSnapshot.setInt(2, snapshot.rows.size());
                    insertSnapshot.setString(3, snapshot.date);
                    insertSnapshot.executeUpdate();

                    for (int i = 0; i < snapshot.rows.size(); i++) {
                        SnapshotRow row = snapshot.rows.get(i);
                        insertRow.setString(1, snapshot.date);
                        insertRow.setString(2, safeText(row.code));
                        insertRow.setInt(3, i);
                        insertRow.setString(4, safeText(row.name));
                        insertRow.setString(5, safeText(row.market));
                        insertRow.setString(6, safeText(row.industry));
                        insertRow.setDouble(7, safeNumber(row.score));
                        insertRow.setDouble(8, safeNumber(row.selectionScore));
                        insertRow.setDouble(9, safeNumber(row.price));
                        insertRow.setDouble(10, safeNumber(row.volumeRatio));
                        insertRow.setInt(11, row.likely ? 1 : 0);
                        insertRow.setString(12, toRowJson(row).toJSONString());
                        insertRow.executeUpdate();
                    }

                    upsertMeta.setString(1, "version");
                    upsertMeta.setString(2, Long.toString(DATABASE_VERSION));
                    upsertMeta.executeUpdate();
                    upsertMeta.setString(1, "updatedDate");
                    upsertMeta.setString(2, snapshot.date);
                    upsertMeta.executeUpdate();
                    upsertMeta.setString(1, "source");
                    upsertMeta.setString(2, "StockAnalysis");
                    upsertMeta.executeUpdate();

                    connection.commit();
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    deleteRows.close();
                    deleteSnapshot.close();
                    insertSnapshot.close();
                    insertRow.close();
                    upsertMeta.close();
                }
            } finally {
                connection.close();
            }
        }

        private Map<String, Snapshot> loadSnapshots() throws Exception {
            Map<String, Snapshot> snapshotsByDate = new HashMap<String, Snapshot>();
            Connection connection = openConnection();
            try {
                Statement statement = connection.createStatement();
                try {
                    ResultSet resultSet = statement.executeQuery(
                            "select snapshot_date, row_count from snapshots order by snapshot_date");
                    try {
                        while (resultSet.next()) {
                            String date = safeText(resultSet.getString("snapshot_date"));
                            Snapshot snapshot = new Snapshot();
                            snapshot.date = date;
                            snapshotsByDate.put(date, snapshot);
                        }
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    statement.close();
                }

                PreparedStatement rowStatement = connection.prepareStatement(
                        "select snapshot_date, row_json from snapshot_rows order by snapshot_date, sort_order, code");
                try {
                    ResultSet resultSet = rowStatement.executeQuery();
                    try {
                        while (resultSet.next()) {
                            String date = safeText(resultSet.getString("snapshot_date"));
                            String rowJson = safeText(resultSet.getString("row_json"));
                            Snapshot snapshot = snapshotsByDate.get(date);
                            if (snapshot == null) {
                                snapshot = new Snapshot();
                                snapshot.date = date;
                                snapshotsByDate.put(date, snapshot);
                            }
                            Snapshot parsed = parseSnapshotRowJson(date, rowJson);
                            if (!parsed.rows.isEmpty()) {
                                snapshot.rows.add(parsed.rows.get(0));
                            }
                        }
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    rowStatement.close();
                    connection.commit();
                }
            } finally {
                connection.close();
            }
            return snapshotsByDate;
        }
    }
}
