package stock.vo;

public class ProfileSnapshotVO {

    private final double currentPrice;
    private final String industry;
    private final String marketType;
    private final long capital;
    private final long sharesOutstanding;
    private final double marketCapMillions;
    private final double displayedPe;
    private final double peerAveragePe;
    private final long latestVolumeLots;
    private final double latestTurnoverBillion;
    private final double grossMarginPct;
    private final double operatingMarginPct;
    private final double returnOnAssetsPct;
    private final double returnOnEquityPct;
    private final double bookValue;
    private final String shareholderMeetingDate;
    private final String cashDividendPayoutDate;
    private final String exDividendDate;

    public ProfileSnapshotVO(double currentPrice, String industry, String marketType, long capital, long sharesOutstanding,
            double marketCapMillions, double displayedPe, double peerAveragePe, long latestVolumeLots,
            double latestTurnoverBillion, double grossMarginPct, double operatingMarginPct, double returnOnAssetsPct,
            double returnOnEquityPct, double bookValue, String shareholderMeetingDate, String cashDividendPayoutDate,
            String exDividendDate) {
        this.currentPrice = currentPrice;
        this.industry = industry;
        this.marketType = marketType;
        this.capital = capital;
        this.sharesOutstanding = sharesOutstanding;
        this.marketCapMillions = marketCapMillions;
        this.displayedPe = displayedPe;
        this.peerAveragePe = peerAveragePe;
        this.latestVolumeLots = latestVolumeLots;
        this.latestTurnoverBillion = latestTurnoverBillion;
        this.grossMarginPct = grossMarginPct;
        this.operatingMarginPct = operatingMarginPct;
        this.returnOnAssetsPct = returnOnAssetsPct;
        this.returnOnEquityPct = returnOnEquityPct;
        this.bookValue = bookValue;
        this.shareholderMeetingDate = shareholderMeetingDate;
        this.cashDividendPayoutDate = cashDividendPayoutDate;
        this.exDividendDate = exDividendDate;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public String getIndustry() {
        return industry;
    }

    public String getMarketType() {
        return marketType;
    }

    public long getCapital() {
        return capital;
    }

    public long getSharesOutstanding() {
        return sharesOutstanding;
    }

    public double getMarketCapMillions() {
        return marketCapMillions;
    }

    public double getDisplayedPe() {
        return displayedPe;
    }

    public double getPeerAveragePe() {
        return peerAveragePe;
    }

    public long getLatestVolumeLots() {
        return latestVolumeLots;
    }

    public double getLatestTurnoverBillion() {
        return latestTurnoverBillion;
    }

    public double getGrossMarginPct() {
        return grossMarginPct;
    }

    public double getOperatingMarginPct() {
        return operatingMarginPct;
    }

    public double getReturnOnAssetsPct() {
        return returnOnAssetsPct;
    }

    public double getReturnOnEquityPct() {
        return returnOnEquityPct;
    }

    public double getBookValue() {
        return bookValue;
    }

    public String getShareholderMeetingDate() {
        return shareholderMeetingDate;
    }

    public String getCashDividendPayoutDate() {
        return cashDividendPayoutDate;
    }

    public String getExDividendDate() {
        return exDividendDate;
    }
}
