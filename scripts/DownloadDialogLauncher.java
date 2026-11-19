// IAG (herramientas: Codex (OpenAI))
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Shared source-mode launcher. All generated files belong to one private workspace. */
public final class DownloadDialogLauncher {
    private static final String TEST_CLASS = "es.deusto.prog3.githubanalyzer.gui.DownloadDialogDesktopCheck";
    private static final Path TEST_SOURCE = Path.of("test", "es", "deusto", "prog3", "githubanalyzer", "gui", "DownloadDialogDesktopCheck.java");
    private static final String JUNIT = "org.junit.platform.console.ConsoleLauncher";

    public static void main(String[] args) {
        boolean unitTests = args.length == 2 && "--unit".equals(args[1]);
        int result = 1;
        Workspace workspace = null;
        try {
            boolean desktopCheck = args.length == 1 || (args.length == 2 && "--desktop".equals(args[1]));
            if ((!desktopCheck && !unitTests) || Runtime.version().feature() < 17)
                throw new IOException("Use a complete JDK 17 or newer and provide the project directory.");
            Path project = Path.of(args[0]).toRealPath();
            boolean windows = File.separatorChar == '\\';
            String suffix = windows ? ".exe" : "";
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            Path java = bin.resolve("java" + suffix);
            Path javac = bin.resolve("javac" + suffix);
            if (!Files.isExecutable(java) || !Files.isExecutable(javac))
                throw new IOException("The selected Java installation is not a complete JDK with java and javac.");
            if (!Files.isDirectory(project.resolve("src")))
                throw new IOException("The project sources are missing.");
            workspace = Workspace.create();
            Path classes = Files.createDirectory(workspace.root.resolve("classes"));
            List<String> sources = new ArrayList<>();
            addSources(project.resolve("src"), sources);
            if (unitTests)
                addSources(project.resolve("test"), sources);
            else if (Files.isRegularFile(project.resolve(TEST_SOURCE)))
                sources.add(quoteSource(project.resolve(TEST_SOURCE)));
            else
                throw new IOException("The desktop regression check is missing.");
            Path exitCheck = project.resolve("test/es/deusto/prog3/githubanalyzer/gui/ApplicationExitDesktopCheck.java");
            if (!unitTests && Files.isRegularFile(exitCheck))
                sources.add(quoteSource(exitCheck));
            Path arguments = workspace.root.resolve("sources.txt");
            Files.write(arguments, sources, StandardCharsets.UTF_8);
            // The wildcard belongs to the Java classpath syntax and is not a Windows path component.
            String libraries = project.resolve("lib") + File.separator + "*";
            List<String> compile = new ArrayList<>();
            compile.add(javac.toString());
            if (windows)
                compile.add("-J-XX:-UsePerfData");
            compile.addAll(List.of("-J-Dfile.encoding=UTF-8", "--release", "17",
                    "-encoding", "UTF-8", "-cp", libraries,
                    "-d", classes.toString(), "@" + arguments));
            result = workspace.execute(compile);
            if (result == 0) {
                String classpath = String.join(File.pathSeparator, classes.toString(),
                        project.resolve("resources").toString(), libraries);
                List<String> command = new ArrayList<>();
                command.add(java.toString());
                if (windows) {
                    // Do not leave a JVM performance-data directory in TEMP.
                    command.add("-XX:-UsePerfData");
                    // Some Windows temp folders allow AF_UNIX bind but fail on connect.
                    // Use a file as the socket directory so the JDK falls back to TCP loopback at bind time.
                    command.add("-Djdk.net.unixdomain.tmpdir=" + arguments.getFileName());
                }
                command.addAll(List.of("-cp", classpath, "-Djava.awt.headless=" + unitTests,
                        "-Djava.io.tmpdir=" + workspace.root));
                if (unitTests) {
                    Files.copy(project.resolve("README.md"), workspace.root.resolve("README.md"));
                    Path resources = Files.createDirectory(workspace.root.resolve("resources"));
                    Files.copy(project.resolve("resources/config.properties.example"), resources.resolve("config.properties.example"));
                    command.addAll(List.of(JUNIT, "execute",
                            "--scan-classpath=" + classes, "--details=summary"));
                } else {
                    command.addAll(List.of("-Dgithubanalyzer.desktop.fixture.directory=" + workspace.root, TEST_CLASS));
                }
                result = workspace.execute(command);
            }
        } catch (Exception failure) {
            System.err.println("Could not run the Java checks: " + failure.getMessage());
            result = 1;
        } finally {
            if (workspace != null && !workspace.cleanup())
                result = 1;
        }
        if (result == 0 && workspace != null && !workspace.shutdownRequested)
            System.out.println((unitTests ? "All unit tests passed." : "All download dialog checks passed.")
                    + " Temporary test files were removed.");
        System.exit(result);
    }

    private static void addSources(Path directory, List<String> sources) throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                    .sorted().forEach(path -> sources.add(quoteSource(path)));
        }
    }

    private static String quoteSource(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static final class Workspace {
        private Path root;
        volatile boolean shutdownRequested;
        private Process child;
        private boolean cleaning;
        private boolean cleaned;
        private boolean successful;

        static Workspace create() throws IOException {
            String configured = System.getenv(File.separatorChar == '\\' ? "TEMP" : "TMPDIR");
            Path parent = Path.of(configured == null || configured.isBlank()
                    ? System.getProperty("java.io.tmpdir") : configured).toRealPath();
            Workspace workspace = new Workspace();
            // Install cleanup before creating anything, including interruption during setup.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                workspace.shutdownRequested = true;
                workspace.cleanup();
            }, "download-dialog-cleanup"));
            synchronized (workspace) {
                if (workspace.shutdownRequested || workspace.cleaning)
                    throw new IOException("The launcher is shutting down.");
                workspace.root = Files.createTempDirectory(parent, "github-analyzer-dialogs-");
            }
            return workspace;
        }

        int execute(List<String> command) throws IOException, InterruptedException {
            Process running;
            synchronized (this) {
                if (cleaning || shutdownRequested)
                    throw new IOException("The launcher is shutting down.");
                ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).inheritIO();
                builder.environment().remove("GITHUB_TOKEN");
                running = builder.start();
                child = running;
            }
            int result = running.waitFor();
            synchronized (this) {
                if (child == running)
                    child = null;
            }
            return result;
        }

        boolean cleanup() {
            Process running;
            boolean interrupted = Thread.interrupted();
            synchronized (this) {
                while (cleaning && !cleaned) {
                    try { wait(); }
                    catch (InterruptedException ex) { interrupted = true; }
                }
                if (cleaned) {
                    if (interrupted) Thread.currentThread().interrupt();
                    return successful;
                }
                cleaning = true;
                running = child;
            }
            boolean removed = false;
            try {
                if (running != null && running.isAlive()) {
                    running.destroy();
                    if (!running.waitFor(5, TimeUnit.SECONDS)) {
                        running.destroyForcibly();
                        if (!running.waitFor(5, TimeUnit.SECONDS))
                            throw new IOException("The test child could not be stopped; temporary data was retained.");
                    }
                }
                // Do not follow symlinks or delete anything outside this invocation's workspace.
                if (root != null) Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                        if (failure != null) throw failure;
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
                removed = true;
            } catch (Exception failure) {
                System.err.println("Could not clean temporary test folder " + root + ": " + failure.getMessage());
            } finally {
                synchronized (this) {
                    successful = removed;
                    cleaned = true;
                    notifyAll();
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            return removed;
        }
    }
}
