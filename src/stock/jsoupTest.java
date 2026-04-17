package stock;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class jsoupTest {

	public static void main(String[] args) throws Exception {
	    Document doc = Jsoup.connect("http://histock.tw/stock/financial.aspx?no=6141&st=2").get();

	    for (Element table : doc.select("table.tb-stock")) {
	        for (Element row : table.select("tr")) {
	            Elements tds = row.select("td");
	            Elements ths = row.select("th");
	            System.out.println(ths.text());
	            	for(int i = 0 ; i<tds.size();i++){
	                  System.out.print(tds.get(i).text() + ":");
	            	}
	            	System.out.println("");
	        }
	    }
	}
	
	
}
