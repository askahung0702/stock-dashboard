package stock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;
import stock.vo.StockAnalysisResultVO;

public class StockAnalysis {

    private static final String STAGE_FULL = "full";
    private static final String STAGE_CLOSE = "close";
    private static final String STAGE_INTRADAY_CLOSE = "intraday-close";

    public static void main(String[] args) throws Exception {
        RunOptions runOptions = parseArgs(args);
        int maxStocks = runOptions.maxStocks;
        boolean closeStage = STAGE_CLOSE.equals(runOptions.stage) || STAGE_INTRADAY_CLOSE.equals(runOptions.stage);
        boolean stageOnly = Boolean.getBoolean("stock.analysis.stageOnly");
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        analyzer.setRunStage(runOptions.stage);
        analyzer.markRunStatus("starting", 0, "stage boot");
        String allFileName = analyzer.buildDatedFileName("stock_candidates");
        String likelyFileName = analyzer.buildDatedFileName("stock_candidates_likely");
        String likelyVolumeFileName = analyzer.buildDatedFileName("stock_candidates_likely_volume_surge");
        String nonLikelyVolumeFileName = analyzer.buildDatedFileName("stock_candidates_non_likely_volume_surge");
        String highConvictionFileName = analyzer.buildDatedFileName("stock_candidates_high_conviction");
        String momentumAttackFileName = analyzer.buildDatedFileName("stock_candidates_momentum_attack");
        String swingPositionFileName = analyzer.buildDatedFileName("stock_candidates_swing_position");
        String catalystWatchFileName = analyzer.buildDatedFileName("stock_candidates_catalyst_watch");
        String themeReferenceFileName = analyzer.buildDatedFileName("stock_theme_reference");
        String themeMarketReferenceFileName = analyzer.buildDatedFileName("theme_market_reference");
        String themeMarketCandidatesFileName = analyzer.buildDatedFileName("theme_market_candidates");
        String dashboardFileName = analyzer.buildDatedHtmlFileName("stock_dashboard");
        String latestHistoryDashboardFileName = "history_dashboard.html";

        List<StockAnalysisResultVO> results = analyzer.analyze(maxStocks);
        analyzer.writeStageSnapshots(results);
        writeStageMarketDataSafely(analyzer, results);
        analyzer.markRunStatus("stage_snapshot_saved", results.size(), "raw and analysis saved");
        if (stageOnly) {
            analyzer.markRunStatus("stage_only_completed", results.size(),
                    closeStage ? "close stage saved for export" : "full stage saved for export");
            System.out.println("");
            System.out.println("Analyzed stocks: " + results.size());
            System.out.println("Mode: " + runOptions.stage + " stage-only run");
            return;
        }
        List<StockAnalysisResultVO> likelyCandidates = analyzer.getLikelyCandidates(results);
        List<StockAnalysisResultVO> watchlistCandidates = analyzer.getWatchlistCandidates(results);
        List<StockAnalysisResultVO> likelyVolumeCandidates = analyzer.getLikelyVolumeSurgeCandidates(results);
        List<StockAnalysisResultVO> nonLikelyVolumeCandidates = analyzer.getNonLikelyVolumeSurgeCandidates(results);
        StockDashboardWriter dashboardWriter = new StockDashboardWriter(analyzer.currentDateStamp(),
                analyzer.getLikelyThreshold(), analyzer.getWatchlistThreshold(), analyzer.getVolumeSurgeThreshold());
        analyzer.printLikelyCandidates(results);
        analyzer.printPostCloseCandidates(results);
        analyzer.printTopCandidates(results);
        analyzer.printMarketDistribution(results);
        writeCsvSafely(analyzer, results, allFileName, false);
        writeCsvSafely(analyzer, results, likelyFileName, true);
        writeVolumeSurgeCsvSafely(analyzer, results, likelyVolumeFileName, true);
        writeVolumeSurgeCsvSafely(analyzer, results, nonLikelyVolumeFileName, false);
        writePostCloseCsvSafely(analyzer, results, highConvictionFileName, "high_conviction");
        writePostCloseCsvSafely(analyzer, results, momentumAttackFileName, "momentum_attack");
        writePostCloseCsvSafely(analyzer, results, swingPositionFileName, "swing_position");
        writePostCloseCsvSafely(analyzer, results, catalystWatchFileName, "catalyst_watch");
        writeThemeReferenceCsvSafely(analyzer, results, themeReferenceFileName);
        writeThemeMarketReferenceCsvSafely(analyzer, themeMarketReferenceFileName);
        writeThemeMarketCandidatesCsvSafely(analyzer, themeMarketCandidatesFileName);
        writeDashboardSafely(dashboardWriter, results, likelyCandidates, watchlistCandidates, likelyVolumeCandidates,
                nonLikelyVolumeCandidates, dashboardFileName);
        if (maxStocks > 0) {
            System.out.println("History snapshot skipped in limited run to keep daily history complete.");
        } else {
            writeHistorySafely(analyzer, results);
        }
        if (closeStage) {
            System.out.println("Backtest, calibration, and walk-forward optimization skipped in close stage.");
        } else {
            writeBacktestSafely();
        }
        writeHistoryDashboardSafely(dashboardWriter, results, likelyCandidates, watchlistCandidates,
                likelyVolumeCandidates, nonLikelyVolumeCandidates, latestHistoryDashboardFileName);
        writeStaticSiteDataSafely();
        writeSnapshotStatusSafely(runOptions.stage, maxStocks > 0);
        if (!closeStage && maxStocks <= 0) {
            writeStageDiffSafely(analyzer.currentDateStamp());
        }
        analyzer.markRunStatus("completed", results.size(), closeStage ? "close stage complete" : "full stage complete");

        System.out.println("");
        System.out.println("Analyzed stocks: " + results.size());
        if (maxStocks > 0) {
            System.out.println("Mode: " + runOptions.stage + " limited run (" + maxStocks + " stocks)");
        } else if (STAGE_INTRADAY_CLOSE.equals(runOptions.stage)) {
            System.out.println("Mode: intraday-close stage full-market run");
        } else if (closeStage) {
            System.out.println("Mode: close stage full-market run");
        } else {
            System.out.println("Mode: full TWSE + TPEX run");
        }
    }

    private static RunOptions parseArgs(String[] args) {
        RunOptions options = new RunOptions();
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String trimmed = arg.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            if ("intraday-close".equalsIgnoreCase(trimmed) || "intraday".equalsIgnoreCase(trimmed)
                    || "--stage=intraday-close".equalsIgnoreCase(trimmed)
                    || "--mode=intraday-close".equalsIgnoreCase(trimmed)) {
                options.stage = STAGE_INTRADAY_CLOSE;
                continue;
            }
            if ("close".equalsIgnoreCase(trimmed) || "--stage=close".equalsIgnoreCase(trimmed)
                    || "--mode=close".equalsIgnoreCase(trimmed)) {
                options.stage = STAGE_CLOSE;
                continue;
            }
            if ("full".equalsIgnoreCase(trimmed) || "--stage=full".equalsIgnoreCase(trimmed)
                    || "--mode=full".equalsIgnoreCase(trimmed)) {
                options.stage = STAGE_FULL;
                continue;
            }
            options.maxStocks = Integer.parseInt(trimmed);
        }
        return options;
    }

    private static void writeCsvSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results, String fileName,
            boolean likelyOnly) {
        try {
            if (likelyOnly) {
                analyzer.writeLikelyCandidatesCsv(results, fileName);
                System.out.println("Likely candidates CSV: " + analyzer.resolveOutputPath(fileName));
            } else {
                analyzer.writeCsv(results, fileName);
                System.out.println("CSV output: " + analyzer.resolveOutputPath(fileName));
            }
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
            System.out.println("Please close the file if it is open in Excel, then rerun to refresh CSV output.");
        }
    }

    private static void writeHistorySafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results) {
        try {
            String databasePath = analyzer.writeHistoryDatabase(results);
            System.out.println("History database: " + databasePath);
        } catch (Exception ex) {
            System.out.println("Cannot write history database: " + ex.getMessage());
        }

        try {
            String snapshotPath = analyzer.writeHistorySnapshot(results);
            System.out.println("History snapshot: " + snapshotPath);
        } catch (Exception ex) {
            System.out.println("Cannot write history snapshot: " + ex.getMessage());
        }

        try {
            String performanceReportPath = analyzer.writePerformanceReport(results);
            if (performanceReportPath.length() > 0) {
                System.out.println("Performance report: " + performanceReportPath);
            }
        } catch (Exception ex) {
            System.out.println("Cannot write history performance report: " + ex.getMessage());
        }
    }

    private static void writeBacktestSafely() {
        try {
            String path = new StockBacktestReport().writeDefaultReport();
            if (path.length() > 0) {
                System.out.println("Backtest summary: " + new java.io.File(path).getAbsolutePath());
                BacktestCalibrationService.CalibrationArtifacts artifacts = new BacktestCalibrationService()
                        .writeDefaultArtifacts();
                if (artifacts.signalCalibrationReportPath.length() > 0) {
                    System.out.println("Signal calibration report: " + artifacts.signalCalibrationReportPath);
                }
                if (artifacts.recommendedThresholdsPath.length() > 0) {
                    System.out.println("Recommended thresholds: " + artifacts.recommendedThresholdsPath);
                }
                if (artifacts.excludeRulesPath.length() > 0) {
                    System.out.println("Exclude rules: " + artifacts.excludeRulesPath);
                }
                WalkForwardOptimizationService.OptimizationArtifacts optimizationArtifacts = new WalkForwardOptimizationService()
                        .writeDefaultArtifacts();
                if (optimizationArtifacts.reportPath.length() > 0) {
                    System.out.println("Walk-forward optimization: " + optimizationArtifacts.reportPath);
                }
                if (optimizationArtifacts.recommendationPath.length() > 0) {
                    System.out.println("Parameter recommendations: " + optimizationArtifacts.recommendationPath);
                }
            }
        } catch (Exception ex) {
            System.out.println("Cannot write backtest summary: " + ex.getMessage());
        }
    }

    private static void writeStageMarketDataSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results) {
        try {
            StockApiRenderer renderer = new StockApiRenderer();
            JSONObject marketPayload = renderer.renderLatestMarketJson(analyzer.currentDateStamp(), results);
            new StockHistoryDatabase().upsertDailyMarketData(analyzer.currentDateStamp(), analyzerStage(results),
                    "market_overview", marketPayload);
            System.out.println("Daily market data saved: " + analyzer.currentDateStamp() + " (" + analyzerStage(results)
                    + ")");
        } catch (Exception ex) {
            System.out.println("Cannot write daily market data: " + ex.getMessage());
        }
    }

    private static String analyzerStage(List<StockAnalysisResultVO> results) {
        if (results == null || results.isEmpty() || results.get(0) == null
                || results.get(0).getSnapshotStage() == null || results.get(0).getSnapshotStage().trim().length() == 0) {
            return STAGE_FULL;
        }
        return results.get(0).getSnapshotStage().trim().toLowerCase();
    }

    private static void writeStageDiffSafely(String date) {
        try {
            StockHistoryDatabase database = new StockHistoryDatabase();
            Snapshot closeSnapshot = database.loadDailyStockAnalysis(date, STAGE_CLOSE);
            Snapshot fullSnapshot = database.loadDailyStockAnalysis(date, STAGE_FULL);
            if (closeSnapshot.rows.isEmpty() || fullSnapshot.rows.isEmpty()) {
                return;
            }
            Map<String, SnapshotRow> closeRowsByCode = new HashMap<String, SnapshotRow>();
            for (SnapshotRow row : closeSnapshot.rows) {
                closeRowsByCode.put(row.code, row);
            }
            File outputFile = new File("history", "close_full_diff_" + date + ".csv");
            int comparedCount = 0;
            int changedSelectionCount = 0;
            int changedBuyPointCount = 0;
            int newsReadyImproved = 0;
            int brokerReadyImproved = 0;
            int financialReadyImproved = 0;
            double selectionDeltaSum = 0D;
            double buyPointDeltaSum = 0D;
            double confidenceDeltaSum = 0D;
            Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
            try {
                writer.write(
                        "date,code,name,close_selection_score,full_selection_score,selection_delta,close_buy_point,full_buy_point,buy_point_delta,close_data_confidence,full_data_confidence,confidence_delta,close_news_ready,full_news_ready,close_financial_ready,full_financial_ready,close_broker_ready,full_broker_ready,close_institutional_ready,full_institutional_ready,close_analysis_version,full_analysis_version\n");
                for (SnapshotRow fullRow : fullSnapshot.rows) {
                    SnapshotRow closeRow = closeRowsByCode.get(fullRow.code);
                    if (closeRow == null) {
                        continue;
                    }
                    double selectionDelta = fullRow.selectionScore - closeRow.selectionScore;
                    double buyPointDelta = fullRow.buyPointScore - closeRow.buyPointScore;
                    double confidenceDelta = fullRow.dataConfidence - closeRow.dataConfidence;
                    comparedCount++;
                    selectionDeltaSum += selectionDelta;
                    buyPointDeltaSum += buyPointDelta;
                    confidenceDeltaSum += confidenceDelta;
                    if (Math.abs(selectionDelta) >= 0.1D) {
                        changedSelectionCount++;
                    }
                    if (Math.abs(buyPointDelta) >= 0.1D) {
                        changedBuyPointCount++;
                    }
                    if (!closeRow.newsReady && fullRow.newsReady) {
                        newsReadyImproved++;
                    }
                    if (!closeRow.brokerReady && fullRow.brokerReady) {
                        brokerReadyImproved++;
                    }
                    if (!closeRow.financialReady && fullRow.financialReady) {
                        financialReadyImproved++;
                    }
                    writer.write(csv(date));
                    writer.write(',');
                    writer.write(csv(fullRow.code));
                    writer.write(',');
                    writer.write(csv(fullRow.name));
                    writer.write(',');
                    writer.write(Double.toString(closeRow.selectionScore));
                    writer.write(',');
                    writer.write(Double.toString(fullRow.selectionScore));
                    writer.write(',');
                    writer.write(Double.toString(selectionDelta));
                    writer.write(',');
                    writer.write(Double.toString(closeRow.buyPointScore));
                    writer.write(',');
                    writer.write(Double.toString(fullRow.buyPointScore));
                    writer.write(',');
                    writer.write(Double.toString(buyPointDelta));
                    writer.write(',');
                    writer.write(Double.toString(closeRow.dataConfidence));
                    writer.write(',');
                    writer.write(Double.toString(fullRow.dataConfidence));
                    writer.write(',');
                    writer.write(Double.toString(confidenceDelta));
                    writer.write(',');
                    writer.write(Boolean.toString(closeRow.newsReady));
                    writer.write(',');
                    writer.write(Boolean.toString(fullRow.newsReady));
                    writer.write(',');
                    writer.write(Boolean.toString(closeRow.financialReady));
                    writer.write(',');
                    writer.write(Boolean.toString(fullRow.financialReady));
                    writer.write(',');
                    writer.write(Boolean.toString(closeRow.brokerReady));
                    writer.write(',');
                    writer.write(Boolean.toString(fullRow.brokerReady));
                    writer.write(',');
                    writer.write(Boolean.toString(closeRow.institutionalReady));
                    writer.write(',');
                    writer.write(Boolean.toString(fullRow.institutionalReady));
                    writer.write(',');
                    writer.write(csv(closeRow.analysisVersion));
                    writer.write(',');
                    writer.write(csv(fullRow.analysisVersion));
                    writer.write('\n');
                }
            } finally {
                writer.close();
            }
            writeStageDiffSummarySafely(date, comparedCount, changedSelectionCount, changedBuyPointCount,
                    newsReadyImproved, brokerReadyImproved, financialReadyImproved, selectionDeltaSum, buyPointDeltaSum,
                    confidenceDeltaSum);
            System.out.println("Stage diff report: " + outputFile.getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Cannot write close/full diff report: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeStageDiffSummarySafely(String date, int comparedCount, int changedSelectionCount,
            int changedBuyPointCount, int newsReadyImproved, int brokerReadyImproved, int financialReadyImproved,
            double selectionDeltaSum, double buyPointDeltaSum, double confidenceDeltaSum) {
        File summaryFile = new File("web\\data", "close_full_diff_summary.json");
        try {
            File parent = summaryFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JSONObject result = new JSONObject();
            result.put("date", date);
            result.put("comparedCount", Long.valueOf(comparedCount));
            result.put("changedSelectionCount", Long.valueOf(changedSelectionCount));
            result.put("changedBuyPointCount", Long.valueOf(changedBuyPointCount));
            result.put("newsReadyImproved", Long.valueOf(newsReadyImproved));
            result.put("brokerReadyImproved", Long.valueOf(brokerReadyImproved));
            result.put("financialReadyImproved", Long.valueOf(financialReadyImproved));
            result.put("avgSelectionDelta", Double.valueOf(comparedCount > 0 ? selectionDeltaSum / comparedCount : 0D));
            result.put("avgBuyPointDelta", Double.valueOf(comparedCount > 0 ? buyPointDeltaSum / comparedCount : 0D));
            result.put("avgConfidenceDelta", Double.valueOf(comparedCount > 0 ? confidenceDeltaSum / comparedCount : 0D));
            Writer writer = new OutputStreamWriter(new FileOutputStream(summaryFile), "UTF-8");
            try {
                writer.write(result.toJSONString());
            } finally {
                writer.close();
            }
            System.out.println("Stage diff summary: " + summaryFile.getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Cannot write close/full diff summary: " + ex.getMessage());
        }
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static void writeVolumeSurgeCsvSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results,
            String fileName, boolean likelyOnly) {
        try {
            if (likelyOnly) {
                analyzer.writeLikelyVolumeSurgeCsv(results, fileName);
                System.out.println("Likely volume surge CSV: " + analyzer.resolveOutputPath(fileName));
            } else {
                analyzer.writeNonLikelyVolumeSurgeCsv(results, fileName);
                System.out.println("Non-likely volume surge CSV: " + analyzer.resolveOutputPath(fileName));
            }
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
            System.out.println("Please close the file if it is open in Excel, then rerun to refresh CSV output.");
        }
    }

    private static void writePostCloseCsvSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results,
            String fileName, String type) {
        try {
            if ("high_conviction".equals(type)) {
                analyzer.writeHighConvictionCandidatesCsv(results, fileName);
                System.out.println("High-conviction CSV: " + analyzer.resolveOutputPath(fileName));
            } else if ("momentum_attack".equals(type)) {
                analyzer.writeMomentumAttackCandidatesCsv(results, fileName);
                System.out.println("Momentum-attack CSV: " + analyzer.resolveOutputPath(fileName));
            } else if ("swing_position".equals(type)) {
                analyzer.writeSwingPositionCandidatesCsv(results, fileName);
                System.out.println("Swing-position CSV: " + analyzer.resolveOutputPath(fileName));
            } else if ("catalyst_watch".equals(type)) {
                analyzer.writeCatalystWatchCandidatesCsv(results, fileName);
                System.out.println("Catalyst-watch CSV: " + analyzer.resolveOutputPath(fileName));
            }
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
            System.out.println("Please close the file if it is open in Excel, then rerun to refresh CSV output.");
        }
    }

    private static void writeThemeReferenceCsvSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results,
            String fileName) {
        try {
            analyzer.writeThemeReferenceCsv(results, fileName);
            System.out.println("Theme reference CSV: " + analyzer.resolveOutputPath(fileName));
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeThemeMarketReferenceCsvSafely(TaiwanStockAnalyzer analyzer, String fileName) {
        try {
            analyzer.writeThemeMarketReferenceCsv(fileName);
            System.out.println("Theme market reference CSV: " + analyzer.resolveOutputPath(fileName));
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeThemeMarketCandidatesCsvSafely(TaiwanStockAnalyzer analyzer, String fileName) {
        try {
            analyzer.writeThemeMarketCandidatesCsv(fileName);
            System.out.println("Theme market candidates CSV: " + analyzer.resolveOutputPath(fileName));
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeDashboardSafely(StockDashboardWriter dashboardWriter, List<StockAnalysisResultVO> results,
            List<StockAnalysisResultVO> likelyCandidates, List<StockAnalysisResultVO> watchlistCandidates,
            List<StockAnalysisResultVO> likelyVolumeCandidates, List<StockAnalysisResultVO> nonLikelyVolumeCandidates,
            String fileName) {
        try {
            String path = dashboardWriter.writeDashboard(results, likelyCandidates, watchlistCandidates,
                    likelyVolumeCandidates, nonLikelyVolumeCandidates, fileName);
            System.out.println("Web dashboard: " + path);
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeHistoryDashboardSafely(StockDashboardWriter dashboardWriter,
            List<StockAnalysisResultVO> results, List<StockAnalysisResultVO> likelyCandidates,
            List<StockAnalysisResultVO> watchlistCandidates, List<StockAnalysisResultVO> likelyVolumeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeCandidates, String fileName) {
        try {
            String path = dashboardWriter.writeDashboard(results, likelyCandidates, watchlistCandidates,
                    likelyVolumeCandidates, nonLikelyVolumeCandidates, fileName);
            System.out.println("History dashboard: " + path);
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeStaticSiteDataSafely() {
        try {
            new StockStaticApiExporter().writeDefaultOutputs(new java.io.File("web\\data", "latest.json").getPath(),
                    new java.io.File("web\\data", "history.json").getPath());
            System.out.println("Static site data: " + new java.io.File("web\\data").getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Cannot write static site data: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeSnapshotStatusSafely(String stage, boolean limitedRun) {
        File statusFile = new File("web\\data", "snapshot_status.json");
        try {
            File parent = statusFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JSONObject result = new JSONObject();
            result.put("snapshotStage", stage);
            result.put("stageLabel", stageLabel(stage));
            result.put("limitedRun", Boolean.valueOf(limitedRun));
            result.put("generatedAt", LocalDateTime.now().toString());
            Writer writer = new OutputStreamWriter(new FileOutputStream(statusFile), "UTF-8");
            try {
                writer.write(result.toJSONString());
            } finally {
                writer.close();
            }
            System.out.println("Snapshot status: " + statusFile.getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Cannot write snapshot status: " + ex.getMessage());
        }
    }

    private static class RunOptions {
        private String stage = STAGE_FULL;
        private int maxStocks = -1;
    }

    private static String stageLabel(String stage) {
        if (STAGE_INTRADAY_CLOSE.equals(stage)) {
            return "收盤行情初版";
        }
        if (STAGE_CLOSE.equals(stage)) {
            return "盤後初版";
        }
        return "夜間完整版";
    }
}
