package dev.denver.dressroomadvanced;

import android.view.Display;

final class BlurTintPolicy {
    private BlurTintPolicy() {}

    static boolean canAnimate(int displayState, boolean wallpaperAod) {
        return displayState == Display.STATE_ON && !wallpaperAod;
    }
}
