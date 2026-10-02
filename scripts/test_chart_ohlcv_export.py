import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from export_chart_ohlcv import export


class ExportTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        (self.root/'web/data').mkdir(parents=True)
        (self.root/'history').mkdir()
        self.db=self.root/'history/market_ohlcv.sqlite'
        con=sqlite3.connect(self.db)
        con.executescript('CREATE TABLE sources(symbol TEXT,status TEXT,fetched_at TEXT);'
                         'CREATE TABLE bars(symbol TEXT,date TEXT,open REAL,high REAL,low REAL,close REAL,volume INTEGER,valid INTEGER);'
                         'CREATE TABLE events(symbol TEXT,date TEXT,type TEXT);')
        con.execute('INSERT INTO sources VALUES (?,?,?)',('7723.TWO','received','2026-10-01T15:00:00Z'))
        con.execute('INSERT INTO bars VALUES (?,?,?,?,?,?,?,?)',('7723.TWO','2026-10-01',18.4,18.4,18.2,18.25,5000,1))
        con.commit();con.close()
        self.write('latest.json',{'date':'20261001','rows':[{'code':'7723','market':'TPEX','price':18.25}]})
        self.quality={'date':'20261001','stocks':{'7723':{'available':True,'fetchedAt':'2026-10-01T15:00:00Z'}}}
        self.write('ohlcv_quality.json',self.quality)
        self.write('trading_calendar.json',{'years':{'2026':{'sessions':['2026-10-01','2026-10-02']}}})

    def write(self,name,payload):
        (self.root/'web/data'/name).write_text(json.dumps(payload),encoding='utf-8')

    def payload(self):
        return json.loads((self.root/'web/data/ohlcv/7723.json').read_text())

    def test_public_fields_and_readonly_source(self):
        before=self.db.read_bytes()
        result=export(self.root)
        self.assertEqual(result['availableCount'],1)
        p=self.payload()
        self.assertEqual(set(p['bars'][0]),{'date','open','high','low','close','volume','valid'})
        self.assertEqual(p['expectedSessions'],['2026-10-01'])
        self.assertEqual(self.db.read_bytes(),before)

    def test_stale_quality_does_not_publish_old_data_as_current(self):
        self.quality['date']='20260930';self.write('ohlcv_quality.json',self.quality)
        export(self.root)
        self.assertFalse(self.payload()['available']);self.assertEqual(self.payload()['bars'],[])

    def test_mismatching_quote_blocks_signals(self):
        self.write('latest.json',{'date':'20261001','rows':[{'code':'7723','market':'TPEX','price':25}]})
        export(self.root)
        self.assertFalse(self.payload()['available'])

    def test_failed_fetch_never_exports_retained_bars_as_current(self):
        con=sqlite3.connect(self.db);con.execute("UPDATE sources SET status='failed'");con.commit();con.close()
        export(self.root)
        self.assertFalse(self.payload()['available'])


if __name__=='__main__':unittest.main()
