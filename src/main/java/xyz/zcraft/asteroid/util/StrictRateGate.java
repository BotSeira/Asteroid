package xyz.zcraft.asteroid.util;

import java.util.concurrent.TimeUnit;

public final class StrictRateGate {
    private final long intervalNanos;
    private long nextRequestNanos = System.nanoTime();

    public StrictRateGate(long intervalNanos) {
        this.intervalNanos = intervalNanos;
    }

    public synchronized boolean tryAcquire() {
        if (System.nanoTime() < nextRequestNanos) return false;
        nextRequestNanos = System.nanoTime() + intervalNanos;
        return true;
    }

    public synchronized void acquire() throws InterruptedException {
        long waitNanos;
        while ((waitNanos = nextRequestNanos - System.nanoTime()) > 0) {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
        nextRequestNanos = System.nanoTime() + intervalNanos;
    }
}