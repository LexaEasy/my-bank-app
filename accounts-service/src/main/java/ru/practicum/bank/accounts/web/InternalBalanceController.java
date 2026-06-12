package ru.practicum.bank.accounts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.service.BalanceService;

@RestController
@RequestMapping("/api/accounts/internal/balance")
public class InternalBalanceController {

    private final BalanceService balanceService;

    public InternalBalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping("/deposit")
    public BalanceResponse deposit(@Valid @RequestBody BalanceOperationRequest request) {
        return balanceService.deposit(request);
    }

    @PostMapping("/withdraw")
    public BalanceResponse withdraw(@Valid @RequestBody BalanceOperationRequest request) {
        return balanceService.withdraw(request);
    }

    @PostMapping("/transfer")
    public TransferBalanceResponse transfer(@Valid @RequestBody TransferBalanceRequest request) {
        return balanceService.transfer(request);
    }
}
