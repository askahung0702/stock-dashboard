package stock.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.csvreader.CsvReader;

public class CsvMarker {


	 public void createCsv(List<String> stockNum) throws Exception{
		 for (Iterator<String> iter = stockNum.iterator(); iter.hasNext(); ) {
			 System.out.println(iter.next());
		 }
	 }
	 
	 public static List<String> csvReader() throws IOException{
		 CsvReader stock = new CsvReader("C:\\Aska\\ticker-TW.csv");
		 //CsvReader stock = new CsvReader("C:\\AU\\ticker-TW1.csv");
		 List<String> stockList = new ArrayList<String>();
		 while (stock.readRecord())
			{
			 stockList.add(stock.get(0));
			}
		 stock.close();
		 return stockList;
		 
	 }
	 
	 
}
