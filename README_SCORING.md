# 台股選股系統規格總整理

這份文件整理目前 repo 內真正有在跑的 6 個層次：

- 每日資料流與輸出
- 原始特徵欄位
- 分數與分類邏輯
- 大盤狀態機 / 產業相對化 / ATR 風控 / 回測優化
- 本地「新聞與公司摘要器」
- 前端 tab / tag / 篩選條件

主要程式位置：

- `src/stock/TaiwanStockAnalyzer.java`
- `src/stock/ConfiguredScoringStrategy.java`
- `src/stock/MarketRegimeResolver.java`
- `src/stock/IndustryMetricsSnapshot.java`
- `src/stock/NewsCompanySummarizer.java`
- `src/stock/StockHistoryDatabase.java`
- `src/stock/StockApiRenderer.java`
- `src/stock/StockDashboardWriter.java`
- `config/scoring_profiles.json`
- `scripts/early_breakout_screener.py`
- `web/index.html`

## 1. 每日資料流

`run_stock_analysis.bat` 的核心流程是：

1. 編譯並執行 `stock.StockAnalysis`
2. 後端抓每檔基本面、籌碼、技術、新聞、事件
3. `TaiwanStockAnalyzer` 完成所有打分、分類、風控與摘要
4. 寫出：
   - `history/stock_candidates_YYYYMMDD.csv`
   - `history/stock_history_db.json / sqlite`
   - `web/data/latest.json`
   - `web/data/history.json`
   - dashboard / site 靜態頁資料
5. 再跑：
   - `scripts/early_breakout_screener.py`
   - `scripts/early_breakout_forward_returns.py`
   - `scripts/early_breakout_portfolio_tracker.py`

本次新增的「新聞與公司摘要器」已直接整合在 `TaiwanStockAnalyzer` 內，所以每天正常跑 `bat` 時就會自動產生，不需要額外加一段腳本。

## 2. 原始特徵欄位

### 2.1 基本資料

- `code`
- `name`
- `market`
- `industry`
- `note`

### 2.2 營收

- `latest_revenue_yoy_pct`
- `avg_3m_revenue_yoy_pct`
- `accumulated_revenue_yoy_pct`
- `positive_revenue_months`

### 2.3 籌碼

- `latest_institutional_net_lots`
- `latest_institutional_net_ratio_pct`
- `five_day_institutional_net_lots`
- `five_day_institutional_net_ratio_pct`
- `latest_foreign_net_lots`
- `broker_net_lots`
- `broker_net_ratio_pct`

### 2.4 財報 / 估值

- `trailing_eps`
- `trailing_pe`
- `peer_average_pe`
- `peg`
- `latest_quarter_eps`
- `latest_quarter_eps_yoy_pct`
- `positive_eps_quarters`
- `eps_acceleration_pct`
- `latest_operating_cash_flow`
- `latest_free_cash_flow`
- `positive_operating_cash_flow_quarters`
- `positive_free_cash_flow_quarters`
- `gross_margin_pct`
- `operating_margin_pct`
- `roa_pct`
- `roe_pct`
- `debt_ratio_pct`
- `current_ratio`
- `non_operating_ratio_pct`

### 2.5 技術

- `current_price`
- `ma18 / ma20 / ma54 / ma60 / ma120`
- `return_18d_pct / return_20d_pct / return_54d_pct / return_60d_pct`
- `volume_ratio`
- `avg_lots_20`
- `avg_trade_value_20_billion`
- `volatility_20_pct`
- `atr20`
- `drawdown_from_high60_pct`
- `rsi14`
- `stochastic_k / stochastic_d`

`drawdown_from_high60_pct` 的意義：

- `0%`：幾乎就在最近 60 日高點
- `-5%`：比最近 60 日高點低 5%
- `+1%`：略微突破最近 60 日高點

### 2.6 新聞 / 題材 / 事件

- `news_score`
- `news_risk_score`
- `event_direction`
- `event_confidence`
- `event_freshness_days`
- `event_type_summary`
- `news_summary`
- `news_digest`
- `news_source_summary`
- `news_source_credibility_score`
- `news_freshness_score`
- `news_source_count`
- `news_official_source_count`
- `news_media_source_count`
- `theme_score`
- `primary_theme`
- `theme_tags`

### 2.7 新增的本地摘要欄位

這五個欄位是每天分析最後由 `NewsCompanySummarizer` 產生：

- `company_summary`
- `recent_news_brief`
- `transformation_hint`
- `practical_advice`
- `advice_confidence`

定位是：

- 解釋層
- 人工複核層
- 實際操作建議層

不是第一層硬篩選條件。

## 3. 分數與核心判斷

### 3.1 畫面上的分數是什麼

- 前端 `總分` = `selectionScore`
- 前端 `買點` = `buyPointScore`
- 前端 `信心` = `dataConfidence`
- `legacyScore` 才是舊版 `score`

### 3.2 舊版總分 `legacyScore`

先計算六大子分數：

- `revenueScore`
- `chipsScore`
- `liquidityScore`
- `valuationScore`
- `technicalScore`
- `financialQualityScore`

公式：

```text
rawScore =
  revenueScore
  + chipsScore
  + liquidityScore
  + valuationScore
  + technicalScore
  + financialQualityScore
  - eventRiskPenalty

legacyScore = clamp(rawScore, 0, 100)
```

這層仍保留，但現在主要作為中繼分，不是前端主排序。

### 3.3 `qualityScore`

```text
qualityScore =
  (revenueScore / 30) * 35
  + (financialQualityScore / 20) * 35
  + (valuationScore / 20) * 20
  + (liquidityScore / 15) * 10
```

### 3.4 `momentumScore`

主要由：

- `chipsScore`
- `technicalScore`
- 量比
- 20 日漲幅位置
- RSI / KD

組成。

### 3.5 `selectionQualified`

現在已不是只有兩個硬門檻，而是交給 `ScoringStrategy` 依大盤狀態判斷。

共同底線來自 `config/scoring_profiles.json`：

- `minLiquidityScore = 4`
- `minFinancialQualityScore = 8`
- `healthyVolumeMin = 0.8`
- `healthyVolumeMax = 2.5`
- `minDataConfidence = 65`

目前邏輯：

- `liquidityScore < 4` → 不合格
- `financialQualityScore < 8` → 不合格
- 若 `dataConfidence > 0` 且 `< 65` → 不合格
- 在 `BEAR_CORRECTION / PANIC_SELLOFF` 時，量比過低或過高也更容易失去資格

### 3.6 大盤狀態機 `MarketRegime`

目前四種狀態：

- `BULL_TREND`：多頭趨勢
- `RANGE_BOUND`：區間整理
- `BEAR_CORRECTION`：空頭修正
- `PANIC_SELLOFF`：恐慌殺盤

來源：`MarketRegimeResolver`

主要用這些統計推斷：

- `breadthPct`
- `likelyPct`
- `buyReadyPct`
- `scoreUpPct`
- `avgSelection`
- `avgNewsRisk`

大致規則：

- 寬度很差、分數上升率太低 → `PANIC_SELLOFF`
- 寬度偏弱、平均分偏弱 → `BEAR_CORRECTION`
- 寬度強、Likely 夠多、平均分高 → `BULL_TREND`
- 其餘 → `RANGE_BOUND`

### 3.7 `selectionScore` 的動態權重

`selectionScore` 分成兩段：

#### 3.7.1 `baseSelectionScore`

```text
baseSelectionScore =
  qualityScore * qualityWeight
  + momentumScore * momentumWeight
  + rawNormalized * rawWeight
  - eventRiskPenalty * penaltyMultiplier
  - 資格與量比罰分
```

#### 3.7.2 `selectionScore`

```text
selectionScore =
  baseSelectionScore * compositeBaseWeight
  + trendPersistenceScore * trendWeight
  + sectorScore * sectorWeight
  + constant
  - newsRisk penalty
```

各 regime 權重：

| Regime | quality | momentum | raw | composite base | trend | sector | constant |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `BULL_TREND` | 0.34 | 0.46 | 0.20 | 0.78 | 0.12 | 0.07 | 5 |
| `RANGE_BOUND` | 0.45 | 0.35 | 0.20 | 0.82 | 0.08 | 0.06 | 5 |
| `BEAR_CORRECTION` | 0.56 | 0.22 | 0.22 | 0.86 | 0.07 | 0.05 | 4 |
| `PANIC_SELLOFF` | 0.60 | 0.16 | 0.24 | 0.90 | 0.05 | 0.03 | 2 |

解讀：

- 多頭放大動能權重
- 空頭拉高品質與 raw score 權重
- 越差的盤勢，越不鼓勵太多進攻訊號

### 3.8 `Likely`

`Likely` 也是動態 regime 門檻。

`config/scoring_profiles.json` 目前設定：

| Regime | likely selection threshold | likely financial quality |
| --- | ---: | ---: |
| `BULL_TREND` | 72 | 12 |
| `RANGE_BOUND` | 72 | 12 |
| `BEAR_CORRECTION` | 76 | 13 |
| `PANIC_SELLOFF` | 80 | 14 |

另外仍需：

- `selectionQualified = true`
- `0.8 <= volumeRatio <= 2.5`

### 3.9 產業相對標準化

`IndustryMetricsSnapshot` 會把每檔股票和同產業比較，算 percentile。

目前有：

- `grossMarginIndustryPercentile`
- `operatingMarginIndustryPercentile`
- `roaIndustryPercentile`
- `roeIndustryPercentile`
- `pegIndustryPercentile`
- `relativePeIndustryPercentile`
- `nonOperatingIndustryPercentile`
- `valuationIndustryPercentile`
- `financialQualityIndustryPercentile`

回灌方式：

```text
valuationScore =
  valuationScore * 0.65
  + valuationIndustryPercentile * 0.07

financialQualityScore =
  financialQualityScore * 0.65
  + financialQualityIndustryPercentile * 0.07
```

然後再重算：

- `qualityScore`
- `rawScore`
- `legacyScore`

這層的目的是：

- 不只看絕對數值
- 改成看「同產業相對位置」

### 3.10 `buyPointScore`

先算 `baseBuyPointScore`，再做動態 composite。

```text
buyPointScore =
  baseBuyPointScore * buyCompositeBaseWeight
  + structureScore * buyStructureWeight
  + trendPersistenceScore * buyTrendWeight
  + riskRewardScore * buyRiskRewardWeight
  + sectorScore * buySectorWeight
  + newsScore * buyNewsWeight
  - newsRisk penalty
```

各 regime 權重：

| Regime | base | structure | trend | risk/reward | sector | news | news risk penalty |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `BULL_TREND` | 0.50 | 0.18 | 0.11 | 0.09 | 0.04 | 0.08 | 0.15 |
| `RANGE_BOUND` | 0.54 | 0.16 | 0.10 | 0.08 | 0.04 | 0.08 | 0.18 |
| `BEAR_CORRECTION` | 0.56 | 0.16 | 0.08 | 0.10 | 0.05 | 0.05 | 0.22 |
| `PANIC_SELLOFF` | 0.58 | 0.14 | 0.06 | 0.12 | 0.05 | 0.05 | 0.26 |

### 3.11 `structureScore` / `structureLabel`

常見型態：

- `平台突破`
- `回踩承接`
- `追高風險`
- `結構未完成`
- `整理待確認`

### 3.12 ATR 風控 / `riskRewardScore`

現在 `riskRewardScore` 已不是固定 % 停損，而是會吃：

- `supportPrice`
- `volatility20Pct`
- `atr20`
- regime 的 `stopAtrMultiplier`
- regime 的 `trailingAtrMultiplier`

目前 ATR 倍數：

| Regime | stop ATR | trailing ATR |
| --- | ---: | ---: |
| `BULL_TREND` | 2.0 | 2.8 |
| `RANGE_BOUND` | 1.7 | 2.4 |
| `BEAR_CORRECTION` | 1.45 | 2.1 |
| `PANIC_SELLOFF` | 1.25 | 1.9 |

輸出欄位：

- `suggestedStopPrice`
- `suggestedStopPct`
- `suggestedTrailingStopPrice`
- `suggestedTargetPrice`
- `upsidePotentialPct`
- `riskRewardRatio`
- `reducePositionSize`

### 3.13 `sellSignalScore`

轉弱 / 出場訊號主要看：

- `price < MA20`
- `price <= trailing stop`
- `drawdown_from_high60_pct < -12`
- 停損距離是否過大
- ATR 相對價格是否偏大

輸出：

- `sellSignalScore`
- `sellSignalLabel`

目前標籤：

- `續抱觀察`
- `保守續抱`
- `轉弱出場`

### 3.14 `dataConfidence`

本質是資料完整度，不是勝率。

基礎 40 分，再依資料可用性加分：

- profile：`+15`
- EPS：`+15`
- cash flow：`+10`
- income：`+8`
- balance：`+8`
- broker：`+4`

### 3.15 `turnaroundScore`

由這些子訊號組成：

- `revenueGrowthSignalScore`
- `earningsTurnaroundSignalScore`
- `profitabilityTurnaroundSignalScore`
- `oneOffRiskScore`

輸出常見標籤：

- `高品質翻轉`
- `轉虧為盈`
- `業績翻轉`
- `業績成長`
- `翻轉觀察`
- `一次性轉盈風險`
- `尚未明確`

### 3.16 收盤後分類 `postCloseCategory`

目前主要分類：

- `高勝率候選`
- `短線主攻`
- `波段布局`
- `催化觀察`
- `一般觀察`
- `暫不出手`

並搭配：

- `postCloseAction`
- `postCloseReason`
- `signalType`
- `signalHorizonDays`

## 4. 回測與參數優化

目前系統已接上：

- `StockBacktestReport`
- `BacktestCalibrationService`
- `WalkForwardOptimizationService`

目前每日分析後會輸出：

- `history/backtest_summary.csv`
- `history/walk_forward_optimization.csv`
- `history/scoring_parameter_recommendations.json`

這層用來：

- 檢查既有權重表現
- 做 walk-forward 規則掃描
- 產出推薦門檻組合

## 5. 本地新聞與公司摘要器

### 5.1 目的

這一層不是外部 LLM，也不需額外 API 費。

它會用現成欄位整理：

- `industry`
- `primaryTheme / themeTags`
- `newsDigest / newsSummary`
- `eventDirection / eventTypeSummary`
- `turnaroundLabel / turnaroundReason`
- `buyPointScore / structureLabel / postCloseCategory`
- `dataConfidence / qualityScore / riskRewardScore`

### 5.2 輸出欄位

- `companySummary`
  - 用產業、主題、營收與獲利狀態，整理出這家公司目前在市場上的「定位」
- `recentNewsBrief`
  - 用新聞摘要、事件方向、事件類型，整理出近期最值得看的消息
- `transformationHint`
  - 判斷比較像真正轉型、產品升級，還是單純題材 / 一次性事件
- `practicalAdvice`
  - 根據 `buyPointScore / structure / postClose / sellSignal / marketRegime` 給出實際建議
- `adviceConfidence`
  - 依 `dataConfidence + 結構 + 風險報酬 + 新聞來源品質` 計算

### 5.3 定位

這一層比較適合：

- 顯示給前端看
- 幫助人工複核
- 提升「建議」的可讀性

不建議直接當成第一層硬篩選門檻。

## 6. 前端 tag / tab / 篩選

### 6.1 前端衍生分數

前端仍有：

- `shortScore`
- `midScore`

它們目前主要用在輔助比較，不再是主要 tab。

### 6.2 轉空警示

目前前端追蹤 4 種轉空特徵：

- `structureLabel === 結構未完成`
- `postCloseCategory === 暫不出手`
- `return20DayPct <= -8`
- `price < movingAverage20`

畫面會顯示 `轉空警示 X/4`。

### 6.3 `催化成長`

大致條件：

- `revenueScore >= 20`
- `chipsScore >= 24`
- `selectionQualified = true`
- `selectionScore >= 65`
- `65 <= buyPointScore <= 92`
- 有翻轉 / 成長訊號
- 籌碼有支撐
- 文字理由含催化關鍵字

### 6.4 `早期起漲 / 強勢續攻`

這一組來自 `scripts/early_breakout_screener.py`，不是主 analyzer 本體裡的 `buyPoint` 規則。

#### MA20/60 `早期起漲`

- `avg_3m_revenue_yoy_pct > 5`
- `positive_revenue_months >= 2`
- `ma20 > ma60`
- `return_20d_pct 介於 3% ~ 30%`
- `return_60d_pct > 0`
- `drawdown_from_high60_pct 介於 -25% ~ -2%`
- `broker_net_ratio_pct > 0`

#### MA20/60 `強勢續攻`

- `avg_3m_revenue_yoy_pct > 5`
- `positive_revenue_months >= 2`
- `current_price > ma20 > ma60`
- `return_20d_pct 介於 3% ~ 25%`
- `return_60d_pct > 10`
- `drawdown_from_high60_pct 介於 -6% ~ +1%`
- `broker_net_ratio_pct > 0`

#### MA18/54 版本

邏輯相同，只是改看：

- `ma18 / ma54`
- `return_18d_pct / return_54d_pct`

### 6.5 `highWinMode`

這是目前前端最嚴的高勝率名單。

#### 保留條件

- `selectionQualified = true`
- `dataConfidence >= 75`
- `financialQualityScore >= 12`
- `selectionScore >= 75`
- `buyPointScore >= 78`
- `structureScore >= 70`
- `riskRewardScore >= 45`
- `0.9 <= volumeRatio <= 2.2`
- `drawdown_from_high60_pct 介於 -8% ~ 0%`
- `RSI 介於 50 ~ 68`
- `newsRiskScore <= 60`

#### 淘汰條件

符合任一就排除：

- `selectionQualified = false`
- `financialQualityScore < 12`
- `dataConfidence < 70`
- `newsRiskScore > 65`
- `volumeRatio > 2.8`
- `volumeRatio < 0.8`
- `return_20d_pct > 25`
- `RSI >= 75`
- `structureLabel = 追高風險`
- `price < MA20`
- `drawdown_from_high60_pct < -12`

### 6.6 前端 checkbox

- `爆量≥1.8x`
- `股價≤300元`
- `分數上漲`
- `連續≥2天`
- `轉虧為盈`
- `業績成長`
- `早期起漲`
- `僅目前群組自選`

### 6.7 目前前端 tab

現在首頁 tab 是：

- `全部`
- `🚀 催化成長`
- `🌱 早期起漲`
- `🔥 強勢續攻`
- `🌿 早期起漲 18/54`
- `⚡ 強勢續攻 18/54`
- `🏆 highWinMode`
- `⭐ 自選清單`

注意：

- `比較有可能 / 觀察名單` 仍保留在上方統計卡
- 但已不是 tab
- `短線精選 / 中線精選 / 買點佳` 目前也不是首頁 tab

## 7. 一句話總結

- `selectionScore`：目前主策略分，代表值不值得看
- `buyPointScore`：現在的位置能不能買、好不好買
- `MarketRegime`：大盤是進攻還是防守
- `Industry percentile`：看的是同業相對優勢，不只看絕對值
- `ATR / sellSignal`：把出場與部位風險接回系統
- `NewsCompanySummarizer`：把數字轉成可讀的公司定位、消息重點、轉型判讀與實際建議
- `早期起漲 / 強勢續攻 / highWinMode`：是不同用途的候選池，不應混成同一件事
