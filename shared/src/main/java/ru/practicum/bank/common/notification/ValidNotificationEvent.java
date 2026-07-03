package ru.practicum.bank.common.notification;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = NotificationEventValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNotificationEvent {

    String message() default "notification event does not match its type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
