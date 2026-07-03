package com.example.timbya.core;

public enum TimbyaState {
    IDLE("Idle"),
    LISTENING("Listening"),
    PROCESSING("Thinking"),
    SPEAKING("Speaking"),
    OFF("Off");

    private final String label;
    TimbyaState(String label) { this.label = label; }
    public String label() { return label; }
}