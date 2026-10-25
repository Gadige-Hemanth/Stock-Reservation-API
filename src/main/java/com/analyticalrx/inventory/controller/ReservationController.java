package com.analyticalrx.inventory.controller;

import com.analyticalrx.inventory.dto.CreateReservationRequest;
import com.analyticalrx.inventory.dto.ReservationResponse;
import com.analyticalrx.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Endpoints for managing product stock reservations")
public class ReservationController {

    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Create reservation", description = "Creates an ACTIVE reservation and holds requested stock across all specified items atomically")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = inventoryService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve reservation", description = "Returns reservation status, items, quantities, and audit timestamps")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReservation(id));
    }

    @RequestMapping(value = "/{id}/confirm", method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Confirm reservation", description = "Completes the sale: deducts total stock and reserved stock, setting status to CONFIRMED")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.confirmReservation(id));
    }

    @RequestMapping(value = "/{id}/cancel", method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Cancel reservation", description = "Releases reserved stock back to available inventory, setting status to CANCELLED")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.cancelReservation(id));
    }
}
