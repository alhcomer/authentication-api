package uk.gov.di.authentication.shared.domain;

public interface AuditableEvent {

    AuditableEvent parseFromName(String name);
}
