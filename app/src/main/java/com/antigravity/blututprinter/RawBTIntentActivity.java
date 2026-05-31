package com.antigravity.blututprinter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class RawBTIntentActivity extends AppCompatActivity {
    
    private CircularProgressIndicator progressIndicator;
    private TextView tvPrintMessage;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        // 1. Set dialog layout directly as our activity content view
        setContentView(R.layout.dialog_print_status);
        
        // Make the translucent activity window look and behave like a centered floating dialog
        if (getWindow() != null) {
            getWindow().setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getWindow().setGravity(Gravity.CENTER);
        }

        progressIndicator = findViewById(R.id.printProgressIndicator);
        tvPrintMessage = findViewById(R.id.tvPrintMessage);

        // 2. Check connection status of BluetoothPrinterManager
        final BluetoothPrinterManager printer = BluetoothPrinterManager.getInstance();
        if (!printer.isConnected()) {
            // Attempt to auto-connect to default saved printer
            tvPrintMessage.setText("Menghubungkan ke printer...");
            progressIndicator.setVisibility(View.VISIBLE);
            
            printer.connectToDefault(this, new BluetoothPrinterManager.ConnectionCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            tvPrintMessage.setText("Mengirim data cetak...");
                            processPrintIntent(intent, printer);
                        }
                    });
                }

                @Override
                public void onFailure(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            tvPrintMessage.setText("Gagal terhubung ke printer! ⚠️\n" + message);
                            progressIndicator.setVisibility(View.GONE);
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isFinishing()) {
                                        finish();
                                    }
                                }
                            }, 3000);
                        }
                    });
                }
            });
            return;
        }

        // Already connected, process immediately
        processPrintIntent(intent, printer);
    }
    
    private void processPrintIntent(final Intent intent, final BluetoothPrinterManager printer) {
        // 3. Extract and parse raw byte printer payloads
        byte[] printData = null;
        String action = intent.getAction();

        try {
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri dataUri = intent.getData();
                if (dataUri != null && "rawbt".equals(dataUri.getScheme())) {
                    String uriString = dataUri.toString();
                    if (uriString.startsWith("rawbt:data:") && uriString.contains(";base64,")) {
                        int base64StartIndex = uriString.indexOf(";base64,") + 8;
                        String b64Data = uriString.substring(base64StartIndex);
                        printData = Base64.decode(b64Data, Base64.DEFAULT);
                    } else if (dataUri.getQueryParameter("base64") != null) {
                        String b64 = dataUri.getQueryParameter("base64");
                        printData = Base64.decode(b64, Base64.DEFAULT);
                    } else {
                        String textData = uriString.substring(6);
                        try {
                            textData = Uri.decode(textData);
                        } catch (Exception ignored) {}
                        printData = textData.getBytes();
                    }
                }
            } else if ("com.rawbt.print.ACTION_PRINT".equals(action) || Intent.ACTION_SEND.equals(action)) {
                if (intent.hasExtra("bytes")) {
                    printData = intent.getByteArrayExtra("bytes");
                } 
                else if (intent.hasExtra("base64")) {
                    String b64 = intent.getStringExtra("base64");
                    if (b64 != null) {
                        printData = Base64.decode(b64, Base64.DEFAULT);
                    }
                } 
                else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (text != null) {
                        printData = text.getBytes();
                    }
                } 
                else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (streamUri != null) {
                        InputStream in = getContentResolver().openInputStream(streamUri);
                        if (in != null) {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                bos.write(buffer, 0, read);
                            }
                            in.close();
                            printData = bos.toByteArray();
                        }
                    }
                }
            }
        } catch (Exception e) {
            tvPrintMessage.setText("Data struk rusak! ❌");
            progressIndicator.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        finish();
                    }
                }
            }, 2500);
            return;
        }

        // 4. Ensure we have data to print
        if (printData == null || printData.length == 0) {
            tvPrintMessage.setText("Tidak ada data struk! ❌");
            progressIndicator.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        finish();
                    }
                }
            }, 2500);
            return;
        }

        // 5. Send data asynchronously to prevent Main UI Thread freeze
        final byte[] finalPrintData = printData;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Apply the beautiful BUMS watermark!
                byte[] watermarkedData = EscPosDriver.appendWatermark(finalPrintData);
                final boolean success = printer.sendData(watermarkedData);
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;

                        progressIndicator.setVisibility(View.GONE);
                        if (success) {
                            tvPrintMessage.setText("Cetak struk sukses! 🖨️");
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isFinishing()) {
                                        finish();
                                    }
                                }
                            }, 1200);
                        } else {
                            tvPrintMessage.setText("Gagal mencetak struk! ❌");
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isFinishing()) {
                                        finish();
                                    }
                                }
                            }, 2500);
                        }
                    }
                });
            }
        }).start();
    }
}

