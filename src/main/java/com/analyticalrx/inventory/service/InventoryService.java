package com.analyticalrx.inventory.service;

import com.analyticalrx.inventory.dto.*;
import com.analyticalrx.inventory.exception.InsufficientStockException;
import com.analyticalrx.inventory.exception.InvalidReservationStateException;
import com.analyticalrx.inventory.exception.ResourceNotFoundException;
import com.analyticalrx.inventory.model.Product;
import com.analyticalrx.inventory.model.Reservation;
import com.analyticalrx.inventory.model.ReservationItem;
import com.analyticalrx.inventory.model.ReservationStatus;
import com.analyticalrx.inventory.repository.ProductRepository;
import com.analyticalrx.inventory.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    public InventoryService(ProductRepository productRepository, ReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
        return ReservationResponse.fromEntity(reservation);
    }

    /**
     * Atomically creates an ACTIVE reservation across multiple products.
     * Uses pessimistic locking on product rows in ascending ID order to prevent overselling and deadlocks.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse createReservation(CreateReservationRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Reservation must contain at least one item");
        }

        // Aggregate duplicate product entries in the same request to a single total quantity
        Map<Long, Integer> aggregatedQuantities = new HashMap<>();
        for (ReservationItemRequest item : request.getItems()) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Requested quantity must be positive for product ID " + item.getProductId());
            }
            aggregatedQuantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // Sort product IDs to guarantee consistent row lock acquisition order (prevents deadlocks)
        List<Long> sortedProductIds = new ArrayList<>(aggregatedQuantities.keySet());
        Collections.sort(sortedProductIds);

        Map<Long, Product> lockedProducts = new LinkedHashMap<>();

        // Lock all product records first and verify stock
        for (Long productId : sortedProductIds) {
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product with ID " + productId + " does not exist"));

            int requestedQty = aggregatedQuantities.get(productId);
            int availableStock = product.getAvailableStock();

            if (availableStock < requestedQty) {
                log.warn("Insufficient stock for product {} (SKU: {}). Available: {}, Requested: {}",
                        productId, product.getSku(), availableStock, requestedQty);
                throw new InsufficientStockException(String.format(
                        "Insufficient available stock for SKU '%s' (%s). Available: %d, Requested: %d",
                        product.getSku(), product.getName(), availableStock, requestedQty));
            }

            lockedProducts.put(productId, product);
        }

        // If all items are available, create reservation and apply holds
        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.ACTIVE);

        for (Long productId : sortedProductIds) {
            Product product = lockedProducts.get(productId);
            int requestedQty = aggregatedQuantities.get(productId);

            product.setReservedStock(product.getReservedStock() + requestedQty);
            productRepository.save(product);

            ReservationItem item = new ReservationItem(reservation, product, requestedQty);
            reservation.addItem(item);
        }

        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Successfully created ACTIVE reservation with ID {}", savedReservation.getId());
        return ReservationResponse.fromEntity(savedReservation);
    }

    /**
     * Confirms an ACTIVE reservation, completing the sale.
     * Deducts reserved stock and total stock. Idempotent for already CONFIRMED reservations.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse confirmReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.info("Reservation {} is already CONFIRMED. Returning existing state idempotently.", id);
            return ReservationResponse.fromEntity(reservation);
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new InvalidReservationStateException("Cannot confirm reservation " + id + " because it is CANCELLED");
        }

        // Status is ACTIVE: Complete the sale
        for (ReservationItem item : reservation.getItems()) {
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product ID " + item.getProduct().getId() + " not found"));

            product.setTotalStock(product.getTotalStock() - item.getQuantity());
            product.setReservedStock(product.getReservedStock() - item.getQuantity());
            productRepository.save(product);
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} successfully CONFIRMED.", id);
        return ReservationResponse.fromEntity(saved);
    }

    /**
     * Cancels an ACTIVE reservation, releasing held stock.
     * Decrements reserved stock while leaving total stock unchanged. Idempotent for already CANCELLED reservations.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.info("Reservation {} is already CANCELLED. Returning existing state idempotently.", id);
            return ReservationResponse.fromEntity(reservation);
        }

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new InvalidReservationStateException("Cannot cancel reservation " + id + " because it is already CONFIRMED");
        }

        // Status is ACTIVE: Release the reserved hold
        for (ReservationItem item : reservation.getItems()) {
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product ID " + item.getProduct().getId() + " not found"));

            product.setReservedStock(product.getReservedStock() - item.getQuantity());
            productRepository.save(product);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} successfully CANCELLED.", id);
        return ReservationResponse.fromEntity(saved);
    }
}
