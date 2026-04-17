package stock.vo;

public class BrokerTradingSummaryVO {

    private final String dataDate;
    private final long netLots;
    private final long buyLots;
    private final long sellLots;
    private final double netVolumeRatioPct;

    public BrokerTradingSummaryVO(String dataDate, long netLots, long buyLots, long sellLots,
            double netVolumeRatioPct) {
        this.dataDate = dataDate;
        this.netLots = netLots;
        this.buyLots = buyLots;
        this.sellLots = sellLots;
        this.netVolumeRatioPct = netVolumeRatioPct;
    }

    public String getDataDate() {
        return dataDate;
    }

    public long getNetLots() {
        return netLots;
    }

    public long getBuyLots() {
        return buyLots;
    }

    public long getSellLots() {
        return sellLots;
    }

    public double getNetVolumeRatioPct() {
        return netVolumeRatioPct;
    }
}
