"""Versioned vendor OHLCV storage and conservative, retrospective swing diagnostics.

Provider OHLC may already reflect splits. Never apply a split a second time.
Adjusted OHLC is a derived scale, not evidence of an executable historical price.
"""
import argparse
from contextlib import closing
from datetime import date, datetime, timedelta, timezone
import gzip
import hashlib
import json
import math
from pathlib import Path
import sqlite3

from run_stock_job import atomic_json
from trading_calendar import load_schedule, decision, export_public_calendar

ROOT = Path(__file__).resolve().parents[1]
TAIPEI = timezone(timedelta(hours=8))


def number(value, positive=False):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and (not positive or value > 0)


def local_date(timestamp):
    if not number(timestamp) or timestamp != int(timestamp):
        raise ValueError('Invalid timestamp')
    return datetime.fromtimestamp(timestamp, TAIPEI).date().isoformat()


def parse_chart(body, symbol):
    chart = json.loads(body)['chart']
    if chart.get('error') or not isinstance(chart.get('result'), list) or len(chart['result']) != 1:
        raise ValueError('Chart returned an error or no result')
    result = chart['result'][0]
    meta = result['meta']
    if meta.get('symbol') != symbol or meta.get('exchangeTimezoneName') != 'Asia/Taipei' or meta.get('currency') != 'TWD':
        raise ValueError('Unexpected symbol, currency or exchange timezone')
    stamps = result.get('timestamp')
    if not stamps or stamps != sorted(set(stamps)):
        raise ValueError('Timestamps absent, duplicated or unordered')
    quote = result['indicators']['quote'][0]
    arrays = {key: quote[key] for key in ('open', 'high', 'low', 'close', 'volume')}
    adjusted = result['indicators'].get('adjclose', [])
    arrays['adjusted_close'] = adjusted[0]['adjclose'] if adjusted else [None] * len(stamps)
    if any(not isinstance(values, list) or len(values) != len(stamps) for values in arrays.values()):
        raise ValueError('OHLCV arrays must align with every timestamp')
    bars, seen = [], set()
    for i, stamp in enumerate(stamps):
        day = local_date(stamp)
        if day in seen:
            raise ValueError('More than one daily bar for a local trading date')
        seen.add(day)
        bar = dict(date=day, timestamp=stamp, **{key: values[i] for key, values in arrays.items()})
        prices = [bar[key] for key in ('open', 'high', 'low', 'close')]
        valid = all(number(value, True) for value in prices)
        if valid:
            valid = bar['low'] <= min(bar['open'], bar['close']) + 1e-5 and bar['high'] + 1e-5 >= max(bar['open'], bar['close']) and bar['high'] >= bar['low']
        volume = bar['volume']
        valid = valid and number(volume) and volume >= 0 and int(volume) == volume
        bar['valid'] = bool(valid)
        factor = bar['adjusted_close'] / bar['close'] if valid and number(bar['adjusted_close'], True) else None
        bar['adjustment_factor'] = factor if number(factor, True) else None
        # Keep missing bars as missing, never filter arrays independently or fill with zero.
        bars.append(bar)
    events = []
    for kind, values in result.get('events', {}).items():
        if kind not in ('dividends', 'splits'):
            continue
        if not isinstance(values, dict):
            raise ValueError('Invalid corporate action collection')
        for key, event in values.items():
            day = local_date(event['date'])
            if kind == 'dividends' and not number(event.get('amount'), True):
                raise ValueError('Invalid dividend amount')
            if kind == 'splits' and not all(number(event.get(field), True) for field in ('numerator', 'denominator')):
                raise ValueError('Invalid split ratio')
            events.append(dict(key=f'{kind}:{key}', date=day, type=kind, details=event))
    return bars, events


def open_store(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(path)
    con.execute('PRAGMA busy_timeout=30000')
    con.executescript('''
    CREATE TABLE IF NOT EXISTS captures(symbol TEXT, sha256 TEXT, first_fetched_at TEXT, raw_path TEXT, PRIMARY KEY(symbol,sha256));
    CREATE TABLE IF NOT EXISTS sources(symbol TEXT PRIMARY KEY, sha256 TEXT, fetched_at TEXT, status TEXT, error TEXT);
    CREATE TABLE IF NOT EXISTS bars(symbol TEXT, date TEXT, timestamp INTEGER, open REAL, high REAL, low REAL, close REAL, volume INTEGER, adjusted_close REAL, adjustment_factor REAL, valid INTEGER, sha256 TEXT, PRIMARY KEY(symbol,date));
    CREATE TABLE IF NOT EXISTS events(symbol TEXT, event_key TEXT, date TEXT, type TEXT, details TEXT, sha256 TEXT, PRIMARY KEY(symbol,event_key));
    ''')
    return con


def ingest(root, con):
    imported, failures = 0, []
    for pointer in sorted((root/'history/ohlcv_raw/latest').glob('*.json')):
        symbol = pointer.stem
        fetched, digest = None, None
        try:
            metadata = json.loads(pointer.read_text(encoding='utf-8'))
            if metadata.get('symbol') != symbol:
                raise ValueError('Capture pointer symbol mismatch')
            fetched, digest = metadata.get('fetchedAt'), metadata.get('sha256')
            if metadata.get('status') != 'received':
                raise ValueError('Latest fetch failed: ' + str(metadata.get('error', 'unknown')))
            if not fetched or datetime.fromisoformat(fetched).tzinfo is None:
                raise ValueError('Capture has no timezone-aware retrieval time')
            previous = con.execute('SELECT sha256,status,fetched_at FROM sources WHERE symbol=?', (symbol,)).fetchone()
            if previous and tuple(previous[:2]) == (digest, 'received'):
                if previous[2] != fetched:
                    con.execute('UPDATE sources SET fetched_at=? WHERE symbol=?', (fetched, symbol))
                    con.commit()
                continue
            raw = (root/metadata['rawPath']).resolve()
            raw.relative_to((root/'history/ohlcv_raw/versions').resolve())
            body = gzip.decompress(raw.read_bytes())
            if hashlib.sha256(body).hexdigest() != digest:
                raise ValueError('Archived response checksum mismatch')
            bars, events = parse_chart(body, symbol)
            evidence_path = raw.parent / (digest + '.metadata.json')
            evidence = json.loads(evidence_path.read_text(encoding='utf-8')) if evidence_path.exists() else metadata
            first_fetched = evidence['fetchedAt']
            with con:
                con.execute('DELETE FROM bars WHERE symbol=?', (symbol,))
                con.execute('DELETE FROM events WHERE symbol=?', (symbol,))
                fields = ('date', 'timestamp', 'open', 'high', 'low', 'close', 'volume', 'adjusted_close', 'adjustment_factor', 'valid')
                con.executemany('INSERT INTO bars VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', [(symbol, *(bar[key] for key in fields), digest) for bar in bars])
                con.executemany('INSERT INTO events VALUES (?,?,?,?,?,?)', [(symbol, e['key'], e['date'], e['type'], json.dumps(e['details'], ensure_ascii=False), digest) for e in events])
                con.execute('INSERT OR IGNORE INTO captures VALUES (?,?,?,?)', (symbol, digest, first_fetched, metadata['rawPath']))
                con.execute('INSERT OR REPLACE INTO sources VALUES (?,?,?,?,?)', (symbol, digest, fetched, 'received', None))
            imported += 1
        except (OSError, ValueError, TypeError, KeyError, IndexError) as exc:
            with con:
                con.execute('INSERT OR REPLACE INTO sources VALUES (?,?,?,?,?)', (symbol, digest, fetched, 'failed', str(exc)))
            failures.append(dict(symbol=symbol, error=str(exc)))
    return imported, failures


def trading_window(schedule, asof, length):
    dates, day = [], asof
    while len(dates) < length:
        if decision(schedule, day)[0]:
            dates.append(day.isoformat())
        day -= timedelta(days=1)
    return list(reversed(dates))


def diagnostics(bars, events, dates, status, fetched, asof):
    by_date = {bar['date']: bar for bar in bars}
    result = dict(available=False, priceBasis='Yahoo adjusted close; exchange reconciliation pending',
                  corporateActionsVerified=False, pointInTimeVerified=False,
                  fetchedAt=fetched, barsThrough=max(by_date, default=None),
                  barCount=len(bars), eventCount=len(events), events=events[-10:], warnings=[], returns={})
    if status != 'received':
        result['warnings'].append('最新行情收取或解析失敗；保留的舊資料不視為更新成功')
        return result
    if not dates or dates[-1] != asof.isoformat():
        result['warnings'].append('缺少此日期的已驗證交易日曆')
        return result
    # A Yahoo bar on the same date before the close is provisional.
    retrieved = datetime.fromisoformat(fetched).astimezone(TAIPEI)
    if retrieved.date() < asof or (retrieved.date() == asof and (retrieved.hour, retrieved.minute) < (13, 40)):
        result['warnings'].append('資料取得時間早於本日收盤完成，不能當作完整日行情')
        return result
    def usable(bar):
        return bar and bar['valid'] and number(bar.get('adjusted_close'), True) and number(bar.get('adjustment_factor'), True) and bar.get('volume', 0) > 0
    missing = [day for day in dates if not usable(by_date.get(day))]
    result['missingTradingDates'] = missing
    if missing:
        result['warnings'].append('交易日有缺價、零成交或缺少還原數值，不推定為停牌，也不補零')
    latest = by_date.get(dates[-1])
    if not usable(latest):
        result['warnings'].append('最新預期交易日沒有完整 OHLCV 與還原收盤價')
        return result
    result['available'] = True
    result['lastClose'] = latest['close']
    for horizon in (10, 20, 40):
        window = dates[-(horizon + 1):]
        ready = len(window) == horizon + 1 and not any(day in missing for day in window)
        value = (latest['adjusted_close'] / by_date[window[0]]['adjusted_close'] - 1) * 100 if ready else None
        result['returns'][str(horizon)] = round(value, 4) if number(value) else None
    window = dates[-41:]
    if len(window) == 41 and not any(day in missing for day in window):
        peak, worst = 0, 0
        for day in window:
            value = by_date[day]['adjusted_close']
            peak = max(peak, value)
            worst = min(worst, value / peak - 1)
        result['maxDrawdown40Pct'] = round(worst * 100, 4)
    else:
        result['maxDrawdown40Pct'] = None
    # Diagnostic only: do not apply dividends or splits again to provider-adjusted data.
    recent_events = [event for event in events if dates[0] <= event['date'] <= dates[-1]]
    result['recentCorporateActionCount'] = len(recent_events)
    previous = None
    unexplained = []
    for day in dates:
        bar = by_date.get(day)
        factor = bar.get('adjustment_factor') if bar else None
        if number(factor, True) and previous and abs(factor / previous - 1) > .005:
            if not any(abs((date.fromisoformat(event['date']) - date.fromisoformat(day)).days) <= 3 for event in recent_events):
                unexplained.append(day)
        previous = factor if number(factor, True) else None
    result['unexplainedAdjustmentDates'] = unexplained
    if unexplained:
        result['warnings'].append('還原因子跳動未找到對應股利／分割事件，需查核減資或來源修訂')
    result['warnings'].append('第三方調整僅供行情觀察；減資、配股及交易狀態尚未完成官方核對')
    return result


def build_report(root, con, failures):
    snapshot = json.loads((root/'web/data/latest.json').read_text(encoding='utf-8-sig'))
    asof = datetime.strptime(snapshot['date'], '%Y%m%d').date()
    if not snapshot.get('rows'):
        raise ValueError('Snapshot universe empty')
    schedule = load_schedule(root, asof)
    export_public_calendar(root)
    try:
        dates = trading_window(schedule, asof, 41)
    except ValueError:
        dates = []
    con.row_factory = sqlite3.Row
    stocks = {}
    for stock in snapshot['rows']:
        code, market = stock['code'], stock['market']
        if market not in ('TWSE', 'TPEX'):
            raise ValueError('Unknown stock market')
        symbol = code + ('.TWO' if market == 'TPEX' else '.TW')
        source = con.execute('SELECT * FROM sources WHERE symbol=?', (symbol,)).fetchone()
        if source is None:
            continue
        bars = [dict(row) for row in con.execute('SELECT * FROM bars WHERE symbol=? AND date<=? ORDER BY date', (symbol, asof.isoformat()))]
        events = [dict(row) for row in con.execute('SELECT date,type,details FROM events WHERE symbol=? AND date<=? ORDER BY date', (symbol, asof.isoformat()))]
        for event in events:
            event['details'] = json.loads(event['details'])
        stocks[code] = diagnostics(bars, events, dates, source['status'], source['fetched_at'], asof)
        current = stocks[code]
        if current['available'] and number(stock.get('price'), True):
            difference = (current['lastClose'] / stock['price'] - 1) * 100
            current['snapshotPriceDifferencePct'] = round(difference, 4)
            if abs(difference) > .5:
                current['warnings'].append(f'供應商收盤價與主快照差異 {difference:.2f}%，需核對報價時間與來源')
    available = sum(stock['available'] for stock in stocks.values())
    complete = sum(all(stock.get('returns', {}).get(str(h)) is not None for h in (10, 20, 40)) for stock in stocks.values())
    result = dict(schemaVersion=1, date=snapshot['date'], generatedAt=datetime.now(timezone.utc).isoformat(),
                  universeCount=len(snapshot['rows']), attemptedCount=len(stocks), capturedCount=sum(stock['barCount'] > 0 for stock in stocks.values()), availableCount=available,
                  complete40DayCount=complete, status='partial' if complete < len(snapshot['rows']) else 'vendor_data_complete_unverified',
                  source='Yahoo Chart', sources=['https://help.yahoo.com/kb/finance/adjusted-close-sln28256.html'],
                  failures=failures, stocks=stocks,
                  limitations=['供應商 OHLC 可能已包含分割調整，不能當成交易所原始成交價',
                               '還原 OHLC 僅為衍生價格尺度；本頁不模擬交易或提供勝率',
                               '原始版本與取得時間已保留，首次收集以前不具有當時可得資訊證據',
                               '股利／分割事件不代表已涵蓋全部減資、配股與停復牌事件'])
    atomic_json(root/'web/data/ohlcv_quality.json', result)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    with closing(open_store(args.root/'history/market_ohlcv.sqlite')) as con:
        imported, failures = ingest(args.root, con)
        report = build_report(args.root, con, failures)
    print(f"OHLCV imported={imported}, captured={report['capturedCount']}/{report['universeCount']}, available={report['availableCount']}, complete40d={report['complete40DayCount']}, failed={len(failures)}", flush=True)
    # Per-symbol failures remain visible and unusable. Do not stop all market publication
    # for a suspended/delisted symbol; failure to build the report itself still raises.
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
