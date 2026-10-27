package dev.atlasac;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Locale;

/**
 * Laedt beim Serverstart nur die Datenbank-Treiber, die laut config.yml gebraucht werden
 * (bei storage.type: yaml gar nichts). Paper cached die Dateien im libraries-Ordner.
 */
public final class AtlasLoader implements PluginLoader {
	private static final String MIRROR = "https://maven-central.storage-download.googleapis.com/maven2";

	@Override
	public void classloader(PluginClasspathBuilder builder) {
		String type = "yaml";
		File config = builder.getContext().getDataDirectory().resolve("config.yml").toFile();
		if (config.exists()) {
			type = YamlConfiguration.loadConfiguration(config).getString("storage.type", "yaml").toLowerCase(Locale.ROOT);
		}
		String driver = switch (type) {
			case "sqlite" -> "org.xerial:sqlite-jdbc:3.49.1.0";
			case "mysql" -> "com.mysql:mysql-connector-j:9.3.0";
			case "mariadb" -> "org.mariadb.jdbc:mariadb-java-client:3.5.3";
			case "postgresql", "postgres" -> "org.postgresql:postgresql:42.7.7";
			default -> null;
		};
		if (driver == null) return;

		MavenLibraryResolver resolver = new MavenLibraryResolver();
		resolver.addRepository(new RemoteRepository.Builder("central", "default", MIRROR).build());
		resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:6.3.0"), null));
		resolver.addDependency(new Dependency(new DefaultArtifact(driver), null));
		builder.addLibrary(resolver);
	}
}
