package com.ticketapp.infrastructure.persistence.payment;

import com.ticketapp.domain.payment.PaymentTransaction;
import com.ticketapp.domain.payment.PaymentTransactionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionJpaRepository
        extends PaymentTransactionRepository, JpaRepository<PaymentTransaction, Long> {
}
