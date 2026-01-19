// ============================================================================
// EXCEPTION - InvalidRequestException.java
// ============================================================================
package com.exemple.transactionservice.exception;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}