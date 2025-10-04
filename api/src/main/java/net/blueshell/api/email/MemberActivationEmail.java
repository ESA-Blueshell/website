package net.blueshell.api.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;

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
        String activationLink = String.format(
                frontendUrl + "/account/activate/member/%s",
                recipient.getResetKey()
        );

        return String.format(
                """
                        Dear %s,
                        
                        Welcome to Blueshell Esports as a member!
                        Please activate your member account by clicking on [this link](%s).
                        
                        As a member, you now have access to:
                        - Exclusive member events
                        - The full Blueshell Esports discord
                        - The full Blueshell Esports website
                        
                        For any questions, feel free to reach out to us via [discord](https://discord.gg/dFam2yqXu7) or our [website](%s).
                        
                        Please do not reply to this email, as this is a generated email. Any responses will be ignored.
                        
                        Kind regards,
                        Blueshell Esports
                        """,
                recipient.getFullName(),
                activationLink,
                appUrl
        );
    }
}