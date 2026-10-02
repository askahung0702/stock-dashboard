"""Export only public daily market bars for the chart, read-only from the local archive."""
import argparse
from datetime import datetime
import json
import math
import os
from pathlib import Path
import re
import sqlite3


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    body=json.dumps(value,ensure_ascii=False,separators=(',', ':'),allow_nan=False).encode('utf-8')
    temporary=path.with_suffix('.json.tmp')
    temporary.write_bytes(body)
    os.replace(temporary,path)
    return len(body)


def export(root, archive=None):
    latest=json.loads((root/'web/data/latest.json').read_text(encoding='utf-8-sig'))
    quality=json.loads((root/'web/data/ohlcv_quality.json').read_text(encoding='utf-8-sig'))
    calendar=json.loads((root/'web/data/trading_calendar.json').read_text(encoding='utf-8-sig'))
    day=datetime.strptime(latest['date'],'%Y%m%d').date().isoformat()
    sessions=sorted(set(d for year in calendar.get('years',{}).values() for d in year.get('sessions',[]) if d<=day))
    db=archive or root/'history/market_ohlcv.sqlite'
    if not db.exists():
        raise ValueError('Daily OHLCV archive missing; retained chart exports are not updated')
    con=sqlite3.connect(db.resolve().as_uri()+'?mode=ro',uri=True)
    con.row_factory=sqlite3.Row
    total=available=0
    codes=set()
    try:
        for s in latest['rows']:
            code=str(s['code'])
            if not re.fullmatch(r'\d{4,6}',code) or code in codes: raise ValueError('Invalid or repeated code')
            codes.add(code)
            symbol=code+('.TW' if s['market']=='TWSE' else '.TWO')
            source=con.execute('SELECT * FROM sources WHERE symbol=?',(symbol,)).fetchone()
            q=quality.get('stocks',{}).get(code,{})
            ready=quality.get('date')==latest['date'] and q.get('available') is True and source and source['status']=='received' and source['fetched_at']==q.get('fetchedAt')
            bars=[]
            if ready:
                for row in con.execute('SELECT date,open,high,low,close,volume,valid FROM bars WHERE symbol=? AND date<=? ORDER BY date',(symbol,day)):
                    bar=dict(row);bar['valid']=bool(bar['valid'])
                    for k,v in list(bar.items()):
                        if isinstance(v,float) and not math.isfinite(v):bar[k]=None;bar['valid']=False
                    bars.append(bar)
            ready=bool(ready and bars and bars[-1]['date']==day and bars[-1]['valid'] and bars[-1]['volume']>0
                       and isinstance(s.get('price'),(int,float)) and s['price']>0 and bars[-1]['close']
                       and abs(bars[-1]['close']/s['price']-1)<=.005)
            if not ready:bars=[]
            events=[dict(date=r['date'],type=r['type']) for r in con.execute('SELECT date,type FROM events WHERE symbol=? AND date<=? ORDER BY date',(symbol,day))] if ready else []
            payload=dict(schemaVersion=1,code=code,latestDate=latest['date'],available=ready,
                         fetchedAt=source['fetched_at'] if source else None,source='Yahoo Chart',
                         sourceUrl=f'https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1y&interval=1d',
                         priceBasis='provider OHLC; not independently reconciled with exchange',
                         bars=bars,events=events,unexplainedAdjustmentDates=q.get('unexplainedAdjustmentDates',[]),
                         expectedSessions=[d for d in sessions if bars and d>=bars[0]['date']])
            total+=write_json(root/'web/data/ohlcv'/f'{code}.json',payload)
            available+=ready
        summary=dict(schemaVersion=1,latestDate=latest['date'],stockCount=len(codes),availableCount=available,totalBytes=total,
                     fields=['date','open','high','low','close','volume','valid'],source='public Yahoo Chart market data')
        write_json(root/'web/data/ohlcv/index.json',summary)
        return summary
    finally:con.close()


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--archive',type=Path,help='Optional read-only archive path for verification')
    args=parser.parse_args()
    print(json.dumps(export(args.root,args.archive)))
