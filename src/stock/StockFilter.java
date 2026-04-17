package stock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import stock.common.FilterUtil;
import stock.vo.YahooStockIncomeVO;

public class StockFilter {
	public static FilterUtil filterUtil = new FilterUtil();
	

	public static List<String> toYearIncomeFilter(List<YahooStockIncomeVO> yahooStockIncomeList,String symbol,int months,int seasons) throws Exception{
		List<String> stockList = new ArrayList<String>();
		
		YahooStockIncomeVO yahooStockIncomeVO = new YahooStockIncomeVO();
		
		for (Iterator<YahooStockIncomeVO> iter = yahooStockIncomeList.iterator(); iter.hasNext(); ) {
			yahooStockIncomeVO = iter.next();
			Boolean showStock = true;			
			if(showStock && FilterUtil.compareForBoolean(yahooStockIncomeVO.getToYearIncome(),symbol,months,10)){
				showStock = true;
			}else{
				showStock = false;
			}

			if(showStock && FilterUtil.compareForBoolean(yahooStockIncomeVO.getNetProfit(),symbol,seasons,10)){
				showStock = true;
			}else{
				showStock = false;
			}			
			if(showStock){
				System.out.println(yahooStockIncomeVO.getStockId());
				stockList.add(yahooStockIncomeVO.getStockId());
			}
		}
		
		
		return stockList;
	}	
	}
