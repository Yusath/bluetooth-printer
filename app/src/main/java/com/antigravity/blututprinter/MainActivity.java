package com.antigravity.blututprinter;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 99;

    private BluetoothPrinterManager printerManager;
    private SharedPreferences prefs;

    // UI Elements
    private TextView tvServerStatus;
    private TextView tvPrinterStatus;
    private SwitchMaterial swStartServer;
    private SwitchMaterial swStartOnBoot;
    private EditText etServerPort;
    private Spinner spPrinters;
    private Button btnConnect;
    private Button btnTestPrint;

    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private List<String> deviceNames = new ArrayList<>();

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PrintServerService.ACTION_STATUS_CHANGE.equals(intent.getAction())) {
                boolean running = intent.getBooleanExtra(PrintServerService.EXTRA_STATUS, false);
                int port = intent.getIntExtra(PrintServerService.EXTRA_PORT, 6801);
                updateServerUI(running, port);
            }
        }
    };

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getBluetoothClass() != null) {
                    // Filter common printer classes or add if not already in the list
                    try {
                        if (!deviceList.contains(device)) {
                            deviceList.add(device);
                            String name = device.getName() != null ? device.getName() : "Unknown Device";
                            deviceNames.add(name + "\n(" + device.getAddress() + ")");
                            spinnerAdapter.notifyDataSetChanged();
                        }
                    } catch (SecurityException ignored) {}
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        printerManager = BluetoothPrinterManager.getInstance();
        prefs = getSharedPreferences("BlututPrinterPrefs", MODE_PRIVATE);

        initUI();
        setupListeners();
        registerReceivers();

        // Load existing settings
        int port = prefs.getInt("server_port", 6801);
        etServerPort.setText(String.valueOf(port));
        swStartOnBoot.setChecked(prefs.getBoolean("start_on_boot", false));

        // Default Server UI to stopped; service will broadcast its actual running state on start
        updateServerUI(false, port);

        // Check & request Bluetooth permissions
        if (checkBtPermissions()) {
            loadPrinters();
        } else {
            requestBtPermissions();
        }
    }

    private void initUI() {
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvPrinterStatus = findViewById(R.id.tvPrinterStatus);
        swStartServer = findViewById(R.id.swStartServer);
        swStartOnBoot = findViewById(R.id.swStartOnBoot);
        etServerPort = findViewById(R.id.etServerPort);
        spPrinters = findViewById(R.id.spPrinters);
        btnConnect = findViewById(R.id.btnConnect);
        btnTestPrint = findViewById(R.id.btnTestPrint);

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPrinters.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
        // Toggle Server service
        swStartServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(this, PrintServerService.class);
            if (isChecked) {
                // Save port before starting
                int port = 6801;
                try {
                    port = Integer.parseInt(etServerPort.getText().toString());
                } catch (NumberFormatException ignored) {}
                prefs.edit().putInt("server_port", port).apply();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                stopService(serviceIntent);
                updateServerUI(false, prefs.getInt("server_port", 6801));
            }
        });

        // Toggle Boot Autostart
        swStartOnBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("start_on_boot", isChecked).apply();
            Toast.makeText(this, isChecked ? "Auto-start on boot enabled" : "Auto-start on boot disabled", Toast.LENGTH_SHORT).show();
        });

        // Port Text change
        etServerPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int p = Integer.parseInt(s.toString());
                    prefs.edit().putInt("server_port", p).apply();
                } catch (NumberFormatException ignored) {}
            }
        });

        // Scan/Connect Printer Button
        btnConnect.setOnClickListener(v -> {
            int selectedIndex = spPrinters.getSelectedItemPosition();
            if (selectedIndex >= 0 && selectedIndex < deviceList.size()) {
                BluetoothDevice selectedDevice = deviceList.get(selectedIndex);
                connectToPrinter(selectedDevice);
            } else {
                Toast.makeText(this, "Please select a printer first", Toast.LENGTH_SHORT).show();
            }
        });

        // Send Test Receipt Button
        btnTestPrint.setOnClickListener(v -> {
            if (!printerManager.isConnected()) {
                Toast.makeText(this, "Connect to a printer first!", Toast.LENGTH_SHORT).show();
                return;
            }
            sendTestReceipt();
        });

        // Auto connect default printer if previously saved
        spPrinters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < deviceList.size()) {
                    BluetoothDevice device = deviceList.get(position);
                    prefs.edit().putString("default_printer_address", device.getAddress()).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void registerReceivers() {
        registerReceiver(statusReceiver, new IntentFilter(PrintServerService.ACTION_STATUS_CHANGE));
        
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryReceiver, filter);
    }

    private void unregisterReceivers() {
        try {
            unregisterReceiver(statusReceiver);
        } catch (IllegalArgumentException ignored) {}
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    private void loadPrinters() {
        deviceList.clear();
        deviceNames.clear();

        Set<BluetoothDevice> paired = printerManager.getPairedDevices();
        if (paired != null && !paired.isEmpty()) {
            for (BluetoothDevice device : paired) {
                deviceList.add(device);
                try {
                    String name = device.getName() != null ? device.getName() : "Unknown Device";
                    deviceNames.add(name + " [Paired]\n(" + device.getAddress() + ")");
                } catch (SecurityException ignored) {}
            }
        }

        if (deviceList.isEmpty()) {
            deviceNames.add("No bluetooth devices found");
        } else {
            // Check if there was a default printer address and auto-select it
            String savedAddress = prefs.getString("default_printer_address", "");
            if (!savedAddress.isEmpty()) {
                for (int i = 0; i < deviceList.size(); i++) {
                    if (deviceList.get(i).getAddress().equals(savedAddress)) {
                        spPrinters.setSelection(i);
                        break;
                    }
                }
            }
        }
        spinnerAdapter.notifyDataSetChanged();
    }

    private void connectToPrinter(BluetoothDevice device) {
        tvPrinterStatus.setText("Connecting...");
        btnConnect.setEnabled(false);

        printerManager.connect(device, new BluetoothPrinterManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                btnConnect.setEnabled(true);
                try {
                    tvPrinterStatus.setText("Connected to " + device.getName());
                } catch (SecurityException se) {
                    tvPrinterStatus.setText("Connected to " + device.getAddress());
                }
                Toast.makeText(MainActivity.this, "Printer connected!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                btnConnect.setEnabled(true);
                tvPrinterStatus.setText("Disconnected");
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateServerUI(boolean running, int port) {
        // Temporarily remove listener to avoid recursion
        swStartServer.setOnCheckedChangeListener(null);
        swStartServer.setChecked(running);
        setupListeners(); // Re-assign listener

        if (running) {
            String ip = getIPAddress();
            tvServerStatus.setText("Running on:\nhttp://localhost:" + port + "\nhttp://" + ip + ":" + port);
            tvServerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        } else {
            tvServerStatus.setText("Stopped");
            tvServerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        }
    }

    private void sendTestReceipt() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            // ESC/POS Formatting bytes (standard Commands)
            byte[] ESC_ALIGN_CENTER = new byte[]{0x1b, 0x61, 0x01};
            byte[] ESC_ALIGN_LEFT = new byte[]{0x1b, 0x61, 0x00};
            byte[] ESC_FONT_LARGE = new byte[]{0x1d, 0x21, 0x11}; // Double height & width
            byte[] ESC_FONT_NORMAL = new byte[]{0x1d, 0x21, 0x00};
            byte[] ESC_BOLD_ON = new byte[]{0x1b, 0x45, 0x01};
            byte[] ESC_BOLD_OFF = new byte[]{0x1b, 0x45, 0x00};
            byte[] ESC_INITIALIZE = new byte[]{0x1b, 0x40};
            byte[] ESC_FEED_AND_CUT = new byte[]{0x1d, 0x56, 0x42, 0x00}; // Feed paper and cut

            // 1. Initialize
            bytes.write(ESC_INITIALIZE);
            
            // 2. Title
            bytes.write(ESC_ALIGN_CENTER);
            bytes.write(ESC_FONT_LARGE);
            bytes.write(ESC_BOLD_ON);
            bytes.write("BLUTUT PRINTER\n".getBytes());
            
            // 3. Subtitle
            bytes.write(ESC_FONT_NORMAL);
            bytes.write(ESC_BOLD_OFF);
            bytes.write("Web & TCP Print Bridge\n".getBytes());
            bytes.write("--------------------------------\n".getBytes());

            // 4. Content Items
            bytes.write(ESC_ALIGN_LEFT);
            bytes.write("Item A               Rp 15.000\n".getBytes());
            bytes.write("Item B               Rp 20.000\n".getBytes());
            bytes.write("--------------------------------\n".getBytes());

            // 5. Total
            bytes.write(ESC_BOLD_ON);
            bytes.write("TOTAL                Rp 35.000\n".getBytes());
            bytes.write(ESC_BOLD_OFF);
            bytes.write("--------------------------------\n".getBytes());

            // 6. Footer message
            bytes.write(ESC_ALIGN_CENTER);
            bytes.write("Terima Kasih / Thank You!\n".getBytes());
            bytes.write("Successfully bridged!\n\n\n\n".getBytes());

            // 7. Feed and cut
            bytes.write(ESC_FEED_AND_CUT);

            boolean ok = printerManager.sendData(bytes.toByteArray());
            if (ok) {
                Toast.makeText(this, "Receipt sent!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Print failed", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Receipt error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private boolean checkBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                loadPrinters();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for the scanner to function.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkBtPermissions()) {
            loadPrinters();
        }
        if (printerManager.isConnected()) {
            tvPrinterStatus.setText("Connected to " + printerManager.getConnectedDeviceName());
        } else {
            tvPrinterStatus.setText("Disconnected");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceivers();
    }
}
