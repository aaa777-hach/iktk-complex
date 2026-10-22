package ru.iktk.recommendation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.recommendation.model.EquipmentGapAnalysis.Gap;
import ru.iktk.recommendation.model.EquipmentRecommendation.RecommendationStatus;
import ru.iktk.recommendation.model.EquipmentRecommendation.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Модели recommendation-service — конструкторы, бизнес-методы, equals/hashCode")
class EquipmentModelTest {

    // ===== EquipmentRecommendation =====

    @Test
    @DisplayName("EquipmentRecommendation: конструктор ставит PENDING и timestamps")
    void recommendation_constructorDefaults() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.PURCHASE);
        assertThat(r.getId()).isEqualTo("r1");
        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getGapId()).isEqualTo("g1");
        assertThat(r.getType()).isEqualTo("PURCHASE");
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getUpdatedAt()).isNotNull();
        assertThat(r.isPending()).isTrue();
    }

    @Test
    @DisplayName("EquipmentRecommendation: accept ставит ACCEPTED/acceptedBy/acceptedAt")
    void recommendation_accept() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.ALTERNATIVE);
        r.accept("ivanov");
        assertThat(r.getStatus()).isEqualTo("ACCEPTED");
        assertThat(r.getAcceptedBy()).isEqualTo("ivanov");
        assertThat(r.getAcceptedAt()).isNotNull();
        assertThat(r.isAccepted()).isTrue();
        assertThat(r.isPending()).isFalse();
    }

    @Test
    @DisplayName("EquipmentRecommendation: reject ставит REJECTED и дописывает reason в rationale")
    void recommendation_reject() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.PURCHASE);
        r.reject("дорого");
        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getRationale()).contains("Отклонено: дорого");
        assertThat(r.isRejected()).isTrue();
    }

    @Test
    @DisplayName("EquipmentRecommendation: isAlternative/requiresPurchase/hasVendor/hasEquipment")
    void recommendation_predicates() {
        EquipmentRecommendation alt = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.ALTERNATIVE);
        alt.setEquipmentId("eq-1");
        assertThat(alt.isAlternative()).isTrue();
        assertThat(alt.requiresPurchase()).isFalse();
        assertThat(alt.hasEquipment()).isTrue();

        EquipmentRecommendation rent = new EquipmentRecommendation(
                "r2", "p1", "g1", RecommendationType.RENT);
        rent.setVendorId("v-1");
        assertThat(rent.isAlternative()).isFalse();
        assertThat(rent.requiresPurchase()).isTrue();
        assertThat(rent.hasVendor()).isTrue();
    }

    @Test
    @DisplayName("EquipmentRecommendation: getTypeEnum/getStatusEnum с fallback")
    void recommendation_enums() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.OUTSOURCE);
        assertThat(r.getTypeEnum()).isEqualTo(RecommendationType.OUTSOURCE);
        assertThat(r.getStatusEnum()).isEqualTo(RecommendationStatus.PENDING);
        r.setType("NONEXISTENT");
        assertThat(r.getTypeEnum()).isEqualTo(RecommendationType.ALTERNATIVE);
        r.setStatus("NONEXISTENT");
        assertThat(r.getStatusEnum()).isEqualTo(RecommendationStatus.PENDING);
    }

    @Test
    @DisplayName("EquipmentRecommendation: builder + equals/hashCode по id")
    void recommendation_builder_equality() {
        EquipmentRecommendation a = EquipmentRecommendation.builder()
                .id("r1").projectId("p1").type(RecommendationType.PURCHASE)
                .score(new BigDecimal("0.85")).build();
        EquipmentRecommendation b = EquipmentRecommendation.builder()
                .id("r1").projectId("p2").type(RecommendationType.RENT).build();
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a.getStatus()).isEqualTo("PENDING");
        assertThat(a.getScore()).isEqualByComparingTo("0.85");
    }

    @Test
    @DisplayName("EquipmentRecommendation: setUpdatedAt устанавливает значение")
    void recommendation_setUpdatedAt() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.PURCHASE);
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        r.setUpdatedAt(t);
        assertThat(r.getUpdatedAt()).isEqualTo(t);
    }

    @Test
    @DisplayName("EquipmentRecommendation: toString содержит ключевые поля")
    void recommendation_toString() {
        EquipmentRecommendation r = new EquipmentRecommendation(
                "r1", "p1", "g1", RecommendationType.PURCHASE);
        assertThat(r.toString()).contains("r1", "p1", "g1", "PURCHASE", "PENDING");
    }

    // ===== EquipmentGapAnalysis =====

    @Test
    @DisplayName("EquipmentGapAnalysis: конструктор ставит RUNNING и startedAt")
    void analysis_constructor() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        assertThat(a.getId()).isEqualTo("a1");
        assertThat(a.getProjectId()).isEqualTo("p1");
        assertThat(a.getStrategy()).isEqualTo("HYBRID");
        assertThat(a.getStatus()).isEqualTo("RUNNING");
        assertThat(a.isRunning()).isTrue();
        assertThat(a.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: markCompleted/markFailed меняют статус")
    void analysis_statusTransitions() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "STRICT");
        a.markCompleted(5, 12);
        assertThat(a.isCompleted()).isTrue();
        assertThat(a.getGapsCount()).isEqualTo(5);
        assertThat(a.getRecommendationsCount()).isEqualTo(12);
        assertThat(a.getCompletedAt()).isNotNull();

        EquipmentGapAnalysis b = new EquipmentGapAnalysis("a2", "p1", "STRICT");
        b.markFailed("boom");
        assertThat(b.isFailed()).isTrue();
        assertThat(b.getError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: hasGaps и durationMs")
    void analysis_hasGapsAndDuration() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "FLEXIBLE");
        assertThat(a.hasGaps()).isFalse();
        assertThat(a.durationMs()).isZero();
        a.markCompleted(1, 0);
        assertThat(a.hasGaps()).isTrue();
        assertThat(a.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: addGap увеличивает счётчик и getOperationType")
    void analysis_addGap() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        Gap g = Gap.builder()
                .id("g1").analysisId("a1").projectId("p1")
                .operationType("TURNING").severity("CRITICAL")
                .build();
        a.addGap(g);
        assertThat(a.getGapsCount()).isEqualTo(1);
        assertThat(a.getGaps()).hasSize(1);
        assertThat(a.getOperationType()).isEqualTo("TURNING");
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: getRequirements возвращает пустой список при отсутствии gaps")
    void analysis_getRequirements_empty() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        assertThat(a.getRequirements()).isEmpty();
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: getRequirements мапит поля Gap в Map")
    void analysis_getRequirements_mapsFields() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        Gap g = Gap.builder()
                .id("g1").analysisId("a1").projectId("p1")
                .operationType("TURNING").operationId("op1")
                .requiredAccuracy("IT7").maxDimensions("1000x500x400")
                .material("ST45")
                .requiredPowerKw(new BigDecimal("15.5"))
                .requiredForceKn(new BigDecimal("100"))
                .requiredCnc(true)
                .severity("CRITICAL")
                .build();
        a.addGap(g);
        java.util.List<java.util.Map<String, Object>> reqs = a.getRequirements();
        assertThat(reqs).hasSize(1);
        java.util.Map<String, Object> m = reqs.get(0);
        assertThat(m.get("operationType")).isEqualTo("TURNING");
        assertThat(m.get("operationId")).isEqualTo("op1");
        assertThat(m.get("requiredAccuracy")).isEqualTo("IT7");
        assertThat(m.get("maxDimensions")).isEqualTo("1000x500x400");
        assertThat(m.get("material")).isEqualTo("ST45");
        assertThat(m.get("requiredPowerKw")).isEqualTo(new BigDecimal("15.5"));
        assertThat(m.get("requiredForceKn")).isEqualTo(new BigDecimal("100"));
        assertThat(m.get("requiredCnc")).isEqualTo(true);
        assertThat(m.get("severity")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: builder + equals/hashCode")
    void analysis_builder_equality() {
        EquipmentGapAnalysis a = EquipmentGapAnalysis.builder()
                .id("a1").projectId("p1").strategy("HYBRID")
                .gaps(List.of()).build();
        EquipmentGapAnalysis b = EquipmentGapAnalysis.builder()
                .id("a1").projectId("p2").strategy("STRICT").build();
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("Gap: builder + equals/hashCode + toString")
    void gap_builder_equality() {
        Gap g1 = Gap.builder().id("g1").operationType("MILLING").severity("MAJOR").build();
        Gap g2 = Gap.builder().id("g1").operationType("TURNING").severity("MINOR").build();
        assertThat(g1).isEqualTo(g2);
        assertThat(g1).hasSameHashCodeAs(g2);
        assertThat(g1.toString()).contains("g1", "MILLING", "MAJOR");
    }

    // ===== EquipmentRequirement =====

    @Test
    @DisplayName("EquipmentRequirement: конструктор ставит quantity=1, priority=0")
    void requirement_constructor() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        assertThat(r.getId()).isEqualTo("r1");
        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getOperationType()).isEqualTo("TURNING");
        assertThat(r.getQuantity()).isEqualTo(1);
        assertThat(r.getPriority()).isZero();
        assertThat(r.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("EquipmentRequirement: requiresCnc через requiredCnc и requiredAxes")
    void requirement_requiresCnc() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "MILLING");
        assertThat(r.requiresCnc()).isFalse();
        r.setRequiredCnc(true);
        assertThat(r.requiresCnc()).isTrue();

        EquipmentRequirement r2 = new EquipmentRequirement("r2", "p1", "MILLING");
        r2.setRequiredAxes(3);
        assertThat(r2.requiresCnc()).isTrue();
    }

    @Test
    @DisplayName("EquipmentRequirement: приоритеты normal/important/critical")
    void requirement_priorities() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        r.setPriority(2);
        assertThat(r.isNormal()).isTrue();
        assertThat(r.isImportant()).isFalse();
        r.setPriority(5);
        assertThat(r.isImportant()).isTrue();
        r.setPriority(9);
        assertThat(r.isCritical()).isTrue();
    }

    @Test
    @DisplayName("EquipmentRequirement: assignToAnalysis/isAnalyzed")
    void requirement_analysis() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        assertThat(r.isAnalyzed()).isFalse();
        r.assignToAnalysis("a1");
        assertThat(r.isAnalyzed()).isTrue();
        assertThat(r.getAnalysisId()).isEqualTo("a1");
    }

    @Test
    @DisplayName("EquipmentRequirement: escalatePriority с ограничением 10")
    void requirement_escalate() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        r.escalatePriority(3);
        assertThat(r.getPriority()).isEqualTo(3);
        r.escalatePriority(20);
        assertThat(r.getPriority()).isEqualTo(10);
    }

    @Test
    @DisplayName("EquipmentRequirement: builder + equals/hashCode")
    void requirement_builder_equality() {
        EquipmentRequirement a = EquipmentRequirement.builder()
                .id("r1").projectId("p1").operationType("TURNING")
                .requiredPowerKw(new BigDecimal("15.5"))
                .priority(7).build();
        EquipmentRequirement b = EquipmentRequirement.builder()
                .id("r1").projectId("p2").operationType("MILLING").build();
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a.getRequiredPowerKw()).isEqualByComparingTo("15.5");
    }

    // ===== VendorCatalog =====

    @Test
    @DisplayName("VendorCatalog: builder + getName/getCost алиасы")
    void vendor_builder() {
        VendorCatalog v = VendorCatalog.builder()
                .id("v1").vendorId("vnd-1").vendorName("ПромСтанкоСервис")
                .equipmentType("TURNING").equipmentModel("16K20F3")
                .price(new BigDecimal("4500000")).leadTimeDays(60)
                .contactInfo("+7-495-123-45-67").region("Москва")
                .active(true).build();
        assertThat(v.getId()).isEqualTo("v1");
        assertThat(v.getVendorName()).isEqualTo("ПромСтанкоСервис");
        assertThat(v.getPrice()).isEqualByComparingTo("4500000");
        assertThat(v.getActive()).isTrue();
        assertThat(v.getName()).isEqualTo("ПромСтанкоСервис");
        assertThat(v.getCost()).isEqualByComparingTo("4500000");
    }

    @Test
    @DisplayName("VendorCatalog: equals/hashCode по id")
    void vendor_equality() {
        VendorCatalog a = VendorCatalog.builder().id("v1").vendorName("A").build();
        VendorCatalog b = VendorCatalog.builder().id("v1").vendorName("B").build();
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    // ===== OutsourcingOption =====

    @Test
    @DisplayName("OutsourcingOption: builder + operation/contractor/cost/qualityRating")
    void outsourcing_builder() {
        OutsourcingOption o = OutsourcingOption.builder()
                .id("o1").gapId("g1").projectId("p1").type("CONTRACTOR")
                .operation("TURNING").contractor("ООО Ромашка")
                .cost(new BigDecimal("120000")).qualityRating(new BigDecimal("0.92"))
                .comment("ok").leadTimeDays(14).status("PENDING")
                .score(new BigDecimal("0.8"))
                .build();
        assertThat(o.getId()).isEqualTo("o1");
        assertThat(o.getContractor()).isEqualTo("ООО Ромашка");
        assertThat(o.getCost()).isEqualByComparingTo("120000");
        assertThat(o.getQualityRating()).isEqualByComparingTo("0.92");
        assertThat(o.getOperation()).isEqualTo("TURNING");
    }

    @Test
    @DisplayName("OutsourcingOption: equals/hashCode по id")
    void outsourcing_equality() {
        OutsourcingOption a = OutsourcingOption.builder().id("o1").contractor("A").build();
        OutsourcingOption b = OutsourcingOption.builder().id("o1").contractor("B").build();
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}