package com.departementemployee.users.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")//récupère l'adresse email configurée dans application.properties
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
        //to:l'adresse email du destinataire.
        //email : le contenu du message à envoyer.
    @Override
    public void sendEmail(String to, String email) {
        // Gmail non configuré (MAIL_USERNAME vide) : on échoue tout de suite
        // au lieu d'attendre une connexion SMTP qui ne peut pas aboutir.
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("envoi d'email non configuré (MAIL_USERNAME vide)");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setText(email, true);          // true = contenu HTML
            helper.setTo(to);
            helper.setSubject("Confirmez votre email");//Définit le sujet de l'email.
            helper.setFrom(from);//Définit l'expéditeur.
            mailSender.send(mimeMessage);//Envoie réellement le message.
        } catch (MessagingException e) {
            throw new IllegalStateException("failed to send email");
        }
    }
}