package com.savemon.personalfinance.interfaces;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.personalfinance.application.PersonalFinanceOperations;
import com.savemon.personalfinance.application.PersonalFinanceOperations.EditRequest;
import com.savemon.personalfinance.application.PersonalFinanceOperations.FinancialRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnBean(JdbcTemplate.class)
@RequestMapping("/api/v1")
public class PersonalFinanceController {
    private final PersonalFinanceOperations service;
    private final CurrentUserProvider currentUser;

    public PersonalFinanceController(PersonalFinanceOperations service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/accounts")
    public Map<String,Object> accounts(@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.accounts(userId(), status,page,size);
    }
    @GetMapping("/accounts/{accountId}")
    public Map<String,Object> account(@PathVariable UUID accountId) { return service.account(accountId,userId()); }
    @PostMapping("/accounts")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createAccount(@Valid @RequestBody CreateAccount request) {
        return service.createAccount(userId(),request.name(),request.type(),request.currency());
    }
    @PatchMapping("/accounts/{accountId}")
    public Map<String,Object> updateAccount(@PathVariable UUID accountId,@Valid @RequestBody UpdateAccount request) {
        return service.updateAccount(accountId,userId(),request.name());
    }
    @PostMapping("/accounts/{accountId}/archive")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveAccount(@PathVariable UUID accountId) { service.archiveAccount(accountId,userId()); }

    @GetMapping("/categories")
    public Map<String,Object> categories(@RequestParam(required=false) String type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.categories(userId(),type,page,size);
    }
    @PostMapping("/categories")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createCategory(@Valid @RequestBody CreateCategory request) {
        return service.createCategory(userId(),request.name(),request.type(),request.parentId());
    }
    @PatchMapping("/categories/{categoryId}")
    public Map<String,Object> updateCategory(@PathVariable UUID categoryId,@Valid @RequestBody UpdateCategory request) {
        return service.updateCategory(categoryId,userId(),request.name());
    }
    @PostMapping("/categories/{categoryId}/archive")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCategory(@PathVariable UUID categoryId) { service.archiveCategory(categoryId,userId()); }

    @PostMapping("/transactions/income")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> income(@RequestHeader("Idempotency-Key") String key,@RequestBody FinancialRequest request) {
        return service.record(userId(),"INCOME",key,request);
    }
    @PostMapping("/transactions/expense")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> expense(@RequestHeader("Idempotency-Key") String key,@RequestBody FinancialRequest request) {
        return service.record(userId(),"EXPENSE",key,request);
    }
    @GetMapping("/transactions")
    public Map<String,Object> history(
            @RequestParam(required=false) UUID accountId,
            @RequestParam(required=false) String type,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) UUID categoryId,
            @RequestParam(required=false) String currency,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        Instant start = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return service.history(userId(),accountId,type,status,start,end,categoryId,currency,page,size);
    }
    @GetMapping("/transactions/{transactionId}")
    public Map<String,Object> transaction(@PathVariable UUID transactionId) { return service.transaction(transactionId,userId()); }
    @PatchMapping("/transactions/{transactionId}")
    public Map<String,Object> edit(@PathVariable UUID transactionId,@RequestBody EditRequest request) {
        return service.updateTransaction(transactionId,userId(),request);
    }
    @PostMapping("/transactions/{transactionId}/reverse")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> reverse(@PathVariable UUID transactionId,
            @RequestHeader("Idempotency-Key") String key,@RequestBody(required=false) ReverseRequest request) {
        return service.reverse(transactionId,userId(),key,request == null ? null : request.reason());
    }
    @GetMapping("/calendar")
    public Map<String,Object> calendar(@RequestParam(required=false) LocalDate from,
                                       @RequestParam(required=false) LocalDate to,
                                       @RequestParam String currency) {
        return service.calendar(userId(),from,to,currency);
    }
    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(required=false) LocalDate from,
                                     @RequestParam(required=false) LocalDate to,
                                     @RequestParam String currency) {
        return service.summary(userId(),from,to,currency);
    }
    private UUID userId() {
        return currentUser.currentUserId().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record CreateAccount(@NotBlank @Size(max=120) String name,
            @NotBlank @Pattern(regexp="CASH|BANK|EWALLET|OTHER") String type,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency) { }
    public record UpdateAccount(@NotBlank @Size(max=120) String name) { }
    public record CreateCategory(@NotBlank @Size(max=120) String name,
            @NotBlank @Pattern(regexp="INCOME|EXPENSE") String type, UUID parentId) { }
    public record UpdateCategory(@NotBlank @Size(max=120) String name) { }
    public record ReverseRequest(@Size(max=500) String reason) { }
}
