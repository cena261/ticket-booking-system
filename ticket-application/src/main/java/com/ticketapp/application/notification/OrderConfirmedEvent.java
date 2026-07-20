package com.ticketapp.application.notification;

import java.util.List;

public record OrderConfirmedEvent(String orderNumber, String recipientEmail, long totalAmount,
                                  List<String> ticketCodes) {
}
