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

    /**
     * Appends a two-line footer watermark ("BUMS" and "Badan Usaha Milik STIT Riyadhussholihiin")
     * in Font B, centered alignment, to the original ESC/POS print byte array.
     * Searches for existing paper cut (GS V) or feed (ESC d) commands at the end (last 15 bytes)
     * and injects the watermark just before them to ensure it is printed properly before
     * cutting or feeding.
     *
     * @param originalData The original ESC/POS print bytes.
     * @return Byte array with watermark injected.
     */
    public static byte[] appendWatermark(byte[] originalData) {
        if (originalData == null || originalData.length == 0) return originalData;

        byte[] watermarkText;
        try {
            java.io.ByteArrayOutputStream watermarkStream = new java.io.ByteArrayOutputStream();
            
            // 1. Center alignment
            watermarkStream.write(new byte[]{0x1B, 0x61, 0x01});
            
            // 2. Select Font B (small, compact 9x17 dot font)
            watermarkStream.write(new byte[]{0x1B, 0x4D, 0x01});
            
            // 3. Watermark text (Line 1: BUMS, Line 2: Badan Usaha Milik STIT Riyadhussholihiin)
            // Added safe padding around the text
            watermarkStream.write("\nBUMS\nBadan Usaha Milik STIT Riyadhussholihiin\n".getBytes("UTF-8"));
            
            // 4. Reset Font A (standard) and reset left alignment
            watermarkStream.write(new byte[]{0x1B, 0x4D, 0x00});
            watermarkStream.write(new byte[]{0x1B, 0x61, 0x00});
            
            watermarkText = watermarkStream.toByteArray();
        } catch (java.io.IOException e) {
            return originalData;
        }

        int insertIndex = originalData.length;

        // Look at the last 15 bytes of originalData to check for typical cut (GS V) or feed (ESC d) commands
        for (int i = originalData.length - 2; i >= Math.max(0, originalData.length - 15); i--) {
            // Check for GS V command: 0x1D, 0x56
            if (originalData[i] == 0x1D && originalData[i + 1] == 0x56) {
                insertIndex = i;
                break;
            }
            // Check for ESC d command: 0x1B, 0x64
            if (originalData[i] == 0x1B && originalData[i + 1] == 0x64) {
                insertIndex = i;
                break;
            }
        }

        // Construct final data stream
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        try {
            result.write(originalData, 0, insertIndex);
            result.write(watermarkText);
            if (insertIndex < originalData.length) {
                result.write(originalData, insertIndex, originalData.length - insertIndex);
            }
            return result.toByteArray();
        } catch (java.io.IOException e) {
            return originalData;
        }
    }
}
