package ru.iktk.equipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.equipment.repository.EquipmentRepository;

/**
 * Сервис оборудования ИКТК.
 */
@Service
public class EquipmentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

    private final EquipmentRepository repository;

    public EquipmentService(EquipmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Проверить, доступен ли способ обработки на предприятии.
     */
    @Transactional(readOnly = true)
    public boolean isMethodAvailable(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            log.warn("isMethodAvailable: operationType пуст");
            return false;
        }
        String normalized = operationType.trim().toUpperCase();
        boolean available = repository.existsAvailableByOperation(normalized);
        log.info("isMethodAvailable: operationType={}, available={}", normalized, available);
        return available;
    }
}