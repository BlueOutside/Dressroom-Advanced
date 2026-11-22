package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class UpdateRepository {
    static final String PACKAGE = "dev.denver.dressroomadvanced";
    static final String PREF_PRERELEASE = "prerelease_channel";
    static final String PREF_PENDING_RELAUNCH_UNTIL = "pending_relaunch_until";
    static final long RELAUNCH_WINDOW_MS = 5 * 60_000L;
    private static final String REPO = "https://github.com/BlueOutside/Dressroom-Advanced/";
    private static final String API =
            "https://api.github.com/repos/BlueOutside/Dressroom-Advanced/releases/latest";
    private static final String RELEASES_API =
            "https://api.github.com/repos/BlueOutside/Dressroom-Advanced/releases?per_page=30";
    private static final long MAX_APK = 128L * 1024 * 1024;
    private static long checked;
    private static List<JSONObject> cachedReleases;
    private static boolean cachedIncludePrerelease;

    record ReleaseCandidate<T>(String tag, boolean draft, boolean prerelease, T payload) {}

    record UpdateSelection<T>(ReleaseCandidate<T> stable, ReleaseCandidate<T> prerelease) {
        boolean hasUpdate() {
            return stable != null || prerelease != null;
        }

        boolean hasChoice() {
            return stable != null && prerelease != null;
        }
    }

    private record PreparedRelease(JSONObject release, JSONObject asset) {}

    private UpdateRepository() {}

    static boolean prereleaseEnabled(
            boolean hasExplicitPreference, boolean explicitPreference, String installedVersion) {
        if (hasExplicitPreference) return explicitPreference;
        try {
            return ReleaseVersion.parse(installedVersion).isPrerelease();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static <T> UpdateSelection<T> select(
            List<ReleaseCandidate<T>> candidates,
            String installedVersion,
            boolean includePrerelease) {
        if (candidates == null || candidates.isEmpty()) {
            return new UpdateSelection<>(null, null);
        }
        ReleaseVersion installed = ReleaseVersion.parse(installedVersion);
        ReleaseCandidate<T> bestStable = null;
        ReleaseVersion bestStableVersion = null;
        ReleaseCandidate<T> bestPrerelease = null;
        ReleaseVersion bestPrereleaseVersion = null;
        for (ReleaseCandidate<T> candidate : candidates) {
            if (candidate == null || candidate.draft() || candidate.payload() == null) continue;
            ReleaseVersion version;
            try {
                version = ReleaseVersion.parse(candidate.tag());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            boolean isPre = candidate.prerelease() || version.isPrerelease();
            if (isPre && !includePrerelease) continue;
            if (version.compareTo(installed) <= 0) continue;
            if (isPre) {
                if (bestPrereleaseVersion == null
                        || version.compareTo(bestPrereleaseVersion) > 0) {
                    bestPrerelease = candidate;
                    bestPrereleaseVersion = version;
                }
            } else {
                if (bestStableVersion == null || version.compareTo(bestStableVersion) > 0) {
                    bestStable = candidate;
                    bestStableVersion = version;
                }
            }
        }
        // Once a stable build equals or supersedes the newest pre-release, offer only stable.
        if (bestStable != null
                && bestPrerelease != null
                && bestPrereleaseVersion.compareTo(bestStableVersion) <= 0) {
            bestPrerelease = null;
        }
        return new UpdateSelection<>(bestStable, bestPrerelease);
    }

    static boolean shouldRelaunchAfterInstall(long pendingUntilMs, long nowMs) {
        return pendingUntilMs > 0 && nowMs > 0 && nowMs <= pendingUntilMs;
    }

    static void markPendingRelaunch(Context context) {
        long deadline = System.currentTimeMillis() + RELAUNCH_WINDOW_MS;
        context.getSharedPreferences("updates", 0)
                .edit()
                .putLong(PREF_PENDING_RELAUNCH_UNTIL, deadline)
                .commit();
    }

    static void clearPendingRelaunch(Context context) {
        context.getSharedPreferences("updates", 0)
                .edit()
                .remove(PREF_PENDING_RELAUNCH_UNTIL)
                .commit();
    }

    static boolean consumePendingRelaunch(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("updates", 0);
        long until = prefs.getLong(PREF_PENDING_RELAUNCH_UNTIL, 0L);
        if (until != 0L) {
            prefs.edit().remove(PREF_PENDING_RELAUNCH_UNTIL).commit();
        }
        return shouldRelaunchAfterInstall(until, System.currentTimeMillis());
    }

    static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return -1L;
        }
    }

    static long archiveVersionCode(Context context, File apk) {
        if (apk == null || !apk.isFile()) return -1L;
        try {
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return archive == null ? -1L : archive.getLongVersionCode();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static String installedVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return "";
        }
    }

    static boolean isPrereleaseEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("updates", 0);
        boolean hasExplicit = prefs.contains(PREF_PRERELEASE);
        boolean explicit = prefs.getBoolean(PREF_PRERELEASE, false);
        return prereleaseEnabled(hasExplicit, explicit, installedVersionName(context));
    }

    static synchronized Bundle preferences(Context context) {
        Bundle result = new Bundle();
        result.putBoolean("prerelease", isPrereleaseEnabled(context));
        result.putString("installed", installedVersionName(context));
        return result;
    }

    static synchronized Bundle setPrereleaseEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("updates", 0)
                .edit()
                .putBoolean(PREF_PRERELEASE, enabled)
                .commit();
        checked = 0;
        cachedReleases = null;
        return preferences(context);
    }

    static synchronized Bundle check(Context context) throws Exception {
        return check(context, false);
    }

    // Separate from the settings monitor: a slow GitHub request must not hold up Apply.
    static synchronized Bundle check(Context context, boolean force) throws Exception {
        boolean includePrerelease = isPrereleaseEnabled(context);
        long now = android.os.SystemClock.elapsedRealtime();
        if (force
                || checked == 0
                || now - checked > 15 * 60_000
                || cachedIncludePrerelease != includePrerelease
                || cachedReleases == null) {
            cachedReleases = null;
            cachedReleases = fetchReleases(includePrerelease);
            cachedIncludePrerelease = includePrerelease;
            checked = now;
        }
        String installedVersion = installedVersionName(context);
        Bundle result = new Bundle();
        result.putString("installed", installedVersion);
        result.putBoolean("prereleaseEnabled", includePrerelease);
        if (cachedReleases == null || cachedReleases.isEmpty() || installedVersion.isEmpty()) {
            return result;
        }
        List<ReleaseCandidate<PreparedRelease>> candidates =
                new ArrayList<>(cachedReleases.size());
        for (JSONObject release : cachedReleases) {
            String tag = release.optString("tag_name", "");
            boolean draft = release.optBoolean("draft");
            boolean pre = release.optBoolean("prerelease");
            JSONObject asset = null;
            try {
                asset = asset(release);
            } catch (IOException ignored) {
            }
            candidates.add(
                    new ReleaseCandidate<>(
                            tag,
                            draft,
                            pre,
                            asset == null ? null : new PreparedRelease(release, asset)));
        }
        UpdateSelection<PreparedRelease> selection =
                select(candidates, installedVersion, includePrerelease);
        if (!selection.hasUpdate()) return result;

        SharedPreferences requests = context.getSharedPreferences("updates", 0);
        long wallTime = System.currentTimeMillis();
        if (selection.hasChoice()) {
            String stableToken = stageToken(requests, "stable", selection.stable(), wallTime);
            String preToken = stageToken(requests, "prerelease", selection.prerelease(), wallTime);
            result.putString("version", selection.stable().tag());
            result.putString("token", stableToken);
            result.putBoolean("prerelease", false);
            result.putString("altVersion", selection.prerelease().tag());
            result.putString("altToken", preToken);
            result.putBoolean("altPrerelease", true);
            result.putString("latest", selection.prerelease().tag());
        } else if (selection.stable() != null) {
            String token = stageToken(requests, "stable", selection.stable(), wallTime);
            result.putString("version", selection.stable().tag());
            result.putString("token", token);
            result.putBoolean("prerelease", false);
            result.putString("latest", selection.stable().tag());
        } else {
            String token = stageToken(requests, "prerelease", selection.prerelease(), wallTime);
            result.putString("version", selection.prerelease().tag());
            result.putString("token", token);
            result.putBoolean("prerelease", true);
            result.putString("latest", selection.prerelease().tag());
        }
        return result;
    }

    private static String stageToken(
            SharedPreferences prefs,
            String slot,
            ReleaseCandidate<PreparedRelease> candidate,
            long wallTime)
            throws Exception {
        String suffix = "_" + slot;
        PreparedRelease prepared = candidate.payload();
        JSONObject asset = prepared.asset();
        String assetIdentity =
                String.join(
                        "\n",
                        candidate.tag(),
                        asset.optString("id"),
                        asset.getString("browser_download_url"),
                        Long.toString(asset.getLong("size")),
                        asset.optString("digest"),
                        asset.optString("updated_at"));
        String token = prefs.getString("token" + suffix, null);
        if (token == null
                || wallTime >= prefs.getLong("expires" + suffix, 0)
                || !assetIdentity.equals(prefs.getString("asset" + suffix, null))) {
            token = UUID.randomUUID().toString();
            prefs.edit()
                    .putString("token" + suffix, token)
                    .putString("asset" + suffix, assetIdentity)
                    .putString("release" + suffix, prepared.release().toString())
                    .putLong("expires" + suffix, wallTime + 30 * 60_000)
                    .apply();
        }
        return token;
    }

    static JSONObject authorized(Context context, String token) throws Exception {
        var prefs = context.getSharedPreferences("updates", 0);
        long now = System.currentTimeMillis();
        for (String suffix : new String[] {"_stable", "_prerelease", ""}) {
            if (token != null
                    && token.equals(prefs.getString("token" + suffix, null))
                    && now <= prefs.getLong("expires" + suffix, 0)) {
                return new JSONObject(prefs.getString("release" + suffix, "{}"));
            }
        }
        throw new IOException("This update request expired. Reopen the editor to check again.");
    }

    static File apk(Context context) {
        return new File(new File(context.getCacheDir(), "updates"), "release.apk");
    }

    private static List<JSONObject> fetchReleases(boolean includePrerelease) throws Exception {
        List<JSONObject> releases = new ArrayList<>();
        if (!includePrerelease) {
            String body = fetchJson(API);
            if (body != null) releases.add(new JSONObject(body));
            return releases;
        }
        String body = fetchJson(RELEASES_API);
        boolean sawStable = false;
        if (body != null) {
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                releases.add(item);
                if (!item.optBoolean("draft") && !item.optBoolean("prerelease")) {
                    sawStable = true;
                }
            }
        }
        if (!sawStable) {
            String latestBody = fetchJson(API);
            if (latestBody != null) releases.add(new JSONObject(latestBody));
        }
        return releases;
    }

    private static String fetchJson(String url) throws Exception {
        HttpURLConnection connection = connect(url);
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
            return data.toString(java.nio.charset.StandardCharsets.UTF_8);
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
