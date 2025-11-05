/**
 * 05/11/2025: Versión revisada y mejorada usando Gemini AI v 2.5Pro
 * Corrección de errores y optimizaciones varias.
 */

package es.deusto.prog3.githubanalyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
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

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitUser;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.SimpleGitUser;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;

public class GitHubDataLoader {

	private static final GitHubDataLoader instance = new GitHubDataLoader();
	private final List<String> repos = Configurator.getInstance().getRepositories();
	
	// Constantes
	private static final Pattern EXTERNAL_PATTERN = Pattern.compile("IAG|FUENTE-EXTERNA");
	private static final String LINES_KEY = "LINES";
	private static final String REF_KEY = "REF";
	private static final String JAVA_EXTENSION = ".java";

	private GitHubDataLoader() {
	}

	public static GitHubDataLoader getInstance() {
		return instance;
	}

	/**
	 * Carga los datos del repositorio, usando la caché si es posible.
	 * @param statsMap Lista de estadísticas previamente cargadas (usada para caché).
	 * @return Lista de estadísticas de repositorio actualizadas.
	 */
	public List<RepoStats> loadData(List<RepoStats> statsMap) {
		// Por defecto, no forzamos la actualización (usamos caché si es posible)
		return loadData(statsMap, false);
	}

	/**
	 * Carga los datos del repositorio.
	 * @param initialStats Lista de estadísticas previamente cargadas (usada para caché).
	 * @param forceRefresh Si es true, ignora la caché y vuelve a descargar todo desde GitHub.
	 * @return Lista de estadísticas de repositorio actualizadas.
	 */
	public List<RepoStats> loadData(List<RepoStats> initialStats, boolean forceRefresh) {
		// Si no se pasa una lista previa de estadísticas, se crea una vacía
		if (initialStats == null) {
			initialStats = new ArrayList<>();
		}
		
		List<RepoStats> result = Collections.synchronizedList(new ArrayList<>());

		try {
			System.out.printf("- Analyzing %d repositories...\n\n", repos.size());

			// Obtener el número de procesadores disponibles
			// Para tareas I/O (como llamadas API), es bueno tener más hilos que cores.
			// para no agotar la cuota de la API tan rápido.
			int numProcessors = Runtime.getRuntime().availableProcessors() + 1;

			// Crear un ExecutorService con un pool de hilos fijo
			ExecutorService executorService = Executors.newFixedThreadPool(numProcessors);

			// Lista para almacenar los Futures
			List<Future<?>> futures = new ArrayList<>();

			// Crear un objeto GitHub para acceder a los repositorios
			GitHub github = new GitHubBuilder().withOAuthToken(Configurator.getInstance().getGithubToken()).build();
			
			// Referencia final para usar en lambda
			final List<RepoStats> finalStatsMap = initialStats;

			// Por cada URL de repositorio
			repos.forEach(repo -> {
				// Enviar una tarea al ExecutorService
				Future<?> future = executorService.submit(() -> {
					// Usar StringBuilder en lugar de StringBuffer (no se comparte entre hilos)
					StringBuilder buffer = null;

					try {
						String[] tokens = repo.split("/");
						String owner = tokens[tokens.length - 2];
						String repoName = tokens[tokens.length - 1];

						// Crear un objeto para procesar el repositorio
						GHRepository repository = github.getRepository(owner + "/" + repoName);
						
						// StringBuffer para trazar el proceso
						buffer = new StringBuilder(String.format("- Analyzing repository: %s ...\n", repository.getFullName()));						
												
						// Se recupera el RepoStat existente
						RepoStats oldRepoStats = finalStatsMap.stream()
							.filter(r -> r.getUrl().equals(repo))
							.findFirst()
							.orElse(null);		
						
						// --- OPTIMIZACIÓN DE CACHÉ ---
						// Usamos 'pushedAt' en lugar del último commit.
						// 'pushedAt' es un timestamp que actualiza GitHub con *cualquier* push
						// (a cualquier rama, tags, etc.).
						long lastPushTime = repository.getPushedAt().getTime();
						
						// Si ya se había procesado el repositorio y estaba en el fichero
						// Y NO estamos forzando la actualización
						if (oldRepoStats != null && !forceRefresh) {
							// Comparamos el timestamp del último push
							if (oldRepoStats.getLastPushTime() == lastPushTime) {
								// Se utiliza la versión previa del RepoStat
								synchronized (result) {
									result.add(oldRepoStats);
								}
								
								buffer.append(String.format("\t* %s repository has not changed (using cache).\n", repo));
								return; // Fin, no procesamos este repo
							}
						}
						
						// Si no hay caché o está desactualizado, creamos un nuevo RepoStats
						RepoStats repoStats = new RepoStats();
						repoStats.setUrl(repo);
						repoStats.setName(repoName);
						
						// Guardamos el timestamp actual para la caché de la próxima vez
						repoStats.setLastPushTime(lastPushTime);
						
						// Añadir el RepoStats a la lista resultado
						synchronized (result) {
						    result.add(repoStats);
						}
						
						// Obtener las ramas del repositorio (una sola vez)
						Map<String, GHBranch> branches = repository.getBranches();
						repoStats.setBranches(branches.size());						
						
						// Añadir información de la fecha de creación
						repoStats.setCreationDate(repository.getCreatedAt().getTime());

						// Comprobar si el repositorio es privado o público
						repoStats.setPublic(!repository.isPrivate());
						
						// Comprobar si el repositorio está vacío
						if (repository.getSize() == 0) {
							buffer.append(String.format("\t* %s repository is empty :(\n", repo));

							// Inicializar estadísticas vacías
							repoStats.setCodeLines(0);
							repoStats.setExternalReferences(0);
							repoStats.setLinesAdded(0);
							
							// Añadir información de los colaboradores con valores vacíos
							if (repository.listCollaborators() != null) {						
								for (GHUser collaborator : repository.listCollaborators().toList()) {
									repoStats.addUserStats(new UserStats(
										collaborator.getLogin(), 
										collaborator.getEmail(), 
										0, 0, 0, 0, 0, -1, -1
									));
								}
							}

							return;
						}												
						
						// Obtener datos de ficheros y código de TODAS las ramas
						Map<String, Integer> fileCounters = filesStatisticsAllBranches(repository, branches, buffer);
						
						// Asignar líneas de código y referencias externas
						repoStats.setCodeLines(fileCounters.get(LINES_KEY));
						fileCounters.remove(LINES_KEY);
						repoStats.setExternalReferences(fileCounters.get(REF_KEY));
						fileCounters.remove(REF_KEY);

						// Asignar contadores de tipos de ficheros
						repoStats.setFileTypeMap(fileCounters);

						// Se obtienen los commits de todos los usuarios
						Map<SimpleGitUser, List<GHCommit>> commitsPerUser = processBranches(repository, branches);
						
						// Procesar los commits por usuario
						processCommitsPerUser(commitsPerUser, repository, repoStats, buffer);
						
						// Agregamos las líneas totales al RepoStats (opcional, pero útil)
						repoStats.setLinesAdded(repoStats.getUserStats().stream().mapToInt(UserStats::getAdded).sum());
						repoStats.setLinesDeleted(repoStats.getUserStats().stream().mapToInt(UserStats::getDeleted).sum());
						repoStats.setLinesChanged(repoStats.getUserStats().stream().mapToInt(UserStats::getChanged).sum());
							
					} catch (Exception e) {
						System.err.printf("\t* Error analyzing '%s': %s\n\n", repo, e.getMessage());
						e.printStackTrace(); // Más detalle para depuración
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
				    future.get(); // Esperar a que finalice la tarea
				} catch (Exception e) {
				    System.err.printf("\t* Error waiting for a task execution: %s\n", e.getMessage());
				}
			}

			// Apagar el ExecutorService
			executorService.shutdown();

			System.out.printf("- %d repositories successfully analyzed\n\n", result.size());
		} catch (Exception ex) {
			System.err.printf("\t* Error getting info from GitHub: %s\n\n", ex.getMessage());
		}

		// Ordenar la lista de RepoStats
		Collections.sort(result);

		// Ordenar la lista de UserStats
		result.forEach(repo -> Collections.sort(repo.getUserStats()));

		return result;
	}
	
	private Map<String, Integer> filesStatisticsAllBranches(GHRepository repository, 
	                                                        Map<String, GHBranch> branches, 
	                                                        StringBuilder buffer) {
	    Map<String, Integer> result = new TreeMap<>();
	    result.put(LINES_KEY, 0);
	    result.put(REF_KEY, 0);
	    
	    // Usar SHA para identificar archivos únicos (más robusto que path)
	    Set<String> processedFiles = new HashSet<>();
	    
	    try {
	        for (Map.Entry<String, GHBranch> branchEntry : branches.entrySet()) {
	            try {
	                GHBranch branch = branchEntry.getValue();
	                
	                // Obtener el árbol de la rama
	                List<GHContent> allFiles = repository.getDirectoryContent("/", branch.getName());
	                
	                // Procesar archivos de esta rama
	                processFilesFromBranch(allFiles, result, processedFiles, buffer);
	                
	            } catch (Exception ex) {
	                buffer.append(String.format("\t* Error processing files from branch '%s': %s\n", 
	                    branchEntry.getKey(), ex.getMessage()));
	            }
	        }
	    } catch (Exception ex) {
	        buffer.append(String.format("\t* Error getting branches for file statistics: %s\n", 
	            ex.getMessage()));
	    }
	    
	    return result;
	}

	private void processFilesFromBranch(List<GHContent> contentList, 
	                                    Map<String, Integer> result, 
	                                    Set<String> processedFiles, 
	                                    StringBuilder buffer) {
		// Convertido a bucle for-each para mejor manejo de excepciones
		for (GHContent content : contentList) {
			fileStatisticsWithTracking(result, content, processedFiles, buffer);
		}
	}

	private void fileStatisticsWithTracking(Map<String, Integer> counters, 
	                                        GHContent content, 
	                                        Set<String> processedFiles, 
	                                        StringBuilder buffer) {
	    try {
	        if (content.isDirectory()) {
	            content.listDirectoryContent().toList().forEach(c -> {
	                fileStatisticsWithTracking(counters, c, processedFiles, buffer);
	            });
	        } else if (content.getName().contains(".")) {
	            // Usar SHA para identificar archivos únicos (más robusto que path)
	            String fileSHA = content.getSha();
	            
	            // Solo procesar si no se ha procesado antes (evita duplicados entre ramas)
	            if (processedFiles.contains(fileSHA)) {
	                return;
	            }
	            processedFiles.add(fileSHA);
	            
	            // Obtener la extensión del fichero
	            String type = content.getName()
	                .substring(content.getName().lastIndexOf("."))
	                .toLowerCase();

	            // Añadir el contador para la extensión
	            counters.putIfAbsent(type, 0);
	            counters.put(type, counters.get(type) + 1);

	            // Si el fichero es .java
	            if (content.getName().endsWith(JAVA_EXTENSION)) {
	                // Leer el fichero
	                try (BufferedReader in = new BufferedReader(new InputStreamReader(content.read()))) {
	                    String line;
	                    while ((line = in.readLine()) != null) {
	                        // Incrementar líneas de código
	                        counters.put(LINES_KEY, counters.get(LINES_KEY) + 1);

	                        // Buscar referencias externas
	                        Matcher matcher = EXTERNAL_PATTERN.matcher(line);

	                        while (matcher.find()) {
	                            counters.put(REF_KEY, counters.get(REF_KEY) + 1);
	                        }
	                    }
	                } catch (Exception ex) {
	                    buffer.append(String.format("\t* Error processing file '%s': %s\n", 
	                        content.getName(), ex.getMessage()));
	                }
	            }
	        }
	    } catch (Exception ex) {
	        buffer.append(String.format("\t* Error processing content '%s': %s\n", 
	            content.getName(), ex.getMessage()));
	    }
	}
	
	private Map<SimpleGitUser, List<GHCommit>> processBranches(GHRepository repository, 
	                                                           Map<String, GHBranch> branches) {
	    Map<SimpleGitUser, List<GHCommit>> result = new HashMap<>();
	    Set<String> processedCommits = new HashSet<>();
	    
	    try {
	        // Procesar cada rama
	        for (Map.Entry<String, GHBranch> branchEntry : branches.entrySet()) {
	            GHBranch branch = branchEntry.getValue();

	            // Recuperar los commits para cada rama
	            List<GHCommit> commits = repository.queryCommits()
	                .from(branch.getSHA1())
	                .list()
	                .toList();
	            
	            if (commits != null) {
	                // Procesar los commits
	                for (GHCommit c : commits) {
	                    String commitSHA = c.getSHA1();
	                    
	                    // Saltar commits ya procesados
	                    if (processedCommits.contains(commitSHA)) {
	                        continue;
	                    }
	                    processedCommits.add(commitSHA);
	                    
	                    // --- CORRECCIÓN CRÍTICA: Autor vs. Committer ---
	                    // El 'Committer' es quien pulsa "merge" (puedes ser tú).
	                    // El 'Author' es quien escribió el código (el alumno).
	                    // Queremos al 'Author'.
	                    
	                    // Usamos el "Fully Qualified Name" (nombre completo) para evitar problemas
	                    // de importación con el JAR de la v1.329
	                    GitUser authorInfo = c.getCommitShortInfo().getAuthor();
	                    
	                    String name = (authorInfo != null) ? authorInfo.getName() : "Unknown";
	                    String email = (authorInfo != null) ? authorInfo.getEmail() : "unknown";

						// Evitar nombres genéricos de GitHub si podemos
						if ("GitHub".equals(name) && c.getAuthor() != null) {
							name = c.getAuthor().getLogin();
							email = c.getAuthor().getEmail();
						}
	                    
	                    SimpleGitUser author = new SimpleGitUser(name, email);
	                    
	                    // Se añade el commit al autor
	                    result.putIfAbsent(author, new ArrayList<>());
	                    result.get(author).add(c);
	                }
	            }
	        }
	    } catch (Exception ex) {
	        System.err.printf("\t* Error processing branches of '%s': %s\n\n", 
	            repository.getName(), ex.getMessage());
	    }
	    
	    return result;
	}

	private void processCommitsPerUser(Map<SimpleGitUser, List<GHCommit>> commitsPerUser, 
	                                   GHRepository repository, 
	                                   RepoStats repoStats,
	                                   StringBuilder buffer) {
		try {
			commitsPerUser.forEach((user, commits) -> {
				processCommits(commits, user.getName(), user.getEmail(), repoStats);
				buffer.append(String.format("\t* Commits of '%s' (%s) processed: %d\n", 
					user.getName(), user.getEmail(), commits.size()));
			});
		} catch (Exception ex) {
			buffer.append(String.format("\t* Error processing collaborators %s: %s\n", 
				repository.getFullName(), ex.getMessage()));
		}
	}

	private void processCommits(List<GHCommit> commits, String username, String email, RepoStats repoStats) {
		Set<String> javaFilesSet = new HashSet<>();
		int linesAdded = 0;
		int linesDeleted = 0;
		int linesChanged = 0;

		try {
			// Procesar los commits
			for (GHCommit commit : commits) {
				// Recuperar los ficheros afectados por el commit
				// Esta es la llamada más costosa. 1 por commit.
				List<GHCommit.File> files = commit.listFiles().toList();

				// Procesar cada fichero
				for (GHCommit.File file : files) {
					String fileName = file.getFileName().toLowerCase();
					
					// Si el fichero es ".java"
					if (fileName.endsWith(JAVA_EXTENSION)) {
						// Contabilizar líneas añadidas
						linesAdded += file.getLinesAdded();						
						// Contabilizar líneas borradas
						linesDeleted += file.getLinesDeleted();
						// Contabilizar líneas modificadas
						linesChanged += file.getLinesChanged();						
						// Almacenar nombre del fichero
						javaFilesSet.add(fileName);
					}
				}
			}

			// Simplificar cálculo de fechas
			long firstCommitDate = commits.isEmpty() ? -1 : commits.get(commits.size() - 1).getCommitDate().getTime();
			long lastCommitDate = commits.isEmpty() ? -1 : commits.get(0).getCommitDate().getTime();

			// Añadir nuevo UserStats al RepoStats
			repoStats.addUserStats(new UserStats(
				username, 
				email, 
				commits.size(), 
				javaFilesSet.size(), 
				linesAdded, 
				linesDeleted, 
				linesChanged,
				firstCommitDate, 
				lastCommitDate
			));
		} catch (Exception ex) {
			System.err.printf("\t* Error processing commits '%s': %s\n\n", username, ex.getMessage());
		}
	}
}