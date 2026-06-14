package ru.practicum.bank.frontui.web;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.bank.frontui.client.GatewayClient;
import ru.practicum.bank.frontui.client.GatewayClientException;
import ru.practicum.bank.frontui.dto.AccountForm;
import ru.practicum.bank.frontui.dto.AccountResponse;
import ru.practicum.bank.frontui.dto.CashForm;
import ru.practicum.bank.frontui.dto.CashOperationResponse;
import ru.practicum.bank.frontui.dto.RecipientResponse;
import ru.practicum.bank.frontui.dto.TransferForm;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
public class MainPageController {

    private final GatewayClient gatewayClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public MainPageController(GatewayClient gatewayClient, OAuth2AuthorizedClientService authorizedClientService) {
        this.gatewayClient = gatewayClient;
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/")
    public String showMainPage(Model model, Principal principal, Authentication authentication) {
        addCommonModel(model, principal, authentication);
        addDefaultTransferForm(model);
        addRecipients(model, authentication);
        addDefaultCashForm(model);
        return "main";
    }

    @PostMapping("/account")
    public String updateAccount(
            @Valid @ModelAttribute AccountForm accountForm,
            BindingResult bindingResult,
            Model model,
            Principal principal,
            Authentication authentication
    ) {
        if (bindingResult.hasErrors()) {
            addCommonModel(model, principal, authentication);
            addDefaultTransferForm(model);
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните имя и дату рождения");
            return "main";
        }

        try {
            AccountResponse account = gatewayClient.updateAccount(getAccessToken(authentication), accountForm);
            addAccountModel(model, account);
            model.addAttribute("successMessage", "Данные аккаунта сохранены");
        } catch (GatewayClientException exception) {
            addCommonModel(model, principal, authentication);
            model.addAttribute("errorMessage", exception.getMessage());
        }

        addDefaultTransferForm(model);
        addRecipients(model, authentication);
        addDefaultCashForm(model);
        return "main";
    }

    @PostMapping("/cash")
    public String cashOperation(
            @Valid @ModelAttribute CashForm cashForm,
            BindingResult bindingResult,
            @RequestParam String action,
            Model model,
            Principal principal,
            Authentication authentication
    ) {
        addCommonModel(model, principal, authentication);
        if (bindingResult.hasErrors()) {
            addDefaultTransferForm(model);
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните положительную сумму");
            return "main";
        }

        try {
            CashOperationResponse response = switch (action) {
                case "deposit" -> gatewayClient.deposit(getAccessToken(authentication), cashForm);
                case "withdraw" -> gatewayClient.withdraw(getAccessToken(authentication), cashForm);
                default -> throw new GatewayClientException("Unknown cash action: " + action);
            };
            model.addAttribute("balance", response.balance());
            model.addAttribute("currency", response.currency());
            model.addAttribute("successMessage", response.message());
        } catch (GatewayClientException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        }

        addDefaultTransferForm(model);
        addRecipients(model, authentication);
        return "main";
    }

    @PostMapping("/transfers")
    public String transfer(
            @Valid @ModelAttribute TransferForm transferForm,
            BindingResult bindingResult,
            Model model,
            Principal principal,
            Authentication authentication
    ) {
        addCommonModel(model, principal, authentication);
        if (bindingResult.hasErrors()) {
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните получателя, сумму и валюту");
            return "main";
        }

        try {
            var response = gatewayClient.transfer(
                    getAccessToken(authentication),
                    transferForm
            );
            model.addAttribute("successMessage", "Перевод выполнен");
            model.addAttribute("transferResponse", response);
        } catch (GatewayClientException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        }

        addRecipients(model, authentication);
        return "main";
    }

    private void addCommonModel(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("username", getUsername(principal));
        try {
            AccountResponse account = gatewayClient.getAccount(getAccessToken(authentication));
            addAccountModel(model, account);
        } catch (GatewayClientException exception) {
            addEmptyAccountModel(model);
            model.addAttribute("accountLoadError", exception.getMessage());
        }
        addDefaultCashForm(model);
    }

    private void addAccountModel(Model model, AccountResponse account) {
        model.addAttribute("accountForm", new AccountForm(account.name(), account.birthdate()));
        model.addAttribute("balance", account.balance());
        model.addAttribute("currency", account.currency());
    }

    private void addEmptyAccountModel(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm("", null));
        }
        addDefaultCashForm(model);
        model.addAttribute("balance", "");
        model.addAttribute("currency", "RUB");
    }

    private void addRecipients(Model model, Authentication authentication) {
        try {
            List<RecipientResponse> recipients = gatewayClient.getRecipients(getAccessToken(authentication));
            model.addAttribute("recipients", recipients);
        } catch (GatewayClientException exception) {
            model.addAttribute("recipients", List.of());
            model.addAttribute("recipientsLoadError", exception.getMessage());
        }
    }

    private void addDefaultCashForm(Model model) {
        if (!model.containsAttribute("cashForm")) {
            model.addAttribute("cashForm", new CashForm(new BigDecimal("100.00"), "RUB"));
        }
    }

    private void addDefaultTransferForm(Model model) {
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new TransferForm("", new BigDecimal("100.00"), "RUB"));
        }
    }

    private String getAccessToken(Authentication authentication) {
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                "front-ui",
                authentication.getName()
        );
        if (authorizedClient == null) {
            throw new GatewayClientException("OAuth2 client is not authorized");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private String getUsername(Principal principal) {
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getPreferredUsername();
        }
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getPreferredUsername();
        }
        return principal == null ? "" : principal.getName();
    }
}
