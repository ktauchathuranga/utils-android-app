package io.github.ktauchathuranga.utils;

public enum SpeedProfile {
    SLOW(50),   // 50ms delay between key presses
    MEDIUM(20), // 20ms delay
    FAST(5);    // 5ms delay

    private final int delayMs;

    SpeedProfile(int delayMs) {
        this.delayMs = delayMs;
    }

    public int getDelayMs() {
        return delayMs;
    }
}
