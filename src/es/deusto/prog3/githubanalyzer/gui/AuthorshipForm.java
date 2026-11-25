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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
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

/**
 * Detached editor: only an accepted snapshot can change saved rules or results.
 */
final class AuthorshipForm extends JPanel implements javax.swing.Scrollable {
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
	private Object dragRevision = new Object();
	private List<String> dragSources = List.of();
	private AuthorshipConfig previewRules;
	private AuthorshipConfig viewRules;
	private Map<String, Person> viewPeople;
	private Map<String, String> viewAssignments;

	final JLabel previewStatus = new JLabel("Local preview · Apply recalculates without connecting to GitHub.");
	final JPanel summary = new JPanel(new BorderLayout(8, 8));
	final JLabel identityCount = literalLabel();
	final JLabel peopleCount = literalLabel();
	final JLabel contributorCountLabel = literalLabel();
	final JLabel teamSizeValue = literalLabel();
	final JLabel teamSizeSource = literalLabel();
	final JLabel expectedShareValue = literalLabel();
	final JLabel summaryWarning = literalLabel();
	final JLabel historyStatus = new JLabel("No pending changes");
	final JTable people;
	final JList<String> identities = new JList<>();
	final JComboBox<String> identityField = new JComboBox<>();
	final JButton multipleIdentitiesButton = new JButton("Select multiple…");
	private boolean selectingIdentity;
	final JComboBox<String> nameField = new JComboBox<>();
	final JButton editNameButton = new JButton("Edit name");
	final JComboBox<String> emailField = new JComboBox<>();
	final JComboBox<String> accountField = new JComboBox<>();
	final JComboBox<Role> participation = new JComboBox<>(Role.values());
	final JComboBox<PersonChoice> mergeTarget = new JComboBox<>();
	final JComboBox<Role> mergeRole = new JComboBox<>(Role.values());
	final JButton unifyButton = new JButton("Combine selected…");
	private final JLabel mergeInstructions = new JLabel();
	final JButton cancelCombineButton = new JButton("Cancel combination");
	final JButton separateButton = new JButton("Separate selected identities");
	final JButton moveButton = new JButton("Move to…");
	final JButton restoreButton = new JButton("Restore original identities…");
	final JButton discardButton = new JButton("Restore session start");
	final JButton undoButton = new JButton("Undo");
	final JButton redoButton = new JButton("Redo");
	final JTextField searchField = new JTextField(18);
	final JLabel detailTitle = literalLabel();
	final JLabel contributionHint = literalLabel();
	final JLabel commitHint = literalLabel();
	private Map<String, MainWindow.ContributionBadge> contributionBadges = Map.of();
	private final JLabel roleHint = new JLabel();
	private final JLabel mergeSummary = new JLabel();
	private final JLabel dragHint = literalLabel();
	private final CardLayout cards = new CardLayout();
	private final JPanel detailCards = new JPanel(cards);
	private final AbstractTableModel peopleModel;

	record PersonChoice(String id, String label) {
		@Override
		public String toString() {
			return label;
		}
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
		if (source != null)
			source.commits().forEach(commit -> discovered.add(commit.author()));
		authors.forEach(author -> discovered.add(author.identity()));
		originals = List.copyOf(discovered);
		draft = new AuthorshipDraft(config, originals);
		updateRules();
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		peopleModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			private final String[] columns = { "Person", "Role", "Identities", "Java commits", "Observed share" };

			@Override
			public int getRowCount() {
				return activePeople.size();
			}

			@Override
			public int getColumnCount() {
				return columns.length;
			}

			@Override
			public String getColumnName(int col) {
				return columns[col];
			}

			@Override
			public Class<?> getColumnClass(int col) {
				return switch (col) {
				case 1 -> Role.class;
				case 2, 3 -> Integer.class;
				case 4 -> Float.class;
				default -> String.class;
				};
			}

			@Override
			public Object getValueAt(int row, int col) {
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
		// Compact metadata columns leave the remaining space for names and their
		// badges.
		int[] widths = { 300, 84, 70, 64, 86 };
		int[] minimums = { 180, 76, 64, 56, 76 };
		int[] maximums = { Integer.MAX_VALUE, 96, 76, 76, 100 };
		for (int col = 0; col < widths.length; col++) {
			var column = people.getColumnModel().getColumn(col);
			column.setMinWidth(minimums[col]);
			column.setMaxWidth(maximums[col]);
			column.setPreferredWidth(widths[col]);
		}
		configurePeopleHeader();
		DefaultTableCellRenderer personRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			{
				putClientProperty("html.disable", Boolean.TRUE);
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
					int row, int col) {
				super.getTableCellRendererComponent(table, value instanceof Role role ? roleLabel(role) : value,
						selected, focus, row, col);
				setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
				stylePersonCell(this, table, row, col, selected);
				return this;
			}
		};
		people.setDefaultRenderer(Object.class, personRenderer);
		people.setDefaultRenderer(Number.class, personRenderer);
		people.setDefaultRenderer(Role.class, personRenderer);
		people.setDefaultRenderer(Float.class, new ShareRenderer());
		people.setDropMode(DropMode.ON);
		people.setTransferHandler(
				new PeopleTransferHandler(people, this::dragSelection, () -> dragRevision, this::personAtViewRow,
						this::canPrepareCombination, this::prepareDraggedCombination, this::showDragFeedback));
		if (!java.awt.GraphicsEnvironment.isHeadless())
			people.setDragEnabled(true);
		people.getSelectionModel().addListSelectionListener(event -> {
			if (updating || event.getValueIsAdjusting())
				return;
			List<String> selected = selectedPeople();
			runAction(() -> {
				boolean changed = commitDetails();
				if (changed)
					refresh(selected);
				else
					showDetails();
			});
		});

		identityField.setRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				putClientProperty("html.disable", Boolean.TRUE);
				return super.getListCellRendererComponent(list, value == null
						? identities.getSelectedIndices().length + " identities selected" : value, index, selected, focus);
			}
		});
		identities.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		identities.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			{
				putClientProperty("html.disable", Boolean.TRUE);
			}
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
				label.putClientProperty("html.disable", Boolean.TRUE);
				label.setText(String.valueOf(value));
				return label;
			}
		});
		identityField.addActionListener(event -> {
			if (!updating && !selectingIdentity && identityField.getSelectedIndex() >= 0)
				identities.setSelectedIndex(identityField.getSelectedIndex());
		});
		identities.addListSelectionListener(event -> {
			if (!updating && !event.getValueIsAdjusting()) {
				updateIdentityDetails();
				updateActions();
			}
		});
		multipleIdentitiesButton.addActionListener(event -> {
			JList<String> choices = new JList<>(visibleIdentities.stream()
					.map(AuthorshipForm::identityLabel).toArray(String[]::new));
			choices.setCellRenderer(identities.getCellRenderer());
			choices.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			choices.setSelectedIndices(identities.getSelectedIndices());
			JPanel selection = new JPanel(new BorderLayout(6, 6));
			selection.add(new JLabel("Use Ctrl/Cmd or Shift to select multiple identities."), BorderLayout.NORTH);
			JScrollPane scroll = new JScrollPane(choices);
			scroll.setPreferredSize(new Dimension(360, 180));
			selection.add(scroll, BorderLayout.CENTER);
			if (DialogSizing.showConfirmDialog(this, selection, "Select original identities",
					JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
				identities.setSelectedIndices(choices.getSelectedIndices());
		});

		for (JComboBox<String> field : List.of(nameField, emailField, accountField)) {
			PlainTextRenderers.install(field);
			field.addActionListener(event -> {
				if (!updating)
					runAction(() -> {
						if (commitDetails())
							refresh(selectedPeople());
					});
			});
		}
		nameField.setToolTipText("Choose a grouped name, or use Edit name to type a custom visible name.");
		editNameButton.addActionListener(event -> runAction(() -> {
			if (nameField.isEditable()) {
				commitDetails();
				refresh(selectedPeople());
			} else {
				updating = true;
				try {
					nameField.setEditable(true);
					nameField.getEditor().setItem(viewPeople.get(detailPersonId).name());
					editNameButton.setText("Done");
				} finally {
					updating = false;
				}
				JTextField editor = (JTextField) nameField.getEditor().getEditorComponent();
				editor.requestFocusInWindow();
				editor.selectAll();
			}
		}));
		((JTextField) nameField.getEditor().getEditorComponent()).getDocument()
				.addDocumentListener(new DocumentListener() {
					@Override
					public void insertUpdate(DocumentEvent event) {
						updatePending();
					}

					@Override
					public void removeUpdate(DocumentEvent event) {
						updatePending();
					}

					@Override
					public void changedUpdate(DocumentEvent event) {
						updatePending();
					}

					private void updatePending() {
						if (!updating)
							updateActions();
					}
				});
		nameField.getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent event) {
				if (!updating && event.getOppositeComponent() != editNameButton)
					runAction(() -> {
						if (commitDetails())
							refresh(selectedPeople());
					});
			}
		});
		configureRoles(participation);
		configureRoles(mergeRole);
		participation.addActionListener(event -> {
			if (!updating)
				runAction(() -> {
					if (commitDetails())
						refresh(selectedPeople());
				});
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
			@Override
			public void insertUpdate(DocumentEvent event) {
				filter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				filter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				filter();
			}
		});
		left.add(search, BorderLayout.NORTH);
		left.add(scrollPane(people), BorderLayout.CENTER);
		showDragFeedback(0, null);
		left.add(dragHint, BorderLayout.SOUTH);
		left.setMinimumSize(new Dimension(360, 250));
		left.setBorder(BorderFactory.createTitledBorder("People"));

		JPanel single = new JPanel(new BorderLayout(8, 12));
		JPanel fields = new JPanel(new GridBagLayout());
		GridBagConstraints title = constraints(0, 0);
		title.gridwidth = 2;
		detailTitle.setFont(detailTitle.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		fields.add(detailTitle, title);
		GridBagConstraints contribution = constraints(0, 1);
		contribution.gridwidth = 2;
		JPanel contributionRow = new JPanel(new GridBagLayout());
		contributionRow.add(contributionHint, constraints(0, 0));
		contributionRow.add(commitHint, constraints(1, 0));
		Runnable arrangeContribution = () -> {
			boolean narrow = contributionRow.getWidth() < contributionHint.getPreferredSize().width
					+ commitHint.getPreferredSize().width + 16;
			GridBagConstraints position = constraints(narrow ? 0 : 1, narrow ? 1 : 0);
			position.weightx = 1;
			((GridBagLayout) contributionRow.getLayout()).setConstraints(commitHint, position);
			contributionRow.revalidate();
		};
		contributionRow.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override public void componentResized(java.awt.event.ComponentEvent event) {
				arrangeContribution.run();
			}
		});
		contributionHint.addPropertyChangeListener("text", event -> arrangeContribution.run());
		commitHint.addPropertyChangeListener("text", event -> arrangeContribution.run());
		fields.add(contributionRow, contribution);
		addField(fields, 2, "Role:", participation);
		GridBagConstraints hint = constraints(0, 3);
		hint.gridwidth = 2;
		fields.add(roleHint, hint);
		JPanel nameControls = new JPanel(new BorderLayout(6, 0));
		nameControls.add(nameField, BorderLayout.CENTER);
		nameControls.add(editNameButton, BorderLayout.EAST);
		nameControls.setMinimumSize(new Dimension(0, nameControls.getPreferredSize().height));
		for (JComboBox<String> field : List.of(nameField, emailField, accountField, identityField)) {
			field.setMinimumSize(new Dimension(0, field.getPreferredSize().height));
			field.setPreferredSize(new Dimension(180, field.getPreferredSize().height));
		}
		addField(fields, 4, "Visible name:", nameControls);
		addField(fields, 5, "Email:", emailField);
		addField(fields, 6, "GitHub account:", accountField);
		accountField.setToolTipText("Preferred GitHub account for this person, chosen from their original identities.");
		accountField.setRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				putClientProperty("html.disable", Boolean.TRUE);
				return super.getListCellRendererComponent(list,
						value == null ? "No linked GitHub account" : value, index, selected, focus);
			}
		});
		addField(fields, 7, "Original identity:", identityField);
		JPanel identityPanel = new JPanel(new BorderLayout(6, 6));
		identityPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		JPanel identityActions = new JPanel(new GridLayout(0, 1, 0, 6));
		identityActions.add(multipleIdentitiesButton);
		identityActions.add(moveButton);
		identityActions.add(separateButton);
		identityPanel.add(identityActions, BorderLayout.SOUTH);
		JPanel compactDetails = new IdentityDetailsPanel();
		compactDetails.add(fields, BorderLayout.NORTH);
		compactDetails.add(identityPanel, BorderLayout.CENTER);
		JScrollPane detailScroll = new JScrollPane(compactDetails,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		detailScroll.setBorder(null);
		detailScroll.getViewport().setBackground(compactDetails.getBackground());
		detailScroll.getVerticalScrollBar().setUnitIncrement(16);
		single.add(detailScroll, BorderLayout.CENTER);

		JPanel multiple = new JPanel(new BorderLayout(8, 12));
		JPanel mergeFields = new JPanel(new GridBagLayout());
		GridBagConstraints mergeHint = constraints(0, 0);
		mergeHint.gridwidth = 2;
		mergeFields.add(mergeInstructions, mergeHint);
		addField(mergeFields, 1, "Destination:", mergeTarget);
		addField(mergeFields, 2, "Role:", mergeRole);
		GridBagConstraints mergeCounts = constraints(0, 3);
		mergeCounts.gridwidth = 2;
		mergeFields.add(mergeSummary, mergeCounts);
		GridBagConstraints confirm = constraints(0, 4);
		confirm.gridwidth = 2;
		JPanel combinationActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		combinationActions.add(unifyButton);
		combinationActions.add(cancelCombineButton);
		mergeFields.add(combinationActions, confirm);
		multiple.add(mergeFields, BorderLayout.NORTH);
		JLabel empty = new JLabel("Select a person to review their identities and details.", SwingConstants.CENTER);
		detailCards.add(empty, "empty");
		detailCards.add(single, "single");
		detailCards.add(multiple, "multiple");
		detailCards.setMinimumSize(new Dimension(280, 180));
		detailCards.setBorder(BorderFactory.createTitledBorder("Selection details"));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailCards);
		split.setResizeWeight(.52);
		split.setDividerLocation(560);
		split.setBorder(null);
		configureSummary();
		add(summary, BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(8, 8));
		JPanel history = new JPanel(new FlowLayout(FlowLayout.LEADING));
		history.add(undoButton);
		history.add(redoButton);
		history.add(discardButton);
		history.add(restoreButton);
		history.add(historyStatus);
		footer.add(history, BorderLayout.NORTH);
		footer.add(previewStatus, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		setPreferredSize(new Dimension(1160, 700));
		undoButton.addActionListener(event -> runAction(this::undo));
		redoButton.addActionListener(event -> runAction(this::redo));
		discardButton.addActionListener(event -> runAction(this::discardSession));
		restoreButton.addActionListener(event -> {
			if (DialogSizing.showConfirmDialog(this,
					"Remove all saved groupings, names, contact choices and participation overrides for this repository?\n"
							+ "Original identities and default participation will be restored. You can undo this before applying.",
					"Restore original identities", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
				runAction(this::restoreOriginal);
		});
		unifyButton.addActionListener(event -> runAction(this::requestCombination));
		cancelCombineButton.addActionListener(event -> runAction(() -> {
			List<String> sources = dragSources;
			refresh(sources);
		}));
		separateButton.addActionListener(event -> runAction(this::separateSelected));
		moveButton.addActionListener(event -> runAction(this::chooseMoveTarget));
		installHistoryKeys();
		refresh(List.of());
	}

	private void configurePeopleHeader() {
		var header = people.getTableHeader();
		Font font = people.getFont().deriveFont(Font.BOLD);
		header.setFont(font);
		header.setPreferredSize(new Dimension(header.getPreferredSize().width, Math.max(36, font.getSize() * 2 + 8)));
		var renderer = header.getDefaultRenderer();
		header.setDefaultRenderer((table, value, selected, focus, row, column) -> {
			Component component = renderer.getTableCellRendererComponent(table, value, selected, focus, row, column);
			component.setFont(font);
			if (component instanceof JLabel label) {
				int modelColumn = table.convertColumnIndexToModel(column);
				// Only trusted column titles use HTML; author metadata always remains literal.
				label.setText(modelColumn == 3 ? "<html>Java<br>commits</html>"
						: modelColumn == 4 ? "<html>Observed<br>share</html>" : String.valueOf(value));
				label.setHorizontalAlignment(modelColumn == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
				label.setToolTipText(
						modelColumn == 4 ? "Observed share of team Java churn, excluding Teacher and Other accounts."
								: String.valueOf(value));
			}
			return component;
		});
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
		return Math.max(16, people.getRowHeight());
	}

	@Override
	public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
		return Math.max(16, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 16);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return getParent() != null && getParent().getWidth() >= getMinimumSize().width;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return getParent() != null && getParent().getHeight() >= getMinimumSize().height;
	}

	private void configureSummary() {
		JPanel metrics = new JPanel(new GridLayout(1, 5, 10, 0));
		addSummaryMetric(metrics, "GitHub identities", identityCount, new JLabel("Original author identities"),
				"Original Git author identities from the downloaded GitHub repository; multiple identities can belong to one person.",
				true);
		addSummaryMetric(metrics, "People", peopleCount, new JLabel("All observed authors"),
				"All people with commits in the current analysis, including authors without Java commits.", false);
		addSummaryMetric(metrics, "Contributors", contributorCountLabel, new JLabel("Teammates with Java commits"),
				"Teammates with Java commits used for the inferred team size. Authors without Java commits remain visible in People.",
				false);
		addSummaryMetric(metrics, "Team size", teamSizeValue, teamSizeSource,
				"Declared team size when configured; otherwise inferred from teammates with Java commits.", false);
		addSummaryMetric(metrics, "Expected share", expectedShareValue, new JLabel("Per teammate"),
				"Expected contribution is one divided by the declared or inferred team size.", false);
		summaryWarning.setForeground(RepositoryTreeRenderer.PENDING);
		summary.add(metrics, BorderLayout.CENTER);
		summary.add(summaryWarning, BorderLayout.SOUTH);
	}

	private void addSummaryMetric(JPanel metrics, String title, JLabel value, JLabel detail, String tooltip,
			boolean githubIcon) {
		JPanel metric = new JPanel(new BorderLayout(0, 4));
		Color line = javax.swing.UIManager.getColor("Separator.foreground");
		metric.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(line == null ? Color.LIGHT_GRAY : line),
				BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		JLabel caption = new JLabel(title);
		caption.setFont(caption.getFont().deriveFont(Font.BOLD));
		caption.setPreferredSize(new Dimension(caption.getPreferredSize().width, 24));
		caption.setLabelFor(value);
		if (githubIcon) {
			var icon = MainWindow.loadIcon("github.png");
			if (icon != null)
				caption.setIcon(MainWindow.scaleIcon(icon));
		}
		value.setFont(value.getFont().deriveFont(Font.BOLD, value.getFont().getSize2D() + 6));
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		value.getAccessibleContext().setAccessibleName(title);
		detail.setFont(detail.getFont().deriveFont(Math.max(10f, detail.getFont().getSize2D() - 1)));
		caption.setHorizontalAlignment(SwingConstants.RIGHT);
		detail.setHorizontalAlignment(SwingConstants.RIGHT);
		for (JLabel label : List.of(caption, value, detail))
			label.setToolTipText(tooltip);
		metric.add(caption, BorderLayout.NORTH);
		metric.add(value, BorderLayout.CENTER);
		metric.add(detail, BorderLayout.SOUTH);
		metrics.add(metric);
	}

	private JTable literalTable(AbstractTableModel model) {
		return new JTable(model) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(Graphics graphics) {
				super.paintComponent(graphics);
				if (getTransferHandler() instanceof PeopleTransferHandler handler && handler.hoverRow() >= 0
						&& handler.hoverRow() < getRowCount()) {
					java.awt.Rectangle cell = getCellRect(handler.hoverRow(), 0, true);
					Graphics outline = graphics.create();
					try {
						outline.setColor(getSelectionBackground());
						((java.awt.Graphics2D) outline).setStroke(new java.awt.BasicStroke(2));
						outline.drawRect(1, cell.y + 1, getWidth() - 3, cell.height - 3);
					} finally {
						outline.dispose();
					}
				}
			}

			@Override
			public String getToolTipText(java.awt.event.MouseEvent event) {
				int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
				if (row < 0 || column < 0)
					return null;
				Object value = getValueAt(row, column);
				// Prefix keeps untrusted metadata from activating Swing HTML tooltips.
				String text = value instanceof Role role ? roleLabel(role)
						: value instanceof Float number ? NumberFormats.percentage(number) : String.valueOf(value);
				MainWindow.ContributionBadge badge = this == people
						? badgeFor(activePeople.get(convertRowIndexToModel(row)))
						: null;
				return value == null ? null : (badge == null ? "Value: " : badge.shortText() + " · ") + text;
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
		constraints.gridx = x;
		constraints.gridy = y;
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

			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				return super.getListCellRendererComponent(list,
						value instanceof Role role ? roleLabel(role) : "Choose role…", index, selected, focus);
			}
		});
	}

	private static String roleLabel(Role role) {
		return switch (role) {
		case MEMBER -> "Teammate";
		case TEACHER -> "Teacher";
		case EXCLUDED -> "Other";
		};
	}

	private AuthorshipConfig rules() {
		return viewRules;
	}

	private void updateRules() {
		AuthorshipConfig next = draft.snapshot();
		if (viewRules != null && (!viewAssignments.equals(next.assignments()) || !viewPeople.equals(next.people())))
			dragRevision = new Object();
		viewRules = next;
		viewPeople = viewRules.people();
		viewAssignments = viewRules.assignments();
	}

	private List<String> selectedPeople() {
		List<String> ids = new ArrayList<>();
		for (int row : people.getSelectedRows()) {
			int modelRow = people.convertRowIndexToModel(row);
			if (modelRow < activePeople.size())
				ids.add(activePeople.get(modelRow));
		}
		return ids;
	}

	private String personAtViewRow(int row) {
		if (row < 0 || row >= people.getRowCount())
			return null;
		return activePeople.get(people.convertRowIndexToModel(row));
	}

	private List<String> dragSelection() {
		List<String> selected = selectedPeople();
		if (commitDetails())
			refresh(selected);
		return selectedPeople();
	}

	private boolean canPrepareCombination(List<String> sources, String target) {
		if (hasPendingName() || sources.isEmpty() || sources.contains(target) || !activePeople.contains(target))
			return false;
		for (String id : sources) {
			int row = activePeople.indexOf(id);
			if (row < 0 || people.convertRowIndexToView(row) < 0)
				return false;
		}
		return true;
	}

	private void prepareDraggedCombination(List<String> sources, String target) {
		LinkedHashSet<String> selected = new LinkedHashSet<>(sources);
		selected.add(target);
		refresh(List.copyOf(selected));
		for (int index = 0; index < mergeTarget.getItemCount(); index++) {
			if (mergeTarget.getItemAt(index).id().equals(target)) {
				mergeTarget.setSelectedIndex(index);
				break;
			}
		}
		dragSources = List.copyOf(sources);
		// A drop only stages the existing confirmation; it never creates a history
		// entry.
		cancelCombineButton.requestFocusInWindow();
	}

	private void showDragFeedback(int count, String targetId) {
		String text = targetId == null ? "Select people, or drag them onto another person."
				: "Combine " + count + " with " + viewPeople.get(targetId).name() + " · review before confirming";
		dragHint.setText(text);
		dragHint.setToolTipText("Drag: " + text + " Use Ctrl/Cmd or Shift to select multiple people.");
	}

	private List<AuthorIdentity> groupIdentities(String id) {
		return originals.stream().filter(identity -> id.equals(viewAssignments.get(identity.key()))).toList();
	}

	private List<String> options(String id, int column) {
		LinkedHashSet<String> values = new LinkedHashSet<>();
		if (column == 0)
			values.add(viewPeople.get(id).name());
		for (AuthorIdentity identity : groupIdentities(id)) {
			if (column == 0) {
				values.add(identity.name());
				values.add(identity.displayName());
			} else
				values.add(column == 1 ? identity.email() : identity.login());
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
			dragSources = List.of();
			detailPersonId = null;
			visibleIdentities = List.of();
			if (selected.size() == 1) {
				String id = selected.get(0);
				Person person = viewPeople.get(id);
				List<AuthorIdentity> group = groupIdentities(id);
				nameField.setEditable(false);
				editNameButton.setText("Edit name");
				fill(nameField, options(id, 0), person.name());
				fill(emailField, options(id, 1), person.emailFor(group));
				fill(accountField, options(id, 2), person.loginFor(group));
				participation.setSelectedItem(person.role());
				roleHint.setText(person.role() == Role.MEMBER ? "Included in the team and expected share."
						: "Visible in results; excluded from the team share.");
				visibleIdentities = authors.stream()
						.filter(author -> id.equals(viewAssignments.get(author.identity().key()))).toList();
				long identityCount = viewAssignments.values().stream().filter(id::equals).count();
				detailTitle.setText(person.name() + " (" + identityCount
						+ (identityCount == 1 ? " identity)" : " identities)"));
				long additional = identityCount - visibleIdentities.size();
				detailTitle.setToolTipText("Person: " + person.name() + " · " + visibleIdentities.size()
						+ " identities in this analysis"
						+ (additional > 0 ? " · " + additional + " additional saved aliases" : ""));
				detailPersonId = id;
				updateContributionHint();
				cards.show(detailCards, "single");
			} else if (selected.size() > 1) {
				mergeTarget.removeAllItems();
				for (String id : selected)
					mergeTarget.addItem(choice(id));
				// Destination is always an explicit choice, independent of table order.
				mergeTarget.setSelectedIndex(-1);
				Set<Role> roles = new LinkedHashSet<>();
				selected.forEach(id -> roles.add(viewPeople.get(id).role()));
				mergeRole.setSelectedItem(roles.size() == 1 ? roles.iterator().next() : null);
				long count = viewAssignments.values().stream().filter(selected::contains).count();
				mergeSummary.setText(
						selected.size() + " people → 1 person · " + count + " identities (including saved aliases)");
				cards.show(detailCards, "multiple");
			} else
				cards.show(detailCards, "empty");
			identityField.removeAllItems();
			visibleIdentities.stream().map(AuthorshipForm::identityLabel).forEach(identityField::addItem);
			identities.setListData(visibleIdentities.stream().map(AuthorshipForm::identityLabel).toArray(String[]::new));
			if (!visibleIdentities.isEmpty())
				identities.setSelectedIndex(0);
			updateIdentityDetails();
		} finally {
			updating = false;
		}
		updateActions();
	}

	/**
	 * Commit typed names before selection, history, restoration or save can replace
	 * the detail panel.
	 */
	private boolean commitDetails() {
		if (updating || detailPersonId == null)
			return false;
		Person before = viewPeople.get(detailPersonId);
		String name = String
				.valueOf(nameField.isEditable() ? nameField.getEditor().getItem() : nameField.getSelectedItem()).trim();
		if (name.isEmpty()) {
			nameField.getEditor().setItem(before.name());
			throw new IllegalStateException("A visible name cannot be blank.");
		}
		String email = emailField.getSelectedItem() == null ? "" : emailField.getSelectedItem().toString();
		String login = accountField.getSelectedItem() == null ? "" : accountField.getSelectedItem().toString();
		// An untouched automatic contact remains automatic instead of becoming a
		// spurious edit.
		List<AuthorIdentity> group = groupIdentities(before.id());
		if (email.equals(before.emailFor(group)))
			email = before.email();
		if (login.equals(before.loginFor(group)))
			login = before.login();
		Person after = new Person(before.id(), name, (Role) participation.getSelectedItem(), email, login);
		if (after.equals(before))
			return false;
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
				if (row >= 0)
					people.addRowSelectionInterval(row, row);
			}
			int contributors = contributorCount();
			int size = declaredTeamSize == null ? contributors : declaredTeamSize;
			identityCount.setText(Integer.toString(authors.size()));
			peopleCount.setText(Integer.toString(activePeople.size()));
			contributorCountLabel.setText(Integer.toString(contributors));
			teamSizeValue.setText(Integer.toString(size));
			teamSizeSource.setText(declaredTeamSize == null ? "Inferred" : "Declared");
			expectedShareValue.setText(NumberFormats.percentage(size == 0 ? Double.NaN : 1.0 / size));
			boolean mismatch = declaredTeamSize != null && declaredTeamSize != contributors;
			summaryWarning.setText(mismatch
					? "Declared team size (" + declaredTeamSize + ") differs from teammates with Java commits ("
							+ contributors + ")."
					: "");
			summaryWarning.setVisible(mismatch);
		} finally {
			updating = false;
		}
		showDetails();
		updateActions();
		AuthorshipConfig config = rules();
		if (source != null && (previewRules == null || !previewRules.assignments().equals(config.assignments())
				|| !previewRules.people().equals(config.people())))
			updatePreview(config);
	}

	private void updateActions() {
		if (updating)
			return;
		boolean pendingName = hasPendingName();
		undoButton.setEnabled(draft.canUndo() || pendingName);
		redoButton.setEnabled(draft.canRedo() && !pendingName);
		undoButton.setToolTipText(
				pendingName ? "Undo: Edit person" : draft.canUndo() ? "Undo: " + draft.undoLabel() : "Nothing to undo");
		redoButton.setToolTipText(draft.canRedo() ? "Redo: " + draft.redoLabel() : "Nothing to redo");
		discardButton.setEnabled(draft.isDirty() || pendingName);
		historyStatus.setText(draft.isDirty() || pendingName ? "Pending local changes" : "No pending changes");
		boolean multiple = selectedPeople().size() > 1;
		boolean destinationChosen = mergeTarget.getSelectedItem() != null;
		boolean roleChosen = mergeRole.getSelectedItem() != null;
		boolean ready = multiple && destinationChosen && roleChosen;
		unifyButton.setEnabled(multiple);
		unifyButton.setText(ready ? "Confirm combination" : "Combine selected…");
		String instructions = !destinationChosen ? "Choose the destination person before confirming."
				: !roleChosen ? "Choose the role of the combined person."
						: "Review the resulting person before combining.";
		mergeInstructions.setText(instructions);
		unifyButton.setToolTipText(multiple ? instructions : "Select at least two people to combine them.");
		moveButton.setEnabled(!identities.isSelectionEmpty());
		boolean grouped = detailPersonId != null
				&& viewAssignments.values().stream().filter(detailPersonId::equals).count() > 1;
		separateButton.setEnabled(grouped && !identities.isSelectionEmpty());
	}

	private PersonChoice choice(String id) {
		Person person = viewPeople.get(id);
		List<AuthorIdentity> group = groupIdentities(id);
		String context = person.emailFor(group);
		if (context.isEmpty())
			context = person.loginFor(group);
		return new PersonChoice(id, person.name() + (context.isEmpty() ? "" : " — " + context));
	}

	private void requestCombination() {
		if (selectedPeople().size() < 2)
			return;
		JComboBox<?> required = mergeTarget.getSelectedItem() == null ? mergeTarget
				: mergeRole.getSelectedItem() == null ? mergeRole : null;
		if (required != null) {
			required.requestFocusInWindow();
			if (required.isShowing())
				required.showPopup();
			return;
		}
		unifySelected();
	}

	void unifySelected() {
		List<String> selected = selectedPeople();
		PersonChoice target = (PersonChoice) mergeTarget.getSelectedItem();
		Role role = (Role) mergeRole.getSelectedItem();
		if (target == null || role == null)
			throw new IllegalStateException("Choose a destination and role before combining.");
		commitDetails();
		draft.merge(new LinkedHashSet<>(selected), target.id(), role);
		refresh(List.of(target.id()));
	}

	/** Keep identity controls within the viewport, with vertical scrolling on short screens. */
	private static final class IdentityDetailsPanel extends JPanel implements javax.swing.Scrollable {
		private static final long serialVersionUID = 1L;

		IdentityDetailsPanel() {
			super(new BorderLayout(6, 6));
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
		@Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return 16;
		}
		@Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return Math.max(16, visible.height - 16);
		}
	}

	private static String identityLabel(AuthorSummary author) {
		AuthorIdentity identity = author.identity();
		String name = identity.name().isEmpty() ? identity.displayName() : identity.name();
		return name + (identity.email().isEmpty() ? "" : " · " + identity.email())
				+ (identity.login().isEmpty() || identity.login().equals(name) ? "" : " · @" + identity.login());
	}

	private void updateIdentityDetails() {
		selectingIdentity = true;
		try {
			int[] selected = identities.getSelectedIndices();
			identityField.setSelectedIndex(selected.length == 1 ? selected[0] : -1);
			identityField.setToolTipText(selected.length == 1 ? identityLabel(visibleIdentities.get(selected[0]))
					: selected.length + " identities selected");
			multipleIdentitiesButton.setEnabled(visibleIdentities.size() > 1);
			multipleIdentitiesButton.setText(selected.length > 1 ? selected.length + " selected · Change…" : "Select multiple…");
		} finally {
			selectingIdentity = false;
		}
	}

	private List<String> selectedIdentityKeys() {
		List<String> keys = new ArrayList<>();
		for (int row : identities.getSelectedIndices())
			keys.add(visibleIdentities.get(row).identity().key());
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
		if (targets.isEmpty())
			return;
		JComboBox<PersonChoice> choices = new JComboBox<>(targets.toArray(PersonChoice[]::new));
		PlainTextRenderers.install(choices);
		if (DialogSizing.showConfirmDialog(this, choices, "Move selected identities to",
				JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
			moveSelectedTo(((PersonChoice) choices.getSelectedItem()).id());
	}

	void moveSelectedTo(String targetId) {
		List<String> keys = selectedIdentityKeys();
		commitDetails();
		draft.move(keys, targetId);
		refresh(List.of(targetId));
	}

	private boolean hasPendingName() {
		return detailPersonId != null && nameField.isEditable() && !String.valueOf(nameField.getEditor().getItem())
				.trim().equals(viewPeople.get(detailPersonId).name());
	}

	private boolean discardInvalidName() {
		if (detailPersonId == null || !nameField.isEditable()
				|| !String.valueOf(nameField.getEditor().getItem()).isBlank())
			return false;
		showDetails();
		return true;
	}

	void undo() {
		if (discardInvalidName())
			return;
		commitDetails();
		List<String> selected = selectedPeople();
		draft.undo();
		refresh(selected);
	}

	void redo() {
		commitDetails();
		List<String> selected = selectedPeople();
		draft.redo();
		refresh(selected);
	}

	void discardSession() {
		discardInvalidName();
		commitDetails();
		draft.discardSession();
		refresh(List.of());
	}

	void restoreOriginal() {
		discardInvalidName();
		commitDetails();
		draft.restoreOriginal();
		refresh(List.of());
	}

	private void filter() {
		if (updating)
			return;
		runAction(() -> {
			commitDetails();
			updating = true;
			try {
				String query = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
				@SuppressWarnings("unchecked")
				TableRowSorter<AbstractTableModel> sorter = (TableRowSorter<AbstractTableModel>) people.getRowSorter();
				sorter.setRowFilter(new RowFilter<>() {
					@Override
					public boolean include(Entry<? extends AbstractTableModel, ? extends Integer> entry) {
						String id = activePeople.get(entry.getIdentifier());
						String text = viewPeople.get(id).name() + " " + groupIdentities(id).toString();
						return text.toLowerCase(java.util.Locale.ROOT).contains(query);
					}
				});
			} finally {
				updating = false;
			}
			refresh(selectedPeople());
		});
	}

	private void installHistoryKeys() {
		int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
				: Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		bindHistory(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mask), "undo-authorship", this::undo);
		bindHistory(
				KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mask | java.awt.event.InputEvent.SHIFT_DOWN_MASK),
				"redo-authorship", this::redo);
		bindHistory(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, mask), "redo-authorship-y", this::redo);
	}

	private void bindHistory(KeyStroke key, String name, Runnable action) {
		AbstractAction handler = new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent event) {
				runAction(action);
			}
		};
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
		getActionMap().put(name, handler);
		// Text editors must participate in the same operation history.
		JComponent editor = (JComponent) nameField.getEditor().getEditorComponent();
		editor.getInputMap().put(key, name);
		editor.getActionMap().put(name, handler);
	}

	private void runAction(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException ex) {
			DialogSizing.showMessageDialog(this, ex.getMessage(), "Could not edit authorship",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private int contributors(AuthorshipConfig config) {
		return (int) authors.stream().filter(author -> author.javaCommits() > 0)
				.map(author -> config.personFor(author.identity())).filter(person -> person.role() == Role.MEMBER)
				.map(Person::id).distinct().count();
	}

	int contributorCount() {
		return contributors(rules());
	}

	AuthorshipConfig snapshot() {
		if (commitDetails())
			refresh(selectedPeople());
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
						.append("\n  Role: ").append(roleLabel(oldPerson.role())).append(" → ")
						.append(roleLabel(newPerson.role())).append("\n  Email: ").append(oldPerson.email())
						.append(" → ").append(newPerson.email()).append("\n  GitHub: ").append(oldPerson.login())
						.append(" → ").append(newPerson.login()).append('\n');
		}
		Set<String> knownKeys = originals.stream().map(AuthorIdentity::key)
				.collect(java.util.stream.Collectors.toSet());
		Map<String, String> oldAssignments = before.assignments(), newAssignments = after.assignments();
		long absentMoved = oldAssignments.entrySet().stream().filter(entry -> !knownKeys.contains(entry.getKey()))
				.filter(entry -> newAssignments.containsKey(entry.getKey())
						&& !entry.getValue().equals(newAssignments.get(entry.getKey())))
				.count();
		if (absentMoved > 0)
			result.append("Assignments changed for absent identities: ").append(absentMoved).append('\n');
		long removed = oldAssignments.keySet().stream().filter(key -> !newAssignments.containsKey(key)).count();
		if (removed > 0)
			result.append("Saved rules removed for absent identities: ").append(removed).append('\n');
		result.append("Contributors: ").append(contributors(before)).append(" → ").append(contributors(after))
				.append("\n\nApply these assignments and recalculate locally from the original download?");
		return result.toString();
	}

	private void updatePreview(AuthorshipConfig config) {
		previewRules = config.copy();
		int generation = ++previewGeneration;
		if (previewWorker != null)
			previewWorker.cancel(true);
		contributionBadges = Map.of();
		preview = new RepoStats();
		preview.setTeamSize(declaredTeamSize);
		previewStatus.setText("Calculating local preview…");
		updateContributionHint();
		people.repaint();
		previewWorker = new SwingWorker<>() {
			@Override
			protected List<UserStats> doInBackground() {
				return StatisticsCalculator.personStats(source.activeCommits(), config,
						ContributionMetrics::defaultRole);
			}

			@Override
			protected void done() {
				if (generation != previewGeneration || isCancelled())
					return;
				try {
					preview.setUserStats(get());
					int teamChurn = ContributionMetrics.teamChurn(preview);
					int teamSize = ContributionMetrics.effectiveTeamSize(preview);
					Map<String, MainWindow.ContributionBadge> badges = new java.util.HashMap<>();
					for (UserStats user : preview.getUserStats())
						badges.put(user.getPersonId(),
								MainWindow.classifyBadge(user.getChurn(), user.getCommits(), teamChurn, teamSize));
					contributionBadges = Map.copyOf(badges);
					previewStatus.setText("Local preview · Apply recalculates without connecting to GitHub.");
					// Keep selection and pending text intact when calculated values arrive.
					for (int row = 0; row < peopleModel.getRowCount(); row++)
						peopleModel.fireTableCellUpdated(row, 4);
					people.repaint();
					updateContributionHint();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					previewStatus.setText("Local preview interrupted. Apply can recalculate again.");
					updateContributionHint();
				} catch (java.util.concurrent.ExecutionException | RuntimeException ex) {
					previewStatus.setText("Local preview unavailable. Apply can recalculate again.");
					updateContributionHint();
				}
			}
		};
		previewWorker.execute();
	}

	@Override
	public void removeNotify() {
		++previewGeneration;
		if (previewWorker != null)
			previewWorker.cancel(true);
		super.removeNotify();
	}

	private MainWindow.ContributionBadge badgeFor(String id) {
		return switch (viewPeople.get(id).role()) {
		case TEACHER -> MainWindow.ContributionBadge.TEACHER;
		case EXCLUDED -> MainWindow.ContributionBadge.EXCLUDED;
		case MEMBER -> contributionBadges.get(id);
		};
	}

	private void stylePersonCell(JLabel label, JTable table, int row, int col, boolean selected) {
		label.setIcon(null);
		if (row < 0 || row >= table.getRowCount())
			return;
		MainWindow.ContributionBadge badge = badgeFor(activePeople.get(table.convertRowIndexToModel(row)));
		if (badge != null) {
			Color background = MainWindow.contributionBackground(table.getBackground(), badge.color, selected);
			label.setBackground(background);
			label.setForeground(MainWindow.contributionForeground(badge.color, background, selected));
			if (table.convertColumnIndexToModel(col) == 0) {
				label.setIcon(badge.icon());
				label.setIconTextGap(6);
			}
			label.setToolTipText(badge.shortText() + ". Observed share uses team Java churn.");
		} else
			label.setToolTipText("Contribution preview unavailable or calculating.");
		if (col == 0) {
			javax.swing.border.Border marker = selected
					? BorderFactory.createMatteBorder(0, 3, 0, 0, table.getSelectionBackground())
					: BorderFactory.createEmptyBorder(0, 3, 0, 0);
			label.setBorder(BorderFactory.createCompoundBorder(marker, label.getBorder()));
		}
	}

	private void updateContributionHint() {
		if (detailPersonId == null)
			return;
		MainWindow.ContributionBadge badge = badgeFor(detailPersonId);
		contributionHint.setIcon(badge == null ? null : badge.icon());
		contributionHint.setForeground(badge == null ? getForeground()
				: MainWindow.contributionForeground(badge.color, getBackground(), false));
		if (badge == null) {
			contributionHint.setText(previewStatus.getText().startsWith("Calculating") ? "Calculating contribution…"
					: "Contribution preview unavailable");
		} else {
			float share = preview.getUserStats().stream().filter(user -> detailPersonId.equals(user.getPersonId()))
					.findFirst().map(user -> ContributionMetrics.teamShare(preview, user)).orElse(Float.NaN);
			contributionHint.setText(
					badge.shortText() + (Float.isFinite(share) ? " · " + NumberFormats.percentage(share) : ""));
		}
		int commits = visibleIdentities.stream().mapToInt(AuthorSummary::javaCommits).sum();
		commitHint.setText("· " + commits + (commits == 1 ? " Java commit" : " Java commits"));
		contributionHint.setToolTipText(
				"Observed contribution uses Java churn and the current declared or inferred team size.");
	}

	private final class ShareRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		private float share;
		private Color barColor;

		ShareRenderer() {
			setHorizontalAlignment(SwingConstants.RIGHT);
			setOpaque(false);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
				int row, int col) {
			super.getTableCellRendererComponent(table,
					NumberFormats.percentage(value == null ? Double.NaN : (Float) value), selected, focus, row, col);
			share = value instanceof Float number ? number : Float.NaN;
			stylePersonCell(this, table, row, col, selected);
			MainWindow.ContributionBadge badge = row >= 0 && row < table.getRowCount()
					? badgeFor(activePeople.get(table.convertRowIndexToModel(row)))
					: null;
			barColor = badge == null ? new Color(54, 130, 127) : badge.color;
			return this;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			graphics.setColor(getBackground());
			graphics.fillRect(0, 0, getWidth(), getHeight());
			if (Float.isFinite(share)) {
				graphics.setColor(MainWindow.contributionBarColor(barColor, getBackground(), getForeground(), false));
				graphics.fillRoundRect(3, 5, Math.round((getWidth() - 6) * Math.max(0, Math.min(1, share))),
						getHeight() - 10, 5, 5);
			}
			super.paintComponent(graphics);
		}
	}
}
