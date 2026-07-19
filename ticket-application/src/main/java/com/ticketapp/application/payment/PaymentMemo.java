package com.ticketapp.application.payment;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentMemo {

    public static final String PREFIX = "TKT";

    private static final Pattern PATTERN = Pattern.compile("TKT[A-Z0-9]{12}", Pattern.CASE_INSENSITIVE);

    private PaymentMemo() {
    }

    public static Optional<String> extract(String content) {
        if (content == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(content);
        if (matcher.find()) {
            return Optional.of(matcher.group().toUpperCase());
        }
        return Optional.empty();
    }
}
