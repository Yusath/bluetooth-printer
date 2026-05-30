package com.antigravity.blututprinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothPrinterManager {
    private static final String TAG = "BtPrinterManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothPrinterManager instance;

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private BluetoothDevice connectedDevice;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isConnecting = false;

    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String message);
    }

    private BluetoothPrinterManager() {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized BluetoothPrinterManager getInstance() {
        if (instance == null) {
            instance = new BluetoothPrinterManager();
        }
        return instance;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return null;
        }
        try {
            return bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error getting bonded devices", e);
            return null;
        }
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && outputStream != null;
    }

    public synchronized String getConnectedDeviceName() {
        if (isConnected() && connectedDevice != null) {
            try {
                return connectedDevice.getName();
            } catch (SecurityException e) {
                return connectedDevice.getAddress();
            }
        }
        return null;
    }

    public synchronized String getConnectedDeviceAddress() {
        if (isConnected() && connectedDevice != null) {
            return connectedDevice.getAddress();
        }
        return null;
    }

    public synchronized void connect(final BluetoothDevice device, final ConnectionCallback callback) {
        if (isConnecting) {
            if (callback != null) callback.onFailure("Already trying to connect.");
            return;
        }
        isConnecting = true;
        disconnect();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (SecurityException se) {
                        postFailure(callback, "Bluetooth permission denied: " + se.getMessage());
                        isConnecting = false;
                        return;
                    }

                    try {
                        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                            bluetoothAdapter.cancelDiscovery();
                        }
                    } catch (SecurityException ignored) {}

                    socket.connect();
                    outputStream = socket.getOutputStream();
                    connectedDevice = device;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isConnecting = false;
                            if (callback != null) callback.onSuccess();
                        }
                    });

                } catch (final IOException e) {
                    Log.e(TAG, "Connection failed", e);
                    cleanup();
                    postFailure(callback, "Failed to connect: " + e.getMessage());
                }
            }
        });
    }

    private void postFailure(final ConnectionCallback callback, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                isConnecting = false;
                if (callback != null) callback.onFailure(message);
            }
        });
    }

    public synchronized boolean sendData(final byte[] data) {
        if (!isConnected()) {
            Log.e(TAG, "Printer not connected.");
            return false;
        }
        try {
            outputStream.write(data);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to send data, disconnecting", e);
            disconnect();
            return false;
        }
    }

    public synchronized void disconnect() {
        cleanup();
    }

    private void cleanup() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {}
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {}
        outputStream = null;
        socket = null;
        connectedDevice = null;
    }
}
