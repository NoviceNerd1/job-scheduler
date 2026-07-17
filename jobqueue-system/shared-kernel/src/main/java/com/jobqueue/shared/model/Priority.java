package com.jobqueue.shared.model;

public enum Priority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4),
    BACKGROUND(5);
    
    private final int value;
    
    Priority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static Priority fromValue(int value) {
        for (Priority p : values()) {
            if (p.value == value) return p;
        }
        return NORMAL;
    }
}
