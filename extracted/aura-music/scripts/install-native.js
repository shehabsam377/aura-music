#!/usr/bin/env node
// Idempotent installer. PATCHES the Capacitor-generated Android project (never replaces the manifest).
// Originals are saved once as *.aura.bak. Safe to run repeatedly.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..');
const A = path.join(root, 'android'), main = path.join(A, 'app/src/main');
const MARK = 'AURA-NATIVE';
const die = m => { console.error('\nERROR: ' + m + '\nSee native/gradle-additions.txt for the manual steps.'); process.exit(1); };
const rd = p => fs.readFileSync(p, 'utf8');
const wr = (p, s) => { if (!fs.existsSync(p + '.aura.bak')) fs.copyFileSync(p, p + '.aura.bak'); fs.writeFileSync(p, s); };
if (!fs.existsSync(A)) die('android/ not found. Run "npx cap add android" first.');

// 0. namespace must match our Kotlin package
const appG = path.join(A, 'app/build.gradle'); let g = rd(appG);
const ns = (g.match(/namespace\s*=?\s*["']([^"']+)["']/) || [])[1];
if (ns !== 'com.aura.music') die(`app namespace is "${ns}", expected "com.aura.music". Re-create android/ with appId com.aura.music.`);

// 1. Kotlin sources (replace generated MainActivity.java)
const dst = path.join(main, 'java/com/aura/music'); fs.mkdirSync(dst, { recursive: true });
const jv = path.join(dst, 'MainActivity.java');
if (fs.existsSync(jv)) fs.renameSync(jv, jv + '.aura.bak');
const src = path.join(root, 'native/java/com/aura/music');
fs.readdirSync(src).filter(f => f.endsWith('.kt')).forEach(f => fs.copyFileSync(path.join(src, f), path.join(dst, f)));

// 2. Manifest: ADD permissions + service, keep everything Capacitor generated (FileProvider, INTERNET, theme...)
const mp = path.join(main, 'AndroidManifest.xml'); let m = rd(mp);
if (!m.includes(MARK)) {
  if (!/<application[\s>]/.test(m) || !m.includes('</application>')) die('Unexpected AndroidManifest.xml layout.');
  const perms = `    <!-- ${MARK} -->
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
`;
  const svc = `        <!-- ${MARK} -->
        <service android:name=".PlaybackService" android:exported="true" android:foregroundServiceType="mediaPlayback">
            <intent-filter><action android:name="androidx.media3.session.MediaSessionService" /></intent-filter>
        </service>
`;
  m = m.replace(/<application/, () => perms + '    <application').replace('</application>', () => svc + '    </application>');
  wr(mp, m);
}

// 3. Gradle (root: Kotlin plugin)
const rootG = path.join(A, 'build.gradle'); let r = rd(rootG);
if (!r.includes(MARK)) {
  const re = /classpath\s+['"]com\.android\.tools\.build:gradle:[^'"]+['"]/;
  if (!re.test(r)) die('Could not find the AGP classpath line in android/build.gradle.');
  r = r.replace(re, x => `${x}\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24' // ${MARK}`);
  wr(rootG, r);
}
// 3b. Gradle (app: kotlin plugin, Java/Kotlin 17, Media3)
if (!g.includes(MARK)) {
  const a = /apply plugin:\s*['"]com\.android\.application['"]/, b = /\nandroid\s*\{/, c = /\ndependencies\s*\{/;
  if (!a.test(g) || !b.test(g) || !c.test(g)) die('Unexpected android/app/build.gradle layout.');
  g = g.replace(a, x => `${x}\napply plugin: 'kotlin-android' // ${MARK}`)
       .replace(b, x => `${x}\n    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 } // ${MARK}\n    kotlinOptions { jvmTarget = '17' } // ${MARK}`)
       .replace(c, x => `${x}\n    implementation 'androidx.media3:media3-exoplayer:1.4.1' // ${MARK}\n    implementation 'androidx.media3:media3-session:1.4.1' // ${MARK}`);
  wr(appG, g);
}
console.log('Native layer installed. Next: npx cap sync android');
