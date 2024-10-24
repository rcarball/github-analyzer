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
import java.util.concurrent.CountDownLatch;
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
	private static final Pattern externalPattern = Pattern.compile("IAG|FUENTE-EXTERNA");

	private GitHubDataLoader() { }

	public static GitHubDataLoader getInstance() {
		return instance;
	}

	public List<RepoStats> loadData() {
		List<RepoStats> result = new ArrayList<>();
		
		try {
			System.out.format("Procesando %d repositorios...\n\n", repos.size());
			// Usa un CountDownLatch coordinar el fin de todos los hilos
			CountDownLatch latch = new CountDownLatch(repos.size());

			// Se crea un objeto GitHub para acceder a los repositorios
			GitHub github = new GitHubBuilder().withOAuthToken(Configurator.getInstance().getGithubToken()).build();			

			// Por cada URL de repositorio
			repos.forEach(repo -> {				
				// Se crea un hilo para obtener la informacion del repositorio
				new Thread(() -> {
					StringBuffer buffer = null;
					
					try {						
						String[] tokens = repo.split("/");
						String owner = tokens[tokens.length - 2];
						String repoName = tokens[tokens.length - 1];

						// Se crea un nuevo RepoStats
						RepoStats repoStats = new RepoStats();
						repoStats.setUrl(repo);
						repoStats.setName(repoName);
						
						// Se añade el RepoStats a la lista resultado
						result.add(repoStats);
						
						// Se crea un objeto para procesar el repositorio
						GHRepository repository = github.getRepository(owner + "/" + repoName);
						// StringBuffer para hacer una traza del proceso
						buffer = new StringBuffer(String.format("Analizando repositorio: %s ...\n", repository.getFullName()));						
						repoStats.setCreationDate(repository.getCreatedAt().getTime());						
						// Si el repositorio está vacío, todas las estadísticas son 0.

			            // Comprobar si el repositorio está vacío
			            if (repository.getSize() == 0) {
			            	buffer.append(String.format("- El repositorio %s está vacío. \n", repo));
			                
			                // Inicializar estadísticas vacías
			                repoStats.setCodeLines(0);
			                repoStats.setExternalReferences(0);
			                repoStats.setLinesChanged(0);
			                
			                for (GHUser collaborator : repository.listCollaborators().toList()) {
			                	repoStats.addUserStats(new UserStats(collaborator.getLogin(), 0, 0, 0, -1, -1));
			                }
			                
			                return;
			            }
						
						// Se obtiene el contenido de la carpeta raiz del repositorio
			            List<GHContent> allFiles = repository.getDirectoryContent("/");			            			            
			            // Se obtienen datos de ficheros y código
			            Map<String, Integer> fileCounters = filesStatistics(allFiles);			            

			            //Se asignan las líneas de código y referencias externas.
			            repoStats.setCodeLines(fileCounters.get("LINES"));
			            fileCounters.remove("LINES");
			            repoStats.setExternalReferences(fileCounters.get("REF"));
			            fileCounters.remove("REF");
			            			            
			            // Se asignan al repositorio los contadores de tipos de ficheros
			            repoStats.setFileTypeMap(fileCounters);			            
			            
						List<GHUser> collaborators = null;

						try {
							// Se obtiene la lista de colaboradores
							collaborators = repository.listCollaborators().toList();
						} catch (Exception ex) {
							// Si el repositorio es público se produce un error
							collaborators = null;
						}

						// Si se han obtenido colaboradores
						if (collaborators != null) {
							repoStats.setPublic(false);
							// Se procesan los colaboradores
							processCollaborators(collaborators, repository, repoStats, buffer);
						// Cuando el respositorio es público no se puede obtener la lista de colaboradores
						} else {
							repoStats.setPublic(true);
							
							try {
								// Se recuperan los commits del repositorio
								List<GHCommit> commits = repository.listCommits().toList();								
								// Mapa de commits por autor
								Map<String, List<GHCommit>>	commitsByAuthor = new HashMap<>();
								// Se agrupan los commits por autor
								commits.forEach(c -> {
									try { 
										commitsByAuthor.putIfAbsent(c.getAuthor().getLogin(), new ArrayList<>());
										commitsByAuthor.get(c.getAuthor().getLogin()).add(c);
									} catch (Exception ex) {
										System.out.println(String.format("- Error procesando commits %s: %s\n", repository.getFullName(), ex.getMessage()));									
									}
								});
								
							    // Se procesan los commits de cada autor
								commitsByAuthor.forEach((collaborator, commitsList) -> {
									proccessCommits(commitsList, collaborator, repoStats);
								});
							} catch (Exception ex) {
								System.out.format("Error leyendo commits de '%s' (publico): %s", repository.getFullName(), ex.getMessage());
							}
						}
					} catch (Exception e) {
						System.out.format("* Error pocesando '%s': %s\n", repo, e.getMessage());
					} finally {
						 // Al final del hilo, contar hacia abajo en el latch
                        latch.countDown();
                        // Mostrar traza del proceso
                        if (buffer != null) {
                        	System.out.println(buffer.toString());
                        }
					}
				}).start();
			}); // fin repos.forEach

			//Esperar a que terminen todos los hilos del latch
			latch.await();
			
			System.out.format("%d repositorios procesados correctamente\n\n", result.size());
		} catch (Exception ex) {
			System.out.format("Error obteniendo información de GitHub: %s", ex.getMessage());
		}

		//Se ordena la lista de RepoStats
		Collections.sort(result);
		
		//Se ordena la lista de UserStats
		result.forEach(repo -> Collections.sort(repo.getUserStats()));
				
		return result;
	}
	
	private void processCollaborators(List<GHUser> collaborators, GHRepository repository, RepoStats repoStats, StringBuffer buffer) {
		try {
			// Se procesan los colaboradores uno a uno
			for (GHUser collaborator : collaborators) {
				// Se recuperan los commits del colaborador
				List<GHCommit> commits = repository.queryCommits().author(collaborator.getLogin()).list().toList();
				// Se procesan los commits del colaborador
				proccessCommits(commits, collaborator.getLogin(), repoStats);
			}
		} catch (Exception ex) {
			buffer.append(String.format("- Error procesando usuarios %s: %s\n", repository.getFullName(), ex.getMessage()));
		}
	}
	
	private void proccessCommits(List<GHCommit> commits, String collaborator, RepoStats repoStats) {
		Set<String> javaFilesSet = new HashSet<>();
		
		int totalLinesModified = 0;

		try {
			// Se procesan los commits
			for (GHCommit commit : commits) {
				// Se recuperan los ficheros afectados por el commit											
				List<GHCommit.File> files = commit.listFiles().toList();											
				
				// Se procesa cada fichero
				for (GHCommit.File file : files) {
					// Si el fichero el ".java"
					if (file.getFileName().toLowerCase().endsWith(".java")) {
						// Se contabilizan el número de líneas modificadas
						totalLinesModified += file.getLinesChanged();
						// Se almacena el nombre del fichero 
						javaFilesSet.add(file.getFileName().toLowerCase());
					}
				}
			}
			
			// Se añade un nuevo UserStas al RepoStats
			repoStats.addUserStats(new UserStats(collaborator,
					commits.size(), 
					javaFilesSet.size(), 
					totalLinesModified,
					commits.size() > 0 ? commits.get(commits.size() - 1).getCommitDate().getTime() : -1,
					commits.size() > 0 ? commits.get(0).getCommitDate().getTime() : -1));
		} catch (Exception ex) {
			System.out.println(String.format("- Error procesando commits de '%s': %s\n", collaborator, ex.getMessage()));
		}
	}
	
	private Map<String, Integer> filesStatistics(List<GHContent> contentList) {
		Map<String, Integer> result = new TreeMap<>();
		result.put("LINES", 0);
		result.put("REF", 0);
		
		contentList.forEach(c -> fileStatistics(result, c));
		
		return result;
	}
	
	private void fileStatistics(Map<String, Integer> counters, GHContent content) {
		if (content.isDirectory()) {
			try {
				content.listDirectoryContent().toList().forEach(c -> { 					
					// Si el contenido es una carpeta
					if (c.isDirectory()) {
						fileStatistics(counters, c);
					// Si el nombre del fichero contiene "."
					} else if (c.getName().indexOf(".") != -1) {
						// Se obtiene la extesión del fichero
						String type = c.getName().substring(c.getName().lastIndexOf(".")).toLowerCase();						
						// Se añade el contador para la extensión del fichero
						counters.putIfAbsent(type, 0);
						// Se incrementa el contador para la extensión del fichero
						counters.put(type, counters.get(type) + 1);
						// Si el fichero es .java
						if (c.getName().endsWith(".java")) {
							// Se abre el fichero para leerlo
							try (BufferedReader in = new BufferedReader(new InputStreamReader(c.read()))) {					            											                    
								String line;
			                    // Se lee el fichero línea a línee								
			                    while ((line = in.readLine()) != null) {
			                    	// Se incrementa el número de líneas de código
			                    	counters.put("LINES", counters.get("LINES") + 1);			                    
				                    // Busca referencias externas
									Matcher matcher = externalPattern.matcher(line);
				                    
				                    while (matcher.find()) {
				                    	// Se incrementa el número de referencias externas
				                    	counters.put("REF", counters.get("REF") + 1);
				                    }
			                    }
							} catch(Exception ex) {
								System.err.println(ex.getMessage());
							}
		                }
					}
				});
			} catch (Exception ex) {
				System.err.println(ex.getMessage());
			}
		} else {
			return;
		}
	}
}