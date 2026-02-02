package net.blueshell.api.email.recovery;

import lombok.Getter;
import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;

public abstract class RecoveryEmail extends BaseEmail {

    @Getter
    protected final String token;

    public RecoveryEmail(User recipient, String token, String frontendUrl, String appUrl) {
        super(recipient, frontendUrl, appUrl);
        this.token = token;
    }
}
