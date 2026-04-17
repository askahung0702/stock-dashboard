# 收盤後選股 / 資料收集改版清單

對應的工程落地版本請看 [winrate_engineering_task_list.md](./winrate_engineering_task_list.md)。

## 目標

- 以 `收盤後` 的交易資訊與新聞資訊為核心，找出 `隔日續強`、`3-5 日延續`、`5-10 日波段` 的上漲訊號。
- 提高 `勝率`、`平均淨報酬`、`可解釋性`，同時降低資料失真造成的誤判。
- 讓系統不只會排分數，還能清楚回答：
  - 這是哪一種上漲訊號？
  - 這個訊號應該看幾天？
  - 這個訊號是因為基本面、籌碼、技術，還是事件催化？
  - 這筆資料夠不夠新、夠不夠完整、夠不夠可信？

## 勝率優先原則

- 先提升 `直接影響勝率` 的項目，再做基礎建設。
- 先降低 `假強股`、`追高股`、`缺資料股` 混進主名單的機率，再追求更多訊號。
- 每次改版都要用回測驗證，不接受只靠感覺調權重。

## 進出場與回測口徑

### 1. 先固定目前專案的標準驗證口徑

- 訊號日：
  - 以 `T 日收盤後` 跑完整分析，產出當日候選名單。
- 進場日：
  - 目前先以 `T+1 日收盤價` 視為進場價，與現行 `StockBacktestReport` 一致。
- 出場日：
  - 以各訊號類型對應的 horizon 為主。
  - 若先觸發停損或停利，則提前出場。
- 交易成本：
  - 回測固定納入：
    - 買進手續費
    - 賣出手續費
    - 證交稅
    - 買賣滑價
- 現行程式觀察：
  - `simulateTrade()` 目前就是：
    - `T 日產生訊號`
    - `T+1 日收盤價進場`
    - 觀察 `1 / 3 / 5 / 10 日`
    - 先看是否 hit stop / target
    - 否則 horizon 到期出場
- 目的：
  - 先讓所有人討論的是同一種勝率，不要有人用隔日開盤、有人用隔日收盤、有人用盤中高低點。

### 2. 進場規則要先明確定義，不然勝率會失真

- 第一版建議固定三種訊號共用同一個進場口徑：
  - `T 日收盤後選股`
  - `T+1 日收盤價進場`
- 原因：
  - 現有歷史快照是日資料，最容易直接落地。
  - 和目前回測程式假設一致，先避免驗證與實盤口徑不同。
- 第二版再比較不同進場法：
  - `T+1 開盤價`
  - `T+1 收盤價`
  - `T+1 VWAP / 均價近似`
- 目標：
  - 找出最接近真實可執行、且勝率最穩的進場口徑。

### 3. 三種訊號要有各自的出場邏輯

- `隔日續強`
  - 預設持有 `1 日`
  - 若 `T+1` 已明顯轉弱或收盤跌破停損，隔日不續抱
- `3-5 日延續`
  - 預設持有 `3` 或 `5` 日
  - 適合搭配事件催化、題材延續、族群擴散
- `5-10 日波段`
  - 預設持有 `5` 或 `10` 日
  - 重點放在趨勢延續與波段結構，不要求隔日立刻噴出
- 現行可行做法：
  - 先直接沿用 `StockBacktestReport` 已有的 `1 / 3 / 5 / 10` horizon
  - Dashboard 與 CSV 顯示：
    - `signal_type`
    - `signal_horizon_days`
    - `entry_rule`
    - `exit_rule`

### 4. 停損停利規則要固定，不然回測無法比較

- 目前可沿用現行程式邏輯：
  - 停損：
    - 優先用 `suggestedStopPrice`
    - 若無，退回 `suggestedStopPct`
    - 再無，退回波動度推估與預設停損
  - 停利：
    - 優先用 `suggestedTargetPrice`
    - 若無，退回 `upsidePotentialPct`
    - 再無，退回 `riskRewardRatio` 與預設目標報酬
- 第一版建議：
  - 不同訊號先共用這套停損停利邏輯
  - 等回測樣本夠多，再針對不同訊號拆開調整
- 目的：
  - 先讓回測結果具可比性，不要每一類都用不同主觀停損。

### 5. 回測要清楚標示「目前是日資料 close-to-close 驗證」

- 現況限制：
  - 目前回測是看每日快照，不是盤中逐筆。
  - `stop hit / target hit` 也是用每日觀察值判斷，不代表盤中真實成交一定相同。
- 文件中應明示：
  - 這是一套 `收盤後決策` 驗證框架
  - 不是高頻或盤中即時交易引擎
- 勝率用途：
  - 適合比較不同收盤後選股規則
  - 不適合拿來宣稱盤中最精準成交結果

### 6. 驗證口徑要固定，不然後續調整沒有意義

- 每次規則變更，至少固定追蹤：
  - `1 日 net win rate`
  - `3 日 net win rate`
  - `5 日 net win rate`
  - `10 日 net win rate`
  - `avg net return`
  - `median net return`
  - `max drawdown`
  - `stop hit rate`
  - `target hit rate`
  - `sample count`
- 每次改版報告都要回答：
  - 勝率有沒有提高？
  - 報酬有沒有提高？
  - 回撤有沒有變大？
  - 樣本數有沒有小到失真？

## 現行系統影響勝率的主要問題

### 1. 同一套分數同時服務不同目標

- 目前 `selectionScore`、`buyPointScore`、`postCloseCategory` 同時在服務：
  - 隔日續強
  - 幾日延續
  - 波段布局
  - 催化觀察
- 問題：
  - 不同持有期的勝率驅動因子不同，硬用同一套排序，容易讓短線與波段訊號互相污染。
- 現行程式觀察：
  - `finalizeCompositeScores()` 會算出共用的 `selectionScore` / `buyPointScore`
  - `applyPostCloseDecisionProfile()` 再分成 `高勝率候選 / 短線主攻 / 波段布局 / 催化觀察`
  - 但門檻仍主要是手工規則，還沒有依回測結果校準

### 2. 回測有做，但還沒有真正反饋到排序與門檻

- 目前已有 `StockBacktestReport`，會輸出：
  - `LIKELY`
  - `WATCHLIST`
  - `WINRATE_FOCUS`
  - `BUYPOINT_A`
  - 不同 horizon 的勝率與報酬
- 問題：
  - 現在回測結果沒有直接回寫到：
    - 門檻設定
    - 每日排序
    - 類別權重
    - 風險排除規則
- 結果：
  - 系統會做報告，但不會自我校正。

### 3. 趨勢與族群強度，仍偏「分數延續」不是「股價領先」

- `evaluateTrendProfile()` 目前主要看：
  - 過去幾天 `selectionScore` 是否連續站上門檻
  - 與前幾日分數差異
- `buildSectorScores()` 目前主要看：
  - 同產業股票當日平均 `selectionScore / quality / momentum`
- 問題：
  - 這比較像「模型自己認為有沒有延續」，不是「市場價格與量能是否真的領先」。
- 會造成：
  - 模型高分延續，不等於市場真的在追。

### 4. 事件與新聞已有進步，但還不夠方向化

- 目前新聞已補到：
  - 多來源
  - 新鮮度
  - 來源可信度
  - 官方公告與市場題材
- 問題：
  - 還沒有完整結構化成：
    - `event_type`
    - `direction`
    - `confidence`
    - `effective_date`
- 會造成：
  - 新聞熱度高，不一定代表上漲機率高。
  - 例如：
    - 庫藏股、接單、法說上修
    - 和現增、訴訟、處分資產
  - 不應只用相似的熱度邏輯處理。

### 5. 缺少「硬性排除規則」

- 現在高分不一定代表值得做。
- 問題：
  - 若資料缺漏、新聞過舊、量比過熱、非營業依賴高、事件方向不明，仍可能進觀察或主名單。
- 這會直接拖累勝率，因為：
  - 真正傷勝率的通常不是漏掉一檔強股
  - 而是把不該做的股放進來

### 6. 排序仍偏分數導向，不是「預期勝率 / 預期報酬」導向

- 目前主要還是依：
  - `selectionScore`
  - `postClosePriorityScore`
- 問題：
  - 這不等於 `隔日勝率最高`
  - 也不等於 `3-5 日報酬最好`
- 正確做法應該是：
  - 每個類別各自有一套 `winrate_priority_score`
  - 排序看該類型在該 horizon 的歷史表現

## 直接影響勝率的立即可做

### 1. 先定義三種目標訊號，不再混在一起

- 固定拆成：
  - `隔日續強`
  - `3-5 日延續`
  - `5-10 日波段`
- 每種訊號要有：
  - 主要因子
  - 排除條件
  - 目標持有期
  - 驗證 KPI
- 現行可行做法：
  - 直接沿用現有分類對應：
    - `短線主攻 -> 隔日續強`
    - `高勝率候選 / 催化觀察 -> 3-5 日延續`
    - `波段布局 -> 5-10 日波段`
  - 在輸出 CSV / dashboard 增加：
    - `signal_type`
    - `signal_horizon_days`

### 2. 把回測結果真正接回每日排序

- 新增：
  - `winrate_priority_score`
  - `expected_return_score`
  - `max_drawdown_penalty`
- 排序改成：
  - 類別內優先按 `winrate_priority_score`
  - 不再只看 `selectionScore`
- 現行可行做法：
  - 直接讀 `StockBacktestReport` 產出的 cohort 表現
  - 先用現成 cohort 做映射：
    - `WINRATE_FOCUS`
    - `LIKELY`
    - `WATCHLIST`
    - `BUYPOINT_A`
  - 做成每日 `backtest_summary.csv -> ranking weight` 的簡單規則

### 3. 新增硬性排除規則，先砍掉最會拖累勝率的標的

- 對主名單增加不可進入條件：
  - `data_confidence < 70`
  - `核心資料缺漏`
  - `newsFreshnessScore` 過低但屬催化股
  - `volumeRatio` 明顯過熱
  - `structureLabel = 追高風險`
  - `nonOperatingRatioPct` 過高
  - `newsRiskScore` 過高
- 現行可行做法：
  - 直接利用已存在欄位：
    - `dataConfidence`
    - `structureLabel`
    - `volumeRatio`
    - `newsFreshnessScore`
    - `newsRiskScore`
    - `nonOperatingRatioPct`
  - 先只加在：
    - `高勝率候選`
    - `短線主攻`

### 4. 把資料品質真正接進推薦層，不只是顯示

- 目前 `data_confidence` 比較偏說明欄位
- 應改成：
  - `A/B` 才能進主推薦
  - `C/D` 只能進觀察
- 現行可行做法：
  - 在 `applyPostCloseDecisionProfile()` 的分類條件中直接加 gate
  - Dashboard 顯示：
    - `可交易`
    - `只觀察`
    - `資料不足`

### 5. 增加相對強度，而不是只看絕對分數

- 新增：
  - 個股相對同產業報酬
  - 個股相對同產業量比
  - 個股法人買超相對產業 percentile
  - 題材龍頭 / 非龍頭區分
- 現行可行做法：
  - 利用現有 `results` 與 `history snapshots`
  - 先算：
    - `return20DayPct` 相對產業平均
    - `volumeRatio` 相對產業平均
    - `fiveDayInstitutionalNetRatioPct` 相對產業平均
  - 作為 `relative_strength_score`

### 6. 把事件資料做成方向化訊號

- 事件至少要分：
  - `正向催化`
  - `中性待確認`
  - `負向風險`
- 不能只看熱度
- 現行可行做法：
  - 先從公告與新聞摘要做最小版本：
    - `庫藏股 / 法說上修 / 接單 / 量產 / 擴產 / 股利 -> 正向`
    - `現增 / 私募 / 訴訟 / 處分資產 / 減資 -> 負向`
    - `董事會 / 澄清 / 一般公告 -> 中性`
  - 在分析結果新增：
    - `event_direction`
    - `event_confidence`
    - `event_freshness_days`

### 7. 對短線訊號加入「新鮮度」硬要求

- 短線續強最怕看的是舊新聞
- 現行可行做法：
  - `短線主攻` 必須同時滿足：
    - `latestNewsPublishedHint` 在最近 1-2 天內
    - 或 `officialSourceCount >= 1`
  - 否則即使分數高，也降到 `催化觀察`

### 8. 增加「假突破 / 假催化」抑制條件

- 特別排除這些容易拖累勝率的型態：
  - 接近 60 日高點但量比過熱
  - 新聞很熱但 `riskRewardRatio` 很差
  - 非營業依賴高且只是單次題材
  - 法人不站買方但新聞很熱
- 現行可行做法：
  - 利用現有欄位直接做 rule：
    - `drawdownFromHigh60Pct`
    - `volumeRatio`
    - `riskRewardRatio`
    - `fiveDayInstitutionalNetLots`
    - `nonOperatingRatioPct`

## 資料收集立即可做

### 1. 補齊資料透明度欄位

- 每個資料來源加上：
  - `source`
  - `data_as_of`
  - `fetched_at`
  - `fetch_status`
  - `fallback_used`
  - `stale_days`
- 至少先補到：
  - 月營收
  - 法人買賣超
  - EPS
  - 現金流
  - 損益表
  - 資產負債表
  - 新聞
  - 公告
  - 技術面資料
- 目的：
  - 讓前台與分析文案能清楚標示「這是昨天資料、這是最新季資料、這筆抓取失敗後用了 fallback」。

### 2. 將抓取失敗結果結構化，不只印 console

- 現況：`fetchOptional()` 失敗只印 log 後回傳 fallback。
- 立即新增：
  - per-stock fetch error list
  - error code / exception type
  - source URL
  - first failure time
  - fallback value description
- 前台與 CSV 可增加：
  - `data_gap_count`
  - `data_gap_summary`
- 目的：
  - 分數低不再只看結果，還能知道是不是因為缺資料。

### 3. 加入 raw payload 快取

- 對每次抓下來的 HTML / JSON / API 回應做本地快取。
- 建議目錄：
  - `raw_cache/yyyyMMdd/<stock>/<source>.html`
  - `raw_cache/yyyyMMdd/<stock>/<source>.json`
- 先只保留最近 7 到 14 天即可。
- 目的：
  - 方便比對解析錯誤
  - 方便版面改版後回頭修 parser
  - 方便之後做 replay 測試

### 4. 建立 parser smoke test

- 針對以下來源建立固定樣本測試：
  - revenue
  - institutional-trading
  - eps
  - cash-flow-statement
  - income-statement
  - balance-sheet
  - profile
  - announcement
  - news
- 每次改 parser，都能先用已存下來的樣本確認欄位還能正確解析。
- 目的：
  - 防止頁面小改就整批資料 silently 壞掉。

### 5. 新增資料品質分級

- 把現有 `data_confidence` 再拆得更明確：
  - `A`: 核心資料完整，且無 fallback
  - `B`: 核心資料完整，但部分 fallback
  - `C`: 核心資料缺漏 1 到 2 項
  - `D`: 核心資料大量缺漏，只能參考
- 每日分析輸出時，若個股是 `C/D`，應明示不要過度解讀。
- 勝率用途：
  - `C/D` 不進主推薦

### 6. 先補最重要的事件型資料欄位

- 不用一次做完整事件引擎，先做最常見且最有用的：
  - 庫藏股
  - 現金股利 / 除權息
  - 董事會決議
  - 法說會日期與摘要
  - 現增 / 私募
  - 重大資產處分
- 即使先用公告頁關鍵字 + 結構化摘要，也比現在完全混在新聞裡好。

### 7. 增加來源層監控報表

- 每日輸出一份簡單的 source health report：
  - 各來源成功率
  - 各來源平均抓取時間
  - 各來源 fallback 次數
  - 抓不到資料的股票數
- 目的：
  - 一眼知道今天是資料源壞掉，還是真的市場沒訊號。

### 8. 讓每日分析文案引用資料缺口

- 在分析模板裡加入標準句：
  - `本日部分個股因資料源抓取不完整，分數僅供參考`
  - `本檔個股事件資料不足，盤中異動可能未被模型完整反映`
- 目的：
  - 降低過度信任模型輸出的風險。

## 中期重構

### 1. 建立多來源 Provider 架構

- 不要再由單一 `YahooTaiwanStockService` 負責大部分資料。
- 重構成：
  - `MarketListProvider`
  - `FinancialsProvider`
  - `PriceTechnicalProvider`
  - `NewsProvider`
  - `AnnouncementProvider`
  - `EventProvider`
- 每個 Provider 支援：
  - primary source
  - fallback source
  - source priority
  - parse version
- 目標：
  - 官方或結構化來源優先，Yahoo 作 fallback，而不是反過來。

### 2. 建立標準化資料模型與版本控管

- 對每個資料類型建立統一 schema：
  - quote
  - revenue
  - financials
  - institutional flow
  - event
  - news
- 每個 schema 增加：
  - `schema_version`
  - `source_version`
  - `parser_version`
- 目的：
  - 後續換資料源或 parser 時，不會整條 pipeline 全亂掉。

### 3. 建立事件驅動資料層

- 將事件資料從新聞與公告裡抽出，做成獨立表：
  - `event_type`
  - `event_date`
  - `effective_date`
  - `direction`
  - `confidence`
  - `event_summary`
- 事件類別至少包含：
  - 股利
  - 庫藏股
  - 法說
  - 現增 / 私募
  - 訴訟
  - 董監改選
  - 處分資產
  - 接單 / 量產 / 擴產
- 目標：
  - 讓事件不再只是新聞熱度，而是可進排序與排除的結構化訊號。

### 4. 將新聞收集改為多來源與去重機制

- 新聞至少分成：
  - 官方公告
  - 個股媒體新聞
  - 市場總覽新聞
  - 法說摘要
- 增加：
  - headline dedupe
  - source weight
  - recency weight
  - event extraction
- 目標：
  - 降低單一來源偏誤與重複計分問題。

### 5. 將串行日跑改成可控並行管線

- 現況每檔逐一抓取，速度慢但穩。
- 中期目標：
  - Provider 層可並行
  - 來源層有 concurrency limit
  - 來源層有 per-host throttle
  - 股票層有重試與回退
- 目標：
  - 保持穩定下，縮短總執行時間，讓盤後分析更快完成。

### 6. 建立資料倉與回放機制

- 除了分析結果，保留：
  - 原始資料
  - 清洗後資料
  - 特徵值
  - 最終分數
- 每一層都能回放與比對：
  - 原始資料變了？
  - parser 變了？
  - 特徵工程變了？
  - 分數規則變了？
- 目標：
  - 系統可追溯、可除錯、可驗證。

### 7. 將回測升級為「門檻校準器」

- 不只出報表，要能輸出：
  - 哪一類訊號最適合哪個 horizon
  - 哪些特徵組合有最高勝率
  - 哪些條件會明顯拖累勝率
- 產出：
  - `signal calibration report`
  - `recommended thresholds`
  - `exclude rules`
- 目標：
  - 從「回測觀察」走到「回測驅動的規則更新」。

### 8. 建立資料品質 SLA

- 對每一類資料訂出最低標準：
  - 可用率
  - 最大延遲
  - 容許 fallback 比例
  - 盤後最晚完成時間
- 例如：
  - 技術面與行情：可用率 >= 99%
  - 財報 / 月營收：可用率 >= 95%
  - 新聞 / 公告：可用率 >= 90%

## 驗收指標

- 每個主要訊號類型都要分別追蹤：
  - `1 日 net win rate`
  - `3 日 net win rate`
  - `5 日 net win rate`
  - `10 日 net win rate`
  - `avg net return`
  - `median net return`
  - `max drawdown`
  - `sample count`
- 不能只看總平均，要看：
  - `短線主攻`
  - `高勝率候選`
  - `波段布局`
  - `催化觀察`
  - 各自的表現是否改善

## 實作優先順序建議

### 第一階段

- 固定 `進場規則 / 出場規則 / 回測口徑`
- 定義 `signal_type / signal_horizon_days`
- 把回測結果接回排序與門檻
- 增加主名單硬性排除規則
- 把資料品質分級接進主推薦
- 補資料透明度欄位
- 補 fetch error 結構化紀錄

### 第二階段

- 補事件型資料最小集合
- 補新聞方向化與新鮮度 gate
- 補相對強度分數
- 補 raw payload cache
- 補 parser smoke test
- 補 source health report

### 第三階段

- 重構成多來源 Provider
- 建立事件驅動資料層
- 導入資料倉與回放機制
- 將回測升級成校準器

## 完成後的預期效果

- 主名單會更乾淨，缺資料股、追高股、假催化股較不容易混進來。
- 分數不再只是好看，而是更接近實際可交易的 `勝率 / 報酬` 訊號。
- 收盤後分析會更像一個可執行的選股系統，而不是高分排行。
- 事件股、題材股、波段股會被明確分流，不再混在一起比較。
- 後續要做調權重、調門檻、加資料源時，都能用回測與資料品質一起驗證。
