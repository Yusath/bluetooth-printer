package com.antigravity.blututprinter;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class VirtualPrinterService extends PrintService {
    private static final String TAG = "VirtualPrinterService";
    private static final String PRINTER_ID_NAME = "virtual_bluetooth_thermal_printer";

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        backgroundThread = new HandlerThread("PrintServiceBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
    }

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new PrinterDiscoverySession() {
            @Override
            public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
                Log.d(TAG, "onStartPrinterDiscovery");
                List<PrinterInfo> printers = new ArrayList<>();
                PrinterId printerId = generatePrinterId(PRINTER_ID_NAME);
                
                PrinterCapabilitiesInfo.Builder builder = new PrinterCapabilitiesInfo.Builder(printerId);
                
                // Add standard roll-paper media sizes (A6 is standard generic fallback for thermal paper)
                builder.addMediaSize(PrintAttributes.MediaSize.ISO_A6, true);
                builder.addResolution(new PrintAttributes.Resolution("thermal", "203dpi", 203, 203), true);
                builder.setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME);
                
                PrinterInfo printer = new PrinterInfo.Builder(printerId, "Bluetooth Thermal Printer", PrinterInfo.STATUS_IDLE)
                        .setCapabilities(builder.build())
                        .setDescription("Bridged Bluetooth Printer Bridge")
                        .build();
                        
                printers.add(printer);
                addPrinters(printers);
            }

            @Override
            public void onStopPrinterDiscovery() {
                Log.d(TAG, "onStopPrinterDiscovery");
            }

            @Override
            public void onValidatePrinters(List<PrinterId> printerIds) {
                Log.d(TAG, "onValidatePrinters: " + printerIds);
            }

            @Override
            public void onStartPrinterStateTracking(PrinterId printerId) {
                Log.d(TAG, "onStartPrinterStateTracking: " + printerId);
            }

            @Override
            public void onStopPrinterStateTracking(PrinterId printerId) {
                Log.d(TAG, "onStopPrinterStateTracking: " + printerId);
            }

            @Override
            public void onDestroy() {
                Log.d(TAG, "Session onDestroy");
            }
        };
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        Log.d(TAG, "onRequestCancelPrintJob");
        printJob.cancel();
    }

    @Override
    protected void onPrintJobQueued(final PrintJob printJob) {
        Log.d(TAG, "onPrintJobQueued: " + printJob.getId());
        
        final BluetoothPrinterManager printer = BluetoothPrinterManager.getInstance();
        if (!printer.isConnected()) {
            Toast.makeText(this, "Virtual Print Failed: Bluetooth Printer is not connected.", Toast.LENGTH_LONG).show();
            printJob.fail("Bluetooth Printer is not connected.");
            return;
        }

        printJob.start();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                ParcelFileDescriptor pfd = null;
                PdfRenderer renderer = null;
                File tempFile = null;
                try {
                    // Load print settings from SharedPreferences
                    android.content.SharedPreferences prefs = getSharedPreferences("BlututPrinterPrefs", MODE_PRIVATE);
                    int targetWidth = prefs.getInt("paper_width", 384);
                    int contrastThreshold = prefs.getInt("print_contrast", 128);
                    int feedLines = prefs.getInt("extra_feed", 3);
                    boolean isThrottled = prefs.getBoolean("buffer_throttle", false);
                    
                    // Update printer manager throttling state
                    printer.setThrottled(isThrottled);

                    pfd = printJob.getDocument().getData();
                    
                    // Copy non-seekable FD to a temporary seekable file (required by PdfRenderer)
                    tempFile = new File(getCacheDir(), "print_job_" + System.currentTimeMillis() + ".pdf");
                    try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                         FileOutputStream out = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                    }

                    // Open seekable temp file
                    ParcelFileDescriptor seekablePfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                    renderer = new PdfRenderer(seekablePfd);
                    int pageCount = renderer.getPageCount();
                    Log.d(TAG, "PDF loaded for printing. Total pages: " + pageCount);

                    for (int i = 0; i < pageCount; i++) {
                        PdfRenderer.Page page = renderer.openPage(i);
                        
                        double scale = (double) targetWidth / page.getWidth();
                        int targetHeight = (int) (page.getHeight() * scale);

                        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(android.graphics.Color.WHITE); // Clear with white background
                        
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        page.close();

                        // Dither and convert to ESC/POS raster bit command with dynamic threshold and custom extra feeds
                        byte[] escPosData = EscPosDriver.bitmapToEscPos(bitmap, targetWidth, contrastThreshold, feedLines);
                        byte[] finalData = EscPosDriver.appendWatermark(escPosData);
                        
                        // Send data to bluetooth printer
                        boolean ok = printer.sendData(finalData);
                        if (!ok) {
                            throw new IOException("Failed to write to bluetooth device.");
                        }
                    }

                    printJob.complete();
                    Log.d(TAG, "Print job finished successfully");

                } catch (Exception e) {
                    Log.e(TAG, "Error printing document", e);
                    printJob.fail("Print failed: " + e.getMessage());
                } finally {
                    if (renderer != null) {
                        try {
                            renderer.close();
                        } catch (Exception ignored) {}
                    }
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
        });
    }
}
