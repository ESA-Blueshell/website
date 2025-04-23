package net.blueshell.exception;

/**
 * Exception thrown when a user does not have the necessary permissions to perform an action.
 */
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super("Permission denied: " + message);
    }
}
