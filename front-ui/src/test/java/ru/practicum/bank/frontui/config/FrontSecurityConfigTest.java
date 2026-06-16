package ru.practicum.bank.frontui.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.frontui.client.GatewayClient;
import ru.practicum.bank.frontui.web.MainPageController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MainPageController.class)
@Import(FrontSecurityConfig.class)
@TestPropertySource(properties = "bank.security.logout.end-session-uri=http://localhost:8180/realms/bank-realm/protocol/openid-connect/logout")
class FrontSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayClient gatewayClient;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @Test
    void shouldRedirectAnonymousUserToOauth2Login() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/front-ui")));
    }

    @Test
    void shouldLogoutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(oidcLogin().idToken(token -> token.tokenValue("id-token")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        containsString("http://localhost:8180/realms/bank-realm/protocol/openid-connect/logout")
                ))
                .andExpect(header().string("Location", containsString("post_logout_redirect_uri=http://localhost/")))
                .andExpect(header().string("Location", containsString("id_token_hint=id-token")));
    }
}
