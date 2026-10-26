package ru.iktk.modules.forging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgingTooling {
    @Builder.Default
    private List<String> items = new ArrayList<>();
    @Builder.Default
    private List<String> dieCavities = new ArrayList<>();
    private String material;
    private BigDecimal estimatedCost;
}
