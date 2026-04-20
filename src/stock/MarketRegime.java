package stock;

public enum MarketRegime {
    BULL_TREND,
    RANGE_BOUND,
    BEAR_CORRECTION,
    PANIC_SELLOFF;

    public String getLabel() {
        switch (this) {
        case BULL_TREND:
            return "多頭趨勢";
        case RANGE_BOUND:
            return "區間整理";
        case BEAR_CORRECTION:
            return "空頭修正";
        case PANIC_SELLOFF:
            return "恐慌殺盤";
        default:
            return name();
        }
    }
}
