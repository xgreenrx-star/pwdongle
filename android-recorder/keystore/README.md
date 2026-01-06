# Release Signing Setup

This directory contains the release keystore for signing PWDongle Android APKs.

## First-Time Setup

### 1. Generate Keystore

Run the generation script:

```bash
cd android-recorder/keystore
./generate-keystore.sh
```

Follow the prompts to enter:
- Keystore password (minimum 6 characters)
- Key alias (default: `pwdongle`)
- Key password (can be same as keystore password)
- Certificate details:
  - Name: Your name
  - Organization: PWDongle (or your org)
  - City, State, Country: Your location

The script will create `pwdongle-release.keystore` in this directory.

### 2. Configure Gradle Properties

Create or edit `android-recorder/gradle.properties` and add:

```properties
keystorePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=pwdongle
keyPassword=YOUR_KEY_PASSWORD
```

**IMPORTANT:** 
- Never commit `gradle.properties` with real passwords to git
- Keep a backup of the keystore and passwords in a secure location
- If you lose the keystore, you cannot update the app on Google Play

### 3. Build Signed Release APK

```bash
cd android-recorder
./gradlew assembleRelease
```

The signed APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

## CI/CD Setup (GitHub Actions)

For automated release builds, add these secrets to your GitHub repository:

1. Go to repository Settings → Secrets and variables → Actions
2. Add these secrets:
   - `KEYSTORE_PASSWORD`: Your keystore password
   - `KEY_ALIAS`: `pwdongle`
   - `KEY_PASSWORD`: Your key password
   - `KEYSTORE_FILE`: Base64-encoded keystore file

To encode the keystore file:
```bash
base64 -w 0 pwdongle-release.keystore > keystore.b64
# Copy contents of keystore.b64 to KEYSTORE_FILE secret
```

## Verify Signature

To verify APK signature:

```bash
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
```

Or use apksigner:

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

## Security Checklist

- [ ] Keystore file is NOT committed to git (check .gitignore)
- [ ] gradle.properties with passwords is NOT committed to git
- [ ] Keystore and passwords are backed up securely offline
- [ ] GitHub secrets are configured for CI/CD
- [ ] Release APK signature verified

## Keystore Details

- **Type:** PKCS12
- **Algorithm:** RSA
- **Key Size:** 2048 bits
- **Validity:** 10,000 days (~27 years)
- **Alias:** pwdongle

## Troubleshooting

### Build fails with "keystore not found"
- Ensure `pwdongle-release.keystore` exists in `android-recorder/keystore/`
- Check that the path in `app/build.gradle` is correct

### Build fails with "incorrect password"
- Verify passwords in `gradle.properties` or environment variables
- Try regenerating the keystore if passwords are lost

### "Certificate fingerprint does not match"
- You're using a different keystore than before
- For Google Play, you must use the same keystore for all updates
- Contact Google Play support if keystore is lost

## Environment Variables (Alternative)

Instead of `gradle.properties`, you can use environment variables:

```bash
export KEYSTORE_PASSWORD="your_password"
export KEY_ALIAS="pwdongle"
export KEY_PASSWORD="your_password"
./gradlew assembleRelease
```

This is more secure for CI/CD environments.
