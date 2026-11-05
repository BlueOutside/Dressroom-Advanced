package dev.denver.dressroomadvanced;

final class BlurOpacity {
    static float resolve(float explicit,boolean forced,boolean custom,int tint,double brightness,float nativeValue) {
        if(Float.isFinite(explicit) && explicit>=0)return Math.min(1,explicit);
        // Respect intentionally translucent colors; opaque RGB tint should not hide glass.
        if(forced && custom && (tint>>>24)==255 && Double.isFinite(brightness))return brightness>76?.35f:.5f;
        return nativeValue;
    }
}
