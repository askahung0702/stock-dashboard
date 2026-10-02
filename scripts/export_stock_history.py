"""Publish small per-stock histories so a detail view need not fetch the full market."""
import argparse
import json
import os
from pathlib import Path
import re


def export(data_dir):
    source = json.loads((data_dir / 'history.json').read_text(encoding='utf-8-sig'))
    date = source.get('latestDate')
    stocks = source.get('stocks')
    if not isinstance(date, str) or not re.fullmatch(r'\d{8}', date) or not isinstance(stocks, list):
        raise ValueError('History export requires latestDate and stocks')
    codes = set()
    for stock in stocks:
        code = str(stock.get('code', ''))
        if not re.fullmatch(r'\d{4,6}', code) or code in codes or not isinstance(stock.get('history'), list):
            raise ValueError('Invalid or duplicate stock history: ' + code)
        codes.add(code)
    output = data_dir / 'history'
    output.mkdir(parents=True, exist_ok=True)
    total = largest = 0
    for stock in stocks:
        payload = dict(stock, latestDate=date)
        content = json.dumps(payload, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
        target = output / (str(stock['code']) + '.json')
        temporary = target.with_suffix('.json.tmp')
        temporary.write_bytes(content)
        os.replace(temporary, target)
        total += len(content)
        largest = max(largest, len(content))
    return dict(date=date, stockCount=len(stocks), totalBytes=total, largestBytes=largest)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-dir', type=Path, default=Path(__file__).resolve().parents[1] / 'web/data')
    args = parser.parse_args()
    print(json.dumps(export(args.data_dir)))
