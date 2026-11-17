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

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/**
 * Repository visibility is independent of analysis and synchronization status.
 */
final class RepositoryTreeRenderer extends DefaultTreeCellRenderer {
	private static final long serialVersionUID = 1L;
	static final Color PENDING = new Color(245, 143, 41);
	static final Color MISSING = new Color(194, 35, 48);
	private final Map<String, UpdateStatus> updates;
	private final Set<String> failedDownloads;
	private final Map<String, ImageIcon> icons = new HashMap<>();

	RepositoryTreeRenderer(Map<String, UpdateStatus> updates, Set<String> failedDownloads) {
		this.updates = updates;
		this.failedDownloads = failedDownloads;
		putClientProperty("html.disable", Boolean.TRUE);
		setBackgroundSelectionColor(new Color(0, 120, 215));
	}

	static boolean hasPendingChanges(UpdateStatus update) {
		return update != null && (update.pendingCommits() > 0 || update.headsChanged());
	}

	static String pendingSuffix(UpdateStatus update) {
		if (update == null)
			return "";
		if (update.pendingCommits() < 0 || update.error() != null)
			return " (?)";
		if (update.pendingCommits() > 0)
			return " (" + update.pendingCommits() + ")";
		return update.headsChanged() ? " (changes pending)" : "";
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean focus) {
		super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
		setToolTipText(null);
		String iconFile = "github.png";
		Object content = ((DefaultMutableTreeNode) value).getUserObject();
		if (content instanceof RepoStats repo) {
			UpdateStatus update = updates.get(repo.getUrl());
			boolean publicRepository = update != null && update.publicRepository() != null ? update.publicRepository()
					: repo.isPublic();
			iconFile = publicRepository ? "public.png" : "private.png";
			String label = repo.getGroup() == null || repo.getGroup().isEmpty() ? repo.getName() : repo.getGroup();
			boolean failed = failedDownloads.contains(repo.getUrl());
			if (!repo.isAnalysisComplete())
				label += " (no local data)";
			else if (repo.getCommits() == 0)
				label += " (empty repository)";
			if (failed)
				label += " (refresh failed)";
			setText(label + pendingSuffix(update));
			Color color = !repo.isAnalysisComplete() ? MISSING
					: hasPendingChanges(update) || failed || update != null && update.error() != null ? PENDING
							: repo.getCommits() == 0 ? MISSING : new Color(54, 130, 127);
			setForeground(selected ? Color.WHITE : color);
			// Prefix prevents untrusted repository names or remote errors from activating
			// HTML.
			setToolTipText("Repository: " + repo.getName()
					+ (update != null && update.error() != null ? " · " + update.error()
							: failed ? " · Last download failed; retry this repository."
									: hasPendingChanges(update) ? " · Remote changes pending."
											: !repo.isAnalysisComplete() ? " · Download required." : ""));
		} else
			setForeground(selected ? Color.WHITE : tree.getForeground());
		setIcon(icons.computeIfAbsent(iconFile, name -> {
			ImageIcon loaded = MainWindow.loadIcon(name);
			return loaded == null ? null : MainWindow.scaleIcon(loaded);
		}));
		return this;
	}
}
