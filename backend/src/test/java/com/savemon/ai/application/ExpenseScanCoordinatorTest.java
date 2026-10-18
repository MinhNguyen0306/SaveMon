package com.savemon.ai.application;

import com.savemon.ai.interfaces.ExpenseScanResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseScanCoordinatorTest {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private final byte[] image = png();
    private final UUID user = UUID.randomUUID();

    @AfterEach void close() throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test void producesOnlyEditableDraftAfterClaimAndTerminalSuccess() {
        Store store = new Store();
        var pipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("125000", "VND", "2026-10-17", "Quán ăn"));
        UUID key = UUID.randomUUID();
        ExpenseScanResponse response = pipeline.scan(user, key, input(image, "image/png"), () -> false);
        assertThat(response.outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
        assertThat(response.draft().paidTotal()).isEqualTo("125000");
        assertThat(store.status).isEqualTo("COMPLETED");
        assertThat(store.persistedImage).isNull();
        assertThatThrownBy(() -> pipeline.scan(user, key, input(image, "image/png"), () -> false))
                .isInstanceOf(ExpenseScanCoordinator.ClaimException.class)
                .extracting("result").isEqualTo(ExpenseScanIdempotencyStore.ClaimResult.ALREADY_PROCESSED);
    }

    @Test void qrUncertaintyAndCandidateNeverDispatch() {
        AtomicInteger calls = new AtomicInteger();
        for (ScanQrDetector.Verdict verdict : new ScanQrDetector.Verdict[]{
                ScanQrDetector.Verdict.CANDIDATE, ScanQrDetector.Verdict.UNCERTAIN}) {
            Store store = new Store();
            var pipeline = pipeline(store, ignored -> verdict, ignored -> {
                calls.incrementAndGet(); return null;
            });
            var response = pipeline.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false);
            assertThat(response.reasonCode()).isEqualTo(ExpenseScanResponse.ReasonCode.UNSUPPORTED_QR_FORMAT);
            assertThat(store.status).isNull();
        }
        assertThat(calls).hasValue(0);
    }

    @Test void invalidImageNeverClaimsOrExtracts() {
        Store store = new Store();
        AtomicInteger calls = new AtomicInteger();
        var pipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> { calls.incrementAndGet(); return null; });
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), input(image, "image/jpeg"), () -> false))
                .isInstanceOf(ScanImageValidator.InvalidImageException.class);
        assertThat(store.status).isNull();
        assertThat(calls).hasValue(0);
    }

    @Test void deniedEntitlementDoesNotReadOrClaimImage() {
        Store store = new Store();
        AtomicInteger reads = new AtomicInteger();
        var gate = new ExpenseScanEntitlementGate(ignored -> new PremiumEntitlementQuery.Entitlement(
                PremiumEntitlementQuery.Status.INACTIVE, PremiumEntitlementQuery.BillingInterval.MONTHLY,
                clock.instant().plusSeconds(3600)), clock);
        var pipeline = new ExpenseScanCoordinator(gate, new ScanImageValidator(),
                ignored -> { throw new AssertionError("QR must not run"); },
                new ExpenseScanIdempotency(store, clock), ignored -> {
                    throw new AssertionError("Extractor must not run");
                }, new ScanOutputValidator(), () -> {}, workers);
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), () -> {
            reads.incrementAndGet();
            return new ExpenseScanCoordinator.SourceImage(new byte[0], "image/png");
        }, () -> false))
                .isInstanceOf(ExpenseScanEntitlementGate.PremiumRequiredException.class);
        assertThat(store.status).isNull();
        assertThat(reads).hasValue(0);
    }

    @Test void cancellationBeforeLazyInputNeverReads() {
        AtomicInteger reads = new AtomicInteger();
        var pipeline = pipeline(new Store(), ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> { throw new AssertionError("Extractor must not run"); });
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), () -> {
            reads.incrementAndGet();
            return new ExpenseScanCoordinator.SourceImage(image, "image/png");
        }, () -> true)).isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class);
        assertThat(reads).hasValue(0);
    }

    @Test void activeEntitlementAtAdmissionIsCheckedExactlyOnceDespiteLaterChange() {
        AtomicInteger queries = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean(true);
        var gate = new ExpenseScanEntitlementGate(ignored -> {
            queries.incrementAndGet();
            return new PremiumEntitlementQuery.Entitlement(
                    active.get() ? PremiumEntitlementQuery.Status.ACTIVE : PremiumEntitlementQuery.Status.INACTIVE,
                    PremiumEntitlementQuery.BillingInterval.MONTHLY, clock.instant().plusSeconds(3600));
        }, clock);
        var pipeline = new ExpenseScanCoordinator(gate, new ScanImageValidator(),
                ignored -> ScanQrDetector.Verdict.NO_QR,
                new ExpenseScanIdempotency(new Store(), clock),
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"),
                new ScanOutputValidator(), () -> {}, workers);
        var response = pipeline.scan(user, UUID.randomUUID(), () -> {
            active.set(false);
            return new ExpenseScanCoordinator.SourceImage(image, "image/png");
        }, () -> false);
        assertThat(response.outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
        assertThat(queries).hasValue(1);
    }

    @Test void twoSlowReadersHoldBothSlotsAndThirdCannotRead() throws Exception {
        Store store = new Store();
        var firstPipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"));
        var secondPipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"));
        var thirdPipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger thirdReads = new AtomicInteger();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var slowInput = (java.util.function.Supplier<ExpenseScanCoordinator.SourceImage>) () -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return new ExpenseScanCoordinator.SourceImage(image, "image/png");
            };
            var first = callers.submit(() -> firstPipeline.scan(user, UUID.randomUUID(), slowInput, () -> false));
            var second = callers.submit(() -> secondPipeline.scan(user, UUID.randomUUID(), slowInput, () -> false));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> thirdPipeline.scan(user, UUID.randomUUID(), () -> {
                thirdReads.incrementAndGet();
                return new ExpenseScanCoordinator.SourceImage(image, "image/png");
            }, () -> false)).isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class)
                    .extracting("reason").isEqualTo(ExpenseScanCoordinator.ScanUnavailableException.Reason.CAPACITY);
            assertThat(thirdReads).hasValue(0);
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
            assertThat(second.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
        } finally { release.countDown(); callers.shutdownNow(); }
    }

    @Test void cancelledBlockedReaderRetainsLeaseUntilReaderStopsThenReleases() throws Exception {
        AtomicInteger extractionCalls = new AtomicInteger();
        var pipeline = pipeline(new Store(), ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> { extractionCalls.incrementAndGet();
                    return new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"); });
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            var result = callers.submit(() -> assertThatThrownBy(() -> pipeline.scan(user,
                    UUID.randomUUID(), () -> {
                        entered.countDown();
                        try { release.await(); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                        return new ExpenseScanCoordinator.SourceImage(image, "image/png");
                    }, cancelled::get)).isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            cancelled.set(true);
            assertThat(extractionCalls).hasValue(0);
            release.countDown();
            result.get(2, TimeUnit.SECONDS);
            assertThat(extractionCalls).hasValue(0);
            assertThat(pipeline.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false).outcome())
                    .isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
        } finally { release.countDown(); callers.shutdownNow(); }
    }

    @Test void parserFailuresAndRejectedWorkerSubmissionDoNotLeakSlots() {
        Store store = new Store();
        var pipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"));
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(),
                () -> { throw new ScanImageValidator.InvalidImageException(); }, () -> false))
                .isInstanceOf(ScanImageValidator.InvalidImageException.class);
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(),
                () -> { throw new IllegalStateException("reader failure"); }, () -> false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.status).isNull();
        assertThat(pipeline.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false).outcome())
                .isEqualTo(ExpenseScanResponse.Outcome.DRAFT);

        ExecutorService rejected = Executors.newSingleThreadExecutor();
        rejected.shutdownNow();
        Store rejectedStore = new Store();
        var rejectedPipeline = new ExpenseScanCoordinator(paid(), new ScanImageValidator(),
                ignored -> ScanQrDetector.Verdict.NO_QR,
                new ExpenseScanIdempotency(rejectedStore, clock),
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"),
                new ScanOutputValidator(), () -> {}, rejected);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> rejectedPipeline.scan(user, UUID.randomUUID(),
                    input(image, "image/png"), () -> false))
                    .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class)
                    .extracting("reason").isEqualTo(ExpenseScanCoordinator.ScanUnavailableException.Reason.EXTRACTION);
        }
        assertThat(rejectedStore.failures).hasValue(3);
    }

    @Test void terminalCasPendingKeepsSlotAfterWorkerHasEnded() throws Exception {
        Store store = new Store();
        store.completeEntered = new CountDownLatch(1);
        store.completeGate = new CountDownLatch(1);
        CountDownLatch extracted = new CountDownLatch(2);
        var pipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> {
                    extracted.countDown();
                    return new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe");
                });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> pipeline.scan(user, UUID.randomUUID(),
                    input(image, "image/png"), () -> false));
            var second = callers.submit(() -> pipeline.scan(user, UUID.randomUUID(),
                    input(image, "image/png"), () -> false));
            assertThat(extracted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(store.completeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            AtomicInteger thirdReads = new AtomicInteger();
            assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), () -> {
                thirdReads.incrementAndGet();
                return new ExpenseScanCoordinator.SourceImage(image, "image/png");
            }, () -> false)).isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class)
                    .extracting("reason").isEqualTo(ExpenseScanCoordinator.ScanUnavailableException.Reason.CAPACITY);
            assertThat(thirdReads).hasValue(0);
            store.completeGate.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
            assertThat(second.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(ExpenseScanResponse.Outcome.DRAFT);
        } finally { store.completeGate.countDown(); callers.shutdownNow(); }
    }

    @Test void detectorErrorFailsClosedAndExtractorFailureMakesTerminalClaim() {
        Store qrStore = new Store();
        var qrFailure = pipeline(qrStore, ignored -> { throw new IllegalStateException(); },
                ignored -> { throw new AssertionError("Extractor must not run"); });
        assertThat(qrFailure.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false).reasonCode())
                .isEqualTo(ExpenseScanResponse.ReasonCode.UNSUPPORTED_QR_FORMAT);
        assertThat(qrStore.status).isNull();

        Store extractionStore = new Store();
        var extractionFailure = pipeline(extractionStore, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> { throw new IllegalStateException("provider failure"); });
        assertThatThrownBy(() -> extractionFailure.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false))
                .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class);
        assertThat(extractionStore.status).isEqualTo("FAILED");
    }

    @Test void failedTerminalCompleteCannotReturnDraft() {
        Store store = new Store();
        store.rejectComplete = true;
        var pipeline = pipeline(store, ignored -> ScanQrDetector.Verdict.NO_QR,
                ignored -> new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe"));
        assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), input(image, "image/png"), () -> false))
                .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class);
        assertThat(store.status).isEqualTo("FAILED");
    }

    @Test void timeoutFailsClaimAndKeepsSlotUntilUnderlyingCallEnds() throws Exception {
        Store store = new Store();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var pipeline = new ExpenseScanCoordinator(
                paid(), new ScanImageValidator(), ignored -> ScanQrDetector.Verdict.NO_QR,
                new ExpenseScanIdempotency(store, clock), ignored -> {
                    entered.countDown();
                    boolean done = false;
                    while (!done) {
                        try { release.await(); done = true; }
                        catch (InterruptedException ignoredInterrupt) { /* fake uninterruptible provider */ }
                    }
                    return new BillImageExtraction.ExtractionResult("100", "VND", "2026-10-17", "Cafe");
                }, new ScanOutputValidator(), () -> {}, workers,
                Duration.ofMillis(800), Duration.ofMillis(200));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> assertThatThrownBy(() -> pipeline.scan(
                    user, UUID.randomUUID(), input(image, "image/png"), () -> false))
                    .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class));
            var second = callers.submit(() -> assertThatThrownBy(() -> pipeline.scan(
                    user, UUID.randomUUID(), input(image, "image/png"), () -> false))
                    .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            AtomicInteger thirdReads = new AtomicInteger();
            assertThatThrownBy(() -> pipeline.scan(user, UUID.randomUUID(), () -> {
                thirdReads.incrementAndGet();
                return new ExpenseScanCoordinator.SourceImage(image, "image/png");
            }, () -> false))
                    .isInstanceOf(ExpenseScanCoordinator.ScanUnavailableException.class);
            assertThat(thirdReads).hasValue(0);
            assertThat(store.failures.get()).isEqualTo(2);
        } finally { release.countDown(); callers.shutdownNow(); }
    }

    private ExpenseScanCoordinator pipeline(Store store, ScanQrDetector detector,
                                            BillImageExtraction extractor) {
        return new ExpenseScanCoordinator(paid(), new ScanImageValidator(), detector,
                new ExpenseScanIdempotency(store, clock), extractor, new ScanOutputValidator(), () -> {}, workers);
    }

    private java.util.function.Supplier<ExpenseScanCoordinator.SourceImage> input(byte[] bytes, String contentType) {
        return () -> new ExpenseScanCoordinator.SourceImage(bytes, contentType);
    }

    private ExpenseScanEntitlementGate paid() {
        return new ExpenseScanEntitlementGate(ignored -> new PremiumEntitlementQuery.Entitlement(
                PremiumEntitlementQuery.Status.ACTIVE, PremiumEntitlementQuery.BillingInterval.MONTHLY,
                clock.instant().plusSeconds(3600)), clock);
    }

    private static byte[] png() {
        try {
            var out = new ByteArrayOutputStream();
            var pixels = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(pixels, "png", out);
            return out.toByteArray();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static final class Store implements ExpenseScanIdempotencyStore {
        private final Map<UUID, String> fingerprints = new HashMap<>();
        private final Map<UUID, String> statuses = new HashMap<>();
        private String status;
        private byte[] persistedImage;
        private final AtomicInteger failures = new AtomicInteger();
        private boolean rejectComplete;
        private CountDownLatch completeEntered;
        private CountDownLatch completeGate;
        @Override public synchronized ClaimResult claim(UUID userId, UUID key, String hash,
                Instant now, Instant expiresAt, Instant staleBefore) {
            if (!fingerprints.containsKey(key)) {
                fingerprints.put(key, hash); statuses.put(key, "IN_PROGRESS");
                status = "IN_PROGRESS"; return ClaimResult.CLAIMED;
            }
            if (!fingerprints.get(key).equals(hash)) return ClaimResult.REQUEST_MISMATCH;
            return "IN_PROGRESS".equals(statuses.get(key))
                    ? ClaimResult.IN_PROGRESS : ClaimResult.ALREADY_PROCESSED;
        }
        @Override public synchronized void complete(UUID userId, UUID key, String hash) {
            if (completeEntered != null) completeEntered.countDown();
            if (completeGate != null) {
                try { completeGate.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            if (rejectComplete) throw new IllegalStateException("terminal CAS failed");
            statuses.put(key, "COMPLETED"); status = "COMPLETED";
        }
        @Override public synchronized void fail(UUID userId, UUID key, String hash) {
            failures.incrementAndGet(); statuses.put(key, "FAILED"); status = "FAILED";
        }
    }
}
