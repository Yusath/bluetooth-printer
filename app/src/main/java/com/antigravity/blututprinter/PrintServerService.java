package com.antigravity.blututprinter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrintServerService extends Service {
    private static final String TAG = "PrintServerService";
    private static final String CHANNEL_ID = "PrintServerChannel";
    private static final int NOTIFICATION_ID = 888;

    public static final String ACTION_STATUS_CHANGE = "com.antigravity.blututprinter.STATUS_CHANGE";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PORT = "port";

    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private int port = 6801;
    private ExecutorService serverExecutor;
    private BluetoothPrinterManager printerManager;

    @Override
    public void onCreate() {
        super.onCreate();
        printerManager = BluetoothPrinterManager.getInstance();
        serverExecutor = Executors.newCachedThreadPool();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences("BlututPrinterPrefs", MODE_PRIVATE);
        port = prefs.getInt("server_port", 6801);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Starting printer bridge..."));

        startServer();

        return START_STICKY;
    }

    private void startServer() {
        if (isRunning) return;
        isRunning = true;

        serverExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    Log.d(TAG, "Server started on port " + port);
                    broadcastStatus(true, port);
                    updateNotification("Printer bridge is running on port " + port);

                    while (isRunning) {
                        final Socket clientSocket = serverSocket.accept();
                        serverExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(clientSocket);
                            }
                        });
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Server socket error", e);
                    stopServer();
                }
            }
        });
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        serverSocket = null;
        broadcastStatus(false, port);
        stopForeground(true);
        stopSelf();
    }

    private void handleClient(Socket socket) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            byte[] buffer = new byte[8192];
            int bytesRead = in.read(buffer);
            if (bytesRead <= 0) {
                socket.close();
                return;
            }

            String headerStr = new String(buffer, 0, Math.min(bytesRead, 1024));
            if (headerStr.startsWith("POST ") || headerStr.startsWith("OPTIONS ") || headerStr.startsWith("GET ")) {
                handleHttpRequest(socket, headerStr, buffer, bytesRead, in, out);
            } else {
                ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();
                rawBytes.write(buffer, 0, bytesRead);
                
                byte[] tempBuffer = new byte[4096];
                int read;
                while (in.available() > 0 && (read = in.read(tempBuffer)) != -1) {
                    rawBytes.write(tempBuffer, 0, read);
                }
                
                printBytes(rawBytes.toByteArray());
                
                out.write("OK\n".getBytes());
                out.flush();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error handling client socket", e);
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleHttpRequest(Socket socket, String firstHeaders, byte[] initialBuffer, int initialBytesRead, InputStream in, OutputStream out) throws IOException {
        boolean isOptions = firstHeaders.startsWith("OPTIONS ");
        boolean isPost = firstHeaders.startsWith("POST ");
        
        int contentLength = 0;
        String[] lines = firstHeaders.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        if (isOptions) {
            String response = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: POST, OPTIONS, GET\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, X-Requested-With, Origin, Accept\r\n" +
                    "Access-Control-Max-Age: 86400\r\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n";
            out.write(response.getBytes());
            out.flush();
            return;
        }

        if (!isPost) {
            String body = "{\"status\":\"running\",\"message\":\"Bluetooth Printer Bridge is active.\"}";
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "\r\n" + body;
            out.write(response.getBytes());
            out.flush();
            return;
        }

        int headerEndIndex = -1;
        for (int i = 0; i < initialBytesRead - 3; i++) {
            if (initialBuffer[i] == '\r' && initialBuffer[i+1] == '\n' &&
                initialBuffer[i+2] == '\r' && initialBuffer[i+3] == '\n') {
                headerEndIndex = i + 4;
                break;
            }
        }

        if (headerEndIndex == -1) {
            sendHttpResponse(out, 400, "{\"error\":\"Invalid HTTP request headers\"}");
            return;
        }

        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        int bytesInBodyChunk = initialBytesRead - headerEndIndex;
        if (bytesInBodyChunk > 0) {
            bodyStream.write(initialBuffer, headerEndIndex, bytesInBodyChunk);
        }

        if (contentLength > 0) {
            int remainingBytes = contentLength - bytesInBodyChunk;
            byte[] tempBuf = new byte[4096];
            while (remainingBytes > 0) {
                int read = in.read(tempBuf, 0, Math.min(tempBuf.length, remainingBytes));
                if (read == -1) break;
                bodyStream.write(tempBuf, 0, read);
                remainingBytes -= read;
            }
        } else {
            byte[] tempBuf = new byte[4096];
            int read;
            while (in.available() > 0 && (read = in.read(tempBuf)) != -1) {
                bodyStream.write(tempBuf, 0, read);
            }
        }

        byte[] bodyBytes = bodyStream.toByteArray();
        boolean success = false;
        
        String bodyString = new String(bodyBytes, 0, Math.min(bodyBytes.length, 1024));
        if (bodyString.trim().startsWith("{")) {
            try {
                String trimmed = bodyString.trim();
                if (trimmed.contains("\"base64\"")) {
                    int base64Index = trimmed.indexOf("\"base64\"");
                    int colonIndex = trimmed.indexOf(":", base64Index);
                    int startQuote = trimmed.indexOf("\"", colonIndex);
                    int endQuote = trimmed.indexOf("\"", startQuote + 1);
                    if (startQuote != -1 && endQuote != -1) {
                        String b64Value = trimmed.substring(startQuote + 1, endQuote);
                        byte[] decoded = Base64.decode(b64Value, Base64.DEFAULT);
                        success = printBytes(decoded);
                    }
                } else if (trimmed.contains("\"text\"")) {
                    int textIndex = trimmed.indexOf("\"text\"");
                    int colonIndex = trimmed.indexOf(":", textIndex);
                    int startQuote = trimmed.indexOf("\"", colonIndex);
                    int endQuote = trimmed.indexOf("\"", startQuote + 1);
                    if (startQuote != -1 && endQuote != -1) {
                        String textValue = trimmed.substring(startQuote + 1, endQuote);
                        textValue = textValue.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
                        success = printBytes(textValue.getBytes());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error manually parsing print JSON request", e);
            }
        } else {
            success = printBytes(bodyBytes);
        }

        if (success) {
            sendHttpResponse(out, 200, "{\"success\":true,\"message\":\"Printed successfully\"}");
        } else {
            sendHttpResponse(out, 500, "{\"success\":false,\"error\":\"Printer is not connected or failed to print\"}");
        }
    }

    private void sendHttpResponse(OutputStream out, int statusCode, String jsonBody) throws IOException {
        String statusMessage = statusCode == 200 ? "200 OK" : (statusCode == 400 ? "400 Bad Request" : "500 Internal Server Error");
        String response = "HTTP/1.1 " + statusMessage + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + jsonBody.getBytes().length + "\r\n" +
                "\r\n" + jsonBody;
        out.write(response.getBytes());
        out.flush();
    }

    private boolean printBytes(byte[] bytes) {
        if (printerManager.isConnected()) {
            return printerManager.sendData(bytes);
        }
        return false;
    }

    private void broadcastStatus(boolean running, int port) {
        Intent intent = new Intent(ACTION_STATUS_CHANGE);
        intent.putExtra(EXTRA_STATUS, running);
        intent.putExtra(EXTRA_PORT, port);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Printer Bridge Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Printer Server")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(contentText));
        }
    }
}
