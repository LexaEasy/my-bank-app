package ru.practicum.bank.accounts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.OperationAlreadyFailedException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.accounts.exception.StoredOperationReadException;
import ru.practicum.bank.accounts.model.ProcessedOperation;
import ru.practicum.bank.accounts.model.ProcessedOperationStatus;
import ru.practicum.bank.accounts.repository.ProcessedOperationRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final ProcessedOperationRepository operationRepository;
    private final TransactionTemplate operationTransaction;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(
            ProcessedOperationRepository operationRepository,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.operationTransaction = new TransactionTemplate(transactionManager);
        this.operationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    public <T> T execute(
            String operationId,
            String operationType,
            Object request,
            Class<T> responseType,
            Supplier<T> businessOperation
    ) {
        String requestHash = hashRequest(request);
        if (!tryStartOperation(operationId, operationType, requestHash)) {
            return handleExistingOperation(operationId, requestHash, responseType);
        }

        try {
            T response = businessOperation.get();
            completeOperation(operationId, response);
            return response;
        } catch (RuntimeException exception) {
            failOperation(operationId);
            throw exception;
        }
    }

    private boolean tryStartOperation(String operationId, String operationType, String requestHash) {
        try {
            operationTransaction.executeWithoutResult(status -> {
                operationRepository.insertProcessing(
                        operationId,
                        operationType,
                        requestHash,
                        LocalDateTime.now(clock)
                );
            });
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private <T> T handleExistingOperation(String operationId, String requestHash, Class<T> responseType) {
        ProcessedOperation operation = operationTransaction.execute(status -> operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationInProgressException(operationId)));

        if (!operation.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(operationId);
        }
        if (operation.getStatus() == ProcessedOperationStatus.PROCESSING) {
            throw new OperationInProgressException(operationId);
        }
        if (operation.getStatus() == ProcessedOperationStatus.FAILED) {
            throw new OperationAlreadyFailedException(operationId);
        }
        try {
            return objectMapper.readValue(operation.getResponseJson(), responseType);
        } catch (JsonProcessingException exception) {
            throw new StoredOperationReadException(operationId, exception);
        }
    }

    private void completeOperation(String operationId, Object response) {
        String responseJson = writeJson(operationId, response);
        operationTransaction.executeWithoutResult(status -> {
            ProcessedOperation operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new OperationInProgressException(operationId));
            operation.complete(responseJson, LocalDateTime.now(clock));
            operationRepository.save(operation);
        });
    }

    private void failOperation(String operationId) {
        operationTransaction.executeWithoutResult(status -> operationRepository.findById(operationId)
                .ifPresent(operation -> {
                    operation.fail(LocalDateTime.now(clock));
                    operationRepository.save(operation);
                }));
    }

    private String hashRequest(Object request) {
        return sha256(writeJson("request", request));
    }

    private String writeJson(String operationId, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new StoredOperationReadException(operationId, exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
