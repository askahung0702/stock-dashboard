/* Holdings are explicitly recorded by the user and stored only in this browser. */
const SWING_POSITION_KEY = '_swing_positions_v1';
let swingPositions = null;
let swingStorageError = '';
function loadSwingPositions() {
  if (swingPositions) return swingPositions;
  try {
    const raw = localStorage.getItem(SWING_POSITION_KEY);
    const value = raw ? JSON.parse(raw) : {active:{},closed:[]};
    if (!value || value.version && value.version !== 1 || !value.active || typeof value.active !== 'object' || Array.isArray(value.active) || !Array.isArray(value.closed)) throw Error('紀錄格式無效');
    for (const [code,p] of Object.entries(value.active)) {
      if (!/^[A-Za-z0-9]{4,8}$/.test(code) || !p || !Swing.positive(p.cost) || !Swing.positive(p.shares) || !Number.isInteger(p.shares) || !Swing.positive(p.stop) || !Swing.iso(p.date) || ![10,20,40].includes(p.horizon)) throw Error('持股欄位無效');
    }
    swingPositions = value;
  } catch(e) { swingStorageError='持股紀錄無法讀取，原資料保留。請先匯出或備份瀏覽器資料後檢查。'; swingPositions={active:{},closed:[]}; }
  return swingPositions;
}
function persistSwingPositions(next) {
  if (swingStorageError) throw Error(swingStorageError);
  localStorage.setItem(SWING_POSITION_KEY, JSON.stringify({...next,version:1}));
  swingPositions=next;
}
window.addEventListener('storage', event => {
  if (event.key === SWING_POSITION_KEY || event.key === null) {
    swingPositions=null; swingStorageError=''; renderSwingBoard();
  }
});
function swingDecision(s) { return Swing.entry(s,S.meta); }
function swingBadge(s) {
  const d=swingDecision(s);
  return `<div class="et" style="color:var(--${d.tone})" title="${Swing.esc([...d.reasons,...d.missing].join('；'))}">${Swing.esc(d.label)}</div>`;
}
function swingCard(s) {
  const d=swingDecision(s);
  const reasons=[...d.reasons,...d.missing];
  const reference=d.stop && d.target ? `模型參考：停損 ${Swing.money(d.stop)}；目標 ${Swing.money(d.target)}；假設以快照價進場之成本後風報 ${Number.isFinite(d.netRR)?d.netRR.toFixed(2)+'R':'待確認'}` : '尚無完整有效的模型停損／目標價，不補預設百分比。';
  return `<div class="inv-card"><div class="inv-title">波段進場判斷｜${Swing.esc(d.label)}</div><div class="inv-conclusion">行情截至 ${Swing.esc(S.meta.date)}，非即時報價。${d.horizon?'原訊號天期 '+d.horizon+' 個交易日':'目標 10／20／40 個交易日；原訊號天期未對應'}。<br>${Swing.esc(d.note)}</div><p>${Swing.esc(reference)}</p><p class="pos-note">風報僅作價位比較，採回測費率假設，並非交易成功機率或已驗證的進場計畫。</p><ul>${reasons.map(x=>`<li>${Swing.esc(x)}</li>`).join('') || '<li>等待同策略的進場區間與樣本外驗證。</li>'}</ul><p class="pos-note">型態：${Swing.esc(s.structureLabel || '未知')}；營收：${Swing.esc(Swing.researchText(s.revenueReason))}<br>舊版模型動作只供研究，統一以本卡片的狀態為準。<a href="${performancePageUrl()}#swing-results">查看 10／20／40 日成本後回測</a></p></div>`;
}
function positionAdvice(s,p) {
  if (!p) return '<p class="pos-note">尚未登錄實際持股。加入自選只代表觀察，不會建立買進紀錄。</p>';
  const d=Swing.exit(s,p,S.meta);
  const comparable=Swing.positive(s.price) && Swing.iso(s.date)===Swing.iso(S.meta.date) && Swing.iso(p.date)<=Swing.iso(s.date);
  const pnl=comparable?((s.price/p.cost-1)*100).toFixed(2)+'%':'待更新';
  return `<div class="pos-head"><b style="color:var(--${d.tone})">${Swing.esc(d.label)}</b></div><p>原始停損 ${Swing.money(p.initialStop || p.stop)}｜保存防守價 ${Swing.money(p.stop)}｜保存目標 ${Swing.money(p.target)}<br>截至 ${Swing.esc(S.meta.date)} 已經過 ${d.held===null?'未知':d.held} 個預定交易日／${p.horizon} 日期限（買進日為第 0 日）。<br>成本 ${Swing.money(p.cost)} × ${p.shares.toLocaleString()} 股；快照帳面報酬 ${pnl}（未扣成本）。</p><ul>${d.reasons.map(x=>`<li>${Swing.esc(x)}</li>`).join('')}</ul><p class="pos-note">${d.stale?Swing.esc(d.stale)+'。':''}只檢查已載入快照，不會自動下單或盤中推播；不把每日重算的模型停損套入既有持股。預定交易日尚未完整核對臨時休市。</p>`;
}
function buildPositionEditor(s) {
  if (!/^[A-Za-z0-9]{4,8}$/.test(s.code)) return '<p>股票代碼無效，無法登錄持股。</p>';
  const store=loadSwingPositions(), p=store.active[s.code], oldCost=getPositionCost(s.code);
  const e=Swing.esc, code=e(s.code);
  return `<div class="inv-card"><div class="inv-title">我的實際持股</div><p class="pos-note">${swingStorageError?e(swingStorageError):'僅保存在此瀏覽器，不會上傳。登錄與封存都不會送出股票委託。'}${!p && oldCost?'已找到舊成本紀錄，請確認日期、股數及停損後再登錄。':''}</p><form class="swing-form" onsubmit="saveSwingPosition(event,'${code}')">
    <label>實際平均成本<input id="spos-cost-${code}" type="number" min="0.0001" step="any" required value="${p?.cost || oldCost || ''}" ${p?'readonly':''}></label>
    <label>實際股數<input id="spos-shares-${code}" type="number" min="1" step="1" required value="${p?.shares || ''}"></label>
    <label>買進日期<input id="spos-date-${code}" type="date" required value="${Swing.iso(p?.date)||''}" ${p?'readonly':''}></label>
    <label>檢查期限<select id="spos-horizon-${code}" ${p?'disabled':''}>${[10,20,40].map(h=>`<option value="${h}" ${(p?.horizon||20)===h?'selected':''}>${h} 個交易日</option>`).join('')}</select></label>
    <label>保存防守價<input id="spos-stop-${code}" type="number" min="0.0001" step="any" required value="${p?.stop||''}"></label>
    <label>保存目標價（選填）<input id="spos-target-${code}" type="number" min="0.0001" step="any" value="${p?.target||''}"></label>
    <button class="btn" type="submit">${p?'儲存持股計畫':'確認已買進並登錄'}</button>${p?`<button class="btn" type="button" onclick="archiveSwingPosition('${code}')">封存已結束持股</button>`:''}
    </form><p id="spos-error-${code}" role="alert" style="color:var(--red)"></p>${positionAdvice(s,p)}</div>`;
}
function saveSwingPosition(event,code) {
  event.preventDefault();
  const stock=S.all.find(s=>s.code===code); if (!stock) return;
  const val=k=>document.getElementById(`spos-${k}-${code}`).value;
  const store=loadSwingPositions(), previous=store.active[code];
  const p={...previous,cost:Number(val('cost')),shares:Number(val('shares')),date:val('date'),horizon:Number(val('horizon')),stop:Number(val('stop')),target:val('target')===''?null:Number(val('target'))};
  const today=new Date(Date.now()+8*3600000).toISOString().slice(0,10);
  let error=Swing.validatePosition(p,previous,S.meta.tradingCalendar,today);
  if (!error) {
    p.initialStop=previous?.initialStop || p.stop;
    p.updatedAt=new Date().toISOString();
    p.changes=[...(previous?.changes||[]),{at:p.updatedAt,stop:p.stop,target:p.target,shares:p.shares}];
    try { persistSwingPositions({...store,active:{...store.active,[code]:p}}); }
    catch(e) {error='無法保存：'+e.message;}
  }
  if (error) {document.getElementById(`spos-error-${code}`).textContent=error;return;}
  renderSwingBoard(); renderDB(stock,S.historyMap?.[code]||[]);
}
function archiveSwingPosition(code) {
  const store=loadSwingPositions(), p=store.active[code]; if (!p) return;
  if (!window.confirm('確認這筆持股已結束？僅封存本機追蹤紀錄，不會送出賣單。')) return;
  const active={...store.active};delete active[code];
  try {persistSwingPositions({...store,active,closed:[...store.closed,{...p,code,closedAt:new Date().toISOString()}]});}
  catch(e) {window.alert('無法封存：'+e.message);return;}
  renderSwingBoard();const s=S.all.find(x=>x.code===code);if(s)renderDB(s,S.historyMap?.[code]||[]);
}
function renderSwingBoard() {
  const host=document.getElementById('swing-board'); if (!host) return;
  const store=loadSwingPositions();
  const holdings=Object.entries(store.active).map(([code,p])=>{
    const s=S.all.find(x=>x.code===code)||{code,name:'未出現在目前股票清單',date:null,price:null};
    const decision=Swing.exit(s,p,S.meta);
    return {s,p,decision};
  }).sort((a,b)=>a.decision.rank-b.decision.rank);
  const active={...store.active};let changed=false;
  for(const {s,p,decision} of holdings) if(decision.trigger && !p.trigger) {active[s.code]={...p,trigger:decision.trigger};changed=true;}
  let writeError='';
  if(changed)try{persistSwingPositions({...store,active});}catch(e){writeError='觸發紀錄未能保存：'+e.message;}
  const candidates=S.all.filter(s=>/^[A-Za-z0-9]{4,8}$/.test(s.code) && (s.likely===true || /可分批布局/.test(s.postCloseAction||''))).sort((a,b)=>(b.buyPointScore||0)-(a.buyPointScore||0));
  host.innerHTML=`<div class="inv-title">我的持股退場提醒 <span class="pos-note">${holdings.length} 檔</span></div><p class="pos-note">快照日期 ${Swing.esc(S.meta.date)}；退出提醒優先。自選股不等於已買進。${Swing.esc(swingStorageError || writeError)}</p><div class="swing-grid">${holdings.map(({s,p,decision:d})=>`<article class="swing-tile"><button class="btn" onclick="openDetail('${Swing.esc(s.code)}')">${Swing.esc(s.code+' '+s.name)}</button><b style="color:var(--${d.tone})">${Swing.esc(d.label)}</b><p>${Swing.esc(d.reasons.join('；'))}</p><p>防守 ${Swing.money(p.stop)}｜期限 ${p.horizon} 交易日</p>${!s.date?`<button class="btn" onclick="archiveSwingPosition('${Swing.esc(s.code)}')">封存已結束持股</button>`:''}</article>`).join('')||'<p>尚無實際持股；請在個股詳情登錄成本、股數、日期與防守價。</p>'}</div><div class="inv-title" style="margin-top:18px">波段進場候選 <span class="pos-note">原規則候選 ${candidates.length} 檔；尚未完成進場驗證</span></div><p class="pos-note">排序按原買點分供研究，分數不是勝率。點選個股查看未滿足條件；目前不提供可直接買進的綠燈。</p><div class="swing-grid">${candidates.map(s=>`<article class="swing-tile"><button class="btn" onclick="openDetail('${Swing.esc(s.code)}')">${Swing.esc(s.code+' '+s.name)}</button>${swingBadge(s)}<p>${Swing.esc([...swingDecision(s).reasons,...swingDecision(s).missing].slice(0,3).join('；'))}</p></article>`).join('')||'<p>目前沒有原規則候選。</p>'}</div><p class="pos-note">${store.closed.length} 筆持股已封存。<a href="${performancePageUrl()}#swing-results">查看波段回測與限制</a></p>`;
}
