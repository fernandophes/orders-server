package br.edu.ufersa.cc.sd.exceptions;

public class ConnectionException extends RuntimeException {
    
    public ConnectionException(final String message) {
        super(message);
    }

    public ConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(final Throwable cause) {
        super(cause);
    }
}
