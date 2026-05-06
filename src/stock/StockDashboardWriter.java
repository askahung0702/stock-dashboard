package stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stock.common.NumberParser;
import stock.vo.StockAnalysisResultVO;
import stock.vo.TaiwanStockVO;

public class StockDashboardWriter {

    private static final int SPOTLIGHT_LIMIT = 12;
    private static final int CHANGE_LIMIT = 12;
    private static final int HISTORY_TABLE_LIMIT = 20;
    private static final double LIQUIDITY_GATE = 4D;
    private static final double COMPLETE_SNAPSHOT_RATIO = 0.6D;
    private static final int COMPLETE_SNAPSHOT_MIN_ROWS = 200;

    private final String runDate;
    private final double likelyThreshold;
    private final double watchlistThreshold;
    private final double volumeSurgeThreshold;
    private final StockHistoryDatabase historyDatabase = new StockHistoryDatabase();

    public StockDashboardWriter(String runDate, double likelyThreshold, double watchlistThreshold,
            double volumeSurgeThreshold) {
        this.runDate = runDate;
        this.likelyThreshold = likelyThreshold;
        this.watchlistThreshold = watchlistThreshold;
        this.volumeSurgeThreshold = volumeSurgeThreshold;
    }

    public String writeDashboard(List<StockAnalysisResultVO> results, List<StockAnalysisResultVO> likelyCandidates,
            List<StockAnalysisResultVO> watchlistCandidates, List<StockAnalysisResultVO> likelyVolumeSurgeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeSurgeCandidates, String fileName) throws Exception {
        HistoryBundle historyBundle = buildHistoryBundle(results);
        DashboardData dashboardData = resolveDashboardData(results, likelyCandidates, watchlistCandidates,
                likelyVolumeSurgeCandidates, nonLikelyVolumeSurgeCandidates, historyBundle);
        File outputFile = new File(fileName);
        File parentDirectory = outputFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.exists()) {
            parentDirectory.mkdirs();
        }
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8")));
        writer.print(buildDashboardHtml(dashboardData.results, dashboardData.likelyCandidates,
                dashboardData.watchlistCandidates, dashboardData.likelyVolumeSurgeCandidates,
                dashboardData.nonLikelyVolumeSurgeCandidates, historyBundle));
        writer.close();
        return outputFile.getAbsolutePath();
    }

    public String writeHistoryDashboard(String fileName) throws Exception {
        return writeDashboard(Collections.<StockAnalysisResultVO>emptyList(),
                Collections.<StockAnalysisResultVO>emptyList(), Collections.<StockAnalysisResultVO>emptyList(),
                Collections.<StockAnalysisResultVO>emptyList(), Collections.<StockAnalysisResultVO>emptyList(),
                fileName);
    }

    public String renderHistoryDashboardHtml() throws Exception {
        HistoryBundle historyBundle = buildHistoryBundle(Collections.<StockAnalysisResultVO>emptyList());
        DashboardData dashboardData = resolveDashboardData(Collections.<StockAnalysisResultVO>emptyList(),
                Collections.<StockAnalysisResultVO>emptyList(), Collections.<StockAnalysisResultVO>emptyList(),
                Collections.<StockAnalysisResultVO>emptyList(), Collections.<StockAnalysisResultVO>emptyList(),
                historyBundle);
        return buildDashboardHtml(dashboardData.results, dashboardData.likelyCandidates,
                dashboardData.watchlistCandidates, dashboardData.likelyVolumeSurgeCandidates,
                dashboardData.nonLikelyVolumeSurgeCandidates, historyBundle);
    }

    public String renderHistoryDataJson() throws Exception {
        HistoryBundle historyBundle = buildHistoryBundle(Collections.<StockAnalysisResultVO>emptyList());
        return buildHistoryDataJson(historyBundle);
    }

    private String buildDashboardHtml(List<StockAnalysisResultVO> results, List<StockAnalysisResultVO> likelyCandidates,
            List<StockAnalysisResultVO> watchlistCandidates, List<StockAnalysisResultVO> likelyVolumeSurgeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeSurgeCandidates, HistoryBundle historyBundle) {
        String displayDate = historyBundle.currentDate.length() == 0 ? runDate : historyBundle.currentDate;
        StringBuilder builder = new StringBuilder(262144);
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"zh-Hant\">\n");
        builder.append("<head>\n");
        builder.append("  <meta charset=\"UTF-8\">\n");
        builder.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        builder.append("  <title>台股分析儀表板 ").append(html(displayDate)).append("</title>\n");
        builder.append("  <style>\n");
        appendStyles(builder);
        builder.append("  </style>\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("<div class=\"page\">\n");

        appendHero(builder, results, likelyCandidates, watchlistCandidates, likelyVolumeSurgeCandidates,
                nonLikelyVolumeSurgeCandidates, displayDate);
        appendDailyChangeSection(builder, historyBundle);
        appendTrendRankingSection(builder, historyBundle);
        appendSpotlightSection(builder, results, historyBundle);
        appendMarketSummarySection(builder, results, likelyCandidates);
        appendNewsRadarSection(builder, results, historyBundle);
        appendDetailedSection(builder, "收盤後高勝率候選", "品質、續航、結構、風報比同步達標，優先看這份名單。",
                filterPostCloseCategory(results, "高勝率候選"), historyBundle);
        appendDetailedSection(builder, "收盤後短線觀察", "偏向主線延伸與隔日確認，先觀察不當作主攻買進依據。",
                filterPostCloseCategory(results, "短線主攻"), historyBundle);
        appendDetailedSection(builder, "收盤後波段布局", "偏品質與風報比，適合找可分批布局的標的。",
                filterPostCloseCategory(results, "波段布局"), historyBundle);
        appendDetailedSection(builder, "收盤後催化觀察", "有題材或翻轉催化，但時機還沒漂亮，先觀察不追價。",
                filterPostCloseCategory(results, "催化觀察"), historyBundle);
        appendDetailedSection(builder, "比較有可能的股票", "高分且流動性達標，適合優先研究。", likelyCandidates,
                historyBundle);
        appendDetailedSection(builder, "觀察名單", "還差臨門一腳，適合每天收盤後追蹤。", watchlistCandidates,
                historyBundle);
        appendDetailedSection(builder, "Likely 且爆量", "量能同步放大，通常代表市場注意力開始集中。",
                likelyVolumeSurgeCandidates, historyBundle);
        appendDetailedSection(builder, "非 Likely 但爆量", "總分還沒進 likely，但交易面出現明顯活躍。",
                nonLikelyVolumeSurgeCandidates, historyBundle);
        appendHistoryExplorerSection(builder, historyBundle);
        appendFullRankingSection(builder, results, historyBundle);
        appendFootnoteSection(builder);

        builder.append("<script>\n");
        builder.append("window.STOCK_HISTORY_DATA=");
        builder.append(buildHistoryDataJson(historyBundle));
        builder.append(";\n");
        builder.append(buildDashboardScript());
        builder.append("</script>\n");
        builder.append("</div>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private void appendStyles(StringBuilder builder) {
        builder.append(
                "body{margin:0;font-family:\"Noto Sans TC\",\"Microsoft JhengHei\",sans-serif;color:#1f1b16;background:radial-gradient(circle at top left,rgba(241,205,164,.65),transparent 32%),radial-gradient(circle at top right,rgba(150,197,207,.48),transparent 28%),linear-gradient(180deg,#f8f5ef 0%,#efe7d8 100%);} ");
        builder.append(
                ".page{width:min(1440px,calc(100vw - 24px));margin:0 auto;padding:24px 0 48px;} h1,h2,h3{font-family:\"Palatino Linotype\",\"Noto Serif TC\",serif;margin:0;} ");
        builder.append(
                ".hero,.panel{background:rgba(255,251,245,.90);border:1px solid rgba(64,52,40,.14);border-radius:28px;box-shadow:0 22px 50px rgba(43,29,11,.12);padding:24px;} .panel{margin-top:20px;} ");
        builder.append(
                ".eyebrow{font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#8c3316;} .hero p,.hint,.subline,.notes,.footer-note{color:#6c655c;line-height:1.8;} ");
        builder.append(
                ".meta-row,.chip-group{display:flex;flex-wrap:wrap;gap:10px;} .metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));margin-top:20px;gap:14px;} ");
        builder.append(
                ".meta-pill,.market-pill,.score-pill,.chip-button{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:8px 14px;font-size:13px;border:1px solid rgba(105,81,60,.14);background:rgba(255,255,255,.68);color:#3f342b;} ");
        builder.append(
                ".metric-card,.spotlight-card,.change-card,.chart-card{padding:18px;border-radius:20px;background:linear-gradient(180deg,rgba(255,255,255,.92),rgba(247,238,227,.78));border:1px solid rgba(104,75,43,.10);} ");
        builder.append(
                ".metric-label{font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#6c655c;} .metric-value{margin-top:10px;font-size:32px;font-weight:700;} ");
        builder.append(
                ".section-head{display:flex;justify-content:space-between;align-items:end;gap:16px;flex-wrap:wrap;margin-bottom:16px;} ");
        builder.append(
                ".spotlight-grid,.change-grid,.chart-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px;} ");
        builder.append(
                ".score-track{margin-top:12px;height:12px;border-radius:999px;background:rgba(29,95,116,.10);overflow:hidden;} .score-fill{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#efbc77,#ba5a31);} ");
        builder.append(
                ".market-pill.twse{background:#d9efe2;} .market-pill.tpex{background:#f6dfc6;} .score-pill.strong{background:rgba(29,95,116,.12);color:#154d5d;font-weight:700;} .score-pill.watch{background:rgba(186,90,49,.12);color:#8c3316;font-weight:700;} .score-pill.neutral{background:rgba(0,0,0,.05);color:#4b433c;font-weight:700;} ");
        builder.append(
                ".table-shell{overflow:auto;border-radius:20px;border:1px solid rgba(90,71,48,.12);background:rgba(255,255,255,.75);} table{width:100%;border-collapse:collapse;min-width:1120px;} th,td{padding:13px 12px;border-bottom:1px solid rgba(95,76,54,.10);text-align:left;vertical-align:top;font-size:13px;} th{position:sticky;top:0;background:rgba(247,241,231,.96);font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#6c655c;} tbody tr:hover{background:rgba(29,95,116,.05);} .stock-cell strong{display:block;font-size:15px;} ");
        builder.append(
                ".toolbar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-bottom:16px;} .toolbar input,.toolbar select{flex:1 1 220px;min-width:220px;padding:11px 14px;border-radius:999px;border:1px solid rgba(90,71,48,.16);font-size:14px;background:rgba(255,255,255,.82);} .chip-button{cursor:pointer;} .chip-button.active{background:#1d5f74;color:#fff;border-color:#1d5f74;} ");
        builder.append(
                "details{border-radius:14px;background:rgba(255,247,237,.78);border:1px solid rgba(138,112,77,.12);padding:10px 12px;} summary{cursor:pointer;font-weight:700;color:#7b3d21;} .reason-grid{display:grid;gap:10px;margin-top:12px;} .reason-item{padding:10px 12px;border-radius:12px;background:rgba(255,255,255,.72);border:1px solid rgba(102,80,51,.10);} .reason-item strong{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#6c655c;margin-bottom:6px;} ");
        builder.append(
                ".empty{border-radius:20px;padding:18px;background:rgba(29,95,116,.05);border:1px dashed rgba(29,95,116,.18);color:#6c655c;line-height:1.8;} ");
        builder.append(
                ".delta-pos{color:#0f6b45;font-weight:700;} .delta-neg{color:#a24125;font-weight:700;} .delta-flat{color:#6c655c;font-weight:700;} ");
        builder.append(
                ".mini-table{width:100%;border-collapse:collapse;min-width:0;} .mini-table td{padding:9px 0;border-bottom:1px solid rgba(95,76,54,.08);font-size:13px;} .mini-table tr:last-child td{border-bottom:none;} ");
        builder.append(
                ".change-card h3,.chart-card h3{margin-bottom:10px;} .change-note{font-size:13px;color:#6c655c;margin-bottom:12px;} ");
        builder.append(
                ".chart-card svg{width:100%;height:220px;display:block;background:rgba(255,255,255,.66);border-radius:14px;} ");
        builder.append(
                ".explorer-head{margin-bottom:12px;} .lookup-help{font-size:13px;color:#6c655c;margin-bottom:14px;} .history-view{display:none;} .history-view.visible{display:block;} .history-empty.hidden{display:none;} ");
        builder.append(
                "@media (max-width:900px){.page{width:min(100vw - 14px,1440px);padding-top:14px}.hero,.panel{padding:18px;border-radius:22px}.metric-grid,.spotlight-grid,.change-grid,.chart-grid{grid-template-columns:1fr}}");
    }

    private void appendHero(StringBuilder builder, List<StockAnalysisResultVO> results,
            List<StockAnalysisResultVO> likelyCandidates, List<StockAnalysisResultVO> watchlistCandidates,
            List<StockAnalysisResultVO> likelyVolumeSurgeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeSurgeCandidates, String displayDate) {
        int highConvictionCount = filterPostCloseCategory(results, "高勝率候選").size();
        int momentumAttackCount = filterPostCloseCategory(results, "短線主攻").size();
        int swingPositionCount = filterPostCloseCategory(results, "波段布局").size();
        int newsRadarCount = filterNewsRadarCandidates(results).size();
        builder.append("<section class=\"hero\">");
        builder.append("<div class=\"eyebrow\">Taiwan Stock Dashboard</div>");
        builder.append("<h1>台股分析網頁報表</h1>");
        builder.append("<p>除了當天的高分候選與爆量名單，這份頁面現在也會把股票拆成收盤後高勝率、短線觀察、波段布局、催化觀察四種名單，並把每天跑出來的快照串成歷史資料，讓你看今天相較前一天的變化，以及每一檔股票的分數、價格和量比趨勢。</p>");
        builder.append("<div class=\"meta-row\">");
        pill(builder, "資料日期 " + displayDate);
        pill(builder, "Likely 門檻 " + format(likelyThreshold));
        pill(builder, "觀察門檻 " + format(watchlistThreshold));
        pill(builder, "爆量門檻 volumeRatio ≥ " + format(volumeSurgeThreshold));
        builder.append("</div>");
        builder.append("<div class=\"metric-grid\">");
        metric(builder, "全部標的", Integer.toString(results.size()), "本次完成分析的股票數");
        metric(builder, "高勝率候選", Integer.toString(highConvictionCount), "收盤後最優先研究名單");
        metric(builder, "短線觀察", Integer.toString(momentumAttackCount), "偏強勢主線與隔日確認");
        metric(builder, "波段布局", Integer.toString(swingPositionCount), "偏品質與風報比");
        metric(builder, "新聞雷達", Integer.toString(newsRadarCount), "多來源、時間較新、可信度較高");
        metric(builder, "比較有可能", Integer.toString(likelyCandidates.size()), "分數達標且流動性通過");
        metric(builder, "觀察名單", Integer.toString(watchlistCandidates.size()), "介於觀察區間的標的");
        metric(builder, "Likely 且爆量", Integer.toString(likelyVolumeSurgeCandidates.size()), "高分且量能放大");
        metric(builder, "非 Likely 但爆量", Integer.toString(nonLikelyVolumeSurgeCandidates.size()), "量能活躍但總分未達標");
        metric(builder, "最高分", results.isEmpty() ? "0.00" : format(results.get(0).getScore()),
                results.isEmpty() ? "本次沒有成功抓到資料"
                        : results.get(0).getStock().getCode() + " " + results.get(0).getStock().getName());
        builder.append("</div>");
        builder.append("</section>");
    }

    private DashboardData resolveDashboardData(List<StockAnalysisResultVO> results,
            List<StockAnalysisResultVO> likelyCandidates, List<StockAnalysisResultVO> watchlistCandidates,
            List<StockAnalysisResultVO> likelyVolumeSurgeCandidates,
            List<StockAnalysisResultVO> nonLikelyVolumeSurgeCandidates, HistoryBundle historyBundle) {
        DashboardData dashboardData = new DashboardData();
        if (results != null && !results.isEmpty()) {
            dashboardData.results = results;
            dashboardData.likelyCandidates = likelyCandidates;
            dashboardData.watchlistCandidates = watchlistCandidates;
            dashboardData.likelyVolumeSurgeCandidates = likelyVolumeSurgeCandidates;
            dashboardData.nonLikelyVolumeSurgeCandidates = nonLikelyVolumeSurgeCandidates;
            return dashboardData;
        }

        dashboardData.results = buildResultsFromSnapshot(historyBundle.currentSnapshot);
        dashboardData.likelyCandidates = filterLikelyCandidates(dashboardData.results);
        dashboardData.watchlistCandidates = filterWatchlistCandidates(dashboardData.results);
        dashboardData.likelyVolumeSurgeCandidates = filterLikelyVolumeSurgeCandidates(dashboardData.results);
        dashboardData.nonLikelyVolumeSurgeCandidates = filterNonLikelyVolumeSurgeCandidates(dashboardData.results);
        return dashboardData;
    }

    private List<StockAnalysisResultVO> buildResultsFromSnapshot(SnapshotData snapshot) {
        List<StockAnalysisResultVO> results = new ArrayList<StockAnalysisResultVO>();
        if (snapshot == null) {
            return results;
        }

        for (HistoryPoint point : snapshot.rows) {
            StockAnalysisResultVO result = new StockAnalysisResultVO();
            result.setStock(new TaiwanStockVO(point.code, point.name, point.market, yahooSuffix(point.market)));
            result.setIndustry(point.industry);
            result.setScore(point.score);
            result.setRawScore(point.rawScore);
            result.setSelectionScore(point.selectionScore);
            result.setMomentumScore(point.momentumScore);
            result.setQualityScore(point.qualityScore);
            result.setSelectionQualified(point.selectionQualified);
            result.setRevenueScore(point.revenueScore);
            result.setChipsScore(point.chipsScore);
            result.setLiquidityScore(point.liquidityScore);
            result.setValuationScore(point.valuationScore);
            result.setTechnicalScore(point.technicalScore);
            result.setFinancialQualityScore(point.financialQualityScore);
            result.setCurrentPrice(point.price);
            result.setVolumeRatio(point.volumeRatio);
            result.setReturn20DayPct(point.return20DayPct);
            result.setFiveDayInstitutionalNetRatioPct(point.fiveDayInstitutionalNetRatioPct);
            result.setBrokerNetRatioPct(point.brokerNetRatioPct);
            result.setAnalysisNote(point.note);
            result.setScoreReason(point.scoreReason);
            result.setRevenueReason(point.revenueReason);
            result.setChipsReason(point.chipsReason);
            result.setLiquidityReason(point.liquidityReason);
            result.setValuationReason(point.valuationReason);
            result.setTechnicalReason(point.technicalReason);
            result.setFinancialQualityReason(point.financialQualityReason);
            result.setEventRiskReason(point.eventRiskReason);
            result.setEligibilityReason(point.eligibilityReason);
            result.setThemeScore(point.themeScore);
            result.setPrimaryTheme(point.primaryTheme);
            result.setThemeTags(point.themeTags);
            result.setNewsScore(point.newsScore);
            result.setNewsRiskScore(point.newsRiskScore);
            result.setRelativeStrengthScore(point.relativeStrengthScore);
            result.setIndustryReturnStrength(point.industryReturnStrength);
            result.setIndustryVolumeStrength(point.industryVolumeStrength);
            result.setIndustryFlowStrength(point.industryFlowStrength);
            result.setEventDirection(point.eventDirection);
            result.setEventConfidence(point.eventConfidence);
            result.setEventFreshnessDays(point.eventFreshnessDays);
            result.setEventTypeSummary(point.eventTypeSummary);
            result.setNewsSummary(point.newsSummary);
            result.setNewsDigest(point.newsDigest);
            result.setNewsSourceSummary(point.newsSourceSummary);
            result.setLatestNewsPublishedHint(point.latestNewsPublishedHint);
            result.setNewsSourceCredibilityScore(point.newsSourceCredibilityScore);
            result.setNewsFreshnessScore(point.newsFreshnessScore);
            result.setNewsSourceCount(point.newsSourceCount);
            result.setNewsOfficialSourceCount(point.newsOfficialSourceCount);
            result.setNewsMediaSourceCount(point.newsMediaSourceCount);
            result.setCompanySummary(point.companySummary);
            result.setRecentNewsBrief(point.recentNewsBrief);
            result.setTransformationHint(point.transformationHint);
            result.setPracticalAdvice(point.practicalAdvice);
            result.setAdviceConfidence(point.adviceConfidence);
            result.setSignalType(point.signalType);
            result.setSignalHorizonDays(point.signalHorizonDays);
            result.setEntryRule(point.entryRule);
            result.setExitRule(point.exitRule);
            result.setValidationMode(point.validationMode);
            result.setHardExclude(point.hardExclude);
            result.setHardExcludeReason(point.hardExcludeReason);
            result.setDataQualityGrade(point.dataQualityGrade);
            result.setCoreConditionCount(point.coreConditionCount);
            result.setWinratePriorityScore(point.winratePriorityScore);
            result.setExpectedReturnScore(point.expectedReturnScore);
            result.setMaxDrawdownPenalty(point.maxDrawdownPenalty);
            result.setBacktestCohort(point.backtestCohort);
            result.setPostClosePriorityScore(point.postClosePriorityScore);
            result.setPostCloseCategory(point.postCloseCategory);
            result.setPostCloseAction(point.postCloseAction);
            result.setPostCloseReason(point.postCloseReason);
            results.add(result);
        }

        Collections.sort(results, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int scoreCompare = Double.compare(right.getSelectionScore(), left.getSelectionScore());
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return left.getStock().getCode().compareTo(right.getStock().getCode());
            }
        });
        return results;
    }

    private List<StockAnalysisResultVO> filterLikelyCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (isLikelyCandidate(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterWatchlistCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (result.getSelectionScore() >= watchlistThreshold && result.getSelectionScore() < likelyThreshold
                    && isSelectionQualified(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterPostCloseCategory(List<StockAnalysisResultVO> results, String category) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (category.equals(result.getPostCloseCategory())) {
                filtered.add(result);
            }
        }
        Collections.sort(filtered, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int priorityCompare = Double.compare(right.getPostClosePriorityScore(), left.getPostClosePriorityScore());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Double.compare(right.getSelectionScore(), left.getSelectionScore());
            }
        });
        return filtered;
    }

    private List<StockAnalysisResultVO> filterNewsRadarCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            boolean qualified = result.getNewsScore() >= 62D || result.getNewsSourceCount() >= 2
                    || result.getNewsFreshnessScore() >= 60D
                    || result.getNewsSourceCredibilityScore() >= 75D
                    || result.getMarketThemeReferenceScore() >= 60D;
            if (qualified) {
                filtered.add(result);
            }
        }
        Collections.sort(filtered, new Comparator<StockAnalysisResultVO>() {
            public int compare(StockAnalysisResultVO left, StockAnalysisResultVO right) {
                int priorityCompare = Double.compare(newsRadarPriority(right), newsRadarPriority(left));
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Double.compare(right.getNewsScore(), left.getNewsScore());
            }
        });
        return filtered;
    }

    private double newsRadarPriority(StockAnalysisResultVO result) {
        double score = result.getNewsScore() * 0.42D + result.getNewsFreshnessScore() * 0.18D
                + result.getNewsSourceCredibilityScore() * 0.16D + result.getMarketThemeReferenceScore() * 0.10D
                + result.getThemeReferenceScore() * 0.08D + Math.min(10D, result.getNewsSourceCount() * 3D);
        if (result.getNewsRiskScore() > 60D) {
            score -= (result.getNewsRiskScore() - 60D) * 0.18D;
        }
        return score;
    }

    private List<StockAnalysisResultVO> filterLikelyVolumeSurgeCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (isLikelyCandidate(result) && hasVolumeSurge(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private List<StockAnalysisResultVO> filterNonLikelyVolumeSurgeCandidates(List<StockAnalysisResultVO> results) {
        List<StockAnalysisResultVO> filtered = new ArrayList<StockAnalysisResultVO>();
        for (StockAnalysisResultVO result : results) {
            if (!isLikelyCandidate(result) && hasVolumeSurge(result) && isSelectionQualified(result)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private void appendDailyChangeSection(StringBuilder builder, HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>每日變化分析</h2><p class=\"hint\">把今天的結果和前一個交易日快照比較，快速找出變動最大的股票。</p></div></div>");
        builder.append("<div class=\"metric-grid\">");
        metric(builder, "歷史天數", Integer.toString(historyBundle.dates.size()),
                historyBundle.dates.isEmpty() ? "目前尚無歷史資料" : "從 " + historyBundle.dates.get(0) + " 開始累積");
        metric(builder, "前一個比較日",
                historyBundle.previousDate.length() == 0 ? "-" : historyBundle.previousDate,
                historyBundle.previousDate.length() == 0 ? "還沒有前一天可比較" : "和今天做日變化比較");
        metric(builder, "可比較股票", Integer.toString(historyBundle.comparableCount), "今天與前一日都有資料的股票數");
        metric(builder, "新進 Likely", Integer.toString(historyBundle.newLikely.size()), "今天進入 likely、前一日未進入");
        metric(builder, "跌出 Likely", Integer.toString(historyBundle.droppedLikely.size()), "前一日還在 likely、今天掉出");
        metric(builder, "平均分數變化", signed(historyBundle.averageScoreDelta),
                historyBundle.previousDate.length() == 0 ? "尚無比較基準" : "所有可比較股票的平均日變化");
        builder.append("</div>");
        if (historyBundle.previousDate.length() == 0) {
            appendEmpty(builder, "目前只有今天這一份資料，所以網頁還無法計算日變化。等你累積到下一個交易日後，這裡就會開始顯示分數上升、股價上升和 Likely 狀態變動。");
        } else {
            builder.append("<div class=\"change-grid\">");
            appendChangeCard(builder, "今日分數上升最多", "和 " + historyBundle.previousDate + " 相比總分提升最多的股票",
                    historyBundle.scoreRisers, ChangeMode.SCORE);
            appendChangeCard(builder, "今日分數下降最多", "總分掉最多，通常適合回頭檢查技術或籌碼轉弱原因",
                    historyBundle.scoreFallers, ChangeMode.SCORE);
            appendChangeCard(builder, "今日股價上升最多", "對照昨天快照後，價格漲幅最大的股票", historyBundle.priceRisers,
                    ChangeMode.PRICE);
            appendChangeCard(builder, "今日量比放大最多", "量能比前一日變得更活躍的股票", historyBundle.volumeRisers,
                    ChangeMode.VOLUME);
            appendChangeCard(builder, "新進 Likely", "今天正式跨過 likely 門檻的股票", historyBundle.newLikely,
                    ChangeMode.STATUS);
            appendChangeCard(builder, "跌出 Likely", "今天掉出 likely 區間的股票", historyBundle.droppedLikely,
                    ChangeMode.STATUS);
            builder.append("</div>");
        }
        builder.append("</section>");
    }

    private void appendTrendRankingSection(StringBuilder builder, HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>最近 N 天分數變化排名</h2><p class=\"hint\">這裡不是只看今天對昨天，而是可以切換近 1、3、5、10、20 個交易日，找出最近一段時間持續轉強或轉弱的股票。</p></div></div>");
        builder.append("<div class=\"toolbar\">");
        builder.append("<select id=\"trend-lookback-days\">");
        builder.append("<option value=\"1\">近 1 日</option>");
        builder.append("<option value=\"3\">近 3 日</option>");
        builder.append("<option value=\"5\" selected>近 5 日</option>");
        builder.append("<option value=\"10\">近 10 日</option>");
        builder.append("<option value=\"20\">近 20 日</option>");
        builder.append("</select>");
        builder.append("</div>");
        builder.append("<div id=\"trend-summary\" class=\"metric-grid\"></div>");
        builder.append("<div class=\"change-grid\">");
        builder.append("<article class=\"change-card\"><h3>分數上升最多</h3><div class=\"change-note\">近期持續往上修正的股票</div><div id=\"trend-score-risers\"></div></article>");
        builder.append("<article class=\"change-card\"><h3>分數下降最多</h3><div class=\"change-note\">近期轉弱最明顯的股票</div><div id=\"trend-score-fallers\"></div></article>");
        builder.append("<article class=\"change-card\"><h3>股價上升最多</h3><div class=\"change-note\">同一期間股價漲幅最大的股票</div><div id=\"trend-price-risers\"></div></article>");
        builder.append("<article class=\"change-card\"><h3>量比放大最多</h3><div class=\"change-note\">同一期間成交活躍度增加最多的股票</div><div id=\"trend-volume-risers\"></div></article>");
        builder.append("</div>");
        if (historyBundle.dates.size() <= 1) {
            appendEmpty(builder, "目前歷史天數還不夠，等你再累積幾天資料後，這裡就會開始顯示近 N 天分數變化排名。");
        }
        builder.append("</section>");
    }

    private void appendSpotlightSection(StringBuilder builder, List<StockAnalysisResultVO> results,
            HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>高分焦點</h2><p class=\"hint\">快速看本次排序最前面的標的，並一起看它們相較前一日是否持續轉強。</p></div></div>");
        if (results.isEmpty()) {
            appendEmpty(builder, "這次沒有成功抓到可分析資料，所以暫時沒有高分焦點。");
        } else {
            builder.append("<div class=\"spotlight-grid\">");
            int limit = Math.min(SPOTLIGHT_LIMIT, results.size());
            for (int i = 0; i < limit; i++) {
                StockAnalysisResultVO result = results.get(i);
                StockSeries series = historyBundle.seriesByCode.get(result.getStock().getCode());
                builder.append("<article class=\"spotlight-card\">");
                builder.append("<div><strong>").append(html(result.getStock().getCode())).append(" ")
                        .append(html(result.getStock().getName())).append("</strong><div class=\"subline\">")
                        .append(html(marketLabel(result.getStock().getMarket()))).append(" · 現價 ")
                        .append(format(result.getCurrentPrice())).append(" · 量比 ").append(format(result.getVolumeRatio()))
                        .append(" · 分數日變 ").append(deltaSpan(scoreDelta(series), "")).append("</div></div>");
                builder.append("<div class=\"score-track\"><span class=\"score-fill\" style=\"width:")
                        .append(format(result.getScore())).append("%\"></span></div>");
                builder.append("<div class=\"subline\">總分 ").append(format(result.getScore())).append(" · 20 日報酬 ")
                        .append(format(result.getReturn20DayPct())).append("% · 股價日變 ")
                        .append(deltaSpan(priceDeltaPct(series), "%")).append("</div>");
                builder.append("<div class=\"notes\">").append(html(emptyIfBlank(result.getAnalysisNote(), "目前沒有分析摘要")))
                        .append("</div>");
                builder.append("</article>");
            }
            builder.append("</div>");
        }
        builder.append("</section>");
    }

    private void appendMarketSummarySection(StringBuilder builder, List<StockAnalysisResultVO> results,
            List<StockAnalysisResultVO> likelyCandidates) {
        int totalTwse = countByMarket(results, "TWSE");
        int totalTpex = countByMarket(results, "TPEX");
        int likelyTwse = countByMarket(likelyCandidates, "TWSE");
        int likelyTpex = countByMarket(likelyCandidates, "TPEX");
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>市場分布</h2><p class=\"hint\">觀察這次高分股偏向上市還是上櫃，避免只盯一邊。</p></div></div>");
        builder.append("<div class=\"metric-grid\">");
        metric(builder, "全部上市", Integer.toString(totalTwse), "占全部 " + format(percent(totalTwse, results.size())) + "%");
        metric(builder, "全部上櫃", Integer.toString(totalTpex), "占全部 " + format(percent(totalTpex, results.size())) + "%");
        metric(builder, "Likely 上市", Integer.toString(likelyTwse),
                "占 likely " + format(percent(likelyTwse, likelyCandidates.size())) + "%");
        metric(builder, "Likely 上櫃", Integer.toString(likelyTpex),
                "占 likely " + format(percent(likelyTpex, likelyCandidates.size())) + "%");
        builder.append("</div>");
        builder.append("</section>");
    }

    private void appendNewsRadarSection(StringBuilder builder, List<StockAnalysisResultVO> results,
            HistoryBundle historyBundle) {
        List<StockAnalysisResultVO> radar = filterNewsRadarCandidates(results);
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>新聞雷達</h2><p class=\"hint\">把新聞多來源、最新時間與來源可信度直接攤開來看，先抓出真的有被消息推動的股票。</p></div><span class=\"meta-pill\">共 ")
                .append(radar.size()).append(" 檔</span></div>");
        if (radar.isEmpty()) {
            appendEmpty(builder, "目前沒有同時符合新聞熱度、時間或可信度條件的標的。");
        } else {
            builder.append("<div class=\"spotlight-grid\">");
            int limit = Math.min(SPOTLIGHT_LIMIT, radar.size());
            for (int i = 0; i < limit; i++) {
                StockAnalysisResultVO result = radar.get(i);
                StockSeries series = historyBundle.seriesByCode.get(result.getStock().getCode());
                builder.append("<article class=\"spotlight-card\">");
                builder.append("<div><strong>").append(html(result.getStock().getCode())).append(" ")
                        .append(html(result.getStock().getName())).append("</strong><div class=\"subline\">")
                        .append(html(emptyIfBlank(result.getIndustry(), "未分類"))).append(" · 最新 ")
                        .append(html(emptyIfBlank(result.getLatestNewsPublishedHint(), "時間未標示"))).append("</div></div>");
                builder.append("<div class=\"chip-group\" style=\"margin-top:12px;\">");
                pill(builder, "新聞 " + format(result.getNewsScore()));
                pill(builder, "風險 " + format(result.getNewsRiskScore()));
                pill(builder, "新鮮度 " + format(result.getNewsFreshnessScore()));
                pill(builder, "可信度 " + format(result.getNewsSourceCredibilityScore()));
                builder.append("</div>");
                builder.append("<div class=\"subline\" style=\"margin-top:10px;\">來源 ")
                        .append(html(emptyIfBlank(result.getNewsSourceSummary(), "來源不足"))).append(" · 分數日變 ")
                        .append(deltaSpan(scoreDelta(series), "")).append("</div>");
                builder.append("<div class=\"notes\" style=\"margin-top:10px;\">")
                        .append(html(emptyIfBlank(result.getNewsDigest(), emptyIfBlank(result.getNewsSummary(), "目前沒有新聞摘要"))))
                        .append("</div>");
                if (result.getMarketThemeReferenceTheme() != null && result.getMarketThemeReferenceTheme().length() > 0) {
                    builder.append("<div class=\"subline\" style=\"margin-top:10px;\">市場題材 ")
                            .append(html(result.getMarketThemeReferenceTheme())).append(" / ")
                            .append(format(result.getMarketThemeReferenceScore())).append(" 分</div>");
                }
                builder.append("</article>");
            }
            builder.append("</div>");
        }
        builder.append("</section>");
    }

    private void appendDetailedSection(StringBuilder builder, String title, String description,
            List<StockAnalysisResultVO> results, HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>").append(html(title)).append("</h2><p class=\"hint\">")
                .append(html(description)).append("</p></div><span class=\"meta-pill\">共 ").append(results.size())
                .append(" 檔</span></div>");
        if (results.isEmpty()) {
            appendEmpty(builder, "目前這個區塊沒有標的，代表本次資料沒有股票符合這個條件。");
        } else {
            builder.append("<div class=\"table-shell\"><table><thead><tr><th>#</th><th>股票</th><th>市場</th><th>總分</th><th>分數日變</th><th>股價日變</th><th>現價</th><th>量比</th><th>20日報酬</th><th>籌碼</th><th>摘要</th><th>原因</th></tr></thead><tbody>");
            for (int i = 0; i < results.size(); i++) {
                StockAnalysisResultVO result = results.get(i);
                StockSeries series = historyBundle.seriesByCode.get(result.getStock().getCode());
                builder.append("<tr><td>").append(i + 1).append("</td><td class=\"stock-cell\"><strong>")
                        .append(html(result.getStock().getCode())).append(" ").append(html(result.getStock().getName()))
                        .append("</strong><span class=\"subline\">").append(html(emptyIfBlank(result.getIndustry(), "未分類")));
                if (result.getPostCloseCategory() != null && result.getPostCloseCategory().length() > 0) {
                    builder.append(" · ").append(html(result.getPostCloseCategory()));
                    if (result.getPostCloseAction() != null && result.getPostCloseAction().length() > 0) {
                        builder.append(" / ").append(html(result.getPostCloseAction()));
                    }
                }
                if (result.getSignalType() != null && result.getSignalType().length() > 0) {
                    builder.append(" · ").append(html(result.getSignalType()));
                    if (result.getSignalHorizonDays() > 0) {
                        builder.append(" ").append(result.getSignalHorizonDays()).append("日");
                    }
                }
                if (result.getEventDirection() != null && result.getEventDirection().length() > 0
                        && !"中性待確認".equals(result.getEventDirection())) {
                    builder.append(" · ").append(html(result.getEventDirection()));
                }
                if (result.getBacktestCohort() != null && result.getBacktestCohort().length() > 0
                        && !"N/A".equals(result.getBacktestCohort())) {
                    builder.append(" · ").append(html(result.getBacktestCohort()));
                }
                builder.append("</span></td><td><span class=\"market-pill ").append(marketClass(result.getStock().getMarket()))
                        .append("\">").append(html(marketLabel(result.getStock().getMarket()))).append("</span></td><td>")
                        .append(renderScorePill(result.getScore())).append("</td><td>")
                        .append(deltaSpan(scoreDelta(series), "")).append("</td><td>")
                        .append(deltaSpan(priceDeltaPct(series), "%")).append("</td><td>")
                        .append(format(result.getCurrentPrice())).append("</td><td>").append(format(result.getVolumeRatio()))
                        .append("</td><td>").append(format(result.getReturn20DayPct()))
                        .append("%</td><td><div class=\"notes\">5日法人比 ")
                        .append(format(result.getFiveDayInstitutionalNetRatioPct())).append("%<br>主力比 ")
                        .append(format(result.getBrokerNetRatioPct())).append("%</div></td><td><div class=\"notes\">")
                        .append(html(emptyIfBlank(result.getAnalysisNote(), "目前沒有分析摘要")))
                        .append("</div></td><td>");
                if (result.getPostCloseReason() != null && result.getPostCloseReason().length() > 0) {
                    builder.append("<div class=\"notes\">").append(html(result.getPostCloseReason())).append("</div>");
                }
                appendReasons(builder, result);
                builder.append("</td></tr>");
            }
            builder.append("</tbody></table></div>");
        }
        builder.append("</section>");
    }

    private void appendHistoryExplorerSection(StringBuilder builder, HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>個股歷史追蹤</h2><p class=\"hint\">輸入代碼或名稱，查看這檔股票每天的分數、股價、量比與 Likely 狀態變化。</p></div></div>");
        builder.append("<div class=\"toolbar\">");
        builder.append("<input id=\"history-stock-search\" type=\"text\" list=\"history-stock-options\" placeholder=\"搜尋股票代碼或名稱，例如 1101 或 台泥\">");
        builder.append("<datalist id=\"history-stock-options\">");
        for (StockSeries series : historyBundle.seriesList) {
            builder.append("<option value=\"").append(attr(series.code + " " + series.name)).append("\">");
        }
        builder.append("</datalist>");
        builder.append("</div>");
        builder.append("<div class=\"lookup-help\">如果你每天都執行分析，這裡會越來越完整。頁面預設會先帶出本次排名第一的股票。</div>");
        builder.append("<div id=\"history-empty\" class=\"empty history-empty\">輸入股票代碼或名稱後，這裡會顯示該股票近幾天的變化與趨勢圖。</div>");
        builder.append("<div id=\"history-view\" class=\"history-view\">");
        builder.append("<div class=\"explorer-head\"><h3 id=\"history-stock-title\">個股歷史</h3><div id=\"history-stock-subtitle\" class=\"subline\"></div></div>");
        builder.append("<div id=\"history-stock-metrics\" class=\"metric-grid\"></div>");
        builder.append("<div class=\"chart-grid\">");
        builder.append("<article class=\"chart-card\"><h3>分數趨勢</h3><svg id=\"history-score-chart\"></svg></article>");
        builder.append("<article class=\"chart-card\"><h3>股價趨勢</h3><svg id=\"history-price-chart\"></svg></article>");
        builder.append("<article class=\"chart-card\"><h3>量比趨勢</h3><svg id=\"history-volume-chart\"></svg></article>");
        builder.append("</div>");
        builder.append("<div class=\"table-shell\" style=\"margin-top:16px;\"><table><thead><tr><th>日期</th><th>總分</th><th>分數日變</th><th>股價</th><th>股價日變</th><th>量比</th><th>量比日變</th><th>Likely</th></tr></thead><tbody id=\"history-stock-table-body\"></tbody></table></div>");
        builder.append("</div>");
        builder.append("</section>");
    }

    private void appendFullRankingSection(StringBuilder builder, List<StockAnalysisResultVO> results,
            HistoryBundle historyBundle) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>完整排行</h2><p class=\"hint\">可以用搜尋、最低分數與市場切換快速縮小範圍，也能一起看每檔股票相較前一日的變化。</p></div></div>");
        builder.append("<div class=\"toolbar\"><input id=\"ranking-search\" type=\"text\" placeholder=\"搜尋股票代碼、名稱、產業或原因\"><input id=\"ranking-min-score\" type=\"number\" min=\"0\" max=\"100\" step=\"1\" value=\"0\" placeholder=\"最低分數\"><div class=\"chip-group\"><button class=\"chip-button active\" type=\"button\" data-market=\"ALL\">全部</button><button class=\"chip-button\" type=\"button\" data-market=\"TWSE\">上市</button><button class=\"chip-button\" type=\"button\" data-market=\"TPEX\">上櫃</button></div></div>");
        if (results.isEmpty()) {
            appendEmpty(builder, "目前沒有任何排行資料。");
        } else {
            builder.append("<div class=\"table-shell\"><table id=\"ranking-table\"><thead><tr><th>#</th><th>股票</th><th>市場</th><th>總分</th><th>分數日變</th><th>股價日變</th><th>量比日變</th><th>現價</th><th>營收</th><th>籌碼</th><th>技術</th><th>估值</th><th>流動性</th><th>量比</th><th>摘要</th></tr></thead><tbody>");
            for (int i = 0; i < results.size(); i++) {
                StockAnalysisResultVO result = results.get(i);
                StockSeries series = historyBundle.seriesByCode.get(result.getStock().getCode());
                builder.append("<tr data-market=\"").append(attr(result.getStock().getMarket())).append("\" data-score=\"")
                        .append(format(result.getScore())).append("\" data-search=\"")
                        .append(attr(joinSearchText(result))).append("\"><td>").append(i + 1)
                        .append("</td><td class=\"stock-cell\"><strong>").append(html(result.getStock().getCode()))
                        .append(" ").append(html(result.getStock().getName())).append("</strong><span class=\"subline\">")
                        .append(html(emptyIfBlank(result.getIndustry(), "未分類"))).append("</span></td><td><span class=\"market-pill ")
                        .append(marketClass(result.getStock().getMarket())).append("\">")
                        .append(html(marketLabel(result.getStock().getMarket()))).append("</span></td><td>")
                        .append(renderScorePill(result.getScore())).append("</td><td>")
                        .append(deltaSpan(scoreDelta(series), "")).append("</td><td>")
                        .append(deltaSpan(priceDeltaPct(series), "%")).append("</td><td>")
                        .append(deltaSpan(volumeRatioDelta(series), "")).append("</td><td>")
                        .append(format(result.getCurrentPrice())).append("</td><td>")
                        .append(format(result.getRevenueScore())).append("</td><td>")
                        .append(format(result.getChipsScore())).append("</td><td>")
                        .append(format(result.getTechnicalScore())).append("</td><td>")
                        .append(format(result.getValuationScore())).append("</td><td>")
                        .append(format(result.getLiquidityScore())).append("</td><td>")
                        .append(format(result.getVolumeRatio())).append("</td><td><div class=\"notes\">")
                        .append(html(emptyIfBlank(result.getAnalysisNote(), "目前沒有分析摘要")))
                        .append("</div></td></tr>");
            }
            builder.append("</tbody></table></div>");
            builder.append("<div id=\"ranking-empty\" class=\"empty\" style=\"display:none;margin-top:14px;\">沒有符合目前篩選條件的股票，試著降低最低分數或改成全部市場。</div>");
        }
        builder.append("</section>");
    }

    private void appendFootnoteSection(StringBuilder builder) {
        builder.append("<section class=\"panel\">");
        builder.append("<div class=\"section-head\"><div><h2>如何閱讀這份頁面</h2><p class=\"hint\">先看收盤後四種名單，再看每日變化、likely 與爆量名單，最後用個股歷史追蹤確認是不是持續轉強。</p></div></div>");
        builder.append("<div class=\"footer-note\">交易面反應快，基本面反應慢。比較值得注意的通常不是單一分數，而是基本面沒有太差、交易與籌碼開始轉強、股價還沒有過度反應這種組合。收盤後高勝率名單偏嚴格，短線觀察偏強勢主線但不當作主攻買進依據，波段布局偏品質、買點、風報比與大盤狀態，催化觀察則是有題材但不建議追價。現在你每天跑出來的資料都會累積成歷史快照，所以可以開始觀察哪些股票是連續轉強，而不是只看單日結果。</div>");
        builder.append("</section>");
    }

    private String buildHistoryDataJson(HistoryBundle historyBundle) {
        StringBuilder builder = new StringBuilder(131072);
        builder.append("{");
        builder.append("\"latestDate\":\"")
                .append(json(historyBundle.currentDate.length() == 0 ? runDate : historyBundle.currentDate))
                .append("\",");
        builder.append("\"previousDate\":\"").append(json(historyBundle.previousDate)).append("\",");
        builder.append("\"stocks\":[");
        for (int i = 0; i < historyBundle.seriesList.size(); i++) {
            StockSeries series = historyBundle.seriesList.get(i);
            if (i > 0) {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"code\":\"").append(json(series.code)).append("\",");
            builder.append("\"name\":\"").append(json(series.name)).append("\",");
            builder.append("\"market\":\"").append(json(series.market)).append("\",");
            builder.append("\"marketLabel\":\"").append(json(marketLabel(series.market))).append("\",");
            builder.append("\"industry\":\"").append(json(series.industry)).append("\",");
            builder.append("\"history\":[");
            for (int j = 0; j < series.points.size(); j++) {
                HistoryPoint point = series.points.get(j);
                if (j > 0) {
                    builder.append(",");
                }
                builder.append("{");
                builder.append("\"date\":\"").append(json(point.date)).append("\",");
                builder.append("\"score\":").append(format(point.score)).append(",");
                builder.append("\"price\":").append(format(point.price)).append(",");
                builder.append("\"volumeRatio\":").append(format(point.volumeRatio)).append(",");
                builder.append("\"likely\":").append(point.likely ? "true" : "false");
                builder.append("}");
            }
            builder.append("]");
            builder.append("}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private String buildDashboardScript() {
        StringBuilder builder = new StringBuilder(24576);
        builder.append("(function(){");
        builder.append("var searchInput=document.getElementById('ranking-search');");
        builder.append("var minScoreInput=document.getElementById('ranking-min-score');");
        builder.append("var rows=[].slice.call(document.querySelectorAll('#ranking-table tbody tr'));");
        builder.append("var chips=[].slice.call(document.querySelectorAll('.chip-button'));");
        builder.append("var emptyState=document.getElementById('ranking-empty');");
        builder.append("var activeMarket='ALL';");
        builder.append("function applyFilters(){var keyword=(searchInput&&searchInput.value?searchInput.value:'').toLowerCase();var minScore=parseFloat(minScoreInput&&minScoreInput.value?minScoreInput.value:'0');var visibleCount=0;rows.forEach(function(row){var market=row.getAttribute('data-market');var score=parseFloat(row.getAttribute('data-score')||'0');var haystack=(row.getAttribute('data-search')||'').toLowerCase();var visible=haystack.indexOf(keyword)!==-1&&score>=minScore&&(activeMarket==='ALL'||market===activeMarket);row.style.display=visible?'':'none';if(visible){visibleCount+=1;}});if(emptyState){emptyState.style.display=visibleCount===0?'block':'none';}}");
        builder.append("chips.forEach(function(chip){chip.addEventListener('click',function(){chips.forEach(function(item){item.classList.remove('active');});chip.classList.add('active');activeMarket=chip.getAttribute('data-market');applyFilters();});});");
        builder.append("if(searchInput){searchInput.addEventListener('input',applyFilters);}if(minScoreInput){minScoreInput.addEventListener('input',applyFilters);}applyFilters();");
        builder.append("var historyData=window.STOCK_HISTORY_DATA||{stocks:[]};var historyInput=document.getElementById('history-stock-search');var historyEmpty=document.getElementById('history-empty');var historyView=document.getElementById('history-view');var historyTitle=document.getElementById('history-stock-title');var historySubtitle=document.getElementById('history-stock-subtitle');var historyMetrics=document.getElementById('history-stock-metrics');var historyTableBody=document.getElementById('history-stock-table-body');var trendLookback=document.getElementById('trend-lookback-days');var trendSummary=document.getElementById('trend-summary');var trendScoreRisers=document.getElementById('trend-score-risers');var trendScoreFallers=document.getElementById('trend-score-fallers');var trendPriceRisers=document.getElementById('trend-price-risers');var trendVolumeRisers=document.getElementById('trend-volume-risers');var stockList=historyData.stocks||[];var stockLookup={};stockList.forEach(function(stock){stockLookup[stock.code]=stock;stockLookup[stock.code+' '+stock.name]=stock;});");
        builder.append("function fmt(value,digits){var num=Number(value||0);var places=typeof digits==='number'?digits:2;return num.toFixed(places);}function fmtSigned(value,digits,suffix){var num=Number(value||0);var text=(num>0?'+':'')+fmt(num,digits);return text+(suffix||'');}function pctChange(currentValue,previousValue){var currentNum=Number(currentValue||0);var previousNum=Number(previousValue||0);if(!previousNum){return 0;}return (currentNum-previousNum)*100/previousNum;}function dateLabel(date){if(!date||date.length!==8){return date||'';}return date.substring(0,4)+'/'+date.substring(4,6)+'/'+date.substring(6,8);}function resolveStock(query){var text=(query||'').trim();if(!text){return null;}if(stockLookup[text]){return stockLookup[text];}var lower=text.toLowerCase();for(var i=0;i<stockList.length;i++){var stock=stockList[i];var haystack=(stock.code+' '+stock.name+' '+(stock.industry||'')).toLowerCase();if(haystack.indexOf(lower)!==-1){return stock;}}return null;}function metricHtml(label,value,note){return '<article class=\"metric-card\"><div class=\"metric-label\">'+label+'</div><div class=\"metric-value\">'+value+'</div><div class=\"subline\">'+note+'</div></article>';}function deltaClass(value){if(value>0){return 'delta-pos';}if(value<0){return 'delta-neg';}return 'delta-flat';}function deltaHtml(value,suffix){return '<span class=\"'+deltaClass(value)+'\">'+fmtSigned(value,2,suffix||'')+'</span>';}");
        builder.append("function miniRows(items,mode){if(!items.length){return '<div class=\"notes\">目前沒有足夠資料可比較。</div>';}var html='<table class=\"mini-table\">';for(var i=0;i<items.length&&i<12;i++){var item=items[i];html+='<tr><td><strong>'+item.stock.code+' '+item.stock.name+'</strong><div class=\"subline\">';if(mode==='score'){html+='分數 '+deltaHtml(item.scoreDelta,'')+' · 股價 '+deltaHtml(item.priceDeltaPct,'%');}else if(mode==='price'){html+='股價 '+deltaHtml(item.priceDeltaPct,'%')+' · 分數 '+deltaHtml(item.scoreDelta,'');}else if(mode==='volume'){html+='量比 '+deltaHtml(item.volumeDelta,'')+' · 分數 '+deltaHtml(item.scoreDelta,'');}html+='</div></td></tr>';}html+='</table>';return html;}");
        builder.append("function trendItems(lookback){var items=[];for(var i=0;i<stockList.length;i++){var stock=stockList[i];var history=stock.history||[];if(history.length<=lookback){continue;}var current=history[history.length-1];var previous=history[history.length-1-lookback];items.push({stock:stock,current:current,previous:previous,scoreDelta:current.score-previous.score,priceDeltaPct:pctChange(current.price,previous.price),volumeDelta:current.volumeRatio-previous.volumeRatio});}return items;}");
        builder.append("function average(items,key){if(!items.length){return 0;}var total=0;for(var i=0;i<items.length;i++){total+=Number(items[i][key]||0);}return total/items.length;}");
        builder.append("function renderTrendRankings(){if(!trendLookback){return;}var lookback=parseInt(trendLookback.value||'5',10);var items=trendItems(lookback);var scoreRisers=items.slice().filter(function(item){return item.scoreDelta>0;}).sort(function(a,b){return b.scoreDelta-a.scoreDelta;});var scoreFallers=items.slice().filter(function(item){return item.scoreDelta<0;}).sort(function(a,b){return a.scoreDelta-b.scoreDelta;});var priceRisers=items.slice().filter(function(item){return item.priceDeltaPct>0;}).sort(function(a,b){return b.priceDeltaPct-a.priceDeltaPct;});var volumeRisers=items.slice().filter(function(item){return item.volumeDelta>0;}).sort(function(a,b){return b.volumeDelta-a.volumeDelta;});if(trendSummary){trendSummary.innerHTML='';trendSummary.innerHTML+=metricHtml('比較視窗','近 '+lookback+' 日','以今天對照 '+lookback+' 個交易日前');trendSummary.innerHTML+=metricHtml('可比較股票',String(items.length),items.length>0?'目前足夠做近 '+lookback+' 日比較':'目前資料不足');trendSummary.innerHTML+=metricHtml('平均分數變化',fmtSigned(average(items,'scoreDelta'),2,''),items.length>0?'全部可比較股票平均值':'目前資料不足');trendSummary.innerHTML+=metricHtml('平均股價變化',fmtSigned(average(items,'priceDeltaPct'),2,'%'),items.length>0?'全部可比較股票平均值':'目前資料不足');trendSummary.innerHTML+=metricHtml('分數上升檔數',String(scoreRisers.length),items.length>0?'代表近 '+lookback+' 日偏強檔數':'目前資料不足');trendSummary.innerHTML+=metricHtml('分數下降檔數',String(scoreFallers.length),items.length>0?'代表近 '+lookback+' 日偏弱檔數':'目前資料不足');}if(trendScoreRisers){trendScoreRisers.innerHTML=miniRows(scoreRisers,'score');}if(trendScoreFallers){trendScoreFallers.innerHTML=miniRows(scoreFallers,'score');}if(trendPriceRisers){trendPriceRisers.innerHTML=miniRows(priceRisers,'price');}if(trendVolumeRisers){trendVolumeRisers.innerHTML=miniRows(volumeRisers,'volume');}}");
        builder.append("function renderMetrics(stock,current,previous){if(!historyMetrics){return;}var scoreDelta=previous?current.score-previous.score:0;var priceDelta=previous?pctChange(current.price,previous.price):0;var volumeDelta=previous?current.volumeRatio-previous.volumeRatio:0;historyMetrics.innerHTML='';historyMetrics.innerHTML+=metricHtml('最新總分',fmt(current.score,2),previous?'前次 '+fmt(previous.score,2)+'，日變 '+fmtSigned(scoreDelta,2,''):'目前只有一筆資料');historyMetrics.innerHTML+=metricHtml('最新股價',fmt(current.price,2),previous?'前次 '+fmt(previous.price,2)+'，日變 '+fmtSigned(priceDelta,2,'%'):'目前只有一筆資料');historyMetrics.innerHTML+=metricHtml('最新量比',fmt(current.volumeRatio,2),previous?'前次 '+fmt(previous.volumeRatio,2)+'，日變 '+fmtSigned(volumeDelta,2,''):'目前只有一筆資料');historyMetrics.innerHTML+=metricHtml('Likely 狀態',current.likely?'是':'否',previous?'前次 '+(previous.likely?'是':'否'):'目前只有一筆資料');historyMetrics.innerHTML+=metricHtml('歷史筆數',String(stock.history.length),stock.history.length>0?'從 '+dateLabel(stock.history[0].date)+' 開始累積':'');historyMetrics.innerHTML+=metricHtml('市場 / 產業',stock.marketLabel||stock.market||'',stock.industry||'未分類');}");
        builder.append("function drawChart(elementId,history,key,color){var element=document.getElementById(elementId);if(!element){return;}if(!history||!history.length){element.innerHTML='';return;}var width=640;var height=220;var padding=28;var values=history.map(function(item){return Number(item[key]||0);});var min=Math.min.apply(null,values);var max=Math.max.apply(null,values);if(min===max){min=min-1;max=max+1;}var chartHeight=height-padding*2;var chartWidth=width-padding*2;var stepX=history.length===1?0:chartWidth/(history.length-1);var points=[];for(var i=0;i<history.length;i++){var ratio=(values[i]-min)/(max-min);var x=padding+stepX*i;var y=height-padding-ratio*chartHeight;points.push(x.toFixed(1)+','+y.toFixed(1));}var firstDate=dateLabel(history[0].date);var lastDate=dateLabel(history[history.length-1].date);var svg='';svg+='<svg viewBox=\"0 0 '+width+' '+height+'\" preserveAspectRatio=\"none\">';svg+='<line x1=\"'+padding+'\" y1=\"'+(height-padding)+'\" x2=\"'+(width-padding)+'\" y2=\"'+(height-padding)+'\" stroke=\"rgba(95,76,54,.20)\" stroke-width=\"1\" />';svg+='<line x1=\"'+padding+'\" y1=\"'+padding+'\" x2=\"'+padding+'\" y2=\"'+(height-padding)+'\" stroke=\"rgba(95,76,54,.20)\" stroke-width=\"1\" />';svg+='<polyline fill=\"none\" stroke=\"'+color+'\" stroke-width=\"3\" points=\"'+points.join(' ')+'\" />';var lastPoint=points[points.length-1].split(',');svg+='<circle cx=\"'+lastPoint[0]+'\" cy=\"'+lastPoint[1]+'\" r=\"4\" fill=\"'+color+'\" />';svg+='<text x=\"'+padding+'\" y=\"'+(height-8)+'\" fill=\"#6c655c\" font-size=\"12\">'+firstDate+'</text>';svg+='<text x=\"'+(width-padding-74)+'\" y=\"'+(height-8)+'\" fill=\"#6c655c\" font-size=\"12\">'+lastDate+'</text>';svg+='<text x=\"'+padding+'\" y=\"16\" fill=\"#6c655c\" font-size=\"12\">'+fmt(max,2)+'</text>';svg+='<text x=\"'+padding+'\" y=\"'+(height-padding+18)+'\" fill=\"#6c655c\" font-size=\"12\">'+fmt(min,2)+'</text>';svg+='</svg>';element.innerHTML=svg;}");
        builder.append("function renderTable(stock){if(!historyTableBody){return;}var html='';for(var i=stock.history.length-1;i>=0&&i>=stock.history.length-").append(HISTORY_TABLE_LIMIT).append(";i--){var current=stock.history[i];var previous=i>0?stock.history[i-1]:null;var scoreDelta=previous?current.score-previous.score:0;var priceDelta=previous?pctChange(current.price,previous.price):0;var volumeDelta=previous?current.volumeRatio-previous.volumeRatio:0;html+='<tr><td>'+dateLabel(current.date)+'</td><td>'+fmt(current.score,2)+'</td><td>'+deltaHtml(scoreDelta,'')+'</td><td>'+fmt(current.price,2)+'</td><td>'+deltaHtml(priceDelta,'%')+'</td><td>'+fmt(current.volumeRatio,2)+'</td><td>'+deltaHtml(volumeDelta,'')+'</td><td>'+(current.likely?'是':'否')+'</td></tr>';}historyTableBody.innerHTML=html;}");
        builder.append("function renderHistory(stock){if(!stock){if(historyEmpty){historyEmpty.classList.remove('hidden');}if(historyView){historyView.classList.remove('visible');}return;}var history=stock.history||[];if(!history.length){if(historyEmpty){historyEmpty.classList.remove('hidden');historyEmpty.innerHTML='這檔股票目前沒有歷史資料。';}if(historyView){historyView.classList.remove('visible');}return;}var current=history[history.length-1];var previous=history.length>1?history[history.length-2]:null;if(historyEmpty){historyEmpty.classList.add('hidden');}if(historyView){historyView.classList.add('visible');}if(historyTitle){historyTitle.textContent=stock.code+' '+stock.name;}if(historySubtitle){historySubtitle.textContent=(stock.marketLabel||stock.market||'')+' · '+(stock.industry||'未分類')+' · 最新日期 '+dateLabel(current.date);}renderMetrics(stock,current,previous);drawChart('history-score-chart',history,'score','#ba5a31');drawChart('history-price-chart',history,'price','#1d5f74');drawChart('history-volume-chart',history,'volumeRatio','#317a46');renderTable(stock);}");
        builder.append("if(historyInput){historyInput.addEventListener('input',function(){renderHistory(resolveStock(historyInput.value));});}");
        builder.append("if(trendLookback){var availableLookbacks=[20,10,5,3,1];var selected=1;for(var i=0;i<availableLookbacks.length;i++){if((historyData.stocks||[]).some(function(stock){return (stock.history||[]).length>availableLookbacks[i];})){selected=availableLookbacks[i];break;}}trendLookback.value=String(selected);trendLookback.addEventListener('change',renderTrendRankings);}renderTrendRankings();");
        builder.append("if(stockList.length>0){var firstStock=stockList[0];if(historyInput){historyInput.value=firstStock.code+' '+firstStock.name;}renderHistory(firstStock);}else{renderHistory(null);}");
        builder.append("})();");
        return builder.toString();
    }

    private HistoryBundle buildHistoryBundle(List<StockAnalysisResultVO> currentResults) throws Exception {
        Map<String, SnapshotData> snapshotsByDate = new HashMap<String, SnapshotData>();
        loadHistorySnapshots(snapshotsByDate);

        SnapshotData currentSnapshot = null;
        String currentDate = "";
        if (currentResults != null && !currentResults.isEmpty()) {
            currentSnapshot = buildCurrentSnapshot(currentResults);
            currentDate = runDate;
            snapshotsByDate.put(currentDate, currentSnapshot);
        }

        List<String> dates = new ArrayList<String>(snapshotsByDate.keySet());
        Collections.sort(dates);

        if (currentDate.length() == 0 && !dates.isEmpty()) {
            currentDate = selectLatestComparableSnapshotDate(snapshotsByDate, dates);
            currentSnapshot = snapshotsByDate.get(currentDate);
        }

        if (currentSnapshot == null) {
            currentSnapshot = new SnapshotData();
            currentSnapshot.date = runDate;
            currentDate = runDate;
        }

        HistoryBundle bundle = new HistoryBundle();
        bundle.dates.addAll(dates);
        bundle.currentDate = currentDate;
        bundle.currentSnapshot = currentSnapshot;

        int currentIndex = dates.indexOf(currentDate);
        if (currentIndex > 0) {
            bundle.previousDate = dates.get(currentIndex - 1);
            bundle.previousSnapshot = snapshotsByDate.get(bundle.previousDate);
        }

        for (String date : dates) {
            SnapshotData snapshot = snapshotsByDate.get(date);
            if (snapshot == null) {
                continue;
            }
            for (HistoryPoint point : snapshot.rows) {
                StockSeries series = bundle.seriesByCode.get(point.code);
                if (series == null) {
                    series = new StockSeries();
                    series.code = point.code;
                    bundle.seriesByCode.put(point.code, series);
                    bundle.seriesList.add(series);
                }
                series.name = point.name;
                series.market = point.market;
                series.industry = point.industry;
                series.points.add(point);
                if (currentDate.equals(point.date)) {
                    series.current = point;
                }
                if (bundle.previousDate.equals(point.date)) {
                    series.previous = point;
                }
            }
        }

        Collections.sort(bundle.seriesList, new Comparator<StockSeries>() {
            public int compare(StockSeries left, StockSeries right) {
                double leftScore = left.current == null ? -1D : left.current.score;
                double rightScore = right.current == null ? -1D : right.current.score;
                int scoreCompare = Double.compare(rightScore, leftScore);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return left.code.compareTo(right.code);
            }
        });

        buildDailyChanges(bundle);
        return bundle;
    }

    private String selectLatestComparableSnapshotDate(Map<String, SnapshotData> snapshotsByDate, List<String> dates) {
        int maxRows = 0;
        for (String date : dates) {
            SnapshotData snapshot = snapshotsByDate.get(date);
            if (snapshot != null && snapshot.rows.size() > maxRows) {
                maxRows = snapshot.rows.size();
            }
        }

        int minimumRows = Math.max(COMPLETE_SNAPSHOT_MIN_ROWS,
                (int) Math.floor(maxRows * COMPLETE_SNAPSHOT_RATIO));
        for (int i = dates.size() - 1; i >= 0; i--) {
            String date = dates.get(i);
            SnapshotData snapshot = snapshotsByDate.get(date);
            if (snapshot != null && snapshot.rows.size() >= minimumRows) {
                return date;
            }
        }
        return dates.get(dates.size() - 1);
    }

    private void loadHistorySnapshots(Map<String, SnapshotData> snapshotsByDate) throws Exception {
        try {
            Map<String, StockHistoryDatabase.Snapshot> databaseSnapshots = historyDatabase.loadSnapshots();
            for (Map.Entry<String, StockHistoryDatabase.Snapshot> entry : databaseSnapshots.entrySet()) {
                snapshotsByDate.put(entry.getKey(), toSnapshotData(entry.getValue()));
            }
            if (!snapshotsByDate.isEmpty()) {
                return;
            }
        } catch (Exception ex) {
            loadHistorySnapshotsFromCsv(snapshotsByDate);
            return;
        }

        loadHistorySnapshotsFromCsv(snapshotsByDate);
    }

    private SnapshotData toSnapshotData(StockHistoryDatabase.Snapshot snapshot) {
        SnapshotData converted = new SnapshotData();
        converted.date = snapshot.date;
        for (StockHistoryDatabase.SnapshotRow row : snapshot.rows) {
            HistoryPoint point = new HistoryPoint();
            point.date = row.date.length() == 0 ? snapshot.date : row.date;
            point.code = row.code;
            point.name = row.name;
            point.market = row.market;
            point.industry = row.industry;
            point.note = row.note;
            point.score = row.score;
            point.rawScore = row.rawScore;
            point.selectionScore = row.selectionScore;
            point.momentumScore = row.momentumScore;
            point.qualityScore = row.qualityScore;
            point.selectionQualified = row.selectionQualified;
            point.price = row.price;
            point.volumeRatio = row.volumeRatio;
            point.return20DayPct = row.return20DayPct;
            point.liquidityScore = row.liquidityScore;
            point.revenueScore = row.revenueScore;
            point.chipsScore = row.chipsScore;
            point.valuationScore = row.valuationScore;
            point.technicalScore = row.technicalScore;
            point.financialQualityScore = row.financialQualityScore;
            point.fiveDayInstitutionalNetRatioPct = row.fiveDayInstitutionalNetRatioPct;
            point.brokerNetRatioPct = row.brokerNetRatioPct;
            point.scoreReason = row.scoreReason;
            point.revenueReason = row.revenueReason;
            point.chipsReason = row.chipsReason;
            point.liquidityReason = row.liquidityReason;
            point.valuationReason = row.valuationReason;
            point.technicalReason = row.technicalReason;
            point.financialQualityReason = row.financialQualityReason;
            point.eventRiskReason = row.eventRiskReason;
            point.eligibilityReason = row.eligibilityReason;
            point.relativeStrengthScore = row.relativeStrengthScore;
            point.industryReturnStrength = row.industryReturnStrength;
            point.industryVolumeStrength = row.industryVolumeStrength;
            point.industryFlowStrength = row.industryFlowStrength;
            point.eventDirection = row.eventDirection;
            point.eventConfidence = row.eventConfidence;
            point.eventFreshnessDays = row.eventFreshnessDays;
            point.eventTypeSummary = row.eventTypeSummary;
            point.signalType = row.signalType;
            point.signalHorizonDays = row.signalHorizonDays;
            point.entryRule = row.entryRule;
            point.exitRule = row.exitRule;
            point.validationMode = row.validationMode;
            point.hardExclude = row.hardExclude;
            point.hardExcludeReason = row.hardExcludeReason;
            point.dataQualityGrade = row.dataQualityGrade;
            point.coreConditionCount = row.coreConditionCount;
            point.winratePriorityScore = row.winratePriorityScore;
            point.expectedReturnScore = row.expectedReturnScore;
            point.maxDrawdownPenalty = row.maxDrawdownPenalty;
            point.backtestCohort = row.backtestCohort;
            point.postClosePriorityScore = row.postClosePriorityScore;
            point.postCloseCategory = row.postCloseCategory;
            point.postCloseAction = row.postCloseAction;
            point.postCloseReason = row.postCloseReason;
            point.newsSummary = row.newsSummary;
            point.newsDigest = row.newsDigest;
            point.newsSourceSummary = row.newsSourceSummary;
            point.latestNewsPublishedHint = row.latestNewsPublishedHint;
            point.newsSourceCredibilityScore = row.newsSourceCredibilityScore;
            point.newsFreshnessScore = row.newsFreshnessScore;
            point.newsSourceCount = row.newsSourceCount;
            point.newsOfficialSourceCount = row.newsOfficialSourceCount;
            point.newsMediaSourceCount = row.newsMediaSourceCount;
            point.companySummary = row.companySummary;
            point.recentNewsBrief = row.recentNewsBrief;
            point.transformationHint = row.transformationHint;
            point.practicalAdvice = row.practicalAdvice;
            point.adviceConfidence = row.adviceConfidence;
            point.likely = row.likely;
            converted.rows.add(point);
            converted.byCode.put(point.code, point);
        }
        return converted;
    }

    private void loadHistorySnapshotsFromCsv(Map<String, SnapshotData> snapshotsByDate) throws Exception {
        File historyDirectory = new File("history");
        if (!historyDirectory.exists() || !historyDirectory.isDirectory()) {
            return;
        }

        File[] files = historyDirectory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String date = snapshotDateFromFile(file.getName());
            if (date.length() == 0) {
                continue;
            }
            snapshotsByDate.put(date, readSnapshot(file, date));
        }
    }

    private String snapshotDateFromFile(String fileName) {
        String prefix = "stock_candidates_";
        String suffix = ".csv";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return "";
        }
        String date = fileName.substring(prefix.length(), fileName.length() - suffix.length());
        return date.matches("\\d{8}") ? date : "";
    }

    private SnapshotData readSnapshot(File file, String date) throws Exception {
        SnapshotData snapshot = new SnapshotData();
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

                HistoryPoint point = new HistoryPoint();
                point.date = date;
                point.code = valueAt(fields, indexes, "code");
                if (point.code.length() == 0) {
                    continue;
                }
                point.name = valueAt(fields, indexes, "name");
                point.market = valueAt(fields, indexes, "market");
                point.industry = valueAt(fields, indexes, "industry");
                point.note = valueAt(fields, indexes, "note");
            point.score = NumberParser.parseDouble(valueAt(fields, indexes, "score"));
            point.rawScore = NumberParser.parseDouble(valueAt(fields, indexes, "raw_score"));
            point.selectionScore = NumberParser.parseDouble(valueAt(fields, indexes, "selection_score"));
            point.momentumScore = NumberParser.parseDouble(valueAt(fields, indexes, "momentum_score"));
            point.qualityScore = NumberParser.parseDouble(valueAt(fields, indexes, "quality_score"));
            point.selectionQualified = "Y".equalsIgnoreCase(valueAt(fields, indexes, "selection_qualified"))
                    || "true".equalsIgnoreCase(valueAt(fields, indexes, "selection_qualified"));
            point.price = NumberParser.parseDouble(valueAt(fields, indexes, "current_price"));
            point.volumeRatio = NumberParser.parseDouble(valueAt(fields, indexes, "volume_ratio"));
                point.return20DayPct = NumberParser.parseDouble(valueAt(fields, indexes, "return_20d_pct"));
                point.liquidityScore = NumberParser.parseDouble(valueAt(fields, indexes, "liquidity_score"));
                point.revenueScore = NumberParser.parseDouble(valueAt(fields, indexes, "revenue_score"));
                point.chipsScore = NumberParser.parseDouble(valueAt(fields, indexes, "chips_score"));
                point.valuationScore = NumberParser.parseDouble(valueAt(fields, indexes, "valuation_score"));
                point.technicalScore = NumberParser.parseDouble(valueAt(fields, indexes, "technical_score"));
                point.financialQualityScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "financial_quality_score"));
                point.themeScore = NumberParser.parseDouble(valueAt(fields, indexes, "theme_score"));
                point.primaryTheme = valueAt(fields, indexes, "primary_theme");
                point.themeTags = valueAt(fields, indexes, "theme_tags");
                point.newsScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_score"));
                point.newsRiskScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_risk_score"));
                point.relativeStrengthScore = NumberParser.parseDouble(valueAt(fields, indexes, "relative_strength_score"));
                point.industryReturnStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_return_strength"));
                point.industryVolumeStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_volume_strength"));
                point.industryFlowStrength = NumberParser.parseDouble(valueAt(fields, indexes, "industry_flow_strength"));
                point.eventDirection = valueAt(fields, indexes, "event_direction");
                point.eventConfidence = NumberParser.parseDouble(valueAt(fields, indexes, "event_confidence"));
                point.eventFreshnessDays = (int) NumberParser.parseDouble(valueAt(fields, indexes, "event_freshness_days"));
                point.eventTypeSummary = valueAt(fields, indexes, "event_type_summary");
                point.newsSummary = valueAt(fields, indexes, "news_summary");
                point.newsDigest = valueAt(fields, indexes, "news_digest");
                point.newsSourceSummary = valueAt(fields, indexes, "news_source_summary");
                point.latestNewsPublishedHint = valueAt(fields, indexes, "latest_news_published_hint");
                point.newsSourceCredibilityScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_source_credibility_score"));
                point.newsFreshnessScore = NumberParser.parseDouble(valueAt(fields, indexes, "news_freshness_score"));
                point.newsSourceCount = (int) NumberParser.parseDouble(valueAt(fields, indexes, "news_source_count"));
                point.newsOfficialSourceCount = (int) NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_official_source_count"));
                point.newsMediaSourceCount = (int) NumberParser
                        .parseDouble(valueAt(fields, indexes, "news_media_source_count"));
                point.companySummary = valueAt(fields, indexes, "company_summary");
                point.recentNewsBrief = valueAt(fields, indexes, "recent_news_brief");
                point.transformationHint = valueAt(fields, indexes, "transformation_hint");
                point.practicalAdvice = valueAt(fields, indexes, "practical_advice");
                point.adviceConfidence = NumberParser.parseDouble(valueAt(fields, indexes, "advice_confidence"));
                point.signalType = valueAt(fields, indexes, "signal_type");
                point.signalHorizonDays = (int) NumberParser.parseDouble(valueAt(fields, indexes, "signal_horizon_days"));
                point.entryRule = valueAt(fields, indexes, "entry_rule");
                point.exitRule = valueAt(fields, indexes, "exit_rule");
                point.validationMode = valueAt(fields, indexes, "validation_mode");
                point.hardExclude = "Y".equalsIgnoreCase(valueAt(fields, indexes, "hard_exclude"))
                        || "true".equalsIgnoreCase(valueAt(fields, indexes, "hard_exclude"));
                point.hardExcludeReason = valueAt(fields, indexes, "hard_exclude_reason");
                point.dataQualityGrade = valueAt(fields, indexes, "data_quality_grade");
                point.coreConditionCount = (int) NumberParser.parseDouble(valueAt(fields, indexes, "core_count"));
                point.winratePriorityScore = NumberParser.parseDouble(valueAt(fields, indexes, "winrate_priority_score"));
                point.expectedReturnScore = NumberParser.parseDouble(valueAt(fields, indexes, "expected_return_score"));
                point.maxDrawdownPenalty = NumberParser.parseDouble(valueAt(fields, indexes, "max_drawdown_penalty"));
                point.backtestCohort = valueAt(fields, indexes, "backtest_cohort");
                if (point.newsDigest == null || point.newsDigest.length() == 0) {
                    point.newsDigest = point.newsSummary;
                }
                point.fiveDayInstitutionalNetRatioPct = NumberParser
                        .parseDouble(valueAt(fields, indexes, "five_day_institutional_net_ratio_pct"));
                point.brokerNetRatioPct = NumberParser.parseDouble(valueAt(fields, indexes, "broker_net_ratio_pct"));
                point.postClosePriorityScore = NumberParser
                        .parseDouble(valueAt(fields, indexes, "post_close_priority_score"));
                point.postCloseCategory = valueAt(fields, indexes, "post_close_category");
                point.postCloseAction = valueAt(fields, indexes, "post_close_action");
                point.postCloseReason = valueAt(fields, indexes, "post_close_reason");
                point.scoreReason = valueAt(fields, indexes, "score_reason");
                point.revenueReason = valueAt(fields, indexes, "revenue_reason");
                point.chipsReason = valueAt(fields, indexes, "chips_reason");
                point.liquidityReason = valueAt(fields, indexes, "liquidity_reason");
                point.valuationReason = valueAt(fields, indexes, "valuation_reason");
                point.technicalReason = valueAt(fields, indexes, "technical_reason");
            point.financialQualityReason = valueAt(fields, indexes, "financial_quality_reason");
            point.eventRiskReason = valueAt(fields, indexes, "event_risk_reason");
            point.eligibilityReason = valueAt(fields, indexes, "eligibility_reason");
            if (point.rawScore <= 0D) {
                point.rawScore = point.score;
            }
            if (point.selectionScore <= 0D) {
                point.selectionScore = point.score;
            }
            if (!point.selectionQualified) {
                point.selectionQualified = point.liquidityScore >= LIQUIDITY_GATE && point.financialQualityScore >= 8D;
            }
            point.likely = isLikely(point.selectionScore, point.liquidityScore, point.financialQualityScore,
                    point.volumeRatio, point.selectionQualified);

                snapshot.rows.add(point);
                snapshot.byCode.put(point.code, point);
            }
        } finally {
            reader.close();
        }
        return snapshot;
    }

    private SnapshotData buildCurrentSnapshot(List<StockAnalysisResultVO> currentResults) {
        SnapshotData snapshot = new SnapshotData();
        snapshot.date = runDate;
        for (StockAnalysisResultVO result : currentResults) {
            HistoryPoint point = new HistoryPoint();
            point.date = runDate;
            point.code = result.getStock().getCode();
            point.name = result.getStock().getName();
            point.market = result.getStock().getMarket();
            point.industry = result.getIndustry();
            point.note = result.getAnalysisNote();
            point.score = result.getScore();
            point.rawScore = result.getRawScore();
            point.selectionScore = result.getSelectionScore();
            point.momentumScore = result.getMomentumScore();
            point.qualityScore = result.getQualityScore();
            point.selectionQualified = result.isSelectionQualified();
            point.price = result.getCurrentPrice();
            point.volumeRatio = result.getVolumeRatio();
            point.return20DayPct = result.getReturn20DayPct();
            point.liquidityScore = result.getLiquidityScore();
            point.revenueScore = result.getRevenueScore();
            point.chipsScore = result.getChipsScore();
            point.valuationScore = result.getValuationScore();
            point.technicalScore = result.getTechnicalScore();
            point.financialQualityScore = result.getFinancialQualityScore();
            point.themeScore = result.getThemeScore();
            point.primaryTheme = result.getPrimaryTheme();
            point.themeTags = result.getThemeTags();
            point.newsScore = result.getNewsScore();
            point.newsRiskScore = result.getNewsRiskScore();
            point.relativeStrengthScore = result.getRelativeStrengthScore();
            point.industryReturnStrength = result.getIndustryReturnStrength();
            point.industryVolumeStrength = result.getIndustryVolumeStrength();
            point.industryFlowStrength = result.getIndustryFlowStrength();
            point.eventDirection = result.getEventDirection();
            point.eventConfidence = result.getEventConfidence();
            point.eventFreshnessDays = result.getEventFreshnessDays();
            point.eventTypeSummary = result.getEventTypeSummary();
            point.newsSummary = result.getNewsSummary();
            point.newsDigest = result.getNewsDigest();
            point.newsSourceSummary = result.getNewsSourceSummary();
            point.latestNewsPublishedHint = result.getLatestNewsPublishedHint();
            point.newsSourceCredibilityScore = result.getNewsSourceCredibilityScore();
            point.newsFreshnessScore = result.getNewsFreshnessScore();
            point.newsSourceCount = result.getNewsSourceCount();
            point.newsOfficialSourceCount = result.getNewsOfficialSourceCount();
            point.newsMediaSourceCount = result.getNewsMediaSourceCount();
            point.companySummary = result.getCompanySummary();
            point.recentNewsBrief = result.getRecentNewsBrief();
            point.transformationHint = result.getTransformationHint();
            point.practicalAdvice = result.getPracticalAdvice();
            point.adviceConfidence = result.getAdviceConfidence();
            point.signalType = result.getSignalType();
            point.signalHorizonDays = result.getSignalHorizonDays();
            point.entryRule = result.getEntryRule();
            point.exitRule = result.getExitRule();
            point.validationMode = result.getValidationMode();
            point.hardExclude = result.isHardExclude();
            point.hardExcludeReason = result.getHardExcludeReason();
            point.dataQualityGrade = result.getDataQualityGrade();
            point.coreConditionCount = result.getCoreConditionCount();
            point.winratePriorityScore = result.getWinratePriorityScore();
            point.expectedReturnScore = result.getExpectedReturnScore();
            point.maxDrawdownPenalty = result.getMaxDrawdownPenalty();
            point.backtestCohort = result.getBacktestCohort();
            point.fiveDayInstitutionalNetRatioPct = result.getFiveDayInstitutionalNetRatioPct();
            point.brokerNetRatioPct = result.getBrokerNetRatioPct();
            point.postClosePriorityScore = result.getPostClosePriorityScore();
            point.postCloseCategory = result.getPostCloseCategory();
            point.postCloseAction = result.getPostCloseAction();
            point.postCloseReason = result.getPostCloseReason();
            point.scoreReason = result.getScoreReason();
            point.revenueReason = result.getRevenueReason();
            point.chipsReason = result.getChipsReason();
            point.liquidityReason = result.getLiquidityReason();
            point.valuationReason = result.getValuationReason();
            point.technicalReason = result.getTechnicalReason();
            point.financialQualityReason = result.getFinancialQualityReason();
            point.eventRiskReason = result.getEventRiskReason();
            point.eligibilityReason = result.getEligibilityReason();
            point.likely = isLikely(point.selectionScore, point.liquidityScore, point.financialQualityScore,
                    point.volumeRatio, point.selectionQualified);
            snapshot.rows.add(point);
            snapshot.byCode.put(point.code, point);
        }
        return snapshot;
    }

    private void buildDailyChanges(HistoryBundle bundle) {
        double totalScoreDelta = 0D;

        for (StockSeries series : bundle.seriesList) {
            if (series.current == null || series.previous == null) {
                continue;
            }

            DailyChange change = new DailyChange();
            change.series = series;
            change.current = series.current;
            change.previous = series.previous;
            change.scoreDelta = scoreDelta(series);
            change.priceDeltaPct = priceDeltaPct(series);
            change.volumeRatioDelta = volumeRatioDelta(series);

            bundle.comparableCount++;
            totalScoreDelta += change.scoreDelta;

            if (change.scoreDelta > 0D) {
                bundle.scoreRisers.add(change);
            } else if (change.scoreDelta < 0D) {
                bundle.scoreFallers.add(change);
            }
            if (change.priceDeltaPct > 0D) {
                bundle.priceRisers.add(change);
            }
            if (change.volumeRatioDelta > 0D) {
                bundle.volumeRisers.add(change);
            }
            if (change.current.likely && !change.previous.likely) {
                bundle.newLikely.add(change);
            }
            if (!change.current.likely && change.previous.likely) {
                bundle.droppedLikely.add(change);
            }
        }

        bundle.averageScoreDelta = bundle.comparableCount == 0 ? 0D : totalScoreDelta / bundle.comparableCount;

        Collections.sort(bundle.scoreRisers, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(right.scoreDelta, left.scoreDelta);
            }
        });
        Collections.sort(bundle.scoreFallers, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(left.scoreDelta, right.scoreDelta);
            }
        });
        Collections.sort(bundle.priceRisers, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(right.priceDeltaPct, left.priceDeltaPct);
            }
        });
        Collections.sort(bundle.volumeRisers, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(right.volumeRatioDelta, left.volumeRatioDelta);
            }
        });
        Collections.sort(bundle.newLikely, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(right.current.score, left.current.score);
            }
        });
        Collections.sort(bundle.droppedLikely, new Comparator<DailyChange>() {
            public int compare(DailyChange left, DailyChange right) {
                return Double.compare(right.previous.score, left.previous.score);
            }
        });
    }

    private void metric(StringBuilder builder, String label, String value, String note) {
        builder.append("<article class=\"metric-card\"><div class=\"metric-label\">").append(html(label))
                .append("</div><div class=\"metric-value\">").append(html(value)).append("</div><div class=\"subline\">")
                .append(html(note)).append("</div></article>");
    }

    private void pill(StringBuilder builder, String value) {
        builder.append("<span class=\"meta-pill\">").append(html(value)).append("</span>");
    }

    private String renderScorePill(double score) {
        String style = "neutral";
        if (score >= likelyThreshold) {
            style = "strong";
        } else if (score >= watchlistThreshold) {
            style = "watch";
        }
        return "<span class=\"score-pill " + style + "\">" + format(score) + "</span>";
    }

    private String deltaSpan(double value, String suffix) {
        String style = "delta-flat";
        if (value > 0D) {
            style = "delta-pos";
        } else if (value < 0D) {
            style = "delta-neg";
        }
        return "<span class=\"" + style + "\">" + signed(value) + html(suffix) + "</span>";
    }

    private String signed(double value) {
        if (value > 0D) {
            return "+" + format(value);
        }
        return format(value);
    }

    private void appendChangeCard(StringBuilder builder, String title, String note, List<DailyChange> changes,
            ChangeMode changeMode) {
        builder.append("<article class=\"change-card\"><h3>").append(html(title)).append("</h3><div class=\"change-note\">")
                .append(html(note)).append("</div>");
        if (changes.isEmpty()) {
            builder.append("<div class=\"notes\">目前沒有符合條件的股票。</div>");
        } else {
            builder.append("<table class=\"mini-table\">");
            int limit = Math.min(CHANGE_LIMIT, changes.size());
            for (int i = 0; i < limit; i++) {
                DailyChange change = changes.get(i);
                builder.append("<tr><td><strong>").append(html(change.series.code)).append(" ")
                        .append(html(change.series.name)).append("</strong><div class=\"subline\">");
                if (changeMode == ChangeMode.SCORE) {
                    builder.append("分數 ").append(deltaSpan(change.scoreDelta, "")).append(" · 股價 ")
                            .append(deltaSpan(change.priceDeltaPct, "%"));
                } else if (changeMode == ChangeMode.PRICE) {
                    builder.append("股價 ").append(deltaSpan(change.priceDeltaPct, "%")).append(" · 分數 ")
                            .append(deltaSpan(change.scoreDelta, ""));
                } else if (changeMode == ChangeMode.VOLUME) {
                    builder.append("量比 ").append(deltaSpan(change.volumeRatioDelta, "")).append(" · 分數 ")
                            .append(deltaSpan(change.scoreDelta, ""));
                } else {
                    builder.append("今天 ").append(format(change.current.score)).append(" / 前次 ")
                            .append(format(change.previous.score)).append(" · 分數日變 ")
                            .append(deltaSpan(change.scoreDelta, ""));
                }
                builder.append("</div></td></tr>");
            }
            builder.append("</table>");
        }
        builder.append("</article>");
    }

    private void appendReasons(StringBuilder builder, StockAnalysisResultVO result) {
        builder.append("<details><summary>查看拆解</summary><div class=\"reason-grid\">");
        appendReasonItem(builder, "訊號", buildSignalReason(result));
        appendReasonItem(builder, "資料品質", emptyIfBlank(buildDataQualityReason(result), "目前沒有資料品質說明"));
        appendReasonItem(builder, "回測排序", emptyIfBlank(buildBacktestReason(result), "目前沒有回測排序說明"));
        appendReasonItem(builder, "相對強勢", emptyIfBlank(buildRelativeStrengthReason(result), "目前沒有相對強弱說明"));
        appendReasonItem(builder, "事件方向", emptyIfBlank(buildEventDirectionReason(result), "目前沒有事件方向說明"));
        appendReasonItem(builder, "總分", emptyIfBlank(result.getScoreReason(), "目前沒有總分拆解"));
        appendReasonItem(builder, "營收", emptyIfBlank(result.getRevenueReason(), "目前沒有營收說明"));
        appendReasonItem(builder, "籌碼", emptyIfBlank(result.getChipsReason(), "目前沒有籌碼說明"));
        appendReasonItem(builder, "流動性", emptyIfBlank(result.getLiquidityReason(), "目前沒有流動性說明"));
        appendReasonItem(builder, "估值", emptyIfBlank(result.getValuationReason(), "目前沒有估值說明"));
        appendReasonItem(builder, "技術", emptyIfBlank(result.getTechnicalReason(), "目前沒有技術面說明"));
        appendReasonItem(builder, "財報品質", emptyIfBlank(result.getFinancialQualityReason(), "目前沒有財報品質說明"));
        appendReasonItem(builder, "事件風險", emptyIfBlank(result.getEventRiskReason(), "目前沒有事件風險說明"));
        appendReasonItem(builder, "題材 / 新聞", emptyIfBlank(buildThemeNewsReason(result), "目前沒有題材或新聞說明"));
        builder.append("</div></details>");
    }

    private void appendReasonItem(StringBuilder builder, String label, String value) {
        builder.append("<div class=\"reason-item\"><strong>").append(html(label)).append("</strong>")
                .append(html(value)).append("</div>");
    }

    private void appendEmpty(StringBuilder builder, String message) {
        builder.append("<div class=\"empty\">").append(html(message)).append("</div>");
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
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
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

    private boolean isLikely(double selectionScore, double liquidityScore, double financialQualityScore,
            double volumeRatio, boolean selectionQualified) {
        return selectionScore >= likelyThreshold && selectionQualified && liquidityScore >= LIQUIDITY_GATE
                && financialQualityScore >= 14D && volumeRatio >= 0.8D && volumeRatio <= 2.5D;
    }

    private boolean isSelectionQualified(StockAnalysisResultVO result) {
        return result.isSelectionQualified();
    }

    private boolean isLikelyCandidate(StockAnalysisResultVO result) {
        return isLikely(result.getSelectionScore(), result.getLiquidityScore(), result.getFinancialQualityScore(),
                result.getVolumeRatio(), result.isSelectionQualified())
                && (result.getCoreConditionCount() == 0 || result.getCoreConditionCount() >= 8);
    }

    private boolean hasVolumeSurge(StockAnalysisResultVO result) {
        return result.getVolumeRatio() >= volumeSurgeThreshold;
    }

    private String yahooSuffix(String market) {
        if ("TWSE".equalsIgnoreCase(market)) {
            return ".TW";
        }
        if ("TPEX".equalsIgnoreCase(market)) {
            return ".TWO";
        }
        return "";
    }

    private String joinSearchText(StockAnalysisResultVO result) {
        StringBuilder builder = new StringBuilder();
        appendSearchPart(builder, result.getStock().getCode());
        appendSearchPart(builder, result.getStock().getName());
        appendSearchPart(builder, result.getStock().getMarket());
        appendSearchPart(builder, marketLabel(result.getStock().getMarket()));
        appendSearchPart(builder, result.getIndustry());
        appendSearchPart(builder, result.getAnalysisNote());
        appendSearchPart(builder, result.getScoreReason());
        appendSearchPart(builder, result.getPrimaryTheme());
        appendSearchPart(builder, result.getThemeTags());
        appendSearchPart(builder, result.getNewsSummary());
        appendSearchPart(builder, result.getNewsDigest());
        appendSearchPart(builder, result.getNewsSourceSummary());
        appendSearchPart(builder, result.getLatestNewsPublishedHint());
        appendSearchPart(builder, result.getSignalType());
        appendSearchPart(builder, result.getEntryRule());
        appendSearchPart(builder, result.getExitRule());
        appendSearchPart(builder, result.getValidationMode());
        appendSearchPart(builder, result.getDataQualityGrade());
        appendSearchPart(builder, result.getHardExcludeReason());
        appendSearchPart(builder, result.getBacktestCohort());
        appendSearchPart(builder, result.getEventDirection());
        appendSearchPart(builder, result.getEventTypeSummary());
        appendSearchPart(builder, result.getPostCloseCategory());
        appendSearchPart(builder, result.getPostCloseAction());
        appendSearchPart(builder, result.getPostCloseReason());
        appendSearchPart(builder, result.getTechnicalReason());
        appendSearchPart(builder, result.getChipsReason());
        return builder.toString();
    }

    private String buildSignalReason(StockAnalysisResultVO result) {
        StringBuilder builder = new StringBuilder();
        builder.append(emptyIfBlank(result.getSignalType(), "待確認"));
        if (result.getSignalHorizonDays() > 0) {
            builder.append(" / ").append(result.getSignalHorizonDays()).append(" 日");
        }
        if (result.getEntryRule() != null && result.getEntryRule().length() > 0) {
            builder.append(" / 進場 ").append(result.getEntryRule());
        }
        if (result.getExitRule() != null && result.getExitRule().length() > 0) {
            builder.append(" / 出場 ").append(result.getExitRule());
        }
        if (result.getValidationMode() != null && result.getValidationMode().length() > 0) {
            builder.append(" / 驗證 ").append(result.getValidationMode());
        }
        return builder.toString();
    }

    private String buildDataQualityReason(StockAnalysisResultVO result) {
        StringBuilder builder = new StringBuilder();
        builder.append("資料品質 ").append(emptyIfBlank(result.getDataQualityGrade(), "未標記"));
        if (result.getDataConfidenceReason() != null && result.getDataConfidenceReason().length() > 0) {
            builder.append(" / ").append(result.getDataConfidenceReason());
        }
        if (result.isHardExclude()) {
            builder.append(" / 已降級：").append(emptyIfBlank(result.getHardExcludeReason(), "原因未提供"));
        }
        return builder.toString();
    }

    private String buildBacktestReason(StockAnalysisResultVO result) {
        if (result.getBacktestCohort() == null || result.getBacktestCohort().length() == 0
                || "N/A".equals(result.getBacktestCohort())) {
            return "";
        }
        return "cohort " + result.getBacktestCohort() + " / 勝率分 "
                + format(result.getWinratePriorityScore()) + " / 報酬分 "
                + format(result.getExpectedReturnScore()) + " / 回撤懲罰 "
                + format(result.getMaxDrawdownPenalty());
    }

    private String buildRelativeStrengthReason(StockAnalysisResultVO result) {
        if (result.getRelativeStrengthScore() <= 0D) {
            return "";
        }
        return "總分 " + format(result.getRelativeStrengthScore()) + " / 報酬 "
                + format(result.getIndustryReturnStrength()) + " / 量比 "
                + format(result.getIndustryVolumeStrength()) + " / 法人 "
                + format(result.getIndustryFlowStrength());
    }

    private String buildEventDirectionReason(StockAnalysisResultVO result) {
        if (result.getEventDirection() == null || result.getEventDirection().length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(result.getEventDirection()).append(" / 信心 ").append(format(result.getEventConfidence()));
        if (result.getEventFreshnessDays() < 999) {
            builder.append(" / ").append(result.getEventFreshnessDays()).append(" 天");
        }
        if (result.getEventTypeSummary() != null && result.getEventTypeSummary().length() > 0) {
            builder.append(" / ").append(result.getEventTypeSummary());
        }
        return builder.toString();
    }

    private void appendSearchPart(StringBuilder builder, String value) {
        if (value == null || value.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private int countByMarket(List<StockAnalysisResultVO> results, String market) {
        int count = 0;
        for (StockAnalysisResultVO result : results) {
            if (market.equalsIgnoreCase(result.getStock().getMarket())) {
                count++;
            }
        }
        return count;
    }

    private double percent(int value, int total) {
        if (total <= 0) {
            return 0D;
        }
        return value * 100D / total;
    }

    private double scoreDelta(StockSeries series) {
        if (series == null || series.current == null || series.previous == null) {
            return 0D;
        }
        return series.current.score - series.previous.score;
    }

    private double priceDeltaPct(StockSeries series) {
        if (series == null || series.current == null || series.previous == null || series.previous.price == 0D) {
            return 0D;
        }
        return (series.current.price - series.previous.price) * 100D / series.previous.price;
    }

    private double volumeRatioDelta(StockSeries series) {
        if (series == null || series.current == null || series.previous == null) {
            return 0D;
        }
        return series.current.volumeRatio - series.previous.volumeRatio;
    }

    private String marketLabel(String market) {
        if ("TWSE".equalsIgnoreCase(market)) {
            return "上市";
        }
        if ("TPEX".equalsIgnoreCase(market)) {
            return "上櫃";
        }
        return market == null ? "" : market;
    }

    private String marketClass(String market) {
        if ("TWSE".equalsIgnoreCase(market)) {
            return "twse";
        }
        if ("TPEX".equalsIgnoreCase(market)) {
            return "tpex";
        }
        return "neutral";
    }

    private String emptyIfBlank(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value;
    }

    private String buildThemeNewsReason(StockAnalysisResultVO result) {
        StringBuilder builder = new StringBuilder();
        if (result.getPrimaryTheme() != null && result.getPrimaryTheme().trim().length() > 0) {
            builder.append("題材 ").append(result.getPrimaryTheme());
            if (result.getThemeScore() > 0D) {
                builder.append(" ").append(format(result.getThemeScore())).append(" 分");
            }
        }
        if (result.getThemeTags() != null && result.getThemeTags().trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("關聯 ").append(result.getThemeTags());
        }
        if (result.getNewsScore() > 0D || result.getNewsRiskScore() > 0D) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("新聞 ").append(format(result.getNewsScore())).append(" 分 / 風險 ")
                    .append(format(result.getNewsRiskScore())).append(" 分");
        }
        if (result.getNewsSourceSummary() != null && result.getNewsSourceSummary().trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("來源 ").append(result.getNewsSourceSummary()).append(" / 最新 ")
                    .append(emptyIfBlank(result.getLatestNewsPublishedHint(), "未標示"));
        }
        if (result.getNewsFreshnessScore() > 0D || result.getNewsSourceCredibilityScore() > 0D) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append("新鮮度 ").append(format(result.getNewsFreshnessScore())).append(" / 可信度 ")
                    .append(format(result.getNewsSourceCredibilityScore()));
        }
        if (result.getNewsSummary() != null && result.getNewsSummary().trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append(emptyIfBlank(result.getNewsDigest(), result.getNewsSummary()));
        }
        return builder.toString();
    }

    private String format(double value) {
        return String.format("%.2f", Double.valueOf(value));
    }

    private String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String attr(String value) {
        if (value == null) {
            return "";
        }
        return html(value).replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static class SnapshotData {
        private String date;
        private List<HistoryPoint> rows = new ArrayList<HistoryPoint>();
        private Map<String, HistoryPoint> byCode = new HashMap<String, HistoryPoint>();
    }

    private static class HistoryPoint {
        private String date;
        private String code;
        private String name;
        private String market;
        private String industry;
        private String note;
        private double score;
        private double rawScore;
        private double selectionScore;
        private double momentumScore;
        private double qualityScore;
        private boolean selectionQualified;
        private double price;
        private double volumeRatio;
        private double return20DayPct;
        private double liquidityScore;
        private double revenueScore;
        private double chipsScore;
        private double valuationScore;
        private double technicalScore;
        private double financialQualityScore;
        private double themeScore;
        private String primaryTheme;
        private String themeTags;
        private double newsScore;
        private double newsRiskScore;
        private double relativeStrengthScore;
        private double industryReturnStrength;
        private double industryVolumeStrength;
        private double industryFlowStrength;
        private String eventDirection;
        private double eventConfidence;
        private int eventFreshnessDays;
        private String eventTypeSummary;
        private String newsSummary;
        private String newsDigest;
        private String newsSourceSummary;
        private String latestNewsPublishedHint;
        private double newsSourceCredibilityScore;
        private double newsFreshnessScore;
        private int newsSourceCount;
        private int newsOfficialSourceCount;
        private int newsMediaSourceCount;
        private String companySummary;
        private String recentNewsBrief;
        private String transformationHint;
        private String practicalAdvice;
        private double adviceConfidence;
        private String signalType;
        private int signalHorizonDays;
        private String entryRule;
        private String exitRule;
        private String validationMode;
        private boolean hardExclude;
        private String hardExcludeReason;
        private String dataQualityGrade;
        private int coreConditionCount;
        private double winratePriorityScore;
        private double expectedReturnScore;
        private double maxDrawdownPenalty;
        private String backtestCohort;
        private double fiveDayInstitutionalNetRatioPct;
        private double brokerNetRatioPct;
        private double postClosePriorityScore;
        private String postCloseCategory;
        private String postCloseAction;
        private String postCloseReason;
        private String scoreReason;
        private String revenueReason;
        private String chipsReason;
        private String liquidityReason;
        private String valuationReason;
        private String technicalReason;
        private String financialQualityReason;
        private String eventRiskReason;
        private String eligibilityReason;
        private boolean likely;
    }

    private static class StockSeries {
        private String code;
        private String name;
        private String market;
        private String industry;
        private List<HistoryPoint> points = new ArrayList<HistoryPoint>();
        private HistoryPoint current;
        private HistoryPoint previous;
    }

    private static class DailyChange {
        private StockSeries series;
        private HistoryPoint current;
        private HistoryPoint previous;
        private double scoreDelta;
        private double priceDeltaPct;
        private double volumeRatioDelta;
    }

    private static class HistoryBundle {
        private String currentDate;
        private String previousDate = "";
        private SnapshotData currentSnapshot;
        private SnapshotData previousSnapshot;
        private List<String> dates = new ArrayList<String>();
        private Map<String, StockSeries> seriesByCode = new HashMap<String, StockSeries>();
        private List<StockSeries> seriesList = new ArrayList<StockSeries>();
        private List<DailyChange> scoreRisers = new ArrayList<DailyChange>();
        private List<DailyChange> scoreFallers = new ArrayList<DailyChange>();
        private List<DailyChange> priceRisers = new ArrayList<DailyChange>();
        private List<DailyChange> volumeRisers = new ArrayList<DailyChange>();
        private List<DailyChange> newLikely = new ArrayList<DailyChange>();
        private List<DailyChange> droppedLikely = new ArrayList<DailyChange>();
        private int comparableCount;
        private double averageScoreDelta;
    }

    private static class DashboardData {
        private List<StockAnalysisResultVO> results = Collections.emptyList();
        private List<StockAnalysisResultVO> likelyCandidates = Collections.emptyList();
        private List<StockAnalysisResultVO> watchlistCandidates = Collections.emptyList();
        private List<StockAnalysisResultVO> likelyVolumeSurgeCandidates = Collections.emptyList();
        private List<StockAnalysisResultVO> nonLikelyVolumeSurgeCandidates = Collections.emptyList();
    }

    private static enum ChangeMode {
        SCORE, PRICE, VOLUME, STATUS
    }
}
