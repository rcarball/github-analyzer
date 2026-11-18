// IAG (herramientas: Codex (OpenAI))
package es.deusto.prog3.githubanalyzer.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Keeps the packed size unless it exceeds the current monitor's usable area. */
public final class DialogSizing {

	private DialogSizing() { }

	static void packWithinScreen(JDialog dialog, Component owner) {
		dialog.pack();
		fitWithinScreen(dialog, owner);
	}

	static void fitWithinScreen(Window window, Component owner) {
		Rectangle usable = usableBounds(window, owner);
		Rectangle anchor = usable;
		if (owner != null && owner.isShowing()) {
			var location = owner.getLocationOnScreen();
			anchor = new Rectangle(location.x, location.y, owner.getWidth(), owner.getHeight());
		}
		window.setBounds(fit(window.getSize(), usable, anchor));
	}

	private static Rectangle usableBounds(Window window, Component owner) {
		var configuration = owner == null ? window.getGraphicsConfiguration() : owner.getGraphicsConfiguration();
		if (configuration == null)
			configuration = window.getGraphicsConfiguration();
		// AWT bounds and insets already use logical coordinates for display scaling.
		Rectangle screen = configuration.getBounds();
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
		return new Rectangle(screen.x + insets.left, screen.y + insets.top,
				screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
	}

	public static void showMessageDialog(Component owner, Object message, String title, int messageType) {
		showOption(owner, message, title, JOptionPane.DEFAULT_OPTION, messageType);
	}

	static int showConfirmDialog(Component owner, Object message, String title, int optionType) {
		return showConfirmDialog(owner, message, title, optionType, JOptionPane.QUESTION_MESSAGE);
	}

	static int showConfirmDialog(Component owner, Object message, String title, int optionType, int messageType) {
		return showOption(owner, message, title, optionType, messageType);
	}

	static int showChoiceDialog(Component owner, Object message, String title, int messageType,
			Object[] options, Object initialValue) {
		return showOption(owner, message, title, JOptionPane.DEFAULT_OPTION, messageType, options, initialValue);
	}

	private static int showOption(Component owner, Object message, String title, int optionType, int messageType) {
		return showOption(owner, message, title, optionType, messageType, null, null);
	}

	private static int showOption(Component owner, Object message, String title, int optionType, int messageType,
			Object[] options, Object initialValue) {
		JOptionPane pane = new JOptionPane(message, messageType, optionType, null, options, initialValue);
		JDialog dialog = pane.createDialog(owner, title);
		try {
			Rectangle usable = usableBounds(dialog, owner);
			Dimension preferred = dialog.getSize();
			Rectangle bounded = fit(preferred, usable, usable);
			if (bounded.width < preferred.width || bounded.height < preferred.height) {
				Component content;
				if (message instanceof Component component) {
					content = component;
				} else {
					JTextArea text = new JTextArea(String.valueOf(message));
					text.setEditable(false);
					text.setLineWrap(true);
					text.setWrapStyleWord(true);
					text.setFont(pane.getFont());
					text.setBackground(pane.getBackground());
					content = text;
				}
				JScrollPane scroll = new JScrollPane(content);
				scroll.setPreferredSize(new Dimension(Math.max(1, bounded.width - 100),
						Math.max(1, bounded.height - 140)));
				pane.setMessage(scroll);
			}
			packWithinScreen(dialog, owner);
			dialog.setResizable(true);
			dialog.setVisible(true);
			return optionResult(pane.getValue(), options);
		} finally {
			dialog.dispose();
		}
	}

	static int optionResult(Object selected, Object[] options) {
		if (selected == null || selected == JOptionPane.UNINITIALIZED_VALUE)
			return JOptionPane.CLOSED_OPTION;
		if (options != null) {
			for (int index = 0; index < options.length; index++)
				if (java.util.Objects.equals(options[index], selected))
					return index;
			return JOptionPane.CLOSED_OPTION;
		}
		return selected instanceof Integer value ? value : JOptionPane.CLOSED_OPTION;
	}

	static Rectangle fit(Dimension preferred, Rectangle usable, Rectangle owner) {
		int width = Math.min(preferred.width, Math.max(1, (int) (usable.width * .9)));
		int height = Math.min(preferred.height, Math.max(1, (int) (usable.height * .9)));
		int x = owner.x + (owner.width - width) / 2;
		int y = owner.y + (owner.height - height) / 2;
		x = Math.max(usable.x, Math.min(x, usable.x + usable.width - width));
		y = Math.max(usable.y, Math.min(y, usable.y + usable.height - height));
		return new Rectangle(x, y, width, height);
	}
}
