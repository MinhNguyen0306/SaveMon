package com.savemon.ai.application;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/** Isolated in-memory image validation. No servlet upload path or disk cache is involved. */
public final class ScanImageValidator {
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final long MAX_PIXELS = 12_000_000L;

    public ValidatedImage validate(byte[] source, String contentType) {
        if (source == null || source.length == 0) throw new InvalidImageException();
        if (source.length > MAX_FILE_BYTES) throw new ImageTooLargeException();
        String format = sniff(source);
        if (format == null || !(format.equals("jpeg") && "image/jpeg".equals(contentType)
                || format.equals("png") && "image/png".equals(contentType))) throw new InvalidImageException();
        if (format.equals("png") && containsApngChunk(source)) throw new InvalidImageException();

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidImageException();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
                    throw new InvalidImageException();
                if (reader.getNumImages(true) != 1) throw new InvalidImageException();
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height)
                    throw new InvalidImageException();
                BufferedImage clean = new BufferedImage(width, height,
                        format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = clean.createGraphics();
                try { graphics.drawImage(decoded, 0, 0, null); }
                finally { graphics.dispose(); decoded.flush(); }
                byte[] sanitized = encode(clean, format);
                return new ValidatedImage(clean, sanitized);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidImageException invalid) throw invalid;
            if (exception instanceof ImageTooLargeException tooLarge) throw tooLarge;
            throw new InvalidImageException();
        }
    }

    private static byte[] encode(BufferedImage clean, String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new InvalidImageException();
        ImageWriter writer = writers.next();
        var bytes = new BoundedOutputStream(MAX_FILE_BYTES);
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(clean);
            output.flush();
            return bytes.toByteArray();
        } finally { writer.dispose(); }
    }

    private static String sniff(byte[] source) {
        if (source.length >= 3 && (source[0] & 255) == 255 && (source[1] & 255) == 216
                && (source[2] & 255) == 255) return "jpeg";
        byte[] png = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (source.length >= 8) {
            boolean match = true;
            for (int i = 0; i < 8; i++) match &= source[i] == png[i];
            if (match) return "png";
        }
        return null;
    }

    private static boolean containsApngChunk(byte[] bytes) {
        int offset = 8;
        while (offset + 12 <= bytes.length) {
            long length = ((long)(bytes[offset] & 255) << 24) | ((long)(bytes[offset + 1] & 255) << 16)
                    | ((long)(bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
            if (length > bytes.length - offset - 12L) return true;
            if (bytes[offset + 4] == 'a' && bytes[offset + 5] == 'c'
                    && bytes[offset + 6] == 'T' && bytes[offset + 7] == 'L') return true;
            offset += (int) length + 12;
        }
        return offset != bytes.length;
    }

    public record ValidatedImage(BufferedImage pixels, byte[] sanitizedBytes) {
        public void release() { pixels.flush(); java.util.Arrays.fill(sanitizedBytes, (byte) 0); }
    }
    public static final class InvalidImageException extends RuntimeException { }
    public static final class ImageTooLargeException extends RuntimeException { }

    private static final class BoundedOutputStream extends ByteArrayOutputStream {
        private final int limit;
        private BoundedOutputStream(int limit) { this.limit = limit; }
        @Override public synchronized void write(int value) {
            if (count >= limit) throw new ImageTooLargeException();
            super.write(value);
        }
        @Override public synchronized void write(byte[] values, int offset, int length) {
            if (length > limit - count) throw new ImageTooLargeException();
            super.write(values, offset, length);
        }
    }
}
