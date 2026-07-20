package com.ticketapp.controller.payment;

import com.ticketapp.application.payment.PaymentAppService;
import com.ticketapp.application.payment.PaymentInstruction;
import com.ticketapp.controller.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class PaymentController {

    private final PaymentAppService paymentAppService;

    public PaymentController(PaymentAppService paymentAppService) {
        this.paymentAppService = paymentAppService;
    }

    @PostMapping("/{orderNumber}/pay")
    public ApiResponse<PayResponse> pay(@PathVariable String orderNumber, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PaymentInstruction instruction = paymentAppService.pay(userId, orderNumber);
        return ApiResponse.ok(PayResponse.from(instruction));
    }
}
