# 收盤後勝率改善工程清單

這份文件是 [data_collection_refactor_checklist.md](./data_collection_refactor_checklist.md) 的工程落地版。

目標不是重做整套系統，而是用目前專案已有的分析、快照、回測、dashboard 基礎，優先把最有機會提高 `收盤後選股勝率` 的改動落地。

## 工程原則

- 先做 `直接影響勝率` 的項目，再做架構美化。
- 優先改已存在的主流程：
  - `TaiwanStockAnalyzer`
  - `StockBacktestReport`
  - `StockHistoryDatabase`
  - `StockApiRenderer`
  - `StockDashboardWriter`
- 每一項改動都要同時補：
  - 後端欄位
  - CSV 輸出
  - 歷史快照
  - API / dashboard
  - 回測驗證

## 第一階段

### 1. 固定訊號類型與驗證欄位

- 目的：
  - 把目前 `高勝率候選 / 短線主攻 / 波段布局 / 催化觀察` 正式對應成可回測的訊號類型。
- 先改檔案：
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/StockApiRenderer.java`
  - `src/stock/StockDashboardWriter.java`
- 新增欄位：
  - `signalType`
  - `signalHorizonDays`
  - `entryRule`
  - `exitRule`
  - `validationMode`
- 實作方式：
  - 在 `applyPostCloseDecisionProfile()` 裡直接設定：
    - `短線主攻 -> 隔日續強 / 1`
    - `高勝率候選 -> 3-5日延續 / 3`
    - `催化觀察 -> 3-5日延續 / 5`
    - `波段布局 -> 5-10日波段 / 10`
  - `entryRule` 第一版固定為 `T+1 close`
  - `exitRule` 第一版固定為 `stop/target/or horizon`
  - `validationMode` 第一版固定為 `daily close-to-close`
- 驗收：
  - 當日 CSV 有新欄位
  - `history/stock_history_db.json` 可存回欄位
  - `/api/latest` 和 `/api/history` 有回傳欄位
  - dashboard 明細可看到每檔對應訊號類型

### 2. 把回測結果接回每日排序

- 目的：
  - 讓系統不是只看高分，而是看該類型在該 horizon 的歷史勝率。
- 先改檔案：
  - `src/stock/StockBacktestReport.java`
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/StockDashboardWriter.java`
- 新增欄位：
  - `winratePriorityScore`
  - `expectedReturnScore`
  - `maxDrawdownPenalty`
  - `backtestCohort`
- 實作方式：
  - 第一版不做複雜模型，先直接讀 `history/backtest_summary.csv`
  - 把下列 cohort 映射回目前分類：
    - `WINRATE_FOCUS`
    - `LIKELY`
    - `WATCHLIST`
    - `BUYPOINT_A`
  - 在 `finalizeCompositeScores()` 最後新增一段：
    - 依 `signalType + cohort` 給 `winratePriorityScore`
    - `postClosePriorityScore` 由原本分數導向，改成：
      - 基礎分數
      - 加上 `winratePriorityScore`
      - 扣掉 `maxDrawdownPenalty`
- 驗收：
  - 同類型股票的排序，不再只由 `selectionScore` 決定
  - dashboard 可顯示：
    - 最近該類型 `1/3/5/10` 日淨勝率
    - 主要排序 cohort

### 3. 加入硬性排除規則

- 目的：
  - 優先減少最容易拖累勝率的假訊號。
- 先改檔案：
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/StockApiRenderer.java`
  - `src/stock/StockDashboardWriter.java`
- 新增欄位：
  - `hardExclude`
  - `hardExcludeReason`
  - `dataQualityGrade`
- 第一版規則：
  - `dataConfidence < 70`
  - `structureLabel = 追高風險`
  - `newsRiskScore >= 70`
  - `nonOperatingRatioPct >= 50`
  - `volumeRatio` 明顯過熱
  - 短線訊號但 `newsFreshnessScore` 太低
- 實作方式：
  - 在 `applyPostCloseDecisionProfile()` 前先做一層 `applyHardExclusionRules()`
  - 被排除者不進：
    - `高勝率候選`
    - `短線主攻`
  - 但可保留在：
    - `催化觀察`
    - `資料不足`
- 驗收：
  - CSV / API / dashboard 可明確看到是被排除，不是單純低分
  - 排除理由對使用者可解釋

### 4. 把資料品質真正接進推薦層

- 目的：
  - 避免缺資料股混進主名單。
- 先改檔案：
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/StockApiRenderer.java`
  - `src/stock/StockDashboardWriter.java`
- 新增欄位：
  - `dataGapCount`
  - `dataGapSummary`
  - `tradableReadiness`
- 實作方式：
  - 第一版沿用既有 `dataConfidence`
  - 額外轉成：
    - `A`
    - `B`
    - `C`
    - `D`
  - `A/B` 才能進主推薦
  - `C/D` 只能進觀察
- 驗收：
  - 主名單內不再出現資料不足卻高分的股票
  - dashboard 與文案能區分：
    - `可交易`
    - `只觀察`
    - `資料不足`

### 5. 補相對強度，不只看絕對分數

- 目的：
  - 讓系統辨認「這檔是不是同族群真龍頭」。
- 先改檔案：
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/StockDashboardWriter.java`
  - `src/stock/StockApiRenderer.java`
- 新增欄位：
  - `relativeStrengthScore`
  - `industryReturnStrength`
  - `industryVolumeStrength`
  - `industryFlowStrength`
- 實作方式：
  - 在 `buildSectorScores()` 後新增第二層相對比較：
    - `return20DayPct` 相對產業平均
    - `volumeRatio` 相對產業平均
    - `fiveDayInstitutionalNetRatioPct` 相對產業平均
  - 第一版先做簡單 percentile 或 z-score
- 驗收：
  - 同產業內能分出主帥與跟漲股
  - `短線主攻` 名單更偏向相對強勢股

### 6. 把事件催化做成方向化欄位

- 目的：
  - 不再只看新聞熱度，而是看事件方向。
- 先改檔案：
  - `src/stock/vo/NewsSignalVO.java`
  - `src/stock/YahooTaiwanStockService.java`
  - `src/stock/vo/StockAnalysisResultVO.java`
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/StockHistoryDatabase.java`
  - `src/stock/StockDashboardWriter.java`
- 新增欄位：
  - `eventDirection`
  - `eventConfidence`
  - `eventFreshnessDays`
  - `eventTypeSummary`
- 第一版規則：
  - `庫藏股 / 法說上修 / 接單 / 量產 / 擴產 / 股利 -> 正向`
  - `現增 / 私募 / 訴訟 / 處分資產 / 減資 -> 負向`
  - `董事會 / 一般公告 / 澄清 -> 中性`
- 實作方式：
  - 先不做完整 NLP
  - 以 `公告標題 + 新聞摘要 + 關鍵字` 做最小可用版本
  - `scoreNewsSignal()` 與 `scoreNewsRisk()` 納入方向化加權
- 驗收：
  - 新聞熱但偏負向的股票，不再因熱度被推高
  - 正向新鮮催化股，能比一般新聞股更前排

## 第二階段

### 1. 短線訊號加入新鮮度 gate

- 先改檔案：
  - `src/stock/TaiwanStockAnalyzer.java`
  - `src/stock/StockDashboardWriter.java`
- 規則：
  - `短線主攻` 必須滿足：
    - `latestNewsPublishedHint` 在 1-2 日內
    - 或 `newsOfficialSourceCount >= 1`
  - 否則降級為 `催化觀察`

### 2. 假突破 / 假催化抑制

- 先改檔案：
  - `src/stock/TaiwanStockAnalyzer.java`
- 規則：
  - `接近 60 日高點 + 量比過熱 + 風報比差`
  - `新聞很熱但法人不站買方`
  - `非營業依賴高且只有單次題材`
- 目的：
  - 降低最容易套人的追價型訊號

### 3. 將回測升級成校準器

- 先改檔案：
  - `src/stock/StockBacktestReport.java`
  - 新增 `src/stock/BacktestCalibrationService.java`
  - `src/stock/TaiwanStockAnalyzer.java`
- 產出：
  - `recommended thresholds`
  - `exclude rules`
  - `signal calibration report`
- 目的：
  - 從手工調門檻，升級成回測驅動調整

## 每個檔案第一步要怎麼改

### `src/stock/vo/StockAnalysisResultVO.java`

- 先補所有新欄位與 getter/setter
- 優先順序：
  - `signalType / signalHorizonDays / entryRule / exitRule / validationMode`
  - `winratePriorityScore / expectedReturnScore / maxDrawdownPenalty / backtestCohort`
  - `hardExclude / hardExcludeReason / dataQualityGrade`
  - `relativeStrengthScore / industryReturnStrength / industryVolumeStrength / industryFlowStrength`
  - `eventDirection / eventConfidence / eventFreshnessDays / eventTypeSummary`

### `src/stock/StockHistoryDatabase.java`

- `DATABASE_VERSION` 需要升級
- `SnapshotRow` 對應新增欄位
- 補：
  - `toRow()`
  - `toRowJson()`
  - `parseRowJson()`
  - `readSnapshotFromCsv()`
- 舊資料 fallback：
  - 缺欄位時要有預設值，不要讀壞歷史資料

### `src/stock/TaiwanStockAnalyzer.java`

- 第一批新增方法建議：
  - `applySignalProfile()`
  - `applyHardExclusionRules()`
  - `applyDataQualityGate()`
  - `applyRelativeStrengthScores()`
  - `applyEventDirectionProfile()`
  - `applyBacktestCalibration()`
- 先接位置：
  - `finalizeCompositeScores()` 末段
  - `applyPostCloseDecisionProfile()` 前後
- 原則：
  - 先做 second-pass 規則
  - 不要把所有新邏輯都塞回單一長方法

### `src/stock/StockBacktestReport.java`

- 第一批工作：
  - 補 `signalType` 維度
  - 補 `entryRule / exitRule / validationMode` 欄位
  - 補 `hardExclude` 前後的 cohort 比較
- 第一版先保留既有 `1 / 3 / 5 / 10` horizon
- 先不要一次重寫成多策略框架

### `src/stock/StockApiRenderer.java`

- `renderLatestJson()` 與 `renderStockHistoryJson()` 都要補新欄位
- 前端至少要拿到：
  - `signalType`
  - `signalHorizonDays`
  - `hardExclude`
  - `hardExcludeReason`
  - `dataQualityGrade`
  - `relativeStrengthScore`
  - `eventDirection`
  - `winratePriorityScore`

### `src/stock/StockDashboardWriter.java`

- 優先補三種畫面資訊：
  - 每檔訊號類型與持有期
  - 排除理由與資料品質
  - 類型內排序依據與最近勝率提示
- 不用一次把所有欄位都攤開
- 第一版先在：
  - `收盤後高勝率候選`
  - `收盤後短線主攻`
  - `收盤後波段布局`
  - `收盤後催化觀察`
  這四區補上提示即可

## 建議執行順序

### Sprint 1

- 補 `signalType / signalHorizonDays / entryRule / exitRule / validationMode`
- 補 `hardExclude / hardExcludeReason / dataQualityGrade`
- 將欄位串到：
  - CSV
  - history snapshot
  - API
  - dashboard

### Sprint 2

- 將回測結果接回 `postClosePriorityScore`
- 補 `winratePriorityScore / expectedReturnScore / backtestCohort`
- 做第一版類型內排序改造

### Sprint 3

- 補 `relativeStrengthScore`
- 補 `eventDirection / eventConfidence / eventFreshnessDays`
- 將短線新鮮度 gate 與假突破抑制接上

## 完成判準

- 主名單不再只是高分排行，而是有明確的 `訊號類型 + 驗證口徑 + 排除規則`
- 排序能反映該類型在該 horizon 的歷史勝率，而不是只看總分
- dashboard 能直接看出：
  - 這檔適合看幾天
  - 為什麼能進主名單
  - 為什麼被排除
  - 這個類型最近到底有沒有勝率
- 每次改版都能用同一套 `1 / 3 / 5 / 10 日 net win rate` 指標比較前後差異
