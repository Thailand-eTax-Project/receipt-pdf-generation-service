package com.wpanther.receipt.pdf.domain.constants;

public final class PdfGenerationConstants {

    private PdfGenerationConstants() {}

    public static final String DOCUMENT_TYPE = "RECEIPT";
    public static final String S3_KEY_PREFIX = "receipt-";
    public static final String PDF_FILE_EXTENSION = ".pdf";
    public static final String DEFAULT_MIME_TYPE = "application/pdf";

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_MAX_PDF_SIZE_BYTES = 52_428_800L; // 50 MB
}
