// IAG (herramientas: Codex (OpenAI))
// AI-GENERATED TEST CASES
package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;

class DialogSizingTest {

	@Test void dismissingCloseWarningNeverAcceptsCancellation() {
		Object[] options = { "Keep downloading", "Cancel download and close" };
		assertEquals(0, DialogSizing.optionResult(options[0], options));
		assertEquals(1, DialogSizing.optionResult(options[1], options));
		assertEquals(javax.swing.JOptionPane.CLOSED_OPTION, DialogSizing.optionResult(null, options));
		assertEquals(javax.swing.JOptionPane.CLOSED_OPTION,
				DialogSizing.optionResult(javax.swing.JOptionPane.UNINITIALIZED_VALUE, options));
		assertEquals(javax.swing.JOptionPane.OK_OPTION, DialogSizing.optionResult(javax.swing.JOptionPane.OK_OPTION, null));
	}

	@Test void smallLaptopAndScaledDesktopKeepDialogWithinUsableBounds() {
		for (Rectangle usable : List.of(new Rectangle(0, 0, 1366, 728),
				new Rectangle(0, 0, 911, 472), new Rectangle(-1280, 24, 1280, 696))) {
			for (Dimension normal : List.of(new Dimension(1160, 760), new Dimension(1200, 700),
					new Dimension(820, 320), new Dimension(650, 150))) {
				Rectangle fitted = DialogSizing.fit(normal, usable,
						new Rectangle(usable.x - 100, usable.y - 30, 1200, 700));
				assertTrue(usable.contains(fitted));
				assertTrue(fitted.width <= usable.width * .9);
				assertTrue(fitted.height <= usable.height * .9);
				assertTrue(fitted.width <= normal.width);
				assertTrue(fitted.height <= normal.height);
			}
		}
	}

	@Test void largeMonitorPreservesNormalSizeAndCentersOnOwner() {
		Rectangle owner = new Rectangle(2100, 400, 1200, 700);
		Rectangle fitted = DialogSizing.fit(new Dimension(1160, 760),
				new Rectangle(1920, 0, 3840, 2120), owner);
		assertEquals(new Rectangle(2120, 370, 1160, 760), fitted);
		for (Dimension normal : List.of(new Dimension(1200, 700), new Dimension(820, 320),
				new Dimension(650, 150))) {
			Rectangle large = DialogSizing.fit(normal, new Rectangle(1920, 0, 3840, 2120), owner);
			assertEquals(normal, large.getSize());
		}
	}

	@Test void compactFormKeepsItsControlsReachableByScrolling() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AuthorshipForm form = new AuthorshipForm(List.of(), new AuthorshipConfig(), 4);
			JScrollPane scroll = new JScrollPane(form);
			scroll.setSize(700, 350);
			layout(scroll);
			assertTrue(scroll.getHorizontalScrollBar().isVisible());
			assertTrue(scroll.getVerticalScrollBar().isVisible());
			assertTrue(form.getWidth() >= form.getMinimumSize().width);
			assertTrue(form.getHeight() >= form.getMinimumSize().height);
			scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
			scroll.getHorizontalScrollBar().setValue(scroll.getHorizontalScrollBar().getMaximum());
			assertTrue(scroll.getViewport().getViewPosition().x > 0);
			assertTrue(scroll.getViewport().getViewPosition().y > 0);
			assertEquals(new Dimension(1160, 700), form.getPreferredSize());
		});
	}

	private static void layout(Container parent) {
		parent.doLayout();
		for (Component child : parent.getComponents())
			if (child instanceof Container container)
				layout(container);
	}
}
