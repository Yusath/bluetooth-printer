package com.antigravity.blututprinter;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.provider.Settings;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private Button btnTestImagePrint;
    private Button btnOpenPrintSettings;

    // Bluetooth Warning Banner
    private LinearLayout llBluetoothWarning;
    
    // Pending Jobs Warning Banner
    private LinearLayout llPendingJobsWarning;
    private TextView tvPendingJobsDesc;
    private Button btnForcePrintPending;

    // Advanced Settings
    private SwitchMaterial swAutoUpdate;
    private SwitchMaterial swBufferThrottle;
    private Button btnCheckUpdate;
    private Spinner spPaperSize;
    private Spinner spFeedLines;
    private SeekBar sbContrast;

    // Activity Log Dashboard
    private ScrollView svLogs;
    private TextView tvLogs;
    private Button btnClearLogs;

    // Bottom Navigation Elements
    private LinearLayout btnTabPrinter, btnTabServer, btnTabSettings, btnTabLogs;
    private ImageView ivTabPrinter, ivTabServer, ivTabSettings, ivTabLogs;
    private TextView tvTabPrinter, tvTabServer, tvTabSettings, tvTabLogs;
    private LinearLayout tabLayoutPrinter, tabLayoutServer, tabLayoutSettings, tabLayoutLogs;
    private ScrollView mainScrollView;
    private View vIndicatorPrinter, vIndicatorServer, vIndicatorSettings, vIndicatorLogs;

    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private List<String> deviceNames = new ArrayList<>();

    // Receivers
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

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PrintServerService.ACTION_LOG_EVENT.equals(intent.getAction())) {
                String log = intent.getStringExtra(PrintServerService.EXTRA_LOG);
                appendLog(log);
            }
        }
    };

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                checkBluetoothState();
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

        // Load Advanced Settings
        swAutoUpdate.setChecked(prefs.getBoolean("check_updates_auto", true));
        swBufferThrottle.setChecked(prefs.getBoolean("buffer_throttle", false));
        printerManager.setThrottled(swBufferThrottle.isChecked());

        // Initialize Paper Size Spinner
        List<String> paperSizes = new ArrayList<>();
        paperSizes.add("58mm (384 px)");
        paperSizes.add("80mm (576 px)");
        ArrayAdapter<String> paperAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, paperSizes);
        paperAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        spPaperSize.setAdapter(paperAdapter);
        
        int savedWidth = prefs.getInt("paper_width", 384);
        spPaperSize.setSelection(savedWidth == 576 ? 1 : 0);

        // Initialize Extra Feed Spinner
        List<String> feedOptions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            feedOptions.add(i + " lines");
        }
        ArrayAdapter<String> feedAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, feedOptions);
        feedAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        spFeedLines.setAdapter(feedAdapter);

        int savedFeed = prefs.getInt("extra_feed", 3);
        spFeedLines.setSelection(Math.min(4, Math.max(0, savedFeed - 1)));

        // Initialize Contrast SeekBar
        int savedContrast = prefs.getInt("print_contrast", 128); // 80 - 180 threshold
        sbContrast.setProgress(savedContrast - 80);

        // Default Server UI to stopped; service will broadcast its actual running state on start
        updateServerUI(false, port);

        // Check & request Bluetooth permissions
        if (checkBtPermissions()) {
            loadPrinters();
            checkBluetoothState();
            triggerAutoReconnect();
        } else {
            requestBtPermissions();
        }

        // Trigger Auto-Update Check on startup if enabled
        if (swAutoUpdate.isChecked()) {
            AutoUpdater.checkForUpdates(this, false, null);
        }

        // Initialize Tab
        switchTab(0);

        // Handle SETUP_PRINTER_FOR_PRINT launch intent
        if (getIntent() != null && "SETUP_PRINTER_FOR_PRINT".equals(getIntent().getStringExtra("action"))) {
            switchTab(0);
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
        btnTestImagePrint = findViewById(R.id.btnTestImagePrint);
        btnOpenPrintSettings = findViewById(R.id.btnOpenPrintSettings);

        // Dynamically display app version
        TextView tvAppVersion = findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            try {
                android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                tvAppVersion.setText("v" + pInfo.versionName);
            } catch (Exception e) {
                tvAppVersion.setText("v1.0.3");
            }
        }

        // Warning Banner
        llBluetoothWarning = findViewById(R.id.llBluetoothWarning);

        // Pending Jobs Banner
        llPendingJobsWarning = findViewById(R.id.llPendingJobsWarning);
        tvPendingJobsDesc = findViewById(R.id.tvPendingJobsDesc);
        btnForcePrintPending = findViewById(R.id.btnForcePrintPending);

        // Advanced Settings
        swAutoUpdate = findViewById(R.id.swAutoUpdate);
        swBufferThrottle = findViewById(R.id.swBufferThrottle);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        spPaperSize = findViewById(R.id.spPaperSize);
        spFeedLines = findViewById(R.id.spFeedLines);
        sbContrast = findViewById(R.id.sbContrast);

        // Activity Log Dashboard
        svLogs = findViewById(R.id.svLogs);
        tvLogs = findViewById(R.id.tvLogs);
        btnClearLogs = findViewById(R.id.btnClearLogs);

        // Bottom Navigation Bar
        btnTabPrinter = findViewById(R.id.btnTabPrinter);
        btnTabServer = findViewById(R.id.btnTabServer);
        btnTabSettings = findViewById(R.id.btnTabSettings);
        btnTabLogs = findViewById(R.id.btnTabLogs);

        ivTabPrinter = findViewById(R.id.ivTabPrinter);
        ivTabServer = findViewById(R.id.ivTabServer);
        ivTabSettings = findViewById(R.id.ivTabSettings);
        ivTabLogs = findViewById(R.id.ivTabLogs);

        tvTabPrinter = findViewById(R.id.tvTabPrinter);
        tvTabServer = findViewById(R.id.tvTabServer);
        tvTabSettings = findViewById(R.id.tvTabSettings);
        tvTabLogs = findViewById(R.id.tvTabLogs);

        tabLayoutPrinter = findViewById(R.id.tabLayoutPrinter);
        tabLayoutServer = findViewById(R.id.tabLayoutServer);
        tabLayoutSettings = findViewById(R.id.tabLayoutSettings);
        tabLayoutLogs = findViewById(R.id.tabLayoutLogs);
        
        mainScrollView = findViewById(R.id.mainScrollView);

        // Custom Floating Dock Active Indicators
        vIndicatorPrinter = findViewById(R.id.vIndicatorPrinter);
        vIndicatorServer = findViewById(R.id.vIndicatorServer);
        vIndicatorSettings = findViewById(R.id.vIndicatorSettings);
        vIndicatorLogs = findViewById(R.id.vIndicatorLogs);

        spinnerAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, deviceNames);
        spinnerAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        spPrinters.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
        // Bottom Nav Listeners
        btnTabPrinter.setOnClickListener(v -> switchTab(0));
        btnTabServer.setOnClickListener(v -> switchTab(1));
        btnTabSettings.setOnClickListener(v -> switchTab(2));
        btnTabLogs.setOnClickListener(v -> switchTab(3));

        // Toggle Server service
        swStartServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent serviceIntent = new Intent(this, PrintServerService.class);
            if (isChecked) {
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

        // Toggle Auto Update
        swAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("check_updates_auto", isChecked).apply();
        });

        // Toggle Throttling
        swBufferThrottle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("buffer_throttle", isChecked).apply();
            printerManager.setThrottled(isChecked);
            appendLog("[System] Buffer Throttling (Anti-Overflow) set to: " + isChecked);
        });

        // Check Update Button
        btnCheckUpdate.setOnClickListener(v -> {
            appendLog("[System] Manual update check requested...");
            AutoUpdater.checkForUpdates(this, true, new AutoUpdater.UpdateCheckCallback() {
                @Override
                public void onNoUpdate() {
                    Toast.makeText(MainActivity.this, "Your app is up to date!", Toast.LENGTH_SHORT).show();
                    appendLog("[System] App is fully up to date.");
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Update check failed: " + error, Toast.LENGTH_LONG).show();
                    appendLog("[System error] Update check failed: " + error);
                }
            });
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

        // Paper Size Spinner
        spPaperSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int width = (position == 1) ? 576 : 384;
                prefs.edit().putInt("paper_width", width).apply();
                appendLog("[System] Paper width setting changed to: " + (position == 1 ? "80mm (576px)" : "58mm (384px)"));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Feed Lines Spinner
        spFeedLines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int feed = position + 1;
                prefs.edit().putInt("extra_feed", feed).apply();
                appendLog("[System] Post-Print Feed changed to: " + feed + " lines");
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Contrast SeekBar
        sbContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = 80 + progress;
                prefs.edit().putInt("print_contrast", threshold).apply();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int threshold = 80 + seekBar.getProgress();
                appendLog("[System] Floyd-Steinberg Contrast threshold updated to: " + threshold);
            }
        });

        // Force Print Pending Button Click
        btnForcePrintPending.setOnClickListener(v -> printPendingJobsDirectly());

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

        // Send Test Receipt Button (Text-only)
        btnTestPrint.setOnClickListener(v -> {
            if (!printerManager.isConnected()) {
                Toast.makeText(this, "Connect to a printer first!", Toast.LENGTH_SHORT).show();
                return;
            }
            sendTestReceipt();
        });

        // Send Calibration Image Button
        btnTestImagePrint.setOnClickListener(v -> {
            if (!printerManager.isConnected()) {
                Toast.makeText(this, "Connect to a printer first!", Toast.LENGTH_SHORT).show();
                return;
            }
            sendCalibrationImage();
        });

        // Default printer address selection
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

        // Clear Logs Button
        btnClearLogs.setOnClickListener(v -> {
            tvLogs.setText("");
        });

        // Warning banner click opens system bluetooth settings
        llBluetoothWarning.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            } catch (Exception ignored) {}
        });

        // Open System Print Settings
        btnOpenPrintSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_PRINT_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open print settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchTab(int tabIndex) {
        // Toggle Layouts
        tabLayoutPrinter.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        tabLayoutServer.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        tabLayoutSettings.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        tabLayoutLogs.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        // Reset Colors using modern Sky Blue and Text Secondary
        int colorAccent = ContextCompat.getColor(this, R.color.accent_blue);
        int colorSecondary = ContextCompat.getColor(this, R.color.text_secondary);

        ivTabPrinter.setColorFilter(tabIndex == 0 ? colorAccent : colorSecondary);
        tvTabPrinter.setTextColor(tabIndex == 0 ? colorAccent : colorSecondary);
        tvTabPrinter.setTypeface(null, tabIndex == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        vIndicatorPrinter.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);

        ivTabServer.setColorFilter(tabIndex == 1 ? colorAccent : colorSecondary);
        tvTabServer.setTextColor(tabIndex == 1 ? colorAccent : colorSecondary);
        tvTabServer.setTypeface(null, tabIndex == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        vIndicatorServer.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);

        ivTabSettings.setColorFilter(tabIndex == 2 ? colorAccent : colorSecondary);
        tvTabSettings.setTextColor(tabIndex == 2 ? colorAccent : colorSecondary);
        tvTabSettings.setTypeface(null, tabIndex == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        vIndicatorSettings.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        ivTabLogs.setColorFilter(tabIndex == 3 ? colorAccent : colorSecondary);
        tvTabLogs.setTextColor(tabIndex == 3 ? colorAccent : colorSecondary);
        tvTabLogs.setTypeface(null, tabIndex == 3 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        vIndicatorLogs.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        // Smooth Scroll back to top
        mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, 0));
    }

    private void registerReceivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, new IntentFilter(PrintServerService.ACTION_STATUS_CHANGE), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(logReceiver, new IntentFilter(PrintServerService.ACTION_LOG_EVENT), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(statusReceiver, new IntentFilter(PrintServerService.ACTION_STATUS_CHANGE));
            registerReceiver(logReceiver, new IntentFilter(PrintServerService.ACTION_LOG_EVENT));
            registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(discoveryReceiver, filter);
        }
    }

    private void unregisterReceivers() {
        try {
            unregisterReceiver(statusReceiver);
        } catch (IllegalArgumentException ignored) {}
        try {
            unregisterReceiver(logReceiver);
        } catch (IllegalArgumentException ignored) {}
        try {
            unregisterReceiver(btStateReceiver);
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

    private void checkBluetoothState() {
        BluetoothAdapter adapter = printerManager.getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            llBluetoothWarning.setVisibility(View.VISIBLE);
        } else {
            llBluetoothWarning.setVisibility(View.GONE);
        }
    }

    private void triggerAutoReconnect() {
        String savedAddress = prefs.getString("default_printer_address", "");
        if (savedAddress.isEmpty() || printerManager.isConnected()) return;

        appendLog("[System] Auto-reconnecting to default printer: " + savedAddress);
        tvPrinterStatus.setText("Connecting...");
        tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
        btnConnect.setEnabled(false);

        printerManager.connectToDefault(this, new BluetoothPrinterManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                btnConnect.setEnabled(true);
                String name = printerManager.getConnectedDeviceName();
                tvPrinterStatus.setText("Connected to " + name);
                tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_connected);
                appendLog("[System] Connected successfully to " + name);
                onPrinterConnectedSuccessfully();
            }

            @Override
            public void onFailure(String message) {
                btnConnect.setEnabled(true);
                tvPrinterStatus.setText("Disconnected");
                tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
                appendLog("[System error] Reconnect failed: " + message);
            }
        });
    }

    private void connectToPrinter(BluetoothDevice device) {
        tvPrinterStatus.setText("Connecting...");
        tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
        btnConnect.setEnabled(false);
        appendLog("[System] Connecting to " + device.getAddress() + "...");

        printerManager.connect(device, new BluetoothPrinterManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                btnConnect.setEnabled(true);
                try {
                    tvPrinterStatus.setText("Connected to " + device.getName());
                    tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_connected);
                    appendLog("[System] Connected to " + device.getName());
                } catch (SecurityException se) {
                    tvPrinterStatus.setText("Connected to " + device.getAddress());
                    tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_connected);
                    appendLog("[System] Connected to " + device.getAddress());
                }
                Toast.makeText(MainActivity.this, "Printer connected!", Toast.LENGTH_SHORT).show();
                onPrinterConnectedSuccessfully();
            }

            @Override
            public void onFailure(String message) {
                btnConnect.setEnabled(true);
                tvPrinterStatus.setText("Disconnected");
                tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
                appendLog("[System error] Connection failed: " + message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateServerUI(boolean running, int port) {
        swStartServer.setOnCheckedChangeListener(null);
        swStartServer.setChecked(running);
        setupListeners();

        if (running) {
            String ip = getIPAddress();
            tvServerStatus.setText("Running on:\nhttp://localhost:" + port + "\nhttp://" + ip + ":" + port);
            tvServerStatus.setBackgroundResource(R.drawable.bg_status_connected);
        } else {
            tvServerStatus.setText("Stopped");
            tvServerStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
        }
    }

    private void appendLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        final String newLogLine = "[" + timestamp + "] " + message + "\n";
        tvLogs.append(newLogLine);
        svLogs.post(() -> svLogs.fullScroll(View.FOCUS_DOWN));
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
            
            // Feed command builder
            int feedLines = prefs.getInt("extra_feed", 3);
            byte[] ESC_FEED = new byte[]{0x1d, 0x56, 0x42, (byte) feedLines};

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
            bytes.write("POS Bridge & Auto Updater\n".getBytes());
            bytes.write("--------------------------------\n".getBytes());

            // 4. Content Items
            bytes.write(ESC_ALIGN_LEFT);
            bytes.write("Item POS-Project A   Rp 15.000\n".getBytes());
            bytes.write("Item POS-Project B   Rp 20.000\n".getBytes());
            bytes.write("--------------------------------\n".getBytes());

            // 5. Total
            bytes.write(ESC_BOLD_ON);
            bytes.write("TOTAL                Rp 35.000\n".getBytes());
            bytes.write(ESC_BOLD_OFF);
            bytes.write("--------------------------------\n".getBytes());

            // 6. Footer message
            bytes.write(ESC_ALIGN_CENTER);
            bytes.write("Terima Kasih / Thank You!\n".getBytes());
            bytes.write("Successfully bridged!\n\n".getBytes());

            // 7. Feed
            bytes.write(ESC_FEED);

            appendLog("[Test] Sending ESC/POS text receipt print...");
            byte[] finalBytes = EscPosDriver.appendWatermark(bytes.toByteArray());
            boolean ok = printerManager.sendData(finalBytes);
            if (ok) {
                Toast.makeText(this, "Receipt sent!", Toast.LENGTH_SHORT).show();
                appendLog("[Test] Text receipt print successful.");
            } else {
                Toast.makeText(this, "Print failed", Toast.LENGTH_SHORT).show();
                appendLog("[Test error] Text receipt print failed.");
            }

        } catch (Exception e) {
            Toast.makeText(this, "Receipt error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendCalibrationImage() {
        appendLog("[Test] Generating calibration image patterns...");
        int width = prefs.getInt("paper_width", 384);
        int contrast = prefs.getInt("print_contrast", 128);
        int feed = prefs.getInt("extra_feed", 3);

        Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // 1. Draw white background
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, width, paint);

        // 2. Draw black border
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        canvas.drawRect(3, 3, width - 3, width - 3, paint);

        // 3. Draw grid lines
        paint.setStrokeWidth(2);
        for (int i = width / 8; i < width; i += width / 8) {
            canvas.drawLine(i, 0, i, width, paint);
            canvas.drawLine(0, i, width, i, paint);
        }

        // 4. Draw labels
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(width / 15);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("CALIBRATION GRID", width / 2, width / 4, paint);
        
        paint.setTextSize(width / 20);
        paint.setFakeBoldText(false);
        canvas.drawText("WIDTH: " + width + "px (" + (width == 576 ? "80mm" : "58mm") + ")", width / 2, width / 2 - 15, paint);
        canvas.drawText("CONTRAST: " + contrast, width / 2, width / 2 + 15, paint);
        canvas.drawText("FEED EXTRA: " + feed + " lines", width / 2, width / 2 + 45, paint);

        // 5. Draw gray gradient blocks
        for (int j = 0; j < 4; j++) {
            int greyVal = 64 + j * 48; // 64, 112, 160, 208
            paint.setColor(Color.rgb(greyVal, greyVal, greyVal));
            int left = (width / 8) * (2 * j);
            int top = width - (width / 5);
            canvas.drawRect(left + 8, top, left + (width / 8) * 2 - 8, width - 15, paint);
        }

        appendLog("[Test] Converting calibration pattern to ESC/POS...");
        byte[] ditherData = EscPosDriver.bitmapToEscPos(bitmap, width, contrast, feed);
        
        appendLog("[Test] Sending calibration print (" + ditherData.length + " bytes)...");
        boolean ok = printerManager.sendData(ditherData);
        if (ok) {
            Toast.makeText(this, "Calibration image sent!", Toast.LENGTH_SHORT).show();
            appendLog("[Test] Calibration print successful.");
        } else {
            Toast.makeText(this, "Calibration print failed", Toast.LENGTH_SHORT).show();
            appendLog("[Test error] Calibration print failed.");
        }
        bitmap.recycle();
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
                checkBluetoothState();
                triggerAutoReconnect();
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
            checkBluetoothState();
        }
        if (printerManager.isConnected()) {
            tvPrinterStatus.setText("Connected to " + printerManager.getConnectedDeviceName());
            tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_connected);
        } else {
            tvPrinterStatus.setText("Disconnected");
            tvPrinterStatus.setBackgroundResource(R.drawable.bg_status_disconnected);
        }
        checkPendingJobs();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "SETUP_PRINTER_FOR_PRINT".equals(intent.getStringExtra("action"))) {
            switchTab(0);
        }
    }

    private void onPrinterConnectedSuccessfully() {
        Intent intent = new Intent("com.antigravity.blututprinter.ACTION_PRINTER_CONNECTED");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        checkPendingJobs();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (printerManager.isConnected()) {
                    printPendingJobsDirectly();
                }
            }
        }, 1500);
    }

    private void completeActivePrintJobInService(String jobId) {
        Intent intent = new Intent("com.antigravity.blututprinter.ACTION_COMPLETE_PRINT_JOB");
        intent.setPackage(getPackageName());
        intent.putExtra("job_id", jobId);
        sendBroadcast(intent);
    }

    private void checkPendingJobs() {
        if (llPendingJobsWarning == null) return;
        List<PendingJobManager.PendingJob> jobs = PendingJobManager.getPendingJobs(this);
        if (jobs.isEmpty()) {
            llPendingJobsWarning.setVisibility(View.GONE);
        } else {
            llPendingJobsWarning.setVisibility(View.VISIBLE);
            tvPendingJobsDesc.setText(jobs.size() + " dokumen dalam antrian. Hubungkan printer.");
            if (printerManager.isConnected()) {
                btnForcePrintPending.setVisibility(View.VISIBLE);
            } else {
                btnForcePrintPending.setVisibility(View.GONE);
            }
        }
    }

    private void printPendingJobsDirectly() {
        if (!printerManager.isConnected()) return;
        final List<PendingJobManager.PendingJob> jobs = PendingJobManager.getPendingJobs(this);
        if (jobs.isEmpty()) return;

        appendLog("[System] Auto-printing " + jobs.size() + " pending jobs...");
        runOnUiThread(() -> {
            if (btnForcePrintPending != null) btnForcePrintPending.setEnabled(false);
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                int targetWidth = prefs.getInt("paper_width", 384);
                int contrastThreshold = prefs.getInt("print_contrast", 128);
                int feedLines = prefs.getInt("extra_feed", 3);
                boolean isThrottled = prefs.getBoolean("buffer_throttle", false);
                printerManager.setThrottled(isThrottled);

                for (PendingJobManager.PendingJob job : jobs) {
                    File file = new File(job.filePath);
                    if (!file.exists()) {
                        PendingJobManager.removePendingJob(MainActivity.this, job.id);
                        continue;
                    }

                    ParcelFileDescriptor pfd = null;
                    PdfRenderer renderer = null;
                    try {
                        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                        renderer = new PdfRenderer(pfd);
                        int pageCount = renderer.getPageCount();

                        for (int i = 0; i < pageCount; i++) {
                            PdfRenderer.Page page = renderer.openPage(i);
                            double scale = (double) targetWidth / page.getWidth();
                            int targetHeight = (int) (page.getHeight() * scale);

                            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            bitmap.eraseColor(Color.WHITE);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            page.close();

                            byte[] escPosData = EscPosDriver.bitmapToEscPos(bitmap, targetWidth, contrastThreshold, feedLines);
                            byte[] finalData = EscPosDriver.appendWatermark(escPosData);

                            boolean ok = printerManager.sendData(finalData);
                            if (!ok) {
                                throw new IOException("Failed to send data to bluetooth printer.");
                            }
                            bitmap.recycle();
                        }

                        PendingJobManager.removePendingJob(MainActivity.this, job.id);
                        completeActivePrintJobInService(job.id);

                        final String title = job.title;
                        runOnUiThread(() -> appendLog("[System] Printed pending job: " + title));

                    } catch (Exception e) {
                        Log.e(TAG, "Error printing pending job " + job.id, e);
                        final String errMsg = e.getMessage();
                        runOnUiThread(() -> appendLog("[System error] Failed to print " + job.title + ": " + errMsg));
                        break; 
                    } finally {
                        if (renderer != null) {
                            try {
                                renderer.close();
                            } catch (Exception ignored) {}
                        }
                        if (pfd != null) {
                            try {
                                pfd.close();
                            } catch (Exception ignored) {}
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (btnForcePrintPending != null) btnForcePrintPending.setEnabled(true);
                    checkPendingJobs();
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceivers();
    }
}
