package com.savemon.ai.application;

/** Isolated dispatch reservation seam; no production paid provider is registered. */
@FunctionalInterface
public interface ScanDispatchGate {
    void requireAvailable();
}
