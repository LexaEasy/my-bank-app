package ru.practicum.bank.frontui.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepRealmAlignedWithGatewayApiAndServiceClientSecrets() throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of("../keycloak/realms/bank-realm.json").toFile());

        JsonNode frontUi = client(realm, "front-ui");
        assertThat(frontUi.get("secret").asText()).isEqualTo("${FRONT_UI_CLIENT_SECRET}");
        assertThat(frontUi.get("redirectUris").get(0).asText())
                .isEqualTo("${BANK_FRONT_UI_PUBLIC_URL}/login/oauth2/code/*");
        assertThat(frontUi.get("webOrigins").get(0).asText()).isEqualTo("${BANK_FRONT_UI_PUBLIC_URL}");
        assertThat(frontUi.get("attributes").get("post.logout.redirect.uris").asText())
                .isEqualTo("${BANK_FRONT_UI_PUBLIC_URL}/");

        assertThat(client(realm, "cash-service").get("secret").asText())
                .isEqualTo("${CASH_SERVICE_CLIENT_SECRET}");
        assertThat(client(realm, "transfer-service").get("secret").asText())
                .isEqualTo("${TRANSFER_SERVICE_CLIENT_SECRET}");
        assertThat(client(realm, "exchange-generator").get("secret").asText())
                .isEqualTo("${EXCHANGE_GENERATOR_CLIENT_SECRET}");
        assertThat(hasClient(realm, "notifications-service")).isFalse();
    }

    @Test
    void shouldGrantServiceAccountsRolesRequiredBySecurityConfigs() throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of("../keycloak/realms/bank-realm.json").toFile());

        assertThat(realmRoles(user(realm, "service-account-cash-service")))
                .contains("SERVICE", "ACCOUNTS_INTERNAL")
                .doesNotContain("NOTIFICATIONS_WRITE");
        assertThat(realmRoles(user(realm, "service-account-transfer-service")))
                .contains("SERVICE", "ACCOUNTS_INTERNAL")
                .doesNotContain("NOTIFICATIONS_WRITE");
        assertThat(realmRoles(user(realm, "service-account-accounts-service")))
                .contains("SERVICE")
                .doesNotContain("NOTIFICATIONS_WRITE");
        assertThat(hasUser(realm, "service-account-notifications-service")).isFalse();
        assertThat(realmRoles(user(realm, "service-account-exchange-generator")))
                .contains("SERVICE");
    }

    private JsonNode client(JsonNode realm, String clientId) {
        return StreamSupport.stream(realm.get("clients").spliterator(), false)
                .filter(client -> clientId.equals(client.get("clientId").asText()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode user(JsonNode realm, String username) {
        return StreamSupport.stream(realm.get("users").spliterator(), false)
                .filter(user -> username.equals(user.get("username").asText()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasClient(JsonNode realm, String clientId) {
        return StreamSupport.stream(realm.get("clients").spliterator(), false)
                .anyMatch(client -> clientId.equals(client.get("clientId").asText()));
    }

    private boolean hasUser(JsonNode realm, String username) {
        return StreamSupport.stream(realm.get("users").spliterator(), false)
                .anyMatch(user -> username.equals(user.get("username").asText()));
    }

    private List<String> realmRoles(JsonNode user) {
        return StreamSupport.stream(user.get("realmRoles").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
