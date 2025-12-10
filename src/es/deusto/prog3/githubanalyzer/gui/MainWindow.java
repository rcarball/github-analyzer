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
	private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM-dd");
	private JTable jTableUserStats;
	private DefaultTableModel tableModelUserStats;
	private JTree jTreeFileType;
	private JTree jTreeRepos = new JTree();
	
	private JButton btnRefresh = new JButton("Refresh from GitHub");

	private Map<String, RepoStats> repoStatsMap = new HashMap<>();
	private String selectedRepo;

	private enum ContributionBadge {
	    TEACHER("🎓", Color.DARK_GRAY,
	            "Teacher account",
	            "Teacher account: excluded from expected-share calculations."),
	    VERY_LOW("🛑", new Color(234, 23, 68),
	            "Very low / no contribution",
	            "Very low / no contribution: below expected or near zero. Check additional evidence."),
	    BELOW("🟠️", new Color(245, 143, 41),
	            "Below expected contribution",
	            "Below expected contribution: noticeable but under the expected share."),
	    BALANCED("✅", new Color(54, 130, 127),
	            "Balanced contribution",
	            "Balanced contribution: close to expected for the team size."),
	    HIGH("🌟", new Color(54, 130, 127),
	            "High contribution",
	            "High contribution: above expected for the team size.");

	    final String emoji;
	    final Color color;
	    final String shortLabel;
	    final String longLine;

	    ContributionBadge(String emoji, Color color, String shortLabel, String longLine) {
	        this.emoji = emoji;
	        this.color = color;
	        this.shortLabel = shortLabel;
	        this.longLine = longLine;
	    }

	    public String shortText() {
	    	return String.format("%s %s", emoji, shortLabel);
	    }
	}
	
	private enum AlertFlag {
	    ENGINE("🚀️", "Team “engine”", "Far above expected. Review task distribution and authorship."),
	    CLEANUP("🧹", "Cleanup/correction work", "High deletion ratio. Verify context and continuity."),
	    AI_PASTE("🧠", "AI/paste-like pattern", "Very high churn per commit vs repo average. Ask for a explanation."),
	    RHYTHM("⏱️", "Irregular rhythm", "Activity concentrated near the end of the period.");

	    final String emoji;
	    final String title;
	    final String description;

	    AlertFlag(String emoji, String title, String description) {
	        this.emoji = emoji;
	        this.title = title;
	        this.description = description;
	    }

	    public String shortText() {
	    	return String.format("%s %s", emoji, title);
	    }

	    public String htmlLine() {
	        return emoji + " <b>" + title + "</b>: " + description + "<br>";
	    }
	}

	private static class Interpretation {
	    final ContributionBadge badge;
	    final List<AlertFlag> flags;

	    public Interpretation(ContributionBadge badge, List<AlertFlag> flags) {
	        this.badge = badge;
	        this.flags = flags;
	    }
	}
	
	public MainWindow(List<RepoStats> data) {
		jTreeRepos.setRowHeight(23);

		jTreeRepos.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, 
					boolean selected, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

				String iconName = "resources/images/";
				
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object userObject = node.getUserObject();				

				// Check if the node represents a RepoStats object
				if (userObject instanceof RepoStats) {
					RepoStats repoStats = (RepoStats) userObject;
					iconName += repoStats.isPublic() ? "public.png" : "private.png";
					
					// If the repository is empty (no commits), change the text color to orange
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

				if (selected) {
				    setForeground(Color.WHITE);
				    setBackgroundSelectionColor(new Color(0, 120, 215));
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
		
		// Update the JTree with the initial data
		updateReposJTree(data);
		// Initialize the user stats table
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
		
		lblCommits.setToolTipText("<html><b>Unique commits</b> across all branches (deduplicated by SHA).<br>This is a global activity indicator (not the same as “Java commits per person”).</html>");
		lblCreationDate.setToolTipText("Repository creation date (from GitHub).");
		lblFirstCommit.setToolTipText("Earliest commit date found in the analyzed history.");
		lblLastCommit.setToolTipText("Latest commit date found in the analyzed history.");
		lblColeLines.setToolTipText("<html><b>Java LOC</b> = current number of lines in .java files (snapshot).<br>It measures code size, not effort.</html>");
		lblLinesChanged.setToolTipText("<html><b>Java churn</b> = added + deleted lines in .java files.<br>Computed from non-merge commits only.</html>");
		lblExternalRefs.setToolTipText("<html>Occurrences of patterns <b>IAG</b> or <b>FUENTE-EXTERNA</b> in .java files.<br>Useful to flag external/AI-assisted code references.</html>");
		lblURL.setToolTipText("Click to open the repository in your browser.");
		
        // MoseListener to open the URL when clicked
		lblURL.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    // Use default browser to open the URL
                	if (selectedRepo != null) {
                	    Desktop.getDesktop().browse(new URI(selectedRepo));
                	}
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            	if (selectedRepo != null) {
            		lblURL.setCursor(new Cursor(Cursor.HAND_CURSOR));
            	}
            }

            @Override
            public void mouseExited(MouseEvent e) {
            	if (selectedRepo != null) {
            		lblURL.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
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
            	lblFooter.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
            	lblFooter.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

		// Refresh button action
		btnRefresh.setToolTipText("Refresh (GitHub)");
		btnRefresh.addActionListener(e -> {						
			// SwingWorker to perform the refresh in the background
	        SwingWorker<List<RepoStats>, String> worker = new SwingWorker<>() {
	            
	            @Override
	            protected List<RepoStats> doInBackground() throws Exception {
	                // Load new data from GitHub
	            	List<RepoStats> newStats = GitHubDataLoader.getInstance().loadData(null, true);
	    	    	// Store the new data in the local cache
	    	    	DataManager.getInstance().storeData(newStats);
	    	    	// Return the new data
	    	    	return newStats;
	            }

	            @Override
	            protected void done() {
	            	try {
		            	// Update the UI with the new data
		    	    	updateReposJTree(get());
		    	    	
		    	    	SwingUtilities.invokeLater(() -> {
			            	// Show a success message
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

	        // Execute the worker
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
	
		// ToolTipManager personalized settings
		ToolTipManager.sharedInstance().setInitialDelay(300);   // appear after 0.3 segundos
		ToolTipManager.sharedInstance().setDismissDelay(20000); // show for 20 segundos
		ToolTipManager.sharedInstance().setReshowDelay(100);    // reshow after 0.1 segundos
		
		this.setVisible(true);
	}

	private void updateReposJTree(List<RepoStats> data) {
		repoStatsMap.clear();
		
		data.forEach(repo -> repoStatsMap.put(repo.getUrl(), repo));
		
		DefaultMutableTreeNode repoRootNode = new DefaultMutableTreeNode(String.format("%d Repositories", data.size()));
		
		data.forEach(repo -> {
			DefaultMutableTreeNode repoNode = new DefaultMutableTreeNode(repo);
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
	        public String getToolTipText(MouseEvent e) {
	            if (selectedRepo == null) return super.getToolTipText(e);

	            int viewRow = rowAtPoint(e.getPoint());
	            if (viewRow < 0) return super.getToolTipText(e);

	            int modelRow = convertRowIndexToModel(viewRow);

	            RepoStats repo = repoStatsMap.get(selectedRepo);
	            if (repo == null) return super.getToolTipText(e);
	            if (modelRow < 0 || modelRow >= repo.getUserStats().size()) return super.getToolTipText(e);

	            UserStats u = repo.getUserStats().get(modelRow);
	            return buildInterpretationTooltip(repo, u);
	        }
	    };
	    
	    jTableUserStats.setRowHeight(28);
	    JTableHeader header = jTableUserStats.getTableHeader();
	    header.setPreferredSize(new Dimension(header.getPreferredSize().width, 35));

	    // --- Table cell renderer (single source of truth for color + short tooltip) ---
	    TableCellRenderer cellRenderer = (table, value, isSelected, hasFocus, row, column) -> {
	        JLabel label = new JLabel();
	        label.setOpaque(true);

	        // 1) Safe value and formatted text
	        Object v = (value == null) ? "" : value;
	        String text = formatCellValue(v);
	        label.setText(text.startsWith("<html>") ? text : (" " + text));

	        // 2) Alignment rules (decided once, no redundant overrides)
	        label.setHorizontalAlignment(computeAlignment(v, column));

	        // 3) Color coding + short tooltip
	        RepoStats repo = (selectedRepo == null) ? null : repoStatsMap.get(selectedRepo);
	       
	        if (repo != null && row >= 0) {
	            int modelRow = table.convertRowIndexToModel(row);
	            
	            if (modelRow >= 0 && modelRow < repo.getUserStats().size()) {
	                UserStats user = repo.getUserStats().get(modelRow);
	                Interpretation it = interpret(repo, user);
	                
		            // Apply row color (all columns)
		            label.setForeground(it.badge.color);
	
		            // Tooltip for all columns
	                String shortTip = it.badge.shortText();	              
	                if (!it.flags.isEmpty()) {
	                    shortTip += "  |  " + it.flags.stream().map(AlertFlag::shortText).reduce((a,b)->a+"  "+b).orElse("");
	                }
	                label.setToolTipText(shortTip);
	            }
	        } else {
	            // Default appearance when no repo is selected
	            label.setForeground(table.getForeground());
	            label.setToolTipText(null);
	        }

	        // 4) Selection 
	        if (isSelected) {
	            label.setBackground(table.getSelectionBackground());
	            label.setForeground(table.getSelectionForeground());
	        } else {
	            label.setBackground(table.getBackground());
	        }

	        return label;
	    };

	    // Header tooltips
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
	        	RepoStats repo = repoStatsMap.get(selectedRepo);
	        	int viewRow = jTableUserStats.rowAtPoint(e.getPoint());
	        	if (repo != null && viewRow >= 0) {
	        	    int modelRow = jTableUserStats.convertRowIndexToModel(viewRow);
	        	    if (modelRow >= 0 && modelRow < repo.getUserStats().size()) {
	        	        lblStatus.setText(statusTextFor(repo.getUserStats().get(modelRow), repo));
	        	    } else {
	        	        lblStatus.setText(" ");
	        	    }
	        	} else {
	        	    lblStatus.setText(" ");
	        	}
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
			// Update labels with repository stats
			lblCreationDate.setText(String.format("• Creation date: %s", dateFormat.format(new Date(repoStats.getCreationDate()))));			
			
			if (repoStats.getFirstCommit() == -1) {
                lblFirstCommit.setText("• First commit: -");
            } else {
            	lblFirstCommit.setText(String.format("• First commit: %s", dateFormat.format(new Date(repoStats.getFirstCommit()))));
            }

			if (repoStats.getLastCommit() == -1) {
				lblLastCommit.setText("• Last commit: -");
			} else {			
				lblLastCommit.setText(String.format("• Last commit: %s", dateFormat.format(new Date(repoStats.getLastCommit()))));
			}
			
			int javaCommitsSum = repoStats.getUserStats().stream().mapToInt(UserStats::getCommits).sum();

			lblCommits.setText(String.format("• Total commits (unique): %d", repoStats.getCommits()));
			lblCommits.setToolTipText(String.format(
			    "<html>Unique commits across all branches (deduplicated by SHA).<br>Java commits (sum of users, non-merge commits touching .java): %d</html>",
			    javaCommitsSum
			));
			
			lblColeLines.setText(String.format("• Total lines of code: %d", repoStats.getCodeLines()));
			lblLinesChanged.setText(String.format("• Java churn (added+deleted): %d", repoStats.getLinesChanged()));
			lblLinesChanged.setToolTipText(String.format("Added: %d | Deleted: %d", repoStats.getLinesAdded(), repoStats.getLinesDeleted()));
			lblExternalRefs.setText(String.format("• External references: %d", repoStats.getExternalReferences()));
			lblURL.setText(String.format("<html>• <u><i>%s</i></u></html>", repoStats.getName()));			
			lblURL.setForeground(Color.BLUE);			

			// Clear and populate the user stats table
			tableModelUserStats.setRowCount(0);

			int repoChurn = repoStats.getLinesChanged(); // churn java
			
			repoStats.getUserStats().forEach(s -> {
			    int churn = s.getAdded() + s.getDeleted();
			    float pct = (repoChurn == 0) ? 0f : ((float) churn) / repoChurn;

			    Interpretation it = interpret(repoStats, s);
			    String displayName = String.format(
			    	    "<html><span style='font-size: 150%%;'>%s</span>&nbsp;%s</html>",
			    	    it.badge.emoji,
			    	    (s.getUsername() == null ? "" : s.getUsername())
			    	);

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
			lblCreationDate.setText("• Creation date:");
			lblFirstCommit.setText("• First commit:");
			lblLastCommit.setText("• Last commit:");
			lblCommits.setText("• Total commits:");
			lblColeLines.setText("• Total lines of code:");
			lblLinesChanged.setText("• Java churn (added+deleted):");
			lblExternalRefs.setText("• External references:");
			lblURL.setText("• URL:");

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
	
    private String formatCellValue(Object v) {
        if (v instanceof Long) {
            long ts = (Long) v;
            return (ts == -1L) ? "-" : dateFormat.format(new Date(ts));
        }
        if (v instanceof Float) {
            return String.format("%.2f %%", ((Float) v) * 100f);
        }
        // Integer and other values
        return String.valueOf(v);
    }

    private int computeAlignment(Object v, int column) {
        if (column == 0) return JLabel.LEFT;
        
        if (v instanceof Long || column == 7 || column == 8) return JLabel.CENTER;
        if (v instanceof Float) return JLabel.RIGHT;
        if (v instanceof Integer) return JLabel.RIGHT;

        return JLabel.LEFT;
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
	
	private Interpretation interpret(RepoStats repo, UserStats u) {
	    // Defensive defaults
	    if (repo == null || u == null) return new Interpretation(ContributionBadge.BELOW, List.of());

	    // Teacher is always a special case (excluded from expected-share calculations)
	    if (isTeacher(u)) return new Interpretation(ContributionBadge.TEACHER, List.of());

	    // Repo-wide churn (Java added + deleted, as computed in RepoStats)
	    int repoChurn = repo.getLinesChanged();

	    // User churn (Java added + deleted) and "real Java commits" (non-merge commits touching .java)
	    int uChurn = userChurn(u);
	    int commitsJava = u.getCommits();

	    // Hard guardrail: if there is no real Java activity, classify as VERY_LOW
	    // (prevents misleading shares when commits/lines are missing or repoChurn is small)
	    if (uChurn == 0 || commitsJava == 0) {
	        return new Interpretation(ContributionBadge.VERY_LOW, List.of());
	    }

	    // Expected contribution share is computed over "real contributors" only (excluding teacher)
	    List<UserStats> contributors = realContributorsExcludingTeacher(repo);
	    int n = Math.max(1, contributors.size());
	    double expected = 1.0 / n;

	    // User share of repo Java churn (ratio in [0..1])
	    double shareRaw = (repoChurn <= 0) ? 0.0 : (uChurn / (double) repoChurn);

	    // Keep the classification consistent with the GUI:
	    // the GUI shows share as a percentage with 2 decimals (e.g., 62.47%),
	    // which corresponds to rounding the ratio to 4 decimals (0.6247).
	    double share = Math.round(shareRaw * 10000.0) / 10000.0;

	    // Thresholds around the expected share:
	    // - veryLow: below 50% of expected (or near-zero)
	    // - okMin/okMax: "balanced band" = expected ±20%
	    double veryLow = expected * 0.5;
	    double okMin   = expected * 0.8;
	    double okMax   = expected * 1.2;

	    // ENGINE is treated as an additional flag (not a badge)
	    double engineT = expected * 2.0;

	    // Badge selection using contiguous ranges (no gaps, no overlaps):
	    // 1) share < veryLow  -> VERY_LOW
	    // 2) share < okMin    -> BELOW
	    // 3) share <= okMax   -> BALANCED
	    // 4) otherwise        -> HIGH
	    ContributionBadge badge;
	    if (share < veryLow) badge = ContributionBadge.VERY_LOW;
	    else if (share < okMin) badge = ContributionBadge.BELOW;
	    else if (share <= okMax) badge = ContributionBadge.BALANCED;
	    else badge = ContributionBadge.HIGH;

	    // Additional alert flags (secondary indicators)
	    List<AlertFlag> flags = new ArrayList<>();

	    // ENGINE: user far above expected (may indicate uneven task distribution / authorship concentration)
	    if (share >= engineT) flags.add(AlertFlag.ENGINE);

	    // CLEANUP: high deletion ratio with a minimum churn to reduce false positives
	    if (uChurn > 0) {
	        double delRatio = u.getDeleted() / (double) uChurn;
	        if (delRatio >= 0.55 && uChurn >= 200) flags.add(AlertFlag.CLEANUP);
	    }

	    // AI_PASTE: churn-per-commit much higher than repo average (heuristic indicator)
	    double churnPerCommit = (commitsJava <= 0) ? 0.0 : (uChurn / (double) commitsJava);
	    int totalCommitsJava = contributors.stream().mapToInt(UserStats::getCommits).sum();
	    int totalChurn = contributors.stream().mapToInt(this::userChurn).sum();
	    double avgChurnPerCommit = (totalCommitsJava > 0) ? totalChurn / (double) totalCommitsJava : 0.0;

	    if (commitsJava > 0 && avgChurnPerCommit > 0 && churnPerCommit >= avgChurnPerCommit * 2.5) {
	        flags.add(AlertFlag.AI_PASTE);
	    }

	    // RHYTHM: activity concentrated near the end of the repo timeline (heuristic indicator)
	    if (u.getFirstCommit() != -1 && u.getLastCommit() != -1 &&
	        repo.getFirstCommit() != -1 && repo.getLastCommit() != -1) {

	        long repoSpan = repo.getLastCommit() - repo.getFirstCommit();
	        long userLastOffset = u.getLastCommit() - repo.getFirstCommit();

	        if (repoSpan > 0 && (userLastOffset / (double) repoSpan) > 0.85 && uChurn >= 200) {
	            flags.add(AlertFlag.RHYTHM);
	        }
	    }

	    return new Interpretation(badge, flags);
	}

	private String buildInterpretationTooltip(RepoStats repo, UserStats u) {
	    Interpretation it = interpret(repo, u);

	    List<UserStats> contributors = realContributorsExcludingTeacher(repo);
	    int n = Math.max(1, contributors.size());
	    float expected = 1f / n;

	    StringBuilder sb = new StringBuilder("<html>");
	    sb.append("<b>Teaching interpretation (indicators)</b><br>");
	    sb.append(String.format("Active contributors (excluding teacher): <b>%d</b> → expected ≈ <b>%.0f%%</b><br><br>", n, expected * 100));
	    sb.append(it.badge.emoji).append(" <b>").append(it.badge.shortLabel).append("</b>");
	    sb.append(": ").append(it.badge.longLine.replaceFirst("^[^:]*:\\s*", "")).append("<br>");

	    for (AlertFlag f : it.flags) sb.append(f.htmlLine());

	    sb.append("</html>");
	    
	    return sb.toString();
	}
		
	private String statusTextFor(UserStats u, RepoStats repo) {
	    if (repo == null || u == null) return " ";
	    Interpretation it = interpret(repo, u);

	    List<String> parts = new ArrayList<>();
	    parts.add(it.badge.emoji + " " + it.badge.shortLabel);

	    for (AlertFlag f : it.flags) parts.add(f.shortText());

	    return String.join("   |   ", parts);
	}
}