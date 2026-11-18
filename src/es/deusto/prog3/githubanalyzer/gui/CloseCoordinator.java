// IAG (herramientas: Codex (OpenAI))
package es.deusto.prog3.githubanalyzer.gui;

import java.util.function.BooleanSupplier;

/** EDT-only close request: wait for active operations instead of terminating a save. */
final class CloseCoordinator {

	private final BooleanSupplier busy;
	private final Runnable cancel;
	private final Runnable close;
	private boolean requested;
	private boolean closed;

	CloseCoordinator(BooleanSupplier busy, Runnable cancel, Runnable close) {
		this.busy = busy;
		this.cancel = cancel;
		this.close = close;
	}

	void request() {
		if (requested)
			return;
		requested = true;
		if (busy.getAsBoolean())
			cancel.run();
		operationFinished();
	}

	boolean isRequested() {
		return requested;
	}

	void operationFinished() {
		if (requested && !closed && !busy.getAsBoolean()) {
			closed = true;
			close.run();
		}
	}
}
