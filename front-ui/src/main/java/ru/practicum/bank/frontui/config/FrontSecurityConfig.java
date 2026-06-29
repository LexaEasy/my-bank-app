package ru.practicum.bank.frontui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Configuration
@EnableWebSecurity
public class FrontSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${bank.security.logout.end-session-uri}") URI endSessionUri
    ) throws Exception {
        var loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/front-ui");
        loginEntryPoint.setFavorRelativeUris(true);

        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(loginEntryPoint))
                .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(endSessionUri))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(URI endSessionUri) {
        return (request, response, authentication) -> {
            var postLogoutRedirectUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/")
                    .build()
                    .toUriString();
            var logoutUri = UriComponentsBuilder.fromUri(endSessionUri)
                    .queryParam("post_logout_redirect_uri", postLogoutRedirectUri);
            var idToken = idToken(authentication);

            if (StringUtils.hasText(idToken)) {
                logoutUri.queryParam("id_token_hint", idToken);
            }

            response.sendRedirect(logoutUri.build().encode().toUriString());
        };
    }

    private String idToken(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getIdToken().getTokenValue();
        }

        return null;
    }
}
