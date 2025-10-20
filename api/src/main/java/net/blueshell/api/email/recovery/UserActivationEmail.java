package net.blueshell.api.email.recovery;

import net.blueshell.api.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UserActivationEmail extends RecoveryEmail {

    public UserActivationEmail(User recipient, String token, String frontendUrl, String appUrl) {
        super(recipient, token, frontendUrl, appUrl);
    }

    @Override
    public String getSubject() {
        return "Activate your Account";
    }

    @Override
    public String getMarkdownContent() {
        String username = URLEncoder.encode(recipient.getUsername(), StandardCharsets.UTF_8);
        String token = URLEncoder.encode(getToken(), StandardCharsets.UTF_8);
        String activationLink = String.format("%s/account/activate/user?username=%s&token=%s", frontendUrl, username, token);

        return String.format(
                """
                        Dear %s,
                        
                        Thank you for signing up to the Blueshell website!
                        
                        You can activate your account by clicking on [this link](%s).
                        For information on events and our general community, check out our [discord](https://discord.gg/dFam2yqXu7) or [website](%s).
                        Enjoy your stay!
                        
                        Please do not reply to this email, as this is a generated email. Any responses will be ignored.
                        
                        Kind regards, <br/>
                        Blueshell Esports
                        """,
                recipient.getFullName(),
                activationLink,
                appUrl
        );
    }
}
