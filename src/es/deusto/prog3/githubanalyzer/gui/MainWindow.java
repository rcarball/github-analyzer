package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class MainWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private JLabel lblCreationDate;
	private JLabel lblFirstCommit;
	private JLabel lblLastCommit;
	private JLabel lblCommits;
	private JLabel lblColeLines;
	private JLabel lblLinesChanged;
	private JLabel lblExternalRefs;
	private JLabel lblURL;
	private JLabel lblStatus = new JLabel(" ");
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
	private JTable jTableUserStats;
	private DefaultTableModel tableModelUserStats;
	private JTree jTreeFileType;
	private JTree jTreeRepos = new JTree();
	
	private JButton btnRefresh = new JButton("Refresh from GitHub");

	private Map<String, RepoStats> repoStatsMap = new HashMap<>();
	private String selectedRepo;

	public MainWindow(List<RepoStats> data) {
		// Se configura el JTree de repositorios
		jTreeRepos.setRowHeight(23);

		// Asignar un render personalizado como clase anónima
		jTreeRepos.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row,
						hasFocus);

				String iconName = "resources/images/";
				
				// Obtener el nodo y su valor asociado
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object userObject = node.getUserObject();				

				// Verificar si el userObject es de tipo RepoStats
				if (userObject instanceof RepoStats) {
					RepoStats repoStats = (RepoStats) userObject;
					iconName += repoStats.isPublic() ? "public.png" : "private.png";
					
					// Si el repositorio está vacío, cambiar el color de text a rojo
					if (repoStats.getCommits() == 0) {
						component.setForeground(new Color(245, 143, 41));
						setText(repoStats.getName() + " (empty)");
					} else {
						component.setForeground(new Color(54, 130, 127));						
						setText(repoStats.getName() + " - " + repoStats.getBranches() + " branch(es)");
					}
				} else {
					iconName += "github.png";
					component.setForeground(Color.BLACK);
				}

				if (selected || hasFocus) {
					component.setForeground(Color.WHITE);
					component.setBackground(Color.BLUE);
				}
				
				this.setIcon(scaleIcon(new ImageIcon(iconName)));

				return component;
			}
		});

		jTreeRepos.addTreeSelectionListener(e -> {
			DefaultMutableTreeNode selectedNode = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent());
			Object nodeValue = selectedNode.getUserObject();

			if (nodeValue instanceof RepoStats) {
				RepoStats repoStats = (RepoStats) nodeValue;
				loadRepoStats(repoStats);
				selectedRepo = repoStats.getUrl();
			} else {
				loadRepoStats(null);
				selectedRepo = null;
			}
		});
		
		// Se cargan los datos iniciales de los repositorios
		updateReposJTree(data);
		// Se inicializa la tabla de personas colaboradoras
		initTable();

		JScrollPane reposJScrollPane = new JScrollPane(jTreeRepos);
		reposJScrollPane.setBorder(new TitledBorder("Repositories"));

		JPanel panelDetails = new JPanel();
		panelDetails.setBorder(new TitledBorder("Repository overview"));
		panelDetails.setLayout(new GridLayout(4, 2, 0, 0));

		lblCommits       = new JLabel("• Total commits (unique):");
		lblCreationDate  = new JLabel("• Created:");
		lblFirstCommit   = new JLabel("• First commit:");
		lblLastCommit    = new JLabel("• Last commit:");
		lblColeLines     = new JLabel("• Java LOC (snapshot):");
		lblLinesChanged  = new JLabel("• Java churn (added+deleted):");
		lblExternalRefs  = new JLabel("• External references:");
		lblURL           = new JLabel("• Open repository:");
		
		lblCommits.setToolTipText("<html><b>Unique commits</b> across all branches (deduplicated by SHA).<br>"
		                          + "This is a global activity indicator (not the same as “Java commits per person”).</html>");
		lblCreationDate.setToolTipText("Repository creation date (from GitHub).");
		lblFirstCommit.setToolTipText("Earliest commit date found in the analyzed history.");
		lblLastCommit.setToolTipText("Latest commit date found in the analyzed history.");
		lblColeLines.setToolTipText("<html><b>Java LOC</b> = current number of lines in .java files (snapshot).<br>"
		                            + "It measures code size, not effort.</html>");
		lblLinesChanged.setToolTipText("<html><b>Java churn</b> = added + deleted lines in .java files.<br>"
		                               + "Computed from non-merge commits only.</html>");
		lblExternalRefs.setToolTipText("<html>Occurrences of patterns <b>IAG</b> or <b>FUENTE-EXTERNA</b> in .java files.<br>"
		                               + "Useful to flag external/AI-assisted code references.</html>");
		lblURL.setToolTipText("Click to open the repository in your browser.");
		
        // Añadir el MouseListener para capturar el clic
		lblURL.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    // Abrir la URL en el navegador predeterminado
                	if (selectedRepo != null) {
                		Desktop.getDesktop().browse(new URI(repoStatsMap.get(selectedRepo).getUrl()));
                	}
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            	if (selectedRepo != null) {
            		lblURL.setCursor(new Cursor(Cursor.HAND_CURSOR));  // Cambiar a cursor de mano
            	}
            }

            @Override
            public void mouseExited(MouseEvent e) {
            	if (selectedRepo != null) {
            		lblURL.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));  // Volver al cursor por defecto
            	}
            }
        });

		panelDetails.add(lblCommits);
		panelDetails.add(lblCreationDate);
		panelDetails.add(lblColeLines);
		panelDetails.add(lblFirstCommit);
		panelDetails.add(lblLinesChanged);
		panelDetails.add(lblLastCommit);
		panelDetails.add(lblExternalRefs);
		panelDetails.add(lblURL);		

		JScrollPane usersJScrollPane = new JScrollPane(jTableUserStats);
		usersJScrollPane.setBorder(new TitledBorder("People (authors / contributors)"));

		jTreeFileType = new JTree(new DefaultMutableTreeNode(""));		
		JScrollPane fileTypeJScrollPane = new JScrollPane(jTreeFileType);
		fileTypeJScrollPane.setBorder(new TitledBorder("File types"));

		JPanel centralPanel = new JPanel();
		centralPanel.setLayout(new GridLayout(3, 1, 5, 0));
		centralPanel.add(panelDetails);
		centralPanel.add(usersJScrollPane);
		centralPanel.add(fileTypeJScrollPane);

		JLabel lblFooter = new JLabel("<html><a href=\"\">Icons created by Pixel perfect - Flaticon</a><html>");
		lblFooter.setForeground(Color.BLUE);
		lblFooter.setHorizontalAlignment(JLabel.RIGHT);
		
		lblFooter.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                	Desktop.getDesktop().browse(new URI("https://www.flaticon.com/authors/pixel-perfect"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            	lblFooter.setCursor(new Cursor(Cursor.HAND_CURSOR));  // Cambiar a cursor de mano
            }

            @Override
            public void mouseExited(MouseEvent e) {
            	lblFooter.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));  // Volver al cursor por defecto
            }
        });

		// Configuración del botón de refresco
		btnRefresh.setToolTipText("Refresh (GitHub)");
		btnRefresh.addActionListener(e -> {						
			// SwingWorker para tareas largas en segundo plano
	        SwingWorker<List<RepoStats>, String> worker = new SwingWorker<>() {
	            
	            @Override
	            protected List<RepoStats> doInBackground() throws Exception {
	                // Se obtienen las estadísticas desde GitHub
	            	List<RepoStats> newStats = GitHubDataLoader.getInstance().loadData(null, true);
	    	    	//Se guardan las estadísticas en un fichero binario
	    	    	DataManager.getInstance().storeData(newStats);
	    	    	// Se devuelve la nueva lista de estadísticas
	    	    	return newStats;
	            }

	            @Override
	            protected void done() {
	            	try {
		            	// Se actualizan los datos en la interfaz cuando se obtienen los nuevos datos
		    	    	updateReposJTree(get());
		    	    	
		    	    	SwingUtilities.invokeLater(() -> {
			            	// Se muestra un mensaje de confirmación
		    	    		JOptionPane.showMessageDialog(
		    	    			    null,
		    	    			    "Data refreshed successfully.",
		    	    			    "Refresh completed",
		    	    			    JOptionPane.INFORMATION_MESSAGE
		    	    			);
		    	    	});		    	    	
					} catch (InterruptedException | ExecutionException e) {
						JOptionPane.showMessageDialog(
							    null,
							    "Refresh failed.\n\nTip: GitHub may throttle requests when refreshing many repositories.\n"
							    + "Try again later or use offline mode (cached data).",
							    "Refresh failed",
							    JOptionPane.WARNING_MESSAGE
							);
					}
	            }
	        };

	        // Iniciar el worker
	        worker.execute();
		});
		
		lblStatus.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		lblStatus.setForeground(new Color(80, 80, 80));

		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(lblStatus, BorderLayout.WEST);
		bottomPanel.add(lblFooter, BorderLayout.EAST);
				
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(btnRefresh, BorderLayout.EAST);
		
		this.setTitle("GitHub Repository Analyzer");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		this.setLayout(new BorderLayout(0, 0));	
		this.add(topPanel, BorderLayout.NORTH);
		this.add(centralPanel, BorderLayout.CENTER);
		this.add(reposJScrollPane, BorderLayout.WEST);		
		this.add(bottomPanel, BorderLayout.SOUTH);

		this.setSize(1200, 700);
		this.setLocationRelativeTo(null);
	
		// Tooltips más “lentos” (en ms)
		ToolTipManager.sharedInstance().setInitialDelay(300);   // aparece rápido
		ToolTipManager.sharedInstance().setDismissDelay(20000); // 20 segundos visible
		ToolTipManager.sharedInstance().setReshowDelay(100);    // al pasar entre celdas
		
		this.setVisible(true);		
	}

	private void updateReposJTree(List<RepoStats> data) {
		repoStatsMap.clear();
		data.forEach(repo -> repoStatsMap.put(repo.getUrl(), repo));
		DefaultMutableTreeNode repoRootNode = new DefaultMutableTreeNode(String.format("%d Repositories", data.size()));
		data.forEach(repo -> {
			DefaultMutableTreeNode repoNode = new DefaultMutableTreeNode(repo); // repo es de tipo RepoStats
			repoRootNode.add(repoNode);
		});
		jTreeRepos.setModel(new DefaultTreeModel(repoRootNode));
	}
	
	private void initTable() {
	    Vector<String> cabecera = new Vector<>(
	        Arrays.asList(
	        		"USERNAME",
	        		"<html>JAVA<br>ADDED</html>",
	        		"<html>JAVA<br>DELETED</html>",
	        		"<html>JAVA<br>CHURN</html>",
	        		"<html>% JAVA<br>CHURN</html>",
	        		"<html>JAVA<br>FILES</html>",
	        		"<html>JAVA<br>COMMITS</html>",
	        		"<html>LAST<br>COMMIT</html>",
	        		"<html>FIRST<br>COMMIT</html>"
	        )
	    );

	    tableModelUserStats = new DefaultTableModel(new Vector<Vector<Object>>(), cabecera);

	    jTableUserStats = new JTable(tableModelUserStats) {
	        private static final long serialVersionUID = 1L;

	        @Override
	        public boolean isCellEditable(int row, int col) {
	            return false;
	        }

	        @Override
	        public String getToolTipText(java.awt.event.MouseEvent e) {
	            if (selectedRepo == null) return super.getToolTipText(e);

	            int row = rowAtPoint(e.getPoint());
	            if (row < 0) return super.getToolTipText(e);

	            RepoStats repo = repoStatsMap.get(selectedRepo);
	            if (repo == null) return super.getToolTipText(e);

	            if (row >= repo.getUserStats().size()) return super.getToolTipText(e);

	            UserStats u = repo.getUserStats().get(row);
	            return buildInterpretationTooltip(repo, u);
	        }
	    };

	    JTableHeader header = jTableUserStats.getTableHeader();
	    header.setPreferredSize(new Dimension(header.getPreferredSize().width, 35));

	    TableCellRenderer cellRenderer = (table, value, isSelected, hasFocus, row, column) -> {
	        Object safeValue = (value == null) ? "" : value;
	        JLabel result = new JLabel(" " + safeValue.toString());
	        result.setHorizontalAlignment(JLabel.CENTER);

	        // Formato por tipo
	        if (value instanceof String) {
	            result.setHorizontalAlignment(JLabel.LEFT);
	        } else if (value instanceof Long) {
	            if (((long) value) != -1) result.setText(dateFormat.format(new Date((long) value)));
	            else result.setText("-");
	            result.setHorizontalAlignment(JLabel.CENTER);
	        } else if (value instanceof Float) {
	            result.setText(String.format("%.2f %%", (float) value * 100));
	            result.setHorizontalAlignment(JLabel.RIGHT);
	        } else if (value instanceof Integer) {
	            result.setHorizontalAlignment(JLabel.RIGHT);
	        }

	        // USERNAME a la izquierda
	        if (column == 0) result.setHorizontalAlignment(JLabel.LEFT);

	        // Colorear por contribución (usando la MISMA lista que pinta la tabla)
	        if (selectedRepo != null) {
	            RepoStats repo = repoStatsMap.get(selectedRepo);
	            if (repo != null && row >= 0 && row < repo.getUserStats().size()) {

	                UserStats user = repo.getUserStats().get(row);

	                int numContributors = Math.max(1, realContributorsExcludingTeacher(repo).size());
	                float expected = 1.0f / numContributors;

	                int repoChurn = repo.getLinesChanged(); // churn total repo (java)
	                int userChurn = user.getAdded() + user.getDeleted();
	                float share = (repoChurn == 0) ? 0f : ((float) userChurn) / repoChurn;

	                boolean teacher = isTeacher(user);

	                if (teacher) {
	                    result.setForeground(Color.DARK_GRAY);
	                } else if (userChurn == 0 || user.getFirstCommit() == -1 || share < expected * 0.5f) {
	                    result.setForeground(new Color(234, 23, 68)); // muy bajo
	                } else if (share >= expected * 1.25f) {
	                    result.setForeground(new Color(54, 130, 127)); // alto
	                } else if (share >= expected) {
	                    result.setForeground(new Color(54, 130, 127)); // ok
	                } else {
	                    result.setForeground(new Color(245, 143, 41)); // bajo
	                }

	                // Tooltip corto por celda (opcional): dejamos que JTable.getToolTipText muestre el largo
	                // pero para la col 0 damos un hint rápido
	                if (column == 0) {
	                    if (teacher) result.setToolTipText("👩‍🏫 Teacher (excluded from expected share)");
	                    else if (userChurn == 0 || user.getFirstCommit() == -1 || share < expected * 0.5f)
	                        result.setToolTipText("⛔ Very low / no contribution");
	                    else if (share >= expected * 1.25f)
	                        result.setToolTipText("🌟 High contribution");
	                    else if (share >= expected)
	                        result.setToolTipText("✅ Around expected contribution");
	                    else
	                        result.setToolTipText("⚠️ Below expected contribution");
	                }
	            }
	        }

	        // Fondo selección
	        if (isSelected) {
	            result.setBackground(table.getSelectionBackground());
	            result.setForeground(table.getSelectionForeground());
	        } else {
	            result.setBackground(table.getBackground());
	        }

	        result.setOpaque(true);
	        return result;
	    };

	    final String[] headerTooltips = new String[] {
	    	    "GitHub login when available; otherwise derived from commit author info. Emoji = main contribution indicator.",
	    	    "Java lines added (sum over non-merge commits).",
	    	    "Java lines deleted (sum over non-merge commits).",
	    	    "Java churn = added + deleted (sum over non-merge commits).",
	    	    "User share of repository Java churn.",
	    	    "Distinct .java files modified by the user.",
	    	    "Non-merge commits that touched at least one .java file.",
	    	    "Date of the user's last considered non-merge commit.",
	    	    "Date of the user's first considered non-merge commit."
	    };
	    
	    TableCellRenderer headerRenderer = (table, value, isSelected, hasFocus, row, column) -> {
	        JLabel result = new JLabel(value == null ? "" : value.toString());

	        if (column == 0) result.setHorizontalAlignment(JLabel.LEFT);
	        else if (column >= 7) result.setHorizontalAlignment(JLabel.CENTER);
	        else result.setHorizontalAlignment(JLabel.RIGHT);

	        result.setFont(table.getFont().deriveFont(Font.BOLD));
	        result.setBackground(table.getBackground());
	        result.setForeground(table.getForeground());
	        result.setOpaque(true);

	        if (column >= 0 && column < headerTooltips.length) {
	            result.setToolTipText(headerTooltips[column]);
	        }
	        return result;
	    };

	    jTableUserStats.setRowHeight(26);
	    jTableUserStats.setShowGrid(false);
	    jTableUserStats.getTableHeader().setReorderingAllowed(false);
	    jTableUserStats.getTableHeader().setResizingAllowed(false);
	    jTableUserStats.setAutoCreateRowSorter(false);
	    jTableUserStats.setFillsViewportHeight(true);
	    jTableUserStats.getTableHeader().setDefaultRenderer(headerRenderer);

	    jTableUserStats.getColumnModel().getColumn(0).setPreferredWidth(260); // username
	    jTableUserStats.getColumnModel().getColumn(1).setPreferredWidth(55);  // added
	    jTableUserStats.getColumnModel().getColumn(2).setPreferredWidth(55);  // deleted
	    jTableUserStats.getColumnModel().getColumn(3).setPreferredWidth(55);  // churn
	    jTableUserStats.getColumnModel().getColumn(4).setPreferredWidth(55);  // % churn
	    jTableUserStats.getColumnModel().getColumn(5).setPreferredWidth(55);  // java files
	    jTableUserStats.getColumnModel().getColumn(6).setPreferredWidth(55);  // java commits
	    jTableUserStats.getColumnModel().getColumn(7).setPreferredWidth(75);  // last
	    jTableUserStats.getColumnModel().getColumn(8).setPreferredWidth(75);  // first

	    jTableUserStats.setDefaultRenderer(Object.class, cellRenderer);
	    
	    jTableUserStats.addMouseMotionListener(new MouseMotionAdapter() {
	        @Override
	        public void mouseMoved(MouseEvent e) {
	            if (lblStatus == null) return;
	            String tip = jTableUserStats.getToolTipText(e);
	            lblStatus.setText(tooltipForStatusBar(tip));
	        }
	    });

	    jTableUserStats.addMouseListener(new MouseAdapter() {
	        @Override
	        public void mouseExited(MouseEvent e) {
	            if (lblStatus != null) lblStatus.setText(" ");
	        }
	    });
	}

	private void loadRepoStats(RepoStats repoStats) {
		if (repoStats != null) {
			// Se actualizan las estadísticas generales del repo
			lblCreationDate.setText(String.format("- Creation date: %s", dateFormat.format(new Date(repoStats.getCreationDate()))));			
			
			if (repoStats.getFirstCommit() == -1) {
                lblFirstCommit.setText("- First commit: -");
            } else {
            	lblFirstCommit.setText(String.format("- First commit: %s", dateFormat.format(new Date(repoStats.getFirstCommit()))));
            }

			if (repoStats.getLastCommit() == -1) {
				lblLastCommit.setText("- Last commit: -");
			} else {			
				lblLastCommit.setText(String.format("- Last commit: %s", dateFormat.format(new Date(repoStats.getLastCommit()))));
			}
			
			int javaCommitsSum = repoStats.getUserStats().stream().mapToInt(UserStats::getCommits).sum();

			lblCommits.setText(String.format("- Total commits (unique): %d", repoStats.getCommits()));
			lblCommits.setToolTipText(String.format(
			    "<html>Unique commits across all branches (deduplicated by SHA).<br>" +
			    "Java commits (sum of users, non-merge commits touching .java): %d</html>",
			    javaCommitsSum
			));
			
			lblColeLines.setText(String.format("- Total lines of code: %d", repoStats.getCodeLines()));
			lblLinesChanged.setText(String.format("- Java churn (added+deleted): %d", repoStats.getLinesChanged()));
			lblLinesChanged.setToolTipText(String.format("Added: %d | Deleted: %d", repoStats.getLinesAdded(), repoStats.getLinesDeleted()));
			lblExternalRefs.setText(String.format("- External references: %d", repoStats.getExternalReferences()));
			lblURL.setText(String.format("<html>- <u><i>%s</i></u></html>", repoStats.getName()));			
			lblURL.setForeground(Color.BLUE);			

			// Se actualiza la tabla de colaboradores
			tableModelUserStats.setRowCount(0);

			int repoChurn = repoStats.getLinesChanged(); // churn java
			int numContributors = Math.max(1, realContributorsExcludingTeacher(repoStats).size());
			float expected = 1.0f / numContributors;
			
			repoStats.getUserStats().forEach(s -> {
			    int churn = s.getAdded() + s.getDeleted();
			    float pct = (repoChurn == 0) ? 0f : ((float) churn) / repoChurn;

			    String displayName = String.format("%s %s", 
			    		                           getContributionEmoji(repoStats, s, expected, repoChurn),
			    		                           s.getUsername()); 

			    tableModelUserStats.addRow(new Object[] {
			        displayName,
			        s.getAdded(),
			        s.getDeleted(),
			        churn,
			        pct,
			        s.getJavaFiles(),
			        s.getCommits(),
			        s.getLastCommit(),
			        s.getFirstCommit()
			    });
			});

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) jTreeFileType.getModel().getRoot();
			root.setUserObject(String.format("%d file types", repoStats.getFileTypeMap().keySet().size()));
			root.removeAllChildren();
			repoStats.getFileTypeMap().forEach((k, v) -> root.add(new DefaultMutableTreeNode(String.format("%s (%d)", k, v))));
			((DefaultTreeModel) jTreeFileType.getModel()).nodeStructureChanged(root);
			jTreeFileType.updateUI();
		} else {
			lblCreationDate.setText("- Creation date:");
			lblFirstCommit.setText("- First commit:");
			lblLastCommit.setText("- Last commit:");
			lblCommits.setText("- Total commits:");
			lblColeLines.setText("- Total lines of code:");
			lblLinesChanged.setText("- Java churn (added+deleted):");
			lblExternalRefs.setText("- External references:");
			lblURL.setText("- URL:");

			tableModelUserStats.setRowCount(0);
			
			DefaultMutableTreeNode root = (DefaultMutableTreeNode) jTreeFileType.getModel().getRoot();
			root.setUserObject("0 file types");
			root.removeAllChildren();
			((DefaultTreeModel) jTreeFileType.getModel()).nodeStructureChanged(root);
			jTreeFileType.updateUI();
		}
	}
	
	private ImageIcon scaleIcon(ImageIcon icon) {
		return new ImageIcon(icon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));
	}
	
	private boolean isTeacher(UserStats u) {
	    String tUser = Configurator.getInstance().getTeacherUser();
	    String tEmail = Configurator.getInstance().getTeacherEmail();

	    String user = (u.getUsername() == null) ? "" : u.getUsername().trim().toLowerCase();
	    String email = (u.getEmail() == null) ? "" : u.getEmail().trim().toLowerCase();

	    if (tUser != null && !tUser.isBlank() && user.equals(tUser.trim().toLowerCase())) return true;

	    if (tEmail != null && !tEmail.isBlank()) {
	        String te = tEmail.trim().toLowerCase();
	        if (!email.isBlank() && email.equals(te)) return true;

	        int at1 = email.indexOf('@');
	        int at2 = te.indexOf('@');
	        String lp1 = at1 > 0 ? email.substring(0, at1) : email;
	        String lp2 = at2 > 0 ? te.substring(0, at2) : te;
	        if (!lp1.isBlank() && lp1.equals(lp2)) return true;
	    }
	    return false;
	}

	private int userChurn(UserStats u) {
	    return u.getAdded() + u.getDeleted();
	}

	private boolean isRealContributor(UserStats u) {
	    return userChurn(u) > 0 || u.getCommits() > 0;
	}

	private List<UserStats> realContributorsExcludingTeacher(RepoStats repo) {
	    return repo.getUserStats().stream()
	        .filter(u -> !isTeacher(u))
	        .filter(this::isRealContributor)
	        .collect(java.util.stream.Collectors.toList());
	}
	
	private String buildInterpretationTooltip(RepoStats repo, UserStats u) {
	    int repoChurn = repo.getLinesChanged(); // Java churn at repo level
	    int uChurn = userChurn(u);
	    int commitsJava = u.getCommits();

	    List<UserStats> contributors = realContributorsExcludingTeacher(repo);
	    int n = Math.max(1, contributors.size());
	    float expected = 1f / n;

	    float share = (repoChurn <= 0) ? 0f : (uChurn / (float) repoChurn);
	    float churnPerCommit = (commitsJava <= 0) ? 0f : (uChurn / (float) commitsJava);

	    // Relative thresholds
	    float veryLow = expected * 0.5f;   // < 50% of expected
	    float okMin   = expected * 0.8f;   // 80%
	    float okMax   = expected * 1.2f;   // 120%
	    float high    = expected * 1.25f;  // 125%
	    float engine  = expected * 2.0f;   // 200%

	    // Repo-average churn/commit for AI/paste-like signal
	    float avgChurnPerCommit = 0f;
	    int totalCommitsJava = contributors.stream().mapToInt(UserStats::getCommits).sum();
	    int totalChurn = contributors.stream().mapToInt(this::userChurn).sum();
	    if (totalCommitsJava > 0) avgChurnPerCommit = totalChurn / (float) totalCommitsJava;

	    boolean aiPasteLike = commitsJava > 0 && avgChurnPerCommit > 0 && churnPerCommit >= avgChurnPerCommit * 2.5f;

	    boolean cleanup = false;
	    if (uChurn > 0) {
	        float delRatio = u.getDeleted() / (float) uChurn;
	        cleanup = (delRatio >= 0.55f && uChurn >= 200);
	    }

	    boolean irregularRhythm = false;
	    if (u.getFirstCommit() != -1 && u.getLastCommit() != -1
	            && repo.getFirstCommit() != -1 && repo.getLastCommit() != -1) {

	        long repoSpan = repo.getLastCommit() - repo.getFirstCommit();
	        long userLastOffset = u.getLastCommit() - repo.getFirstCommit();
	        irregularRhythm = (repoSpan > 0 && (userLastOffset / (float) repoSpan) > 0.85f && uChurn >= 200);
	    }

	    StringBuilder sb = new StringBuilder("<html>");
	    sb.append("<b>Teaching interpretation (indicators)</b><br>");
	    sb.append(String.format(
	        "Active contributors (excluding teacher): <b>%d</b> → expected ≈ <b>%.0f%%</b><br><br>",
	        n, expected * 100
	    ));

	    // 1) Teacher
	    if (isTeacher(u)) {
	        sb.append("👩‍🏫 <b>Teacher account</b>: excluded from expected-share calculations.<br>");
	        sb.append("</html>");
	        return sb.toString();
	    }

	    // 2) Very low / no contribution
	    if (uChurn == 0 || commitsJava == 0 || share < veryLow) {
	        sb.append("⛔ <b>Very low / no contribution</b>: below expected or near zero. Check additional evidence.<br>");
	    }

	    // 3) Team engine
	    if (share >= engine) {
	        sb.append("⚠️ <b>Team “engine”</b>: far above expected. Review task distribution and authorship.<br>");
	    }

	    // 4) High contribution
	    if (share >= high) {
	        sb.append("🌟 <b>High contribution</b>: above expected for the team size.<br>");
	    }

	    // 5) Balanced contribution (only if not already flagged as very low)
	    if (share >= okMin && share <= okMax && commitsJava > 0) {
	        sb.append("✅ <b>Balanced contribution</b>: close to expected for the team size.<br>");
	    } else if (uChurn > 0 && commitsJava > 0 && share >= veryLow && share < okMin) {
	        // Optional: keep your "below expected" hint in the tooltip body
	        sb.append("⚠️ <b>Below expected contribution</b>: noticeable but under the expected share.<br>");
	    }

	    // 6) Cleanup/correction work
	    if (cleanup) {
	        sb.append("🔁 <b>Cleanup/correction work</b>: high deletion ratio. Verify context and continuity.<br>");
	    }

	    // 7) AI/paste-like pattern
	    if (aiPasteLike) {
	        sb.append("🧠 <b>AI/paste-like pattern</b>: very high churn per commit vs repo average. Ask for a defense.<br>");
	    }

	    // 8) Irregular rhythm
	    if (irregularRhythm) {
	        sb.append("⏱️ <b>Irregular rhythm</b>: activity concentrated near the end of the period.<br>");
	    }

	    sb.append("</html>");
	    return sb.toString();
	}
	
	private String getContributionEmoji(RepoStats repo, UserStats user, float expected, int repoChurn) {
	    if (isTeacher(user)) return "👩‍🏫";

	    int churn = userChurn(user);
	    float share = (repoChurn == 0) ? 0f : (churn / (float) repoChurn);

	    if (churn == 0 || user.getFirstCommit() == -1 || share < expected * 0.5f) return "⛔";
	    if (share >= expected * 1.25f) return "🌟";
	    if (share >= expected) return "✅";
	    return "⚠️";
	}
	
	private String tooltipForStatusBar(String htmlTooltip) {
	    if (htmlTooltip == null) return " ";

	    String plain = htmlTooltip
	            .replaceAll("(?i)<br\\s*/?>", "\n")
	            .replaceAll("<[^>]*>", "")
	            .replace("&nbsp;", " ")
	            .trim();

	    if (plain.isBlank()) return " ";

	    String[] lines = plain.split("\\R+");
	    List<String> candidates = new ArrayList<>();

	    for (String line : lines) {
	        String s = line.trim();
	        if (s.isEmpty()) continue;

	        String low = s.toLowerCase();
	        if (low.startsWith("teaching interpretation")) continue;
	        if (low.startsWith("active contributors")) continue;
	        if (low.startsWith("contributors")) continue;

	        candidates.add(s);
	    }
	    if (candidates.isEmpty()) return " ";

	    java.util.function.Function<String, String> shortLine = (String s) -> {
	        String t = s.trim();
	        int cut = t.indexOf(':');
	        if (cut < 0) cut = t.indexOf('–');
	        if (cut < 0) cut = t.indexOf('-');
	        if (cut > 0) t = t.substring(0, cut).trim();
	        return t.replaceAll("\\s{2,}", " ");
	    };

	    // Contribution slot: prefer teacher / very low / engine / high / balanced / below expected
	    String contribution = null;
	    String[] contributionPriority = new String[] { "👩‍🏫", "⛔", "⚠️", "🌟", "✅" };

	    outer:
	    for (String p : contributionPriority) {
	        for (String c : candidates) {
	            // ignore AI/rhythm/cleanup for the contribution slot
	            if (c.contains(p) && !c.contains("🧠") && !c.contains("⏱️") && !c.contains("🔁")) {
	                contribution = shortLine.apply(c);
	                break outer;
	            }
	        }
	    }

	    // AI
	    String ai = null;
	    for (String c : candidates) {
	        if (c.contains("🧠")) { ai = shortLine.apply(c); break; }
	    }

	    // Rhythm
	    String rhythm = null;
	    for (String c : candidates) {
	        if (c.contains("⏱️")) { rhythm = shortLine.apply(c); break; }
	    }

	    // Cleanup
	    String cleanup = null;
	    for (String c : candidates) {
	        if (c.contains("🔁")) { cleanup = shortLine.apply(c); break; }
	    }

	    List<String> out = new ArrayList<>();
	    if (contribution != null && !contribution.isBlank()) out.add(contribution);
	    if (ai != null && !ai.isBlank() && !out.contains(ai)) out.add(ai);
	    if (rhythm != null && !rhythm.isBlank() && !out.contains(rhythm)) out.add(rhythm);
	    if (cleanup != null && !cleanup.isBlank() && !out.contains(cleanup)) out.add(cleanup);

	    return out.isEmpty() ? " " : String.join("   |   ", out);
	}
}