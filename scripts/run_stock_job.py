"""Serialize local pipeline jobs, survive stale files, and report real exit status.

Nested export inherits the active lease. No Google Drive authorization is required.
Windows Job Objects terminate descendants when the supervisor exits unexpectedly.
"""
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]

def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(tmp, path)

def stamp():
    return datetime.now(timezone.utc).isoformat()

def configure_output():
    # BAT redirection may use cp950. Diagnostic text must never replace the
    # child's actual failure with a secondary UnicodeEncodeError.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, 'reconfigure'):
            stream.reconfigure(errors='backslashreplace')

class PipelineLock:
    def __init__(self, path):
        self.path = Path(path)
        self.file = None

    def acquire(self, timeout=7200):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.file = self.path.open('a+b')
        if self.path.stat().st_size == 0:
            self.file.write(b'0')
            self.file.flush()
        deadline = time.monotonic() + timeout
        while True:
            try:
                self.file.seek(0)
                if os.name == 'nt':
                    import msvcrt
                    msvcrt.locking(self.file.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self.file, fcntl.LOCK_EX | fcntl.LOCK_NB)
                return
            except OSError:
                if time.monotonic() >= deadline:
                    self.file.close()
                    self.file = None
                    raise TimeoutError('Pipeline is busy; lock wait expired. No analysis or publication started.')
                time.sleep(0.25)

    def close(self):
        if self.file:
            self.file.close()  # OS releases the lock, including after abnormal process termination.
            self.file = None

def inherited_lease(root):
    token = os.environ.get('STOCK_PIPELINE_TOKEN')
    if not token:
        return False
    try:
        owner = json.loads((root / '.stock-pipeline/owner.json').read_text(encoding='utf-8'))
        if owner['token'] != token or owner['root'] != str(root.resolve()):
            return False
        probe = PipelineLock(root / '.stock-pipeline/job.lock')
        try:
            probe.acquire(0)
        except TimeoutError:
            return True
        finally:
            probe.close()
    except (OSError, ValueError, KeyError):
        pass
    return False

def protect_process_tree():
    if os.name != 'nt':
        return None
    # Put this supervisor into a kill-on-close job BEFORE spawning children.
    # The handle is deliberately retained until process exit, never inherited.
    class BASIC(ctypes.Structure):
        _fields_ = [('PerProcessUserTimeLimit', ctypes.c_longlong), ('PerJobUserTimeLimit', ctypes.c_longlong),
                    ('LimitFlags', wintypes.DWORD), ('MinimumWorkingSetSize', ctypes.c_size_t),
                    ('MaximumWorkingSetSize', ctypes.c_size_t), ('ActiveProcessLimit', wintypes.DWORD),
                    ('Affinity', ctypes.c_size_t), ('PriorityClass', wintypes.DWORD), ('SchedulingClass', wintypes.DWORD)]
    class IO(ctypes.Structure):
        _fields_ = [(x, ctypes.c_ulonglong) for x in ['ReadOperationCount', 'WriteOperationCount',
                    'OtherOperationCount', 'ReadTransferCount', 'WriteTransferCount', 'OtherTransferCount']]
    class EXTENDED(ctypes.Structure):
        _fields_ = [('BasicLimitInformation', BASIC), ('IoInfo', IO), ('ProcessMemoryLimit', ctypes.c_size_t),
                    ('JobMemoryLimit', ctypes.c_size_t), ('PeakProcessMemoryUsed', ctypes.c_size_t),
                    ('PeakJobMemoryUsed', ctypes.c_size_t)]
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
    kernel.CreateJobObjectW.restype = wintypes.HANDLE
    kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
    kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    handle = kernel.CreateJobObjectW(None, None)
    limits = EXTENDED()
    limits.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    if not handle or not kernel.SetInformationJobObject(handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
        raise ctypes.WinError(ctypes.get_last_error())
    if not kernel.AssignProcessToJobObject(handle, kernel.GetCurrentProcess()):
        raise ctypes.WinError(ctypes.get_last_error())
    return handle

def normalize_args(args):
    values = []
    for arg in args:
        value = arg.strip().lower()
        if value.startswith('--stage=') or value.startswith('--mode='):
            value = value.split('=', 1)[1]
        values.append(value)
    return values

def core_command(root, args):
    # Modes and numeric limits only; never feed arbitrary metacharacters to cmd.exe.
    modes = {'intraday-close', 'intraday', 'close', 'official-chip', 'official-chips', 'full',
             'news-event', 'news-only', 'market-futures', 'futures-price', 'market-futures-night',
             'futures-night', 'night-futures', 'futures-position', 'taifex-position',
             'shareholder-insider', 'shareholder-distribution', 'insider-transfer', 'export', 'export-now', 'data-integrity', 'ohlcv'}
    args = normalize_args(args)
    if len(args) > 3 or any(not (arg in modes or re.fullmatch(r'[+-]?\d+', arg)) for arg in args):
        raise ValueError('Unsupported BAT arguments; use a documented mode and numeric limits.')
    core = str(root / 'run_stock_analysis_core.bat')
    return '"' + os.environ.get('COMSPEC', 'cmd.exe') + '" /d /s /c ""' + core + '" ' + ' '.join(args) + '"'

def step_results(log_text, mode):
    """Distinguish failed collection, reports and publication, including legacy logs."""
    result = {}
    for step, recorded_mode, code in re.findall(
            r'\[pipeline-step\] (collection|reports) mode=([\w-]+) exit=(\d+)', log_text):
        if step == 'reports' or recorded_mode == mode:
            result[step + 'ExitCode'] = int(code)
    if 'Auto push failed.' in log_text:
        result['publicationExitCode'] = 1
    elif 'GitHub auto push completed.' in log_text:
        result['publicationExitCode'] = 0
    # The full BAT reaches this line only after collection and required reports pass.
    if mode == 'full' and 'Auto-pushing site updates to GitHub...' in log_text:
        result.setdefault('collectionExitCode', 0)
        result.setdefault('reportsExitCode', 0)
    for phase in ('collection', 'reports', 'publication'):
        if result.get(phase + 'ExitCode', 0) != 0:
            result['failurePhase'] = phase
            break
    return result

def scheduled_window(mode, now):
    """Catch-up jobs cannot turn yesterday's missed close into today's morning snapshot."""
    starts = {'intraday-close': (13, 40), 'intraday': (13, 40), 'close': (17, 0),
              'official-chip': (20, 15), 'official-chips': (20, 15), 'full': (21, 0)}
    start = starts.get(mode)
    return start is None or (now.hour, now.minute) >= start

def run(args):
    configure_output()
    scheduled = "--scheduled" in args or os.environ.get("STOCK_SCHEDULED_RUN") == "1"
    args = normalize_args([arg for arg in args if arg != "--scheduled"])
    root = ROOT.resolve()
    command = core_command(root, args)
    if inherited_lease(root):
        return subprocess.call(command, cwd=root)
    lease = PipelineLock(root / '.stock-pipeline/job.lock')
    run_id = datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8]
    status_path = root / 'logs/pipeline' / (run_id + '.json')
    log_path = status_path.with_suffix('.log')
    state = dict(runId=run_id, mode=args[0] if args else 'full', args=args, status='waiting', startedAt=stamp())
    atomic_json(status_path, state)
    print('[pipeline] Waiting for project lock. Run: ' + run_id, flush=True)
    acquired = False
    process = None
    try:
        lease.acquire(float(os.environ.get('STOCK_PIPELINE_WAIT_SECONDS', '7200')))
        acquired = True
        global _job_handle
        _job_handle = protect_process_tree()
        token = uuid.uuid4().hex
        atomic_json(root / '.stock-pipeline/owner.json', dict(token=token, pid=os.getpid(), root=str(root)))
        mode = args[0] if args else 'full'
        scheduled_price_modes = {'full', 'intraday-close', 'intraday', 'close', 'official-chip', 'official-chips',
                                 'market-futures', 'futures-price'}
        if scheduled and not scheduled_window(mode, datetime.now()):
            state.update(status='skipped_outside_stage_window', exitCode=0,
                         reason='Missed scheduled stage cannot run before its current trading-day window.')
            return 0
        if mode in scheduled_price_modes or mode.isdecimal():
            from trading_calendar import load_schedule, decision
            opened, reason = decision(load_schedule(root), datetime.now().date())
            if not opened:
                state.update(status='skipped_nontrading_day', exitCode=0, reason=reason)
                print('[calendar] Skipping collection/export on non-trading day.', flush=True)
                return 0
        from build_stock import build, java_tools
        previous_count = 0
        try:
            previous_count = len(json.loads((root / 'web/data/latest.json').read_text(encoding='utf-8-sig'))['rows'])
        except (OSError, ValueError, KeyError, TypeError):
            pass
        runtime, compiler = java_tools()
        classes = build(root, compiler)
        env = os.environ.copy()
        env.update(STOCK_PIPELINE_TOKEN=token, STOCK_CLASSES_DIR=str(classes), STOCK_JAVA_CMD=runtime,
                   STOCK_PYTHON=sys.executable, STOCK_ANALYSIS_NO_PAUSE='1',
                   STOCK_PREVIOUS_ROW_COUNT=str(previous_count), STOCK_PIPELINE_STARTED_EPOCH=str(time.time()))
        if mode in scheduled_price_modes or mode.isdecimal():
            env['STOCK_EXPECTED_DATA_DATE'] = datetime.now().strftime('%Y%m%d')
        state.update(status='running', acquiredAt=stamp(), log=str(log_path))
        atomic_json(status_path, state)
        atomic_json(root / 'logs/pipeline_status.json', state)
        print('[pipeline] Running; detailed log: ' + str(log_path), flush=True)
        with log_path.open('wb') as log:
            process = subprocess.Popen(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
            result = process.wait(timeout=float(os.environ.get('STOCK_PIPELINE_TIMEOUT_SECONDS', '21600')))
        state.update(status='completed' if result == 0 else 'failed', exitCode=result)
        log_text = log_path.read_text(encoding='utf-8', errors='replace')
        state.update(step_results(log_text, mode))
        if result:
            print(log_text[-6000:], flush=True)
        return result
    except BaseException as exc:
        state.update(status='failed', exitCode=1, error=str(exc))
        if process is not None and process.poll() is None:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            process.wait(timeout=30)
        print('[pipeline] FAILED: ' + str(exc), file=sys.stderr, flush=True)
        return 1
    finally:
        state['finishedAt'] = stamp()
        atomic_json(status_path, state)
        if acquired:
            atomic_json(root / 'logs/pipeline_status.json', state)
        lease.close()
        print('[pipeline] ' + state['status'] + ': ' + run_id, flush=True)

if __name__ == '__main__':
    sys.exit(run(sys.argv[1:]))
