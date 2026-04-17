package stock.vo;

import java.util.List;

public class YahooStockIncomeVO {
    /**
     * 股票代號.
     */
    private String stockId;
    /**
     * 今年月營收.
     */
    private List<Float> toYearIncome;    
    /**
     * 去年月營收.
     */
    private List<Float> lastYearIncome;        
    /**
     * 稅後盈餘.
     */
    private List<Float> netProfit;       
    /**
     * 稅 前 盈 餘.
     */
    private List<Float> realProfit;
    
    
    
	public String getStockId() {
		return stockId;
	}
	public void setStockId(String stockId) {
		this.stockId = stockId;
	}
	public List<Float> getToYearIncome() {
		return toYearIncome;
	}
	public void setToYearIncome(List<Float> toYearIncome) {
		this.toYearIncome = toYearIncome;
	}
	public List<Float> getLastYearIncome() {
		return lastYearIncome;
	}
	public void setLastYearIncome(List<Float> lastYearIncome) {
		this.lastYearIncome = lastYearIncome;
	}
	public List<Float> getNetProfit() {
		return netProfit;
	}
	public void setNetProfit(List<Float> netProfit) {
		this.netProfit = netProfit;
	}
	public List<Float> getRealProfit() {
		return realProfit;
	}
	public void setRealProfit(List<Float> realProfit) {
		this.realProfit = realProfit;
	}
    
    
    
}
