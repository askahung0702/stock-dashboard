package stock.vo;

public class ShareholderConcentrationVO {

    private final String dataDate;
    private final String code;
    private long holders100To1000Lots;
    private long shares100To1000Lots;
    private double ratio100To1000Lots;
    private long holdersOver1000Lots;
    private long sharesOver1000Lots;
    private double ratioOver1000Lots;
    private long holders100To1000LotsDelta;
    private long holdersOver1000LotsDelta;
    private double ratio100To1000LotsDelta;
    private double ratioOver1000LotsDelta;

    public ShareholderConcentrationVO(String dataDate, String code) {
        this.dataDate = dataDate == null ? "" : dataDate;
        this.code = code == null ? "" : code;
    }

    public String getDataDate() {
        return dataDate;
    }

    public String getCode() {
        return code;
    }

    public long getHolders100To1000Lots() {
        return holders100To1000Lots;
    }

    public void add100To1000Lots(long holders, long shares, double ratioPercent) {
        holders100To1000Lots += holders;
        shares100To1000Lots += shares;
        ratio100To1000Lots += ratioPercent;
    }

    public long getShares100To1000Lots() {
        return shares100To1000Lots;
    }

    public double getRatio100To1000Lots() {
        return ratio100To1000Lots;
    }

    public long getHoldersOver1000Lots() {
        return holdersOver1000Lots;
    }

    public void addOver1000Lots(long holders, long shares, double ratioPercent) {
        holdersOver1000Lots += holders;
        sharesOver1000Lots += shares;
        ratioOver1000Lots += ratioPercent;
    }

    public long getSharesOver1000Lots() {
        return sharesOver1000Lots;
    }

    public double getRatioOver1000Lots() {
        return ratioOver1000Lots;
    }

    public long getHolders100To1000LotsDelta() {
        return holders100To1000LotsDelta;
    }

    public void setHolders100To1000LotsDelta(long holders100To1000LotsDelta) {
        this.holders100To1000LotsDelta = holders100To1000LotsDelta;
    }

    public long getHoldersOver1000LotsDelta() {
        return holdersOver1000LotsDelta;
    }

    public void setHoldersOver1000LotsDelta(long holdersOver1000LotsDelta) {
        this.holdersOver1000LotsDelta = holdersOver1000LotsDelta;
    }

    public double getRatio100To1000LotsDelta() {
        return ratio100To1000LotsDelta;
    }

    public void setRatio100To1000LotsDelta(double ratio100To1000LotsDelta) {
        this.ratio100To1000LotsDelta = ratio100To1000LotsDelta;
    }

    public double getRatioOver1000LotsDelta() {
        return ratioOver1000LotsDelta;
    }

    public void setRatioOver1000LotsDelta(double ratioOver1000LotsDelta) {
        this.ratioOver1000LotsDelta = ratioOver1000LotsDelta;
    }
}
