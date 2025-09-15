
package net.blueshell.api.base;

import lombok.Getter;
import net.blueshell.api.model.User;

public abstract class BaseEmail {

    @Getter
    protected final User recipient;
    protected final String frontendUrl;
    protected final String appUrl;
    
    public BaseEmail(User recipient, String frontendUrl, String appUrl) {
        this.recipient = recipient;
        this.frontendUrl = frontendUrl;
        this.appUrl = appUrl;
    }
    
    /**
     * Get the email subject line
     */
    public abstract String getSubject();
    
    /**
     * Generate the markdown content for the email
     */
    public abstract String getMarkdownContent();
    
    /**
     * Get the sender name for this email type
     */
    public String getSenderName() {
        return "ESA Blueshell";
    }
    
    /**
     * Get the sender email address
     */
    public String getSenderAddress() {
        return "sitecie@blueshell.utwente.nl";
    }

    /**
     * Template method for building the complete email content
     */
    public final EmailContent buildEmailContent() {
        return new EmailContent(
            recipient,
            getSubject(),
            getMarkdownContent(),
            getSenderName(),
            getSenderAddress()
        );
    }
}