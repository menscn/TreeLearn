package com.treelearn.core.domain;

/**
 * The outcome of a domain operation: either a value or a {@link DomainError}.
 *
 * <p>Domain operations that can fail for <em>business</em> reasons return this type instead of
 * throwing, so an illegal transition cannot be silently ignored. Invariant violations remain
 * {@link IllegalArgumentException} thrown by {@link Guard} at construction time.
 *
 * @param <T> the success value type
 */
public final class DomainResult<T> {

    private final T value;
    private final DomainError error;

    private DomainResult(T value, DomainError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> DomainResult<T> ok(T value) {
        return new DomainResult<T>(value, null);
    }

    /** Returns a failure. A success value is normally {@code null} when a result carries no value. */
    public static <T> DomainResult<T> ok() {
        return new DomainResult<T>(null, null);
    }

    public static <T> DomainResult<T> fail(DomainError error) {
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }
        return new DomainResult<T>(null, error);
    }

    public static <T> DomainResult<T> fail(DomainErrorCode code, String detail) {
        return fail(DomainError.of(code, detail));
    }

    public boolean isOk() {
        return error == null;
    }

    public boolean isFailure() {
        return error != null;
    }

    /** @throws IllegalStateException when this result is a failure; use {@link #isOk()} first. */
    public T value() {
        if (error != null) {
            throw new IllegalStateException("domain failure has no value: " + error);
        }
        return value;
    }

    /** @throws IllegalStateException when this result is a success. */
    public DomainError error() {
        if (error == null) {
            throw new IllegalStateException("domain success has no error");
        }
        return error;
    }

    @Override
    public String toString() {
        return error == null ? "DomainResult.ok(" + value + ")" : "DomainResult.fail(" + error + ")";
    }
}
