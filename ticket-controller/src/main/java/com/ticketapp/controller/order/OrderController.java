package com.ticketapp.controller.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
import com.ticketapp.controller.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ReserveOrderService reserveOrderService;

    public OrderController(ReserveOrderService reserveOrderService) {
        this.reserveOrderService = reserveOrderService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<ReserveResponse>> reserve(@Valid @RequestBody ReserveRequest request,
                                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ReserveResult result = reserveOrderService.reserve(userId, request.ticketTypeId(), request.quantity());

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok(new ReserveResponse(result.orderNumber(), result.expiresAt())));
        }
        ErrorCode errorCode = result.errorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null));
    }
}
