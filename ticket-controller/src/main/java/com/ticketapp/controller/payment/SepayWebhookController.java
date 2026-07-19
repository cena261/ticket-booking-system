package com.ticketapp.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.application.payment.PaymentAppService;
import com.ticketapp.application.payment.SepayProperties;
import com.ticketapp.application.payment.SepayWebhookRequest;
import com.ticketapp.application.payment.SepayWebhookVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/sepay")
public class SepayWebhookController {

    private static final Map<String, Boolean> SUCCESS = Map.of("success", true);
    private static final Map<String, Boolean> FAILURE = Map.of("success", false);

    private final SepayWebhookVerifier verifier;
    private final PaymentAppService paymentAppService;
    private final SepayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SepayWebhookController(SepayWebhookVerifier verifier, PaymentAppService paymentAppService,
                                  SepayProperties properties) {
        this.verifier = verifier;
        this.paymentAppService = paymentAppService;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<Map<String, Boolean>> receive(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(name = "X-SePay-Timestamp", required = false) String timestamp) {

        if (properties.getWebhook().isRequireSignature() && !verifier.verify(body, signature, timestamp)) {
            log.warn("sepay webhook rejected: invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(FAILURE);
        }

        SepayWebhookRequest request;
        try {
            request = objectMapper.readValue(body, SepayWebhookRequest.class);
        } catch (Exception ex) {
            log.warn("sepay webhook rejected: malformed payload", ex);
            return ResponseEntity.badRequest().body(FAILURE);
        }

        paymentAppService.handleWebhook(request);
        return ResponseEntity.ok(SUCCESS);
    }
}
