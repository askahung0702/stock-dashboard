package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketWeaknessReport {

    private final boolean breadthDivergence;
    private final String breadthDivergenceReason;
    private final boolean momentumBreakdown;
    private final String momentumBreakdownReason;
    private final boolean newLowExpansion;
    private final String newLowExpansionReason;
    private final boolean volatilityExpansion;
    private final String volatilityExpansionReason;
    private final int warningCount;
    private final List<String> alerts;

    public MarketWeaknessReport(boolean breadthDivergence, String breadthDivergenceReason, boolean momentumBreakdown,
            String momentumBreakdownReason, boolean newLowExpansion, String newLowExpansionReason,
            boolean volatilityExpansion, String volatilityExpansionReason, List<String> alerts) {
        this.breadthDivergence = breadthDivergence;
        this.breadthDivergenceReason = breadthDivergenceReason;
        this.momentumBreakdown = momentumBreakdown;
        this.momentumBreakdownReason = momentumBreakdownReason;
        this.newLowExpansion = newLowExpansion;
        this.newLowExpansionReason = newLowExpansionReason;
        this.volatilityExpansion = volatilityExpansion;
        this.volatilityExpansionReason = volatilityExpansionReason;
        this.alerts = alerts == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(alerts));
        int count = 0;
        if (breadthDivergence) {
            count++;
        }
        if (momentumBreakdown) {
            count++;
        }
        if (newLowExpansion) {
            count++;
        }
        if (volatilityExpansion) {
            count++;
        }
        this.warningCount = count;
    }

    public boolean isBreadthDivergence() {
        return breadthDivergence;
    }

    public String getBreadthDivergenceReason() {
        return breadthDivergenceReason;
    }

    public boolean isMomentumBreakdown() {
        return momentumBreakdown;
    }

    public String getMomentumBreakdownReason() {
        return momentumBreakdownReason;
    }

    public boolean isNewLowExpansion() {
        return newLowExpansion;
    }

    public String getNewLowExpansionReason() {
        return newLowExpansionReason;
    }

    public boolean isVolatilityExpansion() {
        return volatilityExpansion;
    }

    public String getVolatilityExpansionReason() {
        return volatilityExpansionReason;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public List<String> getAlerts() {
        return alerts;
    }
}
