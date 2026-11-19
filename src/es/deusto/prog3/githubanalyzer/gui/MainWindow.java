// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI ASSISTANCE AND REVIEW DISCLOSURE
 *
 * The initial version of this codebase was developed in 2024 with partial
 * assistance from ChatGPT (OpenAI).
 *
 * From July to September 2026, the codebase was reviewed and audited using
 * Claude Code (Anthropic) and Codex (OpenAI).
 *
 * The resulting version was reviewed, tested, and refined to identify and
 * correct issues within the scope of the performed verification activities.
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import java.net.URL;
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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.RepositoryConfig;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.format.NumberFormats;
import es.deusto.prog3.githubanalyzer.persistence.AuthorshipStore;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class MainWindow extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final int ICON_SIZE = 24;
	private static final int STATUS_BAR_HEIGHT = ICON_SIZE + 4;

	private JLabel lblCreationDate;
	private JLabel lblFirstCommit;
	private JLabel lblLastCommit;
	private JLabel lblCommits;
	private JLabel lblCodeLines;
	private JLabel lblLinesChanged;
	private JLabel lblExternalRefs;
	private JLabel lblURL;
	private JLabel lblTeamSize;
	private JLabel lblContributors;
	private JLabel lblAuthors;
	private JLabel lblStatus = new JLabel(" ");
	private SimpleDateFormat dateFormatShort = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
	private SimpleDateFormat dateFormatLong = new SimpleDateFormat("yyyy-MMM-dd - HH:mm", java.util.Locale.ROOT);
	private JTable jTableUserStats;
	private DefaultTableModel tableModelUserStats;
	private JTree jTreeFileType;
	private JTree jTreeRepos = new JTree();
	private List<RepoStats> allRepos = new ArrayList<>();

	private JButton btnRefresh = new JButton("Refresh all from GitHub");
	private final JButton btnRefreshPending = new JButton("Refresh pending");
	private final java.util.Set<String> failedDownloads = new java.util.HashSet<>();
	private JButton btnConfig = new JButton("Config");
	private JButton btnAuthorship = new JButton("Manage authorship…");

	private Map<String, RepoStats> repoStatsMap = new HashMap<>();
	private String selectedRepo;
	private final Map<String, GitHubDataLoader.UpdateStatus> remoteUpdates = new HashMap<>();
	private int checkGeneration;
	private SwingWorker<?, ?> updateWorker;
	private Runnable cancelActiveDownload;
	private JDialog activeDownloadDialog;
	private boolean closePromptOpen;
	private final CloseCoordinator closing = new CloseCoordinator(() -> !btnRefresh.isEnabled(), () -> {
		setStatusText("Closing… Waiting for the current operation to finish safely.");
		if (cancelActiveDownload != null)
			cancelActiveDownload.run();
	}, this::disposeForExit);

	// Per-user interpretation cache for the currently loaded repo, so the table
	// renderers/tooltips don't recompute it (with streams) on every cell repaint.
	// Rebuilt whenever a repository is loaded. Keyed by identity (per-repo
	// instances).
	private final java.util.Map<UserStats, Interpretation> interpretationCache = new java.util.IdentityHashMap<>();

	private static final double AI_PASTE_MULTIPLIER = 2.50; // churn/commit vs. media del equipo

	enum ContributionBadge {
		TEACHER("TEACHER.png", Color.DARK_GRAY, "Teacher account",
				"Teacher account: excluded from expected-share calculations."),

		EXCLUDED("TEACHER.png", Color.DARK_GRAY, "Excluded account",
				"Excluded account: visible activity, excluded from team calculations."),

		VERY_LOW("VERY_LOW.png", new Color(234, 23, 68), "Very low / no contribution",
				"Very low / no contribution: below expected or near zero."),

		BELOW("BELOW.png", new Color(245, 143, 41), "Below expected contribution",
				"Below expected contribution: noticeable but under the expected share."),

		BALANCED("BALANCED.png", new Color(54, 130, 127), "Balanced contribution",
				"Balanced contribution: close to expected for the team size."),

		HIGH("HIGH.png", new Color(54, 130, 127), "High contribution",
				"High contribution: above expected for the team size.");

		final String imageFile;
		final Color color;
		final String shortLabel;
		final String longLine;
		private ImageIcon icon;

		ContributionBadge(String imageFile, Color color, String shortLabel, String longLine) {
			this.imageFile = imageFile;
			this.color = color;
			this.shortLabel = shortLabel;
			this.longLine = longLine;
		}

		/**
		 * Loads the badge icon from the packaged resources on first use. Tests can
		 * exercise the classification logic without requiring graphical resources.
		 */
		public ImageIcon icon() {
			if (icon == null) {
				ImageIcon loadedIcon = MainWindow.loadIcon(imageFile);
				icon = loadedIcon == null ? null : MainWindow.scaleIcon(loadedIcon);
			}
			return icon;
		}

		public String shortText() {
			return shortLabel;
		}
	}

	enum AlertFlag {
		AI_PASTE("AI.png", "AI/paste-like pattern", "Very high churn per commit vs repo average.");

		final String imageFile;
		final String title;
		final String description;
		private ImageIcon icon;

		AlertFlag(String imageFile, String title, String description) {
			this.imageFile = imageFile;
			this.title = title;
			this.description = description;
		}

		public ImageIcon icon() {
			if (icon == null) {
				ImageIcon loadedIcon = MainWindow.loadIcon(imageFile);
				icon = loadedIcon == null ? null : MainWindow.scaleIcon(loadedIcon);
			}
			return icon;
		}

		public String shortText() {
			return title;
		}

		public String htmlIcon() {
			String source = MainWindow.iconHtmlSource(imageFile);
			return source == null ? ""
					: "<img src=\"" + source + "\" width=\"" + ICON_SIZE + "\" height=\"" + ICON_SIZE + "\">";
		}

		public String htmlShortText() {
			String image = htmlIcon();
			return image.isEmpty() ? shortText() : image + " " + shortText();
		}

		public String htmlLine() {
			return "<b>" + title + "</b>: " + description + "<br>";
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

	/**
	 * Keeps configuration locked until refresh completion, including error
	 * handling.
	 */
	static abstract class RefreshWorker extends SwingWorker<List<RepoStats>, GitHubDataLoader.DownloadProgress> {
		private final JButton[] buttons;

		RefreshWorker(JButton... buttons) {
			this.buttons = buttons;
			for (JButton button : buttons)
				button.setEnabled(false);
		}

		protected abstract void refreshFinished();

		@Override
		protected final void done() {
			try {
				refreshFinished();
			} finally {
				for (JButton button : buttons)
					button.setEnabled(true);
			}
		}
	}

	/** Waits for actual background cleanup before unlocking controls or reloading saved data. */
	static abstract class DownloadWorker extends RefreshWorker {
		private Thread runner;
		private boolean stopping;

		DownloadWorker(JButton... buttons) {
			super(buttons);
		}

		final synchronized void requestCancellation() {
			stopping = true;
			if (runner != null)
				runner.interrupt();
		}

		final synchronized boolean cancellationRequested() {
			return stopping;
		}

		protected abstract List<RepoStats> download();
		protected abstract List<RepoStats> readSavedData();

		@Override
		protected final List<RepoStats> doInBackground() {
			try {
				synchronized (this) {
					runner = Thread.currentThread();
					if (stopping)
						throw new java.util.concurrent.CancellationException();
				}
				return download();
			} catch (java.util.concurrent.CancellationException ex) {
				if (!cancellationRequested())
					throw ex;
			} finally {
				synchronized (this) {
					runner = null;
				}
				if (cancellationRequested())
					Thread.interrupted();
			}
			// This remains on the background thread, after download tasks have stopped.
			return readSavedData();
		}
	}

	public MainWindow(List<RepoStats> data) {
		jTreeRepos.setRowHeight(23);
		jTreeRepos.setCellRenderer(new RepositoryTreeRenderer(remoteUpdates, failedDownloads));
		btnRefresh.addPropertyChangeListener("enabled", event -> {
			updateRefreshActions();
			closing.operationFinished();
		});
		installRepositoryRefreshMenu();

		jTreeRepos.addTreeSelectionListener(e -> {
			if (jTreeRepos.getSelectionPath() == null) {
				loadRepoStats(null);
				selectedRepo = null;
				return;
			}
			boolean selectionAdded = Arrays.stream(e.getPaths())
					.anyMatch(path -> path.equals(jTreeRepos.getSelectionPath()) && e.isAddedPath(path));
			if (!selectionAdded)
				return;
			DefaultMutableTreeNode selectedNode = ((DefaultMutableTreeNode) jTreeRepos.getSelectionPath()
					.getLastPathComponent());
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

		JScrollPane reposJScrollPane = repositoryTreeScrollPane(jTreeRepos);

		JPanel reposPanel = new JPanel(new BorderLayout());
		reposPanel.setBorder(new TitledBorder("Repositories"));
		reposPanel.add(reposJScrollPane, BorderLayout.CENTER);

		GridLayout overviewLayout = new GridLayout(0, 3, 12, 4);
		JPanel panelDetails = new JPanel(overviewLayout);
		panelDetails.setBorder(new TitledBorder("Repository overview"));

		lblCommits = new JLabel("• Total commits (unique):");
		lblCreationDate = new JLabel("• Created:");
		lblFirstCommit = new JLabel("• First commit:");
		lblLastCommit = new JLabel("• Last commit:");
		lblCodeLines = new JLabel("• Java LOC (default branch):");
		lblLinesChanged = new JLabel("• Java churn (added+deleted):");
		lblExternalRefs = new JLabel("• External references (default branch):");
		lblURL = new JLabel("• Open repository:");
		lblTeamSize = new JLabel("• Team size:");
		lblContributors = new JLabel("• Contributors:");
		lblAuthors = new JLabel("• Author identities:");
		lblContributors.setToolTipText(
				"People after explicit assignments, with Java activity, excluding teachers and excluded accounts.");
		lblAuthors.setToolTipText(
				"Distinct original Git author identities before assignments, across all branches and file types.");

		lblCommits.setToolTipText(
				"<html><b>Unique commits</b> across all branches (deduplicated by SHA).<br>This is a global activity indicator (not the same as “Java commits per person”).</html>");
		lblCreationDate.setToolTipText("Repository creation date (from GitHub).");
		lblFirstCommit
				.setToolTipText("Earliest committer date in the current branch histories; unknown dates are omitted.");
		lblLastCommit
				.setToolTipText("Latest committer date in the current branch histories; unknown dates are omitted.");
		lblCodeLines.setToolTipText(
				"<html><b>Java LOC</b> = current number of lines in .java files on GitHub's default branch.<br>It measures code size, not effort.</html>");
		lblLinesChanged.setToolTipText(
				"<html><b>Java churn</b> = added + deleted lines in .java files.<br>Computed from non-merge commits only.</html>");
		lblExternalRefs.setToolTipText(
				"<html>Occurrences of the standalone markers <b>IAG</b> or <b>FUENTE-EXTERNA</b> in .java files on the default branch (whole-word, not inside identifiers).<br>Useful to flag external/AI-assisted code references.</html>");
		lblURL.setToolTipText("Left-click to open in your browser; right-click to copy the URL.");

		// Right-click menu: open or copy the full repository URL.
		final JPopupMenu urlPopup = new JPopupMenu();
		JMenuItem openItem = new JMenuItem("Open in browser");
		openItem.addActionListener(event -> {
			if (selectedRepo != null)
				openInBrowser(selectedRepo);
		});
		JMenuItem copyItem = new JMenuItem("Copy URL");
		copyItem.addActionListener(event -> copyToClipboard(selectedRepo));
		urlPopup.add(openItem);
		urlPopup.add(copyItem);

		// MouseListener to open the URL when clicked
		lblURL.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (selectedRepo != null && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
					openInBrowser(selectedRepo);
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}

			private void maybeShowPopup(MouseEvent e) {
				if (selectedRepo != null && e.isPopupTrigger()) {
					urlPopup.show(e.getComponent(), e.getX(), e.getY());
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
		panelDetails.add(lblTeamSize);
		panelDetails.add(lblCodeLines);
		panelDetails.add(lblFirstCommit);
		panelDetails.add(lblContributors);
		panelDetails.add(lblLinesChanged);
		panelDetails.add(lblLastCommit);
		panelDetails.add(lblAuthors);
		panelDetails.add(lblExternalRefs);
		panelDetails.add(lblURL);
		int overviewColumnWidth = lblExternalRefs.getPreferredSize().width
				+ lblExternalRefs.getFontMetrics(lblExternalRefs.getFont()).stringWidth(" 00000");
		panelDetails.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				var insets = panelDetails.getInsets();
				int width = panelDetails.getWidth() - insets.left - insets.right;
				int gap = overviewLayout.getHgap();
				int columns = Math.max(1, Math.min(3, (width + gap) / (overviewColumnWidth + gap)));
				if (columns != overviewLayout.getColumns()) {
					overviewLayout.setColumns(columns);
					panelDetails.revalidate();
				}
			}
		});

		JScrollPane usersJScrollPane = new JScrollPane(jTableUserStats);
		usersJScrollPane.setBorder(new TitledBorder("People (authors / contributors)"));

		jTreeFileType = new JTree(new DefaultMutableTreeNode(""));
		JScrollPane fileTypeJScrollPane = new JScrollPane(jTreeFileType);
		fileTypeJScrollPane.setBorder(new TitledBorder("File types (default branch)"));

		JPanel dataPanel = new JPanel(new GridLayout(2, 1));
		dataPanel.add(usersJScrollPane);
		dataPanel.add(fileTypeJScrollPane);
		JPanel centralPanel = new JPanel(new BorderLayout());
		centralPanel.add(panelDetails, BorderLayout.NORTH);
		centralPanel.add(dataPanel, BorderLayout.CENTER);

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

		// Config button action
		btnConfig.setToolTipText("Edit configuration and repositories");
		btnConfig.addActionListener(event -> showConfigDialog());
		btnAuthorship.setToolTipText("Unify or separate downloaded author identities locally");
		btnAuthorship.addActionListener(event -> discoverAuthorship());

		btnRefresh.setToolTipText("Check and download every configured repository from GitHub");
		btnRefresh.addActionListener(event -> refreshRepositories(null));
		btnRefreshPending
				.setToolTipText("Download only repositories with missing data, failed downloads or pending updates");
		btnRefreshPending.addActionListener(
				event -> refreshRepositories(pendingRefreshTargets(allRepos, remoteUpdates, failedDownloads)));
		updateRefreshActions();

		lblStatus.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		lblStatus.setForeground(new Color(80, 80, 80));

		JPanel bottomPanel = new JPanel(new BorderLayout());
		// Reserve the same height whether an interpretation icon is visible or not.
		bottomPanel.setPreferredSize(new Dimension(0, STATUS_BAR_HEIGHT));
		bottomPanel.setMinimumSize(new Dimension(0, STATUS_BAR_HEIGHT));
		bottomPanel.add(lblStatus, BorderLayout.CENTER);
		bottomPanel.add(lblFooter, BorderLayout.EAST);

		JPanel topButtons = new JPanel();
		topButtons.add(btnConfig);
		topButtons.add(btnAuthorship);
		topButtons.add(btnRefreshPending);
		topButtons.add(btnRefresh);
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(topButtons, BorderLayout.EAST);

		this.setTitle("GitHub Repository Analyzer");
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (closing.isRequested() || closePromptOpen)
					return;
				if (cancelActiveDownload != null && !confirmCancelDownload(true))
					return;
				++checkGeneration;
				if (updateWorker != null)
					updateWorker.cancel(true);
				closing.request();
			}
		});

		this.setLayout(new BorderLayout(0, 0));
		this.add(topPanel, BorderLayout.NORTH);
		this.add(centralPanel, BorderLayout.CENTER);
		this.add(reposPanel, BorderLayout.WEST);
		this.add(bottomPanel, BorderLayout.SOUTH);

		this.setSize(1200, 700);
		DialogSizing.fitWithinScreen(this, null);

		// ToolTipManager personalized settings
		ToolTipManager.sharedInstance().setInitialDelay(300); // appear after 0.3 segundos
		ToolTipManager.sharedInstance().setDismissDelay(20000); // show for 20 segundos
		ToolTipManager.sharedInstance().setReshowDelay(100); // reshow after 0.1 segundos

		this.setVisible(true);
		SwingUtilities.invokeLater(this::checkForUpdates);

		// First run / not configured yet: prompt the user to set things up.
		if (!Configurator.getInstance().isConfigured() && allRepos.stream().noneMatch(RepoStats::isAnalysisComplete)) {
			SwingUtilities.invokeLater(() -> {
				setStatusText("Not configured yet — fill in your GitHub user and token.");
				showConfigDialog();
			});
		}
	}

	private boolean confirmCancelDownload(boolean closeApplication) {
		if (closePromptOpen)
			return false;
		closePromptOpen = true;
		try {
			Object[] choices = { "Keep downloading", closeApplication ? "Cancel download and close" : "Cancel download" };
			Component owner = activeDownloadDialog == null ? this : activeDownloadDialog;
			return DialogSizing.showChoiceDialog(owner,
					"A repository download is in progress.\n"
							+ (closeApplication ? "Closing the application will cancel pending downloads.\n"
									: "Closing this dialog will cancel pending downloads.\n")
							+ "Active requests must stop before closing.\n"
							+ "Repositories already downloaded and saved will be kept.",
					"Download in progress", JOptionPane.WARNING_MESSAGE, choices, choices[0]) == 1;
		} finally {
			closePromptOpen = false;
		}
	}

	private void disposeForExit() {
		++checkGeneration;
		if (updateWorker != null)
			updateWorker.cancel(true);
		dispose();
		exitApplication();
	}

	/** Called only once all active operations have completed their cleanup. */
	void exitApplication() {
		System.exit(0);
	}

	static List<String> pendingRefreshTargets(List<RepoStats> repositories,
			Map<String, GitHubDataLoader.UpdateStatus> updates, java.util.Set<String> failed) {
		return repositories.stream().filter(repo -> {
			GitHubDataLoader.UpdateStatus update = updates.get(repo.getUrl());
			return !repo.isAnalysisComplete() || failed.contains(repo.getUrl())
					|| RepositoryTreeRenderer.hasPendingChanges(update)
					|| update != null && (update.error() != null || update.pendingCommits() < 0);
		}).map(RepoStats::getUrl).distinct().toList();
	}

	private void updateRefreshActions() {
		btnRefreshPending.setEnabled(
				btnRefresh.isEnabled() && !pendingRefreshTargets(allRepos, remoteUpdates, failedDownloads).isEmpty());
	}

	private void installRepositoryRefreshMenu() {
		jTreeRepos.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				showMenu(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				showMenu(event);
			}

			private void showMenu(MouseEvent event) {
				if (!event.isPopupTrigger())
					return;
				javax.swing.tree.TreePath path = jTreeRepos.getPathForLocation(event.getX(), event.getY());
				if (path == null || !(((DefaultMutableTreeNode) path.getLastPathComponent())
						.getUserObject() instanceof RepoStats repo))
					return;
				jTreeRepos.setSelectionPath(path);
				JMenuItem refresh = new JMenuItem("Refresh this repository from GitHub");
				refresh.setEnabled(btnRefresh.isEnabled());
				refresh.addActionListener(action -> refreshRepositories(List.of(repo.getUrl())));
				JPopupMenu menu = new JPopupMenu();
				menu.add(refresh);
				menu.show(jTreeRepos, event.getX(), event.getY());
			}
		});
	}

	private void rememberRefreshOutcome() {
		GitHubDataLoader loader = GitHubDataLoader.getInstance();
		failedDownloads.removeAll(loader.getLastRequestedRepositoryUrls());
		failedDownloads.addAll(loader.getLastFailedRepositoryUrls());
		loader.getLastRequestedRepositoryUrls().forEach(remoteUpdates::remove);
	}

	private void refreshRepositories(List<String> requested) {
		if (!btnRefresh.isEnabled())
			return;
		List<String> scope = requested == null ? null : List.copyOf(requested);
		if (scope != null && scope.isEmpty()) {
			setStatusText("No repositories need downloading.");
			return;
		}
		String selection = selectedRepo;
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		checkGeneration++;
		if (updateWorker != null)
			updateWorker.cancel(true);
		setStatusText(scope == null ? "Updating all repositories from GitHub…"
				: "Updating " + scope.size() + " selected repositories from GitHub…");
		DownloadProgressPanel downloadProgress = new DownloadProgressPanel();
		JDialog progressDialog = new JDialog(this, "Downloading repositories", true);
		activeDownloadDialog = progressDialog;
		progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		progressDialog.setContentPane(downloadProgress);
		progressDialog.setCursor(Cursor.getDefaultCursor());
		progressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (downloadProgress.cancel.isEnabled() && confirmCancelDownload(false))
					downloadProgress.cancel.doClick();
			}
		});
		DownloadWorker worker = new DownloadWorker(btnRefresh, btnConfig, btnAuthorship) {
			private boolean synchronizationStarted;

			@Override
			protected List<RepoStats> download() {
				Configurator.getInstance().reload();
				if (!Configurator.getInstance().isConfigured())
					throw new IllegalStateException("Configure a GitHub user and token before refreshing.");
				GitHubDataLoader loader = GitHubDataLoader.getInstance();
				java.util.function.Consumer<GitHubDataLoader.DownloadProgress> progress = event -> {
					synchronizationStarted = true;
					publish(event);
				};
				return scope == null ? loader.loadData(progress) : loader.refreshRepositories(scope, progress);
			}

			@Override
			protected List<RepoStats> readSavedData() {
				return GitHubDataLoader.getInstance().loadLocalData();
			}

			@Override
			protected void process(List<GitHubDataLoader.DownloadProgress> events) {
				events.forEach(downloadProgress::update);
			}

			@Override
			protected void refreshFinished() {
				downloadProgress.finish();
				progressDialog.dispose();
				cancelActiveDownload = null;
				activeDownloadDialog = null;
				if (closing.isRequested())
					return;
				try {
					List<RepoStats> results = get();
					if (synchronizationStarted)
						rememberRefreshOutcome();
					updateReposJTree(results);
					selectedRepo = null;
					loadRepoStats(null);
					if (selection != null)
						selectRepository(selection);
					if (cancellationRequested()) {
						setStatusText("Download cancelled. Saved repositories are available; use Refresh pending to continue.");
						return;
					}
					GitHubDataLoader loader = GitHubDataLoader.getInstance();
					int expected = loader.getLastRequestedRepositoryUrls().size();
					int confirmed = loader.getLastConfirmedRepositoryCount();
					String message = confirmed + " of " + expected + " requested repositories updated.";
					if (confirmed < expected)
						message += "\nPrevious complete downloads were retained where available. Retry failed repositories individually or with Refresh pending.";
					DialogSizing.showMessageDialog(MainWindow.this, message,
							confirmed < expected ? "Refresh completed with warnings" : "Refresh completed",
							confirmed < expected ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					showRefreshFailedDialog();
				} catch (ExecutionException ex) {
					if (reportCsvExportFailure(ex)) {
						if (synchronizationStarted)
							rememberRefreshOutcome();
					} else {
						showRefreshFailedDialog(errorMessage(ex));
					}
				} finally {
					setCursor(Cursor.getDefaultCursor());
					if (!cancellationRequested())
						checkForUpdates(scope);
				}
			}
		};
		downloadProgress.begin(worker::requestCancellation);
		cancelActiveDownload = () -> downloadProgress.cancel.doClick();
		progressDialog.setSize(820, 320);
		DialogSizing.fitWithinScreen(progressDialog, this);
		worker.execute();
		progressDialog.setVisible(true);
	}

	/**
	 * Include selectable pending repositories without presenting invented or old
	 * metrics.
	 */
	static List<RepoStats> withConfiguredRepositories(List<RepoStats> data, List<RepositoryConfig> configs) {
		return withConfiguredRepositories(data, configs, Map.of());
	}

	static List<RepoStats> withConfiguredRepositories(List<RepoStats> data, List<RepositoryConfig> configs,
			Map<String, Boolean> previousVisibility) {
		Map<String, RepoStats> fresh = new HashMap<>(), known = new HashMap<>();
		if (data != null)
			for (RepoStats repo : data)
				if (repo != null) {
					known.put(repo.getUrl(), repo);
					if (repo.isAnalysisComplete())
						fresh.put(repo.getUrl(), repo);
				}
		List<RepoStats> result = new ArrayList<>();
		for (RepositoryConfig config : configs) {
			RepoStats repo = fresh.get(config.url());
			if (repo == null) {
				repo = new RepoStats();
				repo.setUrl(config.url());
				repo.setName(config.url().substring(config.url().lastIndexOf('/') + 1));
				repo.setGroup(config.group());
				repo.setTeamSize(config.teamSize());
				if (known.containsKey(config.url()))
					repo.setPublic(known.get(config.url()).isPublic());
				else if (previousVisibility.containsKey(config.url()))
					repo.setPublic(previousVisibility.get(config.url()));
			} else if (!java.util.Objects.equals(repo.getTeamSize(), config.teamSize())
					|| !java.util.Objects.equals(repo.getGroup(), config.group())) {
				repo = repo.withSettings(config);
			}
			// These local settings affect presentation and expected share, not
			// downloaded work. Keep complete statistics when they change.
			result.add(repo);
		}
		return result;
	}

	private void updateReposJTree(List<RepoStats> data) {
		Map<String, Boolean> visibility = new HashMap<>();
		allRepos.forEach(repo -> visibility.put(repo.getUrl(), repo.isPublic()));
		repoStatsMap.clear();
		List<RepositoryConfig> configs = GitHubDataLoader.getInstance().getRepositoryConfigs();
		allRepos = withConfiguredRepositories(data, configs, visibility);
		allRepos.forEach(repo -> {
			if (failedDownloads.contains(repo.getUrl()) && visibility.containsKey(repo.getUrl()))
				repo.setPublic(visibility.get(repo.getUrl()));
			repoStatsMap.put(repo.getUrl(), repo);
		});
		remoteUpdates.keySet().retainAll(repoStatsMap.keySet());
		failedDownloads.retainAll(repoStatsMap.keySet());
		rebuildRepoTree();
		updateRefreshActions();
	}

	/** Rebuilds the repository tree with every configured repository. */
	private void rebuildRepoTree() {
		DefaultMutableTreeNode repoRootNode = new DefaultMutableTreeNode(
				String.format("%d Repositories", allRepos.size()));
		allRepos.forEach(repo -> repoRootNode.add(new DefaultMutableTreeNode(repo)));

		jTreeRepos.setModel(new DefaultTreeModel(repoRootNode));
		for (int i = 0; i < jTreeRepos.getRowCount(); i++)
			jTreeRepos.expandRow(i);
	}

	private void initTable() {
		Vector<String> cabecera = new Vector<>(
				Arrays.asList("USERNAME", "<html>JAVA<br>ADDED</html>", "<html>JAVA<br>DELETED</html>",
						"<html>JAVA<br>CHURN</html>", "<html>% JAVA<br>CHURN</html>", "<html>JAVA<br>FILES</html>",
						"<html>JAVA<br>COMMITS</html>", "<html>LAST<br>COMMIT</html>", "<html>FIRST<br>COMMIT</html>"));

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
				case 0:
					return String.class; // username
				case 4:
					return Float.class; // % churn
				case 7:
				case 8:
					return Long.class; // last / first commit (timestamps)
				default:
					return Integer.class; // added, deleted, churn, files, commits
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
				if (selectedRepo == null)
					return super.getToolTipText(e);

				int viewRow = rowAtPoint(e.getPoint());
				if (viewRow < 0)
					return super.getToolTipText(e);

				int modelRow = convertRowIndexToModel(viewRow);

				RepoStats repo = repoStatsMap.get(selectedRepo);
				if (repo == null)
					return super.getToolTipText(e);
				if (modelRow < 0 || modelRow >= repo.getUserStats().size())
					return super.getToolTipText(e);

				UserStats u = repo.getUserStats().get(modelRow);
				return buildInterpretationTooltip(repo, u);
			}
		};

		JTableHeader header = jTableUserStats.getTableHeader();
		header.setPreferredSize(new Dimension(header.getPreferredSize().width, 35));

		// --- Table cell renderer (single source of truth for color + short tooltip)
		// ---
		TableCellRenderer cellRenderer = (table, value, isSelected, hasFocus, row, column) -> {
			JLabel label = new JLabel();
			label.setOpaque(true);

			// 1) Safe value and formatted text
			Object v = (value == null) ? "" : value;
			String text = formatCellValue(v);
			label.putClientProperty("html.disable", Boolean.TRUE);
			label.setText(" " + text);

			// 2) Alignment rules (decided once, no redundant overrides)
			label.setHorizontalAlignment(computeAlignment(v, column));

			// 3) Color coding + short tooltip
			RepoStats repo = (selectedRepo == null) ? null : repoStatsMap.get(selectedRepo);
			Color contributionColor = null;

			if (repo != null && row >= 0) {
				int modelRow = table.convertRowIndexToModel(row);

				if (modelRow >= 0 && modelRow < repo.getUserStats().size()) {
					UserStats user = repo.getUserStats().get(modelRow);
					Interpretation it = interpretationFor(repo, user);

					// Apply the same contribution color to all cells in the row.
					contributionColor = it.badge.color;
					if (column == 0) {
						label.setIcon(it.badge.icon());
						label.setIconTextGap(6);
					}

					// Tooltip for all columns
					String shortTip = it.badge.shortText();
					if (!it.flags.isEmpty()) {
						shortTip += "  |  "
								+ it.flags.stream().map(AlertFlag::shortText).reduce((a, b) -> a + "  " + b).orElse("");
					}
					label.setToolTipText(shortTip);
				}
			}

			// 4) Contribution-aware shading. Selection uses a stronger tint instead
			// of the operating system's generic blue, while retaining readable text.
			if (contributionColor != null) {
				Color background = contributionBackground(table.getBackground(), contributionColor, isSelected);
				label.setBackground(background);
				label.setForeground(contributionForeground(contributionColor, background, isSelected));
			} else {
				label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
				label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
				label.setToolTipText(null);
			}

			return label;
		};

		// Header tooltips
		final String[] headerTooltips = new String[] {
				"GitHub login when available; otherwise derived from commit author info. Icon = main contribution indicator.",
				"Java lines added (sum over non-merge commits).", "Java lines deleted (sum over non-merge commits).",
				"Java churn = added + deleted (sum over non-merge commits).", "User share of repository Java churn.",
				"Distinct .java files modified by the user.", "Non-merge commits that touched at least one .java file.",
				"Last non-merge author date; older downloads without it use the committer date.",
				"First non-merge author date; older downloads without it use the committer date." };

		TableCellRenderer headerRenderer = (table, value, isSelected, hasFocus, row, column) -> {
			String base = (value == null) ? "" : value.toString();

			// Show the active sort direction on the sorted column.
			String arrow = "";
			RowSorter<?> sorter = table.getRowSorter();
			if (sorter != null) {
				for (RowSorter.SortKey key : sorter.getSortKeys()) {
					if (key.getColumn() == table.convertColumnIndexToModel(column)) {
						if (key.getSortOrder() == SortOrder.ASCENDING)
							arrow = "▲"; // ▲
						else if (key.getSortOrder() == SortOrder.DESCENDING)
							arrow = "▼"; // ▼
					}
				}
			}
			if (!arrow.isEmpty()) {
				base = base.endsWith("</html>")
						? base.substring(0, base.length() - "</html>".length()) + " " + arrow + "</html>"
						: base + " " + arrow;
			}

			JLabel result = new JLabel(base);

			if (column == 0)
				result.setHorizontalAlignment(JLabel.LEFT);
			else if (column >= 7)
				result.setHorizontalAlignment(JLabel.CENTER);
			else
				result.setHorizontalAlignment(JLabel.RIGHT);

			result.setFont(table.getFont().deriveFont(Font.BOLD));
			result.setBackground(table.getBackground());
			result.setForeground(table.getForeground());
			result.setOpaque(true);

			if (column >= 0 && column < headerTooltips.length) {
				result.setToolTipText(headerTooltips[column]);
			}
			return result;
		};

		// Keep a small vertical margin around the uniformly scaled 24 px icons.
		jTableUserStats.setRowHeight(ICON_SIZE + 4);
		jTableUserStats.setShowGrid(false);
		jTableUserStats.getTableHeader().setReorderingAllowed(false);
		jTableUserStats.getTableHeader().setResizingAllowed(false);
		jTableUserStats.setAutoCreateRowSorter(true); // click a header to sort
		jTableUserStats.setFillsViewportHeight(true);
		jTableUserStats.getTableHeader().setDefaultRenderer(headerRenderer);

		jTableUserStats.getColumnModel().getColumn(0).setPreferredWidth(260); // username
		jTableUserStats.getColumnModel().getColumn(1).setPreferredWidth(55); // added
		jTableUserStats.getColumnModel().getColumn(2).setPreferredWidth(55); // deleted
		jTableUserStats.getColumnModel().getColumn(3).setPreferredWidth(55); // churn
		jTableUserStats.getColumnModel().getColumn(4).setPreferredWidth(55); // % churn
		jTableUserStats.getColumnModel().getColumn(5).setPreferredWidth(55); // java files
		jTableUserStats.getColumnModel().getColumn(6).setPreferredWidth(55); // java commits
		jTableUserStats.getColumnModel().getColumn(7).setPreferredWidth(90); // last (yyyy-MM-dd)
		jTableUserStats.getColumnModel().getColumn(8).setPreferredWidth(90); // first (yyyy-MM-dd)

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
						showInterpretationStatus(repo.getUserStats().get(modelRow), repo);
					} else {
						setStatusText(" ");
					}
				} else {
					setStatusText(" ");
				}
			}
		});

		jTableUserStats.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				if (lblStatus != null)
					setStatusText(" ");
			}
		});
	}

	private void loadRepoStats(RepoStats repoStats) {
		if (repoStats != null && !repoStats.isAnalysisComplete()) {
			loadRepoStats(null);
			lblTeamSize.setText(repoStats.getTeamSize() == null ? "• Team size: pending"
					: "• Team size: " + repoStats.getTeamSize() + " (declared)");
			lblCommits.setText("• Analysis pending: download this repository");
			setStatusText("No local data. Right-click this repository to download it, or use Refresh pending.");
			return;
		}
		// Precompute interpretations once for this repo; renderers/tooltips reuse them.
		rebuildInterpretationCache(repoStats);

		if (repoStats != null) {
			// Update labels with repository stats
			int observed = ContributionMetrics.activeContributors(repoStats).size();
			int size = ContributionMetrics.effectiveTeamSize(repoStats);
			lblTeamSize
					.setText("• Team size: " + size + (repoStats.getTeamSize() == null ? " (inferred)" : " (declared)")
							+ (repoStats.getTeamSize() != null && size != observed ? " ⚠" : ""));
			lblTeamSize.setToolTipText(repoStats.getTeamSize() != null && size != observed
					? "Declared size differs from observed contributors: " + observed
					: "Expected share uses this size; observed churn share always uses actual team work.");
			lblContributors.setText("• Contributors: " + observed);
			lblAuthors.setText("• Author identities: " + repoStats.getAuthorCount());
			lblAuthors
					.setToolTipText(
							"Original download: "
									+ (repoStats.getDataCapturedAt() > 0
											? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
													.format(new Date(repoStats.getDataCapturedAt()))
											: "unknown"));
			lblCreationDate.setText(
					String.format("• Creation date: %s", dateFormatLong.format(new Date(repoStats.getCreationDate()))));

			if (repoStats.getFirstCommit() == -1) {
				lblFirstCommit.setText("• First commit: -");
			} else {
				lblFirstCommit.setText(String.format("• First commit: %s",
						dateFormatLong.format(new Date(repoStats.getFirstCommit()))));
			}

			if (repoStats.getLastCommit() == -1) {
				lblLastCommit.setText("• Last commit: -");
			} else {
				lblLastCommit.setText(
						String.format("• Last commit: %s", dateFormatLong.format(new Date(repoStats.getLastCommit()))));
			}

			int javaCommitsSum = repoStats.getUserStats().stream().mapToInt(UserStats::getCommits).sum();

			lblCommits.setText(String.format("• Total commits (unique): %d", repoStats.getCommits()));
			lblCommits.setToolTipText(String.format(
					"<html>Unique commits across all branches (deduplicated by SHA): <b>%d</b><br>"
							+ "&nbsp;&nbsp;• Merge commits: %d<br>" + "&nbsp;&nbsp;• Non-merge commits: %d<br>"
							+ "&nbsp;&nbsp;• Java commits (sum of users, non-merge touching .java): %d<br>"
							+ "<i>Per-person Java commits sum to ≤ non-merge commits: they only count commits that changed .java.</i></html>",
					repoStats.getCommits(), repoStats.getMergeCommits(), repoStats.getNonMergeCommits(),
					javaCommitsSum));

			lblCodeLines.setText(String.format("• Java LOC (default branch): %d", repoStats.getCodeLines()));
			lblLinesChanged.setText(String.format("• Java churn (added+deleted): %d", repoStats.getLinesChanged()));
			lblLinesChanged.setToolTipText(
					String.format("Added: %d | Deleted: %d", repoStats.getLinesAdded(), repoStats.getLinesDeleted()));
			lblExternalRefs.setText(
					String.format("• External references (default branch): %d", repoStats.getExternalReferences()));
			lblURL.setText(String.format("<html>• <u><i>%s</i></u></html>", repoStats.getName()));
			lblURL.setForeground(Color.BLUE);
			lblURL.setToolTipText(
					repoStats.getUrl() == null ? "Left-click to open in your browser; right-click to copy the URL."
							: repoStats.getUrl() + "  (left-click to open · right-click to copy)");

			// Clear and populate the user stats table
			tableModelUserStats.setRowCount(0);

			repoStats.getUserStats().forEach(s -> {
				int churn = s.getChurn();
				// Share of the team churn (excluding teacher). NaN for the teacher row (shown
				// as "-").
				float pct = ContributionMetrics.teamShare(repoStats, s);

				String displayName = s.getUsername() == null ? "" : s.getUsername();

				tableModelUserStats.addRow(new Object[] { displayName, s.getAdded(), s.getDeleted(), churn, pct,
						s.getJavaFiles(), s.getCommits(), s.getLastCommit(), s.getFirstCommit() });
			});

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) jTreeFileType.getModel().getRoot();
			root.setUserObject(
					String.format("%d file types (default branch)", repoStats.getFileTypeMap().keySet().size()));
			root.removeAllChildren();
			repoStats.getFileTypeMap()
					.forEach((k, v) -> root.add(new DefaultMutableTreeNode(String.format("%s (%d)", k, v))));
			((DefaultTreeModel) jTreeFileType.getModel()).nodeStructureChanged(root);
			jTreeFileType.updateUI();
		} else {
			lblTeamSize.setText("• Team size:");
			lblTeamSize.setToolTipText(null);
			lblContributors.setText("• Contributors:");
			lblAuthors.setText("• Author identities:");
			lblCreationDate.setText("• Creation date:");
			lblFirstCommit.setText("• First commit:");
			lblLastCommit.setText("• Last commit:");
			lblCommits.setText("• Total commits:");
			lblCodeLines.setText("• Java LOC (default branch):");
			lblLinesChanged.setText("• Java churn (added+deleted):");
			lblExternalRefs.setText("• External references (default branch):");
			lblURL.setText("• URL:");
			lblURL.setForeground(lblCommits.getForeground());
			lblURL.setToolTipText("Left-click to open in your browser; right-click to copy the URL.");

			tableModelUserStats.setRowCount(0);

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) jTreeFileType.getModel().getRoot();
			root.setUserObject("0 file types (default branch)");
			root.removeAllChildren();
			((DefaultTreeModel) jTreeFileType.getModel()).nodeStructureChanged(root);
			jTreeFileType.updateUI();
		}
	}

	/**
	 * Loads a tree icon by file name. Tries the classpath first
	 * ({@code /images/<name>}, which is how the icons are bundled inside the
	 * runnable JAR), then falls back to the {@code resources/images/} folder (handy
	 * when running from the project dir in the IDE). Returns {@code null} if the
	 * icon cannot be found, so callers can skip it.
	 */
	static ImageIcon loadIcon(String fileName) {
		URL url = MainWindow.class.getResource("/images/" + fileName);
		if (url != null) {
			return new ImageIcon(url);
		}
		java.io.File file = new java.io.File("resources/images/" + fileName);
		if (file.isFile()) {
			return new ImageIcon(file.getPath());
		}
		return null;
	}

	/**
	 * Returns a URL accepted by Swing's HTML renderer for an icon. It follows the
	 * same classpath-then-project-folder lookup used by {@link #loadIcon(String)}.
	 */
	private static String iconHtmlSource(String fileName) {
		URL url = MainWindow.class.getResource("/images/" + fileName);
		if (url != null) {
			return url.toExternalForm();
		}
		java.io.File file = new java.io.File("resources/images/" + fileName);
		return file.isFile() ? file.toURI().toString() : null;
	}

	static ImageIcon scaleIcon(ImageIcon icon) {
		return new ImageIcon(icon.getImage().getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
	}

	/**
	 * Opens a URL in the system browser, giving the user visible feedback (a dialog
	 * with the address to copy) when it cannot be done — unsupported platform, no
	 * default browser, malformed URL — instead of failing silently on the console.
	 */
	private void openInBrowser(String uri) {
		if (uri == null || uri.isBlank())
			return;

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

		DialogSizing.showMessageDialog(this,
				"Could not open the link automatically.\n\n" + uri + (problem == null ? "" : "\n\n(" + problem + ")")
						+ "\n\nYou can copy the address and open it manually.",
				"Open link", JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Copies text to the system clipboard and shows a brief status confirmation.
	 */
	private void copyToClipboard(String text) {
		if (text == null || text.isBlank())
			return;
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			setStatusText("Copied: " + text);
		} catch (Exception ex) {
			DialogSizing.showMessageDialog(this, "Could not copy to clipboard:\n" + text, "Copy URL",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private void discoverAuthorship() {
		if (!btnAuthorship.isEnabled())
			return;
		String url = selectedRepo;
		if (url == null) {
			DialogSizing.showMessageDialog(this, "Select a repository first.", "Manage authorship",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		btnRefresh.setEnabled(false);
		btnConfig.setEnabled(false);
		btnAuthorship.setEnabled(false);
		JDialog dialog = new JDialog(this, "Manage authorship — " + url, true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		final AuthorshipConfig[] accepted = { null };
		final AuthorshipStore[] ruleStore = { null };
		AuthorshipDiscoveryPanel loading = new AuthorshipDiscoveryPanel(progress -> {
			Configurator config = Configurator.getInstance();
			config.reload();
			GitHubDataLoader loader = GitHubDataLoader.getInstance();
			loader.reloadRepositories();
			RepositoryConfig repository = loader.getRepositoryConfigs().stream().filter(r -> url.equals(r.url()))
					.findFirst().orElseThrow(() -> new IllegalStateException("Repository is no longer configured."));
			AuthorshipStore store = new AuthorshipStore(config.getAuthorshipDirectory());
			AuthorshipConfig rules = store.load(url);
			progress.accept("Reading the local original download…");
			var source = loader.requireRawData(url);
			return new AuthorshipDiscoveryPanel.Result(
					es.deusto.prog3.githubanalyzer.StatisticsCalculator.authors(source), rules, store,
					repository.teamSize(), source);
		}, result -> {
			if (closing.isRequested()) {
				dialog.dispose();
				return;
			}
			ruleStore[0] = result.store();
			populateAuthorshipDialog(dialog, result, accepted);
		}, failure -> {
			DialogSizing.showMessageDialog(dialog,
					"Could not discover original authors. Configuration was not changed.\n" + failure.getMessage(),
					"Manage authorship", JOptionPane.ERROR_MESSAGE);
			dialog.dispose();
		}, dialog::dispose);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				loading.cancel();
			}
		});
		dialog.add(loading, BorderLayout.CENTER);
		boolean reprocessing = false;
		try {
			DialogSizing.packWithinScreen(dialog, this);
			loading.start();
			dialog.setVisible(true);
			if (accepted[0] != null) {
				List<RepoStats> remaining = allRepos.stream()
						.filter(r -> !url.equals(r.getUrl()) && r.isAnalysisComplete()).toList();
				reprocessAuthorship(url, remaining, accepted[0], ruleStore[0]);
				reprocessing = true;
			}
		} finally {
			loading.cancel();
			if (!reprocessing) {
				updateReposJTree(allRepos);
				selectRepository(url);
				btnRefresh.setEnabled(true);
				btnConfig.setEnabled(true);
				btnAuthorship.setEnabled(true);
			}
		}
	}

	static JScrollPane repositoryTreeScrollPane(JTree tree) {
		JScrollPane scroll = new JScrollPane(tree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setPreferredSize(new Dimension(200, scroll.getPreferredSize().height));
		return scroll;
	}

	/**
	 * Renderer-only suffix changes must invalidate the cached node widths as well
	 * as repaint.
	 */
	static void refreshRepositoryTreeLabels(JTree tree) {
		DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
		int[] indices = java.util.stream.IntStream.range(0, root.getChildCount()).toArray();
		if (indices.length > 0)
			model.nodesChanged(root, indices);
	}

	private void checkForUpdates() {
		checkForUpdates(null);
	}

	private void checkForUpdates(List<String> scope) {
		if (closing.isRequested())
			return;
		final int generation = ++checkGeneration;
		if (updateWorker != null)
			updateWorker.cancel(true);
		if (scope == null)
			remoteUpdates.clear();
		else
			scope.forEach(remoteUpdates::remove);
		refreshRepositoryTreeLabels(jTreeRepos);
		updateRefreshActions();
		if (!Configurator.getInstance().isConfigured()) {
			setStatusText("Local data ready. Configure a token to check GitHub updates.");
			return;
		}
		SwingWorker<Void, Map.Entry<String, GitHubDataLoader.UpdateStatus>> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() {
				GitHubDataLoader.getInstance().checkForUpdates(scope, (url, status) -> {
					if (isCancelled())
						throw new java.util.concurrent.CancellationException();
					publish(Map.entry(url, status));
				});
				return null;
			}

			@Override
			protected void process(List<Map.Entry<String, GitHubDataLoader.UpdateStatus>> updates) {
				if (closing.isRequested() || generation != checkGeneration)
					return;
				updates.forEach(entry -> {
					remoteUpdates.put(entry.getKey(), entry.getValue());
					RepoStats repo = repoStatsMap.get(entry.getKey());
					if (repo != null && entry.getValue().publicRepository() != null)
						repo.setPublic(entry.getValue().publicRepository());
				});
				refreshRepositoryTreeLabels(jTreeRepos);
				updateRefreshActions();
			}

			@Override
			protected void done() {
				if (closing.isRequested() || generation != checkGeneration)
					return;
				try {
					get();
					setStatusText("Update check complete. Orange: pending updates. Red: no data or empty repository.");
				} catch (Exception ex) {
					setStatusText("Could not check updates. Local data remains available.");
				}
			}
		};
		updateWorker = worker;
		worker.execute();
	}

	private void selectRepository(String url) {
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) jTreeRepos.getModel().getRoot();
		for (int index = 0; index < root.getChildCount(); index++) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
			if (node.getUserObject() instanceof RepoStats repo && url.equals(repo.getUrl())) {
				jTreeRepos.setSelectionPath(new javax.swing.tree.TreePath(node.getPath()));
				return;
			}
		}
	}

	private static String errorMessage(Exception ex) {
		return ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
	}

	private void populateAuthorshipDialog(JDialog dialog, AuthorshipDiscoveryPanel.Result result,
			AuthorshipConfig[] accepted) {
		AuthorshipForm form = new AuthorshipForm(result.authors(), result.rules(), result.teamSize(), result.source());
		dialog.getContentPane().removeAll();
		JScrollPane formScroll = new JScrollPane(form);
		formScroll.setBorder(BorderFactory.createEmptyBorder());
		dialog.add(formScroll, BorderLayout.CENTER);
		JButton save = new JButton("Apply and recalculate locally");
		JButton cancel = new JButton("Cancel");
		JPanel actions = new JPanel();
		actions.add(cancel);
		actions.add(save);
		dialog.add(actions, BorderLayout.SOUTH);
		cancel.addActionListener(event -> dialog.dispose());
		save.addActionListener(event -> {
			try {
				javax.swing.JTextArea changes = new javax.swing.JTextArea(form.changeSummary(), 16, 65);
				changes.setEditable(false);
				if (DialogSizing.showConfirmDialog(dialog, new JScrollPane(changes), "Confirm authorship assignments",
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
					return;
				accepted[0] = form.snapshot();
				dialog.dispose();
			} catch (IllegalStateException ex) {
				DialogSizing.showMessageDialog(dialog, ex.getMessage(), "Could not save authorship",
						JOptionPane.ERROR_MESSAGE);
			}
		});
		DialogSizing.packWithinScreen(dialog, this);
		dialog.revalidate();
		dialog.repaint();
	}

	private void reprocessAuthorship(String url, List<RepoStats> remaining, AuthorshipConfig rules,
			AuthorshipStore store) {
		setStatusText("Recalculating locally from the original download…");
		new RefreshWorker(btnRefresh, btnConfig, btnAuthorship) {
			@Override
			protected List<RepoStats> doInBackground() throws Exception {
				RepoStats fresh = GitHubDataLoader.getInstance().recalculateRepository(url, rules);
				store.save(url, rules);
				List<RepoStats> results = withConfiguredRepositories(remaining,
						GitHubDataLoader.getInstance().getRepositoryConfigs()).stream()
						.filter(RepoStats::isAnalysisComplete).filter(repo -> !url.equals(repo.getUrl()))
						.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
				results.add(fresh);
				DataManager.getInstance().storeData(results);
				DataManager.getInstance().storeCSV(results);
				return results;
			}

			@Override
			protected void refreshFinished() {
				if (closing.isRequested())
					return;
				try {
					updateReposJTree(get());
					selectRepository(url);
					setStatusText("Authorship applied locally. Original commits unchanged.");
				} catch (Exception ex) {
					if (ex instanceof InterruptedException)
						Thread.currentThread().interrupt();
					if (!reportCsvExportFailure(ex)) {
						setStatusText("Could not apply authorship. Previous results retained.");
						showRefreshFailedDialog("Local recalculation failed.\n" + errorMessage(ex));
					}
				}
			}
		}.execute();
	}

	/**
	 * Opens a modal dialog to edit the configuration (user, token, teacher) and the
	 * repository list. An unreadable list is left untouched while other settings
	 * remain editable. After saving, recalculates locally. The same files can still
	 * be edited by hand outside the app.
	 */
	private void showConfigDialog() {
		if (!btnConfig.isEnabled())
			return;
		Configurator cfg = Configurator.getInstance();
		ConfigurationForm form = new ConfigurationForm(cfg);

		JDialog dialog = new JDialog(this, "Configuration", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.getContentPane().add(new JScrollPane(form), BorderLayout.CENTER);

		JButton save = new JButton("Save");
		JButton cancel = new JButton("Cancel");
		JPanel buttons = new JPanel();
		buttons.add(save);
		buttons.add(cancel);
		dialog.getContentPane().add(buttons, BorderLayout.SOUTH);

		cancel.addActionListener(event -> dialog.dispose());
		save.addActionListener(event -> {
			final Runnable operation;
			try {
				operation = form.prepareSave();
			} catch (IllegalStateException ex) {
				DialogSizing.showMessageDialog(dialog, ex.getMessage(), "Configuration", JOptionPane.ERROR_MESSAGE);
				return;
			}
			String selection = selectedRepo;
			++checkGeneration;
			if (updateWorker != null)
				updateWorker.cancel(true);
			form.setSaving(true);
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			setStatusText("Saving settings and recalculating locally…");
			new RefreshWorker(btnRefresh, btnConfig, btnAuthorship, save, cancel) {
				private boolean settingsSaved;

				@Override
				protected List<RepoStats> doInBackground() {
					operation.run();
					settingsSaved = true;
					return form.repositoriesAvailable ? GitHubDataLoader.getInstance().loadLocalData() : null;
				}

				@Override
				protected void refreshFinished() {
					if (closing.isRequested())
						return;
					try {
						List<RepoStats> updated = get();
						if (updated != null) {
							updateReposJTree(updated);
							if (selection != null)
								selectRepository(selection);
						}
						dialog.dispose();
						setStatusText("Settings saved. Local statistics updated where available.");
						checkForUpdates();
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						showError(ex);
					} catch (ExecutionException | RuntimeException ex) {
						showError(ex);
					} finally {
						form.setSaving(false);
						dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					}
				}

				private void showError(Exception ex) {
					if (reportCsvExportFailure(ex, dialog)) {
						dialog.dispose();
						checkForUpdates();
						return;
					}
					String message = settingsSaved ? "Settings were saved, but local recalculation failed.\n" : "";
					DialogSizing.showMessageDialog(dialog, message + errorMessage(ex), "Configuration",
							JOptionPane.ERROR_MESSAGE);
					setStatusText("Could not finish updating configuration.");
				}
			}.execute();
		});

		DialogSizing.packWithinScreen(dialog, this);
		dialog.setVisible(true);
	}

	public boolean reportCsvExportFailure(Throwable error) {
		return reportCsvExportFailure(error, this);
	}

	private boolean reportCsvExportFailure(Throwable error, Component owner) {
		return CsvExportFeedback.handle(error, results -> {
			String selection = selectedRepo;
			updateReposJTree(results);
			jTreeRepos.clearSelection();
			selectedRepo = null;
			loadRepoStats(null);
			if (selection != null)
				selectRepository(selection);
		}, (title, message) -> {
			setStatusText("Statistics ready; CSV export failed.");
			DialogSizing.showMessageDialog(owner, message, title, JOptionPane.ERROR_MESSAGE);
		});
	}

	private void showRefreshFailedDialog() {
		DialogSizing.showMessageDialog(this,
				"Refresh failed.\n\nTip: GitHub may throttle requests when refreshing many repositories.\n"
						+ "Try again later. Repositories remain pending until a successful GitHub analysis.",
				"Refresh failed", JOptionPane.WARNING_MESSAGE);
	}

	private void showRefreshFailedDialog(String reason) {
		DialogSizing.showMessageDialog(this,
				reason + "\n\nTry again later. Repositories remain pending until a successful GitHub analysis.",
				"Refresh failed", JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Blends an accent color with a base color without relying on a system look and
	 * feel.
	 */
	private static Color blend(Color base, Color accent, float accentWeight) {
		float baseWeight = 1f - accentWeight;
		return new Color(Math.round(base.getRed() * baseWeight + accent.getRed() * accentWeight),
				Math.round(base.getGreen() * baseWeight + accent.getGreen() * accentWeight),
				Math.round(base.getBlue() * baseWeight + accent.getBlue() * accentWeight));
	}

	/**
	 * Uses a subtle tint for a row and a stronger, still restrained tint when
	 * selected.
	 */
	static Color contributionBackground(Color tableBackground, Color contributionColor, boolean selected) {
		return blend(tableBackground, contributionColor, selected ? 0.22f : 0.08f);
	}

	/**
	 * Keeps the contribution color visible and darkens it slightly on selection.
	 */
	static Color contributionForeground(Color contributionColor, Color background, boolean selected) {
		Color candidate = selected ? blend(contributionColor, Color.BLACK, 0.18f) : contributionColor;
		return contrastRatio(candidate, background) >= 3.0f ? candidate
				: (relativeLuminance(background) > 0.5f ? Color.BLACK : Color.WHITE);
	}

	private static float contrastRatio(Color first, Color second) {
		float firstLuminance = relativeLuminance(first);
		float secondLuminance = relativeLuminance(second);
		return (Math.max(firstLuminance, secondLuminance) + 0.05f)
				/ (Math.min(firstLuminance, secondLuminance) + 0.05f);
	}

	private static float relativeLuminance(Color color) {
		return (0.2126f * color.getRed() + 0.7152f * color.getGreen() + 0.0722f * color.getBlue()) / 255f;
	}

	/**
	 * Cell renderer for the "% churn" column: paints a horizontal bar proportional
	 * to the value (in the person's badge color) with the percentage on top, so the
	 * contribution split is readable at a glance. The teacher row (NaN) shows "-".
	 */
	private class PercentBarRenderer extends JComponent implements TableCellRenderer {
		private static final long serialVersionUID = 1L;

		private float fraction; // 0..1, or NaN
		private String text = "";
		private Color barColor = new Color(0, 0, 0, 0);
		private Color textColor = Color.BLACK;
		private Color background = Color.WHITE;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			setFont(table.getFont());

			fraction = (value instanceof Float f && Float.isFinite(f)) ? f : Float.NaN;
			text = NumberFormats.percentage(fraction);

			Color badge = table.getForeground();
			boolean hasContributionColor = false;
			RepoStats repo = (selectedRepo == null) ? null : repoStatsMap.get(selectedRepo);
			if (repo != null && row >= 0) {
				int modelRow = table.convertRowIndexToModel(row);
				if (modelRow >= 0 && modelRow < repo.getUserStats().size()) {
					badge = interpretationFor(repo, repo.getUserStats().get(modelRow)).badge.color;
					hasContributionColor = true;
				}
			}

			if (hasContributionColor) {
				background = contributionBackground(table.getBackground(), badge, isSelected);
				textColor = contributionForeground(badge, background, isSelected);
				barColor = new Color(badge.getRed(), badge.getGreen(), badge.getBlue(), isSelected ? 95 : 55);
			} else {
				background = isSelected ? table.getSelectionBackground() : table.getBackground();
				textColor = isSelected ? table.getSelectionForeground() : table.getForeground();
				barColor = new Color(0, 0, 0, 0);
			}
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
			int tx = w - fm.stringWidth(text) - 6; // right-aligned like the other numbers
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(text, tx, ty);
		}
	}

	private String formatCellValue(Object v) {
		if (v instanceof Long) {
			long ts = (Long) v;
			return (ts == -1L) ? "-" : dateFormatShort.format(new Date(ts));
		}
		if (v instanceof Float) {
			float f = (Float) v;
			return NumberFormats.percentage(f);
		}
		// Integer and other values
		return String.valueOf(v);
	}

	private int computeAlignment(Object v, int column) {
		if (column == 0)
			return JLabel.LEFT;

		if (v instanceof Long || column == 7 || column == 8)
			return JLabel.CENTER;
		if (v instanceof Float)
			return JLabel.RIGHT;
		if (v instanceof Integer)
			return JLabel.RIGHT;

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

	/**
	 * Returns the cached interpretation for a user, computing it on a cache miss.
	 */
	private Interpretation interpretationFor(RepoStats repo, UserStats u) {
		Interpretation cached = interpretationCache.get(u);
		return (cached != null) ? cached : interpret(repo, u);
	}

	/**
	 * Rebuilds the interpretation cache for the users of the given repo (null
	 * clears it).
	 */
	private void rebuildInterpretationCache(RepoStats repo) {
		interpretationCache.clear();
		if (repo != null && repo.getUserStats() != null) {
			for (UserStats u : repo.getUserStats()) {
				if (u != null)
					interpretationCache.put(u, interpret(repo, u));
			}
		}
	}

	/**
	 * Builds the presentation-only interpretation for one row. The teacher check
	 * comes first because that account is deliberately excluded from the team
	 * denominator used by every other badge and alert calculation.
	 */
	private Interpretation interpret(RepoStats repo, UserStats u) {
		// Defensive defaults
		if (repo == null || u == null)
			return new Interpretation(ContributionBadge.BELOW, List.of());

		// Teacher is always a special case (excluded from expected-share calculations)
		if (isTeacher(u))
			return new Interpretation(ContributionBadge.TEACHER, List.of());
		if (ContributionMetrics.isExcluded(u))
			return new Interpretation(ContributionBadge.EXCLUDED, List.of());

		// Observed shares use actual team work; a declared size overrides only expected
		// share.
		List<UserStats> contributors = realContributorsExcludingTeacher(repo);

		int teamChurn = contributors.stream().mapToInt(UserStats::getChurn).sum();
		int uChurn = userChurn(u);
		int commitsJava = u.getCommits();

		ContributionBadge badge = classifyBadge(uChurn, commitsJava, teamChurn,
				ContributionMetrics.effectiveTeamSize(repo));

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
	 * Pure contribution-badge classification (excluding the TEACHER case, which the
	 * caller decides). Package-visible for unit testing.
	 *
	 * <p>
	 * With {@code expected = 1/n} (n = declared or inferred team size, min 1) and
	 * {@code share = userChurn/teamChurn} where {@code teamChurn} is the total
	 * churn of those same active contributors, the badge is chosen over contiguous
	 * ranges: {@code share < 0.5*expected} → VERY_LOW, {@code < 0.8*expected} →
	 * BELOW, {@code <= 1.2*expected} → BALANCED, otherwise HIGH. As a hard
	 * guardrail, a user with no Java churn or no Java commits is always VERY_LOW.
	 */
	static ContributionBadge classifyBadge(int userChurn, int commitsJava, int teamChurn, int contributorCount) {
		// Hard guardrail: if there is no real Java activity, classify as VERY_LOW
		// (prevents misleading shares when commits/lines are missing or teamChurn is
		// small)
		if (userChurn == 0 || commitsJava == 0) {
			return ContributionBadge.VERY_LOW;
		}

		if (teamChurn <= 0 || userChurn < 0 || commitsJava < 0)
			return ContributionBadge.VERY_LOW;
		// Cross-multiply the exact ratios. The product of two positive ints fits
		// in long; divide the thresholds to avoid overflow even for huge teams.
		long weightedChurn = (long) userChurn * Math.max(1, contributorCount);
		if (weightedChurn < (teamChurn + 1L) / 2)
			return ContributionBadge.VERY_LOW;
		if (weightedChurn < (4L * teamChurn + 4) / 5)
			return ContributionBadge.BELOW;
		if (weightedChurn <= 6L * teamChurn / 5)
			return ContributionBadge.BALANCED;
		return ContributionBadge.HIGH;
	}

	/**
	 * Heuristic AI/paste-like detector: true when the user's churn-per-commit is at
	 * least {@link #AI_PASTE_MULTIPLIER}× the repo average. Package-visible for
	 * unit testing.
	 */
	static boolean isAiPasteLike(int userChurn, int commitsJava, int totalChurn, int totalCommitsJava) {
		if (commitsJava <= 0)
			return false;
		double churnPerCommit = userChurn / (double) commitsJava;
		double avgChurnPerCommit = (totalCommitsJava > 0) ? totalChurn / (double) totalCommitsJava : 0.0;
		return avgChurnPerCommit > 0 && churnPerCommit >= avgChurnPerCommit * AI_PASTE_MULTIPLIER;
	}

	private String buildInterpretationTooltip(RepoStats repo, UserStats u) {
		Interpretation it = interpretationFor(repo, u);

		int observed = ContributionMetrics.activeContributors(repo).size();
		int n = ContributionMetrics.effectiveTeamSize(repo);
		float expected = ContributionMetrics.expectedShare(repo);

		StringBuilder sb = new StringBuilder("<html>");
		sb.append("<b>Interpretation</b><br>");
		sb.append(String.format(
				"Contributors: <b>%d</b> · Team size: <b>%d (%s)</b><br>Expected share: <b>%s</b><br><br>", observed, n,
				repo.getTeamSize() == null ? "inferred" : "declared", NumberFormats.percentage(expected)));
		sb.append("<b>").append(it.badge.shortLabel).append("</b>");
		sb.append(": ").append(it.badge.longLine.replaceFirst("^[^:]*:\\s*", "")).append("<br>");

		for (AlertFlag f : it.flags)
			sb.append(f.htmlLine());

		sb.append("</html>");

		return sb.toString();
	}

	private String statusTextFor(UserStats u, RepoStats repo) {
		if (repo == null || u == null)
			return " ";
		Interpretation it = interpretationFor(repo, u);

		if (it.flags.isEmpty()) {
			return it.badge.shortLabel;
		}

		List<String> parts = new ArrayList<>();
		parts.add(it.badge.shortLabel);
		for (AlertFlag f : it.flags)
			parts.add(f.htmlShortText());
		return "<html>" + String.join("&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;", parts) + "</html>";
	}

	/** Shows a regular status message and clears any contribution badge icon. */
	private void setStatusText(String text) {
		lblStatus.setIcon(null);
		lblStatus.setText(text);
		lblStatus.setToolTipText(text.isBlank() ? null : text);
	}

	/**
	 * Shows the selected user's badge icon together with its textual
	 * interpretation.
	 */
	private void showInterpretationStatus(UserStats user, RepoStats repo) {
		if (user == null || repo == null) {
			setStatusText(" ");
			return;
		}
		Interpretation interpretation = interpretationFor(repo, user);
		lblStatus.setIcon(interpretation.badge.icon());
		lblStatus.setIconTextGap(6);
		String status = statusTextFor(user, repo);
		lblStatus.setText(status);
		lblStatus.setToolTipText(status);
	}
}
