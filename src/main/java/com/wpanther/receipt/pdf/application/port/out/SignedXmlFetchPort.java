package com.wpanther.receipt.pdf.application.port.out;

public interface SignedXmlFetchPort {

    String fetch(String url);

    class SignedXmlFetchException extends RuntimeException {
        public SignedXmlFetchException(String message) { super(message); }
        public SignedXmlFetchException(String message, Throwable cause) { super(message, cause); }
    }
}
