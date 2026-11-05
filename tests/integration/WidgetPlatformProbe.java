package dev.denver.dressroomadvanced;

/** Native APK contract checks run with app_process, before installing UI hooks. */
public final class WidgetPlatformProbe {
    private static int checks;
    private static ClassLoader editor, aod;
    private static Class<?> type(ClassLoader loader, String name) throws Exception { return Class.forName(name,false,loader); }
    private static void field(ClassLoader loader, String name, String field) throws Exception { type(loader,name).getDeclaredField(field); checks++; }
    private static void method(ClassLoader loader, String name, String method, int count) throws Exception {
        for (var m : type(loader,name).getDeclaredMethods()) if (m.getName().equals(method) && m.getParameterCount() == count) { checks++; return; }
        throw new NoSuchMethodException(name + "." + method + "/" + count);
    }
    public static void main(String[] args) throws Exception {
        editor = new dalvik.system.PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        aod = new dalvik.system.PathClassLoader(args[1], ClassLoader.getSystemClassLoader());
        for (String f : new String[]{"g","a"}) field(editor,"v8.y",f);
        method(editor,"v8.y","l",0);
        type(editor,"k6.m").getDeclaredConstructor(int.class,int.class,Runnable.class,String.class); checks++;
        field(editor,"k6.m","f"); field(editor,"k6.m","e");
        method(editor,"dc.h1","d",10);
        field(editor,"dc.h1","a"); field(editor,"p8.a","c"); field(editor,"v8.j0","h");
        method(editor,"v8.j0","setAnchorStateSupported",1);
        method(editor,"yc.b","c",1);
        for (String f : new String[]{"a","b","c","D","G","H","L","l","i","p","y","z"}) field(editor,"op.m",f);
        method(editor,"cg.v","a",1);
        method(editor,"rh.a","i",0);
        method(editor,"cg.n","c",1); method(editor,"pc.h","a",1);
        method(editor,"op.m","b",0); method(editor,"pc.h","d",0);
        method(editor,"sd.q","b",2); method(editor,"sd.h","a",3); method(editor,"sd.o","a",9); field(editor,"sd.o","c");
        method(editor,"sd.m","f",7); method(editor,"sd.d","a",2);
        method(editor,"com.samsung.android.lockscreen.editor.item.domain.entity.system.DeviceType","isTablet",0);
        String grid = WidgetEngine.GRID, size = WidgetEngine.SIZE;
        for (String m : new String[]{"getColumn","getRow","isBiggerSize","getWidgetDisplayType","getUnitSize","getScaleFactor"}) method(aod,size,m,0);
        field(aod,size,"context"); field(aod,size,"_scaleFactor"); field(aod,size,"widgetAlignType"); method(aod,size,"init",1);
        for (String m : new String[]{"getWidgetSizeManager","updateRootContainerSize","getModuleInfo"}) method(aod,grid,m,0);
        method(aod,grid,"initGridLayoutSize",2); method(aod,grid,"reconfigureGridLayout",1); method(aod,grid,"setRootContainer",3);
        field(aod,grid,"context");
        field(aod,grid,"containerManager"); method(aod,grid,"getGridManager",0);
        method(aod,"aod.wz2","b",1);
        method(aod,"aod.sy2","f",0); method(aod,"aod.sy2","o",3);
        type(editor,"v8.d0"); checks++;
        android.os.Bundle defaults = WidgetPreferences.defaults();
        if (defaults.getBoolean("layout") || defaults.getInt("anchor") != 0 || defaults.getInt("depth") != 0 || defaults.getBoolean("blurOff"))
            throw new AssertionError("Migration changed native/default behavior"); checks++;
        android.os.Bundle changed = new android.os.Bundle(defaults); changed.putInt("depth",2);
        android.os.Bundle clock = new android.os.Bundle(); clock.putBoolean("depthEnabled",true);
        if (WidgetPreferences.depth(changed,clock)) throw new AssertionError("Widget depth Off still follows clock"); checks++;
        changed.putInt("depth",1); clock.putBoolean("depthEnabled",false);
        if (!WidgetPreferences.depth(changed,clock)) throw new AssertionError("Widget depth On depends on clock depth"); checks++;
        changed.putFloat("scale",Float.NaN); changed.putInt("columns",8); changed.putInt("rows",8);
        android.os.Bundle safe = WidgetPreferences.clean(changed);
        if (safe.getFloat("scale") != 1 || safe.getInt("rows") != 4) throw new AssertionError("Invalid grid metrics reached provider"); checks++;
        byte[] mask = {1,2,3}; changed.putByteArray("depthMask",mask); android.os.Bundle frozen = WidgetPreferences.clean(changed); mask[0] = 9;
        if (frozen.getByteArray("depthMask")[0] != 1) throw new AssertionError("Widget save snapshot shares mutable subject mask"); checks++;
        System.out.println("Widget Samsung/API contracts: " + checks + " checks passed");
    }
}
