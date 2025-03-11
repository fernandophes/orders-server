package br.edu.ufersa.cc.sd.exceptions;

public class OperationException extends RuntimeException {

    public OperationException(final String message) {
        super(message);
    }

    public OperationException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
