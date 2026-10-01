/* Shared decision logic. A stored observation is not an execution or a validated strategy. */
(function (root) {
  'use strict';
  const positive = v => typeof v === 'number' && Number.isFinite(v) && v > 0;
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = v => positive(v) ? v.toLocaleString('zh-TW', {maximumFractionDigits:2}) : '尚無有效價位';
  function iso(v) {
    const s = String(v || '').replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3');
    const d = new Date(s + 'T00:00:00Z');
    return /^\d{4}-\d{2}-\d{2}$/.test(s) && Number.isFinite(+d) && d.toISOString().slice(0,10) === s ? s : null;
  }
  function openDay(calendar, day) {
    day = iso(day);
    const y = day && calendar?.years?.[day.slice(0,4)];
    if (!y || !Array.isArray(y.sessions)) return null;
    return y.sessions.includes(day);
  }
  // Entry day is day 0, aligned with the backtest's next-session entry convention.
  function heldSessions(calendar, start, end) {
    start = iso(start); end = iso(end);
    if (!start || !end || start > end || openDay(calendar,start) !== true) return null;
    let n = 0;
    for (let y = +start.slice(0,4); y <= +end.slice(0,4); y++) {
      const sessions = calendar?.years?.[y]?.sessions;
      if (!Array.isArray(sessions)) return null;
      n += sessions.filter(x => x > start && x <= end).length;
    }
    return n;
  }
  function freshness(s, meta, now = Date.now()) {
    const day = iso(meta.date), own = iso(s.date);
    const taipei = new Date(now + 8*3600000).toISOString();
    const today = taipei.slice(0,10), hour = +taipei.slice(11,13);
    if (!day || own !== day || day > today || !positive(s.price)) return '行情缺漏或日期不符';
    if (day === today) return null;
    const count = heldSessions(meta.tradingCalendar, day, today);
    if (count === null) return '交易日曆缺漏，無法確認行情時效';
    const allowed = hour < 18 && openDay(meta.tradingCalendar,today) === true ? 1 : 0;
    return count > allowed ? '行情已落後預期交易日，請更新資料' : null;
  }
  function entry(s, meta, now = Date.now()) {
    const reasons = [], missing = [];
    const stale = freshness(s,meta,now);
    if (stale) missing.push(stale);
    for (const [key,label] of [['techReady','技術'],['financialReady','財報'],['institutionalReady','法人'],['newsReady','新聞']]) {
      if (s[key] !== true) missing.push(label + '資料不足');
    }
    if (s.snapshotStage !== 'full') missing.push('尚非夜間完整分析');
    if (!s.ohlcv?.available) missing.push('還原行情不足');
    const stop = positive(s.suggestedStopPrice) && s.suggestedStopPrice < s.price ? s.suggestedStopPrice : null;
    const target = positive(s.suggestedTargetPrice) && s.suggestedTargetPrice > s.price ? s.suggestedTargetPrice : null;
    if (!stop || !target) missing.push('模型停損／目標價缺漏或順序不合理');
    const horizon = [10,20,40].includes(s.signalHorizonDays) ? s.signalHorizonDays : null;
    if (!horizon) missing.push('現有訊號天期不符合 10／20／40 交易日');
    const q = s.officialTrading;
    if (q?.assessment === 'review_required') reasons.push('官方交易公告需核對');
    const today = new Date(now + 8*3600000).toISOString().slice(0,10);
    if (!q?.fetchHealthy || q.evaluationDate !== today || q.tradabilityVerified !== true) missing.push('今日可交易狀態尚未確認');
    if (s.hardExclude === true) reasons.push(s.hardExcludeReason || '不符合基本資格');
    if (s.sellSignalScore >= 70 || /轉弱|出場/.test(s.sellSignalLabel || '')) reasons.push('原模型出現轉弱／退出訊號');
    if (/只觀察|暫不|不追/.test(s.postCloseAction || '')) reasons.push('原模型要求觀察、不追價');
    const candidate = s.likely === true && s.selectionQualified === true;
    const netRR = stop && target ? ((target * (1-.0015) * (1-.001425-.003)) - s.price*(1+.0015)*(1+.001425)) /
      (s.price*(1+.0015)*(1+.001425) - stop*(1-.0015)*(1-.001425-.003)) : null;
    // No validated entry band is exported today: never invent one from the current quote.
    const label = reasons.length ? '暫不進場・條件未過' : missing.length ? '待確認・資料或條件不足' : candidate ? '規則候選・策略待驗證' : '觀察・等待策略訊號';
    return {label, reasons, missing, candidate, stop, target, horizon, netRR,
      tone: reasons.length ? 'orange' : 'muted',
      note:'尚無經驗證的進場區間與觸發規則；新回測未完成樣本外驗證，不顯示可直接買進。'};
  }
  function validatePosition(p, previous, calendar, today) {
    if (!positive(p.cost) || !positive(p.shares) || !Number.isInteger(p.shares)) return '請填有效成本及整數股數（零股請直接填股數）。';
    if (!iso(p.date) || iso(p.date) > today) return '買進日期無效或在未來。';
    if (openDay(calendar,p.date) !== true) return '買進日期非官方預定交易日，或缺少該年度日曆。';
    if (![10,20,40].includes(p.horizon)) return '請選擇 10、20 或 40 個交易日。';
    if (!positive(p.stop) || (!previous && p.stop >= p.cost)) return '初始停損需大於 0 並低於成本。';
    if (p.target !== null && (!positive(p.target) || p.target <= p.cost || p.target <= p.stop)) return '目標價需高於成本與停損，或留空。';
    if (previous && (p.cost !== previous.cost || iso(p.date) !== iso(previous.date) || p.horizon !== previous.horizon)) return '同一筆持股保留原成本、進場日及天期；新交易請先封存舊紀錄。';
    if (previous && p.stop < previous.stop) return '防守價不能下調；原停損紀錄會持續保留。';
    return null;
  }
  function exit(s, p, meta, now = Date.now()) {
    const stale = freshness(s,meta,now);
    const held = heldSessions(meta.tradingCalendar,p.date,meta.date);
    const base = {held, stale, asof:meta.date, rank:5, label:'未觸發退出條件', reasons:[], tone:'muted', trigger:null};
    if (p.trigger) return {...base, rank:0, tone:'red', label:'曾觸發停損・待處理', reasons:[`${p.trigger.date} 快照價 ${money(p.trigger.price)} ≤ 當時防守價 ${money(p.trigger.stop)}`]};
    if (!iso(meta.date) || iso(s.date) !== iso(meta.date) || !positive(s.price) || !iso(p.date) || iso(p.date) > iso(meta.date)) {
      return {...base, rank:1, label:'持股行情待更新', reasons:['缺少進場後的有效快照，不能判斷續抱或退出']};
    }
    const actionDates = [...(s.ohlcv?.events || []).map(e=>e.date), ...(s.ohlcv?.unexplainedAdjustmentDates || [])];
    if (actionDates.some(d=>iso(d) > iso(p.date) && iso(d) <= iso(meta.date))) {
      return {...base,rank:1,tone:'orange',label:'公司行動／價位需核對',reasons:['持有期間有股利、分割或調整事件；請核對成本、股數與防守價，暫不直接比較停損或目標']};
    }
    if (s.price <= p.stop) return {...base,rank:0,tone:'red',label:'快照已觸發停損', reasons:[`快照價 ${money(s.price)} ≤ 保存防守價 ${money(p.stop)}；請核對實際報價及公司行動`],trigger:{date:meta.date,price:s.price,stop:p.stop}};
    if (stale) return {...base,rank:1,label:'持股行情待更新',reasons:[stale]};
    if (held === null) return {...base,rank:1,label:'持有天數待核對',reasons:['交易日曆缺漏，暫不判定持有期限']};
    const reasons = [];
    if (positive(p.target) && s.price >= p.target) reasons.push(`已達保存目標 ${money(p.target)}，評估停利`);
    if (held >= p.horizon) reasons.push(`已經過 ${held} 個預定交易日，達 ${p.horizon} 日檢查期限`);
    if (s.sellSignalScore >= 70 || /轉弱|出場/.test(s.sellSignalLabel || '')) reasons.push('原模型出現轉弱訊號，需人工複核');
    if (reasons.length) return {...base,rank:2,tone:'orange',label:'退場／停利條件需處理',reasons};
    if (s.price <= p.stop*1.03 || held >= p.horizon-2) return {...base,rank:3,tone:'orange',label:'接近防守價／期限',reasons:[`距防守價 ${((s.price-p.stop)/s.price*100).toFixed(2)}%；距期限 ${Math.max(0,p.horizon-held)} 個預定交易日`]};
    return {...base,reasons:['僅檢查已載入的快照；盤中曾跌破或未開啟頁面時的觸發尚未監測']};
  }
  function researchText(value) {
    return String(value || '').split(/[；;\n]/).filter(x => !/勝率|回測 cohort|績效校準/.test(x)).join('；');
  }
  function backtestHtml(report) {
    if (!report) return '<p>波段回測尚未載入；不提供個股勝率。</p>';
    const summary=report.pointInTimeConsistentSummary || {}, a=report.assumptions || {};
    const fmt = v => typeof v==='number' && Number.isFinite(v) ? v.toFixed(2)+'%' : '—';
    const rows=[10,20,40].map(h=>{const r=summary[h] || {};const ok=r.sampleCount>=30; return `<tr><td>${h} 個交易日</td><td>${esc(r.sampleCount ?? 0)}</td><td>${ok?fmt(r.winRatePct):'樣本不足'}</td><td>${ok?fmt(r.averageNetReturnPct):'—'}</td><td>${ok?fmt(r.medianNetReturnPct):'—'}</td><td>${ok?fmt(r.stopHitRatePct):'—'}</td></tr>`;}).join('');
    return `<p>2～8 週波段回測｜產生於 ${esc(report.generatedAt)}。訊號日期與更新日期一致的子樣本；不等於已證明當時所有資料可得。</p><div style="overflow:auto"><table><thead><tr><th>持有期</th><th>交易樣本</th><th>歷史勝率</th><th>平均淨報酬</th><th>中位淨報酬</th><th>停損率</th></tr></thead><tbody>${rows}</tbody></table></div><p>完整分析 likely 訊號、次一交易日開盤假設進場；固定停損 ${esc(a.stopLossPct)}%。買／賣手續費各 ${esc(a.buyFeePct?.toFixed(4))}%／${esc(a.sellFeePct?.toFixed(4))}%，賣出稅 ${esc(a.sellTaxPct)}%，買／賣滑價 ${esc(a.buySlippagePct)}%／${esc(a.sellSlippagePct)}%。</p><p>本回測與個股模型停損、目標價、使用者持股計畫並非同一策略，不能套用為個股成功機率。相同訊號的不同天期會重複計入，尚未完成樣本外驗證。</p><ul>${(report.limitations||[]).map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`;
  }
  root.Swing = {positive,esc,money,iso,openDay,heldSessions,freshness,entry,exit,validatePosition,researchText,backtestHtml};
  if (typeof module !== 'undefined') module.exports = root.Swing;
})(typeof globalThis !== 'undefined' ? globalThis : this);
