#!/bin/bash
# Generate release keystore for PWDongle Android app

KEYSTORE_DIR="$(dirname "$0")"
KEYSTORE_FILE="$KEYSTORE_DIR/pwdongle-release.keystore"

# Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore already exists at: $KEYSTORE_FILE"
    echo "Delete it first if you want to regenerate."
    exit 1
fi

echo "=== PWDongle Release Keystore Generation ==="
echo ""
echo "This will create a keystore for signing release APKs."
echo "You will be prompted for:"
echo "  - Keystore password (store this securely!)"
echo "  - Key alias (default: pwdongle)"
echo "  - Key password (can be same as keystore password)"
echo "  - Certificate details (name, organization, etc.)"
echo ""
echo "IMPORTANT: Save the passwords in gradle.properties:"
echo "  keystorePassword=YOUR_PASSWORD"
echo "  keyAlias=pwdongle"
echo "  keyPassword=YOUR_PASSWORD"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# Generate keystore
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias pwdongle \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storetype PKCS12

if [ $? -eq 0 ]; then
    echo ""
    echo "=== Keystore generated successfully! ==="
    echo "Location: $KEYSTORE_FILE"
    echo ""
    echo "Next steps:"
    echo "1. Add these lines to android-recorder/gradle.properties:"
    echo "   keystorePassword=YOUR_PASSWORD"
    echo "   keyAlias=pwdongle"
    echo "   keyPassword=YOUR_PASSWORD"
    echo ""
    echo "2. Build signed release APK:"
    echo "   cd android-recorder"
    echo "   ./gradlew assembleRelease"
    echo ""
    echo "3. Find release APK at:"
    echo "   app/build/outputs/apk/release/app-release.apk"
    echo ""
    echo "SECURITY: Keep keystore and passwords secure!"
    echo "          Add keystore/ to .gitignore"
    echo "          Never commit gradle.properties with passwords"
else
    echo ""
    echo "Keystore generation failed!"
    exit 1
fi
