package com.syharl.anumlab;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

@CapacitorPlugin(name = "Ffmpeg")
public class FfmpegPlugin extends Plugin {

    // Jalankan perintah FFmpeg mentah, dipanggil dari JS
    @PluginMethod
    public void run(PluginCall call) {
        String command = call.getString("command");
        if (command == null) {
            call.reject("Parameter 'command' wajib diisi");
            return;
        }

        FFmpegSession session = FFmpegKit.execute(command);

        JSObject result = new JSObject();
        result.put("success", ReturnCode.isSuccess(session.getReturnCode()));
        result.put("returnCode", session.getReturnCode() != null ? session.getReturnCode().getValue() : -1);
        result.put("logs", session.getAllLogsAsString());

        if (ReturnCode.isSuccess(session.getReturnCode())) {
            call.resolve(result);
        } else {
            call.reject("FFmpeg gagal menjalankan perintah", null, result);
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
