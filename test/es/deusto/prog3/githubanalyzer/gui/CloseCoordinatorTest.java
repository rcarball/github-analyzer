// IAG (herramientas: Codex (OpenAI))
// AI-GENERATED TEST CASES
package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CloseCoordinatorTest {

	@Test void closeDuringDownloadRequestsCancellationAndWaitsForCleanup() {
		var busy = new AtomicBoolean(true);
		var cancelled = new AtomicInteger();
		var disposed = new AtomicInteger();
		var coordinator = new CloseCoordinator(busy::get, cancelled::incrementAndGet, disposed::incrementAndGet);
		coordinator.request();
		coordinator.request();
		assertTrue(coordinator.isRequested());
		assertEquals(1, cancelled.get());
		coordinator.operationFinished();
		assertEquals(0, disposed.get());
		busy.set(false);
		coordinator.operationFinished();
		coordinator.operationFinished();
		assertEquals(1, disposed.get());
	}

	@Test void idleWindowClosesWithoutStartingCancellation() {
		var disposed = new AtomicInteger();
		var coordinator = new CloseCoordinator(() -> false, () -> fail("No download to cancel"),
				disposed::incrementAndGet);
		coordinator.request();
		assertEquals(1, disposed.get());
	}
}
