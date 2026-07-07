package ru.practicum.bank.frontui.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.practicum.bank.common.dto.exchange.ExchangeRateResponse;
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

    private static final Logger log = LoggerFactory.getLogger(MainPageController.class);

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
        log.info("Front main page loaded status=success source=front-ui");
        return "main";
    }

    @PostMapping("/account")
    public String updateAccount(
            @Valid @ModelAttribute AccountForm accountForm,
            BindingResult bindingResult,
            Model model,
            Principal principal,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            log.warn("Front user action rejected operationType=UPDATE_PROFILE status=validation_failed errorCode=FORM_VALIDATION_ERROR source=front-ui");
            addCommonModel(model, principal, authentication);
            addDefaultTransferForm(model);
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните имя и дату рождения");
            return "main";
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("Front downstream action prepared operationType=UPDATE_PROFILE source=front-ui targetService=bank-gateway");
            }
            gatewayClient.updateAccount(getAccessToken(authentication), accountForm);
            log.info("Front user action completed operationType=UPDATE_PROFILE status=success source=front-ui targetService=bank-gateway");
            redirectAttributes.addFlashAttribute("successMessage", "Данные аккаунта сохранены");
        } catch (GatewayClientException exception) {
            logGatewayClientFailure("UPDATE_PROFILE", exception);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/cash")
    public String cashOperation(
            @Valid @ModelAttribute CashForm cashForm,
            BindingResult bindingResult,
            @RequestParam String action,
            Model model,
            Principal principal,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            log.warn("Front user action rejected operationType=CASH status=validation_failed errorCode=FORM_VALIDATION_ERROR source=front-ui");
            addCommonModel(model, principal, authentication);
            addDefaultTransferForm(model);
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните положительную сумму");
            return "main";
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Front downstream action prepared operationType={} source=front-ui targetService=bank-gateway",
                        cashActionType(action)
                );
            }
            CashOperationResponse response = switch (action) {
                case "deposit" -> gatewayClient.deposit(getAccessToken(authentication), cashForm);
                case "withdraw" -> gatewayClient.withdraw(getAccessToken(authentication), cashForm);
                default -> {
                    log.warn("Front user action rejected operationType=CASH status=validation_failed errorCode=UNKNOWN_CASH_ACTION source=front-ui");
                    throw new GatewayClientException("Unknown cash action: " + action);
                }
            };
            log.info(
                    "Front user action completed operationType={} status=success source=front-ui targetService=bank-gateway",
                    cashActionType(action)
            );
            redirectAttributes.addFlashAttribute("successMessage", response.message());
        } catch (GatewayClientException exception) {
            logGatewayClientFailure(cashActionType(action), exception);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("cashForm", cashForm);
        }

        return "redirect:/";
    }

    @PostMapping("/transfers")
    public String transfer(
            @Valid @ModelAttribute TransferForm transferForm,
            BindingResult bindingResult,
            Model model,
            Principal principal,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            log.warn("Front user action rejected operationType=TRANSFER status=validation_failed errorCode=FORM_VALIDATION_ERROR source=front-ui");
            addCommonModel(model, principal, authentication);
            addRecipients(model, authentication);
            model.addAttribute("errorMessage", "Заполните получателя, сумму и валюту");
            return "main";
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("Front downstream action prepared operationType=TRANSFER source=front-ui targetService=bank-gateway");
            }
            var response = gatewayClient.transfer(
                    getAccessToken(authentication),
                    transferForm
            );
            log.info("Front user action completed operationType=TRANSFER status=success source=front-ui targetService=bank-gateway");
            redirectAttributes.addFlashAttribute("successMessage", "Перевод выполнен");
            redirectAttributes.addFlashAttribute("transferResponse", response);
        } catch (GatewayClientException exception) {
            logGatewayClientFailure("TRANSFER", exception);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("transferForm", transferForm);
        }

        return "redirect:/";
    }

    private void addCommonModel(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("username", getUsername(principal));
        try {
            if (log.isDebugEnabled()) {
                log.debug("Front downstream action prepared operationType=LOAD_ACCOUNT source=front-ui targetService=bank-gateway");
            }
            AccountResponse account = gatewayClient.getAccount(getAccessToken(authentication));
            addAccountModel(model, account);
        } catch (GatewayClientException exception) {
            logGatewayClientFailure("LOAD_ACCOUNT", exception);
            addEmptyAccountModel(model);
            model.addAttribute("accountLoadError", exception.getMessage());
        }
        addDefaultCashForm(model);
        addExchangeRates(model, authentication);
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
            if (log.isDebugEnabled()) {
                log.debug("Front downstream action prepared operationType=LOAD_RECIPIENTS source=front-ui targetService=bank-gateway");
            }
            List<RecipientResponse> recipients = gatewayClient.getRecipients(getAccessToken(authentication));
            model.addAttribute("recipients", recipients);
        } catch (GatewayClientException exception) {
            logGatewayClientFailure("LOAD_RECIPIENTS", exception);
            model.addAttribute("recipients", List.of());
            model.addAttribute("recipientsLoadError", exception.getMessage());
        }
    }

    private void addExchangeRates(Model model, Authentication authentication) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Front downstream action prepared operationType=LOAD_EXCHANGE_RATES source=front-ui targetService=bank-gateway");
            }
            List<ExchangeRateResponse> rates = gatewayClient.getExchangeRates(getAccessToken(authentication));
            model.addAttribute("exchangeRates", rates);
        } catch (GatewayClientException exception) {
            logGatewayClientFailure("LOAD_EXCHANGE_RATES", exception);
            model.addAttribute("exchangeRates", List.of());
            model.addAttribute("exchangeRatesLoadError", exception.getMessage());
        }
    }

    private void addDefaultCashForm(Model model) {
        if (!model.containsAttribute("cashForm")) {
            model.addAttribute("cashForm", new CashForm(new BigDecimal("100.00"), "RUB"));
        }
    }

    private void addDefaultTransferForm(Model model) {
        if (!model.containsAttribute("transferForm")) {
            Object accountCurrency = model.getAttribute("currency");
            String sourceCurrency = accountCurrency == null ? "RUB" : accountCurrency.toString();
            model.addAttribute("transferForm", new TransferForm("", new BigDecimal("100.00"), "RUB", sourceCurrency));
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

    private void logGatewayClientFailure(String operationType, GatewayClientException exception) {
        if (exception.isTechnical()) {
            log.error(
                    "Front gateway client failed operationType={} status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    operationType,
                    exception.getClass().getSimpleName()
            );
            return;
        }

        log.warn(
                "Front user action rejected operationType={} status=business_error errorCode=GATEWAY_BUSINESS_ERROR source=front-ui targetService=bank-gateway",
                operationType
        );
    }

    private String cashActionType(String action) {
        return switch (action) {
            case "deposit" -> "DEPOSIT";
            case "withdraw" -> "WITHDRAW";
            default -> "CASH";
        };
    }
}
