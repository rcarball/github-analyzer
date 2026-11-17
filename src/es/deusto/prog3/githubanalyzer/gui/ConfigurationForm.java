package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import es.deusto.prog3.githubanalyzer.persistence.Configurator;

/** Configuration editor that protects a repository list which could not be read. */
final class ConfigurationForm extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Configurator config;
    final JTextField userField;
    final JPasswordField tokenField;
    final JTextField teacherUserField;
    final JTextField teacherEmailField;
    final JTextArea reposArea;
    final boolean repositoriesAvailable;

    ConfigurationForm(Configurator config) {
        super(new GridBagLayout());
        this.config = config;
        String repositoriesText = "";
        boolean available = true;
        try {
            repositoriesText = config.readRepositoriesText();
        } catch (IllegalStateException ex) {
            available = false;
        }
        repositoriesAvailable = available;

        userField = new JTextField(nzs(config.getGithubUser()), 28);
        tokenField = new JPasswordField(nzs(config.getStoredGithubToken()), 28);
        tokenField.setToolTipText("Optional when GITHUB_TOKEN is set in the environment; the environment value takes precedence and is never saved here.");
        char echo = tokenField.getEchoChar();
        JCheckBox showToken = new JCheckBox("show");
        showToken.addActionListener(event -> tokenField.setEchoChar(showToken.isSelected() ? (char) 0 : echo));
        teacherUserField = new JTextField(nzs(config.getTeacherUser()), 28);
        teacherEmailField = new JTextField(nzs(config.getTeacherEmail()), 28);
        reposArea = new JTextArea(repositoriesText, 8, 40);
        reposArea.setEditable(repositoriesAvailable);
        reposArea.setEnabled(repositoriesAvailable);
        reposArea.setToolTipText(repositoriesAvailable
                ? "One repository URL per line. Format: URL[;GROUP][;TEAM_SIZE]. Comma-separated input is also accepted. Lines starting with # are ignored."
                : "Repository list unavailable. Saving other settings will leave this file unchanged.");

        JPanel tokenPanel = new JPanel(new BorderLayout(4, 0));
        tokenPanel.add(tokenField, BorderLayout.CENTER);
        tokenPanel.add(showToken, BorderLayout.EAST);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; add(new JLabel("GitHub user:"), c);
        c.gridx = 1; add(userField, c);
        c.gridx = 0; c.gridy = 1; add(new JLabel("Token:"), c);
        c.gridx = 1; add(tokenPanel, c);
        c.gridx = 0; c.gridy = 3; add(new JLabel("Teacher user:"), c);
        c.gridx = 1; add(teacherUserField, c);
        c.gridx = 0; c.gridy = 4; add(new JLabel("Teacher email:"), c);
        c.gridx = 1; add(teacherEmailField, c);
        c.gridx = 0; c.gridy = 5; c.anchor = GridBagConstraints.NORTHWEST;
        add(new JLabel("<html>Repositories<br>(one URL/line,<br>optional ;GROUP;SIZE):</html>"), c);
        c.gridx = 1; c.fill = GridBagConstraints.BOTH; c.weightx = 1; c.weighty = 1;
        add(new JScrollPane(reposArea), c);

        if (!repositoriesAvailable) {
            c.gridx = 0; c.gridy = 6; c.gridwidth = 2; c.weighty = 0;
            add(new JLabel("<html>Could not read the repository list. Other settings can still be saved.<br>"
                    + "The list will be left unchanged. Restore the file and reopen Config to edit it.</html>"), c);
        }
    }

    void save() {
        if (repositoriesAvailable) {
            try {
                es.deusto.prog3.githubanalyzer.domain.RepositoryConfig.parseLines(reposArea.getText().lines().toList());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
        config.saveSettings(userField.getText().trim(), new String(tokenField.getPassword()).trim(),
                teacherUserField.getText().trim(), teacherEmailField.getText().trim(),
                repositoriesAvailable ? reposArea.getText() : null);
    }

    private static String nzs(String s) {
        return s == null ? "" : s;
    }
}
