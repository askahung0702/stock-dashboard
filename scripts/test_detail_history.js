/* Exercise the real detail loader and chart configuration without a browser. */
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const html = fs.readFileSync(path.join(root, 'web/index.html'), 'utf8');
const script = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
  .map(x => x[1]).find(x => x.includes('const S =')).replace(/\ninit\(\);/, '');
const fixture = JSON.parse(fs.readFileSync(path.join(root, 'web/data/history.json'), 'utf8'));
const latest = JSON.parse(fs.readFileSync(path.join(root, 'web/data/latest.json'), 'utf8'));
const stock = code => fixture.stocks.find(s => s.code === code);
function setup(fetcher, hostname = 'example.github.io') {
  const nodes = new Map(), charts = [], calls = [], errors = [];
  function element(id) {
    if (!nodes.has(id)) nodes.set(id, {id, innerHTML:'', textContent:'', value:'', style:{},
      classList:{add(){},remove(){},contains(){return true}},
      closest(){return element('card-' + id)}, querySelector(selector){return element(id + selector)}});
    return nodes.get(id);
  }
  const c = vm.createContext({URL, AbortController, setTimeout, clearTimeout,
    console:{error(...args){errors.push(args)},log(){}},
    localStorage:{getItem(){return null},setItem(){}},
    window:{addEventListener(){},location:{hostname,protocol:'https:'}},
    document:{getElementById:element,addEventListener(){},querySelectorAll(){return []}},
    Chart:class {constructor(node, config){charts.push({id:node.id, config})}destroy(){}},
    fetch:async (url, options) => {calls.push(url); return fetcher(url, options)}});
  for (const name of ['technical-overlays.js','video-entry.js', 'swing.js', 'swing-ui.js']) vm.runInContext(fs.readFileSync(path.join(root, 'web', name), 'utf8'), c);
  vm.runInContext(script, c);
  c.latest = latest;
  vm.runInContext('S.all=latest.rows;S.meta=latest;S.hotThemes=new Set();', c);
  return {c, charts, calls, errors, element, run: code => vm.runInContext(code, c)};
}
const ok = payload => ({ok:true,json:async()=>payload});
const deferred = () => {let resolve; const promise=new Promise(r=>resolve=r); return {promise,resolve}};
(async () => {
  const small = {...stock('2330'), latestDate:latest.date};
  const one = setup(async url => ok({...stock(url.match(/history\/(\d+)/)[1]), latestDate:latest.date}));
  await one.run('openDetail("2330")');
  assert.deepStrictEqual(one.calls, ['data/history/2330.json']);
  assert.deepStrictEqual(one.charts.slice(-3).map(x=>x.id), ['cv-r','cv-t','cv-h']);
  assert(one.charts.find(x=>x.id==='cv-t').config.data.datasets[1].data.length > 1);
  await one.run('openDetail("2330")');
  assert.strictEqual(one.calls.length, 1, 'reopen uses valid history cache');
  await one.run('updateTechnicalChart({name:"bands",type:"checkbox",checked:true})');
  let config = one.charts.at(-1).config;
  assert.strictEqual(config.data.datasets.filter(series => series.label.startsWith('BB ')).length,3);
  assert(config.data.datasets.find(series => series.label.startsWith('BB 中軌')).data[0] > 0, 'warmup uses history preceding the visible 90 observations');
  await one.run('updateTechnicalChart({name:"fibonacci",type:"checkbox",checked:true})');
  config = one.charts.at(-1).config;
  assert.strictEqual(config.data.datasets.filter(series => series.fibonacci).length,7);
  assert(config.data.datasets.every(series => series.data.length === config.data.labels.length));
  assert(one.element('technical-overlay-note').innerHTML.includes('收盤快照'));
  await one.run('updateTechnicalChart({name:"period",type:"number",value:"30"})');
  assert(one.charts.at(-1).config.data.datasets.some(series => series.label === 'BB 中軌 (30)'));
  const beforeInvalid = one.charts.length;
  await one.run('updateTechnicalChart({name:"deviations",type:"number",value:"99"})');
  assert.strictEqual(one.charts.length,beforeInvalid,'invalid settings leave the chart intact');
  await one.run('updateTechnicalChart({name:"bands",type:"checkbox",checked:false})');
  assert(!one.charts.at(-1).config.data.datasets.some(series => series.label.startsWith('BB ')));
  await one.run('updateTechnicalChart({name:"start",type:"select-one",value:S.technicalOptions[2330].end})');
  assert(!one.charts.at(-1).config.data.datasets.some(series => series.fibonacci));
  assert(one.element('technical-overlay-note').innerHTML.includes('起點日期須早於'));
  await one.run('openDetail("2317")');
  // Each stock retains its own anchors and settings instead of carrying 2330 prices over.
  assert(!one.charts.at(-1).config.data.datasets.some(series => series.fibonacci));
  assert.strictEqual(one.run('S.technicalOptions[2317].bands'), false);
  assert.strictEqual(one.run('S.technicalOptions[2317].period'), 20);

  const fallback = setup(async url => url.includes('/history/') ? {ok:false,status:404} : ok(fixture));
  await fallback.run('openDetail("2330")');
  assert.strictEqual(fallback.calls.length, 2);
  assert(fallback.charts.some(x=>x.id==='cv-t'));

  let failed = true;
  const retry = setup(async()=> {if(failed) throw Error('network'); return ok(small)});
  await retry.run('openDetail("2330")');
  assert(retry.element('history-tech.cc-wrap').innerHTML.includes('重新載入圖表'));
  assert(retry.element('history-trend.cc-wrap').innerHTML.includes('載入失敗'));
  assert.strictEqual(retry.run('S.historyMap[2330]'), undefined);
  failed = false;
  await retry.run('openDetail("2330")');
  assert(retry.charts.some(x=>x.id==='cv-h'));

  const waiting = deferred();
  const slow = setup(async()=>waiting.promise);
  const pending = slow.run('openDetail("2330")');
  assert(slow.element('history-tech.cc-wrap').innerHTML.includes('正在載入'));
  slow.run('closeDetail()');
  waiting.resolve(ok(small));
  await pending;
  assert(!slow.charts.some(x=>x.id==='cv-t'), 'closing stops late repaint');

  const first=deferred(), second=deferred();
  const race=setup(async url=>url.includes('2330')?first.promise:second.promise);
  const a=race.run('openDetail("2330")'), b=race.run('openDetail("2317")');
  second.resolve(ok({...stock('2317'),latestDate:latest.date})); await b;
  const count=race.charts.length;
  first.resolve(ok(small)); await a;
  assert.strictEqual(race.charts.length,count,'old request cannot replace new stock charts');

  const empty=setup(async()=>ok({...small,history:[]}));
  await empty.run('openDetail("2330")');
  assert(empty.element('history-trend.cc-wrap').innerHTML.includes('只有 0 筆'));
  assert(!empty.charts.some(x=>x.id==='cv-h'));

  const stale=setup(async url=>ok(url.includes('/history/')?{...small,latestDate:'20000101'}:fixture));
  await stale.run('openDetail("2330")');
  assert(stale.charts.some(x=>x.id==='cv-t'),'stale stock file falls back to current full history');

  const target = {...stock('7723'), latestDate:latest.date};
  const local = setup(async url => url.startsWith('/api/') ? ok({code:'7723',history:[]}) : ok(target), 'localhost');
  await local.run('openDetail("7723")');
  assert.deepStrictEqual(local.calls, ['/web/data/history/7723.json']);
  assert(local.charts.some(x=>x.id==='cv-h'));
  assert.strictEqual(local.run('S.historyMap[7723].length'), target.history.length);

  const apiEmpty = setup(async url => {
    if (url.includes('/history/')) return {ok:false,status:404};
    return ok(url.startsWith('/api/') ? {code:'7723',history:[]} : fixture);
  }, 'localhost');
  await apiEmpty.run('openDetail("7723")');
  assert(apiEmpty.calls.includes('/api/stock/7723'));
  assert(apiEmpty.calls.includes('/web/data/history.json'));
  assert(apiEmpty.charts.some(x=>x.id==='cv-t'), 'empty API must not suppress valid static history');
  await local.run('updateTechnicalChart({name:"bands",type:"checkbox",checked:true})');
  await local.run('updateTechnicalChart({name:"fibonacci",type:"checkbox",checked:true})');
  assert.strictEqual(local.charts.at(-1).config.data.datasets.filter(series => series.fibonacci).length,7);
  assert.strictEqual(local.charts.at(-1).config.data.datasets.filter(series => series.label.startsWith('BB ')).length,3);
  assert(local.run('S.historyMap[7723].length') > 1);
  const {fixture:entryFixture}=require('./test_video_entry.js');
  const daily=entryFixture();
  const lastDate=latest.date.replace(/^(\d{4})(\d{2})(\d{2})$/,'$1-$2-$3');
  const offset=Date.parse(lastDate)-Date.parse(daily.bars.at(-1).date);
  daily.bars.forEach(b=>b.date=new Date(Date.parse(b.date)+offset).toISOString().slice(0,10));
  daily.expectedSessions=daily.bars.map(b=>b.date);daily.latestDate=latest.date;daily.code='2330';
  const dailyUI=setup(async url=>ok(url.includes('/ohlcv/')?daily:small));
  await dailyUI.run('openDetail("2330")');
  await dailyUI.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:true})');
  let dailyConfig=dailyUI.charts.at(-1).config;
  const entrySeries=dailyConfig.data.datasets.find(s=>s.label==='進場條件成立');
  assert(entrySeries && entrySeries.pointStyle==='triangle');
  assert(dailyConfig.data.datasets.some(s=>s.label==='最近訊號停損'));
  assert(dailyUI.element('technical-entry-note').innerHTML.includes('跌破實體K棒停損'));
  assert(dailyUI.element('technical-overlay-note').innerHTML.includes('每日 OHLCV'));
  assert.strictEqual(dailyUI.run('S.technicalHistory.length'),daily.bars.length);
  assert(dailyConfig.data.datasets.every(s=>s.data.length===dailyConfig.data.labels.length));
  const k=entrySeries.events.findIndex(Boolean);
  assert(dailyConfig.options.plugins.tooltip.callbacks.label({dataset:entrySeries,dataIndex:k}).some(s=>s.includes('140.5')));
  await dailyUI.run('updateTechnicalChart({name:"bands",type:"checkbox",checked:true})');
  await dailyUI.run('updateTechnicalChart({name:"fibonacci",type:"checkbox",checked:true})');
  dailyConfig=dailyUI.charts.at(-1).config;
  assert(dailyConfig.data.datasets.filter(s=>s.fibonacci).length===7 && dailyConfig.data.datasets.filter(s=>s.label.startsWith('BB ')).length===3);
  const callsBefore=dailyUI.calls.length;
  await dailyUI.run('updateTechnicalChart({name:"entryFilters",type:"checkbox",checked:false})');
  assert.strictEqual(dailyUI.calls.length,callsBefore,'filter toggle reuses daily OHLCV');
  await dailyUI.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:false})');
  assert(!dailyUI.charts.at(-1).config.data.datasets.some(s=>s.entryMarkers));
  assert.strictEqual(dailyUI.run('S.technicalHistory.length'),small.history.length,'off returns to snapshot data');
  const slowDaily=deferred();
  const dailyRace=setup(async url=>url.includes('/ohlcv/')?slowDaily.promise:ok(small));
  await dailyRace.run('openDetail("2330")');
  const pendingDaily=dailyRace.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:true})');
  dailyRace.run('closeDetail()');const countBefore=dailyRace.charts.length;
  slowDaily.resolve(ok(daily));await pendingDaily;
  assert.strictEqual(dailyRace.charts.length,countBefore,'closing during daily load cannot repaint');
  let failedDaily=true;
  const dailyRetry=setup(async url=>url.includes('/ohlcv/')?(failedDaily?{ok:false,status:503}:ok(daily)):ok(small));
  await dailyRetry.run('openDetail("2330")');
  await dailyRetry.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:true})');
  assert(dailyRetry.element('technical-entry-note').textContent.includes('503'));
  failedDaily=false;
  await dailyRetry.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:true})');
  assert(dailyRetry.charts.at(-1).config.data.datasets.some(s=>s.entryMarkers));
  const noDaily=setup(async url=>ok(url.includes('/ohlcv/')?{...daily,available:false,bars:[]}:small));
  await noDaily.run('openDetail("2330")');
  await noDaily.run('updateTechnicalChart({name:"entrySignals",type:"checkbox",checked:true})');
  assert(!noDaily.charts.at(-1).config.data.datasets.some(s=>s.entryMarkers));
  assert(noDaily.element('technical-entry-note').textContent.includes('無法確認'));
  if (process.argv.includes('--live-local')) {
    const live = setup(async (url, options) => fetch(new URL(url, 'http://localhost:8788/web/index.html'), options), 'localhost');
    await live.run('openDetail("7723")');
    assert(live.charts.some(x=>x.id==='cv-t') && live.charts.some(x=>x.id==='cv-h'));
    assert(live.run('S.historyMap[7723].length') > 1);
    console.log('Live localhost 7723: ' + live.run('S.historyMap[7723].length') + ' history rows; technical and score-price charts configured');
  }
  console.log('Detail history: charts, small-file loading, fallback, retry, loading state, close and switching races, insufficient data and stale file checks passed');
})().catch(error=>{console.error(error);process.exitCode=1});
