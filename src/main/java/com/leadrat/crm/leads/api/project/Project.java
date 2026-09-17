package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.core.Region;
import com.leadrat.crm.leads.api.core.SaveStatus;
import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A real-estate project a lead can enquire about.
 *
 * <p>Adapted from platform-api, trimmed to the fields the lead module actually reads or displays:
 * the inventory, tower, pricing, milestone and stakeholder structures around it are out of scope
 * here.
 */
@Data
@Entity
@Table(name = "project",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_tenant_name", columnNames = {"tenant", "name"}))
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Project extends TenantAwareAggregateRoot<Project> {

    @Column(nullable = false)
    private String name;

    private String brand;

    private String legalEntity;

    @Enumerated(EnumType.STRING)
    private ProjectStage projectStage;

    @Enumerated(EnumType.STRING)
    private Region region;

    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    private ProjectType projectType;

    private LocalDate expectedCompletionDate;

    @Column(length = 2000)
    private String brief;

    private String reraNumber;

    private String reraState;

    @Column(length = 4000)
    private String description;

    @Embedded
    private Address address;

    /**
     * Whether the project has been published or is still a wizard draft.
     *
     * <p>A draft is still listed and still selectable on a lead: half-configured is a normal state
     * while a project is being set up, and blocking it would block lead capture too.
     */
    @Enumerated(EnumType.STRING)
    private SaveStatus saveStatus = SaveStatus.DRAFT;

    private String microMarket;

    private String googleMapsLink;

    private String projectMicrosite;

    @Column(precision = 15, scale = 4)
    private BigDecimal totalLandArea;

    @Enumerated(EnumType.STRING)
    private AreaUnit areaUnit;

    private LocalDate occupancyCertificateTargetDate;
}
