package stock.vo;

public class BalanceSheetRecordVO {

    private final String period;
    private final long totalAssets;
    private final long totalLiabilities;
    private final long equity;
    private final long currentAssets;
    private final long currentLiabilities;

    public BalanceSheetRecordVO(String period, long totalAssets, long totalLiabilities, long equity, long currentAssets,
            long currentLiabilities) {
        this.period = period;
        this.totalAssets = totalAssets;
        this.totalLiabilities = totalLiabilities;
        this.equity = equity;
        this.currentAssets = currentAssets;
        this.currentLiabilities = currentLiabilities;
    }

    public String getPeriod() {
        return period;
    }

    public long getTotalAssets() {
        return totalAssets;
    }

    public long getTotalLiabilities() {
        return totalLiabilities;
    }

    public long getEquity() {
        return equity;
    }

    public long getCurrentAssets() {
        return currentAssets;
    }

    public long getCurrentLiabilities() {
        return currentLiabilities;
    }
}
