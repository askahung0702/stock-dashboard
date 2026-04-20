package stock;

public interface ScoringStrategy {

    MarketRegime getRegime();

    boolean isSelectionQualified(double liquidityScore, double financialQualityScore, double volumeRatio,
            double dataConfidence);

    double scoreSelectionProfile(double rawScore, double qualityScore, double momentumScore, double volumeRatio,
            double eventRiskPenalty, boolean selectionQualified);

    double scoreSelectionComposite(double baseSelectionScore, double trendPersistenceScore, double sectorScore,
            double newsRiskScore);

    double scoreBuyPointComposite(double baseBuyPointScore, double structureScore, double trendPersistenceScore,
            double riskRewardScore, double sectorScore, double newsScore, double newsRiskScore);

    double stopAtrMultiplier();

    double trailingAtrMultiplier();

    double buyPointThreshold();

    double likelySelectionThreshold();

    double likelyMinFinancialQualityScore();
}
