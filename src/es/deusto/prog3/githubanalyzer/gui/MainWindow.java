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
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

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
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
	private JTable jTableUserStats;
	private DefaultTableModel tableModelUserStats;
	private JTree jTreeFileType;

	private Map<String, RepoStats> repoStatsMap = new HashMap<>();
	private String selectedRepo;

	public MainWindow(List<RepoStats> data) {
		// Se crea un mapa con los datos de los repositorios
		data.forEach(repo -> repoStatsMap.put(repo.getUrl(), repo));

		// Inicializar el root del JTree
		DefaultMutableTreeNode repoRootNode = new DefaultMutableTreeNode(String.format("%d Repositories", data.size()));

		// Ahora agregamos los objetos RepoStats directamente en los nodos del JTree
		data.forEach(repo -> {
			DefaultMutableTreeNode repoNode = new DefaultMutableTreeNode(repo); // repo es de tipo RepoStats
			repoRootNode.add(repoNode);
		});

		// Crear el JTree con los nodos que contienen objetos RepoStats
		JTree jTreeRepos = new JTree(repoRootNode);
		jTreeRepos.setRowHeight(23);

		// Asignar un renderizador personalizado como clase anónima
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
					
					// Si el repositorio está vacío, cambiar el color a rojo
					if (repoStats.getCommits() == 0) {
						component.setForeground(new Color(245, 143, 41));
						setText(repoStats.getName() + " (empty)");
					} else {
						component.setForeground(new Color(54, 130, 127));						
						setText(repoStats.getName() + " (" + repoStats.getBranches() + ")");
					}
				} else {
					iconName += "github.png";
					component.setForeground(Color.BLACK);
				}

				if (selected || hasFocus) {
					component.setForeground(Color.WHITE);
					component.setBackground(Color.BLUE);
				}
				
				//Se establece la imagen al nodo
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

		initTable();

		JScrollPane reposJScrollPane = new JScrollPane(jTreeRepos);
		reposJScrollPane.setBorder(new TitledBorder("GitHub Repositories"));

		JPanel panelDetails = new JPanel();
		panelDetails.setBorder(new TitledBorder("General Details"));
		panelDetails.setLayout(new GridLayout(4, 2, 0, 0));

		lblCreationDate = new JLabel("- Creation date:");
		lblFirstCommit = new JLabel("- First commit:");
		lblLastCommit = new JLabel("- Last commit:");
		lblCommits = new JLabel("- Total commit:");
		lblColeLines = new JLabel("- Total lines of code:");
		lblLinesChanged = new JLabel("- Total lines changed:");
		lblExternalRefs = new JLabel("- External references:");
		lblURL = new JLabel("- URL:");
		
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
		usersJScrollPane.setBorder(new TitledBorder("Collaborators / Authors"));

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
		
		this.setTitle("GitHub repositories statistics");
		this.setLayout(new BorderLayout(0, 0));
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		this.add(centralPanel, BorderLayout.CENTER);
		this.add(reposJScrollPane, BorderLayout.WEST);
		this.add(lblFooter, BorderLayout.SOUTH);

		this.setSize(1200, 700);
		this.setLocationRelativeTo(null);
		this.setVisible(true);
	}

	private void initTable() {
		Vector<String> cabecera = new Vector<String>(
				Arrays.asList("", "USERNAME (EMAIL)", 
						          "<html>LINES<br>ADDED</html>", 
						          "<html>% LINES<br>ADDED</html>", 
						          "<html>LINES<br>DELETED</html>",
						          "<html>MODIFIED<br>FILES</html>", 
						          "COMMITS", 
						          "LAST COMMIT", 
						          "FIRST COMMIT"));				
		tableModelUserStats = new DefaultTableModel(new Vector<Vector<Object>>(), cabecera);
		jTableUserStats = new JTable(tableModelUserStats) {
			private static final long serialVersionUID = 1L;

			public boolean isCellEditable(int row, int col) {
				return false;
			}
		};
		
		// Establecer la altura de la cabecera
        JTableHeader header = jTableUserStats.getTableHeader();
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 35)); // Altura de 40 píxeles

		TableCellRenderer cellRenderer = (table, value, isSelected, hasFocus, row, column) -> {
			JLabel result = new JLabel(String.format(" %s", value.toString()));			
			result.setHorizontalAlignment(JLabel.CENTER);

			// Configuración de la alineación y formato del valor dependiendo de su tipo
			if (value instanceof String) {
				result.setHorizontalAlignment(JLabel.LEFT);
			} else if (value instanceof Long) {
				if (((long) value) != -1) {
					result.setText(dateFormat.format(new Date((long) value)));
				} else {
					result.setText("-");
				}
			} else if (value instanceof Float) {
				result.setText(String.format("%.2f %%", (float) value * 100));
				result.setHorizontalAlignment(JLabel.RIGHT);
			} else if (value instanceof Integer) {
				result.setHorizontalAlignment(JLabel.RIGHT);
			}
			
			if (column == 0) {
				result.setHorizontalAlignment(JLabel.CENTER);
			}				

			if (selectedRepo != null) {				
				if (row >= repoStatsMap.get(selectedRepo).getUserStats().size()) {
					System.out.println("Row: " + row);
					repoStatsMap.get(selectedRepo).getUserStats().forEach(u -> System.out.println(u));
                    return result;
				}
				
				UserStats user = repoStatsMap.get(selectedRepo).getUserStats().get(row);
				
				int numCollaborators = repoStatsMap.get(selectedRepo).getUserStats().size();
				
		        float contribution = ((float) user.getAdded()) / repoStatsMap.get(selectedRepo).getLinesAdded();

		        // Configuración del color de texto en función de las estadísticas del usuario
		        if (user.getAdded() == 0 || user.getFirstCommit() == -1 || contribution < 1.0 / numCollaborators * 0.5) {		        	
		        	result.setForeground(new Color(234, 23, 68));  // Ninguna contribución
		        	
		        	if (column == 0) {
		        		result.setIcon(new ImageIcon("resources/images/none.png"));
		        		result.setToolTipText("No contribution to the repository");
		        	}		        	
		        } else {
		            // Aportación muy superior a la media
		            if (contribution >= 1.0 / numCollaborators * 1.25) {		                
		                result.setForeground(new Color(54, 130, 127));
		                
			        	if (column == 0) {
			        		result.setIcon(new ImageIcon("resources/images/excellent.png"));
			        		result.setToolTipText("Excellent: contribution 25% above expected average");
			        	}		        
		        	
		        	// Aportación superior o igual a la media
		            } else if (contribution >= 1.0 / numCollaborators) {		                
		                result.setForeground(new Color(54, 130, 127));
		                
			        	if (column == 0) {
			        		result.setIcon(new ImageIcon("resources/images/good.png"));
			        		result.setToolTipText("Good contribution: around expected average");
			        	}		        	
		            // Aportación inferior a la media
		            } else {
		            	result.setForeground(new Color(245, 143, 41));
		            	
			        	if (column == 0) {
			        		result.setIcon(new ImageIcon("resources/images/poor.png"));
			        		result.setToolTipText("Low contribution: below expected average");
			        	}		        	
		            }
		        }
		    }
			
		    // Configuración de fondo para celdas seleccionadas
		    if (isSelected) {
		        result.setBackground(table.getSelectionBackground());
		        result.setForeground(table.getSelectionForeground());
		    } else {
		        result.setBackground(table.getBackground());
		    }

		    // Se añade un tooltip con texto de la cela
		    if (!result.getText().isEmpty()) {
		    	result.setToolTipText(result.getText());
		    }		 		    		    
		    
			result.setOpaque(true); // Necesario para que el fondo se pinte correctamente

			return result;
		};

		TableCellRenderer headerRenderer = (table, value, isSelected, hasFocus, row, column) -> {
			JLabel result = new JLabel(value.toString());
			result.setHorizontalAlignment(JLabel.RIGHT);

			if (column == 1) {
				result.setHorizontalAlignment(JLabel.LEFT);
			} else if (column > 6) {
				result.setHorizontalAlignment(JLabel.CENTER);
			}

			result.setBackground(table.getBackground());
			result.setForeground(table.getForeground());
			result.setFont(table.getFont().deriveFont(Font.BOLD));
			result.setOpaque(true);

			return result;
		};

		jTableUserStats.setRowHeight(26);
		jTableUserStats.getTableHeader().setReorderingAllowed(false);
		jTableUserStats.getTableHeader().setResizingAllowed(false);
		jTableUserStats.setAutoCreateRowSorter(false);
		jTableUserStats.setFillsViewportHeight(true);
		jTableUserStats.getTableHeader().setDefaultRenderer(headerRenderer);		
		jTableUserStats.getColumnModel().getColumn(0).setPreferredWidth(15);
		jTableUserStats.getColumnModel().getColumn(1).setPreferredWidth(220);
		jTableUserStats.getColumnModel().getColumn(2).setPreferredWidth(30);
		jTableUserStats.getColumnModel().getColumn(3).setPreferredWidth(45);
		jTableUserStats.getColumnModel().getColumn(4).setPreferredWidth(45);
		jTableUserStats.getColumnModel().getColumn(5).setPreferredWidth(45);
		jTableUserStats.getColumnModel().getColumn(6).setPreferredWidth(45);
		jTableUserStats.setDefaultRenderer(Object.class, cellRenderer);
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
			
			lblCommits.setText(String.format("- Total commits: %d", repoStats.getCommits()));
			lblColeLines.setText(String.format("- Total lines of code: %d", repoStats.getCodeLines()));
			lblLinesChanged.setText(String.format("- Total lines added: %d", repoStats.getLinesAdded()));
			lblExternalRefs.setText(String.format("- External references: %d", repoStats.getExternalReferences()));
			lblURL.setText(String.format("<html>- <u><i>%s</i></u></html>", repoStats.getName()));			
			lblURL.setForeground(Color.BLUE);  // Color de hipervínculo			

			// Se actualiza la tabla de colaboradores
			tableModelUserStats.setRowCount(0);

			repoStats.getUserStats().forEach(s -> tableModelUserStats.addRow(new Object[] { "", 
																				s.getEmail() != null ? String.format("%s (%s)", s.getUsername(), s.getEmail()) : s.getUsername(),
																			    s.getAdded(),
																				(s.getAdded() == 0) ? 0 : ((float) s.getAdded()) / repoStats.getLinesAdded(),
																				s.getDeleted(),
																				s.getJavaFiles(),
																				s.getCommits(),
																				s.getLastCommit(),
																				s.getFirstCommit() }));

			// Se actualiza el árbol de tipos de ficheros
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
			lblLinesChanged.setText("- Total lines changed:");
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
}