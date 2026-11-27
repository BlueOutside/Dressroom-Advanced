package dev.denver.dressroomadvanced;

/**
 * Pure-Java configuration model for hidden/experimental Samsung One UI Now Bar Labs features:
 * native scrapped cards (Battery, Contact Info), hidden App Search live cards (Google Finance,
 * Google Commute), global native GPU shader overrides, and Studio long-press integration.
 */
final class DiyNowBarLabs {
    static final String KEY_NATIVE_BATTERY = "dressroom_native_battery";
    static final String KEY_NATIVE_CONTACT = "dressroom_native_contact";

    static final int[] GLOBAL_SHADER_VALUES = {
        DiyNowBarCard.EFFECT_NONE,
        DiyNowBarCard.EFFECT_AI_PRESENCE,
        DiyNowBarCard.EFFECT_AI_MESH_WARP,
        DiyNowBarCard.EFFECT_OUTER_GLOW,
        DiyNowBarCard.EFFECT_GRADIENT_FILL,
        DiyNowBarCard.EFFECT_GUIDING_LIGHT
    };

    static final String[] GLOBAL_SHADER_LABELS = {
        "Off (Samsung default)",
        "AI Presence (Snake Sweep)",
        "AI Border Travel",
        "Outer Glow Ring",
        "Gradient Fill",
        "Guiding Light"
    };

    boolean nativeBatteryCard;
    boolean nativeBatteryAlwaysShow;
    boolean nativeContactCard;
    boolean unlockGoogleFinance;
    boolean unlockGoogleCommute;
    boolean unlockGoogleSports;
    boolean unlockNaverSports;
    boolean geminiCommuteRecap;
    boolean budsInMusicCard;
    int globalCardShader = DiyNowBarCard.EFFECT_NONE;
    int globalColor1 = 0xFF4E8CFF;
    int globalColor2 = 0xFFA86BFF;
    boolean longPressOpensStudio = true;
    long revision;

    DiyNowBarLabs copy() {
        DiyNowBarLabs c = new DiyNowBarLabs();
        c.nativeBatteryCard = nativeBatteryCard;
        c.nativeBatteryAlwaysShow = nativeBatteryAlwaysShow;
        c.nativeContactCard = nativeContactCard;
        c.unlockGoogleFinance = unlockGoogleFinance;
        c.unlockGoogleCommute = unlockGoogleCommute;
        c.unlockGoogleSports = unlockGoogleSports;
        c.unlockNaverSports = unlockNaverSports;
        c.geminiCommuteRecap = geminiCommuteRecap;
        c.budsInMusicCard = budsInMusicCard;
        c.globalCardShader = globalCardShader;
        c.globalColor1 = globalColor1;
        c.globalColor2 = globalColor2;
        c.longPressOpensStudio = longPressOpensStudio;
        c.revision = revision;
        return c;
    }

    DiyNowBarLabs sanitize() {
        if (!nativeBatteryCard) {
            // Preserve user's sub-toggle preference cleanly; no clamping needed
        }
        if (!isValidGlobalShader(globalCardShader)) {
            globalCardShader = DiyNowBarCard.EFFECT_NONE;
        }
        globalColor1 = opaque(globalColor1, 0xFF4E8CFF);
        globalColor2 = opaque(globalColor2, 0xFFA86BFF);
        if (revision < 0L) revision = 0L;
        return this;
    }

    boolean sameContent(DiyNowBarLabs other) {
        if (other == null) return false;
        return nativeBatteryCard == other.nativeBatteryCard
                && nativeBatteryAlwaysShow == other.nativeBatteryAlwaysShow
                && nativeContactCard == other.nativeContactCard
                && unlockGoogleFinance == other.unlockGoogleFinance
                && unlockGoogleCommute == other.unlockGoogleCommute
                && unlockGoogleSports == other.unlockGoogleSports
                && unlockNaverSports == other.unlockNaverSports
                && geminiCommuteRecap == other.geminiCommuteRecap
                && budsInMusicCard == other.budsInMusicCard
                && globalCardShader == other.globalCardShader
                && globalColor1 == other.globalColor1
                && globalColor2 == other.globalColor2
                && longPressOpensStudio == other.longPressOpensStudio;
    }

    static boolean isValidGlobalShader(int value) {
        for (int candidate : GLOBAL_SHADER_VALUES) {
            if (candidate == value) return true;
        }
        return false;
    }

    static int globalShaderIndex(int value) {
        for (int i = 0; i < GLOBAL_SHADER_VALUES.length; i++) {
            if (GLOBAL_SHADER_VALUES[i] == value) return i;
        }
        return 0;
    }

    static String globalShaderLabel(int value) {
        return GLOBAL_SHADER_LABELS[globalShaderIndex(value)];
    }

    static boolean isNativeLabKey(String key) {
        return KEY_NATIVE_BATTERY.equals(key) || KEY_NATIVE_CONTACT.equals(key);
    }

    private static int opaque(int color, int fallback) {
        if (color == 0) return fallback;
        return 0xFF000000 | (color & 0xFFFFFF);
    }
}
