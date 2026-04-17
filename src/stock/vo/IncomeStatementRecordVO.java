package stock.vo;

public class IncomeStatementRecordVO {

    private final String period;
    private final long revenue;
    private final long grossProfit;
    private final long operatingIncome;
    private final long netIncome;

    public IncomeStatementRecordVO(String period, long revenue, long grossProfit, long operatingIncome, long netIncome) {
        this.period = period;
        this.revenue = revenue;
        this.grossProfit = grossProfit;
        this.operatingIncome = operatingIncome;
        this.netIncome = netIncome;
    }

    public String getPeriod() {
        return period;
    }

    public long getRevenue() {
        return revenue;
    }

    public long getGrossProfit() {
        return grossProfit;
    }

    public long getOperatingIncome() {
        return operatingIncome;
    }

    public long getNetIncome() {
        return netIncome;
    }
}
