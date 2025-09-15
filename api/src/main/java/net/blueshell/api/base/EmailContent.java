
package net.blueshell.api.base;

import net.blueshell.api.model.User;

public record EmailContent(
    User recipient,
    String subject,
    String markdownContent,
    String senderName,
    String senderAddress
) {}