package com.savemon.ai.application;

import com.savemon.ai.interfaces.ExpenseScanResponse;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Test-injected, non-HTTP scan pipeline. No Spring component or accepting route. */
public final class ExpenseScanCoordinator {
    private final ExpenseScanEntitlementGate entitlement;
    private final ScanImageValidator images;
    private final ScanQrDetector qrDetector;
    private final ExpenseScanIdempotency idempotency;
    private final BillImageExtraction extraction;
    private final ScanOutputValidator outputs;
    private final ScanDispatchGate dispatchGate;
    private final ExecutorService workers;
    private final Semaphore slots = new Semaphore(2);
    private final Duration totalLimit;
    private final Duration extractionLimit;

    public ExpenseScanCoordinator(ExpenseScanEntitlementGate entitlement, ScanImageValidator images,
            ScanQrDetector qrDetector, ExpenseScanIdempotency idempotency,
            BillImageExtraction extraction, ScanOutputValidator outputs,
            ScanDispatchGate dispatchGate, ExecutorService workers) {
        this(entitlement, images, qrDetector, idempotency, extraction, outputs,
                dispatchGate, workers, Duration.ofSeconds(20), Duration.ofSeconds(15));
    }

    ExpenseScanCoordinator(ExpenseScanEntitlementGate entitlement, ScanImageValidator images,
            ScanQrDetector qrDetector, ExpenseScanIdempotency idempotency,
            BillImageExtraction extraction, ScanOutputValidator outputs,
            ScanDispatchGate dispatchGate, ExecutorService workers,
            Duration totalLimit, Duration extractionLimit) {
        this.entitlement = Objects.requireNonNull(entitlement);
        this.images = Objects.requireNonNull(images);
        this.qrDetector = Objects.requireNonNull(qrDetector);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.extraction = Objects.requireNonNull(extraction);
        this.outputs = Objects.requireNonNull(outputs);
        this.dispatchGate = Objects.requireNonNull(dispatchGate);
        this.workers = Objects.requireNonNull(workers);
        this.totalLimit = Objects.requireNonNull(totalLimit);
        this.extractionLimit = Objects.requireNonNull(extractionLimit);
    }

    public ExpenseScanResponse scan(UUID userId, UUID scanKey, byte[] sourceImage,
                                    String contentType, BooleanSupplier cancelled) {
        long started = System.nanoTime();
        Objects.requireNonNull(userId);
        Objects.requireNonNull(scanKey);
        Objects.requireNonNull(cancelled);
        entitlement.requireActivePaidEntitlement(userId);
        if (!slots.tryAcquire()) throw new ScanUnavailableException();
        boolean transferSlot = false;
        ScanImageValidator.ValidatedImage image = null;
        boolean claimed = false;
        try {
            requireLive(started, cancelled);
            image = images.validate(sourceImage, contentType);
            requireLive(started, cancelled);
            ScanQrDetector.Verdict verdict;
            try { verdict = qrDetector.detect(image.pixels()); }
            catch (RuntimeException exception) { verdict = ScanQrDetector.Verdict.UNCERTAIN; }
            if (verdict != ScanQrDetector.Verdict.NO_QR) {
                return new ExpenseScanResponse(ExpenseScanResponse.Outcome.UNSUPPORTED, null,
                        ExpenseScanResponse.ReasonCode.UNSUPPORTED_QR_FORMAT, true);
            }
            requireLive(started, cancelled);
            var claim = idempotency.claim(userId, scanKey, sourceImage);
            if (claim != ExpenseScanIdempotencyStore.ClaimResult.CLAIMED) throw new ClaimException(claim);
            claimed = true;
            dispatchGate.requireAvailable();
            requireLive(started, cancelled);
            ScanImageValidator.ValidatedImage workerImage = image;
            AtomicBoolean abandoned = new AtomicBoolean();
            Future<ExpenseScanResponse> future = workers.submit(() -> {
                try {
                    if (abandoned.get()) throw new ScanUnavailableException();
                    ExpenseScanResponse result = outputs.validate(extraction.extract(workerImage.sanitizedBytes()));
                    if (abandoned.get()) throw new ScanUnavailableException();
                    return result;
                }
                finally { workerImage.release(); slots.release(); }
            });
            transferSlot = true;
            image = null;
            long extractionStarted = System.nanoTime();
            try {
                long remaining = Math.min(extractionLimit.toNanos(),
                        totalLimit.toNanos() - (System.nanoTime() - started));
                while (remaining > 0) {
                    if (cancelled.getAsBoolean()) throw new ScanUnavailableException();
                    long slice = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100));
                    try {
                        ExpenseScanResponse result = future.get(slice, TimeUnit.NANOSECONDS);
                        requireLive(started, cancelled);
                        try { idempotency.complete(userId, scanKey, sourceImage); }
                        catch (RuntimeException exception) {
                            throw new ScanUnavailableException(ScanUnavailableException.Reason.TERMINAL);
                        }
                        claimed = false;
                        return result;
                    } catch (TimeoutException ignored) {
                        remaining = Math.min(extractionLimit.toNanos() - (System.nanoTime() - extractionStarted),
                                totalLimit.toNanos() - (System.nanoTime() - started));
                    }
                }
                throw new ScanUnavailableException(ScanUnavailableException.Reason.TIMEOUT);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ScanUnavailableException(ScanUnavailableException.Reason.TIMEOUT);
            } catch (ExecutionException exception) {
                throw new ScanUnavailableException(ScanUnavailableException.Reason.EXTRACTION);
            } finally {
                if (claimed) {
                    abandoned.set(true);
                }
            }
        } catch (RuntimeException failure) {
            if (claimed) {
                try { idempotency.fail(userId, scanKey, sourceImage); }
                catch (RuntimeException terminalFailure) {
                    throw new ScanUnavailableException(ScanUnavailableException.Reason.TERMINAL);
                }
            }
            throw failure;
        } finally {
            if (image != null) image.release();
            if (!transferSlot) slots.release();
        }
    }

    private void requireLive(long started, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || System.nanoTime() - started >= totalLimit.toNanos())
            throw new ScanUnavailableException(ScanUnavailableException.Reason.TIMEOUT);
    }

    public static final class ScanUnavailableException extends RuntimeException {
        private final Reason reason;
        public ScanUnavailableException() { this(Reason.CAPACITY); }
        public ScanUnavailableException(Reason reason) { this.reason = reason; }
        public Reason reason() { return reason; }
        public enum Reason { CAPACITY, TIMEOUT, EXTRACTION, TERMINAL }
    }
    public static final class ClaimException extends RuntimeException {
        private final ExpenseScanIdempotencyStore.ClaimResult result;
        ClaimException(ExpenseScanIdempotencyStore.ClaimResult result) { this.result = result; }
        public ExpenseScanIdempotencyStore.ClaimResult result() { return result; }
    }
}
