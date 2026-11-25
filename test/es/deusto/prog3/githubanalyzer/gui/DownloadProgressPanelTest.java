// IAG (herramientas: Codex (OpenAI))
// AI-GENERATED TEST CASES
package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader.DownloadProgress;

class DownloadProgressPanelTest {

	@Test void cancelDisablesButtonAndKeepsStoppingMessageUntilCleanupFinishes() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var calls = new java.util.concurrent.atomic.AtomicInteger();
			var panel = new DownloadProgressPanel();
			panel.begin(calls::incrementAndGet);
			panel.cancel.doClick();
			panel.cancel.doClick();
			assertEquals(1, calls.get());
			assertFalse(panel.cancel.isEnabled());
			panel.update(new DownloadProgress(1, 2, null, "Late progress"));
			assertTrue(panel.phase.getText().startsWith("Cancelling"));
			assertTrue(panel.activity.isIndeterminate());
			panel.finish();
			panel.begin(calls::incrementAndGet);
			assertTrue(panel.cancel.isEnabled());
			panel.cancel.doClick();
			assertEquals(2, calls.get(), "Do not retain callbacks from a previous download");
			panel.finish();
		});
	}

	@Test void parallelDetailsDoNotLoseRepositoriesOrMoveProgressBackwards() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new DownloadProgressPanel();
			panel.begin(() -> { });
			assertTrue(panel.isVisible());
			assertTrue(panel.activity.isIndeterminate());
			panel.update(new DownloadProgress(1, 3, "course/one", "Complete"));
			panel.update(new DownloadProgress(0, 3, "course/two", "Reading commits"));
			assertEquals(1, panel.repositories.getValue());
			assertTrue(panel.details.getText().contains("course/one — Complete"));
			assertTrue(panel.details.getText().contains("course/two — Reading commits"));
			panel.update(new DownloadProgress(3, 3, null, "Finalizing CSV"));
			assertTrue(panel.activity.isIndeterminate(), "Remain active through final persistence");
			assertEquals("Finalizing CSV", panel.phase.getText());
			panel.finish();
			assertFalse(panel.activity.isIndeterminate());
			assertFalse(panel.isVisible());
			panel.begin(() -> { });
			assertEquals(0, panel.repositories.getValue());
			assertEquals("", panel.details.getText());
		});
	}
    @Test void activityAnimatesWithoutDownloadEventsAndSurvivesUiReload() throws Exception {
        var done = new java.util.concurrent.CountDownLatch(1);
        var moved = new java.util.concurrent.atomic.AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DownloadProgressPanel();
            panel.activity.updateUI();
            assertEquals(javax.swing.plaf.basic.BasicProgressBarUI.class, panel.activity.getUI().getClass());
            panel.begin(() -> { });
            panel.addNotify();
            panel.activity.setSize(240, 16);
            int[] before = pixels(panel.activity);
            var timer = new javax.swing.Timer(180, event -> {
                try {
                    moved.set(!java.util.Arrays.equals(before, pixels(panel.activity)));
                } finally {
                    panel.finish();
                    panel.removeNotify();
                    done.countDown();
                }
            });
            timer.setRepeats(false);
            timer.start();
        });
        assertTrue(done.await(3, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(moved.get(), "The activity segment should move even when no progress events arrive");
    }

    private static int[] pixels(javax.swing.JProgressBar bar) {
        var image = new java.awt.image.BufferedImage(240, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { bar.paint(graphics); } finally { graphics.dispose(); }
        return image.getRGB(0, 0, 240, 16, null, 0, 240);
    }

}
