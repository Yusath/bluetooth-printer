package com.antigravity.blututprinter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class RawBTIntentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        // 1. Inflate and show our gorgeous cybernetic dark theme dialog
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_print_status, null);
        final CircularProgressIndicator progressIndicator = dialogView.findViewById(R.id.printProgressIndicator);
        final TextView tvPrintMessage = dialogView.findViewById(R.id.tvPrintMessage);

        final AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        progressDialog.show();

        // 2. Check connection status of BluetoothPrinterManager
        final BluetoothPrinterManager printer = BluetoothPrinterManager.getInstance();
        if (!printer.isConnected()) {
            tvPrintMessage.setText("Printer belum terhubung! ⚠️\nBuka aplikasi untuk koneksi.");
            progressIndicator.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        progressDialog.dismiss();
                        finish();
                    }
                }
            }, 3000);
            return;
        }

        // 3. Extract and parse raw byte printer payloads
        byte[] printData = null;
        String action = intent.getAction();

        try {
            if ("com.rawbt.print.ACTION_PRINT".equals(action) || Intent.ACTION_SEND.equals(action)) {
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
                        progressDialog.dismiss();
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
                        progressDialog.dismiss();
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
                final boolean success = printer.sendData(finalPrintData);
                
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
                                        progressDialog.dismiss();
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
                                        progressDialog.dismiss();
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
