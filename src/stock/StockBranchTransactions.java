package stock;

import java.io.IOException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import stock.common.HtmlUtil;
import stock.common.SqlServerConnect;
import stock.vo.StockBranchTransactionsVO;

public class StockBranchTransactions {
    public static HtmlUtil htmlUtil = new HtmlUtil();
    public static StockFilter stockFilter = new StockFilter();
    public static StockBranchTransactionsVO vo = null;
    public static SqlServerConnect sqlConnect = new SqlServerConnect();
    
    public static String stockId = "";
    
    public static String hiStockStr1="<tralign=center>";
    public static String hiStockStr2="<td>";
    public static String hiStockStr3="</td>";
    public static String hiStockStr4="<tdclass=b-b>";
    		
//    public static String url = "http://histock.tw/stock/main.aspx?no=2891&from=20160419&to=20160419";
    public static String url = "http://histock.tw/stock/main.aspx?no=#stockNum#&from=#date1#&to=#date2#";
    
    
	public static List<?> getStockInfo(List<String> stockList,int day ) throws Exception{
		  String stockHtml ="";
		  List<StockBranchTransactionsVO> stockBranchTransactionList = new ArrayList<StockBranchTransactionsVO>();		  
		  
		  for(int d= 1 ; d<= day ;d++){
			  
			  for (Iterator<String> iter = stockList.iterator(); iter.hasNext(); ) {
				stockId = iter.next();
				String urlNew = url.replace("#stockNum#", stockId).replace("#date1#", getDate(d)).replace("#date2#", getDate(d));
				stockHtml = HtmlUtil.parserHtml(urlNew);
				stockHtml = HtmlUtil.despace(stockHtml);
				if(!stockHtml.equals("error")){
					for (int i=1 ; i<=15 ;i++){
						vo = new StockBranchTransactionsVO() ;
						try{ 
							 vo.setStockId(stockId);
							 vo.setStockBranch(HtmlUtil.pageSplit(stockHtml,hiStockStr1,i,hiStockStr2,1,hiStockStr3));
							 vo.setStockBuy(HtmlUtil.pageSplit(stockHtml,hiStockStr1,i,hiStockStr2,2,hiStockStr3).replace(",", "").replace("&nbsp;", "0"));
							 vo.setStockSell(HtmlUtil.pageSplit(stockHtml,hiStockStr1,i,hiStockStr4,2,hiStockStr3).replace(",", "").replace("&nbsp;", "0"));
							 vo.setStockAvgPrice(HtmlUtil.pageSplit(stockHtml,hiStockStr1,i,hiStockStr2,3,hiStockStr3));
							 vo.setStockDate(getDate(d));
							 vo.setStockRank(String.valueOf(i));
							 vo.setStockType("BUY");
							 Statement st =SqlServerConnect.dbConnect("jdbc:sqlserver://172.61.20.211:1433;databaseName=CMS_Analytics", "csm_admin", "1QAZ2wsx");
		
							 st.executeUpdate("INSERT INTO dbo.st VALUES ("+vo.getStockId()+
							          ",'"+vo.getStockBranch()+"',"+vo.getStockBuy()+
							          ","+vo.getStockSell()+",'"+vo.getStockType()+
							          "',"+vo.getStockRank()+","+vo.getStockDate()+
							          ","+vo.getStockAvgPrice()+")");
							 st.close();
						}catch(Exception e){
							System.out.println("insert error :"+stockId);
						}
					 
					}
					
			    }
					  
			  }
		  }
		return stockBranchTransactionList;
	  }
	
	public static String getDate(int day ) throws Exception{
		Calendar cal=Calendar.getInstance();
		if(cal.get(Calendar.DAY_OF_WEEK) == 1 || cal.get(Calendar.DAY_OF_WEEK) == 2){
			cal.add(Calendar.DATE,-day-cal.get(Calendar.DAY_OF_WEEK));
		}else{
			cal.add(Calendar.DATE,-day);
		}	
	    Date d=cal.getTime();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		String dateString = sdf.format(d);
		
		return dateString;
	}	
	
	public static void main(String[] args) throws IOException{


		 
	}	
}
