#!/usr/bin/env python3
"""Offline checks for the shared Java controller and platform wrappers. Requires JDK 17+, no GUI."""
import ctypes
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import unittest

SCRIPTS = Path(__file__).resolve().parent
FIXTURE_SOURCE = r'''
package es.deusto.prog3.githubanalyzer.gui;
import java.nio.file.*;
import java.util.*;
public class DownloadDialogDesktopCheck {
    public static void main(String[] args) throws Exception {
        if (System.getenv("GITHUB_TOKEN") != null) System.exit(91);
        Path root = Path.of("").toRealPath();
        if (!root.equals(Path.of(System.getProperty("githubanalyzer.desktop.fixture.directory")).toRealPath())) System.exit(92);
        Files.createDirectory(Path.of("resources"));
        Files.writeString(Path.of("resources/stats.csv"), "isolated data");
        String mode = System.getenv("TEST_MODE");
        if ("symlink".equals(mode))
            Files.createSymbolicLink(Path.of("link-to-real-data"), Path.of(System.getenv("TEST_REAL_DATA")));
        if ("cleanup-failure".equals(mode)) {
            Files.createDirectory(Path.of("blocked"));
            Files.writeString(Path.of("blocked/keep"), "temporary");
            Files.setPosixFilePermissions(Path.of("blocked"), Set.of());
        }
        if ("wait".equals(mode)) {
            Files.writeString(Path.of(System.getenv("TEST_CHILD_PID")), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(60000);
        }
        System.exit(Integer.parseInt(System.getenv().getOrDefault("TEST_RUN_RESULT", "0")));
    }
}
'''


class DownloadLauncherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        home = os.environ.get('JAVA_HOME')
        if not home and os.name != 'nt' and Path('/usr/libexec/java_home').exists():
            home = subprocess.check_output(['/usr/libexec/java_home'], text=True).strip()
        if not home:
            compiler = shutil.which('javac')
            if not compiler:
                raise RuntimeError('A JDK 17 or newer is required for these launcher tests')
            home = str(Path(compiler).resolve().parent.parent)
        cls.jdk = home
        suffix = '.exe' if os.name == 'nt' else ''
        cls.java = str(Path(home) / 'bin' / ('java' + suffix))
        if os.name != 'nt':
            cls.shared = Path(tempfile.mkdtemp(prefix='launcher-controller-')).resolve()
            cls.addClassCleanup(shutil.rmtree, cls.shared)
            javac = str(Path(home) / 'bin' / 'javac')
            subprocess.run([javac, '--release', '17', '-d', str(cls.shared),
                            str(SCRIPTS / 'DownloadDialogLauncher.java')], check=True)

    def setUp(self):
        self.directory = Path(tempfile.mkdtemp(prefix='launcher revisión ! (checks) ')).resolve()
        self.addCleanup(self.remove_directory)
        name = 'Project with spaces and acéntos ! (test)'
        if os.name != 'nt':
            name += ' "quotes"'
        self.project = self.directory / name
        (self.project / 'src').mkdir(parents=True)
        (self.project / 'src' / 'Example.java').write_text('class Example {}', encoding='utf-8')
        native = self.project / 'test/es/deusto/prog3/githubanalyzer/gui/DownloadDialogDesktopCheck.java'
        native.parent.mkdir(parents=True)
        native.write_text(FIXTURE_SOURCE, encoding='utf-8')
        (self.project / 'lib').mkdir()
        (self.project / 'resources').mkdir()
        if os.name == 'nt':
            scripts = self.project / 'scripts'
            scripts.mkdir()
            for name in ('DownloadDialogLauncher.java', 'check-download-dialogs.bat', 'run-java-checks.bat'):
                shutil.copyfile(SCRIPTS / name, scripts / name)
        self.config = self.project / 'resources/config.properties'
        self.config.write_text('preserve real fixture settings', encoding='utf-8')
        (self.project / 'resources/raw-data').mkdir()
        self.original = self.project / 'resources/raw-data/saved.dat'
        self.original.write_bytes(b'preserve real downloaded data')
        self.temporary = self.directory / 'Temporary data with spaces'
        self.temporary.mkdir()
        self.unrelated = self.temporary / 'unrelated-existing-file'
        self.unrelated.write_bytes(b'never remove unrelated temporary data')
        self.marker = self.directory / 'running.pid'
        self.environment = os.environ.copy()
        for name in ('TEST_MODE', 'TEST_RUN_RESULT', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
            self.environment.pop(name, None)
        self.environment.update(TMPDIR=str(self.temporary), TEMP=str(self.temporary),
                                GITHUB_TOKEN='must-not-reach-the-child', TEST_CHILD_PID=str(self.marker),
                                TEST_REAL_DATA=str(self.project / 'resources'))

    def remove_directory(self):
        # Restore only permissions deliberately changed by the cleanup-failure fixture.
        for workspace in self.temporary.glob('github-analyzer-dialogs-*'):
            blocked = workspace / 'blocked'
            if blocked.exists():
                blocked.chmod(0o700)
        shutil.rmtree(self.directory)

    def command(self):
        if os.name == 'nt':
            return r'cmd.exe /d /v:off /c scripts\check-download-dialogs.bat --no-pause'
        return [self.java, '-cp', str(self.shared), 'DownloadDialogLauncher', str(self.project)]

    def run_launcher(self):
        working_directory = self.project if os.name == 'nt' else self.directory
        return subprocess.run(self.command(), cwd=working_directory, env=self.environment,
                              stdin=subprocess.DEVNULL, text=True, capture_output=True, timeout=25)

    def assert_clean(self):
        self.assertEqual([], list(self.temporary.glob('github-analyzer-dialogs-*')))
        if os.name == 'nt':
            self.assertEqual([self.unrelated], list(self.temporary.iterdir()))
        self.assertEqual('preserve real fixture settings', self.config.read_text(encoding='utf-8'))
        self.assertEqual(b'preserve real downloaded data', self.original.read_bytes())
        self.assertFalse((self.project / 'build').exists())
        self.assertEqual(b'never remove unrelated temporary data', self.unrelated.read_bytes())

    def test_spaced_paths_credentials_and_success_cleanup(self):
        result = self.run_launcher()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Temporary test files were removed', result.stdout)
        self.assert_clean()

    def test_compile_failure_cleans_up(self):
        (self.project / 'src/Example.java').write_text('invalid Java source', encoding='utf-8')
        result = self.run_launcher()
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('All download dialog checks passed', result.stdout)
        self.assert_clean()

    def test_application_failure_keeps_its_exit_code_and_cleans_up(self):
        self.environment['TEST_RUN_RESULT'] = '7'
        result = self.run_launcher()
        self.assertEqual(7, result.returncode)
        self.assertNotIn('All download dialog checks passed', result.stdout)
        self.assert_clean()

    @unittest.skipIf(os.name == 'nt', 'Uses POSIX permissions to simulate a deletion failure')
    def test_cleanup_failure_is_reported_as_failure(self):
        self.environment['TEST_MODE'] = 'cleanup-failure'
        result = self.run_launcher()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Could not clean temporary test folder', result.stderr)
        self.assertNotIn('All download dialog checks passed', result.stdout)
        self.assertEqual(1, len(list(self.temporary.glob('github-analyzer-dialogs-*'))))
        self.assertEqual('preserve real fixture settings', self.config.read_text())

    def test_invalid_temp_directory_does_not_create_test_data(self):
        self.environment.update(TMPDIR=str(self.directory / 'does not exist'), TEMP=str(self.directory / 'does not exist'))
        result = self.run_launcher()
        self.assertNotEqual(0, result.returncode)
        self.assert_clean()

    @unittest.skipUnless(os.name == 'nt', 'Windows wrapper selection check')
    def test_windows_explicit_java_home_works_without_a_jdk_on_path(self):
        self.environment.update(JAVA_HOME=self.jdk, PATH=str(Path(os.environ['SystemRoot']) / 'System32'))
        result = self.run_launcher()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_clean()

    @unittest.skipUnless(os.name == 'nt', 'Windows wrapper selection check')
    def test_windows_incomplete_java_home_does_not_fall_back_to_path(self):
        jdk = self.directory / 'Incomplete JDK with spaces'
        (jdk / 'bin').mkdir(parents=True)
        self.environment.update(JAVA_HOME=str(jdk), PATH=str(Path(self.java).parent) + os.pathsep + self.environment['PATH'])
        result = self.run_launcher()
        self.assertNotEqual(0, result.returncode)
        self.assertIn(r'JAVA_HOME does not contain bin\javac.exe', result.stdout)
        self.assert_clean()

    @unittest.skipUnless(os.name == 'nt', 'Windows wrapper selection check')
    def test_windows_path_uses_the_runtime_next_to_the_compiler(self):
        fake = self.directory / 'Unrelated runtime'
        fake.mkdir()
        shutil.copyfile(Path(os.environ['SystemRoot']) / 'System32/where.exe', fake / 'java.exe')
        self.environment.pop('JAVA_HOME', None)
        self.environment['PATH'] = os.pathsep.join((str(fake), str(Path(self.java).parent), self.environment['PATH']))
        result = self.run_launcher()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_clean()

    @unittest.skipUnless(os.name == 'nt', 'Requires a private Windows console')
    def test_windows_ctrl_c_stops_the_child_and_cleans_up(self):
        from ctypes import wintypes

        self.environment['TEST_MODE'] = 'wait'
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        console = ctypes.WinDLL('kernel32', use_last_error=True)
        console.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
        console.OpenProcess.restype = wintypes.HANDLE
        console.WaitForSingleObject.argtypes = (wintypes.HANDLE, wintypes.DWORD)
        console.CloseHandle.argtypes = (wintypes.HANDLE,)
        with subprocess.Popen(self.command(), cwd=self.project, env=self.environment, text=True,
                              stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              startupinfo=startup, creationflags=subprocess.CREATE_NEW_CONSOLE) as process:
            try:
                deadline = time.monotonic() + 20
                while not self.marker.exists() and process.poll() is None and time.monotonic() < deadline:
                    time.sleep(.02)
                self.assertTrue(self.marker.exists(), 'Interrupted fixture never started')
                child = console.OpenProcess(0x00100000, False, int(self.marker.read_text()))  # SYNCHRONIZE
                self.assertTrue(child, ctypes.get_last_error())
                try:
                    # Only processes in the newly created console receive Ctrl+C.
                    console_processes = (wintypes.DWORD * 1)()
                    had_console = console.GetConsoleProcessList(console_processes, 1) > 0
                    console.FreeConsole()
                    try:
                        self.assertTrue(console.AttachConsole(process.pid), ctypes.get_last_error())
                        self.assertTrue(console.SetConsoleCtrlHandler(None, True), ctypes.get_last_error())
                        self.assertTrue(console.GenerateConsoleCtrlEvent(0, 0), ctypes.get_last_error())  # CTRL_C_EVENT
                        stdout, stderr = process.communicate(timeout=15)
                    finally:
                        console.FreeConsole()
                        if had_console:
                            console.AttachConsole(wintypes.DWORD(-1))  # ATTACH_PARENT_PROCESS
                    self.assertNotEqual(0, process.returncode, stdout + stderr)
                    self.assertEqual(0, console.WaitForSingleObject(child, 5000), 'Child survived Ctrl+C')
                    self.assert_clean()
                finally:
                    console.CloseHandle(child)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate(timeout=5)

    @unittest.skipIf(os.name == 'nt', 'Creating test symlinks on Windows may require extra permissions')
    def test_cleanup_does_not_follow_links_to_real_data(self):
        self.environment['TEST_MODE'] = 'symlink'
        result = self.run_launcher()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_clean()

    @unittest.skipIf(os.name == 'nt', 'Windows requires a console Ctrl+C check; terminate forcibly kills the JVM')
    def test_signals_stop_the_test_child_and_clean_up(self):
        self.environment['TEST_MODE'] = 'wait'
        for stop in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
            with self.subTest(signal=stop):
                self.marker.unlink(missing_ok=True)
                process = subprocess.Popen(self.command(), cwd=self.directory, env=self.environment,
                                           text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                try:
                    deadline = time.monotonic() + 12
                    while not self.marker.exists() and time.monotonic() < deadline:
                        if process.poll() is not None:
                            self.fail('Launcher exited before starting the fixture: ' + str(process.communicate()))
                        time.sleep(.02)
                    self.assertTrue(self.marker.exists())
                    child = int(self.marker.read_text())
                    process.send_signal(stop)
                    process.communicate(timeout=12)
                    self.assertNotEqual(0, process.returncode)
                    with self.assertRaises(ProcessLookupError):
                        os.kill(child, 0)
                    self.assert_clean()
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.communicate(timeout=5)

    @unittest.skipIf(os.name == 'nt', 'Windows requires a console interruption rather than forced termination')
    def test_interruption_during_compilation_cleans_up(self):
        for index in range(100):
            (self.project / 'src' / ('Extra' + str(index) + '.java')).write_text('class Extra' + str(index) + ' {}')
        process = subprocess.Popen(self.command(), cwd=self.directory, env=self.environment,
                                   text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic() + 8
            while not list(self.temporary.glob('github-analyzer-dialogs-*/sources.txt')) and time.monotonic() < deadline:
                time.sleep(.002)
            self.assertTrue(list(self.temporary.glob('github-analyzer-dialogs-*/sources.txt')))
            time.sleep(.08)
            process.send_signal(signal.SIGTERM)
            process.communicate(timeout=12)
            self.assertNotEqual(0, process.returncode)
            self.assertFalse(self.marker.exists())
            self.assert_clean()
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate(timeout=5)

    @unittest.skipIf(os.name == 'nt', 'POSIX wrapper selection check')
    def test_incomplete_java_home_does_not_fall_back_to_path(self):
        jdk = self.directory / 'Incomplete JDK with spaces'
        (jdk / 'bin').mkdir(parents=True)
        runtime = jdk / 'bin/java'
        runtime.write_text('#!/bin/sh\nexit 0\n')
        runtime.chmod(0o755)
        environment = dict(self.environment, JAVA_HOME=str(jdk))
        result = subprocess.run(['/bin/sh', str(SCRIPTS / 'check-download-dialogs.sh')], env=environment,
                                text=True, capture_output=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('complete JDK', result.stderr)
        self.assert_clean()

    @unittest.skipIf(os.name == 'nt', 'POSIX wrapper selection check')
    def test_path_symlinks_select_bootstrap_from_resolved_compiler_folder(self):
        fake = self.directory / 'fakebin'
        fake.mkdir()
        jdk = self.directory / 'Fake paired JDK'
        (jdk / 'bin').mkdir(parents=True)
        log = self.directory / 'selected-runtime'
        for name, source in {
                'javac': '#!/bin/sh\nexit 0\n',
                'java': '#!/bin/sh\nprintf "%s" "$0" > "$TEST_SELECTION_LOG"\n[ "${GITHUB_TOKEN+x}" != x ]\n'}.items():
            tool = jdk / 'bin' / name
            tool.write_text(source)
            tool.chmod(0o755)
        (fake / 'javac').symlink_to(jdk / 'bin/javac')
        for name, source in {'uname': '#!/bin/sh\necho Linux\n', 'java': '#!/bin/sh\nexit 88\n'}.items():
            tool = fake / name
            tool.write_text(source)
            tool.chmod(0o755)
        environment = dict(self.environment, PATH=str(fake) + ':/usr/bin:/bin', TEST_SELECTION_LOG=str(log))
        environment.pop('JAVA_HOME', None)
        result = subprocess.run(['/bin/sh', str(SCRIPTS / 'check-download-dialogs.sh')], env=environment,
                                text=True, capture_output=True, timeout=5)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(jdk / 'bin/java'), log.read_text())
        self.assert_clean()


if __name__ == '__main__':
    unittest.main(verbosity=2)
