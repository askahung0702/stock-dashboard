package stock.vo;

public class TaiwanStockVO {

    private final String code;
    private final String name;
    private final String market;
    private final String yahooSuffix;

    public TaiwanStockVO(String code, String name, String market, String yahooSuffix) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.yahooSuffix = yahooSuffix;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getMarket() {
        return market;
    }

    public String getYahooSuffix() {
        return yahooSuffix;
    }

    public String getYahooSymbol() {
        return code + yahooSuffix;
    }
}
