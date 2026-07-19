package com.ticketapp.application.payment;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.observability.BuyPathMetrics;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.payment.PaymentTransaction;
import com.ticketapp.domain.payment.PaymentTransactionRepository;
import com.ticketapp.domain.payment.PaymentTransactionStatus;
import com.ticketapp.domain.payment.ProcessedWebhookRepository;
import com.ticketapp.infrastructure.lock.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class PaymentAppService {

    private static final String CONFIRM_LOCK_PREFIX = "LOCK:CONFIRM_ORDER:";
    private static final String TRANSFER_TYPE_IN = "in";

    private final OrderRepository orderRepository;
    private final ProcessedWebhookRepository processedWebhookRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGateway paymentGateway;
    private final DistributedLockService lockService;
    private final SepayProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final BuyPathMetrics metrics;

    public PaymentAppService(OrderRepository orderRepository,
                             ProcessedWebhookRepository processedWebhookRepository,
                             PaymentTransactionRepository paymentTransactionRepository,
                             PaymentGateway paymentGateway,
                             DistributedLockService lockService,
                             SepayProperties properties,
                             PlatformTransactionManager transactionManager,
                             BuyPathMetrics metrics) {
        this.orderRepository = orderRepository;
        this.processedWebhookRepository = processedWebhookRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentGateway = paymentGateway;
        this.lockService = lockService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    public PaymentInstruction pay(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.ORDER_NOT_PAYABLE);
        }
        return paymentGateway.createInstruction(order);
    }

    public WebhookOutcome handleWebhook(SepayWebhookRequest request) {
        Optional<String> memo = PaymentMemo.extract(request.code())
                .or(() -> PaymentMemo.extract(request.content()));
        if (memo.isEmpty()) {
            transactionTemplate.executeWithoutResult(status ->
                    processedWebhookRepository.tryInsert(request.id(), Instant.now()));
            log.warn("sepay webhook unmatched memo txnId={} content={}", request.id(), request.content());
            return WebhookOutcome.UNMATCHED;
        }

        WebhookOutcome[] holder = new WebhookOutcome[1];
        boolean locked = lockService.tryRun(CONFIRM_LOCK_PREFIX + memo.get(),
                properties.getWebhook().getLockWait(), properties.getWebhook().getLockLease(),
                () -> holder[0] = process(request, memo.get()));
        if (!locked) {
            throw new IllegalStateException("could not acquire confirm lock for memo=" + memo.get());
        }

        WebhookOutcome outcome = holder[0];
        if (outcome == WebhookOutcome.CONFIRMED) {
            metrics.paymentConfirmed();
        }
        log.info("sepay webhook processed txnId={} memo={} outcome={}", request.id(), memo.get(), outcome);
        return outcome;
    }

    private WebhookOutcome process(SepayWebhookRequest request, String memo) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            if (!processedWebhookRepository.tryInsert(request.id(), now)) {
                return WebhookOutcome.DUPLICATE;
            }
            if (!TRANSFER_TYPE_IN.equalsIgnoreCase(request.transferType())) {
                return WebhookOutcome.IGNORED;
            }
            Order order = orderRepository.findByPaymentRef(memo).orElse(null);
            if (order == null) {
                return WebhookOutcome.UNMATCHED;
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                order.setRefundRequired(true);
                orderRepository.save(order);
                paymentTransactionRepository.save(record(request, order, PaymentTransactionStatus.REFUND_REQUIRED));
                return WebhookOutcome.REFUND_REQUIRED;
            }
            if (request.transferAmount() != order.getTotalAmount()) {
                paymentTransactionRepository.save(record(request, order, PaymentTransactionStatus.AMOUNT_MISMATCH));
                return WebhookOutcome.AMOUNT_MISMATCH;
            }
            order.transitionTo(OrderStatus.PAID);
            order.setPaidAt(now);
            orderRepository.save(order);
            paymentTransactionRepository.save(record(request, order, PaymentTransactionStatus.CONFIRMED));
            return WebhookOutcome.CONFIRMED;
        });
    }

    private static PaymentTransaction record(SepayWebhookRequest request, Order order,
                                             PaymentTransactionStatus status) {
        return PaymentTransaction.record(request.id(), order.getId(), request.transferAmount(), status,
                request.gateway(), request.transferType(), request.content(),
                request.referenceCode(), request.transactionDate());
    }
}
