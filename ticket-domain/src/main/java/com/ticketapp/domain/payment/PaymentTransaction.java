package com.ticketapp.domain.payment;

import com.ticketapp.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_transaction")
@Getter
@NoArgsConstructor
public class PaymentTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sepay_txn_id", nullable = false, unique = true)
    private long sepayTxnId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "gateway")
    private String gateway;

    @Column(name = "transfer_type", length = 8)
    private String transferType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "reference_code", length = 64)
    private String referenceCode;

    @Column(name = "transaction_date", length = 32)
    private String transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentTransactionStatus status;

    public static PaymentTransaction record(long sepayTxnId, Long orderId, long amount,
                                            PaymentTransactionStatus status, String gateway, String transferType,
                                            String content, String referenceCode, String transactionDate) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.sepayTxnId = sepayTxnId;
        tx.orderId = orderId;
        tx.amount = amount;
        tx.status = status;
        tx.gateway = gateway;
        tx.transferType = transferType;
        tx.content = content;
        tx.referenceCode = referenceCode;
        tx.transactionDate = transactionDate;
        return tx;
    }
}
