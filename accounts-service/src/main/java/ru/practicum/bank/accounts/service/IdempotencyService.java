package ru.practicum.bank.accounts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final ProcessedOperationRepository operationRepository;
    private final TransactionTemplate claimTransaction;
    private final TransactionTemplate businessTransaction;
    private final BalanceTransactionRetryExecutor retryExecutor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(
            ProcessedOperationRepository operationRepository,
            PlatformTransactionManager transactionManager,
            BalanceTransactionRetryExecutor retryExecutor,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.claimTransaction = new TransactionTemplate(transactionManager);
        this.claimTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.businessTransaction = new TransactionTemplate(transactionManager);
        this.retryExecutor = retryExecutor;
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
        String requestHash = hashRequest(operationType, request);
        if (!tryStartOperation(operationId, operationType, requestHash)) {
            return handleExistingOperation(operationId, operationType, requestHash, responseType);
        }

        try {
            return retryExecutor.execute(() ->
                    businessTransaction.execute(status -> {
                        T response = businessOperation.get();
                        completeOperation(operationId, response);
                        return response;
                    }));
        } catch (RuntimeException exception) {
            releaseOperation(operationId);
            throw exception;
        }
    }

    private boolean tryStartOperation(String operationId, String operationType, String requestHash) {
        try {
            claimTransaction.executeWithoutResult(status -> {
                operationRepository.insertProcessing(
                        operationId,
                        operationType,
                        requestHash,
                        LocalDateTime.now(clock)
                );
            });
            return true;
        } catch (DataIntegrityViolationException exception) {
            return retryStaleOperation(operationId, operationType, requestHash);
        }
    }

    private boolean retryStaleOperation(String operationId, String operationType, String requestHash) {
        ProcessedOperation operation = claimTransaction.execute(status -> operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationInProgressException(operationId)));

        validateFingerprint(operationId, operationType, requestHash, operation);
        if (operation.getStatus() != ProcessedOperationStatus.PROCESSING) {
            return false;
        }

        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(PROCESSING_TIMEOUT);
        if (operation.getUpdatedAt().isAfter(staleBefore)) {
            return false;
        }

        int deleted = claimTransaction.execute(status ->
                operationRepository.deleteStaleProcessing(operationId, staleBefore));
        return deleted == 1 && tryStartOperation(operationId, operationType, requestHash);
    }

    private <T> T handleExistingOperation(
            String operationId,
            String operationType,
            String requestHash,
            Class<T> responseType
    ) {
        ProcessedOperation operation = claimTransaction.execute(status -> operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationInProgressException(operationId)));

        validateFingerprint(operationId, operationType, requestHash, operation);
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

    private void validateFingerprint(
            String operationId,
            String operationType,
            String requestHash,
            ProcessedOperation operation
    ) {
        if (!operation.getOperationType().equals(operationType)
                || !operation.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(operationId);
        }
    }

    private void completeOperation(String operationId, Object response) {
        String responseJson = writeJson(operationId, response);
        ProcessedOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationInProgressException(operationId));
        operation.complete(responseJson, LocalDateTime.now(clock));
        operationRepository.save(operation);
    }

    private void releaseOperation(String operationId) {
        claimTransaction.executeWithoutResult(status -> operationRepository.deleteById(operationId));
    }

    String hashRequest(String operationType, Object request) {
        JsonNode payload = normalize(objectMapper.valueToTree(request));
        ObjectNode fingerprint = objectMapper.createObjectNode();
        fingerprint.put("operationType", operationType);
        fingerprint.set("payload", payload);
        return sha256(writeJson("request", fingerprint));
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode normalized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                    normalized.set(entry.getKey(), normalize(entry.getValue())));
            return normalized;
        }
        if (node.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            node.forEach(value -> normalized.add(normalize(value)));
            return normalized;
        }
        if (node.isBigDecimal() || node.isFloatingPointNumber()) {
            return objectMapper.getNodeFactory().numberNode(node.decimalValue().stripTrailingZeros());
        }
        return node;
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
