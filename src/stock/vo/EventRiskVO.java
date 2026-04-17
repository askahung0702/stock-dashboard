package stock.vo;

public class EventRiskVO {

    private final double penalty;
    private final String reason;

    public EventRiskVO(double penalty, String reason) {
        this.penalty = penalty;
        this.reason = reason;
    }

    public double getPenalty() {
        return penalty;
    }

    public String getReason() {
        return reason;
    }
}
