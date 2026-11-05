package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

final class EditorExtras {
    private Context context;
    private Bundle draft;
    private boolean dirty;

    Bundle settings(Context context) {
        this.context = context.getApplicationContext();
        if (draft == null) draft = ExtrasPreferences.read(context);
        return new Bundle(draft);
    }

    void update(Context context, Bundle b) {
        settings(context);
        draft = ExtrasPreferences.clean(b);
        dirty = true;
    }

    Bundle freeze() {
        return dirty && draft != null ? new Bundle(draft) : null;
    }

    void commit(Bundle b) {
        if (context != null) {
            ExtrasPreferences.commit(context, b);
            committed(b);
        }
    }

    void committed(Bundle saved) {
        if (draft == null || saved == null) return;
        for (String key : ExtrasPreferences.FLAGS)
            if (draft.getBoolean(key) != saved.getBoolean(key)) return;
        if (!draft.getString("title", "").equals(saved.getString("title", ""))
                || !draft.getString("message", "").equals(saved.getString("message", ""))) return;
        dirty = false;
    }

    void reset() {
        context = null;
        draft = null;
        dirty = false;
    }
}
