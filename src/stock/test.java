package stock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import stock.common.ExcelController;
import stock.common.HtmlUtil;


public class test {
	public static HtmlUtil htmlUtil = new HtmlUtil();
	public static ExcelController excelController = new ExcelController();
	private static XSSFWorkbook workbook;
	
    public static void main(String[] args) throws Exception {
    	JSONParser parser = new JSONParser();
        try
        {
            FileInputStream file = new FileInputStream(new File("D://EB ERROR_MASTER_20160606.xlsx"));
            workbook = new XSSFWorkbook(file);
            XSSFSheet sheet = workbook.getSheetAt(3);
            Iterator<Row> rowIterator = sheet.iterator();
            List<String> textList = new ArrayList<String>();
            while (rowIterator.hasNext()) 
            {
                Row row = rowIterator.next();
                Iterator<Cell> cellIterator = row.cellIterator();
                
                while (cellIterator.hasNext()) 
                {
                    Cell cell = cellIterator.next();
                    switch (cell.getCellType()) 
                    {
                        case Cell.CELL_TYPE_NUMERIC:
                            System.out.print(cell.getNumericCellValue() + "t");
                            Object obj = parser.parse(new FileReader(HtmlUtil.parserHtmlForJson("https://translate.yandex.net/api/v1.5/tr.json/translate?key=trnsl.1.1.20160620T052531Z.6407a2236456b7ba.01c15242eed3fc628bbca73973d0fa6dae015186&lang=zh&text="+cell.getNumericCellValue())));
                            JSONObject jsonObject = (JSONObject) obj;
                    		String text = (String) jsonObject.get("text");
                    		System.out.println(","+text);

                            break;
                        case Cell.CELL_TYPE_STRING:
                            JSONObject jsonObjectS = (JSONObject) parser.parse(HtmlUtil.parserHtmlForJson("https://translate.yandex.net/api/v1.5/tr.json/translate?key=trnsl.1.1.20160620T052531Z.6407a2236456b7ba.01c15242eed3fc628bbca73973d0fa6dae015186&lang=zh&text="+cell.getStringCellValue()));
                    		textList.add(cell.getStringCellValue()+","+jsonObjectS.get("text").toString());
                            break;

                    }
                }
            }
            file.close();

            csvWriter("D:/aaa.csv",textList);
        } 
        catch (Exception e) 
        {
        	
            e.printStackTrace();
        }
    }
	 public static void csvWriter(String fileName,List<String> textList) throws IOException{
		 FileWriter fileWriter = null;

	     try {
	            fileWriter = new FileWriter(fileName);
	            for (String list : textList) {	            	
	                fileWriter.append(list);
	                fileWriter.append("\n");
	            }
	            System.out.println("CSV file was created successfully !!!");
	        } catch (Exception e) {
	            System.out.println("Error in CsvFileWriter !!!");
	            e.printStackTrace();
	        } finally {
	            try {
	                fileWriter.flush();
	                fileWriter.close();
	            } catch (IOException e) {
	                System.out.println("Error while flushing/closing fileWriter !!!");
	                e.printStackTrace();
	            }
	        }
	 }    
}
