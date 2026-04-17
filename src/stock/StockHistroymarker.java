package stock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.opencsv.CSVReader;

import stock.common.CsvMarker;
import stock.common.SqlServerConnect;
import stock.common.UrlUtil;

public class StockHistroymarker {

	public static void main(String[] args) throws Exception {
		getHistroyForOneMothe();
		
	}
	
	public static void getHistroy() throws IOException, InterruptedException{
		List<String> stockList = new ArrayList<String>();		
		stockList = CsvMarker.csvReader(); 
		String stockNum ="";
		for (Iterator<String> iter = stockList.iterator(); iter.hasNext(); ) {
			stockNum = iter.next();
			for(int i=1;i<=9;i++){
				try{
					String stockDate = "20170";
					String stockUrl ="http://www.twse.com.tw/exchangeReport/STOCK_DAY?response=csv&date=${date}01&stockNo=${stock}";
					stockUrl = stockUrl.replace("${stock}", stockNum);
					stockDate = stockDate+ String.valueOf(i);
					stockUrl = stockUrl.replace("${date}", stockDate);
					InputStream input = UrlUtil.DownLoadPages(stockUrl);
					System.out.println(stockNum+","+stockDate+","+stockUrl);
					CSVReader reader = new CSVReader(new InputStreamReader(input));
					List<String[]> records = reader.readAll();
					Iterator<String[]> iterator = records.iterator();
					if(iterator.hasNext()){
						iterator.next();
						iterator.next();
					}
					while (iterator.hasNext()) {
						String[] record = iterator.next();
						
						if(record.length >= 10 && !record[3].equals("--")){
				
							Statement st = SqlServerConnect.dbConnect("jdbc:sqlserver://192.168.2.252:1433;databaseName=Notes", "sa", "wanguo");
							System.out.println ("INSERT INTO StockHistroy " + 
					                 " VALUES ('"+stockNum+"', "+record[0].replace("106","2017")+", '"+record[1]+"', '"+record[2]+"', '"+record[3]+"','"+record[4]+"','"+record[5]+"','"+record[6]+"','"+record[8]+"')");
						    st.executeUpdate("INSERT INTO StockHistroy " + 
							                 " VALUES ('"+stockNum+"', '"+record[0].replace("106","2017")+"', '"+record[1].replace(",", "")+"', '"+record[2].replace(",", "")+"', '"+record[3].replace(",", "")+"','"+record[4].replace(",", "")+"','"+record[5].replace(",", "")+"','"+record[6].replace(",", "")+"','"+record[8].replace(",", "")+"')");

						}
					}
				}catch (Exception e) {
					// TODO: handle exception
				}
				Thread.sleep(2000);
			}
			
		}
	}
	
	public static void getHistroyForOneMothe() throws IOException, InterruptedException{
		List<String> stockList = new ArrayList<String>();		
		stockList = CsvMarker.csvReader(); 
		String stockNum ="";
		for (Iterator<String> iter = stockList.iterator(); iter.hasNext(); ) {
			stockNum = iter.next();

				try{
					String stockDate = "201709";
					String stockUrl ="http://www.twse.com.tw/exchangeReport/STOCK_DAY?response=csv&date=${date}01&stockNo=${stock}";
					stockUrl = stockUrl.replace("${stock}", stockNum);
					
					stockUrl = stockUrl.replace("${date}", stockDate);
					InputStream input = UrlUtil.DownLoadPages(stockUrl);
					System.out.println(stockNum+","+stockDate+","+stockUrl);
					CSVReader reader = new CSVReader(new InputStreamReader(input));
					List<String[]> records = reader.readAll();
					Iterator<String[]> iterator = records.iterator();
					if(iterator.hasNext()){
						iterator.next();
						iterator.next();
					}
					while (iterator.hasNext()) {
						String[] record = iterator.next();
						
						if(record.length >= 10 && !record[3].equals("--")){
				
							Statement st = SqlServerConnect.dbConnect("jdbc:sqlserver://192.168.2.252:1433;databaseName=Notes", "sa", "wanguo");
							System.out.println ("INSERT INTO StockHistroy " + 
					                 " VALUES ('"+stockNum+"', "+record[0].replace("106","2017")+", '"+record[1]+"', '"+record[2]+"', '"+record[3]+"','"+record[4]+"','"+record[5]+"','"+record[6]+"','"+record[8]+"')");
						    st.executeUpdate("INSERT INTO StockHistroy " + 
							                 " VALUES ('"+stockNum+"', '"+record[0].replace("106","2017")+"', '"+record[1].replace(",", "")+"', '"+record[2].replace(",", "")+"', '"+record[3].replace(",", "")+"','"+record[4].replace(",", "")+"','"+record[5].replace(",", "")+"','"+record[6].replace(",", "")+"','"+record[8].replace(",", "")+"')");

						}
					}
				}catch (Exception e) {
					// TODO: handle exception
				}
				Thread.sleep(2000);
			}
			
		
	}	
	
	
}
