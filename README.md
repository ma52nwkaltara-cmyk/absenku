# AbsenKU Android wrapper

- App name: **AbsenKU**
- Website: https://absensi.rdmku.cloud/index.html
- Package ID: `id.absenku.app`
- Build: GitHub Actions (`.github/workflows/build-apk.yml`), no Android Studio required.
- Location permissions and WebView geolocation handling are included.
- Native notification sound then Indonesian Android Text-to-Speech service are included.
- Firebase is not required for local alarms.

## Important integration note
The website currently must call the native bridge to register each logged-in teacher's schedule for native background alarms. From page JavaScript, call:

```js
if (window.AbsenKuNative?.scheduleLesson) {
  // startEpochMillis must be milliseconds since Unix epoch for today's lesson.
  AbsenKuNative.scheduleLesson(String(schedule.id), startEpochMillis,
    `${schedule.subject}, kelas ${schedule.class_name}`, schedule.teacher_name || 'guru');
}
```

Call it only for the currently logged-in teacher's schedules and whenever schedules change. For each lesson, Android schedules a local alarm five minutes before and at the start time. The webpage also needs to provide a correct start timestamp (with the madrasah's local timezone). Alarms can be restricted by Android battery saver or exact-alarm settings; test on the target phone. A permission prompt may be needed for notifications and location.

## Build on GitHub
1. Upload the complete contents of this folder to the root of a GitHub repository (not the enclosing `AbsenKU` folder nested inside an old project).
2. Open **Actions** and run **Build AbsenKU APK** (or push to `main`/`master`).
3. Download artifact `AbsenKU-debug-apk` from a successful workflow run.

This starter project is created from scratch; it has not yet been tested on a physical phone. Background lesson alarms will not know the website's schedule until the website JavaScript bridge integration described above is added.


## Ikon aplikasi
Ikon launcher AbsenKU sudah ditambahkan untuk density Android mdpi sampai xxxhdpi, termasuk ikon adaptive Android 8+ dan ikon bulat. Manifest memakai `@mipmap/ic_launcher` serta `@mipmap/ic_launcher_round`. Berkas master tersedia di folder `assets/`.
