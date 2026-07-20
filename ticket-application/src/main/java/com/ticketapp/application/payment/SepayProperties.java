package com.ticketapp.application.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

@ConfigurationProperties(prefix = "sepay")
public class SepayProperties {

    private String accountNumber = "";
    private String bankCode = "";
    private String qrBaseUrl = "https://qr.sepay.vn/img";

    @NestedConfigurationProperty
    private Webhook webhook = new Webhook();

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getQrBaseUrl() {
        return qrBaseUrl;
    }

    public void setQrBaseUrl(String qrBaseUrl) {
        this.qrBaseUrl = qrBaseUrl;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public static class Webhook {

        private String secret = "";
        private boolean requireSignature = true;
        private Duration timestampTolerance = Duration.ofMinutes(5);
        private Duration lockWait = Duration.ofSeconds(3);
        private Duration lockLease = Duration.ofSeconds(10);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public boolean isRequireSignature() {
            return requireSignature;
        }

        public void setRequireSignature(boolean requireSignature) {
            this.requireSignature = requireSignature;
        }

        public Duration getTimestampTolerance() {
            return timestampTolerance;
        }

        public void setTimestampTolerance(Duration timestampTolerance) {
            this.timestampTolerance = timestampTolerance;
        }

        public Duration getLockWait() {
            return lockWait;
        }

        public void setLockWait(Duration lockWait) {
            this.lockWait = lockWait;
        }

        public Duration getLockLease() {
            return lockLease;
        }

        public void setLockLease(Duration lockLease) {
            this.lockLease = lockLease;
        }
    }
}
