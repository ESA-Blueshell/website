package net.blueshell.api.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MemberActivationEmail extends BaseEmail {

    public MemberActivationEmail(User recipient, String frontendUrl, String appUrl) {
        super(recipient, frontendUrl, appUrl);
    }

    @Override
    public String getSubject() {
        return "Welcome to Blueshell - Member Account Activation";
    }

    @Override
    public String getMarkdownContent() {
        String token = URLEncoder.encode(recipient.getResetKey(), StandardCharsets.UTF_8);
        String activationLink = String.format("%s/account/activate/member?token=%s", frontendUrl, token);

        return String.format(
                """
                        Dear %s,
                        
                        Welcome to Blueshell Esports as a member!
                        Please activate your member account by clicking on [this link](%s).
                        
                        As a member, you now have access to:
                        - Exclusive member events
                        - The full Blueshell Esports discord
                        - The full Blueshell Esports website
                        
                        For any questions, feel free to reach out to us via [discord](https://discord.gg/dFam2yqXu7), our [website](%s), or by replying to this mail.
                        
                        Kind regards,
                        Blueshell Esports
                        """,
                recipient.getFullName(),
                activationLink,
                appUrl
        );
    }

    @Override
    public String getReplyTo() {
        return "board@blueshell.utwente.nl";
    }
}
