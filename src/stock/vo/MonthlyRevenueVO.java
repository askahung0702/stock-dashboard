package stock.vo;

public class MonthlyRevenueVO {

    private final String period;
    private final long revenue;
    private final double monthOverMonthPct;
    private final long lastYearRevenue;
    private final double yearOverYearPct;
    private final long accumulatedRevenue;
    private final long lastYearAccumulatedRevenue;
    private final double accumulatedYearOverYearPct;

    public MonthlyRevenueVO(String period, long revenue, double monthOverMonthPct, long lastYearRevenue,
            double yearOverYearPct, long accumulatedRevenue, long lastYearAccumulatedRevenue,
            double accumulatedYearOverYearPct) {
        this.period = period;
        this.revenue = revenue;
        this.monthOverMonthPct = monthOverMonthPct;
        this.lastYearRevenue = lastYearRevenue;
        this.yearOverYearPct = yearOverYearPct;
        this.accumulatedRevenue = accumulatedRevenue;
        this.lastYearAccumulatedRevenue = lastYearAccumulatedRevenue;
        this.accumulatedYearOverYearPct = accumulatedYearOverYearPct;
    }

    public String getPeriod() {
        return period;
    }

    public long getRevenue() {
        return revenue;
    }

    public double getMonthOverMonthPct() {
        return monthOverMonthPct;
    }

    public long getLastYearRevenue() {
        return lastYearRevenue;
    }

    public double getYearOverYearPct() {
        return yearOverYearPct;
    }

    public long getAccumulatedRevenue() {
        return accumulatedRevenue;
    }

    public long getLastYearAccumulatedRevenue() {
        return lastYearAccumulatedRevenue;
    }

    public double getAccumulatedYearOverYearPct() {
        return accumulatedYearOverYearPct;
    }
}
