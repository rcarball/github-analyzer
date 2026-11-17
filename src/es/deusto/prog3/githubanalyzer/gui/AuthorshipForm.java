package es.deusto.prog3.githubanalyzer.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import es.deusto.prog3.githubanalyzer.StatisticsCalculator;
import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.format.NumberFormats;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;

/** Detached editor: only an accepted snapshot can change saved rules or results. */
final class AuthorshipForm extends JPanel {
    private static final long serialVersionUID = 1L;
    private final List<AuthorSummary> authors;
    private final List<AuthorIdentity> originals;
    private final AuthorshipDraft draft;
    private final Integer declaredTeamSize;
    private final RepositorySnapshot source;
    private RepoStats preview = new RepoStats();
    private SwingWorker<List<UserStats>, Void> previewWorker;
    private int previewGeneration;
    private List<String> activePeople = List.of();
    private List<AuthorSummary> visibleIdentities = List.of();
    private String detailPersonId;
    private boolean updating;
    private AuthorshipConfig previewRules;
    private AuthorshipConfig viewRules;
    private Map<String, Person> viewPeople;
    private Map<String, String> viewAssignments;

    final JLabel previewStatus = new JLabel("Local preview · Apply recalculates without connecting to GitHub.");
    final JLabel summary = new JLabel();
    final JLabel historyStatus = new JLabel("No pending changes");
    final JTable people;
    final JTable identities;
    final JComboBox<String> nameField = new JComboBox<>();
    final JComboBox<String> emailField = new JComboBox<>();
    final JComboBox<String> accountField = new JComboBox<>();
    final JComboBox<Role> participation = new JComboBox<>(Role.values());
    final JComboBox<PersonChoice> mergeTarget = new JComboBox<>();
    final JComboBox<Role> mergeRole = new JComboBox<>(Role.values());
    final JButton unifyButton = new JButton("Confirm combination");
    final JButton separateButton = new JButton("Separate selected identities");
    final JButton moveButton = new JButton("Move to…");
    final JButton restoreButton = new JButton("Restore original identities…");
    final JButton discardButton = new JButton("Restore session start");
    final JButton undoButton = new JButton("Undo");
    final JButton redoButton = new JButton("Redo");
    final JTextField searchField = new JTextField(18);
    private final JLabel detailTitle = literalLabel();
    private final JLabel roleHint = new JLabel();
    private final JLabel identityHint = new JLabel();
    private final JLabel mergeSummary = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel detailCards = new JPanel(cards);
    private final AbstractTableModel peopleModel;
    private final AbstractTableModel identityModel;

    record PersonChoice(String id, String label) {
        @Override public String toString() { return label; }
    }

    AuthorshipForm(List<AuthorSummary> authors, AuthorshipConfig config, Integer teamSize) {
        this(authors, config, teamSize, null);
    }

    AuthorshipForm(RepositorySnapshot source, AuthorshipConfig config, Integer teamSize) {
        this(StatisticsCalculator.authors(source), config, teamSize, source);
    }

    AuthorshipForm(List<AuthorSummary> authors, AuthorshipConfig config, Integer teamSize, RepositorySnapshot source) {
        super(new BorderLayout(12, 12));
        this.authors = List.copyOf(authors);
        this.source = source;
        declaredTeamSize = teamSize;
        LinkedHashSet<AuthorIdentity> discovered = new LinkedHashSet<>();
        if (source != null) source.commits().forEach(commit -> discovered.add(commit.author()));
        authors.forEach(author -> discovered.add(author.identity()));
        originals = List.copyOf(discovered);
        draft = new AuthorshipDraft(config, originals);
        updateRules();
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        peopleModel = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            private final String[] columns = {"Person", "Participation", "Identities", "Java commits", "Observed share"};
            @Override public int getRowCount() { return activePeople.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int col) { return columns[col]; }
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) { case 1 -> Role.class; case 2, 3 -> Integer.class; case 4 -> Float.class; default -> String.class; };
            }
            @Override public Object getValueAt(int row, int col) {
                String id = activePeople.get(row);
                Person person = viewPeople.get(id);
                return switch (col) {
                    case 0 -> person.name();
                    case 1 -> person.role();
                    case 2 -> (int) viewAssignments.values().stream().filter(id::equals).count();
                    case 3 -> authors.stream().filter(author -> id.equals(viewAssignments.get(author.identity().key())))
                            .mapToInt(AuthorSummary::javaCommits).sum();
                    default -> preview.getUserStats().stream().filter(user -> id.equals(user.getPersonId())).findFirst()
                            .map(user -> ContributionMetrics.teamShare(preview, user)).orElse(Float.NaN);
                };
            }
        };
        people = literalTable(peopleModel);
        people.setAutoCreateRowSorter(true);
        people.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        people.setRowHeight(36);
        people.setFillsViewportHeight(true);
        people.setShowGrid(false);
        people.setIntercellSpacing(new Dimension(8, 2));
        people.setPreferredScrollableViewportSize(new Dimension(560, 420));
        int[] widths = {180, 140, 65, 85, 130};
        for (int col = 0; col < widths.length; col++) people.getColumnModel().getColumn(col).setPreferredWidth(widths[col]);
        people.getColumnModel().getColumn(1).setMinWidth(110);
        people.getColumnModel().getColumn(2).setMinWidth(65);
        people.getColumnModel().getColumn(3).setMinWidth(95);
        people.getColumnModel().getColumn(4).setMinWidth(110);
        PlainTextRenderers.install(people);
        people.setDefaultRenderer(Role.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                return super.getTableCellRendererComponent(table, roleLabel((Role) value), selected, focus, row, col);
            }
        });
        people.setDefaultRenderer(Float.class, new ShareRenderer());
        people.getSelectionModel().addListSelectionListener(event -> {
            if (updating || event.getValueIsAdjusting()) return;
            List<String> selected = selectedPeople();
            runAction(() -> {
                boolean changed = commitDetails();
                if (changed) refresh(selected);
                else showDetails();
            });
        });

        identityModel = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            private final String[] columns = {"Original Git name", "Email", "GitHub account", "Java commits"};
            @Override public int getRowCount() { return visibleIdentities.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int col) { return columns[col]; }
            @Override public Class<?> getColumnClass(int col) { return col == 3 ? Integer.class : String.class; }
            @Override public Object getValueAt(int row, int col) {
                AuthorSummary author = visibleIdentities.get(row);
                return switch (col) {
                    case 0 -> author.identity().name(); case 1 -> author.identity().email();
                    case 2 -> author.identity().login(); default -> author.javaCommits();
                };
            }
        };
        identities = literalTable(identityModel);
        PlainTextRenderers.install(identities);
        identities.setAutoCreateRowSorter(true);
        identities.setRowHeight(28);
        identities.setFillsViewportHeight(true);
        identities.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        identities.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] identityWidths = {160, 200, 125, 95};
        for (int col = 0; col < identityWidths.length; col++) identities.getColumnModel().getColumn(col).setPreferredWidth(identityWidths[col]);
        identities.getSelectionModel().addListSelectionListener(event -> updateActions());

        for (JComboBox<String> field : List.of(nameField, emailField, accountField)) {
            PlainTextRenderers.install(field);
            field.addActionListener(event -> {
                if (!updating) runAction(() -> { if (commitDetails()) refresh(selectedPeople()); });
            });
        }
        nameField.setEditable(true);
        ((JTextField) nameField.getEditor().getEditorComponent()).getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updatePending(); }
            @Override public void removeUpdate(DocumentEvent event) { updatePending(); }
            @Override public void changedUpdate(DocumentEvent event) { updatePending(); }
            private void updatePending() { if (!updating) updateActions(); }
        });
        nameField.getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) {
                if (!updating) runAction(() -> { if (commitDetails()) refresh(selectedPeople()); });
            }
        });
        configureRoles(participation);
        configureRoles(mergeRole);
        participation.addActionListener(event -> {
            if (!updating) runAction(() -> { if (commitDetails()) refresh(selectedPeople()); });
        });
        PlainTextRenderers.install(mergeTarget);
        mergeTarget.addActionListener(event -> updateActions());
        mergeRole.addActionListener(event -> updateActions());

        JPanel left = new JPanel(new BorderLayout(6, 8));
        JPanel search = new JPanel(new BorderLayout(8, 0));
        JLabel searchLabel = new JLabel("Find person:");
        searchLabel.setLabelFor(searchField);
        search.add(searchLabel, BorderLayout.WEST);
        search.add(searchField, BorderLayout.CENTER);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(); }
            @Override public void removeUpdate(DocumentEvent event) { filter(); }
            @Override public void changedUpdate(DocumentEvent event) { filter(); }
        });
        left.add(search, BorderLayout.NORTH);
        left.add(scrollPane(people), BorderLayout.CENTER);
        left.add(new JLabel("Select people with Ctrl/Cmd or Shift to combine them."), BorderLayout.SOUTH);
        left.setMinimumSize(new Dimension(360, 250));
        left.setBorder(BorderFactory.createTitledBorder("People"));

        JPanel single = new JPanel(new BorderLayout(8, 12));
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints title = constraints(0, 0);
        title.gridwidth = 2;
        detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        fields.add(detailTitle, title);
        addField(fields, 1, "Participation:", participation);
        GridBagConstraints hint = constraints(0, 2);
        hint.gridwidth = 2;
        fields.add(roleHint, hint);
        addField(fields, 3, "Visible name:", nameField);
        addField(fields, 4, "Email:", emailField);
        addField(fields, 5, "GitHub account:", accountField);
        single.add(fields, BorderLayout.NORTH);
        JPanel identityPanel = new JPanel(new BorderLayout(6, 6));
        identityPanel.setBorder(BorderFactory.createTitledBorder("Original Git identities"));
        identityPanel.add(identityHint, BorderLayout.NORTH);
        identityPanel.add(scrollPane(identities), BorderLayout.CENTER);
        JPanel identityActions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        identityActions.add(moveButton);
        identityActions.add(separateButton);
        identityPanel.add(identityActions, BorderLayout.SOUTH);
        single.add(identityPanel, BorderLayout.CENTER);

        JPanel multiple = new JPanel(new BorderLayout(8, 12));
        JPanel mergeFields = new JPanel(new GridBagLayout());
        GridBagConstraints mergeHint = constraints(0, 0);
        mergeHint.gridwidth = 2;
        mergeFields.add(new JLabel("Review the resulting person before combining."), mergeHint);
        addField(mergeFields, 1, "Destination:", mergeTarget);
        addField(mergeFields, 2, "Participation:", mergeRole);
        GridBagConstraints mergeCounts = constraints(0, 3);
        mergeCounts.gridwidth = 2;
        mergeFields.add(mergeSummary, mergeCounts);
        GridBagConstraints confirm = constraints(0, 4);
        confirm.gridwidth = 2;
        mergeFields.add(unifyButton, confirm);
        multiple.add(mergeFields, BorderLayout.NORTH);
        JLabel empty = new JLabel("Select a person to review their identities and details.", SwingConstants.CENTER);
        detailCards.add(empty, "empty");
        detailCards.add(single, "single");
        detailCards.add(multiple, "multiple");
        detailCards.setMinimumSize(new Dimension(400, 250));
        detailCards.setBorder(BorderFactory.createTitledBorder("Selection details"));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailCards);
        split.setResizeWeight(.52);
        split.setDividerLocation(560);
        split.setBorder(null);
        add(summary, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        JPanel history = new JPanel(new FlowLayout(FlowLayout.LEADING));
        history.add(undoButton); history.add(redoButton); history.add(discardButton); history.add(restoreButton);
        history.add(historyStatus);
        footer.add(history, BorderLayout.NORTH);
        footer.add(previewStatus, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1160, 650));
        undoButton.addActionListener(event -> runAction(this::undo));
        redoButton.addActionListener(event -> runAction(this::redo));
        discardButton.addActionListener(event -> runAction(this::discardSession));
        restoreButton.addActionListener(event -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Remove all saved groupings, names, contact choices and participation overrides for this repository?\n"
                    + "Original identities and default participation will be restored. You can undo this before applying.",
                    "Restore original identities", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
                runAction(this::restoreOriginal);
        });
        unifyButton.addActionListener(event -> runAction(this::unifySelected));
        separateButton.addActionListener(event -> runAction(this::separateSelected));
        moveButton.addActionListener(event -> runAction(this::chooseMoveTarget));
        installHistoryKeys();
        refresh(List.of());
    }

    private static JTable literalTable(AbstractTableModel model) {
        return new JTable(model) {
            private static final long serialVersionUID = 1L;
            @Override public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) return null;
                Object value = getValueAt(row, column);
                // Prefix keeps untrusted metadata from activating Swing HTML tooltips.
                return value == null ? null : "Value: " + (value instanceof Role role ? roleLabel(role) : value);
            }
        };
    }

    private static JScrollPane scrollPane(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        return scroll;
    }

    private static JLabel literalLabel() {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x; constraints.gridy = y;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.weightx = x == 1 ? 1 : 0;
        return constraints;
    }

    private static void addField(JPanel panel, int row, String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        panel.add(label, constraints(0, row));
        panel.add(field, constraints(1, row));
    }

    private static void configureRoles(JComboBox<Role> combo) {
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Role role ? roleLabel(role) : "Choose participation…", index, selected, focus);
            }
        });
    }

    private static String roleLabel(Role role) {
        return switch (role) { case MEMBER -> "Team member"; case TEACHER -> "Teacher"; case EXCLUDED -> "Other account"; };
    }

    private AuthorshipConfig rules() { return viewRules; }

    private void updateRules() {
        viewRules = draft.snapshot();
        viewPeople = viewRules.people();
        viewAssignments = viewRules.assignments();
    }

    private List<String> selectedPeople() {
        List<String> ids = new ArrayList<>();
        for (int row : people.getSelectedRows()) {
            int modelRow = people.convertRowIndexToModel(row);
            if (modelRow < activePeople.size()) ids.add(activePeople.get(modelRow));
        }
        return ids;
    }

    private List<AuthorIdentity> groupIdentities(String id) {
        return originals.stream().filter(identity -> id.equals(viewAssignments.get(identity.key()))).toList();
    }

    private List<String> options(String id, int column) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (column == 0) values.add(viewPeople.get(id).name());
        for (AuthorIdentity identity : groupIdentities(id)) {
            if (column == 0) { values.add(identity.name()); values.add(identity.displayName()); }
            else values.add(column == 1 ? identity.email() : identity.login());
        }
        values.remove("");
        return List.copyOf(values);
    }

    private static void fill(JComboBox<String> field, List<String> options, String selected) {
        field.removeAllItems();
        options.forEach(field::addItem);
        field.setSelectedItem(selected);
        field.setEnabled(field.isEditable() || !options.isEmpty());
    }

    private void showDetails() {
        updating = true;
        try {
            List<String> selected = selectedPeople();
            detailPersonId = null;
            visibleIdentities = List.of();
            if (selected.size() == 1) {
                String id = selected.get(0);
                Person person = viewPeople.get(id);
                List<AuthorIdentity> group = groupIdentities(id);
                detailTitle.setText(person.name());
                detailTitle.setToolTipText("Person: " + person.name());
                fill(nameField, options(id, 0), person.name());
                fill(emailField, options(id, 1), person.emailFor(group));
                fill(accountField, options(id, 2), person.loginFor(group));
                participation.setSelectedItem(person.role());
                roleHint.setText(person.role() == Role.MEMBER ? "Included in the team and expected share." : "Visible in results; excluded from the team share.");
                visibleIdentities = authors.stream().filter(author -> id.equals(viewAssignments.get(author.identity().key()))).toList();
                long additional = viewAssignments.values().stream().filter(id::equals).count() - visibleIdentities.size();
                identityHint.setText(visibleIdentities.size() + " identities in this analysis"
                        + (additional > 0 ? " · " + additional + " additional saved aliases" : ""));
                detailPersonId = id;
                cards.show(detailCards, "single");
            } else if (selected.size() > 1) {
                mergeTarget.removeAllItems();
                for (String id : selected) mergeTarget.addItem(choice(id));
                // Destination is always an explicit choice, independent of table order.
                mergeTarget.setSelectedIndex(-1);
                Set<Role> roles = new LinkedHashSet<>();
                selected.forEach(id -> roles.add(viewPeople.get(id).role()));
                mergeRole.setSelectedItem(roles.size() == 1 ? roles.iterator().next() : null);
                long count = viewAssignments.values().stream().filter(selected::contains).count();
                mergeSummary.setText(selected.size() + " people → 1 person · " + count + " identities (including saved aliases)");
                cards.show(detailCards, "multiple");
            } else cards.show(detailCards, "empty");
            identityModel.fireTableDataChanged();
        } finally { updating = false; }
        updateActions();
    }

    /** Commit typed names before selection, history, restoration or save can replace the detail panel. */
    private boolean commitDetails() {
        if (updating || detailPersonId == null) return false;
        Person before = viewPeople.get(detailPersonId);
        String name = String.valueOf(nameField.getEditor().getItem()).trim();
        if (name.isEmpty()) {
            nameField.getEditor().setItem(before.name());
            throw new IllegalStateException("A visible name cannot be blank.");
        }
        String email = emailField.getSelectedItem() == null ? "" : emailField.getSelectedItem().toString();
        String login = accountField.getSelectedItem() == null ? "" : accountField.getSelectedItem().toString();
        // An untouched automatic contact remains automatic instead of becoming a spurious edit.
        List<AuthorIdentity> group = groupIdentities(before.id());
        if (email.equals(before.emailFor(group))) email = before.email();
        if (login.equals(before.loginFor(group))) login = before.login();
        Person after = new Person(before.id(), name, (Role) participation.getSelectedItem(), email, login);
        if (after.equals(before)) return false;
        draft.updatePerson(after);
        updateRules();
        return true;
    }

    private void refresh(List<String> selection) {
        updateRules();
        updating = true;
        try {
            AuthorshipConfig config = rules();
            activePeople = authors.stream().map(author -> config.personFor(author.identity()).id()).distinct().toList();
            peopleModel.fireTableDataChanged();
            people.clearSelection();
            for (String id : selection) {
                int modelRow = activePeople.indexOf(id);
                int row = modelRow < 0 ? -1 : people.convertRowIndexToView(modelRow);
                if (row >= 0) people.addRowSelectionInterval(row, row);
            }
            int contributors = contributorCount();
            int size = declaredTeamSize == null ? contributors : declaredTeamSize;
            summary.setText(String.format("<html>Git identities: <b>%d</b> · People: <b>%d</b> · Java contributors: <b>%d</b><br>"
                    + "Team size: <b>%d (%s)</b> · Expected share: <b>%s</b>%s</html>",
                    authors.size(), activePeople.size(), contributors, size, declaredTeamSize == null ? "inferred" : "declared",
                    NumberFormats.percentage(size == 0 ? Double.NaN : 1.0 / size),
                    declaredTeamSize != null && declaredTeamSize != contributors ? " · Declared size differs from observed contributors" : ""));
        } finally { updating = false; }
        showDetails();
        updateActions();
        AuthorshipConfig config = rules();
        if (source != null && (previewRules == null || !previewRules.assignments().equals(config.assignments())
                || !previewRules.people().equals(config.people()))) updatePreview(config);
    }

    private void updateActions() {
        if (updating) return;
        boolean pendingName = hasPendingName();
        undoButton.setEnabled(draft.canUndo() || pendingName);
        redoButton.setEnabled(draft.canRedo() && !pendingName);
        undoButton.setToolTipText(pendingName ? "Undo: Edit person" : draft.canUndo() ? "Undo: " + draft.undoLabel() : "Nothing to undo");
        redoButton.setToolTipText(draft.canRedo() ? "Redo: " + draft.redoLabel() : "Nothing to redo");
        discardButton.setEnabled(draft.isDirty() || pendingName);
        historyStatus.setText(draft.isDirty() || pendingName ? "Pending local changes" : "No pending changes");
        unifyButton.setEnabled(selectedPeople().size() > 1 && mergeTarget.getSelectedItem() != null && mergeRole.getSelectedItem() != null);
        moveButton.setEnabled(identities.getSelectedRowCount() > 0);
        boolean grouped = detailPersonId != null && viewAssignments.values().stream().filter(detailPersonId::equals).count() > 1;
        separateButton.setEnabled(grouped && identities.getSelectedRowCount() > 0);
    }

    private PersonChoice choice(String id) {
        Person person = viewPeople.get(id);
        List<AuthorIdentity> group = groupIdentities(id);
        String context = person.emailFor(group);
        if (context.isEmpty()) context = person.loginFor(group);
        return new PersonChoice(id, person.name() + (context.isEmpty() ? "" : " — " + context));
    }

    void unifySelected() {
        List<String> selected = selectedPeople();
        PersonChoice target = (PersonChoice) mergeTarget.getSelectedItem();
        Role role = (Role) mergeRole.getSelectedItem();
        if (target == null || role == null) throw new IllegalStateException("Choose a destination and participation before combining.");
        commitDetails();
        draft.merge(new LinkedHashSet<>(selected), target.id(), role);
        refresh(List.of(target.id()));
    }

    private List<String> selectedIdentityKeys() {
        List<String> keys = new ArrayList<>();
        for (int row : identities.getSelectedRows())
            keys.add(visibleIdentities.get(identities.convertRowIndexToModel(row)).identity().key());
        return keys;
    }

    void separateSelected() {
        List<String> keys = selectedIdentityKeys();
        List<String> selection = selectedPeople();
        commitDetails();
        draft.separate(keys);
        refresh(selection);
    }

    private void chooseMoveTarget() {
        List<PersonChoice> targets = viewAssignments.values().stream().distinct()
                .filter(id -> !id.equals(detailPersonId)).map(this::choice).toList();
        if (targets.isEmpty()) return;
        JComboBox<PersonChoice> choices = new JComboBox<>(targets.toArray(PersonChoice[]::new));
        PlainTextRenderers.install(choices);
        if (JOptionPane.showConfirmDialog(this, choices, "Move selected identities to", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            moveSelectedTo(((PersonChoice) choices.getSelectedItem()).id());
    }

    void moveSelectedTo(String targetId) {
        List<String> keys = selectedIdentityKeys();
        commitDetails();
        draft.move(keys, targetId);
        refresh(List.of(targetId));
    }

    private boolean hasPendingName() {
        return detailPersonId != null && !String.valueOf(nameField.getEditor().getItem()).trim()
                .equals(viewPeople.get(detailPersonId).name());
    }

    private boolean discardInvalidName() {
        if (detailPersonId == null || !String.valueOf(nameField.getEditor().getItem()).isBlank()) return false;
        showDetails();
        return true;
    }

    void undo() {
        if (discardInvalidName()) return;
        commitDetails();
        List<String> selected = selectedPeople();
        draft.undo();
        refresh(selected);
    }

    void redo() { commitDetails(); List<String> selected = selectedPeople(); draft.redo(); refresh(selected); }
    void discardSession() { discardInvalidName(); commitDetails(); draft.discardSession(); refresh(List.of()); }
    void restoreOriginal() { discardInvalidName(); commitDetails(); draft.restoreOriginal(); refresh(List.of()); }

    private void filter() {
        if (updating) return;
        runAction(() -> {
            commitDetails();
            updating = true;
            try {
                String query = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
                @SuppressWarnings("unchecked")
                TableRowSorter<AbstractTableModel> sorter = (TableRowSorter<AbstractTableModel>) people.getRowSorter();
                sorter.setRowFilter(new RowFilter<>() {
                    @Override public boolean include(Entry<? extends AbstractTableModel, ? extends Integer> entry) {
                        String id = activePeople.get(entry.getIdentifier());
                        String text = viewPeople.get(id).name() + " " + groupIdentities(id).toString();
                        return text.toLowerCase(java.util.Locale.ROOT).contains(query);
                    }
                });
            } finally { updating = false; }
            refresh(selectedPeople());
        });
    }

    private void installHistoryKeys() {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bindHistory(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mask), "undo-authorship", this::undo);
        bindHistory(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mask | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "redo-authorship", this::redo);
        bindHistory(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, mask), "redo-authorship-y", this::redo);
    }

    private void bindHistory(KeyStroke key, String name, Runnable action) {
        AbstractAction handler = new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent event) { runAction(action); }
        };
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
        getActionMap().put(name, handler);
        // Text editors must participate in the same operation history.
        JComponent editor = (JComponent) nameField.getEditor().getEditorComponent();
        editor.getInputMap().put(key, name);
        editor.getActionMap().put(name, handler);
    }

    private void runAction(Runnable action) {
        try { action.run(); }
        catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not edit authorship", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int contributors(AuthorshipConfig config) {
        return (int) authors.stream().filter(author -> author.javaCommits() > 0).map(author -> config.personFor(author.identity()))
                .filter(person -> person.role() == Role.MEMBER).map(Person::id).distinct().count();
    }

    int contributorCount() { return contributors(rules()); }

    AuthorshipConfig snapshot() {
        if (commitDetails()) refresh(selectedPeople());
        return rules().copy();
    }

    String changeSummary() {
        AuthorshipConfig after = snapshot();
        AuthorshipConfig before = draft.initial();
        StringBuilder result = new StringBuilder();
        for (AuthorIdentity identity : originals) {
            Person oldPerson = before.personFor(identity), newPerson = after.personFor(identity);
            if (!oldPerson.id().equals(newPerson.id()))
                result.append(identity.name()).append(" <").append(identity.email()).append(">\n  ")
                        .append(oldPerson.name()).append(" → ").append(newPerson.name()).append('\n');
        }
        for (String id : after.assignments().values().stream().distinct().toList()) {
            Person oldPerson = before.people().get(id), newPerson = after.people().get(id);
            if (oldPerson != null && !oldPerson.equals(newPerson))
                result.append("Person: ").append(oldPerson.name()).append(" → ").append(newPerson.name())
                        .append("\n  Participation: ").append(roleLabel(oldPerson.role())).append(" → ").append(roleLabel(newPerson.role()))
                        .append("\n  Email: ").append(oldPerson.email()).append(" → ").append(newPerson.email())
                        .append("\n  GitHub: ").append(oldPerson.login()).append(" → ").append(newPerson.login()).append('\n');
        }
        Set<String> knownKeys = originals.stream().map(AuthorIdentity::key).collect(java.util.stream.Collectors.toSet());
        Map<String, String> oldAssignments = before.assignments(), newAssignments = after.assignments();
        long absentMoved = oldAssignments.entrySet().stream().filter(entry -> !knownKeys.contains(entry.getKey()))
                .filter(entry -> newAssignments.containsKey(entry.getKey()) && !entry.getValue().equals(newAssignments.get(entry.getKey()))).count();
        if (absentMoved > 0) result.append("Assignments changed for absent identities: ").append(absentMoved).append('\n');
        long removed = oldAssignments.keySet().stream().filter(key -> !newAssignments.containsKey(key)).count();
        if (removed > 0) result.append("Saved rules removed for absent identities: ").append(removed).append('\n');
        result.append("Contributors: ").append(contributors(before)).append(" → ").append(contributors(after))
                .append("\n\nApply these assignments and recalculate locally from the original download?");
        return result.toString();
    }

    private void updatePreview(AuthorshipConfig config) {
        previewRules = config.copy();
        int generation = ++previewGeneration;
        if (previewWorker != null) previewWorker.cancel(true);
        preview = new RepoStats();
        preview.setTeamSize(declaredTeamSize);
        previewStatus.setText("Calculating local preview…");
        previewWorker = new SwingWorker<>() {
            @Override protected List<UserStats> doInBackground() {
                return StatisticsCalculator.personStats(source.activeCommits(), config, ContributionMetrics::defaultRole);
            }
            @Override protected void done() {
                if (generation != previewGeneration || isCancelled()) return;
                try {
                    preview.setUserStats(get());
                    previewStatus.setText("Local preview · Apply recalculates without connecting to GitHub.");
                    // Keep selection and pending text intact when calculated values arrive.
                    for (int row = 0; row < peopleModel.getRowCount(); row++) peopleModel.fireTableCellUpdated(row, 4);
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

    private static final class ShareRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private float share;
        private boolean selected;
        ShareRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); setOpaque(false); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, NumberFormats.percentage(value == null ? Double.NaN : (Float) value), selected, focus, row, col);
            share = value instanceof Float number ? number : Float.NaN;
            this.selected = selected;
            setToolTipText("Observed share of team Java churn; excluded accounts have no team share.");
            return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            if (Float.isFinite(share)) {
                graphics.setColor(selected ? new Color(255, 255, 255, 65) : new Color(51, 150, 147, 55));
                graphics.fillRoundRect(3, 5, Math.round((getWidth() - 6) * Math.max(0, Math.min(1, share))), getHeight() - 10, 5, 5);
            }
            super.paintComponent(graphics);
        }
    }
}
