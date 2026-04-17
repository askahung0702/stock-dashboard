package stock.vo;

public class InstitutionalTradingDailyVO {

    private final String date;
    private final long foreignNetLots;
    private final long trustNetLots;
    private final long dealerNetLots;
    private final long totalNetLots;
    private final double foreignHoldingPct;
    private final double priceChangePct;
    private final long volume;

    public InstitutionalTradingDailyVO(String date, long foreignNetLots, long trustNetLots, long dealerNetLots,
            long totalNetLots, double foreignHoldingPct, double priceChangePct, long volume) {
        this.date = date;
        this.foreignNetLots = foreignNetLots;
        this.trustNetLots = trustNetLots;
        this.dealerNetLots = dealerNetLots;
        this.totalNetLots = totalNetLots;
        this.foreignHoldingPct = foreignHoldingPct;
        this.priceChangePct = priceChangePct;
        this.volume = volume;
    }

    public String getDate() {
        return date;
    }

    public long getForeignNetLots() {
        return foreignNetLots;
    }

    public long getTrustNetLots() {
        return trustNetLots;
    }

    public long getDealerNetLots() {
        return dealerNetLots;
    }

    public long getTotalNetLots() {
        return totalNetLots;
    }

    public double getForeignHoldingPct() {
        return foreignHoldingPct;
    }

    public double getPriceChangePct() {
        return priceChangePct;
    }

    public long getVolume() {
        return volume;
    }
}
