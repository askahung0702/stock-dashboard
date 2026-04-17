package stock;

import java.util.List;

import stock.vo.StockAnalysisResultVO;

public class StockNewsOnlyAnalysis {

    public static void main(String[] args) throws Exception {
        int maxStocks = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        String newsOnlyFileName = analyzer.buildDatedFileName("stock_news_only");
        String themeReferenceFileName = analyzer.buildDatedFileName("stock_news_theme_reference");
        String themeMarketReferenceFileName = analyzer.buildDatedFileName("stock_news_market_reference");
        String themeMarketCandidatesFileName = analyzer.buildDatedFileName("stock_news_market_candidates");

        List<StockAnalysisResultVO> results = analyzer.analyzeNewsOnly(maxStocks);
        analyzer.writeNewsOnlyCsv(results, newsOnlyFileName);
        analyzer.writeThemeReferenceCsv(results, themeReferenceFileName);
        analyzer.writeThemeMarketReferenceCsv(themeMarketReferenceFileName);
        analyzer.writeThemeMarketCandidatesCsv(themeMarketCandidatesFileName);

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
}
