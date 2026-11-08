package dev.denver.dressroomadvanced;

import android.content.*;
import android.content.res.*;
import android.content.res.loader.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.io.*;

/** Read-only APK binding and native guide visibility checks. */
public class Widget85CompatProbe {
    static class C extends ContextWrapper {
        final Resources r;

        C(Context c, Resources r) {
            super(c);
            this.r = r;
        }

        public Resources getResources() {
            return r;
        }

        public String getPackageName() {
            return "com.samsung.android.app.dressroom";
        }
    }

    static int checks;

    static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
        checks++;
    }

    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> m = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        m.getMethod("setInstance", m).invoke(null, m.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        ClassLoader loader =
                new dalvik.system.PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        WidgetEditorBinding editor = new WidgetEditorBinding(loader);
        OneUi85WidgetLayout layout =
                new OneUi85WidgetLayout(loader, editor.create.getDeclaringClass());
        check(layout.gridMetrics.getParameterCount() == 4, "Grid-dimension ABI mismatch");
        System.out.println(
                "8.5 grid and rotation bindings: " + layout.gridMetrics + " / " + layout.rotate);
        try (ParcelFileDescriptor fd =
                        ParcelFileDescriptor.open(
                                new File(args[0]), ParcelFileDescriptor.MODE_READ_ONLY);
                ResourcesProvider provider = ResourcesProvider.loadFromApk(fd)) {
            ResourcesLoader assets = new ResourcesLoader();
            assets.addProvider(provider);
            Resources resources =
                    new Resources(
                            context.getAssets(),
                            context.getResources().getDisplayMetrics(),
                            context.getResources().getConfiguration());
            resources.addLoaders(assets);
            Context c = new C(context, resources);
            int normal =
                    resources.getIdentifier("assist_guide_box_default", "id", c.getPackageName());
            int vertical =
                    resources.getIdentifier("assist_guide_box_left", "id", c.getPackageName());
            check(normal != 0 && vertical != 0, "Native guide IDs unavailable");
            FrameLayout window = new FrameLayout(c),
                    guide = new FrameLayout(c),
                    left = new FrameLayout(c),
                    right = new FrameLayout(c);
            View normalBox = new View(c), verticalBox = new View(c);
            normalBox.setId(normal);
            verticalBox.setId(vertical);
            left.addView(normalBox);
            right.addView(verticalBox);
            guide.addView(left);
            guide.addView(right);
            window.addView(guide);
            boolean[] phone = {true};
            WidgetRotationGuide policy = new WidgetRotationGuide(() -> phone[0]);
            policy.update(window);
            check(guide.getVisibility() == View.GONE, "Phone guide remained visible");
            guide.setVisibility(View.VISIBLE);
            window.getViewTreeObserver().dispatchOnGlobalLayout();
            check(guide.getVisibility() == View.GONE, "Native rebuild restored phone guide");
            phone[0] = false;
            window.getViewTreeObserver().dispatchOnGlobalLayout();
            check(guide.getVisibility() == View.VISIBLE, "Tablet/fold guide was not restored");
            phone[0] = true;
            policy.update(window);
            policy.clear();
            check(guide.getVisibility() == View.VISIBLE, "Cleanup kept guide hidden");
            window.getViewTreeObserver().dispatchOnGlobalLayout();
            check(guide.getVisibility() == View.VISIBLE, "Cleanup left observer installed");
            resources.removeLoaders(assets);
            assets.removeProvider(provider);
            System.out.println("8.5 guide and binding contract: " + checks + " checks passed");
        }
    }
}
