package stock;

import java.util.List;

import stock.vo.StockAnalysisResultVO;

public class StockAnalysis {

    public static void main(String[] args) throws Exception {
        int maxStocks = args.length > 0 ? Integer.parseInt(args[0]) : -1;
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
        writeBacktestSafely();
        writeHistoryDashboardSafely(dashboardWriter, results, likelyCandidates, watchlistCandidates,
                likelyVolumeCandidates, nonLikelyVolumeCandidates, latestHistoryDashboardFileName);

        System.out.println("");
        System.out.println("Analyzed stocks: " + results.size());
        if (maxStocks > 0) {
            System.out.println("Mode: limited run (" + maxStocks + " stocks)");
        } else {
            System.out.println("Mode: full TWSE + TPEX run");
        }
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
}
