// IAG (herramientas: Codex (OpenAI))
// AI-GENERATED TEST CASES
package es.deusto.prog3.githubanalyzer.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.RawDataStore;

/** Optional real-window regression check. Run only in a fresh isolated working directory. */
public final class DownloadDialogDesktopCheck {
	private static final String SAVED = "https://github.com/fixture/saved";
	private static final String PENDING = "https://github.com/fixture/pending";

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = MainWindow.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static <T> T find(Component component, Class<T> type) {
		if (type.isInstance(component))
			return type.cast(component);
		if (component instanceof Container container)
			for (Component child : container.getComponents()) {
				T found = find(child, type);
				if (found != null)
					return found;
			}
		return null;
	}

	private static void checkInside(Component component, Container content) {
		check(component != null && component.getWidth() > 0 && component.getHeight() > 0,
				"Expected a laid-out component");
		var bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), content);
		check(component.isShowing() && new java.awt.Rectangle(content.getSize()).contains(bounds),
				"Component must fit inside the resized content: " + component.getClass().getSimpleName());
	}

	private static void checkCompactMainWindow(MainWindow window) throws Exception {
		Container content = window.getContentPane();
		check(!(content instanceof JScrollPane), "The main window should not scroll as a whole");
		Component commits = (Component) field(window, "lblCommits");
		Component external = (Component) field(window, "lblExternalRefs");
		check(commits.getParent().getWidth() < 600, "Expected a compact repository overview");
		check(external.getWidth() >= external.getPreferredSize().width, "Overview labels must fit after reflow");
		for (String name : List.of("btnConfig", "btnRefresh", "lblStatus", "lblExternalRefs"))
			checkInside((Component) field(window, name), content);
		for (String name : List.of("jTreeRepos", "jTableUserStats", "jTreeFileType")) {
			Component view = (Component) field(window, name);
			checkInside(SwingUtilities.getAncestorOfClass(JScrollPane.class, view), content);
		}
	}

	private static GHRepository completeRepository() {
		return new GHRepository() {
			@Override public String getName() { return "saved"; }
			@Override public String getFullName() { return "fixture/saved"; }
			@Override public Map<String, GHBranch> getBranches() { return Map.of(); }
			@Override public Date getCreatedAt() { return new Date(1); }
			@Override public Date getPushedAt() { return new Date(1); }
			@Override public int getSize() { return 0; }
			@Override public String getDefaultBranch() { return "main"; }
			@Override public boolean isPrivate() { return false; }
		};
	}

	public static void main(String[] args) {
		int result = 0;
		try {
			runChecks();
			ApplicationExitDesktopCheck.runChecks();
		} catch (Throwable failure) {
			failure.printStackTrace();
			result = 1;
		}
		// A failed graphical check must also return an exit code to the launcher.
		System.exit(result);
	}

	private static void runChecks() throws Exception {
		Path directory = Path.of("").toAbsolutePath();
		String isolated = System.getProperty("githubanalyzer.desktop.fixture.directory");
		check(isolated != null && directory.toRealPath().equals(Path.of(isolated).toRealPath()), "Use an isolated fixture directory");
		check(!Files.exists(directory.resolve("resources")), "Fixture directory must be fresh");
		Files.createDirectories(directory.resolve("resources"));
		Path config = directory.resolve("resources/config.properties");
		Files.writeString(config, "github.user=\ngithub.token=\n");
		Files.writeString(directory.resolve("resources/repositories.txt"), SAVED + "\n" + PENDING + "\n");
		GitHubDataLoader loader = GitHubDataLoader.getInstance();
		loader.reloadRepositories();
		AtomicInteger exits = new AtomicInteger();
		AtomicReference<MainWindow> main = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> {
				RepoStats saved = new RepoStats();
				saved.setUrl(SAVED); saved.setName("saved"); saved.setAnalysisComplete(true);
				main.set(new MainWindow(List.of(saved)) {
					@Override void exitApplication() { exits.incrementAndGet(); }
				});
				MainWindow window = main.get();
				window.setSize(Math.min(760, window.getWidth()), Math.min(430, window.getHeight()));
			});
			// Flush the initial credential-free update check before configuring fixture access.
			SwingUtilities.invokeAndWait(() -> { });
			SwingUtilities.invokeAndWait(() -> {
				try {
					checkCompactMainWindow(main.get());
				} catch (Exception error) {
					throw new AssertionError(error);
				}
			});
			Files.writeString(config, "github.user=fixture\ngithub.token=offline-fixture-only\n");
			Configurator.getInstance().reload();
			for (boolean cancelWithButton : new boolean[] { false, true }) {
				AtomicInteger interrupted = new AtomicInteger();
				CountDownLatch entered = new CountDownLatch(1);
				Class<?> reader = Class.forName(GitHubDataLoader.class.getName() + "$RepositoryReader");
				Object proxy = Proxy.newProxyInstance(reader.getClassLoader(), new Class<?>[] { reader }, (object, method, arguments) -> {
					if (!method.getName().equals("get"))
						throw new AssertionError("Unexpected fixture operation");
					if (SAVED.equals(arguments[0]))
						return completeRepository();
					entered.countDown();
					try { new CountDownLatch(1).await(); }
					catch (InterruptedException ex) {
						interrupted.incrementAndGet();
						Thread.currentThread().interrupt();
						throw new IOException("Interrupted fixture request", ex);
					}
					throw new AssertionError("Expected cancellation");
				});
				Field api = GitHubDataLoader.class.getDeclaredField("apiSource");
				api.setAccessible(true);
				api.set(loader, (Supplier<Object>) () -> proxy);
				RawDataStore originals = new RawDataStore(Configurator.getInstance().getRawDataDirectory());
				AtomicReference<byte[]> savedBytes = new AtomicReference<>();
				AtomicReference<String> answer = new AtomicReference<>();
				AtomicInteger stage = new AtomicInteger();
				SwingUtilities.invokeAndWait(() -> {
					Timer answers = new Timer(30, event -> {
						for (Window window : Window.getWindows()) {
							if (window instanceof JDialog dialog && dialog.isShowing() && "Download in progress".equals(dialog.getTitle())) {
								String choice = answer.getAndSet(null);
								if ("dismiss".equals(choice))
									dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
								else if (choice != null)
									find(dialog, JOptionPane.class).setValue(choice);
							}
						}
					});
					Timer actions = new Timer(50, event -> {
						try {
							JDialog progress = (JDialog) field(main.get(), "activeDownloadDialog");
							if (progress == null || !progress.isShowing() || entered.getCount() != 0 || stage.get() >= 5)
								return;
							DownloadProgressPanel panel = find(progress, DownloadProgressPanel.class);
							if (stage.get() == 0) {
								progress.setSize(Math.min(420, progress.getWidth()), Math.min(230, progress.getHeight()));
								progress.validate();
								check(progress.getContentPane() == panel, "The progress dialog should not scroll as a whole");
								checkInside(panel.cancel, panel);
								checkInside(panel.phase, panel);
								checkInside(SwingUtilities.getAncestorOfClass(JScrollPane.class, panel.details), panel);
							}
							if (!panel.details.getText().contains("Complete — saved locally."))
								return;
							if (savedBytes.get() == null)
								savedBytes.set(Files.readAllBytes(originals.pathFor(SAVED)));
							int step = stage.getAndIncrement();
							if (step == 4 && cancelWithButton) {
								panel.cancel.doClick();
							} else {
								answer.set(step == 0 || step == 2 ? "dismiss" : step == 1 || step == 3 ? "Keep downloading" : "Cancel download");
								Window target = step == 2 || step == 3 ? main.get() : progress;
								target.dispatchEvent(new WindowEvent(target, WindowEvent.WINDOW_CLOSING));
							}
							if (step < 4) {
								check(interrupted.get() == 0, "Dismissing or keeping must not interrupt requests");
								check(panel.cancel.isEnabled() && progress.isShowing(), "Progress must remain active");
								check(!((CloseCoordinator) field(main.get(), "closing")).isRequested(), "Declining warnings must not schedule application exit");
							}
						} catch (Throwable ex) {
							failure.compareAndSet(null, ex);
							try { ((Runnable) field(main.get(), "cancelActiveDownload")).run(); }
							catch (Exception ignored) { }
						}
					});
					Timer timeout = new Timer(15000, event -> {
						failure.compareAndSet(null, new AssertionError("Desktop fixture timed out"));
						try { ((Runnable) field(main.get(), "cancelActiveDownload")).run(); }
						catch (Exception ignored) { }
					});
					timeout.setRepeats(false);
					answers.start(); actions.start(); timeout.start();
					try {
						var refresh = MainWindow.class.getDeclaredMethod("refreshRepositories", List.class);
						refresh.setAccessible(true);
						refresh.invoke(main.get(), new Object[] { null });
						check(failure.get() == null, "No desktop callback failures: " + failure.get());
						check(stage.get() == 5 && interrupted.get() > 0, "Only explicit cancellation interrupts requests");
						check(main.get().isShowing() && exits.get() == 0, "Cancelling progress must not exit the application");
						check(java.util.Arrays.equals(savedBytes.get(), Files.readAllBytes(originals.pathFor(SAVED))), "Completed original must be unchanged");
						check(originals.load(PENDING) == null, "Incomplete repository must not be promoted");
						@SuppressWarnings("unchecked")
						List<RepoStats> displayed = (List<RepoStats>) field(main.get(), "allRepos");
						check(displayed.stream().anyMatch(repo -> SAVED.equals(repo.getUrl()) && repo.isAnalysisComplete()), "Saved repository must be displayed after cancellation");
					} catch (Throwable ex) { failure.compareAndSet(null, ex); }
					finally { answers.stop(); actions.stop(); timeout.stop(); }
				});
				check(failure.get() == null, String.valueOf(failure.get()));
				System.out.println("PASS: main/progress warning X and Keep downloading preserve active requests; "
						+ (cancelWithButton ? "cancel button" : "confirmed progress close") + " keeps saved data and the application open");
			}
		} finally {
			SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
		}
	}
}
