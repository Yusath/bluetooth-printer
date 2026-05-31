package com.antigravity.blututprinter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PendingJobManager {
    private static final String TAG = "PendingJobManager";
    private static final String PREFS_NAME = "BlututPrinterPrefs";
    private static final String KEY_PENDING_JOBS = "pending_jobs_json";

    public static class PendingJob {
        public String id;
        public String title;
        public String filePath;
        public long timestamp;

        public PendingJob(String id, String title, String filePath, long timestamp) {
            this.id = id;
            this.title = title;
            this.filePath = filePath;
            this.timestamp = timestamp;
        }
    }

    public static synchronized List<PendingJob> getPendingJobs(Context context) {
        List<PendingJob> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_PENDING_JOBS, "[]");
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new PendingJob(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.getString("filePath"),
                    obj.getLong("timestamp")
                ));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing pending jobs", e);
        }
        return list;
    }

    public static synchronized void savePendingJobs(Context context, List<PendingJob> jobs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        for (PendingJob job : jobs) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", job.id);
                obj.put("title", job.title);
                obj.put("filePath", job.filePath);
                obj.put("timestamp", job.timestamp);
                array.put(obj);
            } catch (JSONException e) {
                Log.e(TAG, "Error serializing job", e);
            }
        }
        prefs.edit().putString(KEY_PENDING_JOBS, array.toString()).apply();
    }

    public static synchronized void addPendingJob(Context context, String id, String title, File tempFile) {
        List<PendingJob> jobs = getPendingJobs(context);
        removePendingJobFromList(jobs, id);
        jobs.add(new PendingJob(id, title, tempFile.getAbsolutePath(), System.currentTimeMillis()));
        savePendingJobs(context, jobs);
        Log.d(TAG, "Added pending job: " + id + " -> " + tempFile.getAbsolutePath());
    }

    public static synchronized void removePendingJob(Context context, String id) {
        List<PendingJob> jobs = getPendingJobs(context);
        PendingJob found = null;
        for (PendingJob job : jobs) {
            if (job.id.equals(id)) {
                found = job;
                break;
            }
        }
        if (found != null) {
            jobs.remove(found);
            savePendingJobs(context, jobs);
            File file = new File(found.filePath);
            if (file.exists()) {
                file.delete();
            }
            Log.d(TAG, "Removed pending job: " + id);
        }
    }

    public static synchronized void clearAllPendingJobs(Context context) {
        List<PendingJob> jobs = getPendingJobs(context);
        for (PendingJob job : jobs) {
            File file = new File(job.filePath);
            if (file.exists()) {
                file.delete();
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PENDING_JOBS).apply();
        Log.d(TAG, "Cleared all pending jobs");
    }

    private static void removePendingJobFromList(List<PendingJob> jobs, String id) {
        PendingJob found = null;
        for (PendingJob job : jobs) {
            if (job.id.equals(id)) {
                found = job;
                break;
            }
        }
        if (found != null) {
            jobs.remove(found);
            File file = new File(found.filePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public static File copyPdfToTemp(Context context, InputStream in, String jobId) throws Exception {
        File dir = new File(context.getCacheDir(), "pending_jobs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File tempFile = new File(dir, "job_" + jobId + "_" + System.currentTimeMillis() + ".pdf");
        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
        return tempFile;
    }
}
