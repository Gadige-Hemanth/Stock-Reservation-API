package com.analyticalrx.inventory.dto;

import com.analyticalrx.inventory.model.ReservationItem;

public class ReservationItemResponse {
    private Long productId;
    private String sku;
    private String productName;
    private int quantity;

    public ReservationItemResponse() {
    }

    public static ReservationItemResponse fromEntity(ReservationItem item) {
        ReservationItemResponse dto = new ReservationItemResponse();
        dto.setProductId(item.getProduct().getId());
        dto.setSku(item.getProduct().getSku());
        dto.setProductName(item.getProduct().getName());
        dto.setQuantity(item.getQuantity());
        return dto;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
