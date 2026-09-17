package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.core.Region;
import com.leadrat.crm.leads.api.core.SaveStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read and write model for a project.
 *
 * <p>One record for both directions, as in platform-api: the wizard sends back whatever it was
 * given, so a separate request type would be the same fields twice.
 */
public record ProjectDto(
        UUID id,
        UUID tenantId,
        @NotBlank(message = "name is required") String name,
        String brand,
        String legalEntity,
        ProjectStage projectStage,
        Region region,
        LocalDate startDate,
        ProjectType projectType,
        LocalDate expectedCompletionDate,
        String brief,
        String reraNumber,
        String reraState,
        String description,
        @Valid Address address,
        SaveStatus saveStatus,
        String microMarket,
        String googleMapsLink,
        String projectMicrosite,
        BigDecimal totalLandArea,
        AreaUnit areaUnit,
        LocalDate occupancyCertificateTargetDate,
        /** Active leads pointing at this project. Populated on the list and detail reads. */
        Long leadCount,
        LocalDateTime created,
        String createdBy,
        LocalDateTime modified,
        String lastModifiedBy,
        Boolean isActive
) {

    /** Applies the writable fields onto an entity, leaving identity and audit columns alone. */
    public void applyTo(Project project) {
        project.setName(name);
        project.setBrand(brand);
        project.setLegalEntity(legalEntity);
        project.setProjectStage(projectStage);
        project.setRegion(region);
        project.setStartDate(startDate);
        project.setProjectType(projectType);
        project.setExpectedCompletionDate(expectedCompletionDate);
        project.setBrief(brief);
        project.setReraNumber(reraNumber);
        project.setReraState(reraState);
        project.setDescription(description);
        project.setAddress(address);
        project.setSaveStatus(saveStatus != null ? saveStatus : SaveStatus.DRAFT);
        project.setMicroMarket(microMarket);
        project.setGoogleMapsLink(googleMapsLink);
        project.setProjectMicrosite(projectMicrosite);
        project.setTotalLandArea(totalLandArea);
        project.setAreaUnit(areaUnit);
        project.setOccupancyCertificateTargetDate(occupancyCertificateTargetDate);
    }

    public Project toEntity() {
        Project project = new Project();
        applyTo(project);
        return project;
    }

    public static ProjectDto fromEntity(Project project) {
        return fromEntity(project, null);
    }

    public static ProjectDto fromEntity(Project project, Long leadCount) {
        return new ProjectDto(
                project.getId(),
                project.getTenant(),
                project.getName(),
                project.getBrand(),
                project.getLegalEntity(),
                project.getProjectStage(),
                project.getRegion(),
                project.getStartDate(),
                project.getProjectType(),
                project.getExpectedCompletionDate(),
                project.getBrief(),
                project.getReraNumber(),
                project.getReraState(),
                project.getDescription(),
                project.getAddress(),
                project.getSaveStatus(),
                project.getMicroMarket(),
                project.getGoogleMapsLink(),
                project.getProjectMicrosite(),
                project.getTotalLandArea(),
                project.getAreaUnit(),
                project.getOccupancyCertificateTargetDate(),
                leadCount,
                project.getCreated(),
                project.getCreatedBy(),
                project.getModified(),
                project.getLastModifiedBy(),
                project.isActive());
    }
}
