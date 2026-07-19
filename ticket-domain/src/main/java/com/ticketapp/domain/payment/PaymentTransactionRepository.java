package com.ticketapp.domain.payment;

import java.util.List;

public interface PaymentTransactionRepository {

    PaymentTransaction save(PaymentTransaction transaction);

    List<PaymentTransaction> findByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
