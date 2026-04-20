package stock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.NumberParser;

public class ScoringConfig {

    public static final String DEFAULT_CONFIG_PATH = "config/scoring_profiles.json";

    private final Qualification qualification;
    private final Map<MarketRegime, RegimeProfile> profiles;

    public ScoringConfig(Qualification qualification, Map<MarketRegime, RegimeProfile> profiles) {
        this.qualification = qualification;
        this.profiles = profiles;
    }

    public Qualification getQualification() {
        return qualification;
    }

    public RegimeProfile getProfile(MarketRegime regime) {
        RegimeProfile profile = profiles.get(regime);
        if (profile != null) {
            return profile;
        }
        return profiles.get(MarketRegime.RANGE_BOUND);
    }

    public static ScoringConfig loadDefault() {
        return load(new File(DEFAULT_CONFIG_PATH));
    }

    public static ScoringConfig load(File file) {
        ScoringConfig defaults = defaults();
        if (file == null || !file.exists()) {
            return defaults;
        }

        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            JSONObject root = (JSONObject) new JSONParser().parse(reader);
            Qualification qualification = Qualification.fromJson((JSONObject) root.get("qualification"),
                    defaults.getQualification());
            Map<MarketRegime, RegimeProfile> profiles = new EnumMap<MarketRegime, RegimeProfile>(MarketRegime.class);
            JSONObject regimes = (JSONObject) root.get("regimes");
            for (MarketRegime regime : MarketRegime.values()) {
                JSONObject regimeJson = regimes == null ? null : (JSONObject) regimes.get(regime.name());
                profiles.put(regime, RegimeProfile.fromJson(regime, regimeJson, defaults.getProfile(regime)));
            }
            return new ScoringConfig(qualification, profiles);
        } catch (Exception ignored) {
            return defaults;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static ScoringConfig defaults() {
        Qualification qualification = new Qualification(4D, 8D, 12D, 0.8D, 2.5D, 65D);
        Map<MarketRegime, RegimeProfile> profiles = new EnumMap<MarketRegime, RegimeProfile>(MarketRegime.class);
        profiles.put(MarketRegime.BULL_TREND, new RegimeProfile(MarketRegime.BULL_TREND, 0.34D, 0.46D, 0.20D, 4D,
                0.78D, 0.12D, 0.07D, 5D, 0.50D, 0.18D, 0.11D, 0.09D, 0.04D, 0.08D, 0.15D, 2.0D, 2.8D, 75D, 72D,
                12D));
        profiles.put(MarketRegime.RANGE_BOUND, new RegimeProfile(MarketRegime.RANGE_BOUND, 0.45D, 0.35D, 0.20D, 4D,
                0.82D, 0.08D, 0.06D, 5D, 0.54D, 0.16D, 0.10D, 0.08D, 0.04D, 0.08D, 0.18D, 1.7D, 2.4D, 75D, 72D,
                12D));
        profiles.put(MarketRegime.BEAR_CORRECTION, new RegimeProfile(MarketRegime.BEAR_CORRECTION, 0.56D, 0.22D, 0.22D,
                4.5D, 0.86D, 0.07D, 0.05D, 4D, 0.56D, 0.16D, 0.08D, 0.10D, 0.05D, 0.05D, 0.22D, 1.45D, 2.1D, 78D,
                76D, 13D));
        profiles.put(MarketRegime.PANIC_SELLOFF, new RegimeProfile(MarketRegime.PANIC_SELLOFF, 0.60D, 0.16D, 0.24D,
                5D, 0.90D, 0.05D, 0.03D, 2D, 0.58D, 0.14D, 0.06D, 0.12D, 0.05D, 0.05D, 0.26D, 1.25D, 1.9D, 82D,
                80D, 14D));
        return new ScoringConfig(qualification, profiles);
    }

    public static class Qualification {
        public final double minLiquidityScore;
        public final double minFinancialQualityScore;
        public final double likelyMinFinancialQualityScore;
        public final double healthyVolumeMin;
        public final double healthyVolumeMax;
        public final double minDataConfidence;

        public Qualification(double minLiquidityScore, double minFinancialQualityScore,
                double likelyMinFinancialQualityScore, double healthyVolumeMin, double healthyVolumeMax,
                double minDataConfidence) {
            this.minLiquidityScore = minLiquidityScore;
            this.minFinancialQualityScore = minFinancialQualityScore;
            this.likelyMinFinancialQualityScore = likelyMinFinancialQualityScore;
            this.healthyVolumeMin = healthyVolumeMin;
            this.healthyVolumeMax = healthyVolumeMax;
            this.minDataConfidence = minDataConfidence;
        }

        private static Qualification fromJson(JSONObject json, Qualification fallback) {
            if (json == null) {
                return fallback;
            }
            return new Qualification(number(json, "minLiquidityScore", fallback.minLiquidityScore),
                    number(json, "minFinancialQualityScore", fallback.minFinancialQualityScore),
                    number(json, "likelyMinFinancialQualityScore", fallback.likelyMinFinancialQualityScore),
                    number(json, "healthyVolumeMin", fallback.healthyVolumeMin),
                    number(json, "healthyVolumeMax", fallback.healthyVolumeMax),
                    number(json, "minDataConfidence", fallback.minDataConfidence));
        }
    }

    public static class RegimeProfile {
        public final MarketRegime regime;
        public final double selectionQualityWeight;
        public final double selectionMomentumWeight;
        public final double selectionRawWeight;
        public final double selectionEventPenaltyMultiplier;
        public final double selectionCompositeBaseWeight;
        public final double selectionTrendWeight;
        public final double selectionSectorWeight;
        public final double selectionConstant;
        public final double buyCompositeBaseWeight;
        public final double buyStructureWeight;
        public final double buyTrendWeight;
        public final double buyRiskRewardWeight;
        public final double buySectorWeight;
        public final double buyNewsWeight;
        public final double buyNewsRiskPenaltyMultiplier;
        public final double stopAtrMultiplier;
        public final double trailingAtrMultiplier;
        public final double buyPointThreshold;
        public final double likelySelectionThreshold;
        public final double likelyMinFinancialQualityScore;

        public RegimeProfile(MarketRegime regime, double selectionQualityWeight, double selectionMomentumWeight,
                double selectionRawWeight, double selectionEventPenaltyMultiplier,
                double selectionCompositeBaseWeight, double selectionTrendWeight, double selectionSectorWeight,
                double selectionConstant, double buyCompositeBaseWeight, double buyStructureWeight,
                double buyTrendWeight, double buyRiskRewardWeight, double buySectorWeight, double buyNewsWeight,
                double buyNewsRiskPenaltyMultiplier, double stopAtrMultiplier, double trailingAtrMultiplier,
                double buyPointThreshold, double likelySelectionThreshold, double likelyMinFinancialQualityScore) {
            this.regime = regime;
            this.selectionQualityWeight = selectionQualityWeight;
            this.selectionMomentumWeight = selectionMomentumWeight;
            this.selectionRawWeight = selectionRawWeight;
            this.selectionEventPenaltyMultiplier = selectionEventPenaltyMultiplier;
            this.selectionCompositeBaseWeight = selectionCompositeBaseWeight;
            this.selectionTrendWeight = selectionTrendWeight;
            this.selectionSectorWeight = selectionSectorWeight;
            this.selectionConstant = selectionConstant;
            this.buyCompositeBaseWeight = buyCompositeBaseWeight;
            this.buyStructureWeight = buyStructureWeight;
            this.buyTrendWeight = buyTrendWeight;
            this.buyRiskRewardWeight = buyRiskRewardWeight;
            this.buySectorWeight = buySectorWeight;
            this.buyNewsWeight = buyNewsWeight;
            this.buyNewsRiskPenaltyMultiplier = buyNewsRiskPenaltyMultiplier;
            this.stopAtrMultiplier = stopAtrMultiplier;
            this.trailingAtrMultiplier = trailingAtrMultiplier;
            this.buyPointThreshold = buyPointThreshold;
            this.likelySelectionThreshold = likelySelectionThreshold;
            this.likelyMinFinancialQualityScore = likelyMinFinancialQualityScore;
        }

        private static RegimeProfile fromJson(MarketRegime regime, JSONObject json, RegimeProfile fallback) {
            if (json == null) {
                return fallback;
            }
            return new RegimeProfile(regime, number(json, "selectionQualityWeight", fallback.selectionQualityWeight),
                    number(json, "selectionMomentumWeight", fallback.selectionMomentumWeight),
                    number(json, "selectionRawWeight", fallback.selectionRawWeight),
                    number(json, "selectionEventPenaltyMultiplier", fallback.selectionEventPenaltyMultiplier),
                    number(json, "selectionCompositeBaseWeight", fallback.selectionCompositeBaseWeight),
                    number(json, "selectionTrendWeight", fallback.selectionTrendWeight),
                    number(json, "selectionSectorWeight", fallback.selectionSectorWeight),
                    number(json, "selectionConstant", fallback.selectionConstant),
                    number(json, "buyCompositeBaseWeight", fallback.buyCompositeBaseWeight),
                    number(json, "buyStructureWeight", fallback.buyStructureWeight),
                    number(json, "buyTrendWeight", fallback.buyTrendWeight),
                    number(json, "buyRiskRewardWeight", fallback.buyRiskRewardWeight),
                    number(json, "buySectorWeight", fallback.buySectorWeight),
                    number(json, "buyNewsWeight", fallback.buyNewsWeight),
                    number(json, "buyNewsRiskPenaltyMultiplier", fallback.buyNewsRiskPenaltyMultiplier),
                    number(json, "stopAtrMultiplier", fallback.stopAtrMultiplier),
                    number(json, "trailingAtrMultiplier", fallback.trailingAtrMultiplier),
                    number(json, "buyPointThreshold", fallback.buyPointThreshold),
                    number(json, "likelySelectionThreshold", fallback.likelySelectionThreshold),
                    number(json, "likelyMinFinancialQualityScore", fallback.likelyMinFinancialQualityScore));
        }
    }

    private static double number(JSONObject json, String key, double fallback) {
        Object value = json.get(key);
        if (value == null) {
            return fallback;
        }
        return NumberParser.parseDouble(String.valueOf(value));
    }
}
