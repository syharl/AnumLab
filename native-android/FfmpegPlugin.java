package com.syharl.anumlab;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "Ffmpeg")
public class FfmpegPlugin extends Plugin {

    // Jalankan perintah FFmpeg secara async + laporkan progress 0-100%
    // lewat event "progress" (dengarkan dari JS pakai Ffmpeg.addListener)
    @PluginMethod
    public void run(final PluginCall call) {
        String command = call.getString("command");
        String durationSourcePath = call.getString("durationSourcePath");
        if (command == null) {
            call.reject("Parameter 'command' wajib diisi");
            return;
        }

        final long[] totalDurationMs = { 0 };
        if (durationSourcePath != null) {
            MediaInformationSession infoSession = FFprobeKit.getMediaInformation(durationSourcePath);
            MediaInformation info = infoSession.getMediaInformation();
            if (info != null && info.getDuration() != null) {
                try {
                    totalDurationMs[0] = (long) (Double.parseDouble(info.getDuration()) * 1000);
                } catch (NumberFormatException ignored) {}
            }
        }

        FFmpegKit.executeAsync(command, session -> {
            boolean success = ReturnCode.isSuccess(session.getReturnCode());
            JSObject result = new JSObject();
            result.put("success", success);
            result.put("returnCode", session.getReturnCode() != null ? session.getReturnCode().getValue() : -1);
            result.put("logs", session.getAllLogsAsString());

            if (success) {
                call.resolve(result);
            } else {
                call.reject("FFmpeg gagal menjalankan perintah", (String) null, result);
            }
        }, log -> {
            // tempat kalau nanti perlu log baris-per-baris untuk debug
        }, statistics -> {
            JSObject progress = new JSObject();
            long timeMs = statistics.getTime();
            double percent = 0;
            if (totalDurationMs[0] > 0) {
                percent = Math.min(100.0, (timeMs / (double) totalDurationMs[0]) * 100.0);
            }
            progress.put("percent", percent);
            notifyListeners("progress", progress);
        });
    }

    // Simpan file hasil ke galeri (folder Movies/AnumLab), supaya
    // kelihatan di aplikasi Galeri / Files biasa, bukan folder privat app
    @PluginMethod
    public void saveToGallery(PluginCall call) {
        String sourcePath = call.getString("sourcePath");
        String fileName = call.getString("fileName");
        if (sourcePath == null || fileName == null) {
            call.reject("Parameter 'sourcePath' dan 'fileName' wajib diisi");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("Simpan ke galeri butuh Android 10 ke atas");
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AnumLab");

            Uri uri = getContext().getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) {
                call.reject("Gagal membuat entri di galeri");
                return;
            }

            try (InputStream in = new FileInputStream(sourcePath);
                 OutputStream out = getContext().getContentResolver().openOutputStream(uri)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            JSObject result = new JSObject();
            result.put("uri", uri.toString());
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Gagal menyimpan ke galeri: " + e.getMessage());
        }
    }

    // Salin file contoh dari folder assets web (www/assets/...) ke
    // folder cache aplikasi, supaya punya path absolut yang bisa
    // dipakai FFmpeg (FFmpeg tidak bisa baca langsung dari assets APK)
    @PluginMethod
    public void copyAssetToCache(PluginCall call) {
        String fileName = call.getString("fileName");
        if (fileName == null) {
            call.reject("Parameter 'fileName' wajib diisi");
            return;
        }
        try {
            InputStream in = getContext().getAssets().open("public/" + fileName);
            File outFile = new File(getContext().getCacheDir(), new File(fileName).getName());
            FileOutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            JSObject result = new JSObject();
            result.put("path", outFile.getAbsolutePath());
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Gagal menyalin asset '" + fileName + "': " + e.getMessage());
        }
    }

    // Ambil path folder cache, dipakai untuk menentukan lokasi file hasil
    @PluginMethod
    public void getCacheDir(PluginCall call) {
        JSObject result = new JSObject();
        result.put("path", getContext().getCacheDir().getAbsolutePath());
        call.resolve(result);
    }
}

