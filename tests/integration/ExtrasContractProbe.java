package dev.denver.dressroomadvanced;

import android.os.Bundle;

public final class ExtrasContractProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Extras check "+checks);checks++;}
    public static void main(String[] args) {
        try { run(); } catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }
    private static void run() {
        Bundle clean=ExtrasPreferences.clean(null);
        for(String flag:ExtrasPreferences.FLAGS)check(!clean.getBoolean(flag));
        Bundle input=new Bundle();input.putBoolean("keepCharging",true);input.putBoolean("keepStretch",true);
        input.putBoolean("fullscreenArt",true);input.putBoolean("textCard",true);
        input.putBoolean("forceNowBarBlur",true);input.putBoolean("forceWidgetBlur",true);
        input.putInt("wallpaperId",55);input.putInt("font",800);input.putBoolean("hideDate",true);
        input.putString("title","  Hello\u0000🌹  ");input.putString("message","🌹".repeat(200));
        clean=ExtrasPreferences.clean(input);
        check(clean.getBoolean("keepCharging") && clean.getBoolean("keepStretch"));
        check(clean.getBoolean("fullscreenArt") && clean.getBoolean("textCard"));
        check(!clean.containsKey("forceNowBarBlur") && !clean.containsKey("forceWidgetBlur"));
        check(!clean.containsKey("wallpaperId") && !clean.containsKey("font") && !clean.containsKey("hideDate"));
        check(clean.getString("title").equals("Hello 🌹"));
        String body=clean.getString("message");check(body.codePointCount(0,body.length())==180);
        check(!Character.isHighSurrogate(body.charAt(body.length()-1)));
        check(input.getString("message").codePointCount(0,input.getString("message").length())==200);
        check(ExtrasPreferences.text("a".repeat(100),80).length()==80);
        System.out.println("Native lockscreen extras contracts: "+checks+" checks passed");
    }
}
