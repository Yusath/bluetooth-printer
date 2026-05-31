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
     * Deprecated: Use applyHeaderAndFooter(byte[], Context) instead.
     */
    @Deprecated
    public static byte[] appendWatermark(byte[] originalData) {
        return applyHeaderAndFooter(originalData, null);
    }

    /**
     * Applies custom header and footer configurations (text or image) and corrects the watermark layout.
     *
     * @param originalData The original ESC/POS bytes.
     * @param context Android context to access SharedPreferences. If null, defaults to BUMS watermark footer.
     * @return Processed ESC/POS bytes.
     */
    public static byte[] applyHeaderAndFooter(byte[] originalData, android.content.Context context) {
        if (originalData == null || originalData.length == 0) return originalData;
        byte[] withHeader = applyHeader(originalData, context);
        byte[] withFooter = applyFooter(withHeader, context);
        return withFooter;
    }

    private static byte[] applyHeader(byte[] originalData, android.content.Context context) {
        if (context == null) return originalData; // No header if context is null
        
        android.content.SharedPreferences prefs = context.getSharedPreferences("BlututPrinterPrefs", android.content.Context.MODE_PRIVATE);
        int headerType = prefs.getInt("header_type", 0); // 0 = None, 1 = Text, 2 = Image
        if (headerType == 0) return originalData;

        byte[] headerBytes = null;
        if (headerType == 1) {
            // Text Header
            String headerText = prefs.getString("header_text", "");
            if (headerText.isEmpty()) return originalData;
            
            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                // 1. Initialize printer state
                stream.write(new byte[]{0x1B, 0x40});
                // 2. Center alignment
                stream.write(new byte[]{0x1B, 0x61, 0x01});
                // 3. Bold on
                stream.write(new byte[]{0x1B, 0x45, 0x01});
                // 4. Text
                stream.write((headerText + "\n\n").getBytes("UTF-8"));
                // 5. Bold off & Left alignment reset
                stream.write(new byte[]{0x1B, 0x45, 0x00});
                stream.write(new byte[]{0x1B, 0x61, 0x00});
                
                headerBytes = stream.toByteArray();
            } catch (Exception e) {
                return originalData;
            }
        } else if (headerType == 2) {
            // Image Header
            String imagePath = prefs.getString("header_image_path", "");
            if (imagePath.isEmpty()) return originalData;
            java.io.File imgFile = new java.io.File(imagePath);
            if (!imgFile.exists()) return originalData;
            
            try {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    int paperWidth = prefs.getInt("paper_width", 384);
                    int contrast = prefs.getInt("print_contrast", 128);
                    // Convert bitmap to ESC/POS with 1 feed line
                    byte[] imgEscPos = bitmapToEscPos(bitmap, paperWidth, contrast, 1);
                    bitmap.recycle();
                    
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    // Center align the image
                    stream.write(new byte[]{0x1B, 0x61, 0x01});
                    stream.write(imgEscPos);
                    stream.write(new byte[]{0x1B, 0x61, 0x00});
                    headerBytes = stream.toByteArray();
                }
            } catch (Exception e) {
                return originalData;
            }
        }

        if (headerBytes == null || headerBytes.length == 0) return originalData;

        // Find insertion index: after the first ESC @ if present at the start
        int insertIndex = 0;
        if (originalData.length >= 2 && originalData[0] == 0x1B && originalData[1] == 0x40) {
            insertIndex = 2;
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            result.write(originalData, 0, insertIndex);
            result.write(headerBytes);
            result.write(originalData, insertIndex, originalData.length - insertIndex);
            return result.toByteArray();
        } catch (java.io.IOException e) {
            return originalData;
        }
    }

    private static byte[] applyFooter(byte[] originalData, android.content.Context context) {
        byte[] footerBytes = null;
        int footerType = 1; // Default to Text (1) for backwards compatibility
        String footerText = "BUMS\nBadan Usaha Milik STIT Riyadhussholihiin";
        String imagePath = "";
        int paperWidth = 384;
        int contrast = 128;

        if (context != null) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("BlututPrinterPrefs", android.content.Context.MODE_PRIVATE);
            footerType = prefs.getInt("footer_type", 1);
            footerText = prefs.getString("footer_text", "BUMS\nBadan Usaha Milik STIT Riyadhussholihiin");
            imagePath = prefs.getString("footer_image_path", "");
            paperWidth = prefs.getInt("paper_width", 384);
            contrast = prefs.getInt("print_contrast", 128);
        }

        if (footerType == 0) return originalData;

        if (footerType == 1) {
            // Text Footer
            if (footerText.isEmpty()) return originalData;

            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                // 1. Center alignment
                stream.write(new byte[]{0x1B, 0x61, 0x01});
                // 2. Select Font B (small, compact 9x17 dot font)
                stream.write(new byte[]{0x1B, 0x4D, 0x01});
                // 3. Text
                stream.write(("\n" + footerText + "\n").getBytes("UTF-8"));
                // 4. Reset Font A (standard) and reset left alignment
                stream.write(new byte[]{0x1B, 0x4D, 0x00});
                stream.write(new byte[]{0x1B, 0x61, 0x00});
                
                footerBytes = stream.toByteArray();
            } catch (Exception e) {
                return originalData;
            }
        } else if (footerType == 2) {
            // Image Footer
            if (imagePath.isEmpty()) return originalData;
            java.io.File imgFile = new java.io.File(imagePath);
            if (!imgFile.exists()) return originalData;

            try {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    // Convert bitmap to ESC/POS with 1 feed line
                    byte[] imgEscPos = bitmapToEscPos(bitmap, paperWidth, contrast, 1);
                    bitmap.recycle();

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    // Center align the image
                    stream.write(new byte[]{0x1B, 0x61, 0x01});
                    stream.write(imgEscPos);
                    stream.write(new byte[]{0x1B, 0x61, 0x00});
                    footerBytes = stream.toByteArray();
                }
            } catch (Exception e) {
                return originalData;
            }
        }

        if (footerBytes == null || footerBytes.length == 0) return originalData;

        // Find insertion index: before the first paper feed or cut command in the trailing window (last 30 bytes)
        int insertIndex = originalData.length;
        int earliestIndex = -1;
        int startScan = Math.max(0, originalData.length - 30);
        for (int i = startScan; i <= originalData.length - 2; i++) {
            // Check for GS V command: 0x1D, 0x56
            if (originalData[i] == 0x1D && originalData[i + 1] == 0x56) {
                earliestIndex = i;
                break;
            }
            // Check for ESC d command: 0x1B, 0x64
            if (originalData[i] == 0x1B && originalData[i + 1] == 0x64) {
                earliestIndex = i;
                break;
            }
        }
        if (earliestIndex != -1) {
            insertIndex = earliestIndex;
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            result.write(originalData, 0, insertIndex);
            result.write(footerBytes);
            if (insertIndex < originalData.length) {
                result.write(originalData, insertIndex, originalData.length - insertIndex);
            }
            return result.toByteArray();
        } catch (java.io.IOException e) {
            return originalData;
        }
    }
}
