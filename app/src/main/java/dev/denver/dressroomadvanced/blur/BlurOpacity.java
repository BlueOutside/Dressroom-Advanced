package dev.denver.dressroomadvanced;

final class BlurOpacity {
    static float resolve(float explicit, float nativeValue) {
        if (Float.isFinite(explicit) && explicit >= 0) return Math.min(1, explicit);
        return nativeValue;
    }
}
