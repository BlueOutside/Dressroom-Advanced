package dev.denver.dressroomadvanced;

import java.util.List;

public final class ReleaseVersionTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void order(String a, String b, int expected) {
        if (Integer.signum(ReleaseVersion.parse(a).compareTo(ReleaseVersion.parse(b))) != expected)
            throw new AssertionError(a + " compared with " + b);
        checks++;
    }

    private static UpdateRepository.ReleaseCandidate<String> cand(
            String tag, boolean prerelease) {
        return new UpdateRepository.ReleaseCandidate<>(tag, false, prerelease, tag);
    }

    private static void verifyPrereleaseSelection() {
        check(!ReleaseVersion.parse("2.0.0").isPrerelease(), "2.0.0 must be stable");
        check(!ReleaseVersion.parse("1.1.9+build.7").isPrerelease(), "build metadata is stable");
        check(ReleaseVersion.parse("2.0.0-dev.1").isPrerelease(), "dev.1 must be prerelease");
        check(ReleaseVersion.parse("2.0.0-dev.10").isPrerelease(), "dev.10 must be prerelease");

        order("2.0.0-dev.1", "2.0.0-dev.2", -1);
        order("2.0.0-dev.2", "2.0.0-dev.10", -1);
        order("2.0.0-dev.10", "2.0.0", -1);
        order("2.0.0", "2.0.1-dev.1", -1);
        order("2.0.1-dev.1", "2.0.1", -1);

        check(!UpdateRepository.prereleaseEnabled(false, false, "2.0.0"), "stable defaults off");
        check(
                UpdateRepository.prereleaseEnabled(false, false, "2.0.0-dev.1"),
                "dev build defaults on");
        check(
                !UpdateRepository.prereleaseEnabled(false, false, "invalid"),
                "unparseable version defaults off");
        check(
                UpdateRepository.prereleaseEnabled(true, true, "2.0.0"),
                "explicit opt-in on stable");
        check(
                !UpdateRepository.prereleaseEnabled(true, false, "2.0.0-dev.1"),
                "explicit opt-out on dev build");

        var s1 = UpdateRepository.select(List.of(cand("v2.0.1-dev.1", true)), "2.0.0", false);
        check(!s1.hasUpdate(), "1: stable with channel off ignores dev prerelease");

        var s2 = UpdateRepository.select(List.of(cand("v2.0.1-dev.1", true)), "2.0.0", true);
        check(
                !s2.hasChoice()
                        && s2.stable() == null
                        && s2.prerelease() != null
                        && "v2.0.1-dev.1".equals(s2.prerelease().tag()),
                "2: stable with channel on picks dev prerelease");

        var s3 =
                UpdateRepository.select(
                        List.of(cand("v2.0.0-dev.2", true), cand("v2.0.0-dev.10", true)),
                        "2.0.0-dev.1",
                        true);
        check(
                s3.stable() == null
                        && s3.prerelease() != null
                        && "v2.0.0-dev.10".equals(s3.prerelease().tag()),
                "3: dev picks highest numeric dev prerelease");

        var s4 = UpdateRepository.select(List.of(cand("v2.0.0", false)), "2.0.0-dev.5", true);
        check(
                s4.prerelease() == null
                        && s4.stable() != null
                        && "v2.0.0".equals(s4.stable().tag()),
                "4: dev upgrades to matching stable");

        var s5 =
                UpdateRepository.select(
                        List.of(cand("v2.0.0-dev.5", true), cand("v2.0.0", false)),
                        "2.0.0-dev.1",
                        true);
        check(
                !s5.hasChoice()
                        && s5.stable() != null
                        && "v2.0.0".equals(s5.stable().tag())
                        && s5.prerelease() == null,
                "5: newer stable supersedes older dev prerelease");

        var s6 =
                UpdateRepository.select(
                        List.of(
                                cand("v2.0.0", false),
                                cand("v2.0.1-dev.2", true),
                                cand("v2.0.1-dev.1", true)),
                        "2.0.0-dev.1",
                        true);
        check(
                s6.hasChoice()
                        && "v2.0.0".equals(s6.stable().tag())
                        && "v2.0.1-dev.2".equals(s6.prerelease().tag()),
                "6: offers both choice when newer dev sits above newer stable");

        var s7 =
                UpdateRepository.select(
                        List.of(cand("v2.0.0-dev.2", true), cand("v2.0.0", false)),
                        "2.0.0-dev.1",
                        false);
        check(
                s7.stable() != null
                        && "v2.0.0".equals(s7.stable().tag())
                        && s7.prerelease() == null,
                "7: dev with explicit opt-out ignores newer dev and picks stable");

        var s8 =
                UpdateRepository.select(
                        List.of(
                                new UpdateRepository.ReleaseCandidate<>(
                                        "v9.0.0-dev.1", true, true, "v9.0.0-dev.1"),
                                cand("v2.0.1-dev.1", true)),
                        "2.0.0",
                        true);
        check(
                s8.prerelease() != null && "v2.0.1-dev.1".equals(s8.prerelease().tag()),
                "8: drafts are ignored");

        var s9 =
                UpdateRepository.select(
                        List.of(
                                cand("latest", false),
                                cand("not-semver", true),
                                cand("v2.0.1", false)),
                        "2.0.0",
                        true);
        check(
                s9.stable() != null
                        && "v2.0.1".equals(s9.stable().tag())
                        && s9.prerelease() == null,
                "9: unparseable tags are skipped cleanly");

        var s10 =
                UpdateRepository.select(
                        List.of(
                                new UpdateRepository.ReleaseCandidate<>(
                                        "v2.0.2-dev.1", false, true, null),
                                cand("v2.0.1-dev.3", true)),
                        "2.0.0",
                        true);
        check(
                s10.prerelease() != null && "v2.0.1-dev.3".equals(s10.prerelease().tag()),
                "10: null APK payloads are skipped cleanly");
    }

    private static void verifyRootUpdaterContracts() {
        check(
                "'/data/user/0/dev.denver.dressroomadvanced/cache/updates/release.apk'"
                        .equals(
                                RootUpdater.shellQuote(
                                        "/data/user/0/dev.denver.dressroomadvanced/cache/updates/release.apk")),
                "plain path shellQuote");
        check(
                "'/tmp/a'\"'\"'b.apk'".equals(RootUpdater.shellQuote("/tmp/a'b.apk")),
                "embedded single quote shellQuote");
        for (String bad : new String[] {null, "a\nb", "a\0b", "a\rb"}) {
            try {
                RootUpdater.shellQuote(bad);
                throw new AssertionError("shellQuote accepted invalid input");
            } catch (IllegalArgumentException expected) {
                checks++;
            }
        }

        check(
                "cat '/tmp/release.apk' | cmd package install -r -S 12345 -"
                        .equals(RootUpdater.installCommand("/tmp/release.apk", 12345L)),
                "installCommand format");
        for (long badSize : new long[] {0L, -1L}) {
            try {
                RootUpdater.installCommand("/tmp/release.apk", badSize);
                throw new AssertionError("installCommand accepted invalid size");
            } catch (IllegalArgumentException expected) {
                checks++;
            }
        }
        try {
            RootUpdater.installCommand("", 100L);
            throw new AssertionError("installCommand accepted empty path");
        } catch (IllegalArgumentException expected) {
            checks++;
        }

        check(
                RootUpdater.isRootOutput(
                        new RootUpdater.CommandResult(0, "uid=0(root) gid=0(root)\n", false)),
                "uid=0 passes root check");
        check(
                !RootUpdater.isRootOutput(
                        new RootUpdater.CommandResult(1, "uid=0(root)\n", false)),
                "non-zero exit fails root check");
        check(
                !RootUpdater.isRootOutput(
                        new RootUpdater.CommandResult(0, "uid=0(root)\n", true)),
                "timedOut fails root check");
        check(
                !RootUpdater.isRootOutput(
                        new RootUpdater.CommandResult(0, "uid=1000(system)\n", false)),
                "non-root uid fails root check");

        check(
                RootUpdater.isInstallSuccess(new RootUpdater.CommandResult(0, "Success\n", false)),
                "Success passes install check");
        check(
                !RootUpdater.isInstallSuccess(
                        new RootUpdater.CommandResult(0, "Failure [INSTALL_FAILED]\n", false)),
                "Failure with exit 0 fails install check");
        check(
                !RootUpdater.isInstallSuccess(
                        new RootUpdater.CommandResult(1, "Success\n", false)),
                "non-zero exit fails install check");
        check(
                !RootUpdater.isInstallSuccess(
                        new RootUpdater.CommandResult(0, "Success\n", true)),
                "timedOut fails install check");

        String relaunch = RootUpdater.relaunchCommand();
        for (String proc : RootUpdater.HOOKED_PROCESSES) {
            check(relaunch.contains("'" + proc + "'"), "relaunchCommand missing " + proc);
        }
        check(
                relaunch.indexOf("com.samsung.android.app.dressroom")
                        < relaunch.indexOf("com.android.systemui"),
                "relaunchCommand kills dressroom before systemui");
        check(
                relaunch.contains("com.samsung.android.app.aodservice:provider"),
                "relaunchCommand includes aodservice:provider");
        check(relaunch.endsWith("; true"), "relaunchCommand ends with ; true");

        check(
                UpdateRepository.shouldRelaunchAfterInstall(2000L, 1000L),
                "within relaunch window");
        check(
                UpdateRepository.shouldRelaunchAfterInstall(2000L, 2000L),
                "exact relaunch boundary");
        check(
                !UpdateRepository.shouldRelaunchAfterInstall(2000L, 2001L),
                "expired relaunch window");
        check(!UpdateRepository.shouldRelaunchAfterInstall(0L, 1000L), "zero until fails");
        check(!UpdateRepository.shouldRelaunchAfterInstall(-1L, 1000L), "negative until fails");
        check(!UpdateRepository.shouldRelaunchAfterInstall(1000L, 0L), "zero now fails");
    }

    public static void main(String[] args) {
        order("1.1.10", "1.1.9", 1);
        order("v1.2.0", "1.1.99", 1);
        order("1.1.9", "v1.1.9", 0);
        order("1.1.9-alpha.2", "1.1.9-alpha.10", -1);
        order("1.1.9-alpha", "1.1.9", -1);
        order("1.1.9+build.7", "1.1.9", 0);
        order("1.1.8", "1.1.9", -1);
        order("1.1.9-alpha.1", "1.1.9-alpha.beta", -1);
        for (String bad :
                new String[] {"", "latest", "1.1", "1.1.9/evil", "v999999999999999999999.0.0"}) {
            try {
                ReleaseVersion.parse(bad);
                throw new AssertionError("Accepted " + bad);
            } catch (IllegalArgumentException expected) {
                checks++;
            }
        }
        verifyPrereleaseSelection();
        verifyRootUpdaterContracts();
        System.out.println("Release versions: " + checks + " checks passed");
    }
}
