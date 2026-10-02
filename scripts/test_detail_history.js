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
  for (const name of ['swing.js', 'swing-ui.js']) vm.runInContext(fs.readFileSync(path.join(root, 'web', name), 'utf8'), c);
  vm.runInContext(script, c);
  c.latest = latest;
  vm.runInContext('S.all=latest.rows;S.meta=latest;S.hotThemes=new Set();', c);
  return {c, charts, calls, errors, element, run: code => vm.runInContext(code, c)};
}
const ok = payload => ({ok:true,json:async()=>payload});
const deferred = () => {let resolve; const promise=new Promise(r=>resolve=r); return {promise,resolve}};
(async () => {
  const small = {...stock('2330'), latestDate:latest.date};
  const one = setup(async()=>ok(small));
  await one.run('openDetail("2330")');
  assert.deepStrictEqual(one.calls, ['data/history/2330.json']);
  assert.deepStrictEqual(one.charts.slice(-3).map(x=>x.id), ['cv-r','cv-t','cv-h']);
  assert(one.charts.find(x=>x.id==='cv-t').config.data.datasets[1].data.length > 1);
  await one.run('openDetail("2330")');
  assert.strictEqual(one.calls.length, 1, 'reopen uses valid history cache');

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
  if (process.argv.includes('--live-local')) {
    const live = setup(async (url, options) => fetch(new URL(url, 'http://localhost:8788/web/index.html'), options), 'localhost');
    await live.run('openDetail("7723")');
    assert(live.charts.some(x=>x.id==='cv-t') && live.charts.some(x=>x.id==='cv-h'));
    assert(live.run('S.historyMap[7723].length') > 1);
    console.log('Live localhost 7723: ' + live.run('S.historyMap[7723].length') + ' history rows; technical and score-price charts configured');
  }
  console.log('Detail history: charts, small-file loading, fallback, retry, loading state, close and switching races, insufficient data and stale file checks passed');
})().catch(error=>{console.error(error);process.exitCode=1});
