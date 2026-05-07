package stock.vo;

public class MarginTradingVO {
    private String dataDate = "";
    private long previousMarginBalance;
    private long marginBuy;
    private long marginSell;
    private long marginCashRepay;
    private long marginBalance;
    private long marginLimit;
    private double marginUsagePct;
    private long previousShortBalance;
    private long shortSell;
    private long shortBuy;
    private long shortRepay;
    private long shortBalance;
    private long shortLimit;
    private double shortUsagePct;
    private long offsetLots;
    private String note = "";
    private String source = "";

    public String getDataDate() {
        return dataDate;
    }

    public void setDataDate(String dataDate) {
        this.dataDate = dataDate;
    }

    public long getPreviousMarginBalance() {
        return previousMarginBalance;
    }

    public void setPreviousMarginBalance(long previousMarginBalance) {
        this.previousMarginBalance = previousMarginBalance;
    }

    public long getMarginBuy() {
        return marginBuy;
    }

    public void setMarginBuy(long marginBuy) {
        this.marginBuy = marginBuy;
    }

    public long getMarginSell() {
        return marginSell;
    }

    public void setMarginSell(long marginSell) {
        this.marginSell = marginSell;
    }

    public long getMarginCashRepay() {
        return marginCashRepay;
    }

    public void setMarginCashRepay(long marginCashRepay) {
        this.marginCashRepay = marginCashRepay;
    }

    public long getMarginBalance() {
        return marginBalance;
    }

    public void setMarginBalance(long marginBalance) {
        this.marginBalance = marginBalance;
    }

    public long getMarginLimit() {
        return marginLimit;
    }

    public void setMarginLimit(long marginLimit) {
        this.marginLimit = marginLimit;
    }

    public double getMarginUsagePct() {
        return marginUsagePct;
    }

    public void setMarginUsagePct(double marginUsagePct) {
        this.marginUsagePct = marginUsagePct;
    }

    public long getMarginBalanceDelta() {
        return marginBalance - previousMarginBalance;
    }

    public long getPreviousShortBalance() {
        return previousShortBalance;
    }

    public void setPreviousShortBalance(long previousShortBalance) {
        this.previousShortBalance = previousShortBalance;
    }

    public long getShortSell() {
        return shortSell;
    }

    public void setShortSell(long shortSell) {
        this.shortSell = shortSell;
    }

    public long getShortBuy() {
        return shortBuy;
    }

    public void setShortBuy(long shortBuy) {
        this.shortBuy = shortBuy;
    }

    public long getShortRepay() {
        return shortRepay;
    }

    public void setShortRepay(long shortRepay) {
        this.shortRepay = shortRepay;
    }

    public long getShortBalance() {
        return shortBalance;
    }

    public void setShortBalance(long shortBalance) {
        this.shortBalance = shortBalance;
    }

    public long getShortBalanceDelta() {
        return shortBalance - previousShortBalance;
    }

    public long getShortLimit() {
        return shortLimit;
    }

    public void setShortLimit(long shortLimit) {
        this.shortLimit = shortLimit;
    }

    public double getShortUsagePct() {
        return shortUsagePct;
    }

    public void setShortUsagePct(double shortUsagePct) {
        this.shortUsagePct = shortUsagePct;
    }

    public double getShortMarginRatioPct() {
        return marginBalance == 0L ? 0D : shortBalance * 100D / marginBalance;
    }

    public long getOffsetLots() {
        return offsetLots;
    }

    public void setOffsetLots(long offsetLots) {
        this.offsetLots = offsetLots;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
