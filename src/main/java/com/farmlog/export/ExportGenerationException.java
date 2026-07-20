package com.farmlog.export;

public class ExportGenerationException extends RuntimeException {
    private final String code;
    private final String safeMessage;
    private final boolean retryable;
    public ExportGenerationException(String code,String safeMessage,boolean retryable,Throwable cause){super(safeMessage,cause);this.code=code;this.safeMessage=safeMessage;this.retryable=retryable;}
    public String code(){return code;}
    public String safeMessage(){return safeMessage;}
    public boolean retryable(){return retryable;}
}
