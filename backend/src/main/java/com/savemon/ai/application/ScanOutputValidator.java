package com.savemon.ai.application;

import com.savemon.ai.interfaces.ExpenseScanResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Treats extractor output as untrusted evidence, never as a financial command. */
public final class ScanOutputValidator {
    private static final Set<String> CURRENCIES = Set.of("VND", "USD");

    public ExpenseScanResponse validate(BillImageExtraction.ExtractionResult result) {
        if (result == null || result.classification() == null) throw new InvalidExtractionException();
        return switch (result.classification()) {
            case UNRELATED_IMAGE -> noDraft(ExpenseScanResponse.Outcome.UNSUPPORTED,
                    ExpenseScanResponse.ReasonCode.UNRELATED_IMAGE);
            case UNSUPPORTED_BILL_CONTENT -> noDraft(ExpenseScanResponse.Outcome.UNSUPPORTED,
                    ExpenseScanResponse.ReasonCode.UNSUPPORTED_BILL_CONTENT);
            case UNREADABLE_BILL -> noDraft(ExpenseScanResponse.Outcome.MANUAL_ENTRY,
                    ExpenseScanResponse.ReasonCode.UNREADABLE_BILL);
            case RECEIPT -> receipt(result);
        };
    }

    private ExpenseScanResponse receipt(BillImageExtraction.ExtractionResult result) {
        String currency = result.currency() != null && CURRENCIES.contains(result.currency())
                ? result.currency() : null;
        String amount = validAmount(result.paidTotal(), currency) ? result.paidTotal() : null;
        LocalDate date = parseDate(result.transactionDate());
        String merchant = validMerchant(result.merchant()) ? result.merchant() : null;
        if (amount == null && currency == null && date == null && merchant == null)
            return noDraft(ExpenseScanResponse.Outcome.MANUAL_ENTRY,
                    ExpenseScanResponse.ReasonCode.UNREADABLE_BILL);
        List<String> missing = new ArrayList<>();
        if (amount == null) missing.add("paidTotal");
        if (currency == null) missing.add("currency");
        if (date == null) missing.add("transactionDate");
        return new ExpenseScanResponse(ExpenseScanResponse.Outcome.DRAFT,
                new ExpenseScanResponse.Draft(amount, currency, date, merchant, missing), null, true);
    }

    private boolean validAmount(String value, String currency) {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,4})?")) return false;
        try {
            BigDecimal decimal = new BigDecimal(value);
            BigDecimal normalized = decimal.stripTrailingZeros();
            int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
            if (decimal.signum() <= 0 || integerDigits > 15 || normalized.precision() > 19) return false;
            if (currency == null) return decimal.scale() <= 4;
            // Currency-specific precision is enforced only when currency evidence is present.
            int scale = "VND".equals(currency) ? 0 : 2;
            return normalized.scale() <= scale;
        } catch (NumberFormatException exception) { return false; }
    }

    private LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { return null; }
    }

    private boolean validMerchant(String value) {
        return value != null && !value.isBlank() && !value.strip().isEmpty()
                && value.codePointCount(0, value.length()) <= 500;
    }

    private ExpenseScanResponse noDraft(ExpenseScanResponse.Outcome outcome,
                                        ExpenseScanResponse.ReasonCode reason) {
        return new ExpenseScanResponse(outcome, null, reason, true);
    }

    public static final class InvalidExtractionException extends RuntimeException { }
}
