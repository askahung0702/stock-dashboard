package stock;

import java.util.List;

import stock.vo.StockAnalysisResultVO;

public class StockNewsOnlyAnalysis {

    public static void main(String[] args) throws Exception {
        int maxStocks = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        boolean stageOnly = Boolean.getBoolean("stock.news.stageOnly");
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        analyzer.setRunStage("news-event");
        analyzer.markRunStatus("running", 0, "news-event stage boot");
        String newsOnlyFileName = analyzer.buildDatedFileName("stock_news_only");
        String themeReferenceFileName = analyzer.buildDatedFileName("stock_news_theme_reference");
        String themeMarketReferenceFileName = analyzer.buildDatedFileName("stock_news_market_reference");
        String themeMarketCandidatesFileName = analyzer.buildDatedFileName("stock_news_market_candidates");
        String latestHistoryDashboardFileName = "history_dashboard.html";

        List<StockAnalysisResultVO> results = analyzer.analyzeNewsOnly(maxStocks);
        analyzer.writeStageSnapshots(results);
        analyzer.markRunStatus(stageOnly ? "stage_only_completed" : "completed", results.size(),
                "news-event rows saved");
        if (stageOnly) {
            System.out.println("");
            System.out.println("News-event staged results: " + results.size());
            System.out.println("Reference snapshot date: "
                    + (analyzer.getLastNewsOnlyReferenceDate().length() == 0 ? "N/A"
                            : analyzer.getLastNewsOnlyReferenceDate()));
            return;
        }
        List<StockAnalysisResultVO> likelyCandidates = analyzer.getLikelyCandidates(results);
        List<StockAnalysisResultVO> watchlistCandidates = analyzer.getWatchlistCandidates(results);
        List<StockAnalysisResultVO> likelyVolumeCandidates = analyzer.getLikelyVolumeSurgeCandidates(results);
        List<StockAnalysisResultVO> nonLikelyVolumeCandidates = analyzer.getNonLikelyVolumeSurgeCandidates(results);
        StockDashboardWriter dashboardWriter = new StockDashboardWriter(analyzer.currentDateStamp(),
                analyzer.getLikelyThreshold(), analyzer.getWatchlistThreshold(), analyzer.getVolumeSurgeThreshold());
        analyzer.writeNewsOnlyCsv(results, newsOnlyFileName);
        analyzer.writeThemeReferenceCsv(results, themeReferenceFileName);
        analyzer.writeThemeMarketReferenceCsv(themeMarketReferenceFileName);
        analyzer.writeThemeMarketCandidatesCsv(themeMarketCandidatesFileName);
        writeNewsDashboardSafely(dashboardWriter, results, likelyCandidates, watchlistCandidates, likelyVolumeCandidates,
                nonLikelyVolumeCandidates, latestHistoryDashboardFileName);
        writeStaticSiteDataSafely(analyzer, results);

        System.out.println("");
        System.out.println("News-only results: " + results.size());
        System.out.println("Reference snapshot date: "
                + (analyzer.getLastNewsOnlyReferenceDate().length() == 0 ? "N/A"
                        : analyzer.getLastNewsOnlyReferenceDate()));
        System.out.println("News-only CSV: " + analyzer.resolveOutputPath(newsOnlyFileName));
        System.out.println("News theme reference CSV: " + analyzer.resolveOutputPath(themeReferenceFileName));
        System.out.println("Market news reference CSV: " + analyzer.resolveOutputPath(themeMarketReferenceFileName));
        System.out.println("Market news candidates CSV: " + analyzer.resolveOutputPath(themeMarketCandidatesFileName));
        if (maxStocks > 0) {
            System.out.println("Mode: limited news-only run (" + maxStocks + " stocks)");
        } else {
            System.out.println("Mode: full news-only run");
        }
    }

    private static void writeNewsDashboardSafely(StockDashboardWriter dashboardWriter,
            List<StockAnalysisResultVO> results, List<StockAnalysisResultVO> likelyCandidates,
            List<StockAnalysisResultVO> watchlistCandidates, List<StockAnalysisResultVO> likelyVolumeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeCandidates, String fileName) {
        try {
            String path = dashboardWriter.writeDashboard(results, likelyCandidates, watchlistCandidates,
                    likelyVolumeCandidates, nonLikelyVolumeCandidates, fileName);
            System.out.println("News-updated dashboard: " + path);
        } catch (Exception ex) {
            System.out.println("Cannot write " + fileName + ": " + ex.getMessage());
        }
    }

    private static void writeStaticSiteDataSafely(TaiwanStockAnalyzer analyzer, List<StockAnalysisResultVO> results) {
        try {
            new StockStaticApiExporter().writeOutputsForResults(new java.io.File("web\\data", "latest.json").getPath(),
                    new java.io.File("web\\data", "history.json").getPath(), analyzer.currentDateStamp(), results);
            System.out.println("Static site data: " + new java.io.File("web\\data").getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Cannot write static site data: " + ex.getMessage());
        }
    }
}
