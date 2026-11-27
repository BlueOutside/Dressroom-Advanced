# Localization (Crowdin)

Dressroom Advanced uses standard Android string resources compiled with `aapt2` and loaded across hooked host processes (`com.samsung.android.app.dressroom`, `com.android.systemui`, `com.samsung.android.app.aodservice`, and `com.samsung.android.app.clockpack`) through [`ModuleStrings.java`](../app/src/main/java/dev/denver/dressroomadvanced/ModuleStrings.java).

## Source and translation files

- **Source strings (English):** [`app/src/main/res/values/strings.xml`](../app/src/main/res/values/strings.xml)
- **Crowdin config:** [`crowdin.yml`](../crowdin.yml)
- **Translated overlays:** `app/src/main/res/values-<android_code>/strings.xml` (managed by Crowdin PRs)

## Connecting Crowdin (GitHub Integration)

1. Create a project on [Crowdin](https://crowdin.com/) with **English** as the source language.
   - Because this repository is open source, you can request a free [Crowdin Open Source License](https://crowdin.com/page/open-source-project-setup-request).
2. In your Crowdin project, open **Integrations** -> **GitHub** -> **Set Up Integration**.
3. Select **Source and translation files mode**.
4. Choose the repository `BlueOutside/Dressroom-Advanced`, select the `dev` branch, and keep the configuration file path as `crowdin.yml`.
5. Keep the default service branch (`l10n_dev`) so Crowdin opens pull requests against `dev` automatically as translations are added or approved. (`crowdin.yml` sets `append_commit_message: false` so GitHub Actions validates every Crowdin PR instead of skipping CI.)
6. In Crowdin **Settings -> Export**, enable **Skip untranslated strings** and **Skip untranslated files** so Crowdin does not open PRs with untranslated English copies.
7. Choose your target languages in Crowdin **Settings -> Languages** (do not add English as a target language since `values/strings.xml` is already the English default). `crowdin.yml` maps single-region languages to clean Android folders (`values-fr`, `values-de`, `values-es`, `values-nb`, `values-sr`, etc.) while keeping regional variants like `values-pt-rBR`, `values-pt-rPT`, `values-zh-rCN`, and `values-zh-rTW`.

## Guidelines for contributors and translators

- Always keep positional placeholders intact (`%1$s`, `%2$d`, etc.). You may reorder them to fit natural grammar in your language.
- Escape apostrophes as `\'` (Crowdin handles Android XML escaping automatically on export).
- Do not translate OpenType axis tags (`HEGT`, `HGHT`), literal telemetry template tokens (`{bat}`, `{temp}`, `{ram}`), or brand names like **Dressroom Advanced**, **Now Bar**, **One UI**, and **LSPosed**.
- Keep pill badges and compact tab labels short so they fit cleanly in Samsung's lockscreen editor and Now Bar pills.

## Validation

Every pull request (including Crowdin `l10n_dev` PRs) runs:

```powershell
python scripts/check-translations.py --self-test
python scripts/check-translations.py
```

This checks XML validity, Android apostrophe/quote escaping, duplicate resource keys, placeholder parity between English and every locale overlay, and Java/Manifest `R.string.*` / `R.plurals.*` references before `aapt2` builds the APK.
