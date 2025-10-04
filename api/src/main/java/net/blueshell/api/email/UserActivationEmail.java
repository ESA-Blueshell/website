package net.blueshell.api.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;

public class UserActivationEmail extends BaseEmail {

    public UserActivationEmail(User recipient, String frontendUrl, String appUrl) {
        super(recipient, frontendUrl, appUrl);
    }

    @Override
    public String getSubject() {
        return "Activate your Account";
    }

    @Override
    public String getMarkdownContent() {
        String activationLink = String.format(
                frontendUrl + "/account/activate/user/%s/%s",
                recipient.getUsername(),
                recipient.getResetKey()
        );

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