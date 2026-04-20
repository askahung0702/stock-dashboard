package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stock.vo.StockAnalysisResultVO;

public class IndustryMetricsSnapshot {

    private final Map<String, MetricGroup> groups;

    private IndustryMetricsSnapshot(Map<String, MetricGroup> groups) {
        this.groups = groups;
    }

    public static IndustryMetricsSnapshot build(List<StockAnalysisResultVO> results) {
        Map<String, MetricGroupBuilder> builders = new HashMap<String, MetricGroupBuilder>();
        for (StockAnalysisResultVO result : results) {
            String key = normalizeIndustry(result.getIndustry());
            MetricGroupBuilder builder = builders.get(key);
            if (builder == null) {
                builder = new MetricGroupBuilder();
                builders.put(key, builder);
            }
            builder.add("grossMargin", result.getGrossMarginPct());
            builder.add("operatingMargin", result.getOperatingMarginPct());
            builder.add("roa", result.getReturnOnAssetsPct());
            builder.add("roe", result.getReturnOnEquityPct());
            builder.add("peg", result.getPeg());
            builder.add("relativePe", relativePe(result));
            builder.add("nonOperating", result.getNonOperatingRatioPct());
        }

        Map<String, MetricGroup> groups = new HashMap<String, MetricGroup>();
        for (Map.Entry<String, MetricGroupBuilder> entry : builders.entrySet()) {
            groups.put(entry.getKey(), entry.getValue().build());
        }
        return new IndustryMetricsSnapshot(groups);
    }

    public double percentile(String industry, String metric, double value, boolean lowerIsBetter) {
        MetricGroup group = groups.get(normalizeIndustry(industry));
        if (group == null) {
            return 50D;
        }
        return group.percentile(metric, value, lowerIsBetter);
    }

    public double zScore(String industry, String metric, double value, boolean lowerIsBetter) {
        MetricGroup group = groups.get(normalizeIndustry(industry));
        if (group == null) {
            return 0D;
        }
        return group.zScore(metric, value, lowerIsBetter);
    }

    private static double relativePe(StockAnalysisResultVO result) {
        if (result.getPeerAveragePe() > 0D && result.getTrailingPe() > 0D) {
            return result.getTrailingPe() / result.getPeerAveragePe();
        }
        return result.getTrailingPe();
    }

    private static String normalizeIndustry(String industry) {
        String normalized = industry == null ? "" : industry.trim();
        return normalized.length() == 0 ? "未分類" : normalized;
    }

    private static class MetricGroupBuilder {
        private final Map<String, List<Double>> values = new HashMap<String, List<Double>>();

        private void add(String metric, double value) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value == 0D) {
                return;
            }
            List<Double> list = values.get(metric);
            if (list == null) {
                list = new ArrayList<Double>();
                values.put(metric, list);
            }
            list.add(Double.valueOf(value));
        }

        private MetricGroup build() {
            Map<String, MetricStats> stats = new HashMap<String, MetricStats>();
            for (Map.Entry<String, List<Double>> entry : values.entrySet()) {
                stats.put(entry.getKey(), MetricStats.from(entry.getValue()));
            }
            return new MetricGroup(stats);
        }
    }

    private static class MetricGroup {
        private final Map<String, MetricStats> stats;

        private MetricGroup(Map<String, MetricStats> stats) {
            this.stats = stats;
        }

        private double percentile(String metric, double value, boolean lowerIsBetter) {
            MetricStats metricStats = stats.get(metric);
            if (metricStats == null) {
                return 50D;
            }
            return metricStats.percentile(value, lowerIsBetter);
        }

        private double zScore(String metric, double value, boolean lowerIsBetter) {
            MetricStats metricStats = stats.get(metric);
            if (metricStats == null) {
                return 0D;
            }
            double z = metricStats.zScore(value);
            return lowerIsBetter ? -z : z;
        }
    }

    private static class MetricStats {
        private final List<Double> sorted;
        private final double mean;
        private final double stddev;

        private MetricStats(List<Double> sorted, double mean, double stddev) {
            this.sorted = sorted;
            this.mean = mean;
            this.stddev = stddev;
        }

        private static MetricStats from(List<Double> input) {
            List<Double> sorted = new ArrayList<Double>(input);
            Collections.sort(sorted);
            double sum = 0D;
            for (Double value : sorted) {
                sum += value.doubleValue();
            }
            double mean = sorted.isEmpty() ? 0D : sum / sorted.size();
            double variance = 0D;
            for (Double value : sorted) {
                double diff = value.doubleValue() - mean;
                variance += diff * diff;
            }
            double stddev = sorted.size() <= 1 ? 1D : Math.sqrt(variance / sorted.size());
            return new MetricStats(sorted, mean, stddev);
        }

        private double percentile(double value, boolean lowerIsBetter) {
            if (sorted.isEmpty()) {
                return 50D;
            }
            int count = 0;
            for (Double existing : sorted) {
                if (existing.doubleValue() <= value) {
                    count++;
                }
            }
            double pct = count * 100D / sorted.size();
            return lowerIsBetter ? 100D - pct : pct;
        }

        private double zScore(double value) {
            if (stddev <= 0D) {
                return 0D;
            }
            return (value - mean) / stddev;
        }
    }
}
