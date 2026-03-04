
package com.exemple.transactionservice.exception;

    /**
     * Exception levée lors d'un problème de scan antivirus.
     */
public  class AntivirusScanException extends Exception {
    
    public AntivirusScanException(String message) {
        super(message);
    }

    public AntivirusScanException(String message, Throwable cause) {
        super(message, cause);
    }
}