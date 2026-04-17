package stock.vo;

public class EpsRecordVO {

    private final String period;
    private final double eps;
    private final double quarterOverQuarterPct;
    private final double yearOverYearPct;
    private final double averagePrice;

    public EpsRecordVO(String period, double eps, double quarterOverQuarterPct, double yearOverYearPct,
            double averagePrice) {
        this.period = period;
        this.eps = eps;
        this.quarterOverQuarterPct = quarterOverQuarterPct;
        this.yearOverYearPct = yearOverYearPct;
        this.averagePrice = averagePrice;
    }

    public String getPeriod() {
        return period;
    }

    public double getEps() {
        return eps;
    }

    public double getQuarterOverQuarterPct() {
        return quarterOverQuarterPct;
    }

    public double getYearOverYearPct() {
        return yearOverYearPct;
    }

    public double getAveragePrice() {
        return averagePrice;
    }
}
