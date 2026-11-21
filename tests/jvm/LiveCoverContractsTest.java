package dev.denver.dressroomadvanced;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class LiveCoverContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void verifyHtmlEditorialVideoExtraction() {
        String html =
                "<html><head><title>Album</title></head><body>"
                        + "<script>window.__noise = {\"video\":\"https://bad.example/wrong.m3u8\"};</script>"
                        + "<script type=\"application/json\" id=\"serialized-server-data\">"
                        + "{\"data\":[{\"seo\":{\"video\":\"https:\\/\\/bad.example\\/outer.m3u8\"},"
                        + "\"squareVideoArtwork\":{\"dictionary\":{\"motionDetailSquare\":{"
                        + "\"previewFrame\":{\"url\":\"https://is1-ssl.mzstatic.com/thumb.png\"},"
                        + "\"video\":\"https:\\/\\/mvod.itunes.apple.com\\/square\\/P1_default.m3u8\"}}},"
                        + "\"tallVideoArtwork\":{\"dictionary\":{\"motionDetailTall\":{"
                        + "\"previewFrame\":{\"bgColor\":\"19181a\"},"
                        + "\"video\":\"https://mvod.itunes.apple.com/tall/P2_default.m3u8\"}}}}"
                        + "]}"
                        + "</script></body></html>";

        AppleHlsResolver.AppleEditorialVideo parsed =
                AppleHlsResolver.extractEditorialVideoFromHtml(html);
        check(
                "https://mvod.itunes.apple.com/tall/P2_default.m3u8".equals(parsed.tallM3u8Url()),
                "Expected tall editorial video URL, got: " + parsed.tallM3u8Url());
        check(
                "https://mvod.itunes.apple.com/square/P1_default.m3u8".equals(parsed.squareM3u8Url()),
                "Expected unescaped square editorial video URL, got: " + parsed.squareM3u8Url());

        String squareOnlyHtml =
                "<script id='serialized-server-data' type='application/json'>"
                        + "{\"motionDetailSquare\":{\"video\":\"https://mvod.itunes.apple.com/s.m3u8\"}}"
                        + "</script>";
        AppleHlsResolver.AppleEditorialVideo squareOnly =
                AppleHlsResolver.extractEditorialVideoFromHtml(squareOnlyHtml);
        check(
                squareOnly.tallM3u8Url().isEmpty()
                        && "https://mvod.itunes.apple.com/s.m3u8".equals(squareOnly.squareM3u8Url()),
                "Square-only editorial video extraction failed");

        check(
                AppleHlsResolver.extractEditorialVideoFromHtml("<html>no script</html>").isEmpty(),
                "Missing serialized-server-data should yield empty editorial video");

        String searchHtml =
                "<html><body><script id=\"serialized-server-data\" type=\"application/json\">"
                        + "{\"data\":{\"sections\":[{\"items\":["
                        + "{\"subtitle\":\"Album\u202f\u00b7\u202fTkay Maidza\",\"itemKind\":\"albums\","
                        + "\"title\":\"High Beams (JPEGMAFIA Remix) - Single\","
                        + "\"contentDescriptor\":{\"kind\":\"album\",\"url\":\"https://music.apple.com/fr/album/high-beams-jpegmafia-remix-single/1646008904\"}},"
                        + "{\"subtitle\":\"Morceau\u202f\u00b7\u202fTkay Maidza\",\"itemKind\":\"songs\","
                        + "\"title\":\"High Beams\","
                        + "\"contentDescriptor\":{\"kind\":\"song\",\"url\":\"https://music.apple.com/fr/album/high-beams/1569896693?i=1569897308\"},"
                        + "\"segue\":{\"destination\":{\"contentDescriptor\":{\"kind\":\"album\",\"url\":\"https://music.apple.com/fr/album/high-beams/1569896693\"}}}}"
                        + "]}]}}</script></body></html>";
        List<AppleHlsResolver.SearchLockup> lockups =
                AppleHlsResolver.extractSearchCandidatesFromHtml(searchHtml);
        check(lockups.size() == 2, "Expected 2 search lockups from SSR HTML, got " + lockups.size());
        AppleHlsResolver.SearchLockup epTrack = lockups.get(1);
        check(
                "High Beams".equals(epTrack.title())
                        && "1569896693".equals(epTrack.collectionId())
                        && "https://music.apple.com/fr/album/high-beams/1569896693"
                                .equals(epTrack.albumUrl())
                        && "Tkay Maidza"
                                .equals(AppleHlsResolver.artistFromSearchSubtitle(epTrack.subtitle())),
                "Failed to extract exact song/album metadata from Apple Music search SSR");
    }

    private static void verifyMasterPlaylistAndVariantSelection() {
        String baseUrl = "https://mvod.itunes.apple.com/itunes-assets/v4/master.m3u8";
        String master =
                "#EXTM3U\n"
                        + "#EXT-X-VERSION:7\n"
                        + "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=500000,RESOLUTION=664x886,URI=\"iframes.m3u8\"\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=900000,BANDWIDTH=1200000,VIDEO-RANGE=SDR,CODECS=\"avc1.64001f\",RESOLUTION=486x648\n"
                        + "gr230_486x648.m3u8\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=1800000,BANDWIDTH=2400000,VIDEO-RANGE=SDR,CODECS=\"avc1.64001f\",RESOLUTION=664x886\n"
                        + "gr240_664x886.m3u8\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2900000,BANDWIDTH=3500000,VIDEO-RANGE=SDR,CODECS=\"avc1.640028\",RESOLUTION=1080x1440\n"
                        + "gr285_1080x1440_avc.m3u8\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2100000,BANDWIDTH=2800000,VIDEO-RANGE=SDR,CODECS=\"hvc1.2.20000000.L123.B0\",RESOLUTION=1080x1440\n"
                        + "gr685_1080x1440_hevc.m3u8\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=14000000,BANDWIDTH=18000000,VIDEO-RANGE=HLG,CODECS=\"hvc1.2.4.L150.B0\",RESOLUTION=1080x1440\n"
                        + "gr_hdr_1080x1440.m3u8\n"
                        + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=15000000,BANDWIDTH=19000000,VIDEO-RANGE=SDR,CODECS=\"avc1.640033\",RESOLUTION=2160x2880\n"
                        + "gr_4k_2160x2880.m3u8\n";

        List<AppleHlsResolver.VideoVariant> variants =
                AppleHlsResolver.parseMasterPlaylist(master, baseUrl);
        check(variants.size() == 6, "Expected 6 variants (excluding I-frame), got " + variants.size());

        AppleHlsResolver.VideoVariant avc1080 =
                AppleHlsResolver.selectBestVariant(variants, 1080, false);
        check(
                avc1080 != null
                        && avc1080.uri().endsWith("gr285_1080x1440_avc.m3u8")
                        && avc1080.sdr(),
                "Expected SDR 1080x1440 AVC variant when HEVC disabled");

        AppleHlsResolver.VideoVariant hevc1080 =
                AppleHlsResolver.selectBestVariant(variants, 1080, true);
        check(
                hevc1080 != null
                        && hevc1080.uri().endsWith("gr685_1080x1440_hevc.m3u8")
                        && hevc1080.sdr(),
                "Expected SDR 1080x1440 HEVC variant when HEVC supported");

        AppleHlsResolver.VideoVariant capped720 =
                AppleHlsResolver.selectBestVariant(variants, 720, false);
        check(
                capped720 != null && capped720.width() == 664 && capped720.height() == 886,
                "Expected 664x886 under 720px width cap");
    }

    private static void verifySingleMp4ResolutionFromVariant() {
        String variantUrl = "https://mvod.itunes.apple.com/hls/v4/aa/bb/variant.m3u8";
        String byterangeM3u8 =
                "#EXTM3U\n"
                        + "#EXT-X-VERSION:7\n"
                        + "#EXT-X-MAP:URI=\"P359_sdr_664x886-.mp4\",BYTERANGE=\"896@0\"\n"
                        + "#EXTINF:4.5,\n"
                        + "#EXT-X-BYTERANGE:1896560@896\n"
                        + "P359_sdr_664x886-.mp4\n"
                        + "#EXTINF:3.8,\n"
                        + "#EXT-X-BYTERANGE:1462583@1897456\n"
                        + "P359_sdr_664x886-.mp4\n"
                        + "#EXT-X-ENDLIST\n";
        String resolved = AppleHlsResolver.resolveSingleMp4FromVariantPlaylist(byterangeM3u8, variantUrl);
        check(
                "https://mvod.itunes.apple.com/hls/v4/aa/bb/P359_sdr_664x886-.mp4".equals(resolved),
                "Failed to resolve single contiguous fMP4 URI: " + resolved);

        String multiFileM3u8 =
                "#EXTM3U\n"
                        + "#EXT-X-MAP:URI=\"init.mp4\"\n"
                        + "#EXTINF:4.0,\n"
                        + "seg1.m4s\n"
                        + "#EXTINF:4.0,\n"
                        + "seg2.m4s\n";
        check(
                AppleHlsResolver.resolveSingleMp4FromVariantPlaylist(multiFileM3u8, variantUrl) == null,
                "Multi-segment playlist should not collapse to single MP4");

        String encryptedM3u8 =
                "#EXTM3U\n"
                        + "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n"
                        + "#EXT-X-MAP:URI=\"enc-.mp4\",BYTERANGE=\"512@0\"\n"
                        + "#EXT-X-BYTERANGE:1024@512\n"
                        + "enc-.mp4\n";
        check(
                AppleHlsResolver.resolveSingleMp4FromVariantPlaylist(encryptedM3u8, variantUrl) == null,
                "Encrypted variant must be rejected");
    }

    private static void verifyCenterCropTransformMath() {
        float[] tallInPhone = AppleHlsResolver.computeCenterCropScale(664, 886, 1080, 2400);
        check(
                Math.abs(tallInPhone[1] - 1.0f) < 1e-4f && tallInPhone[0] > 1.6f,
                "Tall 3:4 video in 9:20 viewport should fill height cleanly and crop horizontally");
        float renderedAspect = (1080f * tallInPhone[0]) / (2400f * tallInPhone[1]);
        check(
                Math.abs(renderedAspect - (664f / 886f)) < 1e-4f,
                "Center-crop scale distorted tall video aspect ratio");
        check(
                Math.abs(tallInPhone[2] - 540f) < 1e-4f && Math.abs(tallInPhone[3] - 1200f) < 1e-4f,
                "Pivot must be centered in viewport");

        float[] wideInSquare = AppleHlsResolver.computeCenterCropScale(1920, 1080, 1000, 1000);
        float wideAspect = (1000f * wideInSquare[0]) / (1000f * wideInSquare[1]);
        check(
                Math.abs(wideAspect - (1920f / 1080f)) < 1e-4f
                        && Math.abs(wideInSquare[1] - 1.0f) < 1e-4f,
                "Wide video in square viewport should fill height cleanly without stretching");
    }

    private static void verifyDiskCacheLruAndNegativeTtl() throws IOException {
        Path tempDir = Files.createTempDirectory("dressroom-live-cover-cache-test");
        try {
            AtomicLong fakeNow = new AtomicLong(10_000L);
            // AnimatedCoverCache clamps minimum quota to 1 MiB (1024 * 1024 bytes).
            AnimatedCoverCache cache =
                    new AnimatedCoverCache(tempDir.toFile(), 1024L * 1024L, 5_000L, fakeNow::get);

            byte[] halfMeg = new byte[512 * 1024];
            File tmp1 = cache.createTempFile();
            Files.write(tmp1.toPath(), halfMeg);
            AnimatedCoverProvider.AnimatedCoverAsset a1 =
                    cache.commitAsset("apple_music", "apple:1", tmp1, "https://x/1.mp4", 664, 886, true);
            check(a1 != null && a1.isPlayableLocalFile(), "First asset commit failed");
            cache.putAlias("track|artist|one|", "apple:1");

            fakeNow.set(20_000L);
            File tmp2 = cache.createTempFile();
            Files.write(tmp2.toPath(), halfMeg);
            AnimatedCoverProvider.AnimatedCoverAsset a2 =
                    cache.commitAsset("apple_music", "apple:2", tmp2, "https://x/2.mp4", 664, 886, true);
            check(a2 != null && cache.totalCachedVideoBytes() == 1024L * 1024L, "Two 512KB files should fit 1MB budget");

            // Touch apple:1 so apple:2 becomes LRU before inserting apple:3.
            fakeNow.set(30_000L);
            check(cache.getByAlias("track|artist|one|") != null, "Alias hit for apple:1 expected");

            fakeNow.set(40_000L);
            File tmp3 = cache.createTempFile();
            Files.write(tmp3.toPath(), halfMeg);
            AnimatedCoverProvider.AnimatedCoverAsset a3 =
                    cache.commitAsset("apple_music", "apple:3", tmp3, "https://x/3.mp4", 664, 886, true);
            check(a3 != null, "Third asset commit failed");
            check(cache.totalCachedVideoBytes() <= 1024L * 1024L, "Cache exceeded 1MB LRU budget");
            check(cache.getByAssetKey("apple:1") != null, "Recently touched apple:1 was wrongly evicted");
            check(cache.getByAssetKey("apple:2") == null, "Oldest untouched apple:2 should have been evicted");

            cache.markNegative("track|missing|song|");
            check(cache.isNegative("track|missing|song|"), "Negative lookup entry should be active");
            fakeNow.addAndGet(4_999L);
            check(cache.isNegative("track|missing|song|"), "Negative lookup expired prematurely");
            fakeNow.addAndGet(1L);
            check(!cache.isNegative("track|missing|song|"), "Negative lookup did not expire at TTL");
        } finally {
            try (var walk = Files.walk(tempDir)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyTrackNormalizationAndPreferences() {
        AnimatedCoverProvider.TrackQuery q1 =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd feat. Daft Punk",
                        "Blinding Lights (Remix)",
                        "After Hours [Deluxe]",
                        "com.spotify.music",
                        "",
                        1080);
        AnimatedCoverProvider.TrackQuery q2 =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd",
                        "Alone Again",
                        "After Hours",
                        "com.google.android.apps.youtube.music",
                        "",
                        1080);
        check(q1.isValid() && q2.isValid(), "Expected valid track queries");
        check(
                q1.normalizedAlbumKey().equals(q2.normalizedAlbumKey()),
                "Tracks from the same album should share normalizedAlbumKey");
        check(
                !q1.normalizedTrackKey().equals(q2.normalizedTrackKey()),
                "Distinct tracks should not collide on normalizedTrackKey");
        var original = new AnimatedCoverProvider.TrackQuery(
                q1.artist(), "Blinding Lights", q1.album(), q1.packageName(), "", 1080);
        check(q1.normalizedTrackKey().equals(original.normalizedTrackKey())
                        && !q1.playbackKey().equals(original.playbackKey()),
                "A track change to a remix must reach the cover renderer");

        check(
                AnimatedCoverProvider.MediaTextNormalize.matchesTrack(
                        "Tkay Maidza",
                        "High Beams",
                        "Last Year Was Weird, Vol. 3",
                        "Tkay Maidza",
                        "High Beams (Explicit)",
                        "Last Year Was Weird, Vol. 3 - EP"),
                "Track matching should tolerate release suffixes and parentheticals");

        check(
                Arrays.asList(ExtrasPreferences.FLAGS).contains(LiveCoverController.KEY),
                "ExtrasPreferences.FLAGS missing LiveCoverController.KEY");
        check(
                Arrays.asList(ExtrasPreferences.DEFAULT_TRUE_FLAGS)
                        .contains(LiveCoverController.WIFI_ONLY),
                "ExtrasPreferences.DEFAULT_TRUE_FLAGS missing LiveCoverController.WIFI_ONLY");
    }

    private static void verifyBlurCaptureAndClosingPolicies() {
        check(
                LiveCoverController.BLUR_FRAME_INTERVAL_MS >= 100L,
                "Expected throttled live blur interval >= 100ms, got "
                        + LiveCoverController.BLUR_FRAME_INTERVAL_MS);
        check(
                LiveCoverController.BLUR_FRAME_MAX_SIDE <= 256,
                "Expected compact live blur readback dimension <= 256px, got "
                        + LiveCoverController.BLUR_FRAME_MAX_SIDE);
        check(
                LiveCoverController.shouldSampleBlurFrame(1000L, 0L, false, false),
                "Initial blur sample with lastSampleMs=0 should be allowed");
        check(
                !LiveCoverController.shouldSampleBlurFrame(
                        1000L + LiveCoverController.BLUR_FRAME_INTERVAL_MS - 1L,
                        1000L,
                        false,
                        false),
                "Blur sample before interval elapsed must be throttled");
        check(
                LiveCoverController.shouldSampleBlurFrame(
                        1000L + LiveCoverController.BLUR_FRAME_INTERVAL_MS,
                        1000L,
                        false,
                        false),
                "Blur sample at or after interval should be allowed");
        check(
                !LiveCoverController.shouldSampleBlurFrame(5000L, 1000L, true, false),
                "Blur readback must be suppressed while closing live player");
        check(
                !LiveCoverController.shouldSampleBlurFrame(5000L, 1000L, false, true),
                "Duplicate pending blur sample must be skipped");
        check(
                LiveCoverController.selectClosingFrame(null, null) == null,
                "Null video and frozen frames should return null closing frame");
    }

    private static void verifySpotifyCanvasUriAndTrackId() {
        check(
                "4uLU6hMCjMI75M1A2tKUQC"
                        .equals(
                                SpotifyCanvasResolver.extractSpotifyTrackId(
                                        "spotify:track:4uLU6hMCjMI75M1A2tKUQC")),
                "Should extract track ID from spotify:track URI");
        check(
                "4uLU6hMCjMI75M1A2tKUQC"
                        .equals(
                                SpotifyCanvasResolver.extractSpotifyTrackId(
                                        "spotify://track/4uLU6hMCjMI75M1A2tKUQC")),
                "Should extract track ID from spotify://track URI");
        check(
                "4uLU6hMCjMI75M1A2tKUQC"
                        .equals(
                                SpotifyCanvasResolver.extractSpotifyTrackId(
                                        "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=xyz")),
                "Should extract track ID from open.spotify.com URL");
        check(
                "4uLU6hMCjMI75M1A2tKUQC"
                        .equals(
                                SpotifyCanvasResolver.extractSpotifyTrackId(
                                        "https://open.spotify.com/intl-fr/track/4uLU6hMCjMI75M1A2tKUQC")),
                "Should extract track ID from localized open.spotify.com URL");
        check(
                SpotifyCanvasResolver.extractSpotifyTrackId("4uLU6hMCjMI75M1A2tKUQC").isEmpty()
                        && SpotifyCanvasResolver.extractSpotifyTrackId("spotify:track:short")
                                .isEmpty()
                        && SpotifyCanvasResolver.extractSpotifyTrackId(
                                        "spotify:album:4uLU6hMCjMI75M1A2tKUQC")
                                .isEmpty(),
                "Non-track or bare IDs must not match as Spotify track IDs");

        check(
                "https://canvaz.scdn.co/upload/artist/abc/video/def.cnvs.mp4"
                        .equals(
                                SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "content://com.spotify.music.provider/canvas/upload/artist/abc/video/def.cnvs.mp4?cdn=canvaz.scdn.co")),
                "Should unwrap Spotify MediaSession content:// canvas URI");
        check(
                "https://canvaz.scdn.co/upload/artist/abc/video/def.cnvs.mp4"
                        .equals(
                                SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "content://com.spotify.music.provider/canvas/upload/artist/abc/video/def.cnvs.mp4")),
                "Missing cdn query param should default to canvaz.scdn.co");
        check(
                "https://canvaz.scdn.co/upload/artist/abc/video/def.cnvs.mp4"
                        .equals(
                                SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "https://canvaz.scdn.co/upload/artist/abc/video/def.cnvs.mp4")),
                "Direct https .cnvs.mp4 URI should pass through");
        check(
                SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "content://com.spotify.music.provider/canvas/upload/a.jpg?cdn=canvaz.scdn.co")
                                .isEmpty()
                        && SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "https://canvaz.scdn.co/upload/a.png")
                                .isEmpty(),
                "Still-image canvas URLs must be rejected");
        check(
                SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "content://com.spotify.music.provider/canvas/../secret.cnvs.mp4?cdn=canvaz.scdn.co")
                                .isEmpty()
                        && SpotifyCanvasResolver.unwrapCanvasMediaUri(
                                        "content://com.spotify.music.provider/canvas/a/b.cnvs.mp4?cdn=evil.example.com")
                                .isEmpty(),
                "Traversal and untrusted CDN hosts must be rejected");
    }

    private static void verifySpotifyTotpAndAuthParsing() {
        check(
                "934995"
                        .equals(
                                SpotifyCanvasResolver.computeTotp(
                                        SpotifyCanvasResolver.FALLBACK_TOTP_SECRET, 1760000000L)),
                "TOTP calculation did not match golden vector");

        SpotifyCanvasResolver.TotpSecret secret =
                SpotifyCanvasResolver.parseLatestSecret(
                        "{\"59\":[1,2,3],\"bad\":\"ignored\",\"0\":[9],\"61\":[44,55,47]}");
        check(
                secret != null
                        && secret.version() == 61
                        && Arrays.equals(secret.secretBytes(), new int[] {44, 55, 47}),
                "parseLatestSecret should choose highest positive numeric version");
        check(
                SpotifyCanvasResolver.parseLatestSecret("{}") == null,
                "Empty secret dictionary should return null");

        check(
                SpotifyCanvasResolver.parseServerTimeSeconds("{\"serverTime\":1760000123}")
                        == 1760000123L,
                "Failed to parse Spotify serverTime");
        check(
                SpotifyCanvasResolver.parseServerTimeSeconds("{\"bad\":1}") == -1L,
                "Missing serverTime should return -1");

        SpotifyCanvasResolver.AnonymousToken token =
                SpotifyCanvasResolver.parseAnonymousToken(
                        "{\"accessToken\":\"tok123\",\"accessTokenExpirationTimestampMs\":2000000,\"isAnonymous\":true}",
                        1000000L);
        check(
                token != null
                        && "tok123".equals(token.accessToken())
                        && token.expiresAtMs() == 2000000L,
                "Failed to parse anonymous Spotify token");
        check(
                SpotifyCanvasResolver.parseAnonymousToken("{\"isAnonymous\":true}", 1000000L)
                        == null,
                "Missing accessToken should return null");
    }

    private static void verifySpotifySearchAndCanvasParsing() {
        String body =
                SpotifyCanvasResolver.buildPathfinderSearchRequestBody(
                        "The Weeknd \"Blinding\"\\Lights", 5);
        check(
                body.contains("\\\"Blinding\\\"\\\\Lights")
                        && body.contains(SpotifyCanvasResolver.SEARCH_DESKTOP_HASH),
                "Pathfinder search request body missing escaped term or persisted query hash");

        String pathfinderJson =
                "{\"data\":{\"searchV2\":{\"tracksV2\":{\"items\":["
                        + "{\"item\":{\"data\":{\"id\":\"1111111111111111111111\","
                        + "\"name\":\"Blinding Lights (Sped Up)\","
                        + "\"albumOfTrack\":{\"name\":\"Blinding Lights - Single\"},"
                        + "\"artists\":{\"items\":[{\"profile\":{\"name\":\"The Weeknd\"}}]}}}},"
                        + "{\"item\":{\"data\":{\"id\":\"2222222222222222222222\","
                        + "\"name\":\"Alone Again\","
                        + "\"albumOfTrack\":{\"name\":\"After Hours\"},"
                        + "\"artists\":{\"items\":[{\"profile\":{\"name\":\"The Weeknd\"}}]}}}},"
                        + "{\"item\":{\"data\":{\"id\":\"3333333333333333333333\","
                        + "\"name\":\"Blinding Lights\","
                        + "\"albumOfTrack\":{\"name\":\"After Hours\"},"
                        + "\"artists\":{\"items\":[{\"profile\":{\"name\":\"The Weeknd\"}}]}}}},"
                        + "{\"item\":{\"data\":{\"uri\":\"spotify:track:3333333333333333333333\","
                        + "\"name\":\"Blinding Lights\","
                        + "\"albumOfTrack\":{\"name\":\"After Hours (Deluxe)\"},"
                        + "\"artists\":{\"items\":[{\"profile\":{\"name\":\"The Weeknd\"}}]}}}},"
                        + "{\"item\":{\"data\":{\"id\":\"4444444444444444444444\","
                        + "\"name\":\"Blinding Lights - Chromatics Remix\","
                        + "\"albumOfTrack\":{\"name\":\"After Hours (Remix)\"},"
                        + "\"artists\":{\"items\":[{\"profile\":{\"name\":\"The Weeknd\"}}]}}}}"
                        + "]}}}}";
        var query =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd", "Blinding Lights", "After Hours", "pkg", "", 1080);
        List<SpotifyCanvasResolver.TrackCandidate> candidates =
                SpotifyCanvasResolver.parseSearchTrackCandidates(pathfinderJson, query);
        check(
                candidates.size() == 3
                        && "3333333333333333333333".equals(candidates.get(0).trackId()),
                "Expected exact original track ranked first and duplicate/wrong song excluded");
        for (SpotifyCanvasResolver.TrackCandidate candidate : candidates) {
            check(
                    !"2222222222222222222222".equals(candidate.trackId()),
                    "Same-album different song must be rejected in per-track Canvas search");
        }

        String canvasJson =
                "{\"canvasesList\":["
                        + "{\"id\":\"img1\",\"canvasUrl\":\"https://canvaz.scdn.co/upload/still.jpg\",\"trackUri\":\"spotify:track:3333333333333333333333\"},"
                        + "{\"id\":\"cnv99\",\"canvasUrl\":\"https://canvaz.scdn.co/upload/artist/x/video/loop.cnvs.mp4\",\"trackUri\":\"spotify:track:3333333333333333333333\"}"
                        + "]}";
        SpotifyCanvasResolver.CanvasVideo parsedCanvas =
                SpotifyCanvasResolver.parseCanvasResponse(canvasJson);
        check(
                parsedCanvas != null
                        && "cnv99".equals(parsedCanvas.canvasId())
                        && "https://canvaz.scdn.co/upload/artist/x/video/loop.cnvs.mp4"
                                .equals(parsedCanvas.videoUrl()),
                "parseCanvasResponse should skip image URLs and extract .cnvs.mp4");
        check(
                SpotifyCanvasResolver.parseCanvasResponse("{\"canvasesList\":[]}") == null,
                "Empty canvasesList should return null");

        var remixQuery =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd", "Blinding Lights (Remix)", "After Hours", "pkg", "", 1080);
        check(
                !SpotifyCanvasResolver.trackCacheKey(query)
                        .equals(SpotifyCanvasResolver.trackCacheKey(remixQuery)),
                "Spotify trackCacheKey should distinguish variant versions such as Remix");
    }

    private static void verifyMultiProviderPolicyAndCacheIsolation() throws IOException {
        check(
                LiveCoverController.clampSource(-1) == LiveCoverController.SOURCE_SPOTIFY_FIRST
                        && LiveCoverController.clampSource(4)
                                == LiveCoverController.SOURCE_SPOTIFY_FIRST
                        && LiveCoverController.clampSource(LiveCoverController.SOURCE_APPLE_ONLY)
                                == LiveCoverController.SOURCE_APPLE_ONLY,
                "clampSource bounds check failed");

        check(
                List.of("spotify_canvas", "apple_music")
                        .equals(
                                LiveCoverController.providersForSource(
                                                LiveCoverController.SOURCE_SPOTIFY_FIRST)
                                        .stream()
                                        .map(AnimatedCoverProvider::id)
                                        .toList()),
                "SOURCE_SPOTIFY_FIRST provider order mismatch");
        check(
                List.of("apple_music", "spotify_canvas")
                        .equals(
                                LiveCoverController.providersForSource(
                                                LiveCoverController.SOURCE_APPLE_FIRST)
                                        .stream()
                                        .map(AnimatedCoverProvider::id)
                                        .toList()),
                "SOURCE_APPLE_FIRST provider order mismatch");
        check(
                List.of("spotify_canvas")
                        .equals(
                                LiveCoverController.providersForSource(
                                                LiveCoverController.SOURCE_SPOTIFY_ONLY)
                                        .stream()
                                        .map(AnimatedCoverProvider::id)
                                        .toList()),
                "SOURCE_SPOTIFY_ONLY provider order mismatch");
        check(
                List.of("apple_music")
                        .equals(
                                LiveCoverController.providersForSource(
                                                LiveCoverController.SOURCE_APPLE_ONLY)
                                        .stream()
                                        .map(AnimatedCoverProvider::id)
                                        .toList()),
                "SOURCE_APPLE_ONLY provider order mismatch");

        String albumKey = "album|the weeknd|after hours";
        check(
                !LiveCoverController.canReuseAlbumAsset(
                        LiveCoverController.SOURCE_SPOTIFY_FIRST, "apple_music", albumKey, albumKey),
                "SOURCE_SPOTIFY_FIRST must never reuse previous track cover on album switch");
        check(
                !LiveCoverController.canReuseAlbumAsset(
                        LiveCoverController.SOURCE_SPOTIFY_ONLY,
                        "spotify_canvas",
                        albumKey,
                        albumKey),
                "SOURCE_SPOTIFY_ONLY must never reuse previous track cover");
        check(
                LiveCoverController.canReuseAlbumAsset(
                                LiveCoverController.SOURCE_APPLE_FIRST,
                                "apple_music",
                                albumKey,
                                albumKey)
                        && !LiveCoverController.canReuseAlbumAsset(
                                LiveCoverController.SOURCE_APPLE_FIRST,
                                "spotify_canvas",
                                albumKey,
                                albumKey),
                "SOURCE_APPLE_FIRST should reuse album cover only when active asset is Apple Music");
        check(
                LiveCoverController.canReuseAlbumAsset(
                        LiveCoverController.SOURCE_APPLE_ONLY, "apple_music", albumKey, albumKey),
                "SOURCE_APPLE_ONLY should reuse album cover across same-album tracks");

        var t1 =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd", "Blinding Lights", "After Hours", "pkg", "", 1080);
        var t2 =
                new AnimatedCoverProvider.TrackQuery(
                        "The Weeknd", "Alone Again", "After Hours", "pkg", "", 1080);
        check(
                !LiveCoverController.requestFlightKey(t1, LiveCoverController.SOURCE_SPOTIFY_FIRST, true)
                        .equals(
                                LiveCoverController.requestFlightKey(
                                        t2, LiveCoverController.SOURCE_SPOTIFY_FIRST, true)),
                "Same-album tracks must have distinct flight keys when Spotify Canvas is enabled");
        check(
                !LiveCoverController.requestFlightKey(t1, LiveCoverController.SOURCE_APPLE_FIRST, true)
                        .equals(
                                LiveCoverController.requestFlightKey(
                                        t2, LiveCoverController.SOURCE_APPLE_FIRST, true)),
                "SOURCE_APPLE_FIRST must keep distinct flight keys for Spotify fallback");
        check(
                !LiveCoverController.requestFlightKey(t1, LiveCoverController.SOURCE_SPOTIFY_ONLY, true)
                        .equals(
                                LiveCoverController.requestFlightKey(
                                        t2, LiveCoverController.SOURCE_SPOTIFY_ONLY, true)),
                "SOURCE_SPOTIFY_ONLY must keep distinct flight keys");
        check(
                LiveCoverController.requestFlightKey(t1, LiveCoverController.SOURCE_APPLE_ONLY, true)
                        .equals(
                                LiveCoverController.requestFlightKey(
                                        t2, LiveCoverController.SOURCE_APPLE_ONLY, true)),
                "SOURCE_APPLE_ONLY should deduplicate in-flight requests per album");

        Path tempDir = Files.createTempDirectory("dressroom-provider-isolation-test");
        try {
            AnimatedCoverCache cache = new AnimatedCoverCache(tempDir.toFile());
            byte[] dummy = new byte[4096];
            File tmpApple = cache.createTempFile();
            Files.write(tmpApple.toPath(), dummy);
            cache.commitAsset(
                    AppleMusicAnimatedCoverProvider.PROVIDER_ID,
                    "apple_music:1",
                    tmpApple,
                    "https://x/a.mp4",
                    664,
                    886,
                    true);
            String appleTrackAlias = AppleMusicAnimatedCoverProvider.PROVIDER_ID + "|" + t1.normalizedTrackKey();
            String spotifyTrackAlias = SpotifyCanvasResolver.trackCacheKey(t1);
            cache.putAlias(appleTrackAlias, "apple_music:1");
            cache.markNegative(spotifyTrackAlias);
            check(
                    cache.getByAlias(appleTrackAlias) != null
                            && cache.getByAlias(spotifyTrackAlias) == null
                            && !cache.isNegative(appleTrackAlias)
                            && cache.isNegative(spotifyTrackAlias),
                    "Provider-namespaced cache aliases and negative entries must stay isolated");
        } finally {
            try (var walk = Files.walk(tempDir)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        verifyLatestCoverRequests();
        verifyHtmlEditorialVideoExtraction();
        verifyMasterPlaylistAndVariantSelection();
        verifySingleMp4ResolutionFromVariant();
        verifyCenterCropTransformMath();
        verifyDiskCacheLruAndNegativeTtl();
        verifyTrackNormalizationAndPreferences();
        verifySpotifyCanvasUriAndTrackId();
        verifySpotifyTotpAndAuthParsing();
        verifySpotifySearchAndCanvasParsing();
        verifyMultiProviderPolicyAndCacheIsolation();
        verifyBlurCaptureAndClosingPolicies();
        System.out.println("Live album cover contracts: " + checks + " checks passed");
    }

    private static void verifyLatestCoverRequests() {
        var workers = new java.util.ArrayDeque<Runnable>();
        var requests = new LatestCoverRequests<String>(workers::add, 2);
        var first = requests.request("first", () -> "one");
        var second = requests.request("second", () -> "two");
        check(requests.request("first", () -> { throw new AssertionError("Duplicate work"); }) == first,
                "The same running cover must share its future");
        var skipped = requests.request("skipped", () -> { throw new AssertionError("Stale queued cover ran"); });
        var latest = requests.request("latest", () -> "last");
        check(skipped.isDone() && skipped.join() == null,
                "Superseded queued cover must release its callers");
        check(workers.size() == 2 && !latest.isDone(), "Only two downloads may run together");
        workers.remove().run();
        check("one".equals(first.join()) && workers.size() == 2,
                "Finishing a download must schedule the newest waiting cover");
        workers.remove().run();
        workers.remove().run();
        check("two".equals(second.join()) && "last".equals(latest.join()),
                "Current covers must finish normally");
        var failed = requests.request("broken", () -> { throw new IllegalStateException("test failure"); });
        workers.remove().run();
        check(failed.isCompletedExceptionally(), "Failed downloads must complete their future");
        var retry = requests.request("broken", () -> "retry");
        workers.remove().run();
        check("retry".equals(retry.join()), "A failed request must not poison deduplication");
    }
}
