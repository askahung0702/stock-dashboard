function attachSourceEvidence(stock, report, date) {
  if (!report || report.date !== date || stock.date !== date) return stock;
  const entry = (report.stocks || {})[stock.code];
  return entry ? {...stock, ...entry} : stock;
}

function buildSourceEvidenceCard(s) {
  const evidence = s.sourceEvidence || {};
  const labels = {price:'價格與技術指標', institutional:'法人', broker:'分點'};
  const states = {current:'本交易日', carried:'沿用較早資料', missing:'資料不足', unknown:'日期尚未確認', mismatch_requires_recompute:'價格與指標待重算'};
  const lines = Object.entries(labels).map(([key, label]) => {
    const item = evidence[key] || {};
    return `${label}：${escapeHtml(states[item.status] || '日期尚未確認')}；資料日 ${escapeHtml(item.dataDate || '未知')}；${escapeHtml(item.source || '來源待確認')}`;
  });
  const financial = evidence.financial || {};
  const names = {revenue:'月營收', eps:'單季EPS', epsTtm:'連續四季EPS', income:'損益', balance:'資產負債', cashFlow:'營業及自由現金流'};
  for (const [key, label] of Object.entries(names)) {
    const item = financial[key] || {};
    lines.push(`${label}：${item.complete === true ? '已收取' : '資料不足或尚未核實'}；期間 ${escapeHtml(item.period || '未知')}；收取日 ${escapeHtml(item.fetchedAt || '未知')}`);
  }
  if (s.legacyScoresNeedRecompute) lines.push('已校正題材與來源狀態；本快照的綜合分數仍待完整分析重算。');
  return `<div class="rsn"><div class="rsn-hdr"><span class="rsn-lbl">各來源資料日期與完整度</span></div><div class="rsn-txt">${lines.join('<br>')}</div></div>`;
}

function buildThemeEvidenceCard(s) {
  const evidence = s.themeEvidence || {};
  const memberships = (evidence.memberships || []).map(item => {
    const recent = item.kind === 'recent_news_relation';
    const sources = (item.articles || []).map(v => {
      const url = /^https:\/\//.test(v.url || '') ? v.url : '';
      const title = escapeHtml(v.headline || '');
      return `${escapeHtml(v.publishedDate || '')} ${url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${title}</a>` : title}`;
    }).join('<br>');
    return `<b>${escapeHtml(item.theme || '')}</b>：${recent ? '近期新聞關聯，仍須核對實際業務' : '人工維護的題材清單，須定期核對實際業務'}${sources ? '<br>' + sources : ''}`;
  });
  const candidates = (evidence.newsCandidates || []).slice(0,6).map(item => `${escapeHtml(item.theme || '')}：${escapeHtml(item.headline || '')}（待確認日期及業務關聯）`);
  return `<div class="rsn"><div class="rsn-hdr"><span class="rsn-lbl">題材判斷依據</span></div><div class="rsn-txt">${memberships.join('<br>') || '尚無足夠的題材關聯依據。'}${candidates.length ? '<br>新聞候選：<br>' + candidates.join('<br>') : ''}<br>題材分表示規則證據強度，不是分類正確率或股票上漲機率。</div></div>`;
}
