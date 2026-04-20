package stock;

public class MarketReversalSignal {

    private final double score;
    private final String label;
    private final String reason;
    private final boolean riskRising;

    public MarketReversalSignal(double score, String label, String reason, boolean riskRising) {
        this.score = score;
        this.label = label;
        this.reason = reason;
        this.riskRising = riskRising;
    }

    public double getScore() {
        return score;
    }

    public String getLabel() {
        return label;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRiskRising() {
        return riskRising;
    }
}
