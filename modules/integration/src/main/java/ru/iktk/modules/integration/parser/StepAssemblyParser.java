package ru.iktk.modules.integration.parser;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.dto.RawItemDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Структурный парсер STEP (ISO 10303, AP203/AP214) — извлекает дерево сборки
 * (PRODUCT -> PRODUCT_DEFINITION_FORMATION -> PRODUCT_DEFINITION ->
 * NEXT_ASSEMBLY_USAGE_OCCURRENCE), НЕ трогая BREP.
 *
 * <p>Геометрия (CARTESIAN_POINT, ADVANCED_FACE, CDSR) игнорируется — для BOM не нужна.
 * Алгоритм: построчный стриминг (файл не грузим целиком), реестр #id -> Entity,
 * BFS от корня (PD без входящих NAUO). Глубина > {@link #MAX_DEPTH} -> исключение.
 */
@Component
public class StepAssemblyParser {

    private static final Logger log = LoggerFactory.getLogger(StepAssemblyParser.class);
    private static final int MAX_DEPTH = 10;

    private static final Set<String> RELEVANT = Set.of(
            "PRODUCT",
            "PRODUCT_DEFINITION",
            "PRODUCT_DEFINITION_FORMATION",
            "PRODUCT_DEFINITION_FORMATION_WITH_SPECIFIED_SOURCE",
            "NEXT_ASSEMBLY_USAGE_OCCURRENCE"
    );

    private record Entity(int id, String type, List<String> args) {}

    private record Node(int pdId, int level) {}

    /**
     * Распарсить STEP-файл в BOM.
     *
     * @throws NoSuchFileException  файл не найден
     * @throws IOException          ошибка чтения
     * @throws IllegalArgumentException нет ни одного PRODUCT / глубина > MAX_DEPTH
     */
    public RawBomDto parse(Path stepFile) throws IOException {
        if (stepFile == null || !Files.exists(stepFile)) {
            throw new NoSuchFileException(String.valueOf(stepFile));
        }
        Map<Integer, Entity> registry = readRegistry(stepFile);
        RawBomDto bom = buildBom(registry);
        log.info("StepAssemblyParser: file={}, products={}, items={}",
                stepFile.getFileName(), registry.size(), bom.getItems() == null ? 0 : bom.getItems().size());
        return bom;
    }

    // ---------- 1. стриминг реестра ----------

    private Map<Integer, Entity> readRegistry(Path stepFile) throws IOException {
        Map<Integer, Entity> reg = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(stepFile, StandardCharsets.ISO_8859_1)) {
            StringBuilder acc = new StringBuilder(512);
            String line;
            while ((line = r.readLine()) != null) {
                acc.append(line);
                int semi;
                while ((semi = acc.indexOf(";")) >= 0) {
                    String entity = acc.substring(0, semi).trim();
                    acc.delete(0, semi + 1);
                    parseEntity(entity, reg);
                }
            }
        }
        return reg;
    }

    private void parseEntity(String entity, Map<Integer, Entity> reg) {
        if (entity.isEmpty() || entity.charAt(0) != '#') return;
        int eq = entity.indexOf('=');
        if (eq < 0) return;
        int id;
        try { id = Integer.parseInt(entity.substring(1, eq).trim()); }
        catch (NumberFormatException e) { return; }
        String rest = entity.substring(eq + 1).trim();
        int paren = rest.indexOf('(');
        if (paren < 0) return;
        String type = rest.substring(0, paren).trim().toUpperCase(Locale.ROOT);
        if (!RELEVANT.contains(type)) return;
        String args = rest.substring(paren + 1).trim();
        if (args.endsWith(")")) args = args.substring(0, args.length() - 1).trim();
        reg.put(id, new Entity(id, type, splitArgs(args)));
    }

    /** Разбивает аргументы по запятым на верхнем уровне, с учётом '...' (с escape '') и (...). */
    static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else inStr = false;
                } else cur.append(c);
                continue;
            }
            if (c == '\'') { inStr = true; continue; }
            if (c == '(') { depth++; cur.append(c); continue; }
            if (c == ')') { depth--; cur.append(c); continue; }
            if (c == ',' && depth == 0) { out.add(cur.toString().trim()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private static Integer asRef(String s) {
        if (s == null || s.isEmpty() || s.charAt(0) != '#') return null;
        try { return Integer.parseInt(s.substring(1)); }
        catch (NumberFormatException e) { return null; }
    }

    // ---------- 2. резолв PD -> PRODUCT name ----------

    private Map<Integer, String> resolveProductNames(Map<Integer, Entity> reg) {
        Map<Integer, String> names = new HashMap<>();
        for (Entity e : reg.values()) {
            if (!"PRODUCT_DEFINITION".equals(e.type()) || e.args().size() < 3) continue;
            Integer pdfId = asRef(e.args().get(2));
            if (pdfId == null) continue;
            Entity pdf = reg.get(pdfId);
            if (pdf == null || !pdf.type().startsWith("PRODUCT_DEFINITION_FORMATION") || pdf.args().size() < 3) continue;
            Integer prodId = asRef(pdf.args().get(2));
            if (prodId == null) continue;
            Entity prod = reg.get(prodId);
            if (prod == null || !"PRODUCT".equals(prod.type())) continue;
            String name = prod.args().size() >= 2 ? prod.args().get(1) : prod.args().get(0);
            if (name != null && !name.isBlank()) names.put(e.id(), name);
        }
        return names;
    }

    // ---------- 3. BOM ----------

    private RawBomDto buildBom(Map<Integer, Entity> reg) {
        Map<Integer, String> pdName = resolveProductNames(reg);
        if (pdName.isEmpty()) {
            throw new IllegalArgumentException("STEP: не найден ни один PRODUCT/PRODUCT_DEFINITION");
        }

        Map<Integer, Map<Integer, Integer>> edges = new LinkedHashMap<>();
        for (Entity e : reg.values()) {
            if (!"NEXT_ASSEMBLY_USAGE_OCCURRENCE".equals(e.type()) || e.args().size() < 5) continue;
            Integer parent = asRef(e.args().get(3));
            Integer child  = asRef(e.args().get(4));
            if (parent == null || child == null) continue;
            if (!pdName.containsKey(parent) || !pdName.containsKey(child)) continue;
            edges.computeIfAbsent(parent, k -> new LinkedHashMap<>()).merge(child, 1, Integer::sum);
        }

        // одиночная деталь: NAUO нет, PD ровно один
        if (edges.isEmpty()) {
            Integer only = pdName.keySet().iterator().next();
            String name = pdName.get(only);
            RawBomDto bom = new RawBomDto();
            bom.setRootId(name);
            bom.setRootDesignation(name);
            bom.setRootName(name);
            bom.setItems(List.of());
            log.info("StepAssemblyParser: single part '{}' (no NAUO)", name);
            return bom;
        }

        Set<Integer> allParents  = new LinkedHashSet<>(edges.keySet());
        Set<Integer> allChildren = new LinkedHashSet<>();
        edges.values().forEach(m -> allChildren.addAll(m.keySet()));
        Set<Integer> roots = new LinkedHashSet<>(allParents);
        roots.removeAll(allChildren);
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("STEP: цикл в сборке — нет корневого PRODUCT_DEFINITION");
        }
        Integer root = roots.iterator().next();
        if (roots.size() > 1) {
            log.warn("StepAssemblyParser: найдено {} корней, берём '{}'", roots.size(), pdName.get(root));
        }

        RawBomDto bom = new RawBomDto();
        String rootName = pdName.get(root);
        bom.setRootId(rootName);
        bom.setRootDesignation(rootName);
        bom.setRootName(rootName);

        List<RawItemDto> items = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        queue.add(new Node(root, 1));
        visited.add(root);

        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            if (cur.level() > MAX_DEPTH) {
                throw new IllegalArgumentException("STEP: глубина сборки > " + MAX_DEPTH);
            }
            Map<Integer, Integer> children = edges.getOrDefault(cur.pdId(), Map.of());
            for (Map.Entry<Integer, Integer> en : children.entrySet()) {
                Integer childPd = en.getKey();
                Integer qty     = en.getValue();
                if (!visited.add(childPd)) {
                    log.warn("StepAssemblyParser: повторное вхождение PD#{} на уровне {} — пропуск", childPd, cur.level());
                    continue;
                }
                String childName = pdName.get(childPd);
                RawItemDto it = new RawItemDto();
                it.setId(childName);
                it.setParentId(pdName.get(cur.pdId()));
                it.setLevel(cur.level());
                it.setQuantity(qty);
                it.setPosition("1");
                it.setDesignation(childName);
                it.setName(childName);
                it.setUnit("шт");
                boolean hasKids = edges.containsKey(childPd);
                it.setType(hasKids ? "ASSEMBLY" : "PART");
                it.setHasChildren(hasKids);
                items.add(it);
                if (hasKids) queue.add(new Node(childPd, cur.level() + 1));
            }
        }
        bom.setItems(items);
        return bom;
    }
}