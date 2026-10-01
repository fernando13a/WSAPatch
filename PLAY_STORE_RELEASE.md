# IronMind - Play Store Release Guide

This guide covers everything needed to build, sign, and submit IronMind to the Google Play Store.

## Pre-Release Checklist

- [ ] Version bumped to 1.0.0 (already done)
- [ ] versionCode set appropriately (already done: 100)
- [ ] All tests passing (`./gradlew test connectedAndroidTest`)
- [ ] Privacy policy reviewed and finalized
- [ ] Store listings (English and Spanish) prepared
- [ ] Screenshot assets created (see below)
- [ ] Release notes written
- [ ] Signing keystore generated and configured

## 1. Generating a Signing Keystore

You need a keystore file to sign your release builds. Generate one with:

```bash
keytool -genkey -v -keystore keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias ironmind-release-key
```

This will prompt for:
- Keystore password (save this securely)
- Key password (can be same as keystore)
- Your name, organization, country, etc.

**Important:** Treat `keystore.jks` as a secret. Back it up securely and never commit it to version control.

## 2. Configuring Signing

Copy `keystore.properties.example` to `keystore.properties`:

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties` with your keystore details:

```properties
storeFile=keystore.jks
storePassword=your_keystore_password
keyAlias=ironmind-release-key
keyPassword=your_key_password
```

**Note:** `keystore.properties` is in `.gitignore` and should never be committed.

## 3. Building an Android App Bundle (AAB)

The AAB format is required for Play Store distribution. It's smaller and more efficient than APK.

Build the AAB:

```bash
cd IronMind
./gradlew bundleRelease
```

This generates: `app/build/outputs/bundle/release/app-release.aab`

**Size estimates:**
- AAB: ~50 MB
- Installed app: ~150-200 MB (with AI model)

## 4. Building a Signed AAB

If `keystore.properties` is configured, the AAB will be automatically signed:

```bash
./gradlew bundleRelease
```

Verify the AAB is signed:

```bash
jarsigner -verify -verbose app/build/outputs/bundle/release/app-release.aab
```

## 5. Testing Before Submission

### Test Locally with AAB

Convert AAB to APK for testing:

```bash
# Using bundletool (install if not present)
bundletool build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks \
  --ks=keystore.jks \
  --ks-key-alias=ironmind-release-key
```

Install on device:

```bash
bundletool install-apks --apks=app.apks
```

### Run Full Test Suite

```bash
./gradlew test connectedAndroidTest
```

## 6. Play Store Submission Steps

1. **Create a Developer Account**
   - Go to https://play.google.com/console
   - Set up a developer account ($25 USD one-time fee)
   - Create a new app entry for "IronMind"

2. **Fill App Details**
   - App title: IronMind - Neural Strength Coach
   - Short description: (see PLAY_STORE_LISTING_EN.md)
   - Full description: (see PLAY_STORE_LISTING_EN.md)
   - Repeat for Spanish: (see PLAY_STORE_LISTING_ES.md)

3. **Add Screenshots**
   - 5 high-quality screenshots per language
   - Size: 1440x2560 px (recommended)
   - Dimensions: 9:16 aspect ratio
   - Showcase: Dashboard, logging, rest timer, AI coach, routines
   - Add captions explaining each feature

4. **Content Rating**
   - Fill Google Play Content Rating Questionnaire
   - IronMind: ESRB E (Everyone) / PEGI 3

5. **Privacy Policy**
   - Link to privacy policy (recommended: host on GitHub)
   - Or upload privacy policy file

6. **Target API Level**
   - Target SDK: 35 (already configured)
   - Min SDK: 26 (API 8.0+)

7. **Review Information**
   - Add test account credentials if needed
   - Add notes on how to test features
   - Explain AI model download requirement

8. **Upload AAB**
   - In Play Console: Create new release
   - Upload `app-release.aab`
   - Add release notes (see version 1.0.0 notes in listings)
   - Select rollout percentage (start with 5% → 25% → 100%)

9. **Staging Review**
   - Submit for staged rollout
   - Wait for approval (typically 2-4 hours)

10. **Production Release**
    - Monitor crash reports and ratings
    - Increase rollout % gradually
    - Once stable, rollout to 100%

## 7. Post-Release Monitoring

After release, monitor:

- **Crash Reports**: Check Play Console for ANRs and crashes
- **Ratings & Reviews**: Respond to user feedback
- **Install Rate**: Track adoption and retention
- **Regional Performance**: Monitor Spanish vs English users

## 8. Future Version Bumping

For future releases:

```groovy
// In app/build.gradle.kts
versionCode = 101  // Increment by 1
versionName = "1.0.1"  // Semantic versioning
```

Then rebuild and upload new AAB.

## AppBundle Structure

```
app-release.aab
├── base/
│   ├── manifest/
│   ├── dex/
│   ├── res/
│   ├── lib/
│   └── assets/
├── BundleConfig.pb
└── bundle-metadata/
```

The Play Store uses this to generate optimized APKs per device configuration.

## Troubleshooting

### AAB Build Fails
```
Error: Keystore not found
→ Generate keystore.jks or disable signing in build.gradle.kts
```

### Signing Issues
```
Error: -debug.keystore used instead of release key
→ Ensure keystore.properties exists and has correct paths
```

### Play Store Upload Fails
```
Error: File not signed or signature corrupted
→ Verify with: jarsigner -verify -verbose app-release.aab
```

### APK Installation Fails After AAB Build
```
Error: Inconsistent application signatures
→ Uninstall previous debug builds first: adb uninstall com.ironmind.app
```

## Security Best Practices

1. **Keystore Security**
   - Never commit `keystore.jks` or `keystore.properties`
   - Store backups in encrypted offline storage
   - Use strong passwords (20+ characters)
   - Keep keystore password separate from key password

2. **Code Signing**
   - Sign all releases with the same key (required by Play Store)
   - Never lose your keystore file (irreplaceable)
   - Enable multi-factor authentication on Play Console

3. **Dependencies**
   - Review ProGuard rules before release
   - Check that no debug logging is enabled
   - Verify no sensitive data is logged

## Release Signing Verification

After building, verify the signing:

```bash
# Check AAB signature
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab

# Check certificate details
keytool -list -v -keystore keystore.jks -alias ironmind-release-key
```

## Support & Resources

- **Play Console**: https://play.google.com/console
- **Play Store Policies**: https://play.google.com/about/developer-content-policy/
- **Android Guidelines**: https://developer.android.com/distribute
- **App Signing**: https://developer.android.com/studio/publish/app-signing

## Rollout Strategy

For v1.0.0:

1. **Stage 1** (0-5%, 2 hours)
   - Monitor crash rates and ANRs
   - Check basic functionality

2. **Stage 2** (5-25%, 12 hours)
   - Monitor install rates
   - Check ratings and reviews
   - Verify AI model downloads

3. **Stage 3** (25-100%, 24 hours)
   - Full rollout if stable
   - Continue monitoring

This staged approach allows you to catch critical issues before reaching 100% of users.

---

**IronMind v1.0.0 is ready for the world. Let's ship it! 🚀**
