package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;
import stock.vo.StockAnalysisResultVO;

public class StockApiRenderer {

    private static final double LIKELY_THRESHOLD = 72D;
    private static final double WATCHLIST_THRESHOLD = 58D;
    private static final double MIN_LIKELY_FINANCIAL_SCORE = 12D;
    private static final double LIKELY_MIN_VOLUME_RATIO = 0.8D;
    private static final double LIKELY_MAX_VOLUME_RATIO = 2.5D;

    @SuppressWarnings("unchecked")
    public String renderLatestJson() throws Exception {
        StockHistoryDatabase db = new StockHistoryDatabase();
        Map<String, Snapshot> snapshots = db.loadSnapshots();

        if (snapshots.isEmpty()) {
            return "{\"date\":\"\",\"prevDate\":\"\",\"count\":0,\"likelyCount\":0,\"watchlistCount\":0,\"rows\":[],"
                 + "\"scoreUpCount\":0,\"scoreUpPct\":0,\"volumeSurgeCount\":0,\"marketScore\":50,"
                 + "\"marketSignal\":\"中性\",\"marketSignalText\":\"資料不足\",\"marketDangerScore\":50,"
                 + "\"marketAlert\":\"中性\",\"marketAlertText\":\"資料不足\",\"themes\":[]}";
        }

        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        String latestDate = dates.get(dates.size() - 1);
        Snapshot snapshot = snapshots.get(latestDate);

        String prevDate = dates.size() >= 2 ? dates.get(dates.size() - 2) : null;
        Map<String, SnapshotRow> prevRows = new HashMap<String, SnapshotRow>();
        if (prevDate != null) {
            for (SnapshotRow row : snapshots.get(prevDate).rows) {
                prevRows.put(row.code, row);
            }
        }

        Map<String, Map<String, Double>> scoreLookup = buildScoreLookup(snapshots, dates);
        Map<String, Integer> consecutiveDays = computeConsecutiveDays(scoreLookup, dates, snapshot);
        int prevLikelyCount = 0;
        double prevSelectionScoreSum = 0D;
        int prevTotal = 0;
        if (prevDate != null) {
            Snapshot prevSnapshot = snapshots.get(prevDate);
            if (prevSnapshot != null) {
                prevTotal = prevSnapshot.rows.size();
                for (SnapshotRow row : prevSnapshot.rows) {
                    if (isLikely(row)) {
                        prevLikelyCount++;
                    }
                    prevSelectionScoreSum += selectionScoreOf(row);
                }
            }
        }

        // 統計資訊
        int likelyCount = 0, watchlistCount = 0, scoreUpCount = 0, volumeSurgeCount = 0, qualifiedCount = 0,
                buyPointCount = 0, confidenceReadyCount = 0, newsHotCount = 0, newsRiskCount = 0, themeHotCount = 0;
        double selectionScoreSum = 0D;
        double confidenceSum = 0D;
        double newsScoreSum = 0D;
        double themeScoreSum = 0D;
        for (SnapshotRow row : snapshot.rows) {
            if (isLikely(row)) likelyCount++;
            else if (selectionScoreOf(row) >= WATCHLIST_THRESHOLD) watchlistCount++;
            if (row.selectionQualified) qualifiedCount++;
            if (buyPointScoreOf(row) >= 75D) buyPointCount++;
            if (row.volumeRatio >= 1.8) volumeSurgeCount++;
            if (row.newsScore >= 60D) newsHotCount++;
            if (row.newsRiskScore >= 65D) newsRiskCount++;
            if (row.themeScore >= 60D) themeHotCount++;
            SnapshotRow prev = prevRows.get(row.code);
            if (prev != null && selectionScoreOf(row) > selectionScoreOf(prev)) scoreUpCount++;
            selectionScoreSum += selectionScoreOf(row);
            newsScoreSum += row.newsScore;
            themeScoreSum += row.themeScore;
            if (hasDataConfidence(row)) {
                confidenceSum += row.dataConfidence;
                confidenceReadyCount++;
            }
        }
        int total = snapshot.rows.size();
        double scoreUpPct = total > 0 ? scoreUpCount * 100.0 / total : 0;
        double volumeSurgePct = total > 0 ? volumeSurgeCount * 100.0 / total : 0;
        double qualifiedPct = total > 0 ? qualifiedCount * 100.0 / total : 0;
        double buyPointPct = total > 0 ? buyPointCount * 100.0 / total : 0;
        double breadthPct = total > 0 ? (likelyCount + watchlistCount) * 100.0 / total : 0;
        double averageSelectionScore = total > 0 ? selectionScoreSum / total : 0;
        double averageNewsScore = total > 0 ? newsScoreSum / total : 0;
        double averageThemeScore = total > 0 ? themeScoreSum / total : 0;
        double averageConfidence = confidenceReadyCount > 0 ? confidenceSum / confidenceReadyCount : 0;
        double prevAverageSelectionScore = prevTotal > 0 ? prevSelectionScoreSum / prevTotal : averageSelectionScore;
        double marketScore = computeMarketScore(scoreUpPct, breadthPct, qualifiedPct, buyPointPct,
                averageSelectionScore);
        double marketDangerScore = computeMarketDangerScore(marketScore, averageSelectionScore, prevAverageSelectionScore,
                likelyCount, prevLikelyCount, total > 0 ? newsRiskCount * 100.0 / total : 0,
                total > 0 ? themeHotCount * 100.0 / total : 0, volumeSurgePct);
        String marketAlert = resolveMarketAlert(marketDangerScore);
        String marketAlertText = buildMarketAlertText(marketAlert, marketDangerScore, newsRiskCount, likelyCount,
                prevLikelyCount, averageSelectionScore, prevAverageSelectionScore);
        JSONArray themes = buildThemeHeatJson(snapshot.rows, prevRows);

        // 市場動能訊號（基於本系統多因子評分）
        String marketSignal;
        String marketSignalText;
        if (marketScore >= 65D) {
            marketSignal = "偏強";
            marketSignalText = String.format("市場環境偏強，分數上升 %.0f%%、可交易標的 %.0f%%，買點可積極挑選", scoreUpPct,
                    qualifiedPct);
        } else if (marketScore >= 55D) {
            marketSignal = "中性";
            marketSignalText = String.format("市場中性偏多，分數上升 %.0f%%，但仍需嚴格挑買點", scoreUpPct);
        } else if (marketScore >= 45D) {
            marketSignal = "偏弱";
            marketSignalText = String.format("市場偏弱，買點數量有限（%.0f%%），建議縮小部位", buyPointPct);
        } else {
            marketSignal = "警戒";
            marketSignalText = String.format("市場警戒，分數上升 %.0f%%、可交易標的 %.0f%%，應偏保守", scoreUpPct, qualifiedPct);
        }

        JSONArray rows = new JSONArray();
        for (SnapshotRow row : snapshot.rows) {
            JSONObject rowObj = rowToJson(row);
            SnapshotRow prev = prevRows.get(row.code);
            rowObj.put("scoreDelta",       Double.valueOf(prev != null ? selectionScoreOf(row) - selectionScoreOf(prev) : 0));
            rowObj.put("prevPrice",        Double.valueOf(prev != null ? prev.price : 0));
            rowObj.put("consecutiveDays",  Long.valueOf(consecutiveDays.getOrDefault(row.code, 1)));
            rows.add(rowObj);
        }

        JSONObject result = new JSONObject();
        result.put("date",              snapshot.date);
        result.put("prevDate",          prevDate != null ? prevDate : "");
        result.put("count",             Long.valueOf(total));
        result.put("likelyCount",       Long.valueOf(likelyCount));
        result.put("watchlistCount",    Long.valueOf(watchlistCount));
        result.put("scoreUpCount",      Long.valueOf(scoreUpCount));
        result.put("scoreUpPct",        Double.valueOf(Math.round(scoreUpPct * 10) / 10.0));
        result.put("volumeSurgeCount",  Long.valueOf(volumeSurgeCount));
        result.put("volumeSurgePct",    Double.valueOf(Math.round(volumeSurgePct * 10) / 10.0));
        result.put("qualifiedCount",    Long.valueOf(qualifiedCount));
        result.put("qualifiedPct",      Double.valueOf(Math.round(qualifiedPct * 10) / 10.0));
        result.put("buyPointCount",     Long.valueOf(buyPointCount));
        result.put("buyPointPct",       Double.valueOf(Math.round(buyPointPct * 10) / 10.0));
        result.put("averageSelectionScore", Double.valueOf(Math.round(averageSelectionScore * 10) / 10.0));
        result.put("averageNewsScore", Double.valueOf(Math.round(averageNewsScore * 10) / 10.0));
        result.put("averageThemeScore", Double.valueOf(Math.round(averageThemeScore * 10) / 10.0));
        result.put("averageConfidence", Double.valueOf(Math.round(averageConfidence * 10) / 10.0));
        result.put("confidenceReady",   Boolean.valueOf(confidenceReadyCount > 0));
        result.put("confidenceReadyCount", Long.valueOf(confidenceReadyCount));
        result.put("likelyThreshold",   Double.valueOf(LIKELY_THRESHOLD));
        result.put("watchlistThreshold", Double.valueOf(WATCHLIST_THRESHOLD));
        result.put("marketScore",       Double.valueOf(Math.round(marketScore * 10) / 10.0));
        result.put("marketSignal",      marketSignal);
        result.put("marketSignalText",  marketSignalText);
        result.put("marketDangerScore", Double.valueOf(Math.round(marketDangerScore * 10) / 10.0));
        result.put("marketAlert",       marketAlert);
        result.put("marketAlertText",   marketAlertText);
        result.put("themes",            themes);
        result.put("rows",              rows);
        return result.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public String renderLatestJson(String latestDate, List<StockAnalysisResultVO> results) throws Exception {
        StockHistoryDatabase db = new StockHistoryDatabase();
        Map<String, Snapshot> snapshots = db.loadSnapshots();
        Snapshot currentSnapshot = db.buildSnapshotForDate(latestDate, results);
        Map<String, SnapshotRow> prevRows = new HashMap<String, SnapshotRow>();

        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        String prevDate = null;
        if (!dates.isEmpty()) {
            int sameDateIndex = dates.indexOf(latestDate);
            if (sameDateIndex >= 0) {
                prevDate = sameDateIndex > 0 ? dates.get(sameDateIndex - 1) : null;
            } else {
                prevDate = dates.get(dates.size() - 1);
            }
        }

        if (prevDate != null) {
            Snapshot prevSnapshot = snapshots.get(prevDate);
            if (prevSnapshot != null) {
                for (SnapshotRow row : prevSnapshot.rows) {
                    prevRows.put(row.code, row);
                }
            }
        }

        List<String> scoreDates = new ArrayList<String>(dates);
        if (!scoreDates.contains(latestDate)) {
            scoreDates.add(latestDate);
        }
        Collections.sort(scoreDates);
        Map<String, Map<String, Double>> scoreLookup = buildScoreLookup(snapshots, dates);
        Map<String, Double> currentScoreMap = new HashMap<String, Double>();
        for (SnapshotRow row : currentSnapshot.rows) {
            currentScoreMap.put(row.code, Double.valueOf(selectionScoreOf(row)));
        }
        scoreLookup.put(latestDate, currentScoreMap);
        Map<String, Integer> consecutiveDays = computeConsecutiveDays(scoreLookup, scoreDates, currentSnapshot);

        int prevLikelyCount = 0;
        double prevSelectionScoreSum = 0D;
        int prevTotal = 0;
        if (prevDate != null) {
            Snapshot prevSnapshot = snapshots.get(prevDate);
            if (prevSnapshot != null) {
                prevTotal = prevSnapshot.rows.size();
                for (SnapshotRow row : prevSnapshot.rows) {
                    if (isLikely(row)) {
                        prevLikelyCount++;
                    }
                    prevSelectionScoreSum += selectionScoreOf(row);
                }
            }
        }

        int likelyCount = 0, watchlistCount = 0, scoreUpCount = 0, volumeSurgeCount = 0, qualifiedCount = 0,
                buyPointCount = 0, confidenceReadyCount = 0, newsHotCount = 0, newsRiskCount = 0, themeHotCount = 0;
        double selectionScoreSum = 0D;
        double confidenceSum = 0D;
        double newsScoreSum = 0D;
        double themeScoreSum = 0D;
        for (SnapshotRow row : currentSnapshot.rows) {
            if (isLikely(row)) likelyCount++;
            else if (selectionScoreOf(row) >= WATCHLIST_THRESHOLD) watchlistCount++;
            if (row.selectionQualified) qualifiedCount++;
            if (buyPointScoreOf(row) >= 75D) buyPointCount++;
            if (row.volumeRatio >= 1.8) volumeSurgeCount++;
            if (row.newsScore >= 60D) newsHotCount++;
            if (row.newsRiskScore >= 65D) newsRiskCount++;
            if (row.themeScore >= 60D) themeHotCount++;
            SnapshotRow prev = prevRows.get(row.code);
            if (prev != null && selectionScoreOf(row) > selectionScoreOf(prev)) scoreUpCount++;
            selectionScoreSum += selectionScoreOf(row);
            newsScoreSum += row.newsScore;
            themeScoreSum += row.themeScore;
            if (hasDataConfidence(row)) {
                confidenceSum += row.dataConfidence;
                confidenceReadyCount++;
            }
        }
        int total = currentSnapshot.rows.size();
        double scoreUpPct = total > 0 ? scoreUpCount * 100.0 / total : 0;
        double volumeSurgePct = total > 0 ? volumeSurgeCount * 100.0 / total : 0;
        double qualifiedPct = total > 0 ? qualifiedCount * 100.0 / total : 0;
        double buyPointPct = total > 0 ? buyPointCount * 100.0 / total : 0;
        double breadthPct = total > 0 ? (likelyCount + watchlistCount) * 100.0 / total : 0;
        double averageSelectionScore = total > 0 ? selectionScoreSum / total : 0;
        double averageNewsScore = total > 0 ? newsScoreSum / total : 0;
        double averageThemeScore = total > 0 ? themeScoreSum / total : 0;
        double averageConfidence = confidenceReadyCount > 0 ? confidenceSum / confidenceReadyCount : 0;
        double prevAverageSelectionScore = prevTotal > 0 ? prevSelectionScoreSum / prevTotal : averageSelectionScore;
        double marketScore = computeMarketScore(scoreUpPct, breadthPct, qualifiedPct, buyPointPct,
                averageSelectionScore);
        double marketDangerScore = computeMarketDangerScore(marketScore, averageSelectionScore, prevAverageSelectionScore,
                likelyCount, prevLikelyCount, total > 0 ? newsRiskCount * 100.0 / total : 0,
                total > 0 ? themeHotCount * 100.0 / total : 0, volumeSurgePct);
        String marketAlert = resolveMarketAlert(marketDangerScore);
        String marketAlertText = buildMarketAlertText(marketAlert, marketDangerScore, newsRiskCount, likelyCount,
                prevLikelyCount, averageSelectionScore, prevAverageSelectionScore);
        JSONArray themes = buildThemeHeatJson(currentSnapshot.rows, prevRows);

        String marketSignal;
        String marketSignalText;
        if (marketScore >= 65D) {
            marketSignal = "偏強";
            marketSignalText = String.format("市場環境偏強，分數上升 %.0f%%、可交易標的 %.0f%%，買點可積極挑選", scoreUpPct,
                    qualifiedPct);
        } else if (marketScore >= 55D) {
            marketSignal = "中性";
            marketSignalText = String.format("市場中性偏多，分數上升 %.0f%%，但仍需嚴格挑買點", scoreUpPct);
        } else if (marketScore >= 45D) {
            marketSignal = "偏弱";
            marketSignalText = String.format("市場偏弱，買點數量有限（%.0f%%），建議縮小部位", buyPointPct);
        } else {
            marketSignal = "警戒";
            marketSignalText = String.format("市場警戒，分數上升 %.0f%%、可交易標的 %.0f%%，應偏保守", scoreUpPct, qualifiedPct);
        }

        JSONArray rows = new JSONArray();
        for (SnapshotRow row : currentSnapshot.rows) {
            JSONObject rowObj = rowToJson(row);
            SnapshotRow prev = prevRows.get(row.code);
            rowObj.put("scoreDelta", Double.valueOf(prev != null ? selectionScoreOf(row) - selectionScoreOf(prev) : 0));
            rowObj.put("prevPrice", Double.valueOf(prev != null ? prev.price : 0));
            rowObj.put("consecutiveDays", Long.valueOf(consecutiveDays.getOrDefault(row.code, 1)));
            rows.add(rowObj);
        }

        JSONObject result = new JSONObject();
        result.put("date", currentSnapshot.date);
        result.put("prevDate", prevDate != null ? prevDate : "");
        result.put("count", Long.valueOf(total));
        result.put("likelyCount", Long.valueOf(likelyCount));
        result.put("watchlistCount", Long.valueOf(watchlistCount));
        result.put("scoreUpCount", Long.valueOf(scoreUpCount));
        result.put("scoreUpPct", Double.valueOf(Math.round(scoreUpPct * 10) / 10.0));
        result.put("volumeSurgeCount", Long.valueOf(volumeSurgeCount));
        result.put("volumeSurgePct", Double.valueOf(Math.round(volumeSurgePct * 10) / 10.0));
        result.put("qualifiedCount", Long.valueOf(qualifiedCount));
        result.put("qualifiedPct", Double.valueOf(Math.round(qualifiedPct * 10) / 10.0));
        result.put("buyPointCount", Long.valueOf(buyPointCount));
        result.put("buyPointPct", Double.valueOf(Math.round(buyPointPct * 10) / 10.0));
        result.put("averageSelectionScore", Double.valueOf(Math.round(averageSelectionScore * 10) / 10.0));
        result.put("averageNewsScore", Double.valueOf(Math.round(averageNewsScore * 10) / 10.0));
        result.put("averageThemeScore", Double.valueOf(Math.round(averageThemeScore * 10) / 10.0));
        result.put("averageConfidence", Double.valueOf(Math.round(averageConfidence * 10) / 10.0));
        result.put("confidenceReady", Boolean.valueOf(confidenceReadyCount > 0));
        result.put("confidenceReadyCount", Long.valueOf(confidenceReadyCount));
        result.put("likelyThreshold", Double.valueOf(LIKELY_THRESHOLD));
        result.put("watchlistThreshold", Double.valueOf(WATCHLIST_THRESHOLD));
        result.put("marketScore", Double.valueOf(Math.round(marketScore * 10) / 10.0));
        result.put("marketSignal", marketSignal);
        result.put("marketSignalText", marketSignalText);
        result.put("marketDangerScore", Double.valueOf(Math.round(marketDangerScore * 10) / 10.0));
        result.put("marketAlert", marketAlert);
        result.put("marketAlertText", marketAlertText);
        result.put("themes", themes);
        result.put("rows", rows);
        return result.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public String renderStockHistoryJson(String code) throws Exception {
        StockHistoryDatabase db = new StockHistoryDatabase();
        Map<String, Snapshot> snapshots = db.loadSnapshots();

        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);

        JSONArray history = new JSONArray();
        String name = "", market = "", industry = "";

        for (String date : dates) {
            for (SnapshotRow row : snapshots.get(date).rows) {
                if (code.equals(row.code)) {
                    if (name.isEmpty()) { name = row.name; market = row.market; industry = row.industry; }
                    history.add(rowToJson(row));
                    break;
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("code",     code);
        result.put("name",     name);
        result.put("market",   market);
        result.put("industry", industry);
        result.put("history",  history);
        return result.toJSONString();
    }

    // ── 連續高分天數 ──────────────────────────────────────────────────────────

    private Map<String, Map<String, Double>> buildScoreLookup(Map<String, Snapshot> snapshots, List<String> sortedDates) {
        Map<String, Map<String, Double>> lookup = new HashMap<String, Map<String, Double>>();
        for (String date : sortedDates) {
            Map<String, Double> scoreMap = new HashMap<String, Double>();
            for (SnapshotRow row : snapshots.get(date).rows) {
                scoreMap.put(row.code, Double.valueOf(selectionScoreOf(row)));
            }
            lookup.put(date, scoreMap);
        }
        return lookup;
    }

    private Map<String, Integer> computeConsecutiveDays(
            Map<String, Map<String, Double>> scoreLookup,
            List<String> sortedDates, Snapshot latestSnapshot) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (SnapshotRow row : latestSnapshot.rows) {
            if (selectionScoreOf(row) < WATCHLIST_THRESHOLD) { result.put(row.code, Integer.valueOf(0)); continue; }
            int count = 1;
            for (int i = sortedDates.size() - 2; i >= 0; i--) {
                Map<String, Double> dayScores = scoreLookup.get(sortedDates.get(i));
                Double score = dayScores != null ? dayScores.get(row.code) : null;
                if (score != null && score >= WATCHLIST_THRESHOLD) count++;
                else break;
            }
            result.put(row.code, Integer.valueOf(count));
        }
        return result;
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private JSONObject rowToJson(SnapshotRow row) {
        JSONObject obj = new JSONObject();
        obj.put("date",     row.date);
        obj.put("code",     row.code);
        obj.put("name",     row.name);
        obj.put("market",   row.market);
        obj.put("industry", row.industry);
        obj.put("note",     row.note);
        obj.put("legacyScore",                     Double.valueOf(row.score));
        obj.put("score",                           Double.valueOf(selectionScoreOf(row)));
        obj.put("rawScore",                        Double.valueOf(row.rawScore));
        obj.put("selectionScore",                  Double.valueOf(row.selectionScore));
        obj.put("momentumScore",                   Double.valueOf(row.momentumScore));
        obj.put("qualityScore",                    Double.valueOf(row.qualityScore));
        obj.put("sectorScore",                     Double.valueOf(row.sectorScore));
        obj.put("themeScore",                      Double.valueOf(row.themeScore));
        obj.put("primaryTheme",                    row.primaryTheme);
        obj.put("themeTags",                       row.themeTags);
        obj.put("trendPersistenceScore",           Double.valueOf(row.trendPersistenceScore));
        obj.put("trendPersistenceDays",            Long.valueOf(row.trendPersistenceDays));
        obj.put("newsScore",                       Double.valueOf(row.newsScore));
        obj.put("newsRiskScore",                   Double.valueOf(row.newsRiskScore));
        obj.put("relativeStrengthScore",           Double.valueOf(row.relativeStrengthScore));
        obj.put("industryReturnStrength",          Double.valueOf(row.industryReturnStrength));
        obj.put("industryVolumeStrength",          Double.valueOf(row.industryVolumeStrength));
        obj.put("industryFlowStrength",            Double.valueOf(row.industryFlowStrength));
        obj.put("eventDirection",                  row.eventDirection);
        obj.put("eventConfidence",                 Double.valueOf(row.eventConfidence));
        obj.put("eventFreshnessDays",              Long.valueOf(row.eventFreshnessDays));
        obj.put("eventTypeSummary",                row.eventTypeSummary);
        obj.put("newsSummary",                     row.newsSummary);
        obj.put("structureScore",                  Double.valueOf(row.structureScore));
        obj.put("structureLabel",                  row.structureLabel);
        obj.put("riskRewardScore",                 Double.valueOf(row.riskRewardScore));
        obj.put("riskRewardRatio",                 Double.valueOf(row.riskRewardRatio));
        obj.put("turnaroundScore",                 Double.valueOf(row.turnaroundScore));
        obj.put("revenueGrowthSignalScore",        Double.valueOf(row.revenueGrowthSignalScore));
        obj.put("earningsTurnaroundSignalScore",   Double.valueOf(row.earningsTurnaroundSignalScore));
        obj.put("profitabilityTurnaroundSignalScore",
                Double.valueOf(row.profitabilityTurnaroundSignalScore));
        obj.put("oneOffRiskScore",                 Double.valueOf(row.oneOffRiskScore));
        obj.put("turnaroundLabel",                 row.turnaroundLabel);
        obj.put("turnaroundReason",                row.turnaroundReason);
        obj.put("suggestedStopPrice",              Double.valueOf(row.suggestedStopPrice));
        obj.put("suggestedStopPct",                Double.valueOf(row.suggestedStopPct));
        obj.put("suggestedTargetPrice",            Double.valueOf(row.suggestedTargetPrice));
        obj.put("upsidePotentialPct",              Double.valueOf(row.upsidePotentialPct));
        obj.put("buyPointScore",                   Double.valueOf(buyPointScoreOf(row)));
        obj.put("buyPointLabel",                   row.buyPointLabel);
        obj.put("buyPointReason",                  row.buyPointReason);
        obj.put("dataConfidence",                  Double.valueOf(dataConfidenceOf(row)));
        obj.put("dataConfidenceReady",             Boolean.valueOf(hasDataConfidence(row)));
        obj.put("dataConfidenceReason",            row.dataConfidenceReason);
        obj.put("signalType",                      row.signalType);
        obj.put("signalHorizonDays",               Long.valueOf(row.signalHorizonDays));
        obj.put("entryRule",                       row.entryRule);
        obj.put("exitRule",                        row.exitRule);
        obj.put("validationMode",                  row.validationMode);
        obj.put("hardExclude",                     Boolean.valueOf(row.hardExclude));
        obj.put("hardExcludeReason",               row.hardExcludeReason);
        obj.put("dataQualityGrade",                row.dataQualityGrade);
        obj.put("winratePriorityScore",            Double.valueOf(row.winratePriorityScore));
        obj.put("expectedReturnScore",             Double.valueOf(row.expectedReturnScore));
        obj.put("maxDrawdownPenalty",              Double.valueOf(row.maxDrawdownPenalty));
        obj.put("backtestCohort",                  row.backtestCohort);
        obj.put("selectionQualified",              Boolean.valueOf(row.selectionQualified));
        obj.put("eligibilityReason",               row.eligibilityReason);
        obj.put("postClosePriorityScore",          Double.valueOf(row.postClosePriorityScore));
        obj.put("postCloseCategory",               row.postCloseCategory);
        obj.put("postCloseAction",                 row.postCloseAction);
        obj.put("postCloseReason",                 row.postCloseReason);
        obj.put("price",                           Double.valueOf(row.price));
        obj.put("movingAverage18",                 Double.valueOf(row.movingAverage18));
        obj.put("movingAverage20",                 Double.valueOf(row.movingAverage20));
        obj.put("movingAverage54",                 Double.valueOf(row.movingAverage54));
        obj.put("movingAverage60",                 Double.valueOf(row.movingAverage60));
        obj.put("movingAverage120",                Double.valueOf(row.movingAverage120));
        obj.put("volumeRatio",                     Double.valueOf(row.volumeRatio));
        obj.put("return18DayPct",                  Double.valueOf(row.return18DayPct));
        obj.put("return20DayPct",                  Double.valueOf(row.return20DayPct));
        obj.put("return54DayPct",                  Double.valueOf(row.return54DayPct));
        obj.put("return60DayPct",                  Double.valueOf(row.return60DayPct));
        obj.put("averageLots20",                   Double.valueOf(row.averageLots20));
        obj.put("averageTradeValue20Billion",      Double.valueOf(row.averageTradeValue20Billion));
        obj.put("volatility20Pct",                 Double.valueOf(row.volatility20Pct));
        obj.put("drawdownFromHigh60Pct",           Double.valueOf(row.drawdownFromHigh60Pct));
        obj.put("revenueScore",                    Double.valueOf(row.revenueScore));
        obj.put("chipsScore",                      Double.valueOf(row.chipsScore));
        obj.put("liquidityScore",                  Double.valueOf(row.liquidityScore));
        obj.put("valuationScore",                  Double.valueOf(row.valuationScore));
        obj.put("technicalScore",                  Double.valueOf(row.technicalScore));
        obj.put("financialQualityScore",           Double.valueOf(row.financialQualityScore));
        obj.put("fiveDayInstitutionalNetRatioPct", Double.valueOf(row.fiveDayInstitutionalNetRatioPct));
        obj.put("brokerNetRatioPct",               Double.valueOf(row.brokerNetRatioPct));
        obj.put("rsi14",                           Double.valueOf(row.rsi14));
        obj.put("stochasticK",                     Double.valueOf(row.stochasticK));
        obj.put("stochasticD",                     Double.valueOf(row.stochasticD));
        obj.put("epsAccelerationPct",              Double.valueOf(row.epsAccelerationPct));
        obj.put("peg",                             Double.valueOf(row.peg));
        obj.put("scoreReason",            row.scoreReason);
        obj.put("revenueReason",          row.revenueReason);
        obj.put("chipsReason",            row.chipsReason);
        obj.put("liquidityReason",        row.liquidityReason);
        obj.put("valuationReason",        row.valuationReason);
        obj.put("technicalReason",        row.technicalReason);
        obj.put("financialQualityReason", row.financialQualityReason);
        obj.put("eventRiskReason",        row.eventRiskReason);
        obj.put("likely",   Boolean.valueOf(isLikely(row)));
        return obj;
    }

    private double selectionScoreOf(SnapshotRow row) {
        return row.selectionScore > 0D ? row.selectionScore : row.score;
    }

    private double buyPointScoreOf(SnapshotRow row) {
        return row.buyPointScore > 0D ? row.buyPointScore : selectionScoreOf(row);
    }

    private double dataConfidenceOf(SnapshotRow row) {
        return hasDataConfidence(row) ? row.dataConfidence : 0D;
    }

    private boolean hasDataConfidence(SnapshotRow row) {
        return row.dataConfidence > 0D;
    }

    private boolean isLikely(SnapshotRow row) {
        return selectionScoreOf(row) >= LIKELY_THRESHOLD && row.selectionQualified
                && row.financialQualityScore >= MIN_LIKELY_FINANCIAL_SCORE
                && row.volumeRatio >= LIKELY_MIN_VOLUME_RATIO && row.volumeRatio <= LIKELY_MAX_VOLUME_RATIO;
    }

    private double computeMarketScore(double scoreUpPct, double breadthPct, double qualifiedPct, double buyPointPct,
            double averageSelectionScore) {
        double score = scoreUpPct * 0.35D + breadthPct * 0.20D + qualifiedPct * 0.20D + buyPointPct * 0.10D
                + averageSelectionScore * 0.15D;
        return Math.max(0D, Math.min(100D, score));
    }

    private double computeMarketDangerScore(double marketScore, double averageSelectionScore,
            double prevAverageSelectionScore, int likelyCount, int prevLikelyCount, double newsRiskPct,
            double themeHotPct, double volumeSurgePct) {
        double score = (100D - marketScore) * 0.55D;
        if (prevLikelyCount > 0 && likelyCount < prevLikelyCount) {
            score += Math.min(18D, (prevLikelyCount - likelyCount) * 100D / prevLikelyCount * 0.35D);
        }
        if (averageSelectionScore < prevAverageSelectionScore) {
            score += Math.min(12D, (prevAverageSelectionScore - averageSelectionScore) * 1.8D);
        }
        score += newsRiskPct * 0.20D;
        score += Math.max(0D, volumeSurgePct - themeHotPct) * 0.08D;
        return Math.max(0D, Math.min(100D, score));
    }

    private String resolveMarketAlert(double marketDangerScore) {
        if (marketDangerScore >= 72D) {
            return "高風險";
        }
        if (marketDangerScore >= 58D) {
            return "警戒";
        }
        if (marketDangerScore >= 45D) {
            return "注意";
        }
        return "中性";
    }

    private String buildMarketAlertText(String marketAlert, double marketDangerScore, int newsRiskCount, int likelyCount,
            int prevLikelyCount, double averageSelectionScore, double prevAverageSelectionScore) {
        List<String> parts = new ArrayList<String>();
        parts.add("危險分數 " + String.format("%.1f", Double.valueOf(marketDangerScore)));
        if (newsRiskCount > 0) {
            parts.add("高新聞風險個股 " + newsRiskCount + " 檔");
        }
        if (prevLikelyCount > 0 && likelyCount < prevLikelyCount) {
            parts.add("Likely 標的減少 " + (prevLikelyCount - likelyCount) + " 檔");
        }
        if (averageSelectionScore < prevAverageSelectionScore) {
            parts.add("平均策略分走弱");
        }
        if (parts.size() == 1) {
            parts.add("目前未見全面性風險擴散");
        }
        return marketAlert + "：" + join(parts, "，");
    }

    @SuppressWarnings("unchecked")
    private JSONArray buildThemeHeatJson(List<SnapshotRow> rows, Map<String, SnapshotRow> prevRows) {
        Map<String, ThemeStats> statsByTheme = new HashMap<String, ThemeStats>();
        for (SnapshotRow row : rows) {
            List<String> themes = resolveThemes(row);
            for (String theme : themes) {
                ThemeStats stats = statsByTheme.get(theme);
                if (stats == null) {
                    stats = new ThemeStats();
                    stats.name = theme;
                    statsByTheme.put(theme, stats);
                }
                stats.count++;
                stats.selectionSum += selectionScoreOf(row);
                stats.buyPointSum += buyPointScoreOf(row);
                stats.newsSum += row.newsScore;
                stats.themeSum += row.themeScore;
                if (selectionScoreOf(row) >= WATCHLIST_THRESHOLD) {
                    stats.strongCount++;
                }
                SnapshotRow prev = prevRows.get(row.code);
                if (prev != null && selectionScoreOf(row) > selectionScoreOf(prev)) {
                    stats.risingCount++;
                }
            }
        }

        List<ThemeStats> ordered = new ArrayList<ThemeStats>(statsByTheme.values());
        Collections.sort(ordered, (a, b) -> Double.compare(b.heatScore(), a.heatScore()));

        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(8, ordered.size()); i++) {
            ThemeStats stats = ordered.get(i);
            JSONObject obj = new JSONObject();
            obj.put("name", stats.name);
            obj.put("heatScore", Double.valueOf(round1(stats.heatScore())));
            obj.put("memberCount", Long.valueOf(stats.count));
            obj.put("avgSelectionScore", Double.valueOf(round1(stats.selectionSum / stats.count)));
            obj.put("avgBuyPointScore", Double.valueOf(round1(stats.buyPointSum / stats.count)));
            obj.put("avgNewsScore", Double.valueOf(round1(stats.newsSum / stats.count)));
            obj.put("risingPct", Double.valueOf(round1(stats.count == 0 ? 0D : stats.risingCount * 100D / stats.count)));
            obj.put("alert", stats.heatScore() >= 75D ? "熱門" : stats.heatScore() >= 62D ? "升溫" : "觀察");
            array.add(obj);
        }
        return array;
    }

    private List<String> resolveThemes(SnapshotRow row) {
        List<String> themes = new ArrayList<String>();
        if (row.primaryTheme != null && row.primaryTheme.length() > 0 && !"一般".equals(row.primaryTheme)) {
            themes.add(row.primaryTheme);
        }
        if (row.themeTags != null && row.themeTags.length() > 0) {
            for (String token : row.themeTags.split("[、,;；]")) {
                String trimmed = token == null ? "" : token.trim();
                if (trimmed.length() > 0 && !"一般".equals(trimmed) && !themes.contains(trimmed)) {
                    themes.add(trimmed);
                }
            }
        }
        return themes;
    }

    private double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static class ThemeStats {
        private String name = "";
        private int count;
        private int strongCount;
        private int risingCount;
        private double selectionSum;
        private double buyPointSum;
        private double newsSum;
        private double themeSum;

        private double heatScore() {
            if (count == 0) {
                return 0D;
            }
            double strongPct = strongCount * 100D / count;
            double risingPct = risingCount * 100D / count;
            return Math.max(0D, Math.min(100D, themeSum / count * 0.30D + newsSum / count * 0.25D
                    + selectionSum / count * 0.20D + buyPointSum / count * 0.15D + strongPct * 0.05D
                    + risingPct * 0.05D));
        }
    }
}
