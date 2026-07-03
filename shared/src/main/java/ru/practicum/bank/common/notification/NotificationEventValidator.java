package ru.practicum.bank.common.notification;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

public class NotificationEventValidator implements ConstraintValidator<ValidNotificationEvent, NotificationEvent> {

    private static final Set<NotificationType> MONEY_EVENT_TYPES = EnumSet.of(
            NotificationType.CASH_DEPOSITED,
            NotificationType.CASH_WITHDRAWN,
            NotificationType.TRANSFER_OUTGOING,
            NotificationType.TRANSFER_INCOMING
    );

    @Override
    public boolean isValid(NotificationEvent event, ConstraintValidatorContext context) {
        if (event == null || event.type() == null) {
            return true;
        }

        if (!MONEY_EVENT_TYPES.contains(event.type())) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (event.amount() == null) {
            addViolation(context, "amount is required for money notification events", "amount");
            valid = false;
        } else if (event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            addViolation(context, "amount must be positive for money notification events", "amount");
            valid = false;
        }

        if (event.currency() == null) {
            addViolation(context, "currency is required for money notification events", "currency");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String message, String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
