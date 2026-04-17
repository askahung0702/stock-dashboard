package stock;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import stock.common.CsvMarker;
import stock.common.HtmlUtil;
import stock.vo.YahooStockIncomeVO;


public class StockIchroRuler {
    public static YahooStockIncomeVO vo = new YahooStockIncomeVO();
    public static HtmlUtil htmlUtil = new HtmlUtil();
    public static StockFilter stockFilter = new StockFilter();
    public static List<String> analysis() throws Exception {
    	/*
    	 * 1.上市公司股價前40名/上櫃股價前30名
    	 * 2.掛牌需2年以上
    	 * 3.過去3年公司營業利益率與每股稅後盈餘不能是負值
    	 * 4.過去6季，單季營業利益率不能大富下滑(超過10%) N比N-1 N-1比N-2
    	 * 5.過去3年自由現金流量為正
    	 * 6.近3個月的營收年增率為正
    	 * 7.過去3年沒有辦過現金增資
    	 * 8.市值30億元以上
    	 * 9.成交量需1000張以上 三十天內平均1000張以上(尚未找到資料來源)
    	 * 
    	 * */
    	//List<String> taiList = getTAIStock();
    	List<String> taiList = CsvMarker.csvReader(); 
    	taiList = checkYearIncome(taiList);
    	taiList = checkCashFlow(taiList);
    	taiList = checkCapital(taiList);    	
    	taiList = checkStockStartDate(taiList);
    	taiList = checkStockIncomeStatement(taiList);
    	taiList = checkStockIncomeRateStatement(taiList);

    	
    	
    	System.out.println("taiList size ="+taiList.size());
    	for(String stock :taiList) {
    		System.out.println(stock);
    	}
    	
		return null;
    	
    }
		
	public static List<String> getTAIStock() throws Exception{

		List<String> stockList = new ArrayList<>();
		Document document = Jsoup.connect("https://tw.stock.yahoo.com/rank/change-down?exchange=TAI").get();
		Elements links = document.getElementsByClass("Fz(14px) C(#979ba7) Ell");
		for(Element stock :links) {
			
			stockList.add(stock.text());
		}
		return stockList;

	}
	  
	
	public static List<String> getTWOStock() throws Exception{

		List<String> stockList = new ArrayList<>();
		Document document = Jsoup.connect("https://tw.stock.yahoo.com/rank/price?exchange=TWO").get();
		Elements links = document.getElementsByClass("Fz(14px) C(#979ba7) Ell");
		
		for(Element stock :links) {
			stockList.add(stock.text());
		}
		return stockList;

	}
	
	public static List<String> checkStockStartDate(List<String> stockList) throws Exception{
		System.out.println("stockList size ="+stockList.size());
		List<String> stockFilterList = new ArrayList<>();
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd");
	   
		for(String stock :stockList) {
			try {
				Document document = Jsoup.connect("https://tw.stock.yahoo.com/quote/"+stock+"/profile").get();
				
				Elements links = document.getElementsByClass("Py(8px) Pstart(12px) Bxz(bb)");
				Date date = df.parse(links.get(6).text());
				long day = new Date().getTime() / 1000 / 3600 / 24 - date.getTime() / 1000 / 3600 / 24;
			    if(day>730) {
			    	stockFilterList.add(stock);
			    }
			} catch (IndexOutOfBoundsException e) {
				// TODO: handle exception
				System.out.println("stock = "+stock + " 找不到資料");
			}

		}
		System.out.println("checkStockStartDate 結束");
		return stockFilterList;

	}	
	
	public static List<String> checkStockIncomeStatement(List<String> stockList) throws Exception{
		System.out.println("stockList size ="+stockList.size());
		List<String> stockFilterList = new ArrayList<>();
		
		for(String stock :stockList) {
			
			try {
				boolean isTrue = true; 
				Document document = Jsoup.connect("https://histock.tw/stock/"+stock.replace(".TW", "")+"/%E6%90%8D%E7%9B%8A%E8%A1%A8").get();
				Elements links = document.getElementById("CPHB1_ctl00_gv").getElementsByTag("tr");
			
				for(int i=1;i<=12;i++) {
					Elements incomeTd = links.get(i).getElementsByTag("td");
					if (incomeTd.get(3).text().contains("-") || incomeTd.get(5).text().contains("-")) {
						isTrue = false;
					}
				}
				if(isTrue) {
			    	stockFilterList.add(stock);
			    }
			} catch (Exception e) {
				// TODO: handle exception
				System.out.println("stock = "+stock + " 找不到資料");
			}			
			

		}
		System.out.println("checkStockIncomeStatement 結束");
		return stockFilterList;

	}	
	
	public static List<String> checkStockIncomeRateStatement(List<String> stockList) throws Exception{
		System.out.println("stockList size ="+stockList.size());
		List<String> stockFilterList = new ArrayList<>();


		for(String stock :stockList) {
			boolean isTrue = true; 
			Document document = Jsoup.connect("https://histock.tw/stock/"+stock.replace(".TW", "")+"/%E6%90%8D%E7%9B%8A%E8%A1%A8").get();
			Elements links = document.getElementById("CPHB1_ctl00_gv").getElementsByTag("tr");
		
			for(int i=1;i<=6;i++) {
				try {
					Elements incomeTd = links.get(i).getElementsByTag("td");
					BigDecimal myLong = new BigDecimal(incomeTd.get(1).text().replace(",", ""));
					BigDecimal myLong1 = new BigDecimal(incomeTd.get(3).text().replace(",", ""));
					BigDecimal c1 = myLong1.divide(myLong,3,BigDecimal.ROUND_DOWN);
				
					Elements incomeTd2 = links.get(i+1).getElementsByTag("td");
					BigDecimal myLong2 = new BigDecimal(incomeTd2.get(1).text().replace(",", ""));
					BigDecimal myLong21 = new BigDecimal(incomeTd2.get(3).text().replace(",", ""));
					BigDecimal c2 = myLong21.divide(myLong2,3,BigDecimal.ROUND_DOWN);
					
					if(c1.subtract(c2).compareTo(new BigDecimal(-10))==-1) {
						isTrue = false;
					}
				} catch (Exception e) {
					// TODO: handle exception
					System.out.println(stock+" 資訊不足跳過");
				}

			}
			if(isTrue) {
				
				stockFilterList.add(stock);
			}
				
		}
		System.out.println("checkStockIncomeRateStatement 結束");
		return stockFilterList;

	}
	
	
	public static List<String> checkCashFlow(List<String> stockList) throws Exception{
		System.out.println("stockList size ="+stockList.size());
		List<String> stockFilterList = new ArrayList<>();
		
		for(String stock :stockList) {
			boolean isTrue = true; 
			Document document = Jsoup.connect("https://histock.tw/stock/"+stock.replace(".TW", "")+"/%E7%8F%BE%E9%87%91%E6%B5%81%E9%87%8F%E5%88%86%E6%9E%90").get();
			
			Elements links = document.getElementById("CPHB1_ctl00_gv").getElementsByTag("tr");
			
			for(int i=1;i<=6;i++) {
				Elements incomeTd = links.get(i).getElementsByTag("td");
				
				if (incomeTd.get(1).text().contains("-") ) {
					isTrue = false;
				}
			}
		    if(isTrue) {
		    	stockFilterList.add(stock);
		    }			
		}
		System.out.println("checkCashFlow 結束");
		return stockFilterList;

	}	
	
	public static List<String> checkYearIncome(List<String> stockList) throws Exception{
		List<String> stockFilterList = new ArrayList<>();
		System.out.println("stockList size ="+stockList.size());
		for(String stock :stockList) {
			
			boolean isTrue = true; 
			try {
				Document document = Jsoup.connect("https://tw.stock.yahoo.com/quote/"+stock.replace(".TW", "")+"/revenue").get();
				Elements links = document.getElementsByClass("Jc(fe) Fw(n)");
				
				for(int i=1;i<=6;i++) {
					if(Float.valueOf(links.get(i).text().replace("%", "")) < 0){
						isTrue = false;
					}
				}
				
			    if(isTrue) {
			    	stockFilterList.add(stock);
			    }
			} catch (Exception e) {
				// TODO: handle exception
			}
			
		}
		System.out.println("checkYearIncome 結束");
		return stockFilterList;

	}		
	
	public static List<String> checkCapital(List<String> stockList) throws Exception{
		List<String> stockFilterList = new ArrayList<>();
		System.out.println("stockList size ="+stockList.size());
		for(String stock :stockList) {
			boolean isTrue = true; 
			try {
				Document document = Jsoup.connect("https://tw.stock.yahoo.com/quote/"+stock+"/profile").get();
				Elements links = document.getElementsByClass("Py(8px) Pstart(12px) Bxz(bb)");
				if(Float.valueOf(links.get(18).text().replace(",", ""))>3000) {
			    	stockFilterList.add(stock);
			    }	
			} catch (Exception e) {
				// TODO: handle exception
			}
		
		}
		System.out.println("checkCapital 結束");
		return stockFilterList;

	}		
	
}
