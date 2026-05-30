package com.antigravity.blututprinter;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

public class EscPosDriver {

    /**
     * Converts a Bitmap into an ESC/POS raster bit image byte array using Floyd-Steinberg Dithering.
     * Uses the standard GS v 0 command (0x1D 0x76 0x30) which is widely compatible with all thermal printers.
     *
     * @param src The source Bitmap.
     * @param targetWidth The desired print width in pixels (typically 384 for 58mm or 576 for 80mm).
     * @return ESC/POS byte array ready to be sent directly to the printer.
     */
    public static byte[] bitmapToEscPos(Bitmap src, int targetWidth) {
        return bitmapToEscPos(src, targetWidth, 128, 3);
    }

    /**
     * Converts a Bitmap into an ESC/POS raster bit image byte array using Floyd-Steinberg Dithering.
     * Uses the standard GS v 0 command (0x1D 0x76 0x30) which is widely compatible with all thermal printers.
     *
     * @param src The source Bitmap.
     * @param targetWidth The desired print width in pixels (typically 384 for 58mm or 576 for 80mm).
     * @param contrastThreshold The Floyd-Steinberg grey threshold (typically 128). Lower values print lighter, higher print darker.
     * @param feedLines The number of extra feed lines after printing (typically 3).
     * @return ESC/POS byte array ready to be sent directly to the printer.
     */
    public static byte[] bitmapToEscPos(Bitmap src, int targetWidth, int contrastThreshold, int feedLines) {
        if (src == null) return new byte[0];

        // 1. Scale bitmap to target width maintaining aspect ratio
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        double ratio = (double) targetWidth / srcWidth;
        int targetHeight = (int) (srcHeight * ratio);

        Bitmap scaled = Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true);

        // 2. Perform Floyd-Steinberg Dithering to get a monochrome image representation
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        int[] pixels = new int[w * h];
        scaled.getPixels(pixels, 0, w, 0, 0, w, h);

        int[][] greyscale = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = pixels[y * w + x];
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                // Standard grayscale luminance formula
                greyscale[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }

        boolean[][] monochrome = new boolean[h][w]; // true = black (printed), false = white (blank)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int oldPixel = greyscale[y][x];
                int newPixel = oldPixel < contrastThreshold ? 0 : 255;
                monochrome[y][x] = (newPixel == 0);
                int err = oldPixel - newPixel;

                // Floyd-Steinberg error distribution
                if (x + 1 < w) {
                    greyscale[y][x + 1] += (err * 7) / 16;
                }
                if (y + 1 < h) {
                    if (x > 0) {
                        greyscale[y + 1][x - 1] += (err * 3) / 16;
                    }
                    greyscale[y + 1][x] += (err * 5) / 16;
                    if (x + 1 < w) {
                        greyscale[y + 1][x + 1] += (err * 1) / 16;
                    }
                }
            }
        }

        // 3. Encode monochrome pixels into ESC/POS GS v 0 command
        // GS v 0 m xL xH yL yH d1...dk
        int widthBytes = (w + 7) / 8;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Initialize printer
        output.write(0x1B); // ESC
        output.write(0x40); // @

        // GS v 0 Command
        output.write(0x1D); // GS
        output.write(0x76); // v
        output.write(0x30); // 0
        output.write(0x00); // m = 0 (Normal mode)

        output.write(widthBytes & 0xFF);         // xL (width bytes LSB)
        output.write((widthBytes >> 8) & 0xFF);  // xH (width bytes MSB)
        output.write(h & 0xFF);                  // yL (height LSB)
        output.write((h >> 8) & 0xFF);           // yH (height MSB)

        // Write bit data
        for (int y = 0; y < h; y++) {
            for (int byteIdx = 0; byteIdx < widthBytes; byteIdx++) {
                int currentByte = 0;
                for (int bitIdx = 0; bitIdx < 8; bitIdx++) {
                    int x = byteIdx * 8 + bitIdx;
                    if (x < w) {
                        if (monochrome[y][x]) {
                            currentByte |= (1 << (7 - bitIdx));
                        }
                    }
                }
                output.write(currentByte);
            }
        }

        // Feed paper
        if (feedLines > 0) {
            output.write(0x1B); // ESC
            output.write(0x64); // d
            output.write(feedLines & 0xFF); // Feed custom lines
        }

        scaled.recycle();

        return output.toByteArray();
    }
}
