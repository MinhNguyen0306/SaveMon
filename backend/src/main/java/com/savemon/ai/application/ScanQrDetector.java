package com.savemon.ai.application;

import java.awt.image.BufferedImage;

/** NO_QR requires separately approved detector evidence; the production boundary never returns it. */
@FunctionalInterface
public interface ScanQrDetector {
    Verdict detect(BufferedImage image);

    enum Verdict { NO_QR, CANDIDATE, UNCERTAIN }
}
