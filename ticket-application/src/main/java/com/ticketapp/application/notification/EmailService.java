package com.ticketapp.application.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailDispatcher dispatcher;
    private final String from;

    public EmailService(JavaMailSender mailSender, EmailDispatcher dispatcher, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.dispatcher = dispatcher;
        this.from = properties.getFrom();
    }

    public void sendOrderConfirmed(OrderConfirmedEvent event) {
        if (!hasRecipient(event.recipientEmail())) {
            return;
        }
        SimpleMailMessage message = message(event.recipientEmail(),
                EmailTemplates.confirmedSubject(event), EmailTemplates.confirmedBody(event));
        dispatcher.dispatch("confirmed:" + event.orderNumber(), () -> mailSender.send(message));
    }

    public void sendOrderCancelled(OrderCancelledEvent event) {
        if (!hasRecipient(event.recipientEmail())) {
            return;
        }
        SimpleMailMessage message = message(event.recipientEmail(),
                EmailTemplates.cancelledSubject(event), EmailTemplates.cancelledBody(event));
        dispatcher.dispatch("cancelled:" + event.orderNumber(), () -> mailSender.send(message));
    }

    private SimpleMailMessage message(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        return message;
    }

    private static boolean hasRecipient(String email) {
        return email != null && !email.isBlank();
    }
}
