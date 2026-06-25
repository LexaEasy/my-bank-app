package ru.practicum.bank.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.bank.accounts.model.ProcessedOperation;

import java.time.LocalDateTime;

public interface ProcessedOperationRepository extends JpaRepository<ProcessedOperation, String> {

    @Modifying
    @Query(value = """
            insert into processed_operations (operation_id, operation_type, request_hash, status, created_at, updated_at)
            values (:operationId, :operationType, :requestHash, 'PROCESSING', :now, :now)
            """, nativeQuery = true)
    void insertProcessing(String operationId, String operationType, String requestHash, LocalDateTime now);
}
