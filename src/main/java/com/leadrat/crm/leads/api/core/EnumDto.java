package com.leadrat.crm.leads.api.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnumDto {

    private String code;
    private String label;

    public static EnumDto of(Enum<?> value) {
        return new EnumDto(value.name(), value.name().replace("_", " "));
    }
}
