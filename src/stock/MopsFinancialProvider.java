package stock;

import stock.vo.TaiwanStockVO;

public class MopsFinancialProvider implements FinancialDataProvider {

    public String providerName() {
        return "MOPS";
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("stock.mops.financial.enabled", "false"));
    }

    public FinancialDataBundle fetch(TaiwanStockVO stock) throws Exception {
        return FinancialDataBundle.empty(providerName());
    }
}
