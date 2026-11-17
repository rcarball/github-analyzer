package es.deusto.prog3.githubanalyzer;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.RepositoryConfig;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;

/** Offline probe of the actual download/calculation paths, outside the normal test suite. */
public final class JavaSourceBenchmark {
    private static final String URL = "https://github.com/benchmark/java-source";
    private static volatile long consumed;
    private static final com.sun.management.ThreadMXBean ALLOCATIONS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        ALLOCATIONS.setThreadAllocatedMemoryEnabled(true);
        System.out.println("java=" + System.getProperty("java.version") + ", vm=" + System.getProperty("java.vm.name"));
        System.out.println("phase,scenario,utf8_MiB,median_ms,allocated_MiB_per_op");
        for (String scenario : List.of("short-lines", "long-line", "unicode")) {
            String unit = switch (scenario) {
                case "short-lines" -> "class Example { int value = 1; } // IAG FUENTE-EXTERNA\r\n";
                case "unicode" -> "// español 漢字 😀 IAG FUENTE-EXTERNA\r\nclass Example {}\n";
                default -> "x";
            };
            String text = unit.repeat((8 * 1024 * 1024) / unit.length());
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            double megabytes = bytes.length / 1048576.0;
            String encoded = Base64.getEncoder().encodeToString(bytes);
            // A real library content object exercises its existing Base64 read path.
            GHContent content = GitHub.getMappingObjectReader().forType(GHContent.class).readValue(
                    "{\"type\":\"file\",\"name\":\"Large.java\",\"path\":\"Large.java\",\"sha\":\"blob\",\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}");
            GHRepository remote = new GHRepository() {
                @Override public String getFullName() { return "benchmark/java-source"; }
                @Override public Date getCreatedAt() { return new Date(1); }
                @Override public Date getPushedAt() { return new Date(2); }
                @Override public boolean isPrivate() { return false; }
                @Override public int getSize() { return 1; }
                @Override public String getDefaultBranch() { return "main"; }
                @Override public Map<String, GHBranch> getBranches() { return Map.of(); }
                @Override public List<GHContent> getDirectoryContent(String path, String ref) { return List.of(content); }
            };
            measure("read", scenario, megabytes, () -> {
                try {
                    String decoded = GitHubDataLoader.getInstance().downloadSnapshot(URL, remote, ignored -> { }).sourceFiles().get(0).javaContent();
                    if (decoded.length() != text.length() || decoded.charAt(decoded.length() - 1) != text.charAt(text.length() - 1))
                        throw new AssertionError("Source content changed");
                    return decoded.length();
                } catch (Exception ex) { throw new IllegalStateException(ex); }
            });
            RepositorySnapshot source = new RepositorySnapshot(1, URL, "java-source", 1, 1, 2, true, "main",
                    Map.of(), List.of(), List.of(new RepositorySnapshot.SourceFile("Large.java", text)));
            RepositoryConfig settings = new RepositoryConfig(URL, "", null);
            measure("calculate", scenario, megabytes, () -> {
                var stats = StatisticsCalculator.calculate(source, settings, new AuthorshipConfig(), id -> AuthorshipConfig.Role.MEMBER);
                return ((long) stats.getCodeLines() << 32) | stats.getExternalReferences();
            });
        }
        if (consumed == Long.MIN_VALUE) throw new AssertionError("Unreachable checksum");
    }

    private static void measure(String phase, String scenario, double megabytes, LongSupplier operation) {
        for (int i = 0; i < 8; i++) consumed = operation.getAsLong();
        System.gc();
        long[] times = new long[5], allocated = new long[5];
        long thread = Thread.currentThread().getId();
        for (int i = 0; i < times.length; i++) {
            long before = ALLOCATIONS.getThreadAllocatedBytes(thread), start = System.nanoTime();
            consumed = operation.getAsLong();
            times[i] = System.nanoTime() - start;
            allocated[i] = ALLOCATIONS.getThreadAllocatedBytes(thread) - before;
        }
        Arrays.sort(times); Arrays.sort(allocated);
        System.out.printf(Locale.ROOT, "%s,%s,%.2f,%.3f,%.3f%n", phase, scenario, megabytes,
                times[2] / 1_000_000.0, allocated[2] / 1048576.0);
    }
}
