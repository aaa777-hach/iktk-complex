package ru.iktk.modules.machining.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Программа по одной операции.
 *
 * <p>Содержит G-код для операции и метаданные (order, name, tool, mode, lineCount).
 * Сохраняется в {@code CncProgram.operations} (JSONB).
 */
public class OperationProgram {

    private final int order;
    private final String name;
    private final MachiningMethod method;
    private final CuttingTool tool;
    private final CuttingMode mode;
    private final String gcode;
    private final int lineCount;

    public OperationProgram(int order, String name, MachiningMethod method,
                            CuttingTool tool, CuttingMode mode,
                            String gcode, int lineCount) {
        this.order = order;
        this.name = name;
        this.method = method;
        this.tool = tool;
        this.mode = mode;
        this.gcode = gcode;
        this.lineCount = lineCount;
    }

    public int getOrder() { return order; }
    public String getName() { return name; }
    public MachiningMethod getMethod() { return method; }
    public CuttingTool getTool() { return tool; }
    public CuttingMode getMode() { return mode; }
    public String getGcode() { return gcode; }
    public int getLineCount() { return lineCount; }

    /**
     * Alias для getMode(): режимы резания операции.
     */
    @JsonIgnore
    public CuttingMode getCuttingMode() {
        return mode;
    }

    /** Восстановить операцию для перегенерации. */
    public MachiningOperation toOperation() {
        return MachiningOperation.builder()
                .order(order)
                .name(name)
                .method(method)
                .cnc(true)
                .tool(tool)
                .cuttingMode(mode)
                .build();
    }
}