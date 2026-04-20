package stock;

import stock.common.NumberParser;

public class ConfiguredScoringStrategy implements ScoringStrategy {

    private final ScoringConfig.Qualification qualification;
    private final ScoringConfig.RegimeProfile profile;

    public ConfiguredScoringStrategy(ScoringConfig.Qualification qualification, ScoringConfig.RegimeProfile profile) {
        this.qualification = qualification;
        this.profile = profile;
    }

    public MarketRegime getRegime() {
        return profile.regime;
    }

    public boolean isSelectionQualified(double liquidityScore, double financialQualityScore, double volumeRatio,
            double dataConfidence) {
        if (liquidityScore < qualification.minLiquidityScore) {
            return false;
        }
        if (financialQualityScore < qualification.minFinancialQualityScore) {
            return false;
        }
        if (dataConfidence > 0D && dataConfidence < qualification.minDataConfidence) {
            return false;
        }
        if (getRegime() == MarketRegime.BEAR_CORRECTION || getRegime() == MarketRegime.PANIC_SELLOFF) {
            if (volumeRatio < qualification.healthyVolumeMin || volumeRatio > 3.0D) {
                return false;
            }
        }
        return true;
    }

    public double scoreSelectionProfile(double rawScore, double qualityScore, double momentumScore, double volumeRatio,
            double eventRiskPenalty, boolean selectionQualified) {
        double rawNormalized = rawScore * 100D / 135D;
        double score = qualityScore * profile.selectionQualityWeight
                + momentumScore * profile.selectionMomentumWeight
                + rawNormalized * profile.selectionRawWeight
                - eventRiskPenalty * profile.selectionEventPenaltyMultiplier;
        if (!selectionQualified) {
            score -= 12D;
        }
        if (volumeRatio < 0.6D) {
            score -= 6D;
        } else if (volumeRatio < qualification.healthyVolumeMin) {
            score -= 2D;
        } else if (volumeRatio > 3.5D) {
            score -= 6D;
        } else if (volumeRatio > qualification.healthyVolumeMax) {
            score -= 3D;
        }
        if (getRegime() == MarketRegime.BULL_TREND && volumeRatio >= 1.0D && volumeRatio <= 2.8D) {
            score += 1.5D;
        }
        if ((getRegime() == MarketRegime.BEAR_CORRECTION || getRegime() == MarketRegime.PANIC_SELLOFF)
                && volumeRatio > qualification.healthyVolumeMax) {
            score -= 2D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    public double scoreSelectionComposite(double baseSelectionScore, double trendPersistenceScore, double sectorScore,
            double newsRiskScore) {
        double score = baseSelectionScore * profile.selectionCompositeBaseWeight
                + trendPersistenceScore * profile.selectionTrendWeight
                + sectorScore * profile.selectionSectorWeight
                + profile.selectionConstant;
        if (newsRiskScore > 60D) {
            score -= (newsRiskScore - 60D) * 0.15D;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    public double scoreBuyPointComposite(double baseBuyPointScore, double structureScore, double trendPersistenceScore,
            double riskRewardScore, double sectorScore, double newsScore, double newsRiskScore) {
        double score = baseBuyPointScore * profile.buyCompositeBaseWeight
                + structureScore * profile.buyStructureWeight
                + trendPersistenceScore * profile.buyTrendWeight
                + riskRewardScore * profile.buyRiskRewardWeight
                + sectorScore * profile.buySectorWeight
                + newsScore * profile.buyNewsWeight;
        if (newsRiskScore > 55D) {
            score -= (newsRiskScore - 55D) * profile.buyNewsRiskPenaltyMultiplier;
        }
        return NumberParser.clamp(score, 0D, 100D);
    }

    public double stopAtrMultiplier() {
        return profile.stopAtrMultiplier;
    }

    public double trailingAtrMultiplier() {
        return profile.trailingAtrMultiplier;
    }

    public double buyPointThreshold() {
        return profile.buyPointThreshold;
    }

    public double likelySelectionThreshold() {
        return profile.likelySelectionThreshold;
    }

    public double likelyMinFinancialQualityScore() {
        return Math.max(qualification.likelyMinFinancialQualityScore, profile.likelyMinFinancialQualityScore);
    }
}
