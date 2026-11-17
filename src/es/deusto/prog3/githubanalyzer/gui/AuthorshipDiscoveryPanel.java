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
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.persistence.AuthorshipStore;

/**
 * Visible immediately; slow discovery and configuration reads never run on the
 * EDT.
 */
final class AuthorshipDiscoveryPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	record Result(List<AuthorSummary> authors, AuthorshipConfig rules, AuthorshipStore store, Integer teamSize,
			RepositorySnapshot source) {
		Result(List<AuthorSummary> authors, AuthorshipConfig rules, AuthorshipStore store, Integer teamSize) {
			this(authors, rules, store, teamSize, null);
		}
	}

	@FunctionalInterface
	interface Source {
		Result load(Consumer<String> progress) throws Exception;
	}

	final JLabel status = new JLabel("Loading local author identities…");
	final JButton cancelButton = new JButton("Cancel");
	private final SwingWorker<Result, String> worker;
	private final Runnable onCancelled;
	private boolean active = true;
	private boolean started;

	AuthorshipDiscoveryPanel(Source source, Consumer<Result> onLoaded, Consumer<Throwable> onFailure,
			Runnable onCancelled) {
		super(new BorderLayout(8, 12));
		this.onCancelled = onCancelled;
		setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));
		setPreferredSize(new Dimension(650, 150));
		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		add(status, BorderLayout.NORTH);
		add(progressBar, BorderLayout.CENTER);
		JPanel actions = new JPanel();
		actions.add(cancelButton);
		add(actions, BorderLayout.SOUTH);
		worker = new SwingWorker<>() {
			@Override
			protected Result doInBackground() throws Exception {
				return source.load(message -> {
					if (isCancelled())
						throw new CancellationException();
					publish(message);
				});
			}

			@Override
			protected void process(List<String> messages) {
				if (active && !messages.isEmpty())
					status.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (!active)
					return;
				active = false;
				try {
					onLoaded.accept(get());
				} catch (CancellationException ex) {
					onCancelled.run();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					onFailure.accept(ex);
				} catch (ExecutionException ex) {
					onFailure.accept(ex.getCause());
				} catch (RuntimeException ex) {
					onFailure.accept(ex);
				}
			}
		};
		cancelButton.addActionListener(event -> cancel());
	}

	void start() {
		if (!started && active) {
			started = true;
			worker.execute();
		}
	}

	/**
	 * Closing the window also cancels. A late API response cannot reopen the
	 * editor.
	 */
	void cancel() {
		if (!active)
			return;
		active = false;
		worker.cancel(true);
		onCancelled.run();
	}
}
