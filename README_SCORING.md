# 台股選股系統規格總整理

更新日期：2026-05-05

這份文件整理目前 repo 內真正有在跑的選股、評分、估值、分階段更新與前端篩選規則。它是工程規格文件，不是投資建議。

主要程式位置：

- `run_stock_analysis.bat`
- `run_stock_1400_intraday.bat`
- `run_stock_1700_close.bat`
- `run_stock_2300_full.bat`
- `src/stock/StockAnalysis.java`
- `src/stock/TaiwanStockAnalyzer.java`
- `src/stock/ConfiguredScoringStrategy.java`
- `src/stock/MarketRegimeResolver.java`
- `src/stock/StockStageExporter.java`
- `src/stock/StockHistoryDatabase.java`
- `src/stock/StockApiRenderer.java`
- `src/stock/StockDashboardWriter.java`
- `src/stock/FinMindFinancialProvider.java`
- `config/scoring_profiles.json`
- `scripts/early_breakout_screener.py`
- `web/index.html`

## 1. 每日資料流

目前系統不是單一批次，而是分階段產出。

### 1.1 14:00 `intraday-close`

入口：`run_stock_1400_intraday.bat`

流程：

1. 執行 `run_stock_analysis.bat intraday-close`
2. 寫入 `daily_stock_raw / daily_stock_analysis` 的 `intraday-close` stage
3. 執行 `run_stock_analysis.bat market-futures`
4. 由 `StockStageExporter` 選擇 `intraday-close` 匯出到前端
5. 跑 `early_breakout_screener.py`
6. 自動推送 site 更新到 GitHub

14:00 模式會設定：

```text
stock.analysis.stageOnly=true
stock.analyzer.perStockPauseMs=150
stock.intraday.deferChips=true
stock.close.deferNews=true
stock.close.deferEventRisk=true
```

重點：

- 14:00 先跑收盤行情初版，不抓完整新聞與事件風險。
- `stock.intraday.deferChips=true` 表示不即時抓 Yahoo 法人/主力頁。
- 若不抓籌碼，系統會沿用前一個有效快照的法人、外資、主力資料，避免籌碼欄位被錯誤歸零。

### 1.2 17:00 `close`

入口：`run_stock_1700_close.bat`

流程：

1. 先跑 `futures-position`
2. 再跑 `run_stock_analysis.bat close`
3. 寫入 `close` stage
4. 匯出正式盤後資料
5. 自動推送 site 更新

17:00 模式會設定：

```text
stock.analysis.stageOnly=true
stock.analyzer.perStockPauseMs=150
stock.close.deferNews=true
stock.close.deferEventRisk=true
```

重點：

- 17:00 會抓盤後籌碼與主力。
- 新聞、事件風險仍延後，避免盤後初版太慢或不穩。

### 1.3 23:00 `full`

入口：`run_stock_2300_full.bat`

流程：

1. 先跑 `futures-position`
2. 再跑 `run_stock_analysis.bat full`
3. 補完整新聞、事件風險、低頻財報資料、回測與報表
4. 匯出前端資料並推送 GitHub

23:00 是每天最完整版本。

### 1.4 stage 匯出優先順序

`StockStageExporter` 依 request mode 選擇資料：

- `intraday-close` / `market-futures`：優先 `intraday-close`，再退回 `close`，再退回 `full`
- `close`：優先 `close`，再退回 `full`
- `full`：優先 `full`，再退回 `close+news-event`
- `news-event`：合併 `close + news-event`
- 手動匯出：優先最完整的 `full`

輸出檔：

- `web/data/latest.json`
- `web/data/history.json`
- `web/data/snapshot_status.json`
- `history_dashboard.html`
- `static/dashboards/stock_dashboard_YYYYMMDD.html`
- `history/stock_history_db.sqlite`

### 1.5 日期判定

`TaiwanStockAnalyzer.currentDateStamp()` 使用台北時間。

若執行時間早於 `05:00`，系統會視為前一個交易日，並且週末會回推到最近的星期五。這是為了避免 23:00 任務跨過午夜後，把前一交易日資料錯寫成隔天日期。

## 2. 原始特徵欄位

### 2.1 基本資料

- `code`
- `name`
- `market`
- `industry`
- `note`
- `sourceUpdatedAt`
- `analysisVersion`
- `snapshotStage`
- `techReady`
- `marketReady`
- `institutionalReady`
- `brokerReady`
- `financialReady`
- `newsReady`

### 2.2 營收

- `latest_revenue_yoy_pct`
- `avg_3m_revenue_yoy_pct`
- `accumulated_revenue_yoy_pct`
- `positive_revenue_months`

資料來源：

- Yahoo
- LowFrequencyDataCache
- FinMind supplement，需有 token
- MOPS provider 目前保留介面，預設未啟用

### 2.3 籌碼

- `latest_institutional_net_lots`
- `latest_institutional_net_ratio_pct`
- `five_day_institutional_net_lots`
- `five_day_institutional_net_ratio_pct`
- `latest_foreign_net_lots`
- `broker_net_lots`
- `broker_net_ratio_pct`

14:00 初版若 deferred chips，會沿用前一個有效籌碼快照。17:00 / 23:00 會重新抓 Yahoo 籌碼資料。

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
- `fair_value_low`
- `fair_value_base`
- `fair_value_high`
- `fair_value_confidence`
- `fair_value_method`
- `fair_value_reason`

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

- `0%`：接近最近 60 日高點
- `-5%`：低於最近 60 日高點 5%
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
- `latest_news_published_hint`
- `news_source_credibility_score`
- `news_freshness_score`
- `news_source_count`
- `news_official_source_count`
- `news_media_source_count`
- `theme_score`
- `primary_theme`
- `theme_tags`
- `theme_reference_score`
- `market_theme_reference_score`

### 2.7 摘要欄位

由 `NewsCompanySummarizer` 產生：

- `company_summary`
- `recent_news_brief`
- `transformation_hint`
- `practical_advice`
- `advice_confidence`

這些欄位是解釋層與人工複核層，不是第一層硬篩選條件。

## 3. 六大基礎分數

### 3.1 `revenueScore`，滿分 30

組成：

- 最新月營收年增，最高 11
- 近 3 月平均營收年增，最高 12
- 累計營收年增，最高 5
- 近 3 月正成長月數，最高 2

### 3.2 `chipsScore`，滿分 30

組成：

- 5 日法人買賣超比率，最高 12
- 最新日法人占比，最高 5
- 外資方向，最高 6
- 主力比率，最高 6
- 主力張數方向，最高 1

### 3.3 `liquidityScore`，滿分 15

組成：

- 20 日平均成交金額，最高 8
- 20 日平均成交張數，最高 4
- 市值，最高 3

### 3.4 `valuationScore`，滿分 20

前提：近四季 EPS 必須大於 0，否則為 0。

組成：

- 相對同業 PE 或絕對 PE，最高 12
- PEG，最高 4
- EPS 加速，最高 4
- 非營業依賴扣分

### 3.5 `technicalScore`，滿分 20

組成：

- 均線多頭排列，最高 9
- MA20 斜率，最高 2
- 20 日 / 60 日報酬，最高 3
- 量比，最高 1
- 接近 60 日高點且有量，最高 3
- RSI14，最高 3，嚴重過熱扣分
- KD，最高 3，超買扣分
- 波動度健康，最高 1

### 3.6 `financialQualityScore`，滿分 20

組成：

- EPS 獲利能力，最高 5
- EPS 加速，最高 3
- 正 EPS 季數，最高 2
- 現金流，最高 4
- 毛利率、營益率、ROA、ROE，最高 4
- 負債比、流動比、非營業依賴，最高 3

## 4. 主分數

### 4.1 前端看到的分數

- 前端 `總分` = `selectionScore`
- 前端 `買點` = `buyPointScore`
- 前端 `資料信心` = `dataConfidence`
- `legacyScore` 才是舊版 `score`

### 4.2 舊版總分 `legacyScore`

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

這層仍保留，但現在主要作為中繼分數。

### 4.3 `qualityScore`

```text
qualityScore =
  (revenueScore / 30) * 35
  + (financialQualityScore / 20) * 35
  + (valuationScore / 20) * 20
  + (liquidityScore / 15) * 10
```

### 4.4 `momentumScore`

主要看：

- `chipsScore`
- `technicalScore`
- `volumeRatio`
- 20 日報酬位置
- RSI / KD

### 4.5 `selectionQualified`

共同底線來自 `config/scoring_profiles.json`：

- `minLiquidityScore = 4`
- `minFinancialQualityScore = 8`
- `healthyVolumeMin = 0.8`
- `healthyVolumeMax = 2.5`
- `minDataConfidence = 65`

規則：

- `liquidityScore < 4` 不合格
- `financialQualityScore < 8` 不合格
- `dataConfidence > 0 且 < 65` 不合格
- 在 `BEAR_CORRECTION` 或 `PANIC_SELLOFF` 時，`volumeRatio < 0.8` 或 `volumeRatio > 3.0` 不合格

## 5. 大盤狀態與動態權重

目前四種 `MarketRegime`：

- `BULL_TREND`
- `RANGE_BOUND`
- `BEAR_CORRECTION`
- `PANIC_SELLOFF`

來源：`MarketRegimeResolver`

主要輸入：

- 市場寬度
- likely 比率
- buy-ready 比率
- 分數上升比率
- 平均 selection score
- 平均 news risk
- 歷史快照變化

### 5.1 `selectionScore`

第一段：

```text
baseSelectionScore =
  qualityScore * qualityWeight
  + momentumScore * momentumWeight
  + rawNormalized * rawWeight
  - eventRiskPenalty * penaltyMultiplier
  - 資格與量比罰分
```

第二段：

```text
selectionScore =
  baseSelectionScore * compositeBaseWeight
  + trendPersistenceScore * trendWeight
  + sectorScore * sectorWeight
  + constant
  - newsRiskPenalty
```

目前權重：

| Regime | quality | momentum | raw | composite base | trend | sector | constant | likely |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `BULL_TREND` | 0.34 | 0.46 | 0.20 | 0.78 | 0.12 | 0.07 | 5 | 78 |
| `RANGE_BOUND` | 0.45 | 0.35 | 0.20 | 0.82 | 0.08 | 0.06 | 5 | 78 |
| `BEAR_CORRECTION` | 0.56 | 0.22 | 0.22 | 0.86 | 0.07 | 0.05 | 4 | 78 |
| `PANIC_SELLOFF` | 0.60 | 0.16 | 0.24 | 0.90 | 0.05 | 0.03 | 2 | 80 |

`newsRiskScore > 60` 時，selection composite 會扣分。

### 5.2 `Likely`

目前 `activeLikelyThreshold()` 由 regime profile 決定：

- 多頭、區間、空頭修正：`selectionScore >= 78`
- 恐慌殺盤：`selectionScore >= 80`

另外仍需：

- `selectionQualified = true`
- `financialQualityScore >= 14`
- 量比在健康區間

### 5.3 `buyPointScore`

```text
buyPointScore =
  baseBuyPointScore * buyCompositeBaseWeight
  + structureScore * buyStructureWeight
  + trendPersistenceScore * buyTrendWeight
  + riskRewardScore * buyRiskRewardWeight
  + sectorScore * buySectorWeight
  + newsScore * buyNewsWeight
  - newsRiskPenalty
```

目前權重：

| Regime | base | structure | trend | risk/reward | sector | news | risk penalty |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `BULL_TREND` | 0.50 | 0.18 | 0.11 | 0.09 | 0.04 | 0.08 | 0.15 |
| `RANGE_BOUND` | 0.54 | 0.16 | 0.10 | 0.08 | 0.04 | 0.08 | 0.18 |
| `BEAR_CORRECTION` | 0.56 | 0.16 | 0.08 | 0.10 | 0.05 | 0.05 | 0.22 |
| `PANIC_SELLOFF` | 0.58 | 0.14 | 0.06 | 0.12 | 0.05 | 0.05 | 0.26 |

`buyPointThreshold` 目前各 regime 都是 `82`。

## 6. 估值與合理價

合理價先由 `buildFairValueProfile()` 建立，再由同業比較後處理校準。前端將三個價格視為三情境：

- `fairValueLow`：保守價
- `fairValueBase`：基準合理價
- `fairValueHigh`：樂觀價
- `fairValueConfidence`
- `fairValueMethod`
- `fairValueReason`

### 6.1 估值 EPS

```text
twoQuarterAnnualizedEps = (latestQuarterEps + previousQuarterEps) * 2
fairValueEps = trailingFourQuarterEps * 0.40 + twoQuarterAnnualizedEps * 0.60
```

因此，剛轉盈或單季爆發的股票，合理價會快速上修；近四季 EPS 很低但股價已經大漲的股票，合理價會很低。

### 6.2 估值風格

`resolveFairValueStyle()` 會判斷：

- `growth`：半導體、電子零組件、電腦週邊、光電、通信網路、其他電子，或 EPS / 營收成長強
- `stable`：book value 與 ROE 足夠
- `balanced`：其他

### 6.3 多模型混合

可能使用：

- 同業 PE，target PE clamp 在 `8 到 36`
- PEG 推估，target PE clamp 在 `10 到 40`
- PB / ROE 資產面估值
- 復甦期市場定價，當 EPS 還不足但營收或 EPS 年增轉正時使用

若現金流、負債、非營業依賴等品質風險偏多，系統會再限制 PE 上限：

- 風險項目 2 個以上：PE 上限約 `28` 倍
- 風險項目 3 個以上：PE 上限約 `24` 倍
- 風險項目 5 個以上、負債很高、非營業依賴很高，或營業 / 自由現金流皆為負：PE 上限可降到 `20~24` 倍

### 6.4 品質折價

合理價會把以下風險折價進去：

- `latestOperatingCashFlow < 0`：營業現金流為負
- `latestFreeCashFlow < 0`：自由現金流為負
- `positiveOperatingCashFlowQuarters <= 1`：營業現金流季數偏少
- `positiveFreeCashFlowQuarters <= 1`：自由現金流季數偏少
- `debtRatioPct >= 60`：負債比偏高
- `currentRatio < 1.2`：流動比偏低
- `nonOperatingRatioPct > 25`：非營業依賴偏高

折價會同時影響：

- PE / PEG / PB 估值倍數
- 復甦期市場定價
- 合理價信心分數

### 6.5 大盤折價

大盤折價：

- `RANGE_BOUND`：0.97
- `BEAR_CORRECTION`：0.94
- `PANIC_SELLOFF`：0.88
- `BULL_TREND`：1.00

信心分數會吃：

- 模型數量
- `dataConfidence`
- `financialQualityScore`
- `valuationScore`
- `selectionQualified`
- 非營業依賴
- EPS 年增方向

### 6.6 同業比較校準

`finalizeCompositeScores()` 會在所有股票都分析完成後建立同業估值樣本，讓合理價不只依賴單檔的 Yahoo 同業平均 PE。

同業樣本規則：

- 優先用 `產業 + 題材` 分組，例如 `光電/AUTO:AI`
- 若細分樣本不足 5 檔，退回同產業分組；櫃買產業名稱會移除開頭的 `櫃` 後再歸類
- PE 有效樣本：`3 到 80` 倍，至少 5 檔才啟用
- PB 有效樣本：`0.2 到 10` 倍，至少 5 檔才啟用
- 使用 PE 中位數與 10% 修剪平均建立同業 PE 錨
- 使用 PB 中位數與 10% 修剪平均建立同業 PB 錨，並依個股 ROE 相對同業 ROE 做 `0.75 到 1.25` 倍調整

同業校準會產生：

- 估值 EPS 估值：`fairValueEps * 同業 PE 錨`
- PB / ROE 估值：`bookValue * 同業 PB 錨 * ROE 相對因子`
- 若 PB 樣本足夠，同業錨為 `70% PE 估值 + 30% PB/ROE 估值`
- 同業錨再以 `22% 到 62%` 權重併入原本合理價；當原估值與同業錨差距很大時，權重會提高並限制極端估值
- 若公司品質風險偏高，且 EPS 估值遠高於 PB / ROE 估值，基準價會套用 PB / ROE 天花板
- 成長 / 復甦型股票若原估值與同業錨差距未達極端，校準權重最高約 `32%`，避免剛轉強的 EPS 被同業落後估值過度壓低
- `fairValueMethod` 會加上 `+同業比較`
- `fairValueReason` 會列出同業分組、有效樣本數、PE 中位數、PE 修剪平均、估值 EPS 估值；若 PB 可用，也會列出 PB 中位數與 PB / ROE 估值
- `fairValueReason` 也會列出三情境：保守 / 基準 / 樂觀

### 6.7 績效校準

估值完成後，系統會用既有回測欄位校準 `fairValueConfidence`：

- `expectedReturnScore >= 60`：信心加分
- `expectedReturnScore < 45`：信心扣分
- `winratePriorityScore >= 60`：信心加分
- `winratePriorityScore < 45`：信心扣分
- `maxDrawdownPenalty >= 10`：信心扣分

績效校準不直接改合理價，避免短期回測把基本面估值扭曲；它只調整「這個估值差值目前值不值得相信」。

合理價是基本面估值，不是短線目標價。題材股、籌碼股可能長時間偏離合理價。

## 7. 結構、風控與出場

### 7.1 `structureLabel`

常見標籤：

- `平台突破`
- `回踩承接`
- `追高風險`
- `結構未完成`
- `整理待確認`

### 7.2 ATR 風控

輸出：

- `suggestedStopPrice`
- `suggestedStopPct`
- `suggestedTrailingStopPrice`
- `suggestedTargetPrice`
- `riskRewardRatio`
- `riskRewardScore`
- `reducePositionSize`

ATR 倍數：

| Regime | stop ATR | trailing ATR |
| --- | ---: | ---: |
| `BULL_TREND` | 2.0 | 2.8 |
| `RANGE_BOUND` | 1.7 | 2.4 |
| `BEAR_CORRECTION` | 1.45 | 2.1 |
| `PANIC_SELLOFF` | 1.25 | 1.9 |

### 7.3 `sellSignalScore`

主要看：

- 跌破 MA20 / MA60 / MA120
- 跌破 trailing stop
- 距 60 日高點回落過深
- 20 日趨勢轉弱
- 量價破線
- 籌碼轉弱

輸出標籤：

- `續抱觀察`
- `保守續抱`
- `轉弱出場`

## 8. 翻轉與資料信心

### 8.1 `turnaroundScore`

```text
turnaroundScore =
  revenueGrowthSignalScore * 0.28
  + earningsTurnaroundSignalScore * 0.32
  + profitabilityTurnaroundSignalScore * 0.30
  - oneOffRiskScore * 0.22
  + 12
```

常見標籤：

- `高品質翻轉`
- `轉虧為盈`
- `業績翻轉`
- `業績成長`
- `翻轉觀察`
- `一次性轉盈風險`
- `尚未明確`

### 8.2 `dataConfidence`

資料完整度分，不等於勝率。

基礎 40 分，再加：

- profile：`+15`
- EPS：`+15`
- cash flow：`+10`
- income statement：`+8`
- balance sheet：`+8`
- broker：`+4`

等級：

- `A`：`>= 85`
- `B`：`>= 70`
- `C`：`>= 55`
- `D`：`< 55`

## 9. 收盤後分類

分類由 `applyPostCloseDecisionProfile()` 決定。

目前類別：

- `高勝率候選`
- `短線主攻`
- `波段布局`
- `催化觀察`
- `一般觀察`
- `暫不出手`

對應 action：

| category | action | signal | horizon |
| --- | --- | --- | ---: |
| `高勝率候選` | `優先研究` | `3-5日延續` | 3 |
| `短線主攻` | `隔日觀察` | `隔日延續` | 1 |
| `波段布局` | `可分批布局` | `5-10日波段` | 10 |
| `催化觀察` | `只觀察不追` | `3-5日延續` | 5 |
| `一般觀察` | `放進觀察名單` | `待確認` | 3 |
| `暫不出手` | `暫不出手` | `待確認` | 0 |

### 9.1 主名單硬降級

主名單包含：

- `高勝率候選`
- `短線主攻`
- `波段布局`

若觸發以下條件，會被降級：

- 核心條件不足 `8/9`
- 財報品質低於主名單門檻
- 資料品質 C / D
- 資料信心低於 70
- `追高風險`
- 新聞風險偏高
- 非營業依賴過高
- 高負債且現金流為負
- 量比過低或過熱
- 股價跌破 MA20
- 事件偏負向
- 短線催化不夠新

### 9.2 核心條件 `coreConditionCount`

共 9 項：

1. 20 日漲幅未過熱、量比未過熱、回檔未失控、RSI 未過熱
2. 5 日法人未明顯轉弱或主力比率為正
3. EPS 季數或 EPS 年增轉正
4. 近 3 月營收轉正且正成長月數至少 2
5. MA60 > MA120
6. 60 日報酬為正
7. 最新月營收年增為正
8. MA20 > MA60
9. PE 合理，低於同業 1.15 倍或低於 35 倍

## 10. 起漲與 launch tags

後端會輸出 `launchTags`，前端也會在舊資料缺欄位時 fallback 計算。

目前 tag：

- `起漲前夜`
- `起漲共振`
- `健康回踩`
- `價量未過熱`
- `營收EPS支撐`
- `外資主力轉買`
- `主力先行`
- `外資接棒`
- `法人籌碼未轉弱`
- `強勢續攻`
- `已過熱勿追`

### 10.1 `起漲前夜`

這是用 6197、8064 起漲確認前一天的共同特徵建立，定位類似 `highWinMode` 的可篩選模式。

條件：

- 現價站上 MA20，且 MA20 >= MA60
- `structureLabel = 整理待確認`
- `postCloseAction` 包含 `優先研究`
- `selectionScore >= 70`
- `buyPointScore >= 85`
- `financialQualityScore >= 14`
- `chipsScore >= 18`
- 外資買超
- `return20DayPct` 介於 `3 到 15`
- `return60DayPct >= 20`
- `volumeRatio` 介於 `0.8 到 1.8`
- `RSI14` 介於 `45 到 60`
- `drawdownFromHigh60Pct` 介於 `-16 到 -8`

這個 tag 找的是「起漲前一日樣貌」，不是已經噴出後的追價段。

### 10.2 `起漲共振`

大致條件：

- 現價站上 MA20 / MA60
- 20 日漲幅早期但未過熱
- RSI、量比健康
- 營收與 EPS 有支撐
- 外資、主力或法人籌碼不弱

### 10.3 `已過熱勿追`

任一過熱特徵可能觸發：

- 20 日漲幅 > 30
- RSI >= 75
- 量比 > 2.8
- 幾乎貼近 60 日高點但風險報酬不足

## 11. 早期起漲 screener

`scripts/early_breakout_screener.py` 是另一層研究工具，不等同於 analyzer 的 `buyPointScore`。

### 11.1 MA20/60 早期起漲

主要條件：

- 營收近 3 月年增 > 5
- 近 3 月正成長 >= 2
- MA20 > MA60
- 20 日漲幅介於 3 到 30
- 60 日漲幅 > 0
- 距 60 日高點回檔介於 -25 到 -2
- 主力比率或法人籌碼未明顯轉弱

### 11.2 MA20/60 強勢續攻

主要條件：

- 現價 > MA20 > MA60
- 20 日漲幅介於 3 到 25
- 60 日漲幅 > 10
- 距 60 日高點介於 -6 到 +1
- 營收與籌碼有支撐

### 11.3 MA18/54 版本

邏輯相同，但改看：

- MA18 / MA54
- 18 日 / 54 日報酬

## 12. 前端 tab 與篩選

目前 `web/index.html` 首頁 tab：

- `全部`
- `催化成長`
- `早期起漲`
- `強勢續攻`
- `早期起漲 18/54`
- `強勢續攻 18/54`
- `波段優勢`
- `highWinMode`
- `起漲前夜`
- `轉弱預警`
- `自選清單`

前端 select：

- 市場
- 產業
- 題材
- `全部起漲Tag`

checkbox：

- 爆量 >= 1.8x
- 股價 <= 300
- 分數上漲
- 連續 >= 2 天
- 轉虧為盈
- 業績成長
- 早期起漲
- 僅目前群組自選

### 12.1 `highWinMode`

保留條件：

- `selectionQualified = true`
- 有資料信心欄位
- `dataConfidence >= 75`
- `financialQualityScore >= 12`
- `selectionScore >= 75`
- `buyPointScore >= 78`
- `structureScore >= 70`
- `riskRewardScore >= 45`
- `volumeRatio` 介於 `0.9 到 2.2`
- `drawdownFromHigh60Pct` 介於 `-8 到 0`
- `RSI14` 介於 `50 到 68`
- `newsRiskScore <= 60`

排除條件：

- `financialQualityScore < 12`
- `dataConfidence < 70`
- `newsRiskScore > 65`
- `volumeRatio > 2.8` 或 `< 0.8`
- `return20DayPct > 25`
- `RSI14 >= 75`
- `structureLabel = 追高風險`
- 股價跌破 MA20
- `drawdownFromHigh60Pct < -12`

### 12.2 `波段優勢`

前端 `isStructureEdgeMode()` 條件：

- `selectionQualified = true`
- 非恐慌盤
- 空頭修正時 `qualityScore >= 75`，其他盤勢 `>= 70`
- 空頭修正時 `buyPointScore >= 82`，其他盤勢 `>= 78`
- `selectionScore >= 72`
- `financialQualityScore >= 14`
- `volumeRatio` 介於 `0.8 到 2.5`
- 20 日與 60 日報酬為正
- RSI 未過熱
- `newsRiskScore < 60`
- 非負向事件
- 結構不是 `追高風險` 或 `結構未完成`

### 12.3 `催化成長`

前端 `isCatalystGrowth()` 條件：

- `revenueScore >= 20`
- `chipsScore >= 24`
- `selectionQualified = true`
- `selectionScore >= 65`
- `buyPointScore` 介於 `65 到 92`
- 有翻轉或成長訊號
- 法人、主力或籌碼方向為正
- 新聞、買點理由或分數理由包含催化關鍵字

### 12.4 `轉弱預警`

前端 `calcBearishProfile()` 會把以下訊號加總：

- 賣出訊號偏高
- 跌破 MA20 / MA60 / MA120
- MA20 下彎到 MA60 下方
- MA60 低於 MA120
- 20 日或 60 日趨勢轉弱
- 距 60 日高點回落過深
- RSI、KD 轉弱
- 跌破 MA20 且放量
- 分數快速下修
- 買點分不足
- 5 日法人或主力賣壓
- 新聞風險偏高
- 估值偏貴後轉弱

輸出 stage：

- `剛轉弱`
- `反彈減碼`
- `走空確認`

## 13. 績效驗證

績效驗證由 `scripts/signal_performance_report.py` 產生，資料源優先使用 `history/stock_history_db.sqlite` 的 `daily_stock_analysis`，因此能依照 `trade_date + stage + code` 分開保存與驗證。

納入 stage：

- `intraday-close`：前端標示 `14:00`
- `close`：前端標示 `17:00`
- `full`：前端標示 `23:00`

不納入 `news-event`，避免新聞補跑資料干擾 14:00 / 17:00 / 23:00 的績效口徑。

每日保存輸出：

- `history/signal_snapshot_detail_YYYYMMDD.csv`
  - 每日、每階段、每檔股票一列
  - 保存股價、分數、買點、盤後分類、操作建議、原始 `launchTags`、衍生訊號 tag
- `history/signal_forward_returns_YYYYMMDD.csv`
  - 每個 tag / mode / flag / category / signal 形成一筆訊號事件
  - 追蹤後續 1、3、5、10、20、40 個交易日報酬
  - 同時計算各天期內最大不利回撤
- `history/signal_performance_summary_YYYYMMDD.csv`
  - 依 tag 與 stage 統計事件數、可驗證樣本數、勝率、平均報酬、中位數報酬、平均回撤、最差回撤
  - 另外包含 `全部階段` 合併統計
- `history/signal_performance_by_date_YYYYMMDD.csv`
  - 依日期 + tag + stage 拆開，方便觀察訊號是否只在特定期間有效
- `web/performance/signal_performance_latest.json`
  - 前端績效頁使用的最新資料

目前驗證的訊號來源：

- `launchTags`
  - 例如 `起漲前夜`、`起漲共振`、`健康回踩`、`價量未過熱`、`營收EPS支撐`、`外資主力轉買`、`強勢續攻`、`已過熱勿追`
- 前端模式
  - `highWinMode`
  - `波段優勢`
  - `催化成長`
- 布林旗標
  - `Likely`
  - `觀察門檻`
- 盤後分類
  - `postCloseCategory`
- 訊號型態
  - `signalType`

績效前端：

- `web/performance/performance_latest.html`
- 主頁 `web/index.html` header 會顯示 `績效驗證` 按鈕
- 可用階段、tag 類型、天期、最低樣本數篩選
- 可直接比較 14:00 / 17:00 / 23:00 與 `全部階段`

排程整合：

- `run_stock_analysis.bat full` 會在早期起漲報表與 portfolio tracker 後執行績效驗證
- `run_stock_analysis.bat export-now` 也會重建績效驗證 JSON
- `scripts/auto_git_push.ps1` 已納入 `web/performance`

限制：

- 長天期 20 / 40 日需要足夠歷史交易日才會有樣本
- 目前以既有 snapshot 收盤價做 close-to-close 驗證，尚未加入手續費、證交稅、滑價、部位大小與重疊持倉限制
- `起漲前夜` 是新 tag，樣本數累積前應以「待驗證訊號」看待

## 14. 一句話總結

- `selectionScore`：主策略分，代表值不值得研究
- `buyPointScore`：位置分，代表現在好不好切入
- `fairValueBase`：基本面估值中位，不是短線目標價
- `MarketRegime`：決定權重、防守程度與 ATR 風控
- `postCloseCategory`：盤後操作分類
- `launchTags`：起漲型態與風險提示
- `起漲前夜`：用已驗證樣本萃取的起漲前一日模式
- `highWinMode`：前端最嚴格的高勝率篩選
- `StockStageExporter`：決定前端目前吃 14:00、17:00 還是 23:00 的資料
- `signal_performance_report.py`：把 tag / mode 的真實後續表現量化成績效頁
