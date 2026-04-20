package stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketAdvisorReport {

    private final MarketRegime regime;
    private final int exposureMinPct;
    private final int exposureMaxPct;
    private final String summary;
    private final String exposureGuidance;
    private final List<String> preferredTabs;
    private final List<String> avoidTabs;
    private final String strategyGuidance;
    private final double atrMultiplier;
    private final String riskGuidance;
    private final List<String> alerts;

    public MarketAdvisorReport(MarketRegime regime, int exposureMinPct, int exposureMaxPct, String summary,
            String exposureGuidance, List<String> preferredTabs, List<String> avoidTabs, String strategyGuidance,
            double atrMultiplier, String riskGuidance, List<String> alerts) {
        this.regime = regime;
        this.exposureMinPct = exposureMinPct;
        this.exposureMaxPct = exposureMaxPct;
        this.summary = summary;
        this.exposureGuidance = exposureGuidance;
        this.preferredTabs = preferredTabs == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(preferredTabs));
        this.avoidTabs = avoidTabs == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(avoidTabs));
        this.strategyGuidance = strategyGuidance;
        this.atrMultiplier = atrMultiplier;
        this.riskGuidance = riskGuidance;
        this.alerts = alerts == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(alerts));
    }

    public MarketRegime getRegime() {
        return regime;
    }

    public int getExposureMinPct() {
        return exposureMinPct;
    }

    public int getExposureMaxPct() {
        return exposureMaxPct;
    }

    public String getSummary() {
        return summary;
    }

    public String getExposureGuidance() {
        return exposureGuidance;
    }

    public List<String> getPreferredTabs() {
        return preferredTabs;
    }

    public List<String> getAvoidTabs() {
        return avoidTabs;
    }

    public String getStrategyGuidance() {
        return strategyGuidance;
    }

    public double getAtrMultiplier() {
        return atrMultiplier;
    }

    public String getRiskGuidance() {
        return riskGuidance;
    }

    public List<String> getAlerts() {
        return alerts;
    }
}
