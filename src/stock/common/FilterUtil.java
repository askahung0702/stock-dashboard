package stock.common;

import java.util.List;

public class FilterUtil {
	
	public static boolean compareForBoolean(List<Float> floatList,String symbol,int count,int val) throws Exception{

		Boolean trueOrFalse = false;

		if(floatList.size() != 0 ){	
			for (int m = 0 ;m<count ;m++ ) {				

				if(floatList.get(m) >= val){
					trueOrFalse = true;
				}else{
					return false;
				}
			}
		}		
		return trueOrFalse;
	}
}
