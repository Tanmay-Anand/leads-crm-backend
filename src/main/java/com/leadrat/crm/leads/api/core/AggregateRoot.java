package com.leadrat.crm.leads.api.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root with Spring Data domain-event support.
 *
 * @param <A> the concrete aggregate root type, for fluent API support
 */
@MappedSuperclass
@ToString(callSuper = true)
@SuppressWarnings("unchecked")
@EqualsAndHashCode(callSuper = true)
public abstract class AggregateRoot<A extends AggregateRoot<A>> extends AbstractAggregateRoot {

    /** All domain events currently captured by the aggregate. */
    @JsonIgnore
    private transient final List<Object> domainEvents = new ArrayList<>(0);

    protected AggregateRoot() {
        super(null);
    }

    @DomainEvents
    @JsonIgnore
    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    protected <T> T registerEvent(T event) {
        Assert.notNull(event, "Domain event must not be null!");
        this.domainEvents.add(event);
        return event;
    }

    @AfterDomainEventPublication
    public void clearDomainEvents() {
        if (this.domainEvents != null) {
            this.domainEvents.clear();
        }
    }

    protected final A andEvent(Object event) {
        registerEvent(event);
        return (A) this;
    }
}
