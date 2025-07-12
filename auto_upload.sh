#!/bin/bash

# Auto-upload script for SimInfoViewer APK to Google Drive
# This script builds the APK and uploads it with version, date, and timestamp

echo "🚀 Starting automatic APK build and upload..."

# Build the APK
echo "📱 Building APK..."
./gradlew assembleRelease

if [ $? -eq 0 ]; then
    echo "✅ APK built successfully!"
    
    # Upload to Google Drive
    echo "☁️  Uploading to Google Drive..."
    python3 upload_to_drive.py
    
    if [ $? -eq 0 ]; then
        echo "🎉 APK successfully uploaded to Google Drive!"
    else
        echo "❌ Failed to upload to Google Drive"
        exit 1
    fi
else
    echo "❌ Failed to build APK"
    exit 1
fi 