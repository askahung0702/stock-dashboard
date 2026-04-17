package stock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import stock.common.HtmlUtil;
import stock.vo.YahooStockIncomeVO;


public class StockEarningHiStock {

	public static String incomeFirst="<tdclass=date>";
	public static String incomeStr1="<tdclass=b-b>";
    public static String incomeStr2="</td>";
    
	public static String profitFirst="<tdclass=date>";
	public static String profitStr1="<td>";
    public static String profitStr2="</td>";    
    
    public static String incomeFirstForMonth="";
    public static String incomeFirstForSeason="";
    public static String stockId = "";
    
    public static String url = "http://histock.tw/stock/financial.aspx?no=#stockNum#";
    public static String urlSt3 = "http://histock.tw/stock/financial.aspx?no=#stockNum#&t=5&st=3";
    
    public static YahooStockIncomeVO vo = new YahooStockIncomeVO();
    
    public static HtmlUtil htmlUtil = new HtmlUtil();
    public static StockFilter stockFilter = new StockFilter();
    
		
	public static List<?> getStockInfo(List<String> stockList,int monthCount,int seasonCount ) throws Exception{
		  String stockHtml ="";
		  String stockHtmlSt3 ="";
		  
		  List<Float> toYearIncome = new ArrayList<Float>();
		  List<Float> lastYearIncome = new ArrayList<Float>();
		  List<Float> netProfit = new ArrayList<Float>();
		  List<Float> realProfit = new ArrayList<Float>();
		  List<YahooStockIncomeVO> stockEarningList = new ArrayList<YahooStockIncomeVO>();
		  
		  
			  for (Iterator<String> iter = stockList.iterator(); iter.hasNext(); ) {
				  stockId = iter.next();
				  //System.out.println(stockId);
				  stockHtml = HtmlUtil.parserHtml(url.replace("#stockNum#", stockId));				  
				  stockHtml = HtmlUtil.despace(stockHtml);

				  stockHtmlSt3 = HtmlUtil.parserHtml(urlSt3.replace("#stockNum#", stockId));				  
				  stockHtmlSt3 = HtmlUtil.despace(stockHtml);				  
				  
				  vo = new YahooStockIncomeVO() ;
				  
				  
				  if(!stockHtml.equals("error") && !stockHtmlSt3.equals("error")){
					  toYearIncome = new ArrayList<Float>(); 
					  lastYearIncome = new ArrayList<Float>();
					  netProfit = new ArrayList<Float>();
					  realProfit = new ArrayList<Float>();
					  
					  for (int i=1 ; i<=monthCount ;i++){
						  try{
							  toYearIncome.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtml,incomeFirst,i,incomeStr1,2,incomeStr2), "%"));
						  }catch (Exception e) {
							// TODO: handle exception
							  System.out.println(stockId+" 發生錯誤沒有執行");
						  }
					  }
					  
					  for (int j=1 ; j<=seasonCount ;j++){
						  try{
							  netProfit.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtmlSt3,profitFirst,j,profitStr1,2,profitStr2), "%"));
						  }catch (Exception e) {
							// TODO: handle exception
							  System.out.println(stockId+" 發生錯誤沒有執行");
						  }
					  }					  

					  vo.setStockId(stockId);
					  vo.setToYearIncome(toYearIncome);
					  vo.setLastYearIncome(lastYearIncome);
					  vo.setNetProfit(netProfit);
					  vo.setRealProfit(realProfit);
					  
					  stockEarningList.add(vo);
				  }
			  }
		return stockEarningList;

	  }
	  
	  
}
