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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

/**
 * Local person-group drags only prepare a combination; confirmation performs
 * the edit.
 */
final class PeopleTransferHandler extends TransferHandler {
	private static final long serialVersionUID = 1L;
	private static final DataFlavor PEOPLE = new DataFlavor(
			DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Payload.class.getName(), "Repository person groups");

	private record Payload(PeopleTransferHandler owner, Object revision, List<String> people) {
	}

	private final JTable table;
	private final Supplier<List<String>> selection;
	private final Supplier<Object> revision;
	private final IntFunction<String> personAtRow;
	private final BiPredicate<List<String>, String> accepts;
	private final BiConsumer<List<String>, String> prepare;
	private final BiConsumer<Integer, String> feedback;
	private Payload activePayload;
	private int hoverRow = -1;

	PeopleTransferHandler(JTable table, Supplier<List<String>> selection, Supplier<Object> revision,
			IntFunction<String> personAtRow, BiPredicate<List<String>, String> accepts,
			BiConsumer<List<String>, String> prepare, BiConsumer<Integer, String> feedback) {
		this.table = table;
		this.selection = selection;
		this.revision = revision;
		this.personAtRow = personAtRow;
		this.accepts = accepts;
		this.prepare = prepare;
		this.feedback = feedback;
		table.addPropertyChangeListener("dropLocation", event -> {
			if (event.getNewValue() == null)
				clearFeedback();
		});
	}

	@Override
	public int getSourceActions(JComponent component) {
		return component == table && table.getSelectedRowCount() > 0 ? MOVE : NONE;
	}

	@Override
	protected Transferable createTransferable(JComponent component) {
		activePayload = null;
		clearFeedback();
		if (component != table)
			return null;
		List<String> ids;
		try {
			ids = List.copyOf(selection.get());
		} catch (IllegalStateException ex) {
			// An invalid pending name cancels the gesture before it can stage a
			// combination.
			return null;
		}
		if (ids.isEmpty())
			return null;
		Payload payload = new Payload(this, revision.get(), ids);
		activePayload = payload;
		return new Transferable() {
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] { PEOPLE };
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return PEOPLE.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);
				return payload;
			}
		};
	}

	private Payload payload(Transferable transfer) {
		if (transfer == null)
			return null;
		try {
			if (!transfer.isDataFlavorSupported(PEOPLE))
				return null;
			Object value = transfer.getTransferData(PEOPLE);
			if (value instanceof Payload payload && payload.owner() == this && payload == activePayload
					&& payload.revision() == revision.get())
				return payload;
		} catch (UnsupportedFlavorException | IOException | RuntimeException ex) {
			// Foreign, expired or malformed transfers are rejected without changing the
			// draft.
		}
		return null;
	}

	/**
	 * Shared by native drop validation and headless tests; view rows are resolved
	 * at drop time.
	 */
	boolean canDrop(Transferable transfer, int viewRow) {
		Payload payload = payload(transfer);
		String target = personAtRow.apply(viewRow);
		return payload != null && target != null && accepts.test(payload.people(), target);
	}

	/** Prepare the same confirmation panel used by ordinary multiple selection. */
	boolean prepareDrop(Transferable transfer, int viewRow) {
		if (!canDrop(transfer, viewRow))
			return false;
		Payload payload = payload(transfer);
		String target = personAtRow.apply(viewRow);
		prepare.accept(payload.people(), target);
		activePayload = null;
		clearFeedback();
		return true;
	}

	@Override
	public boolean canImport(TransferSupport support) {
		if (!support.isDrop() || support.getComponent() != table || (support.getSourceDropActions() & MOVE) == 0) {
			clearFeedback();
			return false;
		}
		JTable.DropLocation location = (JTable.DropLocation) support.getDropLocation();
		boolean allowed = !location.isInsertRow() && !location.isInsertColumn()
				&& canDrop(support.getTransferable(), location.getRow());
		support.setShowDropLocation(allowed);
		if (allowed) {
			support.setDropAction(MOVE);
			hoverRow = location.getRow();
			Payload payload = payload(support.getTransferable());
			feedback.accept(payload.people().size(), personAtRow.apply(hoverRow));
			table.repaint();
		} else
			clearFeedback();
		return allowed;
	}

	@Override
	public boolean importData(TransferSupport support) {
		return canImport(support)
				&& prepareDrop(support.getTransferable(), ((JTable.DropLocation) support.getDropLocation()).getRow());
	}

	@Override
	protected void exportDone(JComponent source, Transferable transfer, int action) {
		// MOVE describes the gesture only. Removing source rows here would bypass
		// confirmation.
		activePayload = null;
		clearFeedback();
	}

	int hoverRow() {
		return hoverRow;
	}

	private void clearFeedback() {
		hoverRow = -1;
		feedback.accept(0, null);
		table.repaint();
	}
}
