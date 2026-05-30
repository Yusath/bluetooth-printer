package com.antigravity.blututprinter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoUpdater {
    private static final String TAG = "AutoUpdater";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/Yusath/bluetooth-printer/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpdateCheckCallback {
        void onNoUpdate();
        void onError(String error);
    }

    public static void checkForUpdates(final Activity activity, final boolean manualCheck, final UpdateCheckCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String currentVersion = "";
                    try {
                        PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                        currentVersion = pInfo.versionName;
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting local version name", e);
                    }

                    if (currentVersion.isEmpty()) {
                        postError(callback, "Could not determine local app version.");
                        return;
                    }

                    URL url = new URL(GITHUB_RELEASE_API);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "Blutut-Printer-Updater");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        // 404 indicates no releases have been published on this repository yet
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onNoUpdate();
                                } else if (manualCheck) {
                                    Toast.makeText(activity, "No releases found on GitHub yet.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        return;
                    }

                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        postError(callback, "GitHub server returned code: " + responseCode);
                        return;
                    }

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                    in.close();
                    conn.disconnect();

                    String response = out.toString("UTF-8");
                    JSONObject json = new JSONObject(response);

                    final String tagName = json.getString("tag_name");
                    final String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    
                    JSONArray assets = json.getJSONArray("assets");
                    String apkDownloadUrlTemp = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.getString("name");
                        if (name.endsWith(".apk")) {
                            apkDownloadUrlTemp = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    final String apkDownloadUrl = apkDownloadUrlTemp;
                    if (apkDownloadUrl == null) {
                        postError(callback, "No APK asset found in the latest release.");
                        return;
                    }

                    final String currentVerFinal = currentVersion;
                    if (isNewerVersion(currentVerFinal, latestVersion)) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(activity, latestVersion, currentVerFinal, apkDownloadUrl);
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onNoUpdate();
                                } else if (manualCheck) {
                                    Toast.makeText(activity, "You are already using the latest version (" + currentVerFinal + ")", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error checking for updates", e);
                    postError(callback, e.getMessage());
                }
            }
        });
    }

    private static void postError(final UpdateCheckCallback callback, final String message) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(message);
                }
            });
        }
    }

    private static boolean isNewerVersion(String currentStr, String latestStr) {
        if (currentStr == null || latestStr == null) return false;
        String[] currentParts = currentStr.split("\\.");
        String[] latestParts = latestStr.split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int currentVal = 0;
            if (i < currentParts.length) {
                try {
                    currentVal = Integer.parseInt(currentParts[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {}
            }
            int latestVal = 0;
            if (i < latestParts.length) {
                try {
                    latestVal = Integer.parseInt(latestParts[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {}
            }
            if (latestVal > currentVal) return true;
            if (currentVal > latestVal) return false;
        }
        return false;
    }

    private static void showUpdateDialog(final Activity activity, final String newVersion, final String currentVersion, final String downloadUrl) {
        new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Update Available!")
                .setMessage("A new version (" + newVersion + ") is available. You are currently on " + currentVersion + ".\n\nWould you like to download and install the update now?")
                .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        checkInstallPermissionAndDownload(activity, downloadUrl);
                    }
                })
                .setNegativeButton("Later", null)
                .setIcon(android.R.drawable.stat_sys_download)
                .show();
    }

    private static void checkInstallPermissionAndDownload(final Activity activity, final String downloadUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Permission Required")
                        .setMessage("To install updates, you must grant permission to allow installing unknown apps from this source.")
                        .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                                Toast.makeText(activity, "Grant permission, then press 'Check for Update' again.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }
        downloadAndInstallApk(activity, downloadUrl);
    }

    private static void downloadAndInstallApk(final Activity activity, final String downloadUrl) {
        final ProgressDialog progressDialog = new ProgressDialog(activity, ProgressDialog.THEME_DEVICE_DEFAULT_DARK);
        progressDialog.setTitle("Downloading Update");
        progressDialog.setMessage("Connecting to server...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.show();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.connect();

                    final int fileLength = conn.getContentLength();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setIndeterminate(false);
                        }
                    });

                    InputStream input = new BufferedInputStream(url.openStream(), 8192);
                    
                    File apkFile = new File(activity.getExternalCacheDir(), "update.apk");
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                    
                    FileOutputStream output = new FileOutputStream(apkFile);

                    byte[] data = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setProgress(progress);
                                }
                            });
                        }
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            installApk(activity, apkFile);
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Error downloading APK", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private static void installApk(Activity activity, File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(activity, "com.antigravity.blututprinter.fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error running APK installation intent", e);
            Toast.makeText(activity, "Could not open installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
