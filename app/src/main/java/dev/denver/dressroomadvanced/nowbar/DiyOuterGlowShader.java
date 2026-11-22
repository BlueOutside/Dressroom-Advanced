package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Runs the installed provider's original glow shader with a persistent native blur pass. */
final class DiyOuterGlowShader {
    private static final String[] PACKAGES = {
            "com.samsung.android.app.aodservice", "com.samsung.android.app.clockpack"};
    private static JSONObject cachedDefinition;
    private static boolean definitionChecked;
    private final RuntimeShader shader;
    private final RenderEffect blur;
    private int lastWidth, lastHeight, lastPrimary, lastSecondary;
    private float lastRadius = -1f, lastInset = -1f;
    private boolean colorsSet, landscape;
    private final float density;

    private DiyOuterGlowShader(Context context, JSONObject definition) throws Exception {
        density = context.getResources().getDisplayMetrics().density;
        JSONArray layers = definition.getJSONArray("shaders");
        JSONObject source = null;
        int enabled = 0;
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            if (!layer.optBoolean("enabled", true)) continue;
            enabled++;
            if (enabled == 1 && !layer.optBoolean("isBlur")) source = layer;
            else if (enabled != 2 || !layer.optBoolean("isBlur"))
                throw new IllegalArgumentException("Unsupported Outer Glow pipeline");
        }
        if (source == null || enabled != 2) throw new IllegalArgumentException("Missing Outer Glow passes");
        String code = source.getString("agslShaderCode");
        // The native first pass generates the halo without sampling its composable input.
        if (code.contains("composable.eval")) throw new IllegalArgumentException("Outer Glow requires an input");
        shader = new RuntimeShader(code);
        JSONArray uniforms = source.getJSONArray("uniforms");
        for (int i = 0; i < uniforms.length(); i++) {
            JSONObject uniform = uniforms.getJSONObject(i);
            String name = uniform.getString("name");
            Object value = uniform.get("value");
            try {
                if (value instanceof Number number) shader.setFloatUniform(name, number.floatValue());
                else if (value instanceof JSONArray vector) {
                    float[] values = new float[vector.length()];
                    for (int j = 0; j < values.length; j++) values[j] = (float) vector.getDouble(j);
                    shader.setFloatUniform(name, values);
                }
            } catch (IllegalArgumentException unusedUniform) {
                // Provider JSON may retain controls removed from its exported shader.
            }
        }
        // OuterGlowView.updateParams overrides the exported blur layer to .015 * 500 pixels.
        blur = RenderEffect.createBlurEffect(7.5f, 7.5f, Shader.TileMode.CLAMP);
    }

    static DiyOuterGlowShader create(Context context) {
        try {
            JSONObject definition = definition(context);
            return definition == null ? null : new DiyOuterGlowShader(context, definition);
        } catch (Exception | LinkageError error) {
            DressroomModule.note("Original Outer Glow shader unavailable", error);
            return null;
        }
    }

    private static synchronized JSONObject definition(Context context) throws Exception {
        if (definitionChecked) return cachedDefinition;
        for (String pkg : PACKAGES) {
            Resources resources = context.getResources();
            int id = resources.getIdentifier("agsl_now_bar_roundrect", "raw", pkg);
            if (id == 0) {
                try {
                    resources = context.createPackageContext(pkg, 0).getResources();
                    id = resources.getIdentifier("agsl_now_bar_roundrect", "raw", pkg);
                } catch (android.content.pm.PackageManager.NameNotFoundException absent) { continue; }
            }
            if (id == 0) continue;
            try (InputStream input = resources.openRawResource(id)) {
                byte[] bytes = input.readNBytes(1024 * 1024 + 1);
                if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("Outer Glow data too large");
                cachedDefinition = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
            definitionChecked = true;
            return cachedDefinition;
        }
        definitionChecked = true;
        return null;
    }

    RuntimeShader shader() { return shader; }
    RenderEffect blur() { return blur; }

    void draw(Canvas canvas, Paint paint, int width, int height, float time,
            float radius, float inset, int primary, int secondary, float opacity,
            boolean isLandscape) {
        if (width != lastWidth || height != lastHeight || radius != lastRadius
                || inset != lastInset || landscape != isLandscape) {
            lastWidth = width;
            lastHeight = height;
            lastRadius = radius;
            lastInset = inset;
            landscape = isLandscape;
            float contentWidth = Math.max(1f, width - 2f * inset);
            float contentHeight = Math.max(1f, height - 2f * inset);
            shader.setFloatUniform("iResolution", width, height);
            shader.setFloatUniform("uRectWidth", contentWidth / width);
            shader.setFloatUniform("uRectHeight", contentHeight / height);
            shader.setFloatUniform("uThickness", 4f * density / height);
            shader.setFloatUniform("uCornerRadius", radius / height);
            shader.setFloatUniform("uSegments", Math.max(10f, Math.min(45f,
                    ((contentWidth + contentHeight - 200f) / 800f) * 35f + 10f)));
            shader.setFloatUniform("uTailLength", isLandscape && contentHeight > 80f * density ? 0.4f : 0.5f);
        }
        if (!colorsSet || primary != lastPrimary || secondary != lastSecondary) {
            colorsSet = true;
            lastPrimary = primary;
            lastSecondary = secondary;
            setColor("uColor1", primary);
            setColor("uColor2", secondary);
            setColor("u2Color1", secondary);
            setColor("u2Color2", primary);
        }
        shader.setFloatUniform("iTime", time);
        paint.setAlpha(Math.round(255f * opacity));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setAlpha(255);
    }

    private void setColor(String name, int color) {
        shader.setFloatUniform(name, Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f);
    }
}
