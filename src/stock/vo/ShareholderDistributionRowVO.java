package stock.vo;

public class ShareholderDistributionRowVO {

    private final String dataDate;
    private final String code;
    private final int holdingLevel;
    private final long holders;
    private final long shares;
    private final double ratioPercent;

    public ShareholderDistributionRowVO(String dataDate, String code, int holdingLevel, long holders, long shares,
            double ratioPercent) {
        this.dataDate = dataDate == null ? "" : dataDate;
        this.code = code == null ? "" : code;
        this.holdingLevel = holdingLevel;
        this.holders = holders;
        this.shares = shares;
        this.ratioPercent = ratioPercent;
    }

    public String getDataDate() {
        return dataDate;
    }

    public String getCode() {
        return code;
    }

    public int getHoldingLevel() {
        return holdingLevel;
    }

    public long getHolders() {
        return holders;
    }

    public long getShares() {
        return shares;
    }

    public double getRatioPercent() {
        return ratioPercent;
    }
}
