package com.analyticalrx.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CreateReservationRequest {

    @NotEmpty(message = "Reservation items list must not be empty")
    @Valid
    private List<ReservationItemRequest> items;

    public CreateReservationRequest() {
    }

    public CreateReservationRequest(List<ReservationItemRequest> items) {
        this.items = items;
    }

    public List<ReservationItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ReservationItemRequest> items) {
        this.items = items;
    }
}
