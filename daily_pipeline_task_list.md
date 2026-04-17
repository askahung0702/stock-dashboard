# Daily Pipeline Task List

這份清單用來避免之後新增評分因子時，只改到單一層而漏掉每日收資料鏈路。

## 本次已完成

- [x] `sectorScore`
- [x] 平台突破 / 結構型買點
- [x] 趨勢持續性正式納入後端評分
- [x] 風險報酬比 / 停損距離納入買點分
- [x] 市場危險警報 `marketDangerScore / marketAlert`
- [x] 題材籃子 + 題材熱度 `theme_baskets / themes`
- [x] 個股 / 題材新聞分數 `newsScore / newsRiskScore`

## 每次新增核心因子都要檢查

### 1. 後端分析

- [ ] `StockAnalysisResultVO` 新增欄位與 getter/setter
- [ ] `TaiwanStockAnalyzer` 實作計分邏輯
- [ ] 若屬於跨股票因子，補 second-pass / finalize 流程
- [ ] 更新 `analysisNote`
- [ ] 更新 `scoreReason`
- [ ] 更新 `buyPointLabel / buyPointReason` 或其他判讀文字

### 2. 每日快照輸出

- [ ] `writeCsv()` 表頭新增欄位
- [ ] `writeCsv()` 每列資料寫出新欄位
- [ ] 檢查 `daily_snapshots/stock_candidates_YYYYMMDD.csv` 是否真的有新欄位

### 3. 歷史資料庫

- [ ] `StockHistoryDatabase` 版本號需要時升級
- [ ] `SnapshotRow` 新增欄位
- [ ] `toRow()` 寫入新欄位
- [ ] `toRowJson()` 寫入新欄位
- [ ] `parseRowJson()` 讀回新欄位
- [ ] `readSnapshotFromCsv()` 匯入 CSV 時能讀到新欄位
- [ ] 舊快照缺欄位時有合理 fallback

### 4. API / Dashboard

- [ ] `StockApiRenderer.renderLatestJson()` 回傳新欄位
- [ ] `StockApiRenderer.renderStockHistoryJson()` 回傳新欄位
- [ ] 若有 `StockDashboardWriter` / 靜態 HTML 產物，也同步補欄位
- [ ] 前端列表 / 明細頁需要時補顯示
- [ ] 舊快照缺欄位時前端不要誤導顯示

### 5. 回測 / 驗證

- [ ] `StockBacktestReport` 視需要新增 cohort
- [ ] 新因子能出現在 `history/backtest_summary.csv`
- [ ] 至少做一次 `javac` 編譯檢查
- [ ] 至少做一次前端 JS 語法檢查
- [ ] 若資料來源不需要即時抓取，補本地 smoke test

### 6. 每日執行流程

- [ ] 跑 `run_stock_analysis.bat`
- [ ] 確認新的 `history/stock_history_db.json` 已更新
- [ ] 確認當日 `daily_snapshots/stock_candidates_YYYYMMDD.csv` 表頭含新欄位
- [ ] 重啟 `run_stock_dashboard_server.bat`
- [ ] 重新整理頁面確認新欄位可見
- [ ] 檢查 `history/backtest_summary.csv` 是否重新產出

## 題材 / 新聞 / 警報加欄位時額外確認

- [ ] `config/theme_baskets.csv` 已更新題材籃子與成分股
- [ ] `YahooTaiwanStockService` 新聞抓取失敗時有合理 fallback
- [ ] `StockApiRenderer` 的 `marketDangerScore / marketAlert / themes` 有回傳
- [ ] 前端市場 banner 有顯示警報，且 `theme-strip` 有題材熱度卡
- [ ] 明細頁有顯示 `題材 / 新聞 / 新聞風險 / 新聞摘要`

## 舊資料注意事項

- 舊快照不會自動長出新欄位。
- 新因子要從「下一次完整分析」開始累積。
- 如果畫面看到 `未刷新` / `0` / fallback 值，先確認當日快照是否已重跑新版分析。
