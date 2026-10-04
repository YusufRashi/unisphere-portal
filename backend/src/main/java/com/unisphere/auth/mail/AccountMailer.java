package com.unisphere.auth.mail;

import com.unisphere.user.User;

/**
 * Port for account emails. The default implementation only logs; an SMTP or provider-API implementation
 * (e.g. Spring Mail, SES) can replace it without touching the auth logic.
 */
public interface AccountMailer {

    void sendActivationLink(User user, String link);

    void sendPasswordResetLink(User user, String link);
}
