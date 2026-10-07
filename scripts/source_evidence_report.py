"""Date-bound evidence overlay; never re-date or silently recompute legacy price scores."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import gzip
import json
import math
from pathlib import Path
from ohlcv_report import parse_chart
from run_stock_job import atomic_json

ROOT = Path(__file__).resolve().parents[1]

def memberships(root):
    baskets = []
    for line in (root / 'config/theme_baskets.csv').read_text(encoding='utf-8-sig').splitlines():
        parts = line.strip().split('|')
        if len(parts) < 3 or parts[0].startswith('#'):
            continue
        name = '車用AI' if parts[0] == 'AUTO:AI' else parts[0]
        if name.startswith('AUTO:'):
            continue
        baskets.append((name, set(parts[2].split(','))))
    return baskets

def assess_row(row, date, baskets, bars=None, archive_error=None, financial=None):
    codes = [name for name, members in baskets if row['code'] in members]
    evidence = dict(row.get('sourceEvidence') or {})
    legacy = (row.get('themeEvidence') or {}).get('version') != 'evidence-v2'
    if bars is not None:
        valid = [bar for bar in bars if bar['valid'] and bar['date'].replace('-', '') <= date]
        last = valid[-1] if valid else None
        through = last['date'].replace('-', '') if last else ''
        price = evidence.get('price') or {}
        mismatch = bool(last and isinstance(row.get('price'), (int, float))
                        and not math.isclose(last['close'], row['price'], abs_tol=0.011)
                        and not (not legacy and str(price.get('source', '')).startswith('Official close')))
        evidence['price'] = dict(price, dataDate=through, vendorClose=last['close'] if last else None,
                                 status='missing' if not last else 'carried' if through != date else
                                        'mismatch_requires_recompute' if mismatch else 'current',
                                 source=price.get('source') or 'Yahoo archived OHLCV',
                                 indicatorsNeedRecompute=legacy and mismatch)
    else:
        evidence['price'] = dict(status='missing', dataDate='', issue=archive_error or 'archive_not_available')
    sections = financial or evidence.get('financial') or {}
    evidence['financial'] = sections
    finance_complete = not legacy and all((sections.get(name) or {}).get('complete') is True
                           for name in ['revenue', 'eps', 'epsTtm', 'income', 'balance', 'cashFlow'])
    if not evidence.get('institutional'):
        evidence['institutional'] = dict(status='unknown', dataDate='', source='legacy_snapshot_date_unverified')
    if not evidence.get('broker'):
        evidence['broker'] = dict(status='unknown', dataDate='', source='legacy_snapshot_date_unverified')
    overlay = dict(sourceEvidence=evidence, financialReady=finance_complete,
                   techReady=(evidence['price'].get('status') == 'current' and not legacy),
                   institutionalReady=(evidence['institutional'].get('status') == 'current'),
                   brokerReady=(evidence['broker'].get('status') == 'current'))
    if legacy:
        overlay.update(primaryTheme=codes[0] if codes else '一般', themeTags='、'.join(codes),
                       themeScore=72 if codes else 0,
                       themeEvidence=dict(version='evidence-v2', scoreMeaning='rule_strength_not_probability',
                                          memberships=[dict(theme=name, kind='curated_membership',
                                              source='config/theme_baskets.csv', status='curated_requires_periodic_review') for name in codes],
                                          newsCandidates=[], autoRadarAffectsClassification=False,
                                          repairScope='membership_only_legacy_scores_not_recomputed'))
    overlay['legacyScoresNeedRecompute'] = legacy
    return overlay

def build(root):
    data = json.loads((root / 'web/data/latest.json').read_text(encoding='utf-8-sig'))
    date = data['date']
    baskets = memberships(root)
    cache_path = root / 'history/low_frequency_cache.json'
    cache = json.loads(cache_path.read_text(encoding='utf-8-sig')) if cache_path.exists() else {}
    cache = cache.get('entries', cache)
    stocks = {}
    for row in data['rows']:
        symbol = row['code'] + ('.TWO' if row.get('market') == 'TPEX' else '.TW')
        bars, error = None, None
        try:
            pointer = json.loads((root / 'history/ohlcv_raw/latest' / (symbol + '.json')).read_text(encoding='utf-8-sig'))
            sha = pointer['sha256']
            if len(sha) != 64 or any(c not in '0123456789abcdef' for c in sha):
                raise ValueError('Invalid archive hash')
            raw = root / 'history/ohlcv_raw/versions' / symbol / (sha + '.json.gz')
            with gzip.open(raw, 'rt', encoding='utf-8') as f:
                bars, _ = parse_chart(f.read(), symbol)
        except (OSError, KeyError, ValueError, TypeError) as exc:
            error = type(exc).__name__
        stocks[row['code']] = assess_row(row, date, baskets, bars, error,
                                        (cache.get(row['code']) or {}).get('sections'))
    counts = Counter(v['sourceEvidence']['price']['status'] for v in stocks.values())
    return dict(schemaVersion=1, date=date, count=len(stocks),
                generatedAt=datetime.now(timezone.utc).isoformat(), priceStatusCounts=dict(counts),
                legacyScoreCount=sum(v['legacyScoresNeedRecompute'] for v in stocks.values()), stocks=stocks)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    report = build(args.root)
    atomic_json(args.root / 'web/data/source_evidence.json', report)
    print(json.dumps({k: v for k, v in report.items() if k != 'stocks'}, ensure_ascii=True))

if __name__ == '__main__':
    main()
