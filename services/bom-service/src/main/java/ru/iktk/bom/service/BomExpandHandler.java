package ru.iktk.bom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BFS-обход многоуровневого BOM (сессия 31).
 *
 * <p>Считает метрики структуры: {@code nodesCount}, {@code maxLevel}, {@code leafCount},
 * {@code errors[]}. Структуру не меняет — только диагностика для decision_log.
 * Полная per-node маршрутизация (N команд в модули) — отдельный техдолг.
 */
@Component
public class BomExpandHandler {

    private static final Logger log = LoggerFactory.getLogger(BomExpandHandler.class);

    public static final int MAX_DEPTH = 50;

    /** Минимальный узел BOM для BFS (отвязан от BomItemDto). */
    public record BomNode(String id, String parentId) {}

    public ExpansionMetrics expand(List<BomNode> nodes, String rootId) {
        ExpansionMetrics m = new ExpansionMetrics();
        if (nodes == null || nodes.isEmpty()) {
            m.getErrors().add("EMPTY_ITEMS");
            return m;
        }
        m.setNodesCount(nodes.size());

        Set<String> ids = new HashSet<>();
        for (BomNode n : nodes) {
            if (n.id() == null || n.id().isBlank()) {
                m.getErrors().add("ITEM_WITHOUT_ID");
            } else {
                ids.add(n.id());
            }
        }

        Map<String, List<BomNode>> childrenByParent = new HashMap<>();
        for (BomNode n : nodes) {
            if (n.id() == null || n.id().isBlank()) {
                continue;
            }
            String p = n.parentId();
            if (p == null || p.isBlank()) {
                continue;
            }
            if (!ids.contains(p)) {
                m.getErrors().add("ORPHAN_PARENT:" + n.id() + "->" + p);
                continue;
            }
            childrenByParent.computeIfAbsent(p, k -> new ArrayList<>()).add(n);
        }

        List<String> roots = new ArrayList<>();
        if (rootId != null && ids.contains(rootId)) {
            roots.add(rootId);
        } else {
            for (BomNode n : nodes) {
                if (n.id() == null || n.id().isBlank()) {
                    continue;
                }
                String p = n.parentId();
                if (p == null || p.isBlank() || !ids.contains(p)) {
                    roots.add(n.id());
                }
            }
        }

        Set<String> visited = new HashSet<>();
        int maxLevel = 0;
        int leafCount = 0;

        for (String r : roots) {
            Deque<String> queue = new ArrayDeque<>();
            Map<String, Integer> levelOf = new HashMap<>();
            queue.add(r);
            levelOf.put(r, 0);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                if (!visited.add(id)) {
                    m.getErrors().add("CYCLE_OR_DUP:" + id);
                    continue;
                }
                int lvl = levelOf.getOrDefault(id, 0);
                if (lvl > MAX_DEPTH) {
                    m.getErrors().add("MAX_DEPTH_EXCEEDED:" + id);
                    break;
                }
                maxLevel = Math.max(maxLevel, lvl);
                List<BomNode> kids = childrenByParent.getOrDefault(id, List.of());
                if (kids.isEmpty()) {
                    leafCount++;
                }
                for (BomNode k : kids) {
                    levelOf.putIfAbsent(k.id(), lvl + 1);
                    queue.add(k.id());
                }
            }
        }

        m.setMaxLevel(maxLevel);
        m.setLeafCount(leafCount);
        log.info("BomExpandHandler.expand: nodes={}, roots={}, maxLevel={}, leaf={}, errors={}",
                m.getNodesCount(), roots.size(), maxLevel, leafCount, m.getErrors().size());
        return m;
    }

    /** Метрики BFS-обхода. */
    public static class ExpansionMetrics {
        private int nodesCount;
        private int maxLevel;
        private int leafCount;
        private final List<String> errors = new ArrayList<>();

        public int getNodesCount() { return nodesCount; }
        public void setNodesCount(int v) { this.nodesCount = v; }
        public int getMaxLevel() { return maxLevel; }
        public void setMaxLevel(int v) { this.maxLevel = v; }
        public int getLeafCount() { return leafCount; }
        public void setLeafCount(int v) { this.leafCount = v; }
        public List<String> getErrors() { return errors; }

        public Map<String, Object> toPayload() {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nodesCount", nodesCount);
            p.put("maxLevel", maxLevel);
            p.put("leafCount", leafCount);
            p.put("errors", List.copyOf(errors));
            return p;
        }
    }
}