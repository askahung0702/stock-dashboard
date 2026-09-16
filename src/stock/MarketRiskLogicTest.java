package stock;

public class MarketRiskLogicTest {

    public static void main(String[] args) {
        MarketRiskLogicTest test = new MarketRiskLogicTest();
        test.panicAdviceUsesRiskOffSemanticsAndVolatilityAdjustedStop();
        test.weaknessScoreDoesNotDoubleCountAboveAndBelowMa20ToOneHundred();
        test.marketIndexCarriesItsOwnDataDate();
        System.out.println("MarketRiskLogicTest: 3 checks passed");
    }

    private void panicAdviceUsesRiskOffSemanticsAndVolatilityAdjustedStop() {
        MarketAdvisorReport report = new MarketStrategyAdvisor().advise(MarketRegime.PANIC_SELLOFF, weakBreadth(),
                weakIndex(), null);

        require(report.getExposureMinPct() == 0, "panic exposure minimum must be zero");
        require(report.getExposureMaxPct() == 20, "panic exposure maximum must be twenty");
        require(report.getAtrMultiplier() >= 2D, "panic ATR reference must not be tightened");
        require(!report.getPreferredTabs().contains("🏆 highWinMode"), "panic mode must not recommend highWinMode");
        require(report.getRiskGuidance().contains("縮小股數"), "panic guidance must reduce position size");
    }

    private void weaknessScoreDoesNotDoubleCountAboveAndBelowMa20ToOneHundred() {
        MarketReversalSignal signal = new MarketReversalAnalyzer().analyze(MarketRegime.PANIC_SELLOFF,
                weakBreadth(), weakIndex());

        require(signal.getScore() >= 75D, "weak market must remain high risk");
        require(signal.getScore() < 100D, "complementary MA20 metrics must not force a perfect score");
        require(signal.getLabel().contains("趨勢惡化"), "score must be labeled as deterioration, not probability");
    }

    private void marketIndexCarriesItsOwnDataDate() {
        require("20260915".equals(weakIndex().getDataDate()), "market index date must be retained");
    }

    private MarketBreadthSnapshot weakBreadth() {
        return new MarketBreadthSnapshot(1968, 207, 790, 74, 407, 380, 1561, 0, 311, 28, 750, 3,
                0.26D, 20.7D, 19.3D, 79.3D, 0D, 15.8D, 1.4D, 38.1D, 55D, 60D);
    }

    private MarketIndexSnapshot weakIndex() {
        return new MarketIndexSnapshot(true, "^TWII", "加權指數", "test", "", "20260915", 45511.5D,
                46035.7D, 45173.6D, 0.4D, 0.8D, 377.5D, 510.4D, -132.9D, 158.3D, false, 1.6D,
                2.3D, "區間整理", "價漲量縮");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
