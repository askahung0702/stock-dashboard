"""Exercise publishing against local bare remotes; never contacts GitHub."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('auto_git_push.ps1')
BAT = SCRIPT.parent.parent / 'run_stock_analysis.bat'
POWERSHELL = r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / 'remote.git'
        self.repo = self.root / 'publisher'
        self.git(self.root, 'init', '--bare', '--initial-branch=main', str(self.remote))
        self.git(self.root, 'clone', str(self.remote), str(self.repo))
        self.configure(self.repo)
        (self.repo / 'scripts').mkdir()
        shutil.copyfile(SCRIPT, self.repo / 'scripts/auto_git_push.ps1')
        (self.repo / 'web/data').mkdir(parents=True)
        self.site = self.repo / 'web/data/latest.json'
        self.site.write_text('initial\n')
        (self.repo / 'notes.txt').write_text('initial\n')
        self.git(self.repo, 'add', '.')
        self.git(self.repo, 'commit', '-m', 'initial')
        self.git(self.repo, 'push', 'origin', 'main')

    def git(self, cwd, *args, check=True):
        return subprocess.run(['git', '-C', str(cwd), *args], check=check,
                              capture_output=True, text=True)

    def configure(self, repo):
        self.git(repo, 'config', 'user.name', 'Publish Test')
        self.git(repo, 'config', 'user.email', 'publish-test@example.invalid')
        self.git(repo, 'config', 'commit.gpgsign', 'false')

    def publish(self, success=True):
        env = os.environ.copy()
        env.pop('STOCK_SKIP_AUTO_PUSH', None)
        result = subprocess.run([POWERSHELL, '-NoProfile', '-ExecutionPolicy',
                                 'Bypass', '-File', str(self.repo / 'scripts/auto_git_push.ps1')],
                                cwd=self.repo, env=env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)

    def other_commit(self, path, content):
        other = self.root / 'other'
        self.git(self.root, 'clone', str(self.remote), str(other))
        self.configure(other)
        (other / path).write_text(content)
        self.git(other, 'add', path)
        self.git(other, 'commit', '-m', 'remote edit')
        self.git(other, 'push', 'origin', 'main')

    def test_merge_divergence_preserves_uncommitted_work(self):
        self.other_commit('remote.txt', 'remote\n')
        (self.repo / 'notes.txt').write_text('user work\n')
        self.site.write_text('updated\n')
        self.publish()
        self.assertEqual((self.repo / 'notes.txt').read_text(), 'user work\n')
        self.assertEqual((self.repo / 'remote.txt').read_text(), 'remote\n')
        self.assertEqual(self.git(self.remote, 'show', 'main:web/data/latest.json').stdout, 'updated\n')

    def test_retries_commit_without_new_file_changes(self):
        self.site.write_text('pending\n')
        self.git(self.repo, 'add', 'web/data/latest.json')
        self.git(self.repo, 'commit', '-m', 'previously failed push')
        self.publish()
        self.assertEqual(self.git(self.remote, 'show', 'main:web/data/latest.json').stdout, 'pending\n')

    def test_conflict_fails_and_aborts_merge(self):
        self.other_commit('web/data/latest.json', 'remote edit\n')
        self.site.write_text('local edit\n')
        self.publish(success=False)
        self.assertEqual(self.site.read_text(), 'local edit\n')
        self.assertFalse((self.repo / '.git/MERGE_HEAD').exists())
        self.assertEqual(self.git(self.remote, 'show', 'main:web/data/latest.json').stdout, 'remote edit\n')

    def test_fetch_failure_keeps_site_commit(self):
        self.site.write_text('pending\n')
        self.git(self.repo, 'remote', 'set-url', 'origin', str(self.root / 'missing.git'))
        self.publish(success=False)
        self.assertEqual(self.git(self.repo, 'show', 'HEAD:web/data/latest.json').stdout, 'pending\n')

    def test_staged_user_changes_are_untouched(self):
        (self.repo / 'notes.txt').write_text('staged work\n')
        self.git(self.repo, 'add', 'notes.txt')
        self.publish(success=False)
        self.assertEqual(self.git(self.repo, 'diff', '--cached', '--name-only').stdout.strip(), 'notes.txt')

    def test_batch_export_failure_is_nonzero(self):
        # Execute the real BAT branches with a failing export helper, without
        # compiling Java, collecting data or publishing anything.
        original = BAT.read_text(encoding='utf-8')
        (self.repo / 'scripts/request_export.ps1').write_text('exit 7\n')
        for label in ['after_market_data_run', 'after_intraday_close_run',
                      'after_close_run', 'after_official_chip_run', 'after_news_event_run']:
            harness = self.repo / 'probe.bat'
            harness.write_text('@echo off\nsetlocal\nset EXIT_CODE=0\n'
                               f'set POWERSHELL_CMD={POWERSHELL}\nset RUN_MODE=close\n'
                               'set STOCK_SKIP_EXPORT_REQUEST=\nset HAS_LIMITED_ARGS=\n'
                               'set SHOULD_PAUSE=0\nset LOCK_ACQUIRED=\ngoto ' + label + '\n' +
                               original, encoding='utf-8')
            result = subprocess.run(['cmd.exe', '/d', '/c', str(harness)],
                                    capture_output=True, text=True, timeout=15)
            self.assertNotEqual(result.returncode, 0, label)


if __name__ == '__main__':
    unittest.main()
