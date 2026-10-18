package com.savemon.ai.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.savemon.ai.application.BillImageExtraction;
import com.savemon.ai.application.ExpenseScanCoordinator;
import com.savemon.ai.application.ExpenseScanEntitlementGate;
import com.savemon.ai.application.ExpenseScanIdempotency;
import com.savemon.ai.application.ExpenseScanIdempotencyStore;
import com.savemon.ai.application.PremiumEntitlementQuery;
import com.savemon.ai.application.ScanImageValidator;
import com.savemon.ai.application.ScanOutputValidator;
import com.savemon.ai.application.ScanQrDetector;
import com.savemon.shared.interfaces.exception.ApiErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only in-memory wire sketch. It is not a servlet route or an ingress-safety proof. */
class ExpenseScanInMemoryHttpHarnessTest {
    private static final String PATH = "/api/v1/expense-scans";
    private static final int MAX_REQUEST_BYTES = 8 * 1024 * 1024 + 256 * 1024;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final byte[] png = png();

    @AfterEach void close() { workers.shutdownNow(); }

    @Test void exactPathHeaderSingleImageAndDraftShape() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        var harness = harness(paid(), new Store());
        var result = harness.handle(request(UUID.randomUUID(), UUID.randomUUID().toString(),
                List.of(part("image", "image/png", png, reads))));
        assertThat(result.status()).isEqualTo(200);
        JsonNode body = json.readTree(result.body());
        assertThat(body.get("outcome").asText()).isEqualTo("DRAFT");
        assertThat(body.get("draft").get("paidTotal").asText()).isEqualTo("125000");
        assertThat(body.get("draft").get("currency").asText()).isEqualTo("VND");
        assertThat(body.get("draft").get("transactionDate").asText()).isEqualTo("2026-10-17");
        assertThat(body.get("manualEntryAllowed").asBoolean()).isTrue();
        assertThat(body.has("reasonCode")).isFalse();
        assertThat(reads).hasValue(1);
    }

    @Test void authAndEntitlementDenialsPrecedeHeaderAndBody() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        var request = request(null, "not-a-uuid", List.of(part("image", "image/png", png, reads)));
        assertError(harness(paid(), new Store()).handle(request), 401, "AUTHENTICATION_FAILED");
        assertThat(reads).hasValue(0);

        UUID subject = UUID.randomUUID();
        request = request(subject, "not-a-uuid", List.of(part("image", "image/png", png, reads)));
        assertError(harness(ignored -> new PremiumEntitlementQuery.Entitlement(
                PremiumEntitlementQuery.Status.INACTIVE, PremiumEntitlementQuery.BillingInterval.MONTHLY,
                clock.instant().plusSeconds(3600)), new Store()).handle(request), 403, "PREMIUM_REQUIRED");
        assertError(harness(ignored -> { throw new IllegalStateException(); }, new Store()).handle(request),
                503, "PREMIUM_ENTITLEMENT_UNAVAILABLE");
        assertThat(reads).hasValue(0);
    }

    @Test void rejectsNonExactRouteMethodPartsAndHeaderBeforeBodyRead() throws Exception {
        var harness = harness(paid(), new Store());
        AtomicInteger reads = new AtomicInteger();
        UUID subject = UUID.randomUUID();
        var oneImage = List.of(part("image", "image/png", png, reads));
        assertError(harness.handle(new Request("POST", PATH + "/confirm", subject,
                UUID.randomUUID().toString(), "multipart/form-data", oneImage, 100)),
                503, "SCAN_CAPACITY_UNAVAILABLE");
        assertError(harness.handle(new Request("GET", PATH, subject,
                UUID.randomUUID().toString(), "multipart/form-data", oneImage, 100)),
                503, "SCAN_CAPACITY_UNAVAILABLE");
        assertError(harness.handle(request(subject, "bad", oneImage)), 400, "VALIDATION_ERROR");
        assertError(harness.handle(request(subject, UUID.randomUUID().toString(),
                List.of(part("file", "image/png", png, reads)))), 400, "VALIDATION_ERROR");
        assertError(harness.handle(request(subject, UUID.randomUUID().toString(),
                List.of(oneImage.get(0), part("other", "image/png", png, reads)))),
                400, "VALIDATION_ERROR");
        assertThat(reads).hasValue(0);
    }

    @Test void rejectsRequestSizeMediaAndSignatureWithCatalogErrors() throws Exception {
        var harness = harness(paid(), new Store());
        AtomicInteger reads = new AtomicInteger();
        UUID subject = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        var original = request(subject, key, List.of(part("image", "image/png", png, reads)));
        assertError(harness.handle(new Request("POST", PATH, subject, key,
                "multipart/form-data", original.parts(), MAX_REQUEST_BYTES + 1)),
                413, "SCAN_IMAGE_TOO_LARGE");
        assertError(harness.handle(request(subject, UUID.randomUUID().toString(),
                List.of(part("image", "application/pdf", png, reads)))),
                415, "SCAN_UNSUPPORTED_MEDIA_TYPE");
        assertThat(reads).hasValue(0);
        assertError(harness.handle(request(subject, UUID.randomUUID().toString(),
                List.of(part("image", "image/jpeg", png, reads)))),
                400, "VALIDATION_ERROR");
        assertThat(reads).hasValue(1);
    }

    @Test void mapsConcurrentMismatchAndTerminalReplayWithoutDraftRecovery() throws Exception {
        Store store = new Store();
        var harness = harness(paid(), store);
        UUID subject = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(png));
        store.claim(subject, key, hash, clock.instant(), clock.instant().plusSeconds(3600), clock.instant());
        assertError(harness.handle(request(subject, key.toString(),
                List.of(part("image", "image/png", png, new AtomicInteger())))),
                409, "IDEMPOTENCY_IN_PROGRESS");
        assertThat(harness.lastHeaders).containsEntry("Retry-After", "2");
        store.complete(subject, key, hash);
        assertError(harness.handle(request(subject, key.toString(),
                List.of(part("image", "image/png", png, new AtomicInteger())))),
                409, "SCAN_ALREADY_PROCESSED");
        assertThat(harness.lastBody).doesNotContain("draft");
        // A distinct valid image is needed so image validation reaches the claim.
        byte[] other = pngWithPixel(0x00ffffff);
        assertError(harness.handle(request(subject, key.toString(),
                List.of(part("image", "image/png", other, new AtomicInteger())))),
                409, "IDEMPOTENCY_REQUEST_MISMATCH");
    }

    @Test void extractionFailureUsesCatalogCodeWithoutReturningDraft() throws Exception {
        var harness = harness(paid(), new Store(), ignored -> {
            throw new IllegalStateException("provider unavailable");
        });
        var result = harness.handle(request(UUID.randomUUID(), UUID.randomUUID().toString(),
                List.of(part("image", "image/png", png, new AtomicInteger()))));
        assertError(result, 503, "BILL_EXTRACTION_UNAVAILABLE");
        assertThat(result.body()).doesNotContain("draft");
    }

    private PremiumEntitlementQuery paid() {
        return ignored -> new PremiumEntitlementQuery.Entitlement(
                PremiumEntitlementQuery.Status.ACTIVE, PremiumEntitlementQuery.BillingInterval.MONTHLY,
                clock.instant().plusSeconds(3600));
    }

    private Harness harness(PremiumEntitlementQuery subscription, Store store) {
        return harness(subscription, store,
                ignored -> new BillImageExtraction.ExtractionResult("125000", "VND", "2026-10-17", "Cafe"));
    }

    private Harness harness(PremiumEntitlementQuery subscription, Store store,
                            BillImageExtraction extractor) {
        var entitlement = new ExpenseScanEntitlementGate(subscription, clock);
        var coordinator = new ExpenseScanCoordinator(entitlement, new ScanImageValidator(),
                ignored -> ScanQrDetector.Verdict.NO_QR,
                new ExpenseScanIdempotency(store, clock), extractor,
                new ScanOutputValidator(), () -> {}, workers);
        return new Harness(entitlement, coordinator);
    }

    private Request request(UUID subject, String key, List<Part> parts) {
        return new Request("POST", PATH, subject, key, "multipart/form-data", parts, 100 +
                parts.stream().mapToInt(part -> part.size()).sum());
    }

    private static Part part(String name, String mediaType, byte[] bytes, AtomicInteger reads) {
        return new Part(name, mediaType, bytes.length, () -> {
            reads.incrementAndGet(); return bytes;
        });
    }

    private void assertError(Result response, int status, String code) throws Exception {
        assertThat(response.status()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(code);
    }

    private static byte[] png() { return pngWithPixel(0); }
    private static byte[] pngWithPixel(int rgb) {
        try {
            var out = new ByteArrayOutputStream();
            var pixels = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            pixels.setRGB(0, 0, rgb);
            ImageIO.write(pixels, "png", out);
            return out.toByteArray();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private record Request(String method, String path, UUID subject, String key,
                           String contentType, List<Part> parts, int requestBytes) { }
    private record Part(String name, String mediaType, int size, Supplier<byte[]> body) { }
    private record Result(int status, String body, Map<String, String> headers) { }

    private final class Harness {
        private final ExpenseScanEntitlementGate entitlement;
        private final ExpenseScanCoordinator coordinator;
        private String lastBody;
        private Map<String, String> lastHeaders;
        private Harness(ExpenseScanEntitlementGate entitlement, ExpenseScanCoordinator coordinator) {
            this.entitlement = entitlement; this.coordinator = coordinator;
        }
        private Result handle(Request request) throws Exception {
            if (!"POST".equals(request.method()) || !PATH.equals(request.path()))
                return error(503, "SCAN_CAPACITY_UNAVAILABLE", Map.of());
            if (request.subject() == null) return error(401, "AUTHENTICATION_FAILED", Map.of());
            try { entitlement.requireActivePaidEntitlement(request.subject()); }
            catch (ExpenseScanEntitlementGate.PremiumRequiredException exception) {
                return error(403, "PREMIUM_REQUIRED", Map.of());
            } catch (ExpenseScanEntitlementGate.EntitlementUnavailableException exception) {
                return error(503, "PREMIUM_ENTITLEMENT_UNAVAILABLE", Map.of());
            }
            UUID key;
            try { key = ExpenseScanRequest.fromIdempotencyHeader(request.key()).idempotencyKey(); }
            catch (IllegalArgumentException exception) { return error(400, "VALIDATION_ERROR", Map.of()); }
            if (!"multipart/form-data".equals(request.contentType())
                    || request.parts().size() != 1 || !"image".equals(request.parts().get(0).name()))
                return error(400, "VALIDATION_ERROR", Map.of());
            Part part = request.parts().get(0);
            if (request.requestBytes() > MAX_REQUEST_BYTES || part.size() > ScanImageValidator.MAX_FILE_BYTES)
                return error(413, "SCAN_IMAGE_TOO_LARGE", Map.of());
            if (!"image/png".equals(part.mediaType()) && !"image/jpeg".equals(part.mediaType()))
                return error(415, "SCAN_UNSUPPORTED_MEDIA_TYPE", Map.of());
            try {
                ExpenseScanResponse response = coordinator.scan(request.subject(), key, part.body().get(),
                        part.mediaType(), () -> false);
                return result(200, json.writeValueAsString(response), Map.of());
            } catch (ScanImageValidator.InvalidImageException exception) {
                return error(400, "VALIDATION_ERROR", Map.of());
            } catch (ScanImageValidator.ImageTooLargeException exception) {
                return error(413, "SCAN_IMAGE_TOO_LARGE", Map.of());
            } catch (ExpenseScanCoordinator.ClaimException exception) {
                return switch (exception.result()) {
                    case IN_PROGRESS -> error(409, "IDEMPOTENCY_IN_PROGRESS", Map.of("Retry-After", "2"));
                    case REQUEST_MISMATCH -> error(409, "IDEMPOTENCY_REQUEST_MISMATCH", Map.of());
                    case ALREADY_PROCESSED -> error(409, "SCAN_ALREADY_PROCESSED", Map.of());
                    case CLAIMED -> throw new AssertionError("CLAIMED is not an error");
                };
            } catch (ExpenseScanCoordinator.ScanUnavailableException exception) {
                return switch (exception.reason()) {
                    case CAPACITY -> error(503, "SCAN_CAPACITY_UNAVAILABLE", Map.of());
                    case TIMEOUT -> error(504, "BILL_EXTRACTION_TIMEOUT", Map.of());
                    case EXTRACTION, TERMINAL -> error(503, "BILL_EXTRACTION_UNAVAILABLE", Map.of());
                };
            }
        }
        private Result error(int status, String code, Map<String, String> headers) throws Exception {
            return result(status, json.writeValueAsString(
                    new ApiErrorResponse(code, code, null, null)), headers);
        }
        private Result result(int status, String body, Map<String, String> headers) {
            lastBody = body; lastHeaders = headers;
            return new Result(status, body, headers);
        }
    }

    private static final class Store implements ExpenseScanIdempotencyStore {
        private final java.util.Map<UUID, String> hashes = new java.util.HashMap<>();
        private final java.util.Map<UUID, String> statuses = new java.util.HashMap<>();
        @Override public synchronized ClaimResult claim(UUID user, UUID key, String hash,
                Instant now, Instant expires, Instant stale) {
            if (!hashes.containsKey(key)) {
                hashes.put(key, hash); statuses.put(key, "IN_PROGRESS"); return ClaimResult.CLAIMED;
            }
            if (!hashes.get(key).equals(hash)) return ClaimResult.REQUEST_MISMATCH;
            return "IN_PROGRESS".equals(statuses.get(key))
                    ? ClaimResult.IN_PROGRESS : ClaimResult.ALREADY_PROCESSED;
        }
        @Override public synchronized void complete(UUID user, UUID key, String hash) {
            statuses.put(key, "COMPLETED");
        }
        @Override public synchronized void fail(UUID user, UUID key, String hash) {
            statuses.put(key, "FAILED");
        }
    }
}
