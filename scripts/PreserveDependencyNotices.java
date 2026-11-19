import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Shared Java 17+ build helper; requires no Python or platform-specific tools. */
class PreserveDependencyNotices {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Expected staging directory, supplemental licenses, and runtime JARs");
        }
        Path stage = Path.of(args[0]).toAbsolutePath().normalize();
        Path supplements = Path.of(args[1]);
        for (int i = 2; i < args.length; i++) {
            // Windows cmd passes wildcard arguments literally; expand them with Java.
            // Split before Path.of: Windows rejects '*' in a Path.
            String pattern = args[i];
            int separator = Math.max(pattern.lastIndexOf('/'), pattern.lastIndexOf('\\'));
            Path directory = separator < 0 ? Path.of(".") : Path.of(pattern.substring(0, separator + 1));
            String name = pattern.substring(separator + 1);
            try (var jars = Files.newDirectoryStream(directory, name)) {
                boolean found = false;
                for (Path dependency : jars) {
                    preserve(dependency, stage, supplements);
                    found = true;
                }
                if (!found) throw new IOException("No runtime dependency matches " + pattern);
            }
        }
    }

    private static void preserve(Path jar, Path stage, Path supplements) throws IOException {
        String name = jar.getFileName().toString().replaceFirst("\\.jar$", "");
        Path destination = stage.resolve("META-INF/licenses").resolve(name).normalize();
        boolean hasLicense = false;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String filename = entry.getName().substring(entry.getName().lastIndexOf('/') + 1)
                        .toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || filename.endsWith(".class")) continue;
                if (!filename.contains("license") && !filename.contains("notice")
                        && !filename.startsWith("copying") && !filename.startsWith("copyright")) continue;
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsafe notice path: " + entry.getName());
                Files.createDirectories(target.getParent());
                try (var input = zip.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                hasLicense |= filename.contains("license") || filename.startsWith("copying");
            }
        }
        Path extra = supplements.resolve(name);
        if (Files.isDirectory(extra)) {
            try (var files = Files.walk(extra)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path target = destination.resolve(extra.relativize(file));
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    hasLicense |= filename.contains("license") || filename.startsWith("copying");
                }
            }
        }
        if (!hasLicense) throw new IOException("Missing license text for " + name);
    }
}
