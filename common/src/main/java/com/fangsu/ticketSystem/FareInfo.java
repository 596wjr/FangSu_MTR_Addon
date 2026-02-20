package com.fangsu.ticketSystem;

public record FareInfo(FareType type, int value, String displayName) {
    public FareInfo(FareType type, int value) {
        this(type, value, "");
    }
}
