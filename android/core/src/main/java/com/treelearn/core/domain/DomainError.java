package com.treelearn.core.domain;

import java.util.Objects;

/** A domain business failure: a stable {@link DomainErrorCode} plus human-readable detail. */
public final class DomainError {

    public final DomainErrorCode code;
    public final String detail;

    public DomainError(DomainErrorCode code, String detail) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        this.code = code;
        this.detail = Guard.text(detail, "detail");
    }

    public static DomainError of(DomainErrorCode code, String detail) {
        return new DomainError(code, detail);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DomainError)) {
            return false;
        }
        DomainError that = (DomainError) other;
        return code == that.code && detail.equals(that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, detail);
    }

    @Override
    public String toString() {
        return code + ": " + detail;
    }
}
