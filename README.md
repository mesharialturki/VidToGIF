# GIF Studio for Android

Turn videos into GIFs and edit them: trim, crop, zoom, filters, text, images, speed and boomerang.
Works fully offline. Saves to **Gallery › GIF Studio**. Needs Android 10 or newer.

## Option A: let GitHub build the APK (no computer needed)

1. Create a free account at github.com and make a new **public** repository (e.g. `gif-studio`).
2. Upload everything in this folder to that repository, including the hidden `.github` folder.
3. Open the repository's **Actions** tab. The "Build APK" job starts by itself and takes about 3–5 minutes.
4. When it shows a green check, open the **Releases** section (right side of the repository's main page)
   and tap **GIF-Studio.apk** to download it.

## Option B: build it on a computer

1. Install Android Studio (free) from developer.android.com/studio.
2. Choose **Open** and pick this folder. Wait for it to finish syncing.
3. Choose **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. The APK appears in `app/build/outputs/apk/`.

## Installing the APK on your phone

1. Open the downloaded **GIF-Studio.apk**.
2. If Android asks, allow your browser or Files app to "install unknown apps".
3. Tap **Install**, then **Open**.

Play Protect may warn that the app comes from an unknown developer. That's normal for apps you build yourself.
Tap **More details › Install anyway**.

## Project layout

- `app/src/main/assets/index.html` – the whole editor (HTML/JS, GPU filters, built-in GIF encoder)
- `app/src/main/java/.../MainActivity.java` – the Android shell: WebView, video picker, save to Gallery, share, back button
- `.github/workflows/build-apk.yml` – automatic cloud build
