package net.blueshell.exception;

/**
 * Exception thrown when the provided form data is invalid.
 */
public class InvalidFormException extends RuntimeException {
    public InvalidFormException(String message) {
        super("Invalid form data: " + message);
    }
}
