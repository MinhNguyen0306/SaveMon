package com.savemon.ai.application;

import com.savemon.ai.interfaces.ExpenseScanResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanOutputValidatorTest {
    private final ScanOutputValidator validator = new ScanOutputValidator();

    @Test void nullsAmbiguousFinancialFieldsAndListsThem() {
        var response = validator.validate(new BillImageExtraction.ExtractionResult(
                "100.5", "VND", "2026-02-30", "Cafe"));
        assertThat(response.draft().paidTotal()).isNull();
        assertThat(response.draft().transactionDate()).isNull();
        assertThat(response.draft().missingOrAmbiguousFields())
                .containsExactly("paidTotal", "transactionDate");
    }

    @Test void rejectsUnknownCurrencyWithoutInventingOne() {
        var response = validator.validate(new BillImageExtraction.ExtractionResult(
                "99", "EUR", "2026-10-17", "Cafe"));
        assertThat(response.draft().currency()).isNull();
        assertThat(response.draft().missingOrAmbiguousFields()).containsExactly("currency");
    }

    @Test void entirelyUnreadableIsManualEntryAndUnrelatedIsUnsupported() {
        assertThat(validator.validate(new BillImageExtraction.ExtractionResult(
                null, null, null, null)).outcome())
                .isEqualTo(ExpenseScanResponse.Outcome.MANUAL_ENTRY);
        assertThat(validator.validate(new BillImageExtraction.ExtractionResult(
                null, null, null, null, BillImageExtraction.Classification.UNRELATED_IMAGE)).reasonCode())
                .isEqualTo(ExpenseScanResponse.ReasonCode.UNRELATED_IMAGE);
    }
}
