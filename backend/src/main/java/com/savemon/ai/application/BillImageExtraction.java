package com.savemon.ai.application;

/**
 * Unimplemented, fail-closed integration seam. No provider adapter is approved or
 * registered; callers must not substitute another extraction mechanism.
 */
public interface BillImageExtraction {

    ExtractionResult extract(byte[] sanitizedImageBytes);

    record ExtractionResult(
            String paidTotal,
            String currency,
            String transactionDate,
            String merchant,
            Classification classification) {

        public ExtractionResult(String paidTotal, String currency, String transactionDate, String merchant) {
            this(paidTotal, currency, transactionDate, merchant, Classification.RECEIPT);
        }
    }

    enum Classification { RECEIPT, UNRELATED_IMAGE, UNSUPPORTED_BILL_CONTENT, UNREADABLE_BILL }
}
