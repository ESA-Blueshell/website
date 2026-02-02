package net.blueshell.api.email.recovery;

import net.blueshell.api.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MemberActivationEmail extends RecoveryEmail {

    public MemberActivationEmail(User recipient, String token, String frontendUrl, String appUrl) {
        super(recipient, token, frontendUrl, appUrl);
    }

    @Override
    public String getSubject() {
        return "Activate your Account";
    }

    @Override
    public String getMarkdownContent() {
        String token = URLEncoder.encode(getToken(), StandardCharsets.UTF_8);
        String activationLink = String.format("%s/account/activate/member?token=%s", frontendUrl, token);

        return String.format("""
                        Dear %s,
                        
                        A member of the board of Blueshell has created an account on the website for you. This was done
                        for administrative purposes and you do not need to take any action. You can use [this link](%s)
                        to activate your account on the website which can be done within 7 days of receiving this email.
                        If you do not activate your account in time you can contact a member of the board, or send a
                        message in the SiteCie suggestion channel of our discord to have a new activation email sent to
                        you :)
                        
                        Kind regards,
                        Board of ESA Blueshell
                        """,
                recipient.getFullName(),
                activationLink
        );
    }

    @Override
    public String getReplyTo() {
        return "board@blueshell.utwente.nl";
    }
}
