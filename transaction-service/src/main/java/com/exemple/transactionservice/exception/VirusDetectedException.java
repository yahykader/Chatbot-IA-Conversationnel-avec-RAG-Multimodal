package com.exemple.transactionservice.exception;
    
/**
* Exception levée lorsqu'un virus est détecté dans un fichier.
*/
public class VirusDetectedException extends AntivirusScanException {

    private final String virusName;

    public VirusDetectedException(String virusName) {
            super("Virus détecté: " + virusName);
            this.virusName = virusName;
    }

    public String getVirusName() {
            return virusName;
    }
}