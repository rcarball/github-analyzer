/**
 * This code was developed with AI assistance (ChatGPT) and reviewed by the author (see the unit tests for the validated parts).
 */
package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class MainWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private JLabel lblCreationDate;
	private JLabel lblFirstCommit;
	private JLabel lblLastCommit;
	private JLabel lblCommits;
	private JLabel lblCodeLines;
	private JLabel lblLinesChanged;
	private JLabel lblExternalRefs;
	private JLabel lblURL;
	private JLabel lblStatus = new JLabel(" ");
	// Locale-independent, year-aware, sortable date format. "MMM-dd" hid the year
	// (a course spanning e.g. Oct→Jan could not tell 2025 from 2026) and localized
	// the month name ("ene"/"Jan"), making the UI inconsistent across systems.
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
	private JTable jTableUserStats;
	private DefaultTableModel tableModelUserStats;
	private JTree jTreeFileType;
	private JTree jTreeRepos = new JTree();
	private JTextField repoFilter = new JTextField();
	private List<RepoStats> allRepos = new ArrayList<>();

	private JButton btnRefresh = new JButton("Refresh from GitHub");

	private Map<String, RepoStats> repoStatsMap = new HashMap<>();
	private String selectedRepo;

	private static final double THRESHOLD_VERY_LOW     = 0.50; // < 50 % del share esperado
	private static final double THRESHOLD_BELOW        = 0.80; // < 80 % del share esperado
	private static final double THRESHOLD_HIGH         = 1.20; // > 120 % del share esperado
	private static final double AI_PASTE_MULTIPLIER    = 2.50; // churn/commit vs. media del equipo

	enum ContributionBadge {
		TEACHER("🎓", Color.DARK_GRAY,
		        "Teacher account",
		        "Teacher account: excluded from expected-share calculations."),

		VERY_LOW("⛔", new Color(234, 23, 68),
		        "Very low / no contribution",
		        "Very low / no contribution: below expected or near zero."),

		BELOW("⚠", new Color(245, 143, 41),
		        "Below expected contribution",
		        "Below expected contribution: noticeable but under the expected share."),

		BALANCED("✓", new Color(54, 130, 127),
		        "Balanced contribution",
		        "Balanced contribution: close to expected for the team size."),

		HIGH("★", new Color(54, 130, 127),
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
	
	enum AlertFlag {
	    AI_PASTE("📋", "AI/paste-like pattern", "Very high churn per commit vs repo average.");

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

				String iconFile = "github.png";

				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				Object userObject = node.getUserObject();

				// Check if the node represents a RepoStats object
				if (userObject instanceof RepoStats) {
					RepoStats repoStats = (RepoStats) userObject;
					iconFile = repoStats.isPublic() ? "public.png" : "private.png";

					setText(repoStats.getGroup() != null && !repoStats.getGroup().isEmpty() ? repoStats.getGroup() : repoStats.getName());

					// If the repository is empty (no commits), change the text color to orange
					if (repoStats.getCommits() == 0) {
						component.setForeground(new Color(245, 143, 41));
						setText(getText() + " (empty repository)");
					} else {
						component.setForeground(new Color(54, 130, 127));
					}

					setToolTipText(repoStats.getName());
				} else {
					component.setForeground(Color.BLACK);
				}

				if (selected) {
				    setForeground(Color.WHITE);
				    setBackgroundSelectionColor(new Color(0, 120, 215));
				}

				ImageIcon icon = loadIcon(iconFile);
				if (icon != null) this.setIcon(scaleIcon(icon));

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

		// Filter box above the repository tree (matches group / name / URL).
		repoFilter.setToolTipText("Filter repositories by group, name or URL");
		repoFilter.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { rebuildRepoTree(); }
			@Override public void removeUpdate(DocumentEvent e) { rebuildRepoTree(); }
			@Override public void changedUpdate(DocumentEvent e) { rebuildRepoTree(); }
		});

		JPanel reposPanel = new JPanel(new BorderLayout(0, 4));
		reposPanel.setBorder(new TitledBorder("Repositories"));
		reposPanel.add(repoFilter, BorderLayout.NORTH);
		reposPanel.add(reposJScrollPane, BorderLayout.CENTER);

		JPanel panelDetails = new JPanel();
		panelDetails.setBorder(new TitledBorder("Repository overview"));
		panelDetails.setLayout(new GridLayout(4, 2, 0, 0));

		lblCommits       = new JLabel("• Total commits (unique):");
		lblCreationDate  = new JLabel("• Created:");
		lblFirstCommit   = new JLabel("• First commit:");
		lblLastCommit    = new JLabel("• Last commit:");
		lblCodeLines     = new JLabel("• Java LOC (snapshot):");
		lblLinesChanged  = new JLabel("• Java churn (added+deleted):");
		lblExternalRefs  = new JLabel("• External references:");
		lblURL           = new JLabel("• Open repository:");
		
		lblCommits.setToolTipText("<html><b>Unique commits</b> across all branches (deduplicated by SHA).<br>This is a global activity indicator (not the same as “Java commits per person”).</html>");
		lblCreationDate.setToolTipText("Repository creation date (from GitHub).");
		lblFirstCommit.setToolTipText("Earliest commit date found in the analyzed history.");
		lblLastCommit.setToolTipText("Latest commit date found in the analyzed history.");
		lblCodeLines.setToolTipText("<html><b>Java LOC</b> = current number of lines in .java files (snapshot).<br>It measures code size, not effort.</html>");
		lblLinesChanged.setToolTipText("<html><b>Java churn</b> = added + deleted lines in .java files.<br>Computed from non-merge commits only.</html>");
		lblExternalRefs.setToolTipText("<html>Occurrences of the standalone markers <b>IAG</b> or <b>FUENTE-EXTERNA</b> in .java files (whole-word, not inside identifiers).<br>Useful to flag external/AI-assisted code references.</html>");
		lblURL.setToolTipText("Click to open the repository in your browser.");
		
        // MouseListener to open the URL when clicked
		lblURL.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (selectedRepo != null) {
                    openInBrowser(selectedRepo);
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
		panelDetails.add(lblCodeLines);
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
                openInBrowser("https://www.flaticon.com/authors/pixel-perfect");
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
			// Give immediate feedback and prevent overlapping refreshes.
			btnRefresh.setEnabled(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			lblStatus.setText("Refreshing from GitHub…");

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
	            		List<RepoStats> newStats = get();
		    	    	updateReposJTree(newStats);

		    	    	int expected = GitHubDataLoader.getInstance().getConfiguredRepositoryCount();
		    	    	int analyzed = newStats.size();

		    	    	if (analyzed < expected) {
		    	    		JOptionPane.showMessageDialog(
		    	    			    MainWindow.this,
		    	    			    String.format(
		    	    			        "Refresh finished, but only %d of %d repositories could be analyzed.\n\n"
		    	    			        + "Some were skipped (private / no access, GitHub rate limits, or an "
		    	    			        + "invalid token). See the console log for details.",
		    	    			        analyzed, expected),
		    	    			    "Refresh completed with warnings",
		    	    			    JOptionPane.WARNING_MESSAGE
		    	    			);
		    	    	} else {
		    	    		JOptionPane.showMessageDialog(
		    	    			    MainWindow.this,
		    	    			    String.format("Data refreshed successfully (%d repositories).", analyzed),
		    	    			    "Refresh completed",
		    	    			    JOptionPane.INFORMATION_MESSAGE
		    	    			);
		    	    	}
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						showRefreshFailedDialog();
					} catch (ExecutionException ex) {
						showRefreshFailedDialog();
					} finally {
						setCursor(Cursor.getDefaultCursor());
						btnRefresh.setEnabled(true);
						lblStatus.setText(" ");
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
		this.add(reposPanel, BorderLayout.WEST);
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

		allRepos = (data == null) ? new ArrayList<>() : data;
		allRepos.forEach(repo -> repoStatsMap.put(repo.getUrl(), repo));

		rebuildRepoTree();
	}

	/** Rebuilds the repository tree, honoring the current filter text. */
	private void rebuildRepoTree() {
		String query = (repoFilter == null) ? "" : repoFilter.getText().trim().toLowerCase();

		List<RepoStats> shown = new ArrayList<>();
		for (RepoStats repo : allRepos) {
			if (matchesFilter(repo, query)) shown.add(repo);
		}

		String rootLabel = query.isEmpty()
				? String.format("%d Repositories", shown.size())
				: String.format("%d / %d Repositories", shown.size(), allRepos.size());

		DefaultMutableTreeNode repoRootNode = new DefaultMutableTreeNode(rootLabel);
		shown.forEach(repo -> repoRootNode.add(new DefaultMutableTreeNode(repo)));

		jTreeRepos.setModel(new DefaultTreeModel(repoRootNode));
		for (int i = 0; i < jTreeRepos.getRowCount(); i++) jTreeRepos.expandRow(i);
	}

	/** A repo matches when the (case-insensitive) query is contained in its group, name or URL. */
	private boolean matchesFilter(RepoStats repo, String query) {
		if (query == null || query.isEmpty()) return true;
		String group = (repo.getGroup() == null) ? "" : repo.getGroup().toLowerCase();
		String name  = (repo.getName()  == null) ? "" : repo.getName().toLowerCase();
		String url   = (repo.getUrl()   == null) ? "" : repo.getUrl().toLowerCase();
		return group.contains(query) || name.contains(query) || url.contains(query);
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

	    tableModelUserStats = new DefaultTableModel(new Vector<Vector<Object>>(), cabecera) {
	        private static final long serialVersionUID = 1L;

	        @Override
	        public boolean isCellEditable(int row, int col) {
	            return false;
	        }

	        // Real column types so the row sorter compares numbers as numbers
	        // (and dates chronologically), not as strings.
	        @Override
	        public Class<?> getColumnClass(int columnIndex) {
	            switch (columnIndex) {
	                case 0:        return String.class;   // username
	                case 4:        return Float.class;    // % churn
	                case 7: case 8:return Long.class;     // last / first commit (timestamps)
	                default:       return Integer.class;  // added, deleted, churn, files, commits
	            }
	        }
	    };

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
	        String base = (value == null) ? "" : value.toString();

	        // Show the active sort direction on the sorted column.
	        String arrow = "";
	        RowSorter<?> sorter = table.getRowSorter();
	        if (sorter != null) {
	            for (RowSorter.SortKey key : sorter.getSortKeys()) {
	                if (key.getColumn() == table.convertColumnIndexToModel(column)) {
	                    if (key.getSortOrder() == SortOrder.ASCENDING) arrow = "▲";       // ▲
	                    else if (key.getSortOrder() == SortOrder.DESCENDING) arrow = "▼"; // ▼
	                }
	            }
	        }
	        if (!arrow.isEmpty()) {
	            base = base.endsWith("</html>")
	                    ? base.substring(0, base.length() - "</html>".length()) + " " + arrow + "</html>"
	                    : base + " " + arrow;
	        }

	        JLabel result = new JLabel(base);

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
	    jTableUserStats.setAutoCreateRowSorter(true);   // click a header to sort
	    jTableUserStats.setFillsViewportHeight(true);
	    jTableUserStats.getTableHeader().setDefaultRenderer(headerRenderer);

	    jTableUserStats.getColumnModel().getColumn(0).setPreferredWidth(260); // username
	    jTableUserStats.getColumnModel().getColumn(1).setPreferredWidth(55);  // added
	    jTableUserStats.getColumnModel().getColumn(2).setPreferredWidth(55);  // deleted
	    jTableUserStats.getColumnModel().getColumn(3).setPreferredWidth(55);  // churn
	    jTableUserStats.getColumnModel().getColumn(4).setPreferredWidth(55);  // % churn
	    jTableUserStats.getColumnModel().getColumn(5).setPreferredWidth(55);  // java files
	    jTableUserStats.getColumnModel().getColumn(6).setPreferredWidth(55);  // java commits
	    jTableUserStats.getColumnModel().getColumn(7).setPreferredWidth(90);  // last  (yyyy-MM-dd)
	    jTableUserStats.getColumnModel().getColumn(8).setPreferredWidth(90);  // first (yyyy-MM-dd)

	    // Register for both Object and Number so our renderer (colors + date/%
	    // formatting) wins over JTable's built-in Number/Float renderers, which the
	    // typed getColumnClass() would otherwise select for the numeric columns.
	    jTableUserStats.setDefaultRenderer(Object.class, cellRenderer);
	    jTableUserStats.setDefaultRenderer(Number.class, cellRenderer);
	    // "% churn" column: draw a proportional bar behind the percentage.
	    jTableUserStats.getColumnModel().getColumn(4).setCellRenderer(new PercentBarRenderer());

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
			    "<html>Unique commits across all branches (deduplicated by SHA): <b>%d</b><br>"
			    + "&nbsp;&nbsp;• Merge commits: %d<br>"
			    + "&nbsp;&nbsp;• Non-merge commits: %d<br>"
			    + "&nbsp;&nbsp;• Java commits (sum of users, non-merge touching .java): %d<br>"
			    + "<i>Per-person Java commits sum to ≤ non-merge commits: they only count commits that changed .java.</i></html>",
			    repoStats.getCommits(),
			    repoStats.getMergeCommits(),
			    repoStats.getNonMergeCommits(),
			    javaCommitsSum
			));
			
			lblCodeLines.setText(String.format("• Java LOC (snapshot): %d", repoStats.getCodeLines()));
			lblLinesChanged.setText(String.format("• Java churn (added+deleted): %d", repoStats.getLinesChanged()));
			lblLinesChanged.setToolTipText(String.format("Added: %d | Deleted: %d", repoStats.getLinesAdded(), repoStats.getLinesDeleted()));
			lblExternalRefs.setText(String.format("• External references: %d", repoStats.getExternalReferences()));
			lblURL.setText(String.format("<html>• <u><i>%s</i></u></html>", repoStats.getName()));			
			lblURL.setForeground(Color.BLUE);			

			// Clear and populate the user stats table
			tableModelUserStats.setRowCount(0);

			repoStats.getUserStats().forEach(s -> {
			    int churn = s.getChurn();
			    // Share of the team churn (excluding teacher). NaN for the teacher row (shown as "-").
			    float pct = ContributionMetrics.teamShare(repoStats, s);

			    Interpretation it = interpret(repoStats, s);
			    String displayName = String.format(
			    	    "%s %s",
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
			lblCodeLines.setText("• Java LOC (snapshot):");
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
	
	/**
	 * Loads a tree icon by file name. Tries the classpath first ({@code /images/<name>},
	 * which is how the icons are bundled inside the runnable JAR), then falls back to
	 * the {@code resources/images/} folder (handy when running from the project dir in
	 * the IDE). Returns {@code null} if the icon cannot be found, so callers can skip it.
	 */
	private ImageIcon loadIcon(String fileName) {
		java.net.URL url = MainWindow.class.getResource("/images/" + fileName);
		if (url != null) {
			return new ImageIcon(url);
		}
		java.io.File file = new java.io.File("resources/images/" + fileName);
		if (file.isFile()) {
			return new ImageIcon(file.getPath());
		}
		return null;
	}

	private ImageIcon scaleIcon(ImageIcon icon) {
		return new ImageIcon(icon.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));
	}

	/**
	 * Opens a URL in the system browser, giving the user visible feedback (a
	 * dialog with the address to copy) when it cannot be done — unsupported
	 * platform, no default browser, malformed URL — instead of failing silently
	 * on the console.
	 */
	private void openInBrowser(String uri) {
		if (uri == null || uri.isBlank()) return;

		String problem;
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(uri));
				return;
			}
			problem = "Opening a browser is not supported on this system.";
		} catch (Exception ex) {
			problem = ex.getMessage();
		}

		JOptionPane.showMessageDialog(
				this,
				"Could not open the link automatically.\n\n" + uri
						+ (problem == null ? "" : "\n\n(" + problem + ")")
						+ "\n\nYou can copy the address and open it manually.",
				"Open link",
				JOptionPane.WARNING_MESSAGE);
	}

	private void showRefreshFailedDialog() {
		JOptionPane.showMessageDialog(
				this,
				"Refresh failed.\n\nTip: GitHub may throttle requests when refreshing many repositories.\n"
						+ "Try again later or use offline mode (cached data).",
				"Refresh failed",
				JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Cell renderer for the "% churn" column: paints a horizontal bar proportional
	 * to the value (in the person's badge color) with the percentage on top, so the
	 * contribution split is readable at a glance. The teacher row (NaN) shows "-".
	 */
	private class PercentBarRenderer extends JComponent implements TableCellRenderer {
		private static final long serialVersionUID = 1L;

		private float fraction;      // 0..1, or NaN
		private String text = "";
		private Color barColor = new Color(0, 0, 0, 0);
		private Color textColor = Color.BLACK;
		private Color background = Color.WHITE;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {
			setFont(table.getFont());

			fraction = (value instanceof Float) ? (Float) value : Float.NaN;
			text = Float.isNaN(fraction) ? "-" : String.format("%.2f %%", fraction * 100f);

			Color badge = table.getForeground();
			RepoStats repo = (selectedRepo == null) ? null : repoStatsMap.get(selectedRepo);
			if (repo != null && row >= 0) {
				int modelRow = table.convertRowIndexToModel(row);
				if (modelRow >= 0 && modelRow < repo.getUserStats().size()) {
					badge = interpret(repo, repo.getUserStats().get(modelRow)).badge.color;
				}
			}

			textColor = isSelected ? table.getSelectionForeground() : badge;
			background = isSelected ? table.getSelectionBackground() : table.getBackground();
			barColor = new Color(badge.getRed(), badge.getGreen(), badge.getBlue(), 55); // translucent
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();

			g.setColor(background);
			g.fillRect(0, 0, w, h);

			if (!Float.isNaN(fraction) && fraction > 0f) {
				int barW = Math.round(Math.min(1f, fraction) * (w - 6));
				g.setColor(barColor);
				g.fillRoundRect(3, 4, Math.max(1, barW), h - 8, 6, 6);
			}

			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(textColor);
			FontMetrics fm = g2.getFontMetrics(getFont());
			int tx = w - fm.stringWidth(text) - 6;               // right-aligned like the other numbers
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(text, tx, ty);
		}
	}
	
    private String formatCellValue(Object v) {
        if (v instanceof Long) {
            long ts = (Long) v;
            return (ts == -1L) ? "-" : dateFormat.format(new Date(ts));
        }
        if (v instanceof Float) {
            float f = (Float) v;
            if (Float.isNaN(f)) return "-";   // e.g. teacher row: excluded from the share model
            return String.format("%.2f %%", f * 100f);
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
	
	// Teacher / contributor / team-churn logic lives in ContributionMetrics so
	// the GUI and the CSV export always agree. These thin wrappers keep the
	// existing call sites readable.
	private boolean isTeacher(UserStats u) {
	    return ContributionMetrics.isTeacher(u);
	}

	private int userChurn(UserStats u) {
	    return u.getChurn();
	}

	private List<UserStats> realContributorsExcludingTeacher(RepoStats repo) {
	    return ContributionMetrics.activeContributors(repo);
	}
	
	private Interpretation interpret(RepoStats repo, UserStats u) {
	    // Defensive defaults
	    if (repo == null || u == null) return new Interpretation(ContributionBadge.BELOW, List.of());

	    // Teacher is always a special case (excluded from expected-share calculations)
	    if (isTeacher(u)) return new Interpretation(ContributionBadge.TEACHER, List.of());

	    // Expected contribution share is computed over "real contributors" only (excluding teacher).
	    // The share denominator is the SAME population's churn (team churn), so
	    // share and expected=1/n are directly comparable and shares sum to 1.
	    List<UserStats> contributors = realContributorsExcludingTeacher(repo);

	    int teamChurn = contributors.stream().mapToInt(UserStats::getChurn).sum();
	    int uChurn = userChurn(u);
	    int commitsJava = u.getCommits();

	    ContributionBadge badge = classifyBadge(uChurn, commitsJava, teamChurn, contributors.size());

	    // Additional alert flags (secondary indicators)
	    List<AlertFlag> flags = new ArrayList<>();
	    int totalCommitsJava = contributors.stream().mapToInt(UserStats::getCommits).sum();
	    int totalChurn = contributors.stream().mapToInt(this::userChurn).sum();
	    if (isAiPasteLike(uChurn, commitsJava, totalChurn, totalCommitsJava)) {
	        flags.add(AlertFlag.AI_PASTE);
	    }

	    return new Interpretation(badge, flags);
	}

	/**
	 * Pure contribution-badge classification (excluding the TEACHER case, which
	 * the caller decides). Package-visible for unit testing.
	 *
	 * <p>With {@code expected = 1/n} (n = active contributors, min 1) and
	 * {@code share = round(userChurn/teamChurn, 4)} where {@code teamChurn} is the
	 * total churn of those same active contributors, the badge is chosen over
	 * contiguous ranges: {@code share < 0.5*expected} → VERY_LOW,
	 * {@code < 0.8*expected} → BELOW, {@code <= 1.2*expected} → BALANCED,
	 * otherwise HIGH. As a hard guardrail, a user with no Java churn or no Java
	 * commits is always VERY_LOW.
	 */
	static ContributionBadge classifyBadge(int userChurn, int commitsJava, int teamChurn, int contributorCount) {
	    // Hard guardrail: if there is no real Java activity, classify as VERY_LOW
	    // (prevents misleading shares when commits/lines are missing or teamChurn is small)
	    if (userChurn == 0 || commitsJava == 0) {
	        return ContributionBadge.VERY_LOW;
	    }

	    int n = Math.max(1, contributorCount);
	    double expected = 1.0 / n;

	    // User share of the team Java churn (ratio in [0..1]).
	    double shareRaw = (teamChurn <= 0) ? 0.0 : (userChurn / (double) teamChurn);

	    // Keep the classification consistent with the GUI: the GUI shows share as
	    // a percentage with 2 decimals (e.g., 62.47%), i.e. the ratio rounded to 4.
	    double share = Math.round(shareRaw * 10000.0) / 10000.0;

	    double veryLow = expected * THRESHOLD_VERY_LOW;
	    double okMin   = expected * THRESHOLD_BELOW;
	    double okMax   = expected * THRESHOLD_HIGH;

	    if (share < veryLow) return ContributionBadge.VERY_LOW;
	    else if (share < okMin) return ContributionBadge.BELOW;
	    else if (share <= okMax) return ContributionBadge.BALANCED;
	    else return ContributionBadge.HIGH;
	}

	/**
	 * Heuristic AI/paste-like detector: true when the user's churn-per-commit is
	 * at least {@link #AI_PASTE_MULTIPLIER}× the repo average. Package-visible for
	 * unit testing.
	 */
	static boolean isAiPasteLike(int userChurn, int commitsJava, int totalChurn, int totalCommitsJava) {
	    if (commitsJava <= 0) return false;
	    double churnPerCommit = userChurn / (double) commitsJava;
	    double avgChurnPerCommit = (totalCommitsJava > 0) ? totalChurn / (double) totalCommitsJava : 0.0;
	    return avgChurnPerCommit > 0 && churnPerCommit >= avgChurnPerCommit * AI_PASTE_MULTIPLIER;
	}

	private String buildInterpretationTooltip(RepoStats repo, UserStats u) {
	    Interpretation it = interpret(repo, u);

	    List<UserStats> contributors = realContributorsExcludingTeacher(repo);
	    int n = Math.max(1, contributors.size());
	    float expected = 1f / n;

	    StringBuilder sb = new StringBuilder("<html>");
	    sb.append("<b>Interpretation</b><br>");
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