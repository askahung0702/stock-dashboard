package stock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import stock.common.HtmlUtil;
import stock.vo.YahooStockIncomeVO;


public class StockEarning {

	public static String incomeFirst="width=3%>#month#</td>";
	public static String profitFirst="width=6%>#season#</td>";
    public static String incomeStr1="height=20>1</td>";
    public static String incomeStr2="align=centerwidth=12%>";
    public static String incomeStr3="</td>";
    public static String netProfitStr2="align=centerwidth=16%>";
    public static String netProfitStr3="</td>";
    public static String realProfitStr2="align=centerwidth=22%>";
    public static String realProfitStr3="</td>";
    
    public static String incomeFirstForMonth="";
    public static String incomeFirstForSeason="";
    public static String stockId = "";
    
    public static String url = "http://histock.tw/stock/financial.aspx?no=#stockNum#";
    
    public static YahooStockIncomeVO vo = new YahooStockIncomeVO();
    
    public static HtmlUtil htmlUtil = new HtmlUtil();
    public static StockFilter stockFilter = new StockFilter();
    
		
	public static List<?> getStockInfo(List<String> stockList ) throws Exception{
		  String stockHtml ="";
		  
		  List<Float> toYearIncome = new ArrayList<Float>();
		  List<Float> lastYearIncome = new ArrayList<Float>();
		  List<Float> netProfit = new ArrayList<Float>();
		  List<Float> realProfit = new ArrayList<Float>();
		  List<YahooStockIncomeVO> stockEarningList = new ArrayList<YahooStockIncomeVO>();
		  
		  
			  for (Iterator<String> iter = stockList.iterator(); iter.hasNext(); ) {
				  stockId = iter.next();
				  System.out.println("stockId="+stockId);
				  System.out.println("URL="+url.replace("#stockNum#", stockId));
				  stockHtml = HtmlUtil.parserHtml(url.replace("#stockNum#", stockId));
				  
				  stockHtml = HtmlUtil.despace(stockHtml);
				  System.out.println(stockHtml);
				  vo = new YahooStockIncomeVO() ;
				  
				  
				  if(!stockHtml.equals("error")){
					  toYearIncome = new ArrayList<Float>(); 
					  lastYearIncome = new ArrayList<Float>();
					  netProfit = new ArrayList<Float>();
					  realProfit = new ArrayList<Float>();
					  
					  for (int i=1 ; i<=12 ;i++){
						  incomeFirstForMonth=incomeFirst.replace("#month#", String.valueOf(i));
						  toYearIncome.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtml,incomeFirstForMonth,2,incomeStr2,1,incomeStr3), "%"));
						  lastYearIncome.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtml,incomeFirstForMonth,1,incomeStr2,1,incomeStr3),"%"));
					  }
					  
					  for (int i=1 ; i<=4 ;i++){
						  incomeFirstForSeason=profitFirst.replace("#season#", String.valueOf(i));
						  netProfit.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtml,incomeFirstForSeason,2,netProfitStr2,1,netProfitStr3),"%"));
						  realProfit.add(HtmlUtil.numeralization(HtmlUtil.pageSplit(stockHtml,incomeFirstForSeason,2,realProfitStr2,1,netProfitStr3),","));
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
