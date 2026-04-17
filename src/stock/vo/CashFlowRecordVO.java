package stock.vo;

public class CashFlowRecordVO {

    private final String period;
    private final long operatingCashFlow;
    private final long freeCashFlow;

    public CashFlowRecordVO(String period, long operatingCashFlow, long freeCashFlow) {
        this.period = period;
        this.operatingCashFlow = operatingCashFlow;
        this.freeCashFlow = freeCashFlow;
    }

    public String getPeriod() {
        return period;
    }

    public long getOperatingCashFlow() {
        return operatingCashFlow;
    }

    public long getFreeCashFlow() {
        return freeCashFlow;
    }
}
