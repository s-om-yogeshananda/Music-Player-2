# Music Player: GitHub-এ আপলোড ও APK বিল্ড গাইড

## ১. GitHub-এ আপলোড
1. github.com-এ নতুন Repository বানান (যেমন `MusicPlayer`)।
2. এই ZIP এক্সট্র্যাক্ট করুন। ভেতরের ফাইল/ফোল্ডারগুলো (`app`, `gradle`, `.github`, `build.gradle.kts` ...) Repository-র রুটে আপলোড করুন।
   - ওয়েবে "uploading an existing file" দিয়ে ফোল্ডার ড্র্যাগ করলে `.github` ফোল্ডার লুকানো থাকায় বাদ পড়তে পারে। তাই git ব্যবহার করা ভালো:
   ```
   git init
   git add .
   git commit -m "Music Player"
   git branch -M main
   git remote add origin https://github.com/<আপনার-ইউজারনেম>/MusicPlayer.git
   git push -u origin main
   ```
3. `gradlew` বা `gradle-wrapper.jar` লাগবে না। Workflow নিজেই Gradle 8.7 সেটআপ করে।

## ২. APK ডাউনলোড
1. Repository-র **Actions** ট্যাবে যান, "Build APK" workflow চলবে (প্রথমবার ৫-১০ মিনিট)।
2. সবুজ টিক এলে রান খুলুন, নিচে **Artifacts** থেকে `MusicPlayer-release` বা `MusicPlayer-debug` ডাউনলোড করুন।
3. ZIP খুলে `.apk` ফোনে ইনস্টল করুন (Unknown sources অনুমতি দিতে হবে)।
4. বিল্ড ফেইল করলে Actions লগের লাল অংশের এরর আমাকে পাঠান, আমি ঠিক করে দেব।

## ৩. নিজের কিস্টোর দিয়ে Release সাইন (ঐচ্ছিক)
না দিলে Release APK-ও debug key দিয়ে সাইন হবে (ইনস্টল করা যাবে)।
1. কিস্টোর বানান: `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mykey`
2. Base64: Linux/Mac: `base64 -w0 release.jks`, Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`
3. Repository → Settings → Secrets and variables → Actions → New repository secret:
   - `KEYSTORE_BASE64`: উপরের Base64 টেক্সট
   - `KEYSTORE_PASSWORD`: কিস্টোর পাসওয়ার্ড
   - `KEY_ALIAS`: `mykey`
   - `KEY_PASSWORD`: কী পাসওয়ার্ড
4. আবার push করলে বা Actions থেকে "Run workflow" দিলে signed APK তৈরি হবে।


## ৪. নতুন UI আপডেট
- YouTube/Online সার্চ ট্যাব সরানো হয়েছে; এখন Library, Search ও Settings আছে।
- নতুন gradient music app icon এবং প্রতিবার চালুর সময় ৩ সেকেন্ডের animated splash screen।
- Mini player-এ ডানে/বামে swipe করলে আগের/পরের গান; নিচে swipe করলে playback stop হয়ে mini player বন্ধ হবে।
- Player action কমিয়ে Queue, Lyrics, Equalizer ও Sleep রাখা হয়েছে।
- Fast alphabet scroller-এ subtle gradient rail এবং নরম transition যোগ করা হয়েছে।
- GitHub Actions workflow আগের মতোই `assembleDebug` ও `assembleRelease` build করবে।
