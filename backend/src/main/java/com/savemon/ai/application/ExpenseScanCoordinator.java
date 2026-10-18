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
import java.util.function.Supplier;
import java.util.Arrays;

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
    private static final Semaphore INSTANCE_SLOTS = new Semaphore(2);
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

    public ExpenseScanResponse scan(UUID userId, UUID scanKey,
                                    Supplier<SourceImage> lazyInput, BooleanSupplier cancelled) {
        long started = System.nanoTime();
        Objects.requireNonNull(userId);
        Objects.requireNonNull(scanKey);
        Objects.requireNonNull(lazyInput);
        Objects.requireNonNull(cancelled);
        entitlement.requireActivePaidEntitlement(userId);
        if (!INSTANCE_SLOTS.tryAcquire()) throw new ScanUnavailableException();
        Lease lease = new Lease();
        ScanImageValidator.ValidatedImage image = null;
        SourceImage source = null;
        boolean claimed = false;
        try {
            requireLive(started, cancelled);
            source = Objects.requireNonNull(lazyInput.get(), "Scan input is required.");
            requireLive(started, cancelled);
            image = images.validate(source.bytes(), source.contentType());
            requireLive(started, cancelled);
            ScanQrDetector.Verdict verdict;
            try { verdict = qrDetector.detect(image.pixels()); }
            catch (RuntimeException exception) { verdict = ScanQrDetector.Verdict.UNCERTAIN; }
            if (verdict != ScanQrDetector.Verdict.NO_QR) {
                return new ExpenseScanResponse(ExpenseScanResponse.Outcome.UNSUPPORTED, null,
                        ExpenseScanResponse.ReasonCode.UNSUPPORTED_QR_FORMAT, true);
            }
            requireLive(started, cancelled);
            var claim = idempotency.claim(userId, scanKey, source.bytes());
            if (claim != ExpenseScanIdempotencyStore.ClaimResult.CLAIMED) throw new ClaimException(claim);
            claimed = true;
            dispatchGate.requireAvailable();
            requireLive(started, cancelled);
            ScanImageValidator.ValidatedImage workerImage = image;
            AtomicBoolean abandoned = new AtomicBoolean();
            WorkerCancellation workerCancellation = new WorkerCancellation(abandoned);
            image = null;
            lease.workerStarting();
            Future<ExpenseScanResponse> future;
            try {
                future = workers.submit(() -> {
                    workerCancellation.started();
                    try {
                        if (abandoned.get()) throw new ScanUnavailableException();
                        ExpenseScanResponse result = outputs.validate(extraction.extract(workerImage.sanitizedBytes()));
                        if (abandoned.get()) throw new ScanUnavailableException();
                        return result;
                    } finally {
                        workerCancellation.finished();
                        workerImage.release();
                        lease.workerDone();
                    }
                });
            } catch (RuntimeException submissionFailure) {
                workerImage.release();
                lease.workerDone();
                throw new ScanUnavailableException(ScanUnavailableException.Reason.EXTRACTION);
            }
            long extractionStarted = System.nanoTime();
            try {
                long remaining = Math.min(extractionLimit.toNanos(),
                        totalLimit.toNanos() - (System.nanoTime() - started));
                while (remaining > 0) {
                    if (cancelled.getAsBoolean())
                        throw new ScanUnavailableException(ScanUnavailableException.Reason.TIMEOUT);
                    long slice = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100));
                    try {
                        ExpenseScanResponse result = future.get(slice, TimeUnit.NANOSECONDS);
                        requireLive(started, cancelled);
                        try { idempotency.complete(userId, scanKey, source.bytes()); }
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
                    workerCancellation.signal();
                }
            }
        } catch (RuntimeException failure) {
            if (claimed) {
                try { idempotency.fail(userId, scanKey, source.bytes()); }
                catch (RuntimeException terminalFailure) {
                    throw new ScanUnavailableException(ScanUnavailableException.Reason.TERMINAL);
                }
            }
            throw failure;
        } finally {
            if (image != null) image.release();
            if (source != null) source.release();
            lease.requestDone();
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
    public record SourceImage(byte[] bytes, String contentType) {
        public SourceImage {
            bytes = Objects.requireNonNull(bytes).clone();
        }
        private void release() { Arrays.fill(bytes, (byte) 0); }
    }

    /** Two-party lease: caller cleanup and actual worker termination must both occur. */
    private final class Lease {
        private final AtomicBoolean requestFinished = new AtomicBoolean();
        private final AtomicBoolean workerFinished = new AtomicBoolean(true);
        private final AtomicBoolean released = new AtomicBoolean();
        private void workerStarting() { workerFinished.set(false); }
        private void workerDone() { workerFinished.set(true); releaseIfReady(); }
        private void requestDone() { requestFinished.set(true); releaseIfReady(); }
        private void releaseIfReady() {
            if (requestFinished.get() && workerFinished.get() && released.compareAndSet(false, true))
                INSTANCE_SLOTS.release();
        }
    }

    /** Interrupt only while the task still owns its worker thread, never a reused pool thread. */
    private static final class WorkerCancellation {
        private final AtomicBoolean abandoned;
        private Thread running;
        private WorkerCancellation(AtomicBoolean abandoned) { this.abandoned = abandoned; }
        private synchronized void started() {
            running = Thread.currentThread();
            if (abandoned.get()) running.interrupt();
        }
        private synchronized void signal() { if (running != null) running.interrupt(); }
        private synchronized void finished() { running = null; }
    }
    public static final class ClaimException extends RuntimeException {
        private final ExpenseScanIdempotencyStore.ClaimResult result;
        ClaimException(ExpenseScanIdempotencyStore.ClaimResult result) { this.result = result; }
        public ExpenseScanIdempotencyStore.ClaimResult result() { return result; }
    }
}
