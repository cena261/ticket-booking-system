package com.ticketapp.controller.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReserveRequest(
        @NotNull Long ticketTypeId,
        @Min(1) @Max(10) int quantity) {
}
