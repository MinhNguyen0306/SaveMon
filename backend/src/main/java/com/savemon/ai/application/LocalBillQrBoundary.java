package com.savemon.ai.application;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.LuminanceSource;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Bounded, local QR gate. A decoder miss is uncertainty, not proof of no QR:
 * neither a miss nor any decoded payload can enter OCR while format approval is absent.
 */
public final class LocalBillQrBoundary implements ScanQrDetector {
    private final BillQrAllowlist allowlist;

    public LocalBillQrBoundary(BillQrAllowlist allowlist) {
        this.allowlist = Objects.requireNonNull(allowlist);
    }

    public Decision inspect(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        if (image.getWidth() <= 0 || image.getHeight() <= 0
                || (long) image.getWidth() * image.getHeight() > 12_000_000L) {
            throw new IllegalArgumentException("Image pixel count is unsupported.");
        }
        try {
            Result result = new QRCodeReader().decode(new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image))));
            return allowlist.classifyDecodedPayload(result.getText())
                    == BillQrAllowlist.Decision.UNSUPPORTED_QR_FORMAT
                    ? Decision.UNSUPPORTED_QR : Decision.UNCERTAIN;
        } catch (NotFoundException | ChecksumException | FormatException | RuntimeException exception) {
            return Decision.UNCERTAIN;
        }
    }

    @Override
    public Verdict detect(BufferedImage image) {
        return inspect(image) == Decision.UNSUPPORTED_QR ? Verdict.CANDIDATE : Verdict.UNCERTAIN;
    }

    public enum Decision { UNSUPPORTED_QR, UNCERTAIN }

    private static final class BufferedImageLuminanceSource extends LuminanceSource {
        private final BufferedImage image;

        private BufferedImageLuminanceSource(BufferedImage image) {
            super(image.getWidth(), image.getHeight());
            this.image = image;
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            if (row == null || row.length < getWidth()) row = new byte[getWidth()];
            for (int x = 0; x < getWidth(); x++) row[x] = luminance(image.getRGB(x, y));
            return row;
        }

        @Override
        public byte[] getMatrix() {
            byte[] pixels = new byte[getWidth() * getHeight()];
            for (int y = 0; y < getHeight(); y++) {
                for (int x = 0; x < getWidth(); x++) {
                    pixels[y * getWidth() + x] = luminance(image.getRGB(x, y));
                }
            }
            return pixels;
        }

        private byte luminance(int rgb) {
            int r = (rgb >>> 16) & 0xff;
            int g = (rgb >>> 8) & 0xff;
            int b = rgb & 0xff;
            return (byte) ((r * 306 + g * 601 + b * 117) >>> 10);
        }
    }
}
