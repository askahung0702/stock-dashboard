package stock;

import java.util.ArrayList;
import java.util.List;

public class MarketFuturesSignalAnalyzer {

    public MarketFuturesSignal analyze(boolean priceAvailable, double futuresChangePct, boolean positionAvailable,
            long foreignOpenInterestNetLots, long foreignTradingNetLots, MarketBreadthSnapshot breadth) {
        if (!priceAvailable && !positionAvailable) {
            List<String> reasons = new ArrayList<String>();
            reasons.add("台指期價格與外資期貨部位尚未更新。");
            return new MarketFuturesSignal(false, 0D, "資料不足", "先依現貨大盤與個股訊號判斷。", reasons);
        }

        double risk = 0D;
        List<String> reasons = new ArrayList<String>();
        boolean breadthWeak = breadth != null && (breadth.isBreadthWeakening() || breadth.getAboveMa20Pct() < 45D
                || breadth.getAdr() < 0.8D);
        boolean breadthHealthy = breadth != null && breadth.getAboveMa20Pct() >= 55D && breadth.getAdr() >= 1D;

        if (priceAvailable) {
            if (futuresChangePct <= -1D) {
                risk += 25D;
                reasons.add("台指期跌幅超過 1%，短線風向偏空。");
            } else if (futuresChangePct <= -0.5D) {
                risk += 15D;
                reasons.add("台指期跌幅超過 0.5%，隔日追高要保守。");
            } else if (futuresChangePct >= 0.5D) {
                risk -= 8D;
                reasons.add("台指期上漲超過 0.5%，短線風向偏多。");
            } else {
                reasons.add("台指期小漲小跌，短線方向未明顯表態。");
            }
        }

        if (positionAvailable) {
            if (foreignOpenInterestNetLots <= -40000L) {
                risk += 30D;
                reasons.add("外資台指期淨空單超過 4 萬口，避險壓力偏高。");
            } else if (foreignOpenInterestNetLots <= -30000L) {
                risk += 20D;
                reasons.add("外資台指期淨空單超過 3 萬口，法人避險偏高。");
            } else if (foreignOpenInterestNetLots <= -20000L) {
                risk += 10D;
                reasons.add("外資台指期維持淨空，需留意大盤壓力。");
            } else if (foreignOpenInterestNetLots >= 5000L) {
                risk -= 10D;
                reasons.add("外資期貨未平倉偏多，避險壓力較低。");
            }

            if (foreignTradingNetLots <= -8000L) {
                risk += 25D;
                reasons.add("外資單日期貨淨賣超超過 8,000 口，避險快速升高。");
            } else if (foreignTradingNetLots <= -4000L) {
                risk += 15D;
                reasons.add("外資單日期貨淨賣超超過 4,000 口。");
            } else if (foreignTradingNetLots >= 4000L) {
                risk -= 8D;
                reasons.add("外資單日期貨偏多，避險部位有下降跡象。");
            }
        }

        if (priceAvailable && futuresChangePct < -0.3D && breadthWeak) {
            risk += 10D;
            reasons.add("台指期轉弱且市場寬度偏弱，屬於風險共振。");
        } else if (priceAvailable && futuresChangePct > 0.3D && breadthHealthy) {
            risk -= 8D;
            reasons.add("台指期偏強且市場寬度健康，短線環境較友善。");
        } else if (priceAvailable && futuresChangePct > 0.3D && breadthWeak) {
            risk += 8D;
            reasons.add("台指期偏強但市場寬度不佳，可能是權值/期貨撐盤。");
        }

        risk = clamp(risk, 0D, 100D);
        String label;
        String action;
        if (risk >= 70D) {
            label = "高風險";
            action = "隔日全面防守，只保留 highWinMode / 自選清單，突破追價暫停。";
        } else if (risk >= 45D) {
            label = "避險升高";
            action = "新倉減半，早期起漲與強勢續攻需更嚴格，只做有承接的標的。";
        } else if (risk <= 20D && priceAvailable && futuresChangePct >= 0D) {
            label = "偏多";
            action = "可維持正常進攻，但仍需搭配大盤寬度與個股買點。";
        } else {
            label = "中性";
            action = "只挑高分與風報合理標的，不因期貨資料單獨放大部位。";
        }
        return new MarketFuturesSignal(true, risk, label, action, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
