// IAG (herramientas: Codex (OpenAI))
package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader.DownloadProgress;

/** EDT-only view of repository completion and the latest phase of each download. */
final class DownloadProgressPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	final JButton cancel = new JButton("Cancel download");
	private boolean stopping;
	final JProgressBar repositories = new JProgressBar();
	// Aqua's native indeterminate bar can appear static. Use Swing's moving
	// segment for this activity indicator on every platform, including UI reloads.
	final JProgressBar activity = new JProgressBar() {
		private static final long serialVersionUID = 1L;
		@Override public void updateUI() {
			setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
			setForeground(new java.awt.Color(0, 122, 255));
			setBorder(BorderFactory.createEmptyBorder());
		}
	};
	final JTextArea phase = new JTextArea(2, 0);
	final JTextArea details = new JTextArea(8, 60);
	private final Map<String, String> latest = new LinkedHashMap<>();

	DownloadProgressPanel() {
		super(new BorderLayout(6, 6));
		setBorder(BorderFactory.createTitledBorder("GitHub download"));
		repositories.setStringPainted(true);
		repositories.setToolTipText("Finished attempts, including failures; repositories differ in size.");
		activity.setPreferredSize(new java.awt.Dimension(80, 6));
		add(repositories, BorderLayout.NORTH);
		JPanel actions = new JPanel(new BorderLayout(6, 0));
		JPanel activityTrack = new JPanel(new java.awt.GridBagLayout());
		var trackConstraints = new java.awt.GridBagConstraints();
		trackConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		trackConstraints.weightx = 1;
		activityTrack.add(activity, trackConstraints);
		actions.add(activityTrack, BorderLayout.CENTER);
		actions.add(cancel, BorderLayout.EAST);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		add(new JScrollPane(details), BorderLayout.CENTER);
		phase.setFont(UIManager.getFont("Label.font"));
		phase.setForeground(UIManager.getColor("Label.foreground"));
		phase.setOpaque(false);
		phase.setEditable(false);
		phase.setLineWrap(true);
		phase.setWrapStyleWord(true);
		JPanel footer = new JPanel(new BorderLayout(0, 4));
		footer.add(phase, BorderLayout.NORTH);
		footer.add(actions, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		setVisible(false);
	}

	void begin(Runnable cancelDownload) {
		for (var listener : cancel.getActionListeners())
			cancel.removeActionListener(listener);
		stopping = false;
		cancel.setEnabled(true);
		cancel.addActionListener(event -> {
			stopping = true;
			cancel.setEnabled(false);
			phase.setText("Cancelling… Waiting for active requests to stop. Saved repositories will be kept.");
			cancelDownload.run();
		});
		latest.clear();
		details.setText("");
		repositories.setMaximum(1);
		repositories.setValue(0);
		repositories.setString("Preparing download…");
		phase.setText("First downloads may take several minutes. Completed repositories are saved as they finish.");
		activity.setIndeterminate(true);
		setVisible(true);
	}

	void update(DownloadProgress event) {
		if (!isVisible())
			return;
		repositories.setMaximum(Math.max(1, event.total()));
		// Parallel workers can deliver a detail event with an older completion count.
		repositories.setValue(Math.max(repositories.getValue(), event.completed()));
		repositories.setString(repositories.getValue() + " / " + event.total() + " repository attempts finished");
		if (event.repository() == null) {
			if (!stopping)
				phase.setText(event.message());
		} else {
			latest.put(event.repository(), event.message());
			details.setText(latest.entrySet().stream().map(entry -> entry.getKey() + " — " + entry.getValue())
					.collect(java.util.stream.Collectors.joining("\n")));
		}
	}

	void finish() {
		cancel.setEnabled(false);
		activity.setIndeterminate(false);
		setVisible(false);
	}
}
