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
import ru.practicum.bank.frontui.client.GatewayClient;
import ru.practicum.bank.frontui.client.GatewayClientException;
import ru.practicum.bank.frontui.dto.TransferForm;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
public class MainPageController {

    private final GatewayClient gatewayClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public MainPageController(GatewayClient gatewayClient, OAuth2AuthorizedClientService authorizedClientService) {
        this.gatewayClient = gatewayClient;
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/")
    public String showMainPage(Model model, Principal principal) {
        addCommonModel(model, principal);
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new TransferForm("", new BigDecimal("100.00"), "RUB"));
        }
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
        addCommonModel(model, principal);
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Заполните получателя, сумму и валюту");
            return "main";
        }

        try {
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    "front-ui",
                    authentication.getName()
            );
            var response = gatewayClient.transfer(
                    authorizedClient.getAccessToken().getTokenValue(),
                    transferForm
            );
            model.addAttribute("successMessage", "Перевод выполнен");
            model.addAttribute("transferResponse", response);
        } catch (GatewayClientException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        }

        return "main";
    }

    private void addCommonModel(Model model, Principal principal) {
        model.addAttribute("username", getUsername(principal));
        model.addAttribute("accountName", "");
        model.addAttribute("birthdate", "");
        model.addAttribute("balance", "");
        model.addAttribute("currency", "RUB");
    }

    private String getUsername(Principal principal) {
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getPreferredUsername();
        }
        return principal == null ? "" : principal.getName();
    }
}
