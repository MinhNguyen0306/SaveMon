package com.savemon.ai.application;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanImageValidatorTest {
    private final ScanImageValidator validator = new ScanImageValidator();

    @Test void acceptsMatchingStillPngAndReencodesToFreshBytes() throws Exception {
        byte[] source = png(3, 2);
        var validated = validator.validate(source, "image/png");
        assertThat(validated.pixels().getWidth()).isEqualTo(3);
        assertThat(validated.sanitizedBytes()).isNotSameAs(source);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(validated.sanitizedBytes())).getHeight())
                .isEqualTo(2);
        validated.release();
        assertThat(validated.sanitizedBytes()).containsOnly((byte) 0);
    }

    @Test void rejectsMimeMismatchOversizeAndAnimatedPngMarker() throws Exception {
        byte[] source = png(2, 2);
        assertThatThrownBy(() -> validator.validate(source, "image/jpeg"))
                .isInstanceOf(ScanImageValidator.InvalidImageException.class);
        assertThatThrownBy(() -> validator.validate(new byte[ScanImageValidator.MAX_FILE_BYTES + 1], "image/png"))
                .isInstanceOf(ScanImageValidator.ImageTooLargeException.class);
        // Insert a zero-length acTL chunk before IEND: the animation marker is rejected
        // before raster decode even when the rest of the file is otherwise a still PNG.
        int iend = source.length - 12;
        byte[] animated = Arrays.copyOf(source, source.length + 12);
        System.arraycopy(source, iend, animated, iend + 12, 12);
        Arrays.fill(animated, iend, iend + 12, (byte) 0);
        animated[iend + 4] = 'a'; animated[iend + 5] = 'c';
        animated[iend + 6] = 'T'; animated[iend + 7] = 'L';
        assertThatThrownBy(() -> validator.validate(animated, "image/png"))
                .isInstanceOf(ScanImageValidator.InvalidImageException.class);
    }

    private static byte[] png(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
