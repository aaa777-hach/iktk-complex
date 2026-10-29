package ru.iktk.bom.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.bom.service.BomExpandHandler.BomNode;
import ru.iktk.bom.service.BomExpandHandler.ExpansionMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BomExpandHandler — BFS многоуровневого BOM")
class BomExpandHandlerTest {

    private BomExpandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BomExpandHandler();
    }

    private BomNode n(String id, String parentId) {
        return new BomNode(id, parentId);
    }

    @Test
    @DisplayName("single node -> 1 узел, maxLevel 0, 1 лист")
    void singleNode() {
        ExpansionMetrics m = handler.expand(List.of(n("root", null)), "root");
        assertThat(m.getNodesCount()).isEqualTo(1);
        assertThat(m.getMaxLevel()).isZero();
        assertThat(m.getLeafCount()).isEqualTo(1);
        assertThat(m.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("2 уровня: root + 2 ребёнка -> nodes=3, maxLevel=1, leaf=2")
    void twoLevel() {
        List<BomNode> nodes = List.of(
                n("root", null),
                n("c1", "root"),
                n("c2", "root")
        );
        ExpansionMetrics m = handler.expand(nodes, "root");
        assertThat(m.getNodesCount()).isEqualTo(3);
        assertThat(m.getMaxLevel()).isEqualTo(1);
        assertThat(m.getLeafCount()).isEqualTo(2);
        assertThat(m.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("3 уровня: root -> a -> b и root -> c -> nodes=4, maxLevel=2, leaf=2")
    void threeLevel() {
        List<BomNode> nodes = List.of(
                n("root", null),
                n("a", "root"),
                n("b", "a"),
                n("c", "root")
        );
        ExpansionMetrics m = handler.expand(nodes, "root");
        assertThat(m.getNodesCount()).isEqualTo(4);
        assertThat(m.getMaxLevel()).isEqualTo(2);
        assertThat(m.getLeafCount()).isEqualTo(2);
        assertThat(m.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("orphan parentId -> ORPHAN_PARENT в errors, узел не в дереве")
    void orphanParent() {
        List<BomNode> nodes = List.of(
                n("root", null),
                n("c1", "root"),
                n("orphan", "missing")
        );
        ExpansionMetrics m = handler.expand(nodes, "root");
        assertThat(m.getNodesCount()).isEqualTo(3);
        assertThat(m.getMaxLevel()).isEqualTo(1);
        assertThat(m.getLeafCount()).isEqualTo(1);
        assertThat(m.getErrors()).anyMatch(e -> e.startsWith("ORPHAN_PARENT:orphan->missing"));
    }

    @Test
    @DisplayName("null/empty items -> EMPTY_ITEMS, nodes=0")
    void emptyItems() {
        ExpansionMetrics m1 = handler.expand(null, null);
        assertThat(m1.getNodesCount()).isZero();
        assertThat(m1.getErrors()).contains("EMPTY_ITEMS");

        ExpansionMetrics m2 = handler.expand(List.of(), null);
        assertThat(m2.getNodesCount()).isZero();
        assertThat(m2.getErrors()).contains("EMPTY_ITEMS");
    }

    @Test
    @DisplayName("глубокое дерево (>MAX_DEPTH) -> MAX_DEPTH_EXCEEDED в errors")
    void deepTree() {
        List<BomNode> nodes = new ArrayList<>();
        nodes.add(n("root", null));
        String prev = "root";
        for (int i = 1; i <= 60; i++) {
            String id = "n" + i;
            nodes.add(n(id, prev));
            prev = id;
        }
        ExpansionMetrics m = handler.expand(nodes, "root");
        assertThat(m.getErrors()).anyMatch(e -> e.startsWith("MAX_DEPTH_EXCEEDED:"));
        assertThat(m.getMaxLevel()).isLessThanOrEqualTo(BomExpandHandler.MAX_DEPTH + 1);
    }

    @Test
    @DisplayName("toPayload: плоская Map с nodesCount/maxLevel/leafCount/errors")
    void toPayload() {
        ExpansionMetrics m = handler.expand(List.of(n("root", null), n("c1", "root")), "root");
        Map<String, Object> p = m.toPayload();
        assertThat(p).containsKeys("nodesCount", "maxLevel", "leafCount", "errors");
        assertThat(p.get("nodesCount")).isEqualTo(2);
        assertThat(p.get("maxLevel")).isEqualTo(1);
        assertThat(p.get("leafCount")).isEqualTo(1);
    }
}