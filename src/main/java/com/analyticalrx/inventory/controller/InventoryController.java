package com.analyticalrx.inventory.controller;

import com.analyticalrx.inventory.dto.ProductResponse;
import com.analyticalrx.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Inventory", description = "Endpoints for viewing warehouse inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping({"/inventory", "/products"})
    @Operation(summary = "View inventory", description = "Lists all warehouse products with total, reserved, and available quantities")
    public ResponseEntity<List<ProductResponse>> viewInventory() {
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }
}
