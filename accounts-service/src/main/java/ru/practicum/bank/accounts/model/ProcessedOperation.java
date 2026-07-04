package ru.practicum.bank.accounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_operations")
public class ProcessedOperation {

    @Id
    @Column(name = "operation_id", nullable = false, length = 128)
    private String operationId;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProcessedOperationStatus status;

    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProcessedOperation() {
    }

    public ProcessedOperation(String operationId, String operationType, String requestHash, LocalDateTime now) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.requestHash = requestHash;
        this.status = ProcessedOperationStatus.PROCESSING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public ProcessedOperationStatus getStatus() {
        return status;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void complete(String responseJson, LocalDateTime now) {
        this.status = ProcessedOperationStatus.COMPLETED;
        this.responseJson = responseJson;
        this.updatedAt = now;
    }

    public void fail(LocalDateTime now) {
        this.status = ProcessedOperationStatus.FAILED;
        this.updatedAt = now;
    }
}
