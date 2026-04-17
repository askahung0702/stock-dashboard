# 評分與買點 README

這份文件整理目前系統中和選股最相關的 4 個欄位：

- `總分`：前端表格顯示的主分數，實際上是 `selectionScore`
- `買點`：前端表格顯示的買點分數，實際上是 `buyPointScore`
- `信心`：前端表格顯示的資料完整度分數，實際上是 `dataConfidence`
- `買點佳`：前端 tab，用來篩出相對適合追蹤的買點

主要程式位置：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:39)
- [StockApiRenderer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/StockApiRenderer.java:241)
- [web/index.html](/c:/Users/AU/eclipse-workspace/stock/web/index.html:874)

## 1. 畫面上的「總分」到底是什麼

前端表格的 `總分` 不是舊版 `score`，而是新版 `selectionScore`。

API 對應如下：

- `legacyScore` = 舊版傳統總分 `score`
- `score` = 新版主分數 `selectionScore`

對應程式：

- [StockApiRenderer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/StockApiRenderer.java:241)
- [StockApiRenderer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/StockApiRenderer.java:242)

所以現在畫面上看的 `總分`，本質上是「策略分」，不是單純把營收、籌碼、技術加總而已。

## 2. 傳統總分 `legacyScore` 的計算方式

### 2.1 六大子分數

傳統總分先算 6 個子分數：

- `revenueScore`：0 到 30
- `chipsScore`：0 到 30
- `liquidityScore`：0 到 15
- `valuationScore`：0 到 20
- `technicalScore`：0 到 20
- `financialQualityScore`：0 到 20

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1009)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1054)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1096)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1131)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1195)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1267)

### 2.2 傳統總分公式

先算：

參數中文解釋：

- `revenueScore`：營收動能分數，反映月營收年增、近 3 月營收趨勢、累計營收與正成長月數
- `chipsScore`：籌碼分數，反映法人 1 日/5 日買賣超、外資與主力買賣超
- `liquidityScore`：流動性分數，反映成交張數、成交金額與市值可交易性
- `valuationScore`：估值分數，反映本益比、同業本益比、PEG、EPS 加速與非營業依賴
- `technicalScore`：技術分數，反映均線位置、20/60 日漲跌、量比、波動、RSI/KD
- `financialQualityScore`：財報品質分數，反映 EPS、現金流、毛利率、營益率、ROA/ROE、負債與流動比
- `eventRiskPenalty`：事件風險扣分，反映公告關鍵字與近期法說/股東會/除息等事件風險

```text
rawScore =
  revenueScore
  + chipsScore
  + liquidityScore
  + valuationScore
  + technicalScore
  + financialQualityScore
  - eventRiskPenalty
```

再做兩層限制：

```text
rawScore    = clamp(0, 135)
legacyScore = clamp(rawScore, 0, 100)
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:888)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:892)

## 3. 新版主分數 `selectionScore` 的計算方式

這個就是目前畫面上的 `總分`。

### 3.1 品質分 `qualityScore`

參數中文解釋：

- `revenueScore`：營收成長與持續性
- `financialQualityScore`：獲利與現金流品質
- `valuationScore`：估值是否合理
- `liquidityScore`：股票是否夠好進出

```text
qualityScore =
  (revenueScore / 30) * 35
  + (financialQualityScore / 20) * 35
  + (valuationScore / 20) * 20
  + (liquidityScore / 15) * 10
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1345)

### 3.2 動能分 `momentumScore`

先以籌碼和技術做主體，再補量價與 RSI/KD：

參數中文解釋：

- `chipsScore`：法人/外資/主力買盤強弱
- `technicalScore`：價格結構與均線強弱
- `volumeRatio`：當日量能相對近 20 日均量的倍數
- `return20DayPct`：近 20 日漲跌幅
- `rsi14`：14 日 RSI，相對強弱指標
- `stochasticK / stochasticD`：KD 指標，短線動能位置

```text
momentumScore =
  (chipsScore / 30) * 40
  + (technicalScore / 20) * 30
  + 量比加減分
  + 20日漲幅加減分
  + RSI 加減分
  + KD 加分
```

重點規則：

- 量比 `0.8 ~ 2.5` 加 `15`
- 量比 `0.6 ~ 0.8` 加 `8`
- 量比 `2.5 ~ 3.5` 加 `6`
- `20日漲幅 0 ~ 20%` 加 `10`
- `20日漲幅 20 ~ 35%` 加 `6`
- `20日漲幅 < -10%` 扣 `4`
- `RSI 50 ~ 70` 加 `3`
- `RSI 70 ~ 80` 加 `2`
- `RSI >= 80` 扣 `3`
- `K > D` 且 `40 <= K < 80` 加 `2`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1352)

### 3.3 基礎策略分 `baseSelectionScore`

先把傳統總分正規化，再和品質/動能合成：

參數中文解釋：

- `rawScore`：六大傳統因子加總後的原始總分
- `rawNormalized`：把 `rawScore` 轉成 0~100 尺度
- `qualityScore`：偏基本面與估值的綜合品質分
- `momentumScore`：偏籌碼與技術的綜合動能分
- `eventRiskPenalty`：事件風險扣分
- `selectionQualified`：是否通過基本資格門檻
- `volumeRatio`：量比，用來避免太冷或太熱的股票

```text
rawNormalized = rawScore * 100 / 135

baseSelectionScore =
  qualityScore * 0.45
  + momentumScore * 0.35
  + rawNormalized * 0.20
  - eventRiskPenalty * 4
  - 資格與量比罰分
```

額外懲罰：

- 若 `selectionQualified = false`，扣 `12`
- 若量比 `< 0.6`，扣 `6`
- 若量比 `< 0.8`，扣 `2`
- 若量比 `> 3.5`，扣 `6`
- 若量比 `> 2.5`，扣 `3`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1427)

### 3.4 資格條件 `selectionQualified`

系統先要求這兩個基本條件：

```text
selectionQualified =
  liquidityScore >= 4
  AND financialQualityScore >= 8
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:43)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:44)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1706)

### 3.5 趨勢續航分 `trendPersistenceScore`

這是用歷史快照去看「最近幾天是不是持續強」。

它會看：

- 目前分數是否已達 `Watchlist` 或 `Likely`
- 連續幾天都在 `Watchlist(58)` 以上
- 相對昨天的分數變化
- 相對近 3 日平均有沒有變強
- 最近 5 日內有幾天在 `Likely(72)` 以上

參數中文解釋：

- `currentSelectionScore`：這一檔股票今天的基礎策略分
- `consecutiveDays`：連續幾天維持在觀察名單以上
- `previousScore`：上一個快照的策略分
- `recentAverage`：近 3 個快照平均策略分
- `strongDays`：近 5 個快照中有幾天達到 Likely

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:443)

### 3.6 族群分 `sectorScore`

族群分是同產業橫向比較，不是單看個股。

公式：

參數中文解釋：

- `averageSelectionScore`：同產業股票的平均基礎策略分
- `averageQualityScore`：同產業股票的平均品質分
- `averageMomentumScore`：同產業股票的平均動能分
- `breadthPct`：同產業中有多少比例達到 Watchlist 以上
- `strongPct`：同產業中有多少比例達到 Likely
- `qualifiedPct`：同產業中有多少比例通過基本資格門檻
- `sampleWeight`：樣本數修正，避免成分股太少的族群分數失真

```text
rawSectorScore =
  平均基礎策略分 * 0.35
  + 平均品質分 * 0.20
  + 平均動能分 * 0.15
  + Watchlist以上比例 * 0.15
  + Likely比例 * 0.10
  + 達基本資格比例 * 0.05
```

再依樣本數做回歸：

```text
sampleWeight = min(1, count / 6)
sectorScore  = 50 + (rawSectorScore - 50) * sampleWeight
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:535)

### 3.7 題材分 `themeScore`

題材分來自：

- `config/theme_baskets.csv` 的股票代碼配對
- 股票名稱 / 產業文字命中
- 新聞標題與摘要的關鍵字命中

參數中文解釋：

- `primaryTheme`：目前系統判定的主要題材
- `themeTags`：命中的其他相關題材標籤
- `themeScore`：題材命中強度，`50` 通常代表中性、未明確命中

目前定位：

- `themeScore` 只做顯示與參考
- 不直接納入 `selectionScore` 總分
- 原因是題材分類仍在擴充期，容易因題材表不完整而產生誤差

題材配對程式：

- [theme_baskets.csv](/c:/Users/AU/eclipse-workspace/stock/config/theme_baskets.csv:1)
- [ThemeBasketRepository.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/ThemeBasketRepository.java:33)

### 3.8 新聞風險 `newsRiskScore`

新聞風險從 `35` 起算，再加上事件風險和負面關鍵字：

參數中文解釋：

- `eventRiskPenalty`：公告/事件面本來就有的風險扣分
- `negativeHits`：負面新聞關鍵字命中次數
- `cautionHits`：中性偏保守關鍵字命中次數
- `positiveHits`：正向關鍵字命中次數

```text
newsRiskScore =
  35
  + eventRiskPenalty * 8
  + negativeHits * 10
  + cautionHits * 4
  - min(12, positiveHits * 2)
```

如果沒有新聞，仍會保留事件風險的影響。

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1402)

### 3.9 最終主分數 `selectionScore`

參數中文解釋：

- `baseSelectionScore`：還沒加入趨勢/族群/題材前的基礎策略分
- `trendPersistenceScore`：這檔股票最近幾天的延續性
- `sectorScore`：所在產業整體強弱
- `newsRiskScore`：新聞與公告的風險程度

```text
selectionScore =
  baseSelectionScore * 0.82
  + trendPersistenceScore * 0.08
  + sectorScore * 0.06
  + 校正值 5
  - 新聞風險罰分
```

新聞風險罰分：

```text
if newsRiskScore > 60:
  扣 (newsRiskScore - 60) * 0.15
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1446)

### 3.10 新版主分數的門檻

- `Likely`：`selectionScore >= 72`
- `Watchlist`：`58 <= selectionScore < 72`

另外 `Likely` 還要再滿足：

- `financialQualityScore >= 12`
- `selectionQualified = true`
- `0.8 <= volumeRatio <= 2.5`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:39)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:40)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:45)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:46)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:47)
- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:666)

這組門檻是依新版 `selectionScore` 分布重校後的結果，目的是讓名單數量回到比較可用的範圍，而不是沿用舊版 `80 / 65` 的尺度。

## 4. 買點的判斷方式 `buyPointScore`

### 4.1 基礎買點分 `baseBuyPointScore`

先用策略分、動能、品質做骨架：

參數中文解釋：

- `selectionScore`：新版主分數，代表這檔股票值不值得看
- `momentumScore`：動能強弱
- `qualityScore`：基本面品質強弱
- `currentPrice`：現價
- `movingAverage20 / 60 / 120`：20/60/120 日均線
- `return20DayPct`：近 20 日漲跌幅
- `volumeRatio`：量比
- `drawdownFromHigh60Pct`：距離 60 日高點的回檔幅度
- `rsi14`：14 日 RSI
- `stochasticK / stochasticD`：KD 指標
- `eventRiskPenalty`：事件風險扣分
- `selectionQualified`：是否過基本門檻
- `financialQualityScore`：財報品質分，用來避免品質太弱卻技術很熱的股票

```text
baseBuyPointScore =
  selectionScore * 0.25
  + momentumScore * 0.20
  + qualityScore * 0.10
  + 均線/回檔/量比/20日漲幅/RSI/KD 的加減分
  - eventRiskPenalty * 3
  - 資格不符罰分
  - 財報品質不足罰分
```

重點規則：

- 站上 `MA20 / MA60 / MA120` 分別加 `8 / 8 / 4`
- `MA20 > MA60` 加 `6`
- 距 60 日高點：
  - `-10% ~ -3%` 加 `12`
  - `-3% ~ +1%` 加 `8`
  - `-18% ~ -10%` 加 `4`
  - `< -25%` 扣 `6`
- 量比：
  - `0.8 ~ 1.8` 加 `10`
  - `1.8 ~ 2.8` 加 `8`
  - `> 3` 扣 `6`
  - `< 0.6` 扣 `4`
- `20日漲幅 0 ~ 12%` 加 `8`
- `20日漲幅 12 ~ 25%` 加 `4`
- `20日漲幅 > 25%` 扣 `8`
- `RSI 48 ~ 65` 加 `8`
- `RSI 65 ~ 72` 加 `4`
- `RSI >= 78` 扣 `8`
- `K > D` 且 `40 <= K < 80` 加 `5`
- `K > D` 且 `K < 40` 加 `3`
- `K >= 85` 扣 `4`
- `selectionQualified = false` 扣 `15`
- `financialQualityScore < 12` 扣 `8`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1456)

### 4.2 結構分 `structureScore`

它主要判斷現在屬於哪種型態：

參數中文解釋：

- `structureScore`：技術型態是否健康
- `structureLabel`：型態中文標籤，例如平台突破、回踩承接、追高風險
- 判斷核心：均線位置、接近前高程度、量比、20 日漲幅、RSI、KD

- `平台突破`
- `回踩承接`
- `整理待確認`
- `追高風險`
- `結構未完成`

其中：

- `平台突破`：`價 > MA20`、`MA20 > MA60`、距 60 日高點 `>-3% 且 <=1%`
- `回踩承接`：`價 > MA20`、`價 > MA60`、距 60 日高點 `-10% ~ -3%`
- `追高風險`：`20日漲幅 > 25%` 或 `RSI >= 78` 或 `量比 > 3`
- `結構未完成`：`價 < MA20` 或 `距 60 日高點 < -18%`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1545)

### 4.3 風險報酬分 `riskRewardScore`

系統會先推估：

參數中文解釋：

- `supportPrice`：推估支撐價
- `stopLossPrice`：模型建議停損價
- `stopLossPct`：從現價到停損價的風險幅度
- `targetPrice`：模型推估目標價
- `upsidePotentialPct`：現價到目標價的預估報酬空間
- `riskRewardRatio`：報酬風險比，`上檔空間 / 停損風險`
- `riskRewardScore`：把風報比轉成 0~100 分

- `stopLossPrice`
- `stopLossPct`
- `targetPrice`
- `upsidePotentialPct`
- `riskRewardRatio`

再依風報比和停損距離算分。

重點規則：

- `riskRewardRatio >= 3` 加 `60`
- `>= 2` 加 `45`
- `>= 1.5` 加 `30`
- `>= 1` 加 `15`
- 停損距離 `3% ~ 8%` 再加 `20`
- 停損過大 `> 12%` 扣 `15`
- 停損過小 `< 2%` 扣 `8`
- 預估上檔 `>= 10%` 加 `12`
- 預估上檔 `>= 6%` 加 `6`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1620)

### 4.4 新聞分 `newsScore`

新聞分從 `45` 起算，新聞篇數越多、正向關鍵字越多越加分：

參數中文解釋：

- `headlineCount`：抓到的新聞標題/摘要片段數
- `positiveHits`：正向題材/利多字詞命中數
- `cautionHits`：觀望/波動/中性題材字詞命中數
- `negativeHits`：利空/風險/下修字詞命中數

```text
newsScore =
  45
  + min(18, headlineCount * 3.5)
  + min(24, positiveHits * 6)
  + min(8, cautionHits * 2)
  - min(22, negativeHits * 7)
```

若沒有新聞，預設回 `50`。

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1387)

### 4.5 最終買點分 `buyPointScore`

參數中文解釋：

- `baseBuyPointScore`：未加結構/風報/新聞前的基礎買點分
- `structureScore`：型態是否健康
- `trendPersistenceScore`：是否有續航
- `riskRewardScore`：這個位置的賠賺比是否划算
- `sectorScore`：所在族群是否有風
- `newsScore`：新聞熱度是否支持
- `newsRiskScore`：新聞/公告是否在示警

```text
buyPointScore =
  baseBuyPointScore * 0.54
  + structureScore * 0.16
  + trendPersistenceScore * 0.10
  + riskRewardScore * 0.08
  + sectorScore * 0.04
  + newsScore * 0.08
  - 新聞風險罰分
```

新聞風險罰分：

```text
if newsRiskScore > 55:
  扣 (newsRiskScore - 55) * 0.18
```

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1533)

## 5. 買點標籤 `buyPointLabel`

系統目前用以下邏輯給標籤：

### 5.1 不建議追

符合任一：

- `selectionQualified = false`
- 或最後沒有達到更高層條件

### 5.2 時機不錯，但資料信心不足

- `dataConfidence < 65`
- 且 `buyPointScore >= 70`

### 5.3 基本面可，但時機差

符合任一：

- `financialQualityScore < 12` 且 `buyPointScore < 75`
- 或 `selectionScore >= 75` 但買點分仍不足

### 5.4 A級買點

必須同時符合：

- `buyPointScore >= 85`
- `structureScore >= 75`
- `riskRewardScore >= 60`

### 5.5 可觀察，等確認

- `buyPointScore >= 75`

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1975)

## 6. 信心的計算方式 `dataConfidence`

信心分是資料完整度分數，不是勝率分數。

參數中文解釋：

- `hasProfileData`：公司基本資料是否抓到
- `hasEpsData`：EPS 歷史是否抓到
- `hasCashFlowData`：現金流量表是否抓到
- `hasIncomeData`：損益表是否抓到
- `hasBalanceData`：資產負債表是否抓到
- `hasBrokerData`：主力/券商籌碼是否抓到

初始值：

```text
40 分
```

代表核心資料：

- 月營收
- 法人資料
- 技術資料

這些是主流程必抓資料。

再依是否成功抓到補充分數：

- `profile` 成功：`+15`
- `EPS` 成功：`+15`
- `現金流` 成功：`+10`
- `損益表` 成功：`+8`
- `資產負債表` 成功：`+8`
- `主力籌碼` 成功：`+4`

公式：

```text
dataConfidence =
  40
  + profile
  + eps
  + cashflow
  + income
  + balance
  + broker
```

最後限制在 `0 ~ 100`。

對應程式：

- [TaiwanStockAnalyzer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/TaiwanStockAnalyzer.java:1682)

### 6.1 信心分的解讀

- `90 ~ 100`：資料很完整
- `65 ~ 89`：大致可用
- `< 65`：資料不完整，買點判斷要保守

目前系統也會在 `buyPointLabel` 中直接反映這件事。

## 7. 「買點佳」的判斷方式

這裡有兩個不同層次，要分開看。

### 7.1 前端 tab `買點佳`

前端 tab 的條件是：

```text
buyPointScore >= 75
AND selectionQualified = true
```

對應程式：

- [web/index.html](/c:/Users/AU/eclipse-workspace/stock/web/index.html:874)

所以 tab 裡看到的是「分數達標，而且基本流動性/財報品質有過門檻」的股票。

### 7.2 頂部摘要 `好買點 %`

頂部 market banner 的 `好買點 %` 是 API 直接計數：

```text
buyPointScore >= 75
```

目前這個百分比沒有額外加上 `selectionQualified` 條件。

對應程式：

- [StockApiRenderer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/StockApiRenderer.java:73)
- [StockApiRenderer.java](/c:/Users/AU/eclipse-workspace/stock/src/stock/StockApiRenderer.java:92)

這代表：

- `買點佳 tab` 比較嚴格
- 上方 `好買點 %` 比較像整體市場熱度統計

## 8. 一句話總結

- `傳統總分 legacyScore`：看個股本身基本面、籌碼、流動性、技術、財報品質的加總
- `新版總分 selectionScore`：在傳統總分之上，再加入趨勢續航、族群與新聞風險校正，題材僅供參考
- `買點分 buyPointScore`：在策略分基礎上，再判斷型態、回踩、過熱、風險報酬比、新聞熱度
- `信心 dataConfidence`：看這次資料抓得完整不完整
- `買點佳`：目前前端定義為 `buyPointScore >= 75` 且 `selectionQualified = true`
