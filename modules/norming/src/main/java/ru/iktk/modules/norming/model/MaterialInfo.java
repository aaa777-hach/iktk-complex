package ru.iktk.modules.norming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInfo {
    private String code;
    private String name;
    private String grade;
    private BigDecimal density;
    private String standard;
    private BigDecimal costPerKg;
}
