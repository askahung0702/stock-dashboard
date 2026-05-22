package stock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketStrategyAdvisor {

    public MarketAdvisorReport advise(MarketRegime regime, MarketBreadthSnapshot breadth, MarketIndexSnapshot index) {
        return advise(regime, breadth, index, null);
    }

    public MarketAdvisorReport advise(MarketRegime regime, MarketBreadthSnapshot breadth, MarketIndexSnapshot index,
            MarketLiquiditySnapshot liquidity) {
        int exposureMin;
        int exposureMax;
        String summary;
        String exposureGuidance;
        List<String> preferredTabs;
        List<String> avoidTabs;
        String strategyGuidance;
        double atrMultiplier;
        String riskGuidance;
        List<String> alerts = new ArrayList<String>();

        switch (regime) {
        case BULL_TREND:
            exposureMin = 80;
            exposureMax = 100;
            summary = "多頭結構仍健康，可偏進攻並讓獲利續跑。";
            exposureGuidance = "建議總資金水位 80% - 100%，可分批加碼強勢股，但仍避免過熱追價。";
            preferredTabs = Arrays.asList("🚀 催化成長", "🔥 強勢續攻", "🌱 早期起漲");
            avoidTabs = Arrays.asList("僅防禦觀望");
            strategyGuidance = "順風局優先做強勢題材與續攻股，突破成功率高時可用趨勢交易放大利潤。";
            atrMultiplier = 1.75D;
            riskGuidance = "高波動但偏多時，ATR 停損可放寬至 1.75 倍；若 sellSignalLabel 轉成『轉弱出場』，仍要執行減碼。";
            break;
        case BEAR_CORRECTION:
            exposureMin = 10;
            exposureMax = 30;
            summary = "市場進入修正，應先守資金與流動性。";
            exposureGuidance = "建議總資金水位 30% 以下，保留現金，汰弱留強，只保留高把握度部位。";
            preferredTabs = Arrays.asList("🏆 highWinMode", "⭐ 自選清單");
            avoidTabs = Arrays.asList("🚀 催化成長", "🔥 強勢續攻", "🌱 早期起漲");
            strategyGuidance = "暫停突破追價，改做高防禦與錯殺回穩股，沒有明顯優勢就不出手。";
            atrMultiplier = 1.35D;
            riskGuidance = "修正盤將停損收緊到 ATR 1.35 倍；只要跌破動態停利或出現轉弱賣訊，就不宜戀戰。";
            break;
        case PANIC_SELLOFF:
            exposureMin = 0;
            exposureMax = 20;
            summary = "恐慌波動主導盤面，重點是活下來，不是找最低點。";
            exposureGuidance = "建議總資金水位 0% - 20%，以現金為主，若要試單只能極小部位。";
            preferredTabs = Arrays.asList("🏆 highWinMode");
            avoidTabs = Arrays.asList("🚀 催化成長", "🔥 強勢續攻", "🌱 早期起漲", "⚡ 強勢續攻 18/54");
            strategyGuidance = "暫停一切突破策略，等待恐慌退潮與內部結構修復後再回到進攻模式。";
            atrMultiplier = 1.25D;
            riskGuidance = "高波動恐慌期將 ATR 停損收緊至 1.25 倍；若 sellSignalLabel 顯示『轉弱出場』，請無條件執行。";
            break;
        case RANGE_BOUND:
        default:
            exposureMin = 50;
            exposureMax = 60;
            summary = "盤勢偏震盪，宜低接高出，不適合全面追價。";
            exposureGuidance = "建議總資金水位 50% - 60%，以分批布局與區間操作為主。";
            preferredTabs = Arrays.asList("🏆 highWinMode", "🌱 早期起漲", "🌿 早期起漲 18/54");
            avoidTabs = Arrays.asList("全面追突破");
            strategyGuidance = "區間盤重視支撐承接與基本面安全邊際，追高突破容易被甩。";
            atrMultiplier = 1.50D;
            riskGuidance = "震盪盤用 ATR 1.5 倍較平衡，獲利部位可用移動停利保護成果。";
            break;
        }

        if (breadth != null) {
            if (breadth.getBreadthDeteriorationDays() >= 3) {
                alerts.add("跌破 MA20 的個股比例已連續 " + breadth.getBreadthDeteriorationDays() + " 天上升，內部結構正在轉弱。");
            }
            if (breadth.getAdr() < 0.85D) {
                alerts.add("ADR 低於 0.85，代表下跌家數明顯多於上漲家數，盤面承接力不足。");
            }
            if (breadth.getAboveMa20Pct() < 42D) {
                alerts.add("站上 MA20 的個股比例不到 42%，即使指數撐住，也偏向權值股撐盤。");
            }
        }

        if (index != null && index.isAvailable()) {
            if ("價漲量縮".equals(index.getDivergenceLabel())) {
                alerts.add("加權指數呈現價漲量縮，若後續無量，突破延續性可能下降。");
            } else if ("價跌量增".equals(index.getDivergenceLabel())) {
                alerts.add("加權指數價跌量增，空方釋放壓力偏大，應優先控風險。");
            }
            if (index.getMacdHistogram() < 0D && index.getTrendLabel().contains("高檔")) {
                alerts.add("指數位於高檔震盪但 MACD 柱體轉負，需提防由震盪轉向修正。");
            }
        } else {
            alerts.add("VIX / Put-Call Ratio 尚未接入，目前情緒面以量價與市場寬度替代判讀。");
        }

        if (liquidity != null && liquidity.isAvailable()) {
            if ("量能轉強".equals(liquidity.getSignalLabel()) || "健康轉強".equals(liquidity.getSignalLabel())) {
                alerts.add("融資/量能：" + liquidity.getSignalLabel() + "，大盤成交量為近 15 日均量 "
                        + format(liquidity.getMarketVolumeRatio15Day()) + " 倍。");
            } else if ("量增槓桿升溫".equals(liquidity.getSignalLabel())
                    || "量縮融資升溫".equals(liquidity.getSignalLabel())
                    || "量能轉弱".equals(liquidity.getSignalLabel())) {
                alerts.add("融資/量能警示：" + liquidity.getSignalLabel() + "，" + liquidity.getSignalText());
            }
        }

        if (alerts.isEmpty()) {
            alerts.add("目前未見明顯宏觀風險擴散，可依主策略節奏執行。");
        }

        return new MarketAdvisorReport(regime, exposureMin, exposureMax, summary, exposureGuidance, preferredTabs,
                avoidTabs, strategyGuidance, atrMultiplier, riskGuidance, alerts);
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(value));
    }
}
