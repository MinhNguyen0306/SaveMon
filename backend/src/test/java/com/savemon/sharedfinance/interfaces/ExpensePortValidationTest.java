package com.savemon.sharedfinance.interfaces;

import com.savemon.personalfinance.application.PersonalPosting;
import com.savemon.personalfinance.interfaces.PersonalExpensePostingBridge;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import com.savemon.sharedfinance.application.SharedLedgerCommands;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpensePortValidationTest {
    private static final Instant PAST = Instant.parse("2020-01-01T00:00:00Z");
    private final AtomicInteger writes = new AtomicInteger();
    private final PersonalExpensePostingBridge personal = new PersonalExpensePostingBridge(
            new PersonalPosting((subject, type, key, command) -> {
                writes.incrementAndGet(); return new PersonalPosting.Receipt(201, "\"0\"", "{}");
            }));
    private final SharedExpensePostingBridge shared = new SharedExpensePostingBridge(
            new SharedLedgerCommands((actor, vault, key, expense) -> {
                writes.incrementAndGet(); return new SharedLedgerCommands.Receipt(201, java.util.Map.of(), "{}");
            }));

    @Test void malformedPersonalItemIsValidationErrorBeforeImmutableCopyOrWrite() {
        var nullItem = new PersonalExpensePostingBridge.TransactionCreate(UUID.randomUUID(),
                new BigDecimal("100"), "VND", PAST, "DATE_ONLY", null, listWithNull(), null);
        assertBusiness(() -> personal.record(UUID.randomUUID(), UUID.randomUUID(), nullItem),
                "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        var missingCategory = new PersonalExpensePostingBridge.TransactionCreate(UUID.randomUUID(),
                new BigDecimal("100"), "VND", PAST, "DATE_ONLY", null,
                List.of(new PersonalExpensePostingBridge.Item(null, new BigDecimal("100"), null)), null);
        assertBusiness(() -> personal.record(UUID.randomUUID(), UUID.randomUUID(), missingCategory),
                "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        assertThat(writes).hasValue(0);
    }

    @Test void personalItemSumUsesCanonicalMismatchError() {
        var request = new PersonalExpensePostingBridge.TransactionCreate(UUID.randomUUID(),
                new BigDecimal("100"), "VND", PAST, "DATE_ONLY", null,
                List.of(new PersonalExpensePostingBridge.Item(UUID.randomUUID(),
                        new BigDecimal("99"), null)), null);
        assertBusiness(() -> personal.record(UUID.randomUUID(), UUID.randomUUID(), request),
                "TRANSACTION_ITEM_TOTAL_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(writes).hasValue(0);
    }

    @Test void sharedFutureDateAndSplitTotalUseDistinctCanonicalErrors() {
        UUID member = UUID.randomUUID();
        var future = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                Instant.parse("2099-01-01T00:00:00Z"), "DATE_ONLY",
                new SharedExpensePostingBridge.Funding("VAULT", null, null),
                List.of(new SharedExpensePostingBridge.Split(member, new BigDecimal("100"))), null);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, future),
                "FUTURE_DATE_NOT_ALLOWED", HttpStatus.UNPROCESSABLE_ENTITY);
        var mismatch = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                PAST, "DATE_ONLY", new SharedExpensePostingBridge.Funding("VAULT", null, null),
                List.of(new SharedExpensePostingBridge.Split(member, new BigDecimal("99"))), null);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, mismatch),
                "EXPENSE_SPLIT_TOTAL_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(writes).hasValue(0);
    }

    @Test void nullSplitAndMalformedDerivationNeverReachStore() {
        var nullSplit = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                PAST, "DATE_ONLY", new SharedExpensePostingBridge.Funding("VAULT", null, null),
                listWithNull(), null);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, nullSplit),
                "SHARED_EXPENSE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        var derivation = new SharedLedgerCommands.Derivation("wrong-version", "EQUAL",
                new SharedLedgerCommands.SplitRequest(new BigDecimal("100"), "VND", "EQUAL",
                        List.of(new SharedLedgerCommands.Participant(UUID.randomUUID(), null, null)), null));
        var malformed = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                PAST, "DATE_ONLY", new SharedExpensePostingBridge.Funding("VAULT", null, null),
                List.of(new SharedExpensePostingBridge.Split(UUID.randomUUID(), new BigDecimal("100"))),
                derivation);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, malformed),
                "SPLIT_INPUT_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        var missingParticipants = new SharedLedgerCommands.Derivation("largest-remainder-v1", "EQUAL",
                new SharedLedgerCommands.SplitRequest(new BigDecimal("100"), "VND", "EQUAL", null, null));
        var nestedMalformed = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                PAST, "DATE_ONLY", new SharedExpensePostingBridge.Funding("VAULT", null, null),
                List.of(new SharedExpensePostingBridge.Split(UUID.randomUUID(), new BigDecimal("100"))),
                missingParticipants);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, nestedMalformed),
                "SPLIT_INPUT_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(writes).hasValue(0);
    }

    @Test void malformedIfMatchIsValidationErrorBeforeStore() {
        var request = new SharedExpensePostingBridge.ExpenseCreate(new BigDecimal("100"), "VND", null,
                PAST, "INSTANT", new SharedExpensePostingBridge.Funding("VAULT", null, null),
                List.of(new SharedExpensePostingBridge.Split(UUID.randomUUID(), new BigDecimal("100"))), null);
        assertBusiness(() -> shared.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "5", request),
                "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        assertThat(writes).hasValue(0);
    }

    private static <T> List<T> listWithNull() {
        var values = new java.util.ArrayList<T>();
        values.add(null);
        return values;
    }

    private static void assertBusiness(Runnable action, String code, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessApiException.class)
                .satisfies(error -> {
                    var business = (BusinessApiException) error;
                    assertThat(business.code()).isEqualTo(code);
                    assertThat(business.status()).isEqualTo(status);
                });
    }
}
