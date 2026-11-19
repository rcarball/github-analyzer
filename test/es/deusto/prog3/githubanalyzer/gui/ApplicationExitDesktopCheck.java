package es.deusto.prog3.githubanalyzer.gui;

import java.awt.Window;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/** Process-level desktop regression: never replace MainWindow.exitApplication(). */
public final class ApplicationExitDesktopCheck {
    private static final String FIXTURE = "githubanalyzer.desktop.fixture.directory";

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--child".equals(args[0])) {
            runChild(args[1]);
            // A successful child must terminate through the production window handler.
            return;
        }
        check(args.length == 0, "Unexpected arguments");
        runChecks();
    }

    static void runChecks() throws Exception {
        Path root = fixtureDirectory();
        String classpath = String.join(File.pathSeparator, Arrays.stream(
                System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString()).toList());
        String suffix = File.separatorChar == '\\' ? ".exe" : "";
        Path java = Path.of(System.getProperty("java.home"), "bin", "java" + suffix);
        for (String scenario : List.of("idle", "pending-success", "pending-failure", "cancel-download")) {
            Path directory = Files.createTempDirectory(root, "application-exit-");
            Path log = directory.resolve("child.log");
            ProcessBuilder builder = new ProcessBuilder(java.toString(), "-Djava.awt.headless=false",
                    "-Djava.io.tmpdir=" + directory, "-D" + FIXTURE + "=" + directory,
                    "-cp", classpath, ApplicationExitDesktopCheck.class.getName(), "--child", scenario)
                    .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            for (String name : List.of("GITHUB_TOKEN", "GH_TOKEN", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"))
                builder.environment().remove(name);
            Process child = builder.start();
            try {
                check(child.waitFor(15, TimeUnit.SECONDS), "Application did not exit after closing: " + scenario);
                check(child.exitValue() == 0, "Expected exit 0, got " + child.exitValue() + ": " + scenario);
                for (String marker : List.of("window-opened", "close-requested"))
                    check(Files.exists(directory.resolve(marker)), "Missing " + marker + ": " + scenario);
                if (!"idle".equals(scenario)) {
                    for (String marker : List.of("close-waited", "cleanup-finished", "refresh-finished"))
                        check(Files.exists(directory.resolve(marker)), "Exited before " + marker + ": " + scenario);
                }
                if ("pending-failure".equals(scenario))
                    check(Files.exists(directory.resolve("failure-observed")), "Failure path was not exercised");
                if ("cancel-download".equals(scenario))
                    check(Files.exists(directory.resolve("saved-data-read")), "Exited before cancellation recovery");
                System.out.println("PASS: application close " + scenario + " exits its JVM with code 0 after cleanup");
            } catch (Throwable failure) {
                System.err.println("Application exit fixture " + scenario + ":\n" + Files.readString(log));
                throw failure;
            } finally {
                // Timeout/failed assertions are failures, even when cleanup can stop the process.
                if (child.isAlive()) {
                    child.destroy();
                    if (!child.waitFor(5, TimeUnit.SECONDS)) {
                        child.destroyForcibly();
                        check(child.waitFor(5, TimeUnit.SECONDS), "Could not stop failed exit fixture");
                    }
                }
            }
        }
    }

    private static Path fixtureDirectory() throws IOException {
        String configured = System.getProperty(FIXTURE);
        Path directory = Path.of("").toRealPath();
        check(configured != null && directory.equals(Path.of(configured).toRealPath()),
                "Use the isolated desktop-check launcher");
        return directory;
    }

    private static void runChild(String scenario) throws Exception {
        Path directory = fixtureDirectory();
        check(!Files.exists(directory.resolve("resources")), "Child fixture must be fresh");
        Files.createDirectory(directory.resolve("resources"));
        Files.writeString(directory.resolve("resources/config.properties"), "github.user=\ngithub.token=\n");
        Files.writeString(directory.resolve("resources/repositories.txt"), "");
        RepoStats saved = new RepoStats();
        saved.setUrl("https://github.com/fixture/saved");
        saved.setName("saved");
        saved.setAnalysisComplete(true);
        MainWindow[] window = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> window[0] = new MainWindow(List.of(saved)));
        // Flush the credential-free initial check before starting an operation.
        SwingUtilities.invokeAndWait(() -> {
            check(window[0].isShowing(), "Main window did not open");
            mark("window-opened");
        });
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        boolean download = "cancel-download".equals(scenario);
        if (!"idle".equals(scenario)) {
            SwingUtilities.invokeAndWait(() -> {
                JButton refresh = (JButton) field(window[0], "btnRefresh");
                MainWindow.RefreshWorker worker;
                if (download) {
                    MainWindow.DownloadWorker downloading = new MainWindow.DownloadWorker(refresh) {
                        @Override protected List<RepoStats> download() {
                            entered.countDown();
                            try { new CountDownLatch(1).await(); }
                            catch (InterruptedException expected) {
                                await(release);
                                mark("cleanup-finished");
                                throw new java.util.concurrent.CancellationException();
                            }
                            throw new AssertionError("Download should be cancelled");
                        }
                        @Override protected List<RepoStats> readSavedData() {
                            mark("saved-data-read");
                            return List.of(saved);
                        }
                        @Override protected void refreshFinished() {
                            verifyFinished(this, false);
                        }
                    };
                    setField(window[0], "cancelActiveDownload", (Runnable) downloading::requestCancellation);
                    worker = downloading;
                } else {
                    worker = new MainWindow.RefreshWorker(refresh) {
                        @Override protected List<RepoStats> doInBackground() throws IOException {
                            entered.countDown();
                            await(release);
                            mark("cleanup-finished");
                            if ("pending-failure".equals(scenario)) throw new IOException("Synthetic operation failure");
                            return List.of(saved);
                        }
                        @Override protected void refreshFinished() {
                            verifyFinished(this, "pending-failure".equals(scenario));
                        }
                    };
                }
                worker.execute();
            });
            check(entered.await(5, TimeUnit.SECONDS), "Background operation did not start");
        }
        SwingUtilities.invokeAndWait(() -> {
            Timer confirmation = new Timer(25, event -> {
                for (Window candidate : Window.getWindows())
                    if (candidate instanceof JDialog dialog && dialog.isShowing()
                            && "Download in progress".equals(dialog.getTitle()))
                        optionPane(dialog).setValue("Cancel download and close");
            });
            if (download) confirmation.start();
            try {
                mark("close-requested");
                window[0].dispatchEvent(new WindowEvent(window[0], WindowEvent.WINDOW_CLOSING));
                if ("idle".equals(scenario)) return;
                check(window[0].isDisplayable() && !Files.exists(directory.resolve("cleanup-finished")),
                        "Window must wait for pending cleanup");
                check(((CloseCoordinator) field(window[0], "closing")).isRequested(), "Close was not requested");
                mark("close-waited");
            } finally {
                confirmation.stop();
            }
        });
        release.countDown();
        // No test-side System.exit or disposal: the production coordinator must finish the JVM.
    }

    private static void verifyFinished(MainWindow.RefreshWorker worker, boolean failureExpected) {
        try {
            worker.get();
            check(!failureExpected, "Expected synthetic operation failure");
        } catch (ExecutionException failure) {
            check(failureExpected && failure.getCause() instanceof IOException, "Unexpected worker failure");
            mark("failure-observed");
        } catch (InterruptedException failure) {
            throw new AssertionError(failure);
        }
        mark("refresh-finished");
    }

    private static JOptionPane optionPane(Component component) {
        if (component instanceof JOptionPane pane) return pane;
        if (component instanceof Container container)
            for (Component child : container.getComponents()) {
                JOptionPane found = optionPane(child);
                if (found != null) return found;
            }
        return null;
    }

    private static void await(CountDownLatch release) {
        try { check(release.await(5, TimeUnit.SECONDS), "Cleanup was never released"); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }

    private static void mark(String name) {
        try { Files.writeString(Path.of(name), "done\n"); }
        catch (IOException failure) { throw new AssertionError(failure); }
    }

    private static Object field(MainWindow window, String name) {
        try {
            Field field = MainWindow.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(window);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void setField(MainWindow window, String name, Object value) {
        try {
            Field field = MainWindow.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(window, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
