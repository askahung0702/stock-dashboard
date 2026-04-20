package stock;

public class MarketBreadthSnapshot {

    private final int total;
    private final int advancingCount;
    private final int decliningCount;
    private final int unchangedCount;
    private final int aboveMa20Count;
    private final int aboveMa18Count;
    private final int belowMa20Count;
    private final int likelyCount;
    private final int qualifiedCount;
    private final int buyReadyCount;
    private final int scoreUpCount;
    private final int breadthDeteriorationDays;
    private final double adr;
    private final double aboveMa20Pct;
    private final double aboveMa18Pct;
    private final double belowMa20Pct;
    private final double likelyPct;
    private final double qualifiedPct;
    private final double buyReadyPct;
    private final double scoreUpPct;
    private final double averageSelectionScore;
    private final double averageNewsRiskScore;

    public MarketBreadthSnapshot(int total, int advancingCount, int decliningCount, int unchangedCount,
            int aboveMa20Count, int aboveMa18Count, int belowMa20Count, int likelyCount, int qualifiedCount,
            int buyReadyCount, int scoreUpCount, int breadthDeteriorationDays, double adr, double aboveMa20Pct,
            double aboveMa18Pct, double belowMa20Pct, double likelyPct, double qualifiedPct, double buyReadyPct,
            double scoreUpPct, double averageSelectionScore, double averageNewsRiskScore) {
        this.total = total;
        this.advancingCount = advancingCount;
        this.decliningCount = decliningCount;
        this.unchangedCount = unchangedCount;
        this.aboveMa20Count = aboveMa20Count;
        this.aboveMa18Count = aboveMa18Count;
        this.belowMa20Count = belowMa20Count;
        this.likelyCount = likelyCount;
        this.qualifiedCount = qualifiedCount;
        this.buyReadyCount = buyReadyCount;
        this.scoreUpCount = scoreUpCount;
        this.breadthDeteriorationDays = breadthDeteriorationDays;
        this.adr = adr;
        this.aboveMa20Pct = aboveMa20Pct;
        this.aboveMa18Pct = aboveMa18Pct;
        this.belowMa20Pct = belowMa20Pct;
        this.likelyPct = likelyPct;
        this.qualifiedPct = qualifiedPct;
        this.buyReadyPct = buyReadyPct;
        this.scoreUpPct = scoreUpPct;
        this.averageSelectionScore = averageSelectionScore;
        this.averageNewsRiskScore = averageNewsRiskScore;
    }

    public int getTotal() {
        return total;
    }

    public int getAdvancingCount() {
        return advancingCount;
    }

    public int getDecliningCount() {
        return decliningCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public int getAboveMa20Count() {
        return aboveMa20Count;
    }

    public int getAboveMa18Count() {
        return aboveMa18Count;
    }

    public int getBelowMa20Count() {
        return belowMa20Count;
    }

    public int getLikelyCount() {
        return likelyCount;
    }

    public int getQualifiedCount() {
        return qualifiedCount;
    }

    public int getBuyReadyCount() {
        return buyReadyCount;
    }

    public int getScoreUpCount() {
        return scoreUpCount;
    }

    public int getBreadthDeteriorationDays() {
        return breadthDeteriorationDays;
    }

    public double getAdr() {
        return adr;
    }

    public double getAboveMa20Pct() {
        return aboveMa20Pct;
    }

    public double getAboveMa18Pct() {
        return aboveMa18Pct;
    }

    public double getBelowMa20Pct() {
        return belowMa20Pct;
    }

    public double getLikelyPct() {
        return likelyPct;
    }

    public double getQualifiedPct() {
        return qualifiedPct;
    }

    public double getBuyReadyPct() {
        return buyReadyPct;
    }

    public double getScoreUpPct() {
        return scoreUpPct;
    }

    public double getAverageSelectionScore() {
        return averageSelectionScore;
    }

    public double getAverageNewsRiskScore() {
        return averageNewsRiskScore;
    }

    public boolean isBreadthWeakening() {
        return adr < 0.95D || aboveMa20Pct < 48D || breadthDeteriorationDays >= 3;
    }

    public boolean isBreadthHealthy() {
        return adr >= 1.2D && aboveMa20Pct >= 58D && scoreUpPct >= 45D;
    }
}
