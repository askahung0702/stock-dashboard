const assert=require('assert');
const indicator=require('../web/video-entry.js');
function fixture() {
  const rows=[];
  const add=(open,high,low,close,volume=10000)=>{
    const date=new Date(Date.UTC(2026,0,1+rows.length)).toISOString().slice(0,10);
    rows.push({date,open,high,low,close,volume,valid:true});
  };
  for(let i=0;i<210;i++)add(100,101,100,100);
  for(let i=0;i<8;i++)add(101+i*9,110+i*9,100+i*9,109+i*9);
  add(175,180,174,179); // peak 218
  add(168,175,165,169);
  add(156,164,153,158); // peak confirmed, no buy
  add(145,146,140,141); // first low at 0.5
  add(141,145,141,144); // first bounce confirmed: observation only
  add(139,140,130.56,135); // deeper 0.382, breakdown only
  add(140.5,145,138,144,22000); // reclaim + bullish + higher volume
  add(143,144,140,142,10000); // later intraday stop
  return {schemaVersion:1,code:'9999',available:true,latestDate:rows.at(-1).date.replaceAll('-',''),bars:rows,
    expectedSessions:rows.map(b=>b.date),events:[],unexplainedAdjustmentDates:[]};
}
const data=fixture(), scan=indicator.scan(data);
const entry=scan.events.find(e=>e.type==='entry');
assert(entry,'full sequential setup with all initial trend filters can pass');
assert.strictEqual(entry.index,224);
assert.strictEqual(entry.stop,140.5,'body low is min(open, close), not wick low');
assert.strictEqual(entry.position,.382);
assert.strictEqual(entry.closed.date,data.bars[225].date);
assert(scan.events.find(e=>e.type==='observe').index===222,'first rebound only produces observation');
assert(scan.events.find(e=>e.type==='break').index===223);
assert.strictEqual(scan.events.filter(e=>e.type==='entry').length,1);
// Append-only invariance of emitted conditions, ignoring later stop status.
for(let i=0;i<data.bars.length;i++) {
  const prefix={...data,bars:data.bars.slice(0,i+1),latestDate:data.bars[i].date.replaceAll('-','')};
  const events=indicator.scan(prefix).events.map(({closed,...e})=>e);
  const known=scan.events.filter(e=>e.index<=i).map(({closed,...e})=>e);
  assert.deepStrictEqual(events,known,`no future confirmation moves an entry onto day ${i}`);
}
const altered=change=>{const p=structuredClone(data);change(p);return indicator.scan(p,{requireFilters:false});};
assert(!altered(p=>p.bars[224].volume=5000).events.some(e=>e.type==='entry'),'price increase without volume is not entry');
assert(!altered(p=>{p.bars[224].close=139;p.bars[224].open=138;}).events.some(e=>e.type==='entry'),'reclaim of the first low is required');
assert(!altered(p=>p.bars[223].low=141).events.some(e=>e.type==='entry'),'must actually break first low');
assert(!altered(p=>p.events=[{date:p.bars[223].date,type:'dividends'}]).events.some(e=>e.type==='entry'),'no setup spanning corporate action');
assert(!altered(p=>p.bars.splice(223,1)).events.some(e=>e.type==='entry'),'missing expected session does not get skipped');
assert(!altered(p=>p.bars[223].valid=false).events.some(e=>e.type==='entry'),'invalid OHLCV resets setup');
assert(!altered(p=>p.expectedSessions=[]).events.some(e=>e.type==='entry'),'calendar absent does not claim confirmed signal');
assert(!indicator.scan({...data,available:false}).events.length);
assert(!indicator.scan({...data,bars:[data.bars[0],data.bars[0]]}).events.length);
assert(!indicator.scan(data,{asof:'20261231'}).events.length,'snapshot mismatch rejects stale data');
const metrics=indicator.indicators(data.bars);
assert.strictEqual(metrics[199].ma200,100);
assert.strictEqual(metrics[27].adx14,0,'flat market ADX is zero after Wilder warmup');
assert.strictEqual(metrics[26].adx14,null,'ADX needs 14 DX values');
assert.strictEqual(metrics[224].avgVolume30,10000,'30-day volume average excludes current bar');
const partial={...data,bars:data.bars.slice(190),expectedSessions:data.expectedSessions.slice(190)};
const filtered=indicator.scan(partial);
assert(filtered.events.some(e=>e.type==='filtered'),'missing MA200 blocks initial trend filter');
assert(!filtered.events.some(e=>e.type==='entry'));
assert(indicator.scan(partial,{requireFilters:false}).events.some(e=>e.type==='entry'),'user can request pattern only');
console.log('Video entry: sequential setup, body stop, price/volume, filters, corporate actions, gaps, Wilder warmup and every-prefix no-lookahead checks passed');
module.exports={fixture};
