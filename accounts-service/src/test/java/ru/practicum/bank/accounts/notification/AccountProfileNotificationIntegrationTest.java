package ru.practicum.bank.accounts.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.bank.accounts.dto.UpdateAccountRequest;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.repository.AccountRepository;
import ru.practicum.bank.accounts.service.AccountService;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AccountProfileNotificationIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private NotificationEventPublisher notificationEventPublisher;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldPublishNotificationOnlyAfterTransactionCommit() {
        var account = new Account(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                Currency.RUB
        );
        var request = new UpdateAccountRequest("Иван Иванов", LocalDate.of(1992, 5, 10));
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountService.updateCurrentAccount("ivan", request);
            verify(notificationEventPublisher, never()).publish(any());
        });

        var eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().source()).isEqualTo(NotificationSource.ACCOUNTS);
        assertThat(eventCaptor.getValue().type()).isEqualTo(NotificationType.ACCOUNT_UPDATED);
        assertThat(eventCaptor.getValue().recipientLogin()).isEqualTo("ivan");
    }
}
