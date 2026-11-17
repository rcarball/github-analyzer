package es.deusto.prog3.githubanalyzer.gui;

import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Display untrusted Git metadata literally, including text starting with <html>. */
final class PlainTextRenderers {
    private PlainTextRenderers() { }

    static void install(JTable table) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, renderer);
    }

    static <T> void install(JComboBox<T> choices) {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        choices.setRenderer(renderer);
    }
}
