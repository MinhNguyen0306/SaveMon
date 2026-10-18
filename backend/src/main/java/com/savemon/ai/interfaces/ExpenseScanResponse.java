package com.savemon.ai.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Allowlisted transient response shape. Constructors enforce mutually exclusive
 * outcomes and field-level output constraints before serialization.
 */
public record ExpenseScanResponse(
        Outcome outcome,
        Draft draft,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReasonCode reasonCode,
        boolean manualEntryAllowed) {

    public ExpenseScanResponse {
        if (outcome == null || !manualEntryAllowed) {
            throw new IllegalArgumentException("A valid scan outcome is required.");
        }
        if (outcome == Outcome.DRAFT) {
            if (draft == null || reasonCode != null) {
                throw new IllegalArgumentException("DRAFT requires a draft and no reason code.");
            }
        } else if (draft != null || reasonCode == null) {
            throw new IllegalArgumentException("A null-draft outcome requires a reason code.");
        }
        if (outcome == Outcome.MANUAL_ENTRY && reasonCode != ReasonCode.UNREADABLE_BILL) {
            throw new IllegalArgumentException("MANUAL_ENTRY requires an unreadable bill reason.");
        }
        if (outcome == Outcome.UNSUPPORTED && reasonCode == ReasonCode.UNREADABLE_BILL) {
            throw new IllegalArgumentException("UNREADABLE_BILL requires MANUAL_ENTRY.");
        }
    }

    public enum Outcome {
        DRAFT,
        UNSUPPORTED,
        MANUAL_ENTRY
    }

    public enum ReasonCode {
        PAYMENT_QR,
        UNSUPPORTED_QR_FORMAT,
        UNRELATED_IMAGE,
        UNSUPPORTED_BILL_CONTENT,
        UNREADABLE_BILL
    }

    public record Draft(
            String paidTotal,
            String currency,
            LocalDate transactionDate,
            String merchant,
            List<String> missingOrAmbiguousFields) {

        private static final Set<String> REQUIRED_FIELDS =
                Set.of("paidTotal", "transactionDate", "currency");

        public Draft {
            validatePaidTotal(paidTotal);
            if (currency != null && !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("currency must be a three-letter uppercase code.");
            }
            if (merchant != null && merchant.codePointCount(0, merchant.length()) > 500) {
                throw new IllegalArgumentException("merchant is too long.");
            }
            if (paidTotal == null && currency == null && transactionDate == null && merchant == null) {
                throw new IllegalArgumentException("A draft must contain a meaningful extracted field.");
            }
            if (missingOrAmbiguousFields == null
                    || !REQUIRED_FIELDS.containsAll(missingOrAmbiguousFields)
                    || missingOrAmbiguousFields.stream().distinct().count() != missingOrAmbiguousFields.size()) {
                throw new IllegalArgumentException("missingOrAmbiguousFields contains an unsupported field.");
            }
            missingOrAmbiguousFields = List.copyOf(missingOrAmbiguousFields);
            if ((paidTotal == null) != missingOrAmbiguousFields.contains("paidTotal")
                    || (currency == null) != missingOrAmbiguousFields.contains("currency")
                    || (transactionDate == null) != missingOrAmbiguousFields.contains("transactionDate")) {
                throw new IllegalArgumentException("Missing required values must be listed for correction.");
            }
        }

        private static void validatePaidTotal(String paidTotal) {
            if (paidTotal == null) {
                return;
            }
            if (!paidTotal.matches("[0-9]+(?:\\.[0-9]{1,4})?")) {
                throw new IllegalArgumentException("paidTotal must be a plain decimal string.");
            }
            BigDecimal amount = new BigDecimal(paidTotal);
            BigDecimal normalized = amount.stripTrailingZeros();
            int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
            if (amount.signum() <= 0 || amount.scale() > 4
                    || integerDigits > 15 || normalized.precision() > 19) {
                throw new IllegalArgumentException("paidTotal is outside the supported numeric range.");
            }
        }
    }
}
