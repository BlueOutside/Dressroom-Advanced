package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.UUID;

final class UpdateRepository {
    static final String PACKAGE = "dev.denver.dressroomadvanced";
    private static final String REPO = "https://github.com/BlueOutside/Dressroom-Advanced/";
    private static final String API =
            "https://api.github.com/repos/BlueOutside/Dressroom-Advanced/releases/latest";
    private static final long MAX_APK = 128L * 1024 * 1024;
    private static long checked;
    private static JSONObject cached;

    private UpdateRepository() {}

    // Separate from the settings monitor: a slow GitHub request must not hold up Apply.
    static synchronized Bundle check(Context context) throws Exception {
        long now = android.os.SystemClock.elapsedRealtime();
        if (checked == 0 || now - checked > 15 * 60_000) {
            cached = null;
            cached = latest();
            checked = now;
        }
        Bundle result = new Bundle();
        if (cached == null) return result;
        PackageInfo installed = context.getPackageManager().getPackageInfo(PACKAGE, 0);
        String version = cached.getString("tag_name");
        result.putString("latest", version);
        result.putString("installed", installed.versionName);
        if (ReleaseVersion.parse(version).compareTo(ReleaseVersion.parse(installed.versionName))
                <= 0) return result;
        JSONObject asset = asset(cached);
        if (asset == null) return result;
        String release = cached.toString();
        String assetIdentity = String.join("\n", version, asset.optString("id"),
                asset.getString("browser_download_url"), Long.toString(asset.getLong("size")),
                asset.optString("digest"), asset.optString("updated_at"));
        var requests = context.getSharedPreferences("updates", 0);
        long wallTime = System.currentTimeMillis();
        String token = requests.getString("token", null);
        if (token == null || wallTime >= requests.getLong("expires", 0)
                || !assetIdentity.equals(requests.getString("asset", null))) {
            token = UUID.randomUUID().toString();
            requests.edit()
                    .putString("token", token)
                    .putString("asset", assetIdentity)
                    .putString("release", release)
                    .putLong("expires", wallTime + 30 * 60_000)
                    .apply();
        }
        result.putString("version", version);
        result.putString("installed", installed.versionName);
        result.putString("token", token);
        return result;
    }

    static JSONObject authorized(Context context, String token) throws Exception {
        var prefs = context.getSharedPreferences("updates", 0);
        if (token == null
                || !token.equals(prefs.getString("token", null))
                || System.currentTimeMillis() > prefs.getLong("expires", 0))
            throw new IOException("This update request expired. Reopen the editor to check again.");
        return new JSONObject(prefs.getString("release", "{}"));
    }

    static File apk(Context context) {
        return new File(new File(context.getCacheDir(), "updates"), "release.apk");
    }

    private static JSONObject latest() throws Exception {
        HttpURLConnection connection = connect(API);
        try {
            if (connection.getResponseCode() == 404) return null;
            if (connection.getResponseCode() != 200) throw new IOException("Release check failed");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (var input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (data.size() + count > 2 * 1024 * 1024)
                        throw new IOException("Release response too large");
                    data.write(buffer, 0, count);
                }
            }
            JSONObject release =
                    new JSONObject(data.toString(java.nio.charset.StandardCharsets.UTF_8));
            return release.optBoolean("draft") || release.optBoolean("prerelease") ? null : release;
        } finally {
            connection.disconnect();
        }
    }

    static JSONObject asset(JSONObject release) throws Exception {
        var assets = release.getJSONArray("assets");
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.getJSONObject(i);
            String name = item.optString("name");
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) continue;
            if (!item.getString("browser_download_url").startsWith(REPO + "releases/download/"))
                continue;
            long size = item.optLong("size", 0);
            if (size <= 0 || size > MAX_APK) continue;
            if (found != null) throw new IOException("Release contains more than one APK");
            found = item;
        }
        return found;
    }

    interface Progress {
        void update(int percent);

        boolean cancelled();
    }

    static File download(Context context, JSONObject release, Progress progress) throws Exception {
        if (progress.cancelled()) throw new IOException("Download cancelled");
        JSONObject asset = asset(release);
        if (asset == null) throw new IOException("No update APK is available");
        File target = apk(context), partial = new File(target.getParentFile(), "release.part");
        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
            throw new IOException("Could not create update folder");
        long expected = asset.getLong("size"), received = 0;
        long deadline = android.os.SystemClock.elapsedRealtime() + 180_000;
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        HttpURLConnection connection = connect(asset.getString("browser_download_url"));
        try {
            if (connection.getResponseCode() != 200)
                throw new IOException("Update download failed");
            try (var input = connection.getInputStream();
                    var output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32768];
                int count, previous = -1;
                while ((count = input.read(buffer)) != -1) {
                    if (progress.cancelled()) throw new IOException("Download cancelled");
                    if (android.os.SystemClock.elapsedRealtime() > deadline)
                        throw new IOException("Download timed out");
                    received += count;
                    if (received > expected || received > MAX_APK)
                        throw new IOException("Update size mismatch");
                    output.write(buffer, 0, count);
                    hash.update(buffer, 0, count);
                    int percent = (int) (received * 100 / expected);
                    if (percent != previous) {
                        progress.update(percent);
                        previous = percent;
                    }
                }
            }
            if (received != expected) throw new IOException("Update download was incomplete");
            String digest = asset.optString("digest");
            if (digest.startsWith("sha256:")
                    && !digest.substring(7).equalsIgnoreCase(hex(hash.digest())))
                throw new IOException("Update checksum mismatch");
            verify(context, partial, release.getString("tag_name"));
            if (progress.cancelled()) throw new IOException("Download cancelled");
            java.nio.file.Files.move(
                    partial.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            connection.disconnect();
            partial.delete();
        }
    }

    static void verify(Context context, File file, String releaseVersion) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive =
                pm.getPackageArchiveInfo(
                        file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null
                || !PACKAGE.equals(archive.packageName)
                || archive.signingInfo == null
                || archive.getLongVersionCode() <= installed.getLongVersionCode()
                || ReleaseVersion.parse(archive.versionName)
                                .compareTo(ReleaseVersion.parse(releaseVersion))
                        != 0)
            throw new IOException("The downloaded APK is not a newer Dressroom Advanced release");
        for (var signature : archive.signingInfo.getApkContentsSigners()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            if (!pm.hasSigningCertificate(PACKAGE, digest, PackageManager.CERT_INPUT_SHA256))
                throw new IOException("The update was signed with a different key");
        }
        if (archive.signingInfo.getApkContentsSigners().length == 0)
            throw new IOException("Update signature is missing");
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    private static HttpURLConnection connect(String address) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            URI uri = URI.create(address);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !("api.github.com".equals(host)
                            || "github.com".equals(host)
                            || (host != null && host.endsWith(".githubusercontent.com"))))
                throw new IOException("Untrusted update URL");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "Dressroom-Advanced-Updater");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                int code = connection.getResponseCode();
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308)
                    return connection;
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Update redirect is missing");
                address = uri.resolve(location).toString();
            } catch (Exception error) {
                connection.disconnect();
                throw error;
            }
        }
        throw new IOException("Too many update redirects");
    }
}
