package com.analyticalrx.inventory.dto;

import com.analyticalrx.inventory.model.Reservation;
import com.analyticalrx.inventory.model.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ReservationResponse {
    private Long id;
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReservationItemResponse> items;

    public ReservationResponse() {
    }

    public static ReservationResponse fromEntity(Reservation reservation) {
        ReservationResponse dto = new ReservationResponse();
        dto.setId(reservation.getId());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setUpdatedAt(reservation.getUpdatedAt());
        dto.setItems(reservation.getItems().stream()
                .map(ReservationItemResponse::fromEntity)
                .collect(Collectors.toList()));
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ReservationItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ReservationItemResponse> items) {
        this.items = items;
    }
}
