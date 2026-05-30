package com.antigravity.blututprinter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class RawBTIntentActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        BluetoothPrinterManager printer = BluetoothPrinterManager.getInstance();
        if (!printer.isConnected()) {
            Toast.makeText(this, "Printer is not connected. Open the app to connect.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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
            Toast.makeText(this, "Error reading intent print data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (printData != null && printData.length > 0) {
            boolean success = printer.sendData(printData);
            if (success) {
                Toast.makeText(this, "Print job sent successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to send print job to printer", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No printable data found in intent", Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
