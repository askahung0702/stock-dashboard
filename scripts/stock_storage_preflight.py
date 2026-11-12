"""Check the data volume and reopen SQLite after transient I/O failures."""
import os
from pathlib import Path
import shutil
import sqlite3
import time
import uuid


def probe(root):
    free = shutil.disk_usage(root).free
    if free < 1024 ** 3:
        raise OSError('Data volume has less than 1 GiB free; collection stopped before updating snapshots')
    directory = root / '.stock-pipeline'
    directory.mkdir(exist_ok=True)
    marker = directory / ('storage-probe-' + uuid.uuid4().hex + '.tmp')
    try:
        with marker.open('xb') as handle:
            handle.write(b'stock storage write/read probe\n')
            handle.flush()
            os.fsync(handle.fileno())
        if marker.read_bytes() != b'stock storage write/read probe\n':
            raise OSError('Storage probe read differs from write')
    finally:
        marker.unlink(missing_ok=True)
    for name, query in (
        ('stock_history_db.sqlite', 'select data_date from shareholder_concentration_weekly order by data_date desc limit 1'),
        ('market_ohlcv.sqlite', 'select symbol,volume from bars where valid=1 and volume>0 order by date desc limit 1'),
    ):
        path = root / 'history' / name
        if not path.exists():
            continue
        # A new connection for every probe avoids reusing an errored pager.
        connection = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True, timeout=5)
        try:
            connection.execute(query).fetchall()
        finally:
            connection.close()
    return dict(status='ok', freeBytes=free)


def retry_read(operation, attempts=3):
    for attempt in range(attempts):
        try:
            return operation()
        except sqlite3.OperationalError as exc:
            code = getattr(exc, 'sqlite_errorcode', 0) & 0xff
            if code not in (sqlite3.SQLITE_IOERR, sqlite3.SQLITE_BUSY, sqlite3.SQLITE_LOCKED) or attempt + 1 == attempts:
                raise
            print('[storage] Transient SQLite I/O/lock failure; reopening before retry ' + str(attempt + 2), flush=True)
            time.sleep(2 * (attempt + 1))


def check(root, attempts=3):
    return retry_read(lambda: probe(Path(root)), attempts)
