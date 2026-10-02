/* Executable interpretation of the user's supplied video summary, not a transcript.
 * Every event is stamped when it becomes knowable; no future bars confirm an earlier buy. */
(function(root) {
  'use strict';
  const positive = v => typeof v === 'number' && Number.isFinite(v) && v > 0;
  const iso = v => String(v || '').replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3');
  function valid(b) {
    return b && /^\d{4}-\d{2}-\d{2}$/.test(b.date) && b.valid !== false &&
      ['open','high','low','close','volume'].every(k => positive(b[k])) &&
      b.low <= Math.min(b.open,b.close) && b.high >= Math.max(b.open,b.close) && b.high >= b.low;
  }
  function indicators(bars) {
    const result=[], segment=[];
    let tr=0, plus=0, minus=0, dxs=[], adx=null;
    for (const b of bars) {
      if (!valid(b) || b.gap) {
        segment.length=0;tr=plus=minus=0;dxs=[];adx=null;result.push({});continue;
      }
      const prev=segment.at(-1); segment.push(b); const n=segment.length;
      const mean = size => n >= size ? segment.slice(-size).reduce((sum,x)=>sum+x.close,0)/size : null;
      const avgVolume = n > 30 ? segment.slice(-31,-1).reduce((sum,x)=>sum+x.volume,0)/30 : null;
      if (prev) {
        const range=Math.max(b.high-b.low,Math.abs(b.high-prev.close),Math.abs(b.low-prev.close));
        const up=b.high-prev.high, down=prev.low-b.low;
        const p=up>0 && up>down ? up : 0, m=down>0 && down>up ? down : 0;
        if (n<=15) { tr+=range;plus+=p;minus+=m; }
        else { tr=tr-tr/14+range;plus=plus-plus/14+p;minus=minus-minus/14+m; }
        if (n>=15) {
          const dx=plus+minus>0 ? Math.abs(plus-minus)/(plus+minus)*100 : 0;
          if (dxs.length<14) {dxs.push(dx);if(dxs.length===14)adx=dxs.reduce((a,x)=>a+x,0)/14;}
          else adx=(adx*13+dx)/14;
        }
      }
      const ret=n>126 ? (b.close/segment[n-127].close-1)*100 : null;
      result.push({ma50:mean(50),ma200:mean(200),return126:ret,adx14:adx,avgVolume30:avgVolume,
        volumeRatio:avgVolume ? b.volume/avgVolume : null});
    }
    return result;
  }
  function filter(metric,b) {
    const checks=[['近126交易日漲幅 < 50%',metric.return126, v=>v<50],
      ['ADX14 > 20',metric.adx14,v=>v>20],
      ['MA50 > MA200',positive(metric.ma50)&&positive(metric.ma200)?metric.ma50-metric.ma200:null,v=>v>0],
      ['成交量 > 前30日均量',metric.avgVolume30,v=>b.volume>v]];
    return {pass:checks.every(([,v,test])=>v!==null && v!==undefined && test(v)),
      reasons:checks.filter(([,v,test])=>v===null || v===undefined || !test(v)).map(([label,v])=>label+(v===null||v===undefined?'（資料不足）':'（未符合）'))};
  }
  function scan(payload, options={}) {
    const asof=iso(options.asof || payload.latestDate);
    if (!payload.available || payload.latestDate?.replaceAll('-','') !== asof.replaceAll('-','')) {
      return {events:[],bars:[],metrics:[],wave:null,status:'每日行情尚未就緒或日期不符，無法確認進場。'};
    }
    const raw=payload.bars || [];
    if (raw.some((b,i)=>!/^\d{4}-\d{2}-\d{2}$/.test(b.date) || (i && b.date<=raw[i-1].date))) {
      return {events:[],bars:[],metrics:[],wave:null,status:'日K日期重複或順序異常，無法確認進場。'};
    }
    const bars=raw.filter(b=>b.date<=asof).map(b=>({...b}));
    const sessions=(payload.expectedSessions || []).filter(d=>d<=asof);
    const expected=new Set(sessions), byDate=new Set(bars.map(b=>b.date));
    for (const day of sessions) if (!byDate.has(day) && day>=bars[0]?.date) bars.push({date:day,valid:false,gap:true});
    bars.sort((a,b)=>a.date.localeCompare(b.date));
    const actions=new Set([...(payload.events||[]).map(e=>e.date),...(payload.unexplainedAdjustmentDates||[])]);
    const metrics=indicators(bars), events=[];
    let wave=null, segmentStart=0;
    const emit = (type,i,extra={}) => {
      const event={type,date:bars[i].date,index:i,price:bars[i].close,...extra};events.push(event);return event;
    };
    for (let i=0;i<bars.length;i++) {
      const b=bars[i], prev=bars[i-1];
      if (!valid(b) || b.gap || actions.has(b.date)) {
        for(const e of events.filter(e=>e.type==='entry'&&!e.closed))e.closed={date:b.date,reason:actions.has(b.date)?'公司行動需重選波段':'行情中斷，後續停損狀態未知'};
        wave=null;segmentStart=i+1;continue;
      }
      for(const e of events.filter(e=>e.type==='entry'&&!e.closed)) {
        if(i>e.index && b.low<e.stop) {
          e.closed={date:b.date,reason:'盤中低點跌破實體K棒停損'};
          emit('stop',i,{stop:e.stop,signalDate:e.date});
        }
      }
      if(!expected.has(b.date)) {wave=null;segmentStart=i+1;continue;}
      if (wave && b.low<=wave.low) {wave=null;segmentStart=i;}
      // Peak at i-2 is only confirmed on i; never backdate a confirmation to the peak.
      const p=i-2;
      if (p>=segmentStart+4 && (!wave || wave.stage==='done')) {
        const peak=bars[p];
        if (bars.slice(p-2,i+1).every(valid) && bars.slice(p-2,i+1).every((x,j)=>j===2 || x.high<peak.high)) {
          const start=Math.max(segmentStart,p-59);let lowIndex=start;
          for(let j=start;j<p;j++)if(bars[j].low<bars[lowIndex].low)lowIndex=j;
          const low=bars[lowIndex].low;
          if(p-lowIndex>=5 && peak.high/low>=1.1) wave={low,high:peak.high,start:bars[lowIndex].date,end:peak.date,
            confirmed:b.date,confirmedIndex:i,stage:'first',trough:null,first:null,broken:null,entry:null};
        }
      }
      if(!wave || wave.stage==='done' || !prev)continue;
      const position=(b.low-wave.low)/(wave.high-wave.low);
      if(b.high>wave.high) {wave=null;continue;}
      if(wave.stage==='first') {
        if(position<.45) {wave.stage='done';continue;}
        if(position<=.55 && (!wave.trough || b.low<wave.trough.low))wave.trough={low:b.low,date:b.date,index:i};
        if(wave.trough && i>wave.trough.index && b.close>b.open && b.close>prev.close && b.close>=wave.trough.low*1.02) {
          wave.first={...wave.trough,confirmed:b.date};wave.stage='break';
          emit('observe',i,{reference:wave.first.low,lowDate:wave.first.date,wave:{start:wave.start,end:wave.end,low:wave.low,high:wave.high}});
        }
      } else if(wave.stage==='break') {
        if(b.low<wave.first.low) {
          const nearDeep=Math.abs(position-.382)<=.05 || Math.abs(position-.236)<=.05;
          if(!nearDeep) {if(position<.186)wave.stage='done';continue;}
          wave.broken={index:i,date:b.date,low:b.low,position};wave.stage='reclaim';
          emit('break',i,{reference:wave.first.low,low:b.low,position});
        }
      }
      if(wave.stage==='reclaim') {
        if(b.low<wave.broken.low) {wave.broken.low=b.low;wave.broken.position=position;}
        if(position<.186 || i-wave.broken.index>3) {wave.stage='done';continue;}
        const m=metrics[i], screening=filter(m,b);
        const priceUp=b.close>prev.close && b.close>b.open && b.close>wave.first.low;
        const volumeUp=positive(m.avgVolume30) && b.volume>prev.volume && b.volume>m.avgVolume30;
        if(priceUp && volumeUp) {
          const passed=options.requireFilters!==false ? screening.pass : true;
          const event=emit(passed?'entry':'filtered',i,{reference:wave.first.low,firstDate:wave.first.date,
            breakDate:wave.broken.date,low:wave.broken.low,stop:Math.min(b.open,b.close),
            position:wave.broken.position,volume:b.volume,volumeRatio:m.volumeRatio,screening,
            wave:{start:wave.start,end:wave.end,low:wave.low,high:wave.high}});
          if(passed)wave.entry=event;
          wave.stage='done';
        }
      }
    }
    const status=!sessions.length?'缺少交易日曆，無法確認進場。':wave?.stage==='first'?'等待 0.5 附近第一次反彈。':
      wave?.stage==='break'?'第一低點已確認，等待跌破前低。':wave?.stage==='reclaim'?'已破前低，等待 3 個交易日內站回且價漲量增。':
      '尚未出現符合完整次序的進場訊號。';
    return {events,bars,metrics,wave,status};
  }
  root.VideoEntry={valid,indicators,filter,scan};
  if(typeof module!=='undefined')module.exports=root.VideoEntry;
})(typeof globalThis!=='undefined'?globalThis:this);
