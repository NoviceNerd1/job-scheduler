package com.jobqueue.shared.model;

public enum JobStatus {
    PENDING,
    LEASED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRY,
    CANCELLED,
    DEAD;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == DEAD;
    }
    
    public boolean canTransitionTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == LEASED || next == CANCELLED;
            case LEASED -> next == RUNNING || next == FAILED;
            case RUNNING -> next == COMPLETED || next == FAILED;
            case FAILED -> next == RETRY || next == DEAD;
            case RETRY -> next == PENDING || next == DEAD;
            default -> false;
        };
    }
}
