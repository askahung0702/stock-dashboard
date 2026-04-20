package stock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.List;

import org.json.simple.JSONObject;

import stock.vo.StockAnalysisResultVO;

public class StockAnalysis {

    private static final String STAGE_FULL = "full";
    private static final String STAGE_CLOSE = "close";

    public static void main(String[] args) throws Exception {
        RunOptions runOptions = parseArgs(args);
        int maxStocks = runOptions.maxStocks;
        boolean closeStage = STAGE_CLOSE.equals(runOptions.stage);
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
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

        System.out.println("");
        System.out.println("Analyzed stocks: " + results.size());
        if (maxStocks > 0) {
            System.out.println("Mode: " + runOptions.stage + " limited run (" + maxStocks + " stocks)");
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
            result.put("stageLabel", STAGE_CLOSE.equals(stage) ? "盤後初版" : "夜間完整版");
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
}
