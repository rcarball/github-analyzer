package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.StatisticsCalculator;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;
import es.deusto.prog3.githubanalyzer.format.NumberFormats;

/** Detached editor: no configuration or results are changed until the dialog saves. */
final class AuthorshipForm extends JPanel {
    private static final long serialVersionUID = 1L;
    private final List<AuthorSummary> authors;
    private final AuthorshipConfig original;
    private final AuthorshipConfig draft;
    private final Integer declaredTeamSize;
    private final RepositorySnapshot source;
    private RepoStats preview = new RepoStats();
    private javax.swing.SwingWorker<List<UserStats>, Void> previewWorker;
    private int previewGeneration;
    final JLabel previewStatus = new JLabel("Local preview · Apply recalculates without connecting to GitHub.");
    final javax.swing.JButton unifyButton = new javax.swing.JButton("Unify selected identities");
    final javax.swing.JButton separateButton = new javax.swing.JButton("Separate selected identities");
    final javax.swing.JButton restoreButton = new javax.swing.JButton("Restore original identities");
    final javax.swing.JButton renameButton = new javax.swing.JButton("Rename person…");
    final javax.swing.JButton roleButton = new javax.swing.JButton("Change participation…");
    final JTable identities;
    final JTable people;
    final JLabel summary = new JLabel();
    private List<String> activePeople = List.of();
    private final AbstractTableModel identityModel;
    private final AbstractTableModel peopleModel;
    private record PersonChoice(String id, String label) {
        @Override public String toString() { return label; }
    }

    AuthorshipForm(List<AuthorSummary> authors, AuthorshipConfig config, Integer teamSize) {
        this(authors, config, teamSize, null);
    }

    AuthorshipForm(RepositorySnapshot source, AuthorshipConfig config, Integer teamSize) {
        this(StatisticsCalculator.authors(source), config, teamSize, source);
    }

    AuthorshipForm(List<AuthorSummary> authors, AuthorshipConfig config, Integer teamSize, RepositorySnapshot source) {
        super(new BorderLayout(8, 8));
        this.source = source;
        this.authors = List.copyOf(authors);
        draft = config.copy();
        declaredTeamSize = teamSize;
        for (AuthorSummary author : authors) draft.discover(author.identity(), ContributionMetrics.defaultRole(author.identity()));
        original = draft.copy();
        identityModel = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            private final String[] columns = {"Original Git name", "Email", "GitHub account", "Java commits", "Person"};
            @Override public int getRowCount() { return AuthorshipForm.this.authors.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int col) { return columns[col]; }
            @Override public Class<?> getColumnClass(int col) { return col == 3 ? Integer.class : col == 4 ? PersonChoice.class : String.class; }
            @Override public boolean isCellEditable(int row, int col) { return col == 4; }
            @Override public Object getValueAt(int row, int col) {
                AuthorSummary author = AuthorshipForm.this.authors.get(row);
                var identity = author.identity();
                Person person = draft.personFor(identity);
                return switch (col) {
                    case 0 -> identity.name();
                    case 1 -> identity.email();
                    case 2 -> identity.login();
                    case 3 -> author.javaCommits();
                    default -> choice(person);
                };
            }
            @Override public void setValueAt(Object value, int row, int col) {
                if (col != 4 || !(value instanceof PersonChoice target)) return;
                if (target.id() == null) separate(row);
                else assign(row, target.id());
            }
        };
        peopleModel = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            @Override public int getRowCount() { return activePeople.size(); }
            @Override public int getColumnCount() { return 6; }
            @Override public String getColumnName(int col) { return new String[] {"Visible name", "Email", "GitHub account", "Participation", "Java commits", "Observed share"}[col]; }
            @Override public Class<?> getColumnClass(int col) { return switch (col) { case 3 -> Role.class; case 4 -> Integer.class; case 5 -> Float.class; default -> String.class; }; }
            @Override public boolean isCellEditable(int row, int col) {
                return col == 0 || col == 3 || ((col == 1 || col == 2) && !metadataOptions(activePeople.get(row), col).isEmpty());
            }
            @Override public Object getValueAt(int row, int col) {
                String id = activePeople.get(row);
                Person person = draft.people().get(id);
                if (col == 0) return person.name();
                if (col == 1) return person.emailFor(groupIdentities(id));
                if (col == 2) return person.loginFor(groupIdentities(id));
                if (col == 3) return person.role();
                UserStats stats = preview.getUserStats().stream().filter(user -> id.equals(user.getPersonId())).findFirst().orElse(null);
                if (col == 4) return stats == null ? null : stats.getCommits();
                return stats == null ? Float.NaN : ContributionMetrics.teamShare(preview, stats);
            }
            @Override public void setValueAt(Object value, int row, int col) {
                Person person = draft.people().get(activePeople.get(row));
                if (col == 0) {
                    String name = Objects.toString(value, "").trim();
                    if (name.isEmpty()) return;
                    draft.putPerson(new Person(person.id(), name, person.role(), person.email(), person.login()));
                } else if (col == 1 || col == 2) {
                    String selected = Objects.toString(value, "");
                    if (!metadataOptions(person.id(), col).contains(selected)) return;
                    draft.putPerson(new Person(person.id(), person.name(), person.role(),
                            col == 1 ? selected : person.email(), col == 2 ? selected : person.login()));
                } else if (col == 3 && value instanceof Role role) {
                    draft.putPerson(new Person(person.id(), person.name(), role, person.email(), person.login()));
                } else return;
                updateView();
            }
        };
        identities = new JTable(identityModel) {
            private static final long serialVersionUID = 1L;
            @Override public boolean editCellAt(int row, int col, java.util.EventObject event) {
                return commitEditor(people) && super.editCellAt(row, col, event);
            }
            @Override public javax.swing.table.TableCellEditor getCellEditor(int row, int col) {
                if (convertColumnIndexToModel(col) != 4) return super.getCellEditor(row, col);
                JComboBox<PersonChoice> targets = new JComboBox<>();
                LinkedHashSet<String> available = new LinkedHashSet<>(activePeople);
                draft.assignments().values().stream().sorted().forEach(available::add);
                for (String id : available) {
                    Person person = draft.people().get(id);
                    PersonChoice option = choice(person);
                    targets.addItem(activePeople.contains(id) ? option : new PersonChoice(id, option.label() + " (not observed)"));
                }
                targets.addItem(new PersonChoice(null, "New person…"));
                PlainTextRenderers.install(targets);
                return new DefaultCellEditor(targets);
            }
        };
        identities.setToolTipText("Edit the Person column to merge identities or create a separate person.");
        int[] widths = {160, 240, 130, 95, 250};
        for (int col = 0; col < widths.length; col++) identities.getColumnModel().getColumn(col).setPreferredWidth(widths[col]);
        people = new JTable(peopleModel) {
            private static final long serialVersionUID = 1L;
            @Override public boolean editCellAt(int row, int col, java.util.EventObject event) {
                return commitEditor(identities) && super.editCellAt(row, col, event);
            }
            @Override public javax.swing.table.TableCellEditor getCellEditor(int row, int col) {
                int modelColumn = convertColumnIndexToModel(col);
                if (modelColumn > 2) return super.getCellEditor(row, col);
                JComboBox<String> options = new JComboBox<>(metadataOptions(activePeople.get(convertRowIndexToModel(row)), modelColumn).toArray(String[]::new));
                PlainTextRenderers.install(options);
                options.setEditable(modelColumn == 0);
                options.setToolTipText(modelColumn == 0 ? "Choose a grouped name or type a custom visible name." : "Choose a non-empty value from this person's grouped identities.");
                return new DefaultCellEditor(options);
            }
        };
        PlainTextRenderers.install(identities);
        PlainTextRenderers.install(people);
        people.setToolTipText("Edit Visible name, Email or GitHub account to choose which grouped values represent the person.");
        int[] personWidths = {190, 230, 140, 170, 95, 110};
        for (int col = 0; col < personWidths.length; col++) people.getColumnModel().getColumn(col).setPreferredWidth(personWidths[col]);
        JComboBox<Role> roles = new JComboBox<>(Role.values());
        roles.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Role role ? roleLabel(role) : value, index, selected, focus);
            }
        });
        people.setDefaultEditor(Role.class, new DefaultCellEditor(roles));
        people.setDefaultRenderer(Role.class, (table, value, selected, focus, row, col) ->
                new javax.swing.table.DefaultTableCellRenderer().getTableCellRendererComponent(table,
                        roleLabel((Role) value), selected, focus, row, col));
        identities.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        identities.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) updateActions(); });
        people.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            int row = people.getSelectedRow();
            if (row >= 0 && row < activePeople.size()) {
                String selected = activePeople.get(row);
                identities.getSelectionModel().setValueIsAdjusting(true);
                identities.clearSelection();
                for (int index = 0; index < authors.size(); index++)
                    if (draft.personFor(authors.get(index).identity()).id().equals(selected)) identities.addRowSelectionInterval(index, index);
                identities.getSelectionModel().setValueIsAdjusting(false);
            }
            updateActions();
        });
        unifyButton.addActionListener(event -> runAction(this::unifySelected));
        separateButton.addActionListener(event -> runAction(this::separateSelected));
        restoreButton.addActionListener(event -> runAction(this::restoreOriginal));
        restoreButton.setToolTipText("Separate original identities and restore their initial participation. Applied only after saving.");
        renameButton.addActionListener(event -> runAction(() -> {
            int row = people.getSelectedRow();
            if (row < 0) return;
            String id = activePeople.get(row);
            snapshot();
            Person person = draft.people().get(id);
            String value = javax.swing.JOptionPane.showInputDialog(this, "Person name:", person.name());
            if (value != null && !value.isBlank()) { draft.putPerson(new Person(id, value, person.role(), person.email(), person.login())); updateView(); }
        }));
        roleButton.addActionListener(event -> runAction(() -> {
            int row = people.getSelectedRow();
            if (row < 0) return;
            String id = activePeople.get(row);
            snapshot();
            Person person = draft.people().get(id);
            Object value = javax.swing.JOptionPane.showInputDialog(this, "Participation:", "Person participation",
                    javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                    java.util.Arrays.stream(Role.values()).map(AuthorshipForm::roleLabel).toArray(), roleLabel(person.role()));
            for (Role role : Role.values()) if (roleLabel(role).equals(value)) {
                draft.putPerson(new Person(id, person.name(), role, person.email(), person.login())); updateView();
            }
        }));
        people.setDefaultRenderer(Float.class, (table, value, selected, focus, row, col) ->
                new javax.swing.table.DefaultTableCellRenderer().getTableCellRendererComponent(table,
                        NumberFormats.percentage(value == null ? Double.NaN : (Float) value),
                        selected, focus, row, col));
        JPanel tables = new JPanel(new GridLayout(2, 1, 8, 8));
        JPanel originalPanel = section("1. Select identities (Ctrl/Cmd or Shift), then unify or separate", identities);
        JPanel identityActions = new JPanel(); identityActions.add(unifyButton); identityActions.add(separateButton); identityActions.add(restoreButton);
        originalPanel.add(identityActions, BorderLayout.SOUTH);
        tables.add(originalPanel);
        JPanel peoplePanel = section("2. Choose a visible name, email and GitHub account; review participation and contribution", people);
        JPanel personActions = new JPanel(); personActions.add(renameButton); personActions.add(roleButton);
        peoplePanel.add(personActions, BorderLayout.SOUTH);
        tables.add(peoplePanel);
        add(summary, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
        add(previewStatus, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1000, 600));
        updateView();
    }

    private static JPanel section(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        panel.add(scroll, BorderLayout.CENTER);
        table.setRowHeight(Math.max(table.getRowHeight(), 24));
        return panel;
    }
    private static String roleLabel(Role role) {
        return switch (role) { case MEMBER -> "Team member · include"; case TEACHER -> "Teacher · exclude"; case EXCLUDED -> "Other account · exclude"; };
    }
    private List<AuthorIdentity> groupIdentities(String personId) {
        return authors.stream().map(AuthorSummary::identity)
                .filter(identity -> draft.personFor(identity).id().equals(personId)).toList();
    }
    private List<String> metadataOptions(String personId, int column) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (column == 0) values.add(draft.people().get(personId).name());
        for (AuthorIdentity identity : groupIdentities(personId)) {
            if (column == 0) { values.add(identity.name()); values.add(identity.displayName()); }
            else values.add(column == 1 ? identity.email() : identity.login());
        }
        values.remove("");
        return List.copyOf(values);
    }
    private PersonChoice choice(Person person) {
        AuthorIdentity reference = authors.stream().map(AuthorSummary::identity)
                .filter(identity -> draft.personFor(identity) != null && draft.personFor(identity).id().equals(person.id()))
                .findFirst().orElseGet(() -> authors.stream().map(AuthorSummary::identity)
                        .filter(identity -> original.personFor(identity) != null && original.personFor(identity).id().equals(person.id()))
                        .findFirst().orElse(null));
        String context = reference == null ? "" : !reference.email().isEmpty() ? reference.email()
                : !reference.login().isEmpty() ? reference.login() : reference.name();
        return new PersonChoice(person.id(), person.name() + (context.isEmpty() || context.equals(person.name()) ? "" : " — " + context));
    }
    void assign(int authorRow, String personId) {
        if (!commitEditor(people)) return;
        draft.assign(authors.get(authorRow).identity().key(), personId);
        updateView();
    }
    void separate(int authorRow) {
        if (!commitEditor(people)) return;
        var identity = authors.get(authorRow).identity();
        Person previous = draft.personFor(identity);
        Person person = new Person(UUID.randomUUID().toString(), identity.displayName(), previous.role());
        draft.putPerson(person);
        draft.assign(identity.key(), person.id());
        updateView();
    }
    void unifySelected() {
        int[] rows = identities.getSelectedRows();
        if (rows.length < 2) return;
        snapshot();
        String target = draft.personFor(authors.get(rows[0]).identity()).id();
        for (int row : rows) draft.assign(authors.get(row).identity().key(), target);
        updateView();
    }
    void separateSelected() {
        int[] rows = identities.getSelectedRows();
        snapshot();
        for (int row : rows) {
            var identity = authors.get(row).identity();
            Person previous = draft.personFor(identity);
            if (authors.stream().filter(a -> draft.personFor(a.identity()).id().equals(previous.id())).count() <= 1) continue;
            Person person = new Person(UUID.randomUUID().toString(), identity.displayName(), previous.role());
            draft.putPerson(person); draft.assign(identity.key(), person.id());
        }
        updateView();
    }
    void restoreOriginal() {
        snapshot();
        List<AuthorIdentity> identities = source == null ? authors.stream().map(AuthorSummary::identity).toList()
                : source.commits().stream().map(RepositorySnapshot.Commit::author).distinct().toList();
        for (AuthorIdentity identity : identities) {
            Person person = new Person("author-" + identity.key(), identity.displayName(), ContributionMetrics.defaultRole(identity));
            draft.putPerson(person); draft.assign(identity.key(), person.id());
        }
        updateView();
    }
    private void updateActions() {
        var selectedPeople = new java.util.HashSet<String>();
        boolean canSeparate = false;
        for (int row : identities.getSelectedRows()) {
            if (row >= authors.size()) continue;
            String id = draft.personFor(authors.get(row).identity()).id();
            selectedPeople.add(id);
            if (authors.stream().filter(a -> draft.personFor(a.identity()).id().equals(id)).count() > 1) canSeparate = true;
        }
        unifyButton.setEnabled(selectedPeople.size() > 1);
        separateButton.setEnabled(canSeparate);
        renameButton.setEnabled(people.getSelectedRow() >= 0);
        roleButton.setEnabled(people.getSelectedRow() >= 0);
    }
    int contributorCount() { return contributors(draft); }
    private int contributors(AuthorshipConfig config) {
        return (int) authors.stream().filter(a -> a.javaCommits() > 0)
                .map(a -> config.personFor(a.identity())).filter(p -> p.role() == Role.MEMBER)
                .map(Person::id).distinct().count();
    }
    private void updateView() {
        LinkedHashSet<String> active = new LinkedHashSet<>();
        for (AuthorSummary author : authors) active.add(draft.personFor(author.identity()).id());
        activePeople = new ArrayList<>(active);
        preview = new RepoStats();
        preview.setTeamSize(declaredTeamSize);
        if (source != null) updatePreview();
        int contributors = contributorCount();
        int size = declaredTeamSize == null ? contributors : declaredTeamSize;
        summary.setText(String.format("<html>Author identities: <b>%d</b> · People: <b>%d</b> · Contributors: <b>%d</b><br>"
                + "Team size: <b>%d (%s)</b> · Expected share: <b>%s</b>%s</html>",
                authors.size(), activePeople.size(), contributors, size, declaredTeamSize == null ? "inferred" : "declared",
                NumberFormats.percentage(size == 0 ? Double.NaN : 1.0 / size),
                declaredTeamSize != null && declaredTeamSize != contributors ? " · Declared size differs from observed contributors" : ""));
        identityModel.fireTableDataChanged();
        peopleModel.fireTableDataChanged();
        updateActions();
    }
    private void runAction(Runnable action) {
        try { action.run(); }
        catch (RuntimeException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not edit authorship", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePreview() {
        int generation = ++previewGeneration;
        if (previewWorker != null) previewWorker.cancel(true);
        AuthorshipConfig rules = draft.copy();
        previewStatus.setText("Calculating local preview…");
        previewWorker = new javax.swing.SwingWorker<>() {
            @Override protected List<UserStats> doInBackground() {
                return StatisticsCalculator.personStats(source.activeCommits(), rules, ContributionMetrics::defaultRole);
            }
            @Override protected void done() {
                if (generation != previewGeneration || isCancelled()) return;
                try {
                    preview.setUserStats(get());
                    previewStatus.setText("Local preview · Apply recalculates without connecting to GitHub.");
                    // Refresh only calculated cells: keep the selection and any
                    // name/contact editor open while the background work finishes.
                    for (int row = 0; row < peopleModel.getRowCount(); row++) {
                        peopleModel.fireTableCellUpdated(row, 4);
                        peopleModel.fireTableCellUpdated(row, 5);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    previewStatus.setText("Local preview interrupted. Apply can recalculate again.");
                } catch (java.util.concurrent.ExecutionException | RuntimeException ex) {
                    previewStatus.setText("Local preview unavailable. Apply can recalculate again.");
                }
            }
        };
        previewWorker.execute();
    }

    @Override public void removeNotify() {
        ++previewGeneration;
        if (previewWorker != null) previewWorker.cancel(true);
        super.removeNotify();
    }

    private static boolean commitEditor(JTable table) {
        return table == null || !table.isEditing() || table.getCellEditor().stopCellEditing();
    }
    AuthorshipConfig snapshot() {
        if (!commitEditor(people) || !commitEditor(identities))
            throw new IllegalStateException("Complete the current table edit before saving.");
        return draft.copy();
    }
    String changeSummary() {
        snapshot();
        StringBuilder result = new StringBuilder();
        for (AuthorSummary author : authors) {
            Person before = original.personFor(author.identity()), after = draft.personFor(author.identity());
            if (!before.id().equals(after.id())) result.append(author.identity().name()).append(" <").append(author.identity().email())
                    .append("> (GitHub: ").append(author.identity().login()).append("): ")
                    .append(choice(before)).append(" [").append(roleLabel(before.role())).append("] → ")
                    .append(choice(after)).append(" [").append(roleLabel(after.role())).append("]\n");
        }
        for (String id : activePeople) {
            Person before = original.people().get(id), after = draft.people().get(id);
            if (before != null && !before.equals(after))
                result.append(before.name()).append(" [").append(roleLabel(before.role())).append("]: ")
                    .append(after.name()).append(" [").append(roleLabel(after.role())).append("]")
                    .append(" · Email: ").append(before.email()).append(" → ").append(after.email())
                    .append(" · GitHub: ").append(before.login()).append(" → ").append(after.login()).append("\n");
        }
        result.append("Contributors: ").append(contributors(original)).append(" → ").append(contributorCount())
                .append("\n\nApply these assignments and recalculate locally from the original download?");
        return result.toString();
    }
}
