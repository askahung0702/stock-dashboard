"""TWSE planned trading calendar. Emergency closures still require quote-date checks."""
from datetime import date, datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import urllib.request

URL = 'https://openapi.twse.com.tw/v1/holidaySchedule/holidaySchedule'

def export_public_calendar(root, output_root=None):
    """Export only validated cached years, never assume missing years are weekdays."""
    from run_stock_job import atomic_json
    root = Path(root)
    years, issues = {}, []
    for path in sorted((root / 'history/trading_calendar').glob('*.json')):
        try:
            cached = json.loads(path.read_text(encoding='utf-8-sig'))
            schedule = parse_schedule(cached['rows'])
            year = schedule['year']
            if path.stem != str(year) or cached.get('source') != URL:
                raise ValueError('Calendar source/year mismatch')
            fetched = datetime.fromisoformat(cached['fetchedAt'])
            if fetched.tzinfo is None:
                raise ValueError('Calendar fetch time requires timezone')
            day = date(year, 1, 1)
            sessions = []
            while day.year == year:
                if decision(schedule, day)[0]:
                    sessions.append(day.isoformat())
                day += timedelta(days=1)
            years[str(year)] = dict(sessions=sessions, source=URL, fetchedAt=cached['fetchedAt'])
        except (OSError, ValueError, KeyError, TypeError) as exc:
            issues.append(dict(file=path.name, error=str(exc)))
    result = dict(schemaVersion=1, generatedAt=datetime.now(timezone.utc).isoformat(),
                  years=years, issues=issues, status='planned_only' if years else 'unavailable',
                  limitations=['官方預定休市表推導交易日；尚未涵蓋臨時休市及個股停牌。'])
    atomic_json(Path(output_root or root) / 'web/data/trading_calendar.json', result)
    return result

def parse_schedule(rows):
    if not isinstance(rows, list) or len(rows) < 10:
        raise ValueError('Official holiday schedule is empty or unexpectedly short')
    entries = {}
    years = set()
    for row in rows:
        raw = str(row['Date'])
        if len(raw) != 7 or not raw.isdecimal():
            raise ValueError('Unsupported official calendar date format')
        day = date(int(raw[:3]) + 1911, int(raw[3:5]), int(raw[5:]))
        text = row['Name'] + ' ' + row['Description']
        if '開始交易' in text or '最後交易' in text:
            opened = True
        elif '放假' in text or '無交易' in text or '補假' in text:
            opened = False
        else:
            raise ValueError('Unrecognized official calendar event: ' + row['Name'])
        entries[day.isoformat()] = dict(open=opened, name=row['Name'])
        years.add(day.year)
    if len(years) != 1 or not any(x.endswith('-01-01') for x in entries) or not any('-12-' in x for x in entries):
        raise ValueError('Official calendar year coverage is incomplete')
    return dict(year=years.pop(), entries=entries)

def decision(schedule, day):
    if day.year != schedule['year']:
        raise ValueError('No verified official schedule for ' + str(day.year))
    entry = schedule['entries'].get(day.isoformat())
    if entry:
        return entry['open'], entry['name']
    return day.weekday() < 5, '一般交易日' if day.weekday() < 5 else '週末休市'

def fetch_rows():
    try:
        with urllib.request.urlopen(URL, timeout=30) as response:
            return json.load(response)
    except OSError:
        if os.name != 'nt':
            raise
        # Windows curl uses the Windows certificate store. TLS validation remains enabled.
        result = subprocess.run(['curl.exe', '--fail', '--silent', '--show-error', '--connect-timeout', '10',
                                 '--max-time', '30', URL], check=True, capture_output=True)
        return json.loads(result.stdout.decode('utf-8-sig'))

def load_schedule(root, day=None):
    day = day or date.today()
    path = Path(root) / f'history/trading_calendar/{day.year}.json'
    cached = None
    try:
        cached = json.loads(path.read_text(encoding='utf-8'))
        schedule = parse_schedule(cached['rows'])
        decision(schedule, day)
        fetched = datetime.fromisoformat(cached['fetchedAt'])
        if (datetime.now(timezone.utc) - fetched).total_seconds() < 86400:
            return schedule
    except (OSError, ValueError, KeyError, TypeError):
        cached = None
    try:
        rows = fetch_rows()
        schedule = parse_schedule(rows)
        decision(schedule, day)
        from run_stock_job import atomic_json
        atomic_json(path, dict(source=URL, fetchedAt=datetime.now(timezone.utc).isoformat(), rows=rows))
        return schedule
    except Exception:
        if cached is not None:
            print('[calendar] Live refresh failed; using the validated planned schedule cached for this year.', flush=True)
            return parse_schedule(cached['rows'])
        raise
