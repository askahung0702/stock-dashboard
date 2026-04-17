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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import stock.common.NumberParser;

public class BacktestCalibrationService {

    private static final int MIN_SAMPLE_COUNT = 80;

    public CalibrationArtifacts writeDefaultArtifacts() throws Exception {
        return writeArtifacts(new File("history", "backtest_summary.csv"));
    }

    public CalibrationArtifacts writeArtifacts(File backtestSummaryFile) throws Exception {
        CalibrationArtifacts artifacts = new CalibrationArtifacts();
        if (backtestSummaryFile == null || !backtestSummaryFile.exists() || backtestSummaryFile.length() == 0L) {
            return artifacts;
        }

        Map<Integer, List<BacktestRow>> rowsByHorizon = loadRows(backtestSummaryFile);
        if (rowsByHorizon.isEmpty()) {
            return artifacts;
        }

        File historyDirectory = backtestSummaryFile.getAbsoluteFile().getParentFile();
        artifacts.signalCalibrationReportPath = writeSignalCalibrationReport(rowsByHorizon,
                new File(historyDirectory, "signal_calibration_report.csv"));
        artifacts.recommendedThresholdsPath = writeRecommendedThresholds(rowsByHorizon,
                new File(historyDirectory, "recommended_thresholds.csv"));
        artifacts.excludeRulesPath = writeExcludeRules(rowsByHorizon,
                new File(historyDirectory, "exclude_rules.csv"));
        return artifacts;
    }

    private Map<Integer, List<BacktestRow>> loadRows(File backtestSummaryFile) throws Exception {
        Map<Integer, List<BacktestRow>> rowsByHorizon = new LinkedHashMap<Integer, List<BacktestRow>>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(backtestSummaryFile), "UTF-8"));
        try {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rowsByHorizon;
            }
            Map<String, Integer> indexes = buildHeaderIndexes(parseCsvLine(stripBom(headerLine)));
            String line = null;
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.isEmpty()) {
                    continue;
                }
                BacktestRow row = new BacktestRow();
                row.horizonDays = (int) NumberParser.parseDouble(valueAt(fields, indexes, "horizon_days"));
                row.cohort = valueAt(fields, indexes, "cohort");
                row.sampleCount = (int) NumberParser.parseDouble(valueAt(fields, indexes, "sample_count"));
                row.netWinRatePct = NumberParser.parseDouble(valueAt(fields, indexes, "net_win_rate_pct"));
                row.avgNetReturnPct = NumberParser.parseDouble(valueAt(fields, indexes, "avg_net_return_pct"));
                row.avgMaxDrawdownClosePct = NumberParser.parseDouble(valueAt(fields, indexes, "avg_max_drawdown_close_pct"));
                if (row.horizonDays <= 0 || row.cohort.length() == 0) {
                    continue;
                }
                List<BacktestRow> rows = rowsByHorizon.get(Integer.valueOf(row.horizonDays));
                if (rows == null) {
                    rows = new ArrayList<BacktestRow>();
                    rowsByHorizon.put(Integer.valueOf(row.horizonDays), rows);
                }
                rows.add(row);
            }
        } finally {
            reader.close();
        }
        return rowsByHorizon;
    }

    private String writeSignalCalibrationReport(Map<Integer, List<BacktestRow>> rowsByHorizon, File file)
            throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
        try {
            writer.write('\uFEFF');
            writer.println(
                    "horizon_days,rank,cohort,sample_count,net_win_rate_pct,avg_net_return_pct,avg_max_drawdown_close_pct,recommendation");
            for (Map.Entry<Integer, List<BacktestRow>> entry : rowsByHorizon.entrySet()) {
                List<BacktestRow> ranked = rankRows(entry.getValue());
                for (int i = 0; i < ranked.size(); i++) {
                    BacktestRow row = ranked.get(i);
                    writer.println(entry.getKey().intValue() + "," + (i + 1) + "," + csv(row.cohort) + ","
                            + row.sampleCount + "," + format(row.netWinRatePct) + "," + format(row.avgNetReturnPct) + ","
                            + format(row.avgMaxDrawdownClosePct) + "," + csv(buildRecommendation(row)));
                }
            }
        } finally {
            writer.close();
        }
        return file.getAbsolutePath();
    }

    private String writeRecommendedThresholds(Map<Integer, List<BacktestRow>> rowsByHorizon, File file) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
        try {
            writer.write('\uFEFF');
            writer.println("signal_type,horizon_days,recommended_cohort,recommended_rule,reason");
            writeThresholdRow(writer, "隔日續強", 1, chooseBest(rowsByHorizon.get(Integer.valueOf(1)),
                    new String[] { "BUYPOINT_A", "BUYPOINT_75", "LIKELY", "WATCHLIST" }));
            writeThresholdRow(writer, "3-5日延續", 3, chooseBest(rowsByHorizon.get(Integer.valueOf(3)),
                    new String[] { "LIKELY", "QUALITY_70", "BUYPOINT_A", "BUYPOINT_75", "WATCHLIST" }));
            writeThresholdRow(writer, "5-10日波段", 5, chooseBest(rowsByHorizon.get(Integer.valueOf(5)),
                    new String[] { "QUALITY_70", "LIKELY", "BUYPOINT_A", "QUALIFIED" }));
            writeThresholdRow(writer, "5-10日波段", 10, chooseBest(rowsByHorizon.get(Integer.valueOf(10)),
                    new String[] { "QUALITY_70", "LIKELY", "QUALIFIED", "BUYPOINT_A" }));
        } finally {
            writer.close();
        }
        return file.getAbsolutePath();
    }

    private void writeThresholdRow(PrintWriter writer, String signalType, int horizonDays, BacktestRow row) {
        if (row == null) {
            return;
        }
        writer.println(csv(signalType) + "," + horizonDays + "," + csv(row.cohort) + ","
                + csv(resolveThresholdRule(row.cohort, horizonDays)) + "," + csv(buildRecommendation(row)));
    }

    private String writeExcludeRules(Map<Integer, List<BacktestRow>> rowsByHorizon, File file) throws Exception {
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
        try {
            writer.write('\uFEFF');
            writer.println("signal_type,horizon_days,rule_type,recommended_rule,reason");
            writeExcludeComparison(writer, "隔日續強", 1, findRow(rowsByHorizon.get(Integer.valueOf(1)), "WATCHLIST"),
                    findRow(rowsByHorizon.get(Integer.valueOf(1)), "LIKELY"),
                    "avoid_weaker_watchlist", "短線優先 likely，不把 watchlist 當主攻");
            writeExcludeComparison(writer, "隔日續強", 1, findRow(rowsByHorizon.get(Integer.valueOf(1)), "BUYPOINT_75"),
                    findRow(rowsByHorizon.get(Integer.valueOf(1)), "BUYPOINT_A"),
                    "prefer_stricter_timing", "短線追價時機要更嚴格，優先 A 級時機");
            writeExcludeComparison(writer, "5-10日波段", 5, findRow(rowsByHorizon.get(Integer.valueOf(5)), "QUALIFIED"),
                    findRow(rowsByHorizon.get(Integer.valueOf(5)), "QUALITY_70"),
                    "require_quality_gate", "波段布局優先保留品質門檻");
            writeExcludeComparison(writer, "5-10日波段", 10, findRow(rowsByHorizon.get(Integer.valueOf(10)), "ALL"),
                    findRow(rowsByHorizon.get(Integer.valueOf(10)), "LIKELY"),
                    "avoid_broad_low_edge_pool", "避免把低邊際優勢標的放入波段主名單");
        } finally {
            writer.close();
        }
        return file.getAbsolutePath();
    }

    private void writeExcludeComparison(PrintWriter writer, String signalType, int horizonDays, BacktestRow weaker,
            BacktestRow stronger, String ruleType, String recommendedRule) {
        if (weaker == null || stronger == null) {
            return;
        }
        double winrateGap = stronger.netWinRatePct - weaker.netWinRatePct;
        double returnGap = stronger.avgNetReturnPct - weaker.avgNetReturnPct;
        if (winrateGap < 2D && returnGap < 0.3D) {
            return;
        }
        String reason = stronger.cohort + " 相較 " + weaker.cohort + " 勝率 +" + format(winrateGap)
                + "，平均淨報酬 +" + format(returnGap);
        writer.println(csv(signalType) + "," + horizonDays + "," + csv(ruleType) + ","
                + csv(recommendedRule) + "," + csv(reason));
    }

    private BacktestRow chooseBest(List<BacktestRow> rows, String[] preferredCohorts) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        BacktestRow best = null;
        for (String cohort : preferredCohorts) {
            BacktestRow row = findRow(rows, cohort);
            if (row == null) {
                continue;
            }
            if (row.sampleCount < MIN_SAMPLE_COUNT) {
                continue;
            }
            if (best == null || compareRows(row, best) < 0) {
                best = row;
            }
        }
        return best != null ? best : rankRows(rows).isEmpty() ? null : rankRows(rows).get(0);
    }

    private List<BacktestRow> rankRows(List<BacktestRow> rows) {
        List<BacktestRow> ranked = new ArrayList<BacktestRow>();
        if (rows != null) {
            ranked.addAll(rows);
        }
        Collections.sort(ranked, new Comparator<BacktestRow>() {
            public int compare(BacktestRow left, BacktestRow right) {
                return compareRows(left, right);
            }
        });
        return ranked;
    }

    private int compareRows(BacktestRow left, BacktestRow right) {
        int samplePriority = Integer.compare(right.sampleCount >= MIN_SAMPLE_COUNT ? 1 : 0,
                left.sampleCount >= MIN_SAMPLE_COUNT ? 1 : 0);
        if (samplePriority != 0) {
            return samplePriority;
        }
        int winrateCompare = Double.compare(right.netWinRatePct, left.netWinRatePct);
        if (winrateCompare != 0) {
            return winrateCompare;
        }
        int returnCompare = Double.compare(right.avgNetReturnPct, left.avgNetReturnPct);
        if (returnCompare != 0) {
            return returnCompare;
        }
        return Double.compare(left.avgMaxDrawdownClosePct, right.avgMaxDrawdownClosePct);
    }

    private BacktestRow findRow(List<BacktestRow> rows, String cohort) {
        if (rows == null || cohort == null) {
            return null;
        }
        for (BacktestRow row : rows) {
            if (cohort.equals(row.cohort)) {
                return row;
            }
        }
        return null;
    }

    private String resolveThresholdRule(String cohort, int horizonDays) {
        if ("BUYPOINT_A".equals(cohort)) {
            return "buy_point_score >= 85";
        }
        if ("BUYPOINT_75".equals(cohort)) {
            return "buy_point_score >= 75";
        }
        if ("LIKELY".equals(cohort)) {
            return horizonDays <= 3 ? "selection_score >= 72 and likely gates" : "prefer likely-qualified names";
        }
        if ("QUALITY_70".equals(cohort)) {
            return "quality_score >= 70";
        }
        if ("WATCHLIST".equals(cohort)) {
            return "watchlist only if catalyst is fresh";
        }
        if ("QUALIFIED".equals(cohort)) {
            return "selection_qualified == true";
        }
        return cohort + " as current best cohort";
    }

    private String buildRecommendation(BacktestRow row) {
        return row.cohort + " / 勝率 " + format(row.netWinRatePct) + " / 平均淨報酬 " + format(row.avgNetReturnPct)
                + " / 平均回撤 " + format(row.avgMaxDrawdownClosePct);
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

    private String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private static class BacktestRow {
        private int horizonDays;
        private String cohort = "";
        private int sampleCount;
        private double netWinRatePct;
        private double avgNetReturnPct;
        private double avgMaxDrawdownClosePct;
    }

    public static class CalibrationArtifacts {
        public String signalCalibrationReportPath = "";
        public String recommendedThresholdsPath = "";
        public String excludeRulesPath = "";
    }
}
