package com.ticketapp.controller.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.AsyncReserveResult;
import com.ticketapp.application.order.AsyncReserveService;
import com.ticketapp.application.order.OrderStatusView;
import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
import com.ticketapp.application.ratelimit.ReserveRateLimiter;
import com.ticketapp.controller.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ReserveOrderService reserveOrderService;
    private final AsyncReserveService asyncReserveService;
    private final ReserveRateLimiter reserveRateLimiter;

    public OrderController(ReserveOrderService reserveOrderService, AsyncReserveService asyncReserveService,
                           ReserveRateLimiter reserveRateLimiter) {
        this.reserveOrderService = reserveOrderService;
        this.asyncReserveService = asyncReserveService;
        this.reserveRateLimiter = reserveRateLimiter;
    }

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<ReserveResponse>> reserve(@Valid @RequestBody ReserveRequest request,
                                                                Authentication authentication) {
        Long userId = userId(authentication);
        reserveRateLimiter.check(userId);
        ReserveResult result = reserveOrderService.reserve(userId,
                request.ticketTypeId(), request.quantity());

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok(new ReserveResponse(result.orderNumber(), result.expiresAt())));
        }
        return error(result.errorCode());
    }

    @PostMapping("/reserve-async")
    public ResponseEntity<ApiResponse<AsyncReserveResponse>> reserveAsync(@Valid @RequestBody ReserveRequest request,
                                                                          Authentication authentication) {
        Long userId = userId(authentication);
        reserveRateLimiter.check(userId);
        AsyncReserveResult result = asyncReserveService.reserveAsync(userId,
                request.ticketTypeId(), request.quantity());

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok(new AsyncReserveResponse(result.token())));
        }
        return error(result.errorCode());
    }

    @GetMapping("/status/{token}")
    public ApiResponse<OrderStatusView> status(@PathVariable String token) {
        return ApiResponse.ok(asyncReserveService.status(token));
    }

    private static Long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private static <T> ResponseEntity<ApiResponse<T>> error(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null));
    }
}
