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

    public static void main(String[] args) {
        int result = 1;
        Workspace workspace = null;
        try {
            if (args.length != 1 || Runtime.version().feature() < 17)
                throw new IOException("Use a complete JDK 17 or newer and provide the project directory.");
            Path project = Path.of(args[0]).toRealPath();
            String suffix = File.separatorChar == '\\' ? ".exe" : "";
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            Path java = bin.resolve("java" + suffix);
            Path javac = bin.resolve("javac" + suffix);
            if (!Files.isExecutable(java) || !Files.isExecutable(javac))
                throw new IOException("The selected Java installation is not a complete JDK with java and javac.");
            if (!Files.isDirectory(project.resolve("src")) || !Files.isRegularFile(project.resolve(TEST_SOURCE)))
                throw new IOException("The project sources or desktop regression check are missing.");
            workspace = Workspace.create();
            Path classes = Files.createDirectory(workspace.root.resolve("classes"));
            List<String> sources = new ArrayList<>();
            try (var paths = Files.walk(project.resolve("src"))) {
                paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                        .sorted().forEach(path -> sources.add(quoteSource(path)));
            }
            sources.add(quoteSource(project.resolve(TEST_SOURCE)));
            Path arguments = workspace.root.resolve("sources.txt");
            Files.write(arguments, sources, StandardCharsets.UTF_8);
            result = workspace.execute(List.of(javac.toString(), "-J-Dfile.encoding=UTF-8", "--release", "17",
                    "-encoding", "UTF-8", "-cp", project.resolve("lib").resolve("*").toString(),
                    "-d", classes.toString(), "@" + arguments));
            if (result == 0) {
                String classpath = String.join(File.pathSeparator, classes.toString(),
                        project.resolve("resources").toString(), project.resolve("lib").resolve("*").toString());
                result = workspace.execute(List.of(java.toString(), "-Djava.awt.headless=false",
                        "-Dgithubanalyzer.desktop.fixture.directory=" + workspace.root,
                        "-cp", classpath, TEST_CLASS));
            }
        } catch (Exception failure) {
            System.err.println("Download dialog checks failed: " + failure.getMessage());
            result = 1;
        } finally {
            if (workspace != null && !workspace.cleanup())
                result = 1;
        }
        if (result == 0 && workspace != null && !workspace.shutdownRequested)
            System.out.println("All download dialog checks passed. Temporary test files were removed.");
        System.exit(result);
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
