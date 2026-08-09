package com.fangsu.mappings;

public class SimpleMappings<T> implements IMappings<T> {
    protected final T raw;

    protected SimpleMappings(T raw) {
        this.raw = raw;
    }

    @Override
    public T getRaw() {
        return this.raw;
    }

    @Override
    public String toString() {
        return "SimpleMappings{raw=" + raw + "}";
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleMappings<?> other) {
            return raw.equals(other.raw);
        }
        return false;
    }
}