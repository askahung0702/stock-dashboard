import json
from pathlib import Path
import tempfile
import unittest

from export_stock_history import export


class HistoryExportTests(unittest.TestCase):
    def test_each_stock_retains_all_history_and_snapshot_date(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stocks = [dict(code='2330', name='台積電', history=[dict(date='20261001', price=100, score=80)]),
                      dict(code='0050', name='ETF', history=[])]
            (root / 'history.json').write_text(json.dumps(dict(latestDate='20261001', stocks=stocks)), encoding='utf-8')
            report = export(root)
            self.assertEqual(report['stockCount'], 2)
            for stock in stocks:
                payload = json.loads((root / 'history' / (stock['code'] + '.json')).read_text(encoding='utf-8'))
                self.assertEqual(payload, dict(stock, latestDate='20261001'))
            stocks[0]['history'].append(dict(date='20261002', price=101, score=81))
            (root / 'history.json').write_text(json.dumps(dict(latestDate='20261002', stocks=stocks)), encoding='utf-8')
            export(root)
            self.assertEqual(len(json.loads((root / 'history/2330.json').read_text(encoding='utf-8'))['history']), 2)

    def test_invalid_codes_and_duplicates_fail_before_writing(self):
        for codes in [['2330', '../escape'], ['2330', '2330']]:
            with self.subTest(codes=codes), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'history.json').write_text(json.dumps(dict(latestDate='20261001', stocks=[dict(code=c, history=[]) for c in codes])), encoding='utf-8')
                with self.assertRaises(ValueError):
                    export(root)
                self.assertFalse((root / 'history').exists())


if __name__ == '__main__':
    unittest.main()
