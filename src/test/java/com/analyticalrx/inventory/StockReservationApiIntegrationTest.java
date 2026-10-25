package com.analyticalrx.inventory;

import com.analyticalrx.inventory.dto.*;
import com.analyticalrx.inventory.exception.InsufficientStockException;
import com.analyticalrx.inventory.exception.InvalidReservationStateException;
import com.analyticalrx.inventory.exception.ResourceNotFoundException;
import com.analyticalrx.inventory.model.Product;
import com.analyticalrx.inventory.model.ReservationStatus;
import com.analyticalrx.inventory.repository.ProductRepository;
import com.analyticalrx.inventory.repository.ReservationRepository;
import com.analyticalrx.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SuppressWarnings("null")
public class StockReservationApiIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Product notebook;
    private Product pen;
    private Product backpack;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        productRepository.deleteAll();

        notebook = productRepository.save(new Product("NOTE-001", "Notebook", 10, 0));
        pen = productRepository.save(new Product("PEN-001", "Pen", 5, 0));
        backpack = productRepository.save(new Product("BAG-001", "Backpack", 2, 0));
    }

    @Test
    @DisplayName("Scenario 1: Successful lifecycle (Reserve 3 notebooks and 2 pens, then confirm)")
    void testSuccessfulLifecycle() {
        // 1. Initial State
        Product initialNotebook = productRepository.findById(notebook.getId()).orElseThrow();
        Product initialPen = productRepository.findById(pen.getId()).orElseThrow();
        assertEquals(10, initialNotebook.getAvailableStock());
        assertEquals(5, initialPen.getAvailableStock());

        // 2. Reserve 3 notebooks and 2 pens
        CreateReservationRequest request = new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 3),
                new ReservationItemRequest(pen.getId(), 2)
        ));

        ReservationResponse reservation = inventoryService.createReservation(request);
        assertNotNull(reservation.getId());
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());

        // Verify stock after reservation (Notebook: 10/3/7, Pen: 5/2/3)
        Product resNotebook = productRepository.findById(notebook.getId()).orElseThrow();
        Product resPen = productRepository.findById(pen.getId()).orElseThrow();

        assertEquals(10, resNotebook.getTotalStock());
        assertEquals(3, resNotebook.getReservedStock());
        assertEquals(7, resNotebook.getAvailableStock());

        assertEquals(5, resPen.getTotalStock());
        assertEquals(2, resPen.getReservedStock());
        assertEquals(3, resPen.getAvailableStock());

        // 3. Confirm Reservation
        ReservationResponse confirmed = inventoryService.confirmReservation(reservation.getId());
        assertEquals(ReservationStatus.CONFIRMED, confirmed.getStatus());

        // Verify stock after confirmation (Notebook: 7/0/7, Pen: 3/0/3)
        Product confNotebook = productRepository.findById(notebook.getId()).orElseThrow();
        Product confPen = productRepository.findById(pen.getId()).orElseThrow();

        assertEquals(7, confNotebook.getTotalStock());
        assertEquals(0, confNotebook.getReservedStock());
        assertEquals(7, confNotebook.getAvailableStock());

        assertEquals(3, confPen.getTotalStock());
        assertEquals(0, confPen.getReservedStock());
        assertEquals(3, confPen.getAvailableStock());
    }

    @Test
    @DisplayName("Scenario 2: Cancellation (Reserve 3 notebooks and 2 pens, then cancel)")
    void testCancellationLifecycle() {
        CreateReservationRequest request = new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 3),
                new ReservationItemRequest(pen.getId(), 2)
        ));

        ReservationResponse reservation = inventoryService.createReservation(request);
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());

        // Cancel reservation
        ReservationResponse cancelled = inventoryService.cancelReservation(reservation.getId());
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());

        // Verify stock is released back: Notebook: 10/0/10, Pen: 5/0/5
        Product relNotebook = productRepository.findById(notebook.getId()).orElseThrow();
        Product relPen = productRepository.findById(pen.getId()).orElseThrow();

        assertEquals(10, relNotebook.getTotalStock());
        assertEquals(0, relNotebook.getReservedStock());
        assertEquals(10, relNotebook.getAvailableStock());

        assertEquals(5, relPen.getTotalStock());
        assertEquals(0, relPen.getReservedStock());
        assertEquals(5, relPen.getAvailableStock());
    }

    @Test
    @DisplayName("Scenario 3: Atomic failure (One unavailable item causes entire request to fail without partial holds)")
    void testAtomicFailure() {
        // Request 2 notebooks (available: 10) and 3 backpacks (available: 2)
        CreateReservationRequest request = new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 2),
                new ReservationItemRequest(backpack.getId(), 3)
        ));

        assertThrows(InsufficientStockException.class, () -> inventoryService.createReservation(request));

        // Verify zero active reservations created
        assertEquals(0, reservationRepository.count());

        // Verify all product stocks remain completely untouched
        Product unchangedNotebook = productRepository.findById(notebook.getId()).orElseThrow();
        Product unchangedBackpack = productRepository.findById(backpack.getId()).orElseThrow();

        assertEquals(10, unchangedNotebook.getTotalStock());
        assertEquals(0, unchangedNotebook.getReservedStock());
        assertEquals(10, unchangedNotebook.getAvailableStock());

        assertEquals(2, unchangedBackpack.getTotalStock());
        assertEquals(0, unchangedBackpack.getReservedStock());
        assertEquals(2, unchangedBackpack.getAvailableStock());
    }

    @Test
    @DisplayName("Scenario 4: Invalid requests & State transitions")
    void testInvalidRequestsAndTransitions() {
        // 1. Missing product
        CreateReservationRequest missingProductReq = new CreateReservationRequest(List.of(
                new ReservationItemRequest(999999L, 1)
        ));
        assertThrows(ResourceNotFoundException.class, () -> inventoryService.createReservation(missingProductReq));

        // 2. Missing reservation
        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getReservation(999999L));
        assertThrows(ResourceNotFoundException.class, () -> inventoryService.confirmReservation(999999L));
        assertThrows(ResourceNotFoundException.class, () -> inventoryService.cancelReservation(999999L));

        // 3. Confirm an already cancelled reservation must fail
        ReservationResponse res = inventoryService.createReservation(new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 1)
        )));
        inventoryService.cancelReservation(res.getId());
        assertThrows(InvalidReservationStateException.class, () -> inventoryService.confirmReservation(res.getId()));

        // 4. Cancel an already confirmed reservation must fail
        ReservationResponse res2 = inventoryService.createReservation(new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 1)
        )));
        inventoryService.confirmReservation(res2.getId());
        assertThrows(InvalidReservationStateException.class, () -> inventoryService.cancelReservation(res2.getId()));
    }

    @Test
    @DisplayName("Scenario 5: Repeated actions (Idempotency)")
    void testIdempotency() {
        ReservationResponse res = inventoryService.createReservation(new CreateReservationRequest(List.of(
                new ReservationItemRequest(notebook.getId(), 2)
        )));

        // First confirm
        inventoryService.confirmReservation(res.getId());
        Product p1 = productRepository.findById(notebook.getId()).orElseThrow();
        assertEquals(8, p1.getTotalStock());
        assertEquals(0, p1.getReservedStock());

        // Repeated confirm must not change stock a second time
        ReservationResponse repeatedConfirm = inventoryService.confirmReservation(res.getId());
        assertEquals(ReservationStatus.CONFIRMED, repeatedConfirm.getStatus());

        Product p2 = productRepository.findById(notebook.getId()).orElseThrow();
        assertEquals(8, p2.getTotalStock());
        assertEquals(0, p2.getReservedStock());
    }

    @Test
    @DisplayName("Scenario 6: Competing concurrent requests (2 backpacks available, 2 concurrent requests for 2 backpacks)")
    void testConcurrencyCompetingRequests() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // ensure both threads fire simultaneously
                    CreateReservationRequest req = new CreateReservationRequest(List.of(
                            new ReservationItemRequest(backpack.getId(), 2)
                    ));
                    inventoryService.createReservation(req);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // trigger both threads
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Threads should complete within timeout");
        assertEquals(1, successCount.get(), "Exactly one request must succeed");
        assertEquals(1, failureCount.get(), "The competing request must fail due to stock depletion");

        // Verify backpack stock is 2 / 2 / 0
        Product finalBackpack = productRepository.findById(backpack.getId()).orElseThrow();
        assertEquals(2, finalBackpack.getTotalStock());
        assertEquals(2, finalBackpack.getReservedStock());
        assertEquals(0, finalBackpack.getAvailableStock());
    }
}
