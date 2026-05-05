package stock;

import stock.vo.TaiwanStockVO;

public interface FinancialDataProvider {

    String providerName();

    boolean isEnabled();

    FinancialDataBundle fetch(TaiwanStockVO stock) throws Exception;
}
