package com.leadrat.crm.leads.api.leadstatus.dto;

import com.leadrat.crm.leads.api.leadstatus.LeadStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class LeadStatusDto {

    private UUID id;
    private String name;
    private String displayName;
    private String colorCode;
    private boolean isDefault;
    private boolean isNoteRequired;
    private int displayOrder;

    public static LeadStatusDto from(LeadStatus s) {
        LeadStatusDto dto = new LeadStatusDto();
        dto.id = s.getId();
        dto.name = s.getName();
        dto.displayName = s.getDisplayName();
        dto.colorCode = s.getColorCode();
        dto.isDefault = s.isDefault();
        dto.isNoteRequired = s.isNoteRequired();
        dto.displayOrder = s.getDisplayOrder();
        return dto;
    }
}
