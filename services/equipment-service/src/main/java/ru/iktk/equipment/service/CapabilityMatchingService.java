package ru.iktk.equipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.equipment.model.Equipment;
import ru.iktk.equipment.model.EquipmentCapability;
import ru.iktk.equipment.model.EquipmentAssignment;
import ru.iktk.equipment.repository.EquipmentAssignmentRepository;
import ru.iktk.equipment.repository.EquipmentRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CapabilityMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityMatchingService.class);
    private final EquipmentRepository equipmentRepository;
    private final EquipmentAssignmentRepository assignmentRepository;

    public CapabilityMatchingService(EquipmentRepository equipmentRepository,
                                         EquipmentAssignmentRepository assignmentRepository) {
        this.equipmentRepository = equipmentRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findMatching(Map<String, Object> requirement) {
        if (requirement == null) return Optional.empty();
        String operationType = str(requirement.get("operationType"));
        if (operationType == null || operationType.isBlank()) return Optional.empty();
        List<Map<String, Object>> scored = matchAll(operationType, requirement);
        if (scored.isEmpty()) return Optional.empty();
        return Optional.of(scored.get(0));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAlternatives(String operationType,
                                                      String requiredAccuracy,
                                                      List<Map<String, Object>> candidates) {
        if (operationType == null || operationType.isBlank()) return List.of();

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("operationType", operationType);
        req.put("requiredAccuracy", requiredAccuracy);
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> first = candidates.get(0);
            if (first != null) {
                req.putIfAbsent("requiredCnc", first.get("requiredCnc"));
                req.putIfAbsent("requiredPowerKw", first.get("requiredPowerKw"));
                req.putIfAbsent("requiredForceKn", first.get("requiredForceKn"));
                req.putIfAbsent("material", first.get("material"));
                req.putIfAbsent("maxDimensions", first.get("maxDimensions"));
            }
        }
        return matchAll(operationType, req);
    }

    @Transactional
    public void assign(String projectId, String equipmentId, String assignedBy) {
        if (projectId == null || equipmentId == null) {
            throw new IllegalArgumentException("assign: projectId и equipmentId обязательны");
        }
        assignmentRepository.findByProjectIdAndEquipmentIdAndActiveTrue(projectId, equipmentId)
                .orElseGet(() -> assignmentRepository.save(
                        new EquipmentAssignment(java.util.UUID.randomUUID().toString(),
                                projectId, equipmentId, assignedBy)));
        log.info("assign: projectId={}, equipmentId={}, assignedBy={}", projectId, equipmentId, assignedBy);
    }

    private List<Map<String, Object>> matchAll(String operationType, Map<String, Object> req) {
        List<Equipment> candidates = equipmentRepository.findActiveWithCapabilities(operationType);
        if (candidates == null || candidates.isEmpty()) return List.of();

        Boolean reqCnc = bool(req.get("requiredCnc"));
        BigDecimal reqPower = dec(req.get("requiredPowerKw"));
        BigDecimal reqForce = dec(req.get("requiredForceKn"));
        Integer reqAccuracy = parseAccuracy(str(req.get("requiredAccuracy")));
        String reqMaterial = str(req.get("material"));
        String reqDimensions = str(req.get("maxDimensions"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Equipment eq : candidates) {
            if (eq.getCapabilities() == null) continue;
            for (EquipmentCapability c : eq.getCapabilities()) {
                if (c.getOperationType() == null || !c.getOperationType().equalsIgnoreCase(operationType)) continue;
                if (reqCnc != null && reqCnc && !c.isCncCapable()) continue;
                if (reqPower != null && c.getPowerKw() != null && c.getPowerKw().compareTo(reqPower) < 0) continue;
                if (reqForce != null && c.getForceKn() != null && c.getForceKn().compareTo(reqForce) < 0) continue;
                if (reqAccuracy != null && c.getAccuracyGrade() != null && c.getAccuracyGrade() > reqAccuracy) continue;
                if (reqMaterial != null && c.getMaterials() != null && !c.getMaterials().isEmpty()
                        && c.getMaterials().stream().noneMatch(m -> m.equalsIgnoreCase(reqMaterial))) continue;
                if (reqDimensions != null && c.getMaxDimensions() != null
                        && !dimensionsFit(reqDimensions, c.getMaxDimensions())) continue;

                double score = score(c, reqAccuracy, reqPower, reqForce, reqCnc);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("equipmentId", eq.getId());
                m.put("equipmentName", eq.getName());
                m.put("equipmentType", eq.getType());
                m.put("capabilityId", c.getId());
                m.put("operationType", c.getOperationType());
                m.put("score", Math.round(score * 10000.0) / 10000.0);
                m.put("rationale", "operation=" + c.getOperationType()
                        + ", cnc=" + c.isCncCapable()
                        + ", power=" + c.getPowerKw()
                        + ", accuracyGrade=" + c.getAccuracyGrade());
                result.add(m);
            }
        }
        result.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("score")).reversed());
        return result;
    }

    private double score(EquipmentCapability c, Integer reqAccuracy, BigDecimal reqPower,
                         BigDecimal reqForce, Boolean reqCnc) {
        double accuracy = 0.5;
        if (reqAccuracy != null && c.getAccuracyGrade() != null) {
            accuracy = Math.max(0.0, 1.0 - Math.max(0, c.getAccuracyGrade() - reqAccuracy) * 0.1);
        }
        double power = 0.5;
        if (reqPower != null && c.getPowerKw() != null && reqPower.doubleValue() > 0) {
            power = Math.min(1.0, c.getPowerKw().doubleValue() / reqPower.doubleValue());
        }
        double force = 0.5;
        if (reqForce != null && c.getForceKn() != null && reqForce.doubleValue() > 0) {
            force = Math.min(1.0, c.getForceKn().doubleValue() / reqForce.doubleValue());
        }
        double cnc = (reqCnc == null || !reqCnc) ? 1.0 : (c.isCncCapable() ? 1.0 : 0.0);
        return 0.30 * accuracy + 0.25 * power + 0.20 * force + 0.25 * cnc;
    }

    private boolean dimensionsFit(String required, String available) {
        try {
            int[] r = parseDims(required);
            int[] a = parseDims(available);
            if (r == null || a == null) return true;
            return a[0] >= r[0] && a[1] >= r[1] && a[2] >= r[2];
        } catch (Exception e) {
            return true;
        }
    }

    private int[] parseDims(String s) {
        if (s == null) return null;
        String[] parts = s.toLowerCase().replace("х", "x").split("x");
        if (parts.length != 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseAccuracy(String s) {
        if (s == null) return null;
        try {
            String digits = s.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? null : Integer.parseInt(digits);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    private Boolean bool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.valueOf(String.valueOf(o));
    }

    private BigDecimal dec(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}