package es.deusto.prog3.githubanalyzer.persistence;

import java.nio.file.Path;

/** Shares isolated configuration instances with GUI integration tests. */
public final class ConfigurationFixtures {
    private ConfigurationFixtures() { }

    public static Configurator create(Path directory) {
        return new Configurator(directory);
    }
}
