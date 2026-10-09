package com.savemon.sharedfinance.interfaces;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.sharedfinance.application.SharedFinanceOperations;
import com.savemon.sharedfinance.application.SharedFinanceOperations.ContributionRequest;
import com.savemon.sharedfinance.application.SharedFinanceOperations.ExpenseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnBean(JdbcTemplate.class)
@RequestMapping("/api/v1")
public class SharedFinanceController {
    private final SharedFinanceOperations service;
    private final CurrentUserProvider currentUser;

    public SharedFinanceController(SharedFinanceOperations service,CurrentUserProvider currentUser) {
        this.service=service;
        this.currentUser=currentUser;
    }

    @PostMapping("/vaults")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createVault(@Valid @RequestBody CreateVault request) {
        return service.createVault(userId(),request.name(),request.currency());
    }
    @GetMapping("/vaults")
    public Map<String,Object> vaults(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.vaults(userId(),page,size);
    }
    @GetMapping("/vaults/{vaultId}")
    public Map<String,Object> vault(@PathVariable UUID vaultId) { return service.vault(vaultId,userId()); }

    @GetMapping("/vaults/{vaultId}/members")
    public Map<String,Object> members(@PathVariable UUID vaultId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.members(vaultId,userId(),page,size);
    }
    @PostMapping("/vaults/{vaultId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> addMember(@PathVariable UUID vaultId,@Valid @RequestBody AddMember request) {
        return service.addMember(vaultId,userId(),request.userId());
    }
    @PostMapping("/vaults/{vaultId}/members/{memberId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID vaultId,@PathVariable UUID memberId) {
        service.removeMember(vaultId,userId(),memberId);
    }
    @PostMapping("/vaults/{vaultId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> contribution(@PathVariable UUID vaultId,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ContributionRequest request) {
        return service.contribution(vaultId,userId(),key,request);
    }
    @GetMapping("/vaults/{vaultId}/contributions")
    public Map<String,Object> contributions(@PathVariable UUID vaultId,
            @RequestParam(required=false) UUID memberId,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.contributions(vaultId,userId(),memberId,from,to,page,size);
    }
    @PostMapping("/vaults/{vaultId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createExpense(@PathVariable UUID vaultId,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ExpenseRequest request) {
        return service.expense(vaultId,userId(),key,request);
    }
    @GetMapping("/vaults/{vaultId}/expenses")
    public Map<String,Object> expenses(@PathVariable UUID vaultId,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) UUID memberId,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.expenses(vaultId,userId(),from,to,memberId,page,size);
    }
    @GetMapping("/vaults/{vaultId}/expenses/{expenseId}")
    public Map<String,Object> expense(@PathVariable UUID vaultId,@PathVariable UUID expenseId) {
        return service.expenseDetail(expenseId,vaultId,userId());
    }
    @GetMapping("/vaults/{vaultId}/members/{memberId}/responsibility")
    public Map<String,Object> responsibility(@PathVariable UUID vaultId,@PathVariable UUID memberId) {
        return service.responsibility(vaultId,userId(),memberId);
    }

    private UUID userId() {
        return currentUser.currentUserId().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public record CreateVault(@NotBlank @Size(max=120) String name,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency){}
    public record AddMember(@jakarta.validation.constraints.NotNull UUID userId){}
}
