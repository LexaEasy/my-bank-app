package ru.practicum.bank.accounts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

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
            if (log.isDebugEnabled()) {
                log.debug(
                        "Processed operation already exists operationId={} operationType={} status=conflict source=accounts-service",
                        operationId,
                        operationType
                );
            }
            return retryStaleOperation(operationId, operationType, requestHash);
        } catch (RuntimeException exception) {
            log.error(
                    "Processed operation write failed operationId={} operationType={} status=error errorCategory=database errorType={} source=accounts-service",
                    operationId,
                    operationType,
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private boolean retryStaleOperation(String operationId, String operationType, String requestHash) {
        ProcessedOperation operation = claimTransaction.execute(status -> operationRepository.findById(operationId)
                .orElseThrow(() -> new OperationInProgressException(operationId)));

        validateFingerprint(operationId, operationType, requestHash, operation);
        if (operation.getStatus() != ProcessedOperationStatus.PROCESSING) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Processed operation result reused operationId={} operationType={} status={} source=accounts-service",
                        operationId,
                        operationType,
                        operation.getStatus()
                );
            }
            return false;
        }

        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(PROCESSING_TIMEOUT);
        if (operation.getUpdatedAt().isAfter(staleBefore)) {
            return false;
        }

        int deleted = claimTransaction.execute(status ->
                operationRepository.deleteStaleProcessing(operationId, staleBefore));
        boolean retryStarted = deleted == 1 && tryStartOperation(operationId, operationType, requestHash);
        if (retryStarted && log.isDebugEnabled()) {
            log.debug(
                    "Stale processed operation retry applied operationId={} operationType={} status=retry source=accounts-service",
                    operationId,
                    operationType
            );
        }
        return retryStarted;
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
            log.warn(
                    "Processed operation rejected operationId={} operationType={} status=in_progress errorCode=OPERATION_IN_PROGRESS source=accounts-service",
                    operationId,
                    operationType
            );
            throw new OperationInProgressException(operationId);
        }
        if (operation.getStatus() == ProcessedOperationStatus.FAILED) {
            log.warn(
                    "Processed operation rejected operationId={} operationType={} status=failed errorCode=OPERATION_ALREADY_FAILED source=accounts-service",
                    operationId,
                    operationType
            );
            throw new OperationAlreadyFailedException(operationId);
        }
        try {
            T response = objectMapper.readValue(operation.getResponseJson(), responseType);
            if (log.isDebugEnabled()) {
                log.debug(
                        "Stored processed operation response returned operationId={} operationType={} status=completed source=accounts-service",
                        operationId,
                        operationType
                );
            }
            return response;
        } catch (JsonProcessingException exception) {
            log.error(
                    "Processed operation response read failed operationId={} operationType={} status=error errorCategory=serialization errorType={} source=accounts-service",
                    operationId,
                    operationType,
                    exception.getClass().getSimpleName()
            );
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
            log.warn(
                    "Processed operation rejected operationId={} operationType={} status=conflict errorCode=IDEMPOTENCY_CONFLICT source=accounts-service",
                    operationId,
                    operationType
            );
            throw new IdempotencyConflictException(operationId);
        }
    }

    private void completeOperation(String operationId, Object response) {
        String responseJson = writeJson(operationId, response);
        try {
            ProcessedOperation operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new OperationInProgressException(operationId));
            operation.complete(responseJson, LocalDateTime.now(clock));
            operationRepository.save(operation);
        } catch (RuntimeException exception) {
            log.error(
                    "Processed operation write failed operationId={} status=error errorCategory=database errorType={} source=accounts-service",
                    operationId,
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private void releaseOperation(String operationId) {
        try {
            claimTransaction.executeWithoutResult(status -> operationRepository.deleteById(operationId));
        } catch (RuntimeException exception) {
            log.error(
                    "Processed operation release failed operationId={} status=error errorCategory=database errorType={} source=accounts-service",
                    operationId,
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
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
            log.error(
                    "Processed operation serialization failed operationId={} status=error errorCategory=serialization errorType={} source=accounts-service",
                    operationId,
                    exception.getClass().getSimpleName()
            );
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
