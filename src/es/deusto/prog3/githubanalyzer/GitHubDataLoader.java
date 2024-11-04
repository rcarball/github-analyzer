package es.deusto.prog3.githubanalyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;

public class GitHubDataLoader {

	private static GitHubDataLoader instance = new GitHubDataLoader();
	private final List<String> repos = Configurator.getInstance().getRepositories();
	private static final Pattern externalPattern = Pattern.compile("IAG|" + "FUENTE-EXTERNA");

	private GitHubDataLoader() {
	}

	public static GitHubDataLoader getInstance() {
		return instance;
	}

	public List<RepoStats> loadData() {
		List<RepoStats> result = Collections.synchronizedList(new ArrayList<>());

		try {
			System.out.format("- Analyzing %d repositories...\n\n", repos.size());

			// Obtener el número de procesadores disponibles
			int numProcessors = Runtime.getRuntime().availableProcessors() * 2;

			// Crear un ExecutorService con un pool de hilos fijo
			ExecutorService executorService = Executors.newFixedThreadPool(numProcessors);

			// Lista para almacenar los Futures
			List<Future<?>> futures = new ArrayList<>();

			// Crear un objeto GitHub para acceder a los repositorios
			GitHub github = new GitHubBuilder().withOAuthToken(Configurator.getInstance().getGithubToken()).build();

			// Por cada URL de repositorio
			repos.forEach(repo -> {
				// Enviar una tarea al ExecutorService
				Future<?> future = executorService.submit(() -> {
					StringBuffer buffer = null;

					try {
						String[] tokens = repo.split("/");
						String owner = tokens[tokens.length - 2];
						String repoName = tokens[tokens.length - 1];

						// Crear un nuevo RepoStats
						RepoStats repoStats = new RepoStats();
						repoStats.setUrl(repo);
						repoStats.setName(repoName);

						// Añadir el RepoStats a la lista resultado
						result.add(repoStats);

						// Crear un objeto para procesar el repositorio
						GHRepository repository = github.getRepository(owner + "/" + repoName);

						// StringBuffer para trazar el proceso
						buffer = new StringBuffer(
								String.format("- Analyzing repository: %s ...\n", repository.getFullName()));
						repoStats.setCreationDate(repository.getCreatedAt().getTime());

						// Comprobar si el repositorio está vacío
						if (repository.getSize() == 0) {
							buffer.append(String.format("\t* %s repository " + "is empty :( \n", repo));

							// Inicializar estadísticas vacías
							repoStats.setCodeLines(0);
							repoStats.setExternalReferences(0);
							repoStats.setLinesChanged(0);

							for (GHUser collaborator : repository.listCollaborators().toList()) {
								repoStats.addUserStats(new UserStats(collaborator.getLogin(), collaborator.getEmail(), 0, 0, 0, -1, -1));
							}

							return;
						}

						// Obtener el contenido de la carpeta raíz
						List<GHContent> allFiles = repository.getDirectoryContent("/");

						// Obtener datos de ficheros y código
						Map<String, Integer> fileCounters = filesStatistics(allFiles, buffer);

						// Asignar líneas de código y referencias externas
						repoStats.setCodeLines(fileCounters.get("LINES"));
						fileCounters.remove("LINES");
						repoStats.setExternalReferences(fileCounters.get("REF"));
						fileCounters.remove("REF");

						// Asignar contadores de tipos de ficheros
						repoStats.setFileTypeMap(fileCounters);

						List<GHUser> collaborators = null;

						try {
							// Obtener la lista de colaboradores
							collaborators = repository.listCollaborators().toList();
						} catch (Exception ex) {
							// Si el repositorio es público se produce un error
							collaborators = null;
						}

						// Procesar colaboradores
						if (collaborators != null) {
							repoStats.setPublic(false);
							processCollaborators(collaborators, repository, repoStats, buffer);
						} else {
							repoStats.setPublic(true);

							try {
								// Recuperar los commits del repositorio
								List<GHCommit> commits = repository.listCommits().toList();

								// Mapa de commits por autor (del autor se guarda una lista con su username y email)
								Map<List<String>, List<GHCommit>> commitsByAuthor = new HashMap<>();

								// Agrupar los commits por autor
								commits.forEach(c -> {
									try {
										List<String> author = Arrays.asList(c.getAuthor().getLogin(), c.getAuthor().getEmail());
										
										commitsByAuthor.putIfAbsent(author, new ArrayList<>());
										commitsByAuthor.get(author).add(c);
									} catch (Exception ex) {
										System.err
												.println(String.format("\t* Error processing " + "commits '%s': %s\n\n",
														repository.getFullName(), ex.getMessage()));
									}
								});

								// Procesar los commits de cada autor
								commitsByAuthor.forEach((collaborator, commitsList) -> {
									proccessCommits(commitsList, collaborator.getFirst(), collaborator.getLast(), repoStats);
								});
							} catch (Exception ex) {
								System.err.format("\t* Error reading commits " + "'%s' (public): %s\n\n",
										repository.getFullName(), ex.getMessage());
							}
						}

						// Actualizar el primer y último commit
						repoStats.updateFirstAndLastCommit();
					} catch (Exception e) {
						System.err.format("\t* Error analyzing '%s': %s\n\n", repo, e.getMessage());
					} finally {
						// Mostrar traza del proceso
						if (buffer != null) {
							System.out.println(buffer.toString());
						}
					}
				});

				// Añadir el Future a la lista
				futures.add(future);
			}); // fin repos.forEach

			// Esperar a que todas las tareas se completen
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (Exception e) {
					System.err.format("Error in task execution: %s\n", e.getMessage());
				}
			}

			// Apagar el ExecutorService
			executorService.shutdown();

			System.out.format("- %d repositories successfully analyzed\n\n", result.size());
		} catch (Exception ex) {
			System.err.format("\t* Error getting info from GitHub: %s\n\n", ex.getMessage());
		}

		// Ordenar la lista de RepoStats
		Collections.sort(result);

		// Ordenar la lista de UserStats
		result.forEach(repo -> Collections.sort(repo.getUserStats()));

		return result;
	}

	private void processCollaborators(List<GHUser> collaborators, GHRepository repository, RepoStats repoStats,
			StringBuffer buffer) {
		try {
			// Procesar cada colaborador
			for (GHUser collaborator : collaborators) {
				// Recuperar los commits del colaborador
				List<GHCommit> commits = repository.queryCommits().author(collaborator.getLogin()).list().toList();

				// Procesar los commits del colaborador
				proccessCommits(commits, collaborator.getLogin(), collaborator.getEmail(), repoStats);
			}
		} catch (Exception ex) {
			buffer.append(String.format("\t* Error processing collaborators " + "%s: %s\n", repository.getFullName(),
					ex.getMessage()));
		}
	}

	private void proccessCommits(List<GHCommit> commits, String username, String email, RepoStats repoStats) {
		Set<String> javaFilesSet = new HashSet<>();
		int totalLinesModified = 0;

		try {
			// Procesar los commits
			for (GHCommit commit : commits) {
				// Recuperar los ficheros afectados por el commit
				List<GHCommit.File> files = commit.listFiles().toList();

				// Procesar cada fichero
				for (GHCommit.File file : files) {
					// Si el fichero es ".java"
					if (file.getFileName().toLowerCase().endsWith(".java")) {
						// Contabilizar líneas modificadas
						totalLinesModified += file.getLinesChanged();

						// Almacenar nombre del fichero
						javaFilesSet.add(file.getFileName().toLowerCase());
					}
				}
			}

			// Añadir nuevo UserStats al RepoStats
			repoStats.addUserStats(new UserStats(username, email, commits.size(), javaFilesSet.size(), totalLinesModified,
					commits.isEmpty() ? -1 : commits.get(commits.size() - 1).getCommitDate().getTime(),
					commits.isEmpty() ? -1 : commits.get(0).getCommitDate().getTime()));
		} catch (Exception ex) {
			System.err.println(
					String.format("\t* Error processing commits " + "'%s': %s\n\n", username, ex.getMessage()));
		}
	}

	private Map<String, Integer> filesStatistics(List<GHContent> contentList, StringBuffer buffer) {
		Map<String, Integer> result = new TreeMap<>();
		result.put("LINES", 0);
		result.put("REF", 0);

		contentList.forEach(c -> fileStatistics(result, c, buffer));

		return result;
	}

	private void fileStatistics(Map<String, Integer> counters, GHContent content, StringBuffer buffer) {
		if (content.isDirectory()) {
			try {
				content.listDirectoryContent().toList().forEach(c -> {
					// Si el contenido es una carpeta
					if (c.isDirectory()) {
						fileStatistics(counters, c, buffer);
					} else if (c.getName().indexOf(".") != -1) {
						// Obtener la extensión del fichero
						String type = c.getName().substring(c.getName().lastIndexOf(".")).toLowerCase();

						// Añadir el contador para la extensión
						counters.putIfAbsent(type, 0);
						counters.put(type, counters.get(type) + 1);

						// Si el fichero es .java
						if (c.getName().endsWith(".java")) {
							// Leer el fichero
							try (BufferedReader in = new BufferedReader(new InputStreamReader(c.read()))) {
								String line;
								while ((line = in.readLine()) != null) {
									// Incrementar líneas de código
									counters.put("LINES", counters.get("LINES") + 1);

									// Buscar referencias externas
									Matcher matcher = externalPattern.matcher(line);

									while (matcher.find()) {
										counters.put("REF", counters.get("REF") + 1);
									}
								}
							} catch (Exception ex) {
								buffer.append(String.format("\t* Error " + "processing file '%s': %s\n", c.getName(),
										ex.getMessage()));
							}
						}
					}
				});
			} catch (Exception ex) {
				buffer.append(String.format("\t* Error processing folder '%s'" + ": %s\n", content.getName(),
						ex.getMessage()));
			}
		}
	}
}