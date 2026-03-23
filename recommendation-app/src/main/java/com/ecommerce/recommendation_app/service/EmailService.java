package com.ecommerce.recommendation_app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPinEmail(String toEmail, String pin) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("ShopSmart - Password Reset PIN");
        message.setText(
            "Hi there!\n\n" +
            "You requested to reset your ShopSmart password.\n\n" +
            "Your 4-digit PIN is: " + pin + "\n\n" +
            "This PIN will expire in 10 minutes.\n\n" +
            "If you did not request this, please ignore this email.\n\n" +
            "Thanks,\nShopSmart Team"
        );
        mailSender.send(message);
    }
}