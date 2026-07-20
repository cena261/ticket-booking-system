package com.ticketapp.application.notification;

final class EmailTemplates {

    private EmailTemplates() {
    }

    static String confirmedSubject(OrderConfirmedEvent event) {
        return "Your tickets for order " + event.orderNumber();
    }

    static String confirmedBody(OrderConfirmedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("Your payment for order ").append(event.orderNumber()).append(" is confirmed.\n");
        body.append("Amount paid: ").append(event.totalAmount()).append(" VND\n\n");
        body.append("Your e-ticket code(s):\n");
        for (String code : event.ticketCodes()) {
            body.append("  ").append(code).append('\n');
        }
        body.append("\nPresent the code at the venue entrance.\n");
        return body.toString();
    }

    static String cancelledSubject(OrderCancelledEvent event) {
        return "Order " + event.orderNumber() + " " + event.reason().toLowerCase();
    }

    static String cancelledBody(OrderCancelledEvent event) {
        return "Order " + event.orderNumber() + " was " + event.reason().toLowerCase()
                + " and its tickets were released.\n"
                + "If you were charged, a refund will be arranged.\n";
    }
}
