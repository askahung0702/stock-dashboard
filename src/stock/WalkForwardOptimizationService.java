package stock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import stock.StockHistoryDatabase.Snapshot;
import stock.StockHistoryDatabase.SnapshotRow;
import stock.common.NumberParser;

public class WalkForwardOptimizationService {

    public static void main(String[] args) throws Exception {
        OptimizationArtifacts artifacts = new WalkForwardOptimizationService().writeDefaultArtifacts();
        if (artifacts.reportPath.length() == 0) {
            System.out.println("Walk-forward optimization skipped: not enough history snapshots.");
            return;
        }
        System.out.println("Walk-forward optimization: " + artifacts.reportPath);
        if (artifacts.recommendationPath.length() > 0) {
            System.out.println("Parameter recommendations: " + artifacts.recommendationPath);
        }
    }

    public OptimizationArtifacts writeDefaultArtifacts() throws Exception {
        StockHistoryDatabase database = new StockHistoryDatabase();
        Map<String, Snapshot> snapshots = database.loadSnapshots();
        if (snapshots.size() < 15) {
            return new OptimizationArtifacts();
        }

        File historyDir = new File("history");
        OptimizationArtifacts artifacts = new OptimizationArtifacts();
        artifacts.reportPath = writeWalkForwardReport(snapshots,
                new File(historyDir, "walk_forward_optimization.csv"));
        artifacts.recommendationPath = writeRecommendations(snapshots,
                new File(historyDir, "scoring_parameter_recommendations.json"));
        return artifacts;
    }

    private String writeWalkForwardReport(Map<String, Snapshot> snapshots, File file) throws Exception {
        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        List<Map<String, SnapshotRow>> rowMaps = buildRowMaps(dates, snapshots);
        List<CandidateRule> candidateRules = buildCandidateRules();

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
        try {
            writer.write('\uFEFF');
            writer.println(
                    "train_start,train_end,test_start,test_end,best_rule,train_sample_count,train_avg_return_pct,test_sample_count,test_avg_return_pct,test_win_rate_pct");

            int trainWindow = 20;
            int testWindow = 5;
            for (int start = 0; start + trainWindow + testWindow < dates.size(); start += testWindow) {
                int trainStart = start;
                int trainEnd = start + trainWindow - 1;
                int testStart = trainEnd + 1;
                int testEnd = Math.min(dates.size() - 1, testStart + testWindow - 1);

                CandidateEvaluation best = null;
                for (CandidateRule rule : candidateRules) {
                    CandidateEvaluation evaluation = evaluateRule(rule, rowMaps, trainStart, trainEnd, 5);
                    if (best == null || evaluation.compareTo(best) < 0) {
                        best = evaluation;
                    }
                }
                if (best == null) {
                    continue;
                }

                CandidateEvaluation test = evaluateRule(best.rule, rowMaps, testStart, testEnd, 5);
                writer.println(csv(dates.get(trainStart)) + "," + csv(dates.get(trainEnd)) + ","
                        + csv(dates.get(testStart)) + "," + csv(dates.get(testEnd)) + ","
                        + csv(best.rule.name) + "," + best.sampleCount + "," + format(best.avgReturnPct) + ","
                        + test.sampleCount + "," + format(test.avgReturnPct) + "," + format(test.winRatePct));
            }
        } finally {
            writer.close();
        }
        return file.getAbsolutePath();
    }

    @SuppressWarnings("unchecked")
    private String writeRecommendations(Map<String, Snapshot> snapshots, File file) throws Exception {
        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        List<Map<String, SnapshotRow>> rowMaps = buildRowMaps(dates, snapshots);

        JSONArray ranked = new JSONArray();
        List<CandidateEvaluation> evaluations = new ArrayList<CandidateEvaluation>();
        for (CandidateRule rule : buildCandidateRules()) {
            evaluations.add(evaluateRule(rule, rowMaps, 0, dates.size() - 6, 5));
        }
        Collections.sort(evaluations);

        for (int i = 0; i < Math.min(5, evaluations.size()); i++) {
            CandidateEvaluation evaluation = evaluations.get(i);
            JSONObject obj = new JSONObject();
            obj.put("rank", Long.valueOf(i + 1));
            obj.put("rule", evaluation.rule.name);
            obj.put("sampleCount", Long.valueOf(evaluation.sampleCount));
            obj.put("avgReturnPct", Double.valueOf(round1(evaluation.avgReturnPct)));
            obj.put("winRatePct", Double.valueOf(round1(evaluation.winRatePct)));
            obj.put("likelyThreshold", Double.valueOf(evaluation.rule.selectionThreshold));
            obj.put("buyPointThreshold", Double.valueOf(evaluation.rule.buyPointThreshold));
            obj.put("minFinancialQuality", Double.valueOf(evaluation.rule.minFinancialQuality));
            ranked.add(obj);
        }

        JSONObject root = new JSONObject();
        root.put("generatedDate", dates.get(dates.size() - 1));
        root.put("optimizer", "walk_forward_threshold_grid");
        root.put("topRules", ranked);

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
        try {
            writer.write(root.toJSONString());
        } finally {
            writer.close();
        }
        return file.getAbsolutePath();
    }

    private CandidateEvaluation evaluateRule(CandidateRule rule, List<Map<String, SnapshotRow>> rowMaps,
            int startIndex, int endIndexInclusive, int horizonDays) {
        double totalReturn = 0D;
        int winCount = 0;
        int sampleCount = 0;
        int maxSignalIndex = Math.min(endIndexInclusive, rowMaps.size() - horizonDays - 1);
        for (int signalIndex = startIndex; signalIndex <= maxSignalIndex; signalIndex++) {
            Map<String, SnapshotRow> signals = rowMaps.get(signalIndex);
            Map<String, SnapshotRow> exits = rowMaps.get(signalIndex + horizonDays);
            for (SnapshotRow row : signals.values()) {
                if (!rule.matches(row)) {
                    continue;
                }
                SnapshotRow exitRow = exits.get(row.code);
                if (exitRow == null || row.price <= 0D || exitRow.price <= 0D) {
                    continue;
                }
                double ret = (exitRow.price - row.price) * 100D / row.price;
                totalReturn += ret;
                if (ret > 0D) {
                    winCount++;
                }
                sampleCount++;
            }
        }
        return new CandidateEvaluation(rule, sampleCount, sampleCount == 0 ? 0D : totalReturn / sampleCount,
                sampleCount == 0 ? 0D : winCount * 100D / sampleCount);
    }

    private List<Map<String, SnapshotRow>> buildRowMaps(List<String> dates, Map<String, Snapshot> snapshots) {
        List<Map<String, SnapshotRow>> rowMaps = new ArrayList<Map<String, SnapshotRow>>();
        for (String date : dates) {
            Snapshot snapshot = snapshots.get(date);
            Map<String, SnapshotRow> map = new LinkedHashMap<String, SnapshotRow>();
            if (snapshot != null) {
                for (SnapshotRow row : snapshot.rows) {
                    map.put(row.code, row);
                }
            }
            rowMaps.add(map);
        }
        return rowMaps;
    }

    private List<CandidateRule> buildCandidateRules() {
        List<CandidateRule> rules = new ArrayList<CandidateRule>();
        double[] selectionThresholds = new double[] { 70D, 72D, 75D, 78D };
        double[] buyPointThresholds = new double[] { 72D, 75D, 78D, 82D };
        double[] minFinancialQuality = new double[] { 12D, 13D, 14D };
        for (double selectionThreshold : selectionThresholds) {
            for (double buyPointThreshold : buyPointThresholds) {
                for (double minFinancial : minFinancialQuality) {
                    String name = "sel>=" + format(selectionThreshold) + "_buy>=" + format(buyPointThreshold)
                            + "_fin>=" + format(minFinancial);
                    rules.add(new CandidateRule(name, selectionThreshold, buyPointThreshold, minFinancial));
                }
            }
        }
        return rules;
    }

    private static String format(double value) {
        return String.format("%.1f", Double.valueOf(value));
    }

    private static double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private static String csv(String text) {
        String safe = text == null ? "" : text;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    public static class OptimizationArtifacts {
        public String reportPath = "";
        public String recommendationPath = "";
    }

    private static class CandidateRule {
        private final String name;
        private final double selectionThreshold;
        private final double buyPointThreshold;
        private final double minFinancialQuality;

        private CandidateRule(String name, double selectionThreshold, double buyPointThreshold,
                double minFinancialQuality) {
            this.name = name;
            this.selectionThreshold = selectionThreshold;
            this.buyPointThreshold = buyPointThreshold;
            this.minFinancialQuality = minFinancialQuality;
        }

        private boolean matches(SnapshotRow row) {
            double selectionScore = row.selectionScore > 0D ? row.selectionScore : row.score;
            double buyPointScore = row.buyPointScore > 0D ? row.buyPointScore : selectionScore;
            double dataConfidence = row.dataConfidence > 0D ? row.dataConfidence : 100D;
            double newsRiskScore = row.newsRiskScore > 0D ? row.newsRiskScore : 50D;
            double financialQuality = row.financialQualityScore > 0D ? row.financialQualityScore : minFinancialQuality;
            boolean selectionQualified = row.selectionQualified || row.financialQualityScore == 0D;
            return selectionQualified && selectionScore >= selectionThreshold
                    && buyPointScore >= buyPointThreshold
                    && financialQuality >= minFinancialQuality
                    && dataConfidence >= 65D
                    && newsRiskScore <= 70D
                    && row.volumeRatio >= 0.8D && row.volumeRatio <= 2.8D;
        }
    }

    private static class CandidateEvaluation implements Comparable<CandidateEvaluation> {
        private final CandidateRule rule;
        private final int sampleCount;
        private final double avgReturnPct;
        private final double winRatePct;

        private CandidateEvaluation(CandidateRule rule, int sampleCount, double avgReturnPct, double winRatePct) {
            this.rule = rule;
            this.sampleCount = sampleCount;
            this.avgReturnPct = avgReturnPct;
            this.winRatePct = winRatePct;
        }

        public int compareTo(CandidateEvaluation other) {
            int samplePriority = Integer.compare(other.sampleCount >= 60 ? 1 : 0, sampleCount >= 60 ? 1 : 0);
            if (samplePriority != 0) {
                return samplePriority;
            }
            int returnCompare = Double.compare(other.avgReturnPct, avgReturnPct);
            if (returnCompare != 0) {
                return returnCompare;
            }
            return Double.compare(other.winRatePct, winRatePct);
        }
    }
}
