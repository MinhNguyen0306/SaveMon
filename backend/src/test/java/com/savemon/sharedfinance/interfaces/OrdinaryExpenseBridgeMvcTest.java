package com.savemon.sharedfinance.interfaces;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.personalfinance.application.PersonalFinanceOperations;
import com.savemon.personalfinance.application.PersonalPosting;
import com.savemon.personalfinance.interfaces.PersonalExpensePostingBridge;
import com.savemon.personalfinance.interfaces.PersonalFinanceController;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import com.savemon.sharedfinance.application.CommandJournal;
import com.savemon.sharedfinance.application.SharedFinanceOperations;
import com.savemon.sharedfinance.application.SharedLedgerCommands;
import com.savemon.sharedfinance.application.SplitPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Same-path standalone MVC sketch; intentionally never registered in the application context. */
class OrdinaryExpenseBridgeMvcTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private final ObjectMapper strict = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    @Test void personalDateOnlyAndOmittedPrecisionPreserveExactAmountAndImmutableReceipt() throws Exception {
        AtomicReference<PersonalPosting.Command> seen = new AtomicReference<>();
        AtomicReference<UUID> seenKey = new AtomicReference<>();
        PersonalPosting.Store store = (subject, type, key, command) -> {
            assertThat(subject).isEqualTo(ACTOR);
            assertThat(type).isEqualTo("EXPENSE");
            seen.set(command); seenKey.set(key);
            return new PersonalPosting.Receipt(201, "\"0\"", "{\"id\":\"receipt-1\"}");
        };
        var mvc = MockMvcBuilders.standaloneSetup(new TestController(
                new PersonalExpensePostingBridge(new PersonalPosting(store)),
                new SharedExpensePostingBridge(new SharedLedgerCommands(new NoopSharedStore())), strict)).build();
        UUID key = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        String dated = """
                {"accountId":"%s","amount":100.00,"currency":"VND",
                 "transactionDate":"2020-01-02T00:00:00Z","datePrecision":"DATE_ONLY",
                 "items":[{"categoryId":"%s","amount":100,"description":"Lunch"}]}
                """.formatted(account, category);
        mvc.perform(post("/api/v1/transactions/expense").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key).content(dated))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(content().json("{\"id\":\"receipt-1\"}"));
        assertThat(seenKey.get()).isEqualTo(key);
        assertThat(seen.get().amount()).isEqualByComparingTo("100.00");
        assertThat(seen.get().transactionDate()).isEqualTo(Instant.parse("2020-01-02T12:00:00Z"));
        assertThat(seen.get().datePrecision()).isEqualTo("DATE_ONLY");
        assertThat(seen.get().items()).hasSize(1);
        assertThat(seen.get().items().get(0).categoryId()).isEqualTo(category);

        String undated = """
                {"accountId":"%s","amount":1.25,"currency":"USD",
                 "transactionDate":"2020-01-02T09:30:00Z"}
                """.formatted(account);
        mvc.perform(post("/api/v1/transactions/expense").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID()).content(undated))
                .andExpect(status().isCreated());
        assertThat(seen.get().datePrecision()).isEqualTo("INSTANT");
        assertThat(seen.get().transactionDate()).isEqualTo(Instant.parse("2020-01-02T09:30:00Z"));
        assertThat(seen.get().items()).isEmpty();
    }

    @Test void sharedExplicitFundingSplitsDerivationAndExpectedVersionReachCoreBoundary() throws Exception {
        AtomicReference<SharedLedgerCommands.Expense> seen = new AtomicReference<>();
        AtomicReference<UUID> seenKey = new AtomicReference<>();
        SharedLedgerCommands.Store store = new NoopSharedStore() {
            @Override public CommandJournal.Receipt recordExpense(UUID actor, UUID vault, UUID key,
                                                                    SharedLedgerCommands.Expense input) {
                assertThat(actor).isEqualTo(ACTOR);
                seen.set(input); seenKey.set(key);
                return new CommandJournal.Receipt(201, Map.of("ETag", "\"6\""),
                        "{\"id\":\"shared-receipt\"}");
            }
        };
        var mvc = MockMvcBuilders.standaloneSetup(new TestController(
                new PersonalExpensePostingBridge(new PersonalPosting((a, b, c, d) -> null)),
                new SharedExpensePostingBridge(new SharedLedgerCommands(store)), strict)).build();
        UUID vault = UUID.randomUUID(), payer = UUID.randomUUID(), account = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), key = UUID.randomUUID();
        String body = """
                {"amount":10.00,"currency":"USD","description":"Dinner",
                 "expenseDate":"2020-01-02T00:00:00Z","datePrecision":"DATE_ONLY",
                 "funding":{"type":"MEMBER","payerMemberId":"%s","sourceAccountId":"%s"},
                 "splits":[{"memberId":"%s","amount":5.00},{"memberId":"%s","amount":5.00}],
                 "splitDerivation":{"algorithmVersion":"largest-remainder-v1","mode":"EQUAL",
                   "input":{"amount":10.00,"currency":"USD","mode":"EQUAL",
                     "participants":[{"memberId":"%s"},{"memberId":"%s"}]}}}
                """.formatted(payer, account, first, second, first, second);
        mvc.perform(post("/api/v1/vaults/{vaultId}/expenses", vault).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key).header("If-Match", "\"5\"").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"6\""))
                .andExpect(content().json("{\"id\":\"shared-receipt\"}"));
        assertThat(seenKey.get()).isEqualTo(key);
        assertThat(seen.get().amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(seen.get().expenseDate()).isEqualTo(Instant.parse("2020-01-02T12:00:00Z"));
        assertThat(seen.get().datePrecision()).isEqualTo("DATE_ONLY");
        assertThat(seen.get().funding()).isEqualTo("MEMBER");
        assertThat(seen.get().payerMemberId()).isEqualTo(payer);
        assertThat(seen.get().sourceAccountId()).isEqualTo(account);
        assertThat(seen.get().splits()).hasSize(2);
        assertThat(seen.get().splitDerivation().algorithmVersion()).isEqualTo(SplitPreviewService.VERSION);
        assertThat(seen.get().expectedVaultVersion()).isEqualTo(5L);
    }

    @Test void strictStandaloneBindingRejectsUnknownAndDuplicateFields() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestController(
                new PersonalExpensePostingBridge(new PersonalPosting((a, b, c, d) -> null)),
                new SharedExpensePostingBridge(new SharedLedgerCommands(new NoopSharedStore())), strict)).build();
        UUID account = UUID.randomUUID();
        String prefix = "{\"accountId\":\"" + account + "\",\"amount\":1,\"currency\":\"VND\","
                + "\"transactionDate\":\"2020-01-01T00:00:00Z\"";
        mvc.perform(post("/api/v1/transactions/expense").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID()).content(prefix + ",\"unknown\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transactions/expense").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID()).content(prefix + ",\"amount\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test void sharedOmittedPrecisionAndIfMatchStayInstantAndUnversioned() throws Exception {
        AtomicReference<SharedLedgerCommands.Expense> seen = new AtomicReference<>();
        SharedLedgerCommands.Store store = new NoopSharedStore() {
            @Override public CommandJournal.Receipt recordExpense(UUID actor, UUID vault, UUID key,
                                                                    SharedLedgerCommands.Expense input) {
                seen.set(input);
                return new CommandJournal.Receipt(201, Map.of(), "{}");
            }
        };
        var mvc = MockMvcBuilders.standaloneSetup(new TestController(
                new PersonalExpensePostingBridge(new PersonalPosting((a, b, c, d) -> null)),
                new SharedExpensePostingBridge(new SharedLedgerCommands(store)), strict)).build();
        UUID member = UUID.randomUUID();
        String body = """
                {"amount":100,"currency":"VND","expenseDate":"2020-01-02T09:30:00Z",
                 "funding":{"type":"VAULT"},
                 "splits":[{"memberId":"%s","amount":100}]}
                """.formatted(member);
        mvc.perform(post("/api/v1/vaults/{vaultId}/expenses", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID()).content(body))
                .andExpect(status().isCreated());
        assertThat(seen.get().datePrecision()).isEqualTo("INSTANT");
        assertThat(seen.get().expenseDate()).isEqualTo(Instant.parse("2020-01-02T09:30:00Z"));
        assertThat(seen.get().expectedVaultVersion()).isNull();
        assertThat(seen.get().splitDerivation()).isNull();
        assertThat(seen.get().payerMemberId()).isNull();
        assertThat(seen.get().sourceAccountId()).isNull();
    }

    @Test void bridgesDoNotReplaceRegisteredLegacyRuntimeMappings() throws Exception {
        assertThat(PersonalExpensePostingBridge.class.isAnnotationPresent(RestController.class)).isFalse();
        assertThat(SharedExpensePostingBridge.class.isAnnotationPresent(RestController.class)).isFalse();
        assertThat(PersonalFinanceController.class.getMethod("expense", String.class,
                PersonalFinanceOperations.FinancialRequest.class)).isNotNull();
        assertThat(SharedFinanceController.class.getMethod("createExpense", UUID.class, String.class,
                SharedFinanceOperations.ExpenseRequest.class)).isNotNull();
    }

    @RestController
    static final class TestController {
        private final PersonalExpensePostingBridge personal;
        private final SharedExpensePostingBridge shared;
        private final ObjectMapper json;
        TestController(PersonalExpensePostingBridge personal, SharedExpensePostingBridge shared, ObjectMapper json) {
            this.personal = personal; this.shared = shared; this.json = json;
        }
        @PostMapping("/api/v1/transactions/expense")
        ResponseEntity<String> personal(@RequestHeader("Idempotency-Key") UUID key,
                                        @RequestBody String raw) throws JsonProcessingException {
            var request = json.readValue(raw, PersonalExpensePostingBridge.TransactionCreate.class);
            var receipt = personal.record(ACTOR, key, request);
            return ResponseEntity.status(receipt.status()).header("ETag", receipt.etag()).body(receipt.body());
        }
        @PostMapping("/api/v1/vaults/{vaultId}/expenses")
        ResponseEntity<String> shared(@PathVariable("vaultId") UUID vaultId,
                                      @RequestHeader("Idempotency-Key") UUID key,
                                      @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                      @RequestBody String raw) throws JsonProcessingException {
            var request = json.readValue(raw, SharedExpensePostingBridge.ExpenseCreate.class);
            var receipt = shared.record(ACTOR, vaultId, key, ifMatch, request);
            var result = ResponseEntity.status(receipt.status());
            receipt.headers().forEach(result::header);
            return result.body(receipt.body());
        }
        @ExceptionHandler(JsonProcessingException.class)
        ResponseEntity<String> invalidJson(JsonProcessingException exception) {
            return ResponseEntity.badRequest().body("VALIDATION_ERROR");
        }
        @ExceptionHandler(BusinessApiException.class)
        ResponseEntity<String> business(BusinessApiException exception) {
            return ResponseEntity.status(exception.status()).body(exception.code());
        }
    }

    private static class NoopSharedStore implements SharedLedgerCommands.Store {
        @Override public CommandJournal.Receipt recordExpense(UUID actor, UUID vault, UUID key,
                                                                SharedLedgerCommands.Expense input) {
            return new CommandJournal.Receipt(201, Map.of(), "{}");
        }
        @Override public CommandJournal.Receipt reverseContribution(UUID actor, UUID vault, UUID child,
                                                                     UUID key, String reason) {
            throw new UnsupportedOperationException();
        }
        @Override public CommandJournal.Receipt reverseExpense(UUID actor, UUID vault, UUID child,
                                                                UUID key, String reason) {
            throw new UnsupportedOperationException();
        }
        @Override public CommandJournal.Receipt archiveVault(UUID actor, UUID vault, UUID key) {
            throw new UnsupportedOperationException();
        }
    }
}
