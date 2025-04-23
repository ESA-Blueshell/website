package net.blueshell.exception;

/**
 * Exception thrown when a required parameter is missing in the request.
 */
public class MissingParameterException extends RuntimeException {
    public MissingParameterException(String parameterName) {
        super("Missing required parameter: " + parameterName);
    }
}
