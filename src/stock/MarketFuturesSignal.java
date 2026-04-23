package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketFuturesSignal {

    private final boolean available;
    private final double riskScore;
    private final String label;
    private final String action;
    private final List<String> reasons;

    public MarketFuturesSignal(boolean available, double riskScore, String label, String action, List<String> reasons) {
        this.available = available;
        this.riskScore = riskScore;
        this.label = label;
        this.action = action;
        this.reasons = reasons == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(reasons));
    }

    public boolean isAvailable() { return available; }
    public double getRiskScore() { return riskScore; }
    public String getLabel() { return label; }
    public String getAction() { return action; }
    public List<String> getReasons() { return reasons; }
}
