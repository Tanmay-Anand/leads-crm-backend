package com.leadrat.crm.leads.api.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UUID-keyed, soft-deletable, audited base for every aggregate root.
 */
@Getter
@MappedSuperclass
@ToString(callSuper = false)
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAggregateRoot implements Persistable<UUID>, Serializable {

    @Id
    @JdbcTypeCode(Types.VARCHAR)
    private UUID id;

    @Version
    @JsonIgnore
    private Long version;

    @Column(name = "is_active")
    private boolean isActive = true;

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    @Transient
    private boolean isNew = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected LocalDateTime created = LocalDateTime.now();

    @CreatedBy
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime modified = LocalDateTime.now();

    @LastModifiedBy
    private String lastModifiedBy;

    protected AbstractAggregateRoot() {
        this(null);
    }

    protected AbstractAggregateRoot(UUID givenId) {
        this.id = givenId != null ? givenId : UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNew;
    }

    /**
     * Marks the entity as not new, so Spring Data merges loaded instances instead of
     * trying to persist them a second time.
     */
    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj.getClass().equals(this.getClass()))) {
            return false;
        }
        AbstractAggregateRoot that = (AbstractAggregateRoot) obj;
        return ObjectUtils.nullSafeEquals(this.getId(), that.getId());
    }

    protected Boolean terminate() {
        if (this.isActive) {
            this.isActive = false;
        }
        return isActive;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
