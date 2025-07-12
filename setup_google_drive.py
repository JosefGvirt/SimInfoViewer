#!/usr/bin/env python3
"""
Setup script for Google Drive API access
This script helps you create a service account and set up the necessary credentials
"""

import os
import webbrowser
import json

def main():
    print("🔧 Google Drive API Setup for SimInfoViewer")
    print("=" * 50)
    
    print("\n📋 Step-by-step instructions:")
    print("1. Go to Google Cloud Console")
    print("2. Create a new project or select existing one")
    print("3. Enable Google Drive API")
    print("4. Create a Service Account")
    print("5. Download the JSON key file")
    print("6. Share your Drive folder with the service account")
    
    print("\n🚀 Let me open the Google Cloud Console for you...")
    
    # Open Google Cloud Console
    webbrowser.open("https://console.cloud.google.com/")
    
    print("\n📝 Follow these steps:")
    print("\n1️⃣ Create/Select Project:")
    print("   - Go to https://console.cloud.google.com/")
    print("   - Create a new project or select an existing one")
    
    print("\n2️⃣ Enable Google Drive API:")
    print("   - Go to 'APIs & Services' > 'Library'")
    print("   - Search for 'Google Drive API'")
    print("   - Click on it and press 'Enable'")
    
    print("\n3️⃣ Create Service Account:")
    print("   - Go to 'APIs & Services' > 'Credentials'")
    print("   - Click 'Create Credentials' > 'Service Account'")
    print("   - Name: 'siminfoviewer-upload'")
    print("   - Description: 'Upload APKs to Google Drive'")
    print("   - Click 'Create and Continue'")
    print("   - Skip role assignment (click 'Continue')")
    print("   - Click 'Done'")
    
    print("\n4️⃣ Download JSON Key:")
    print("   - Click on the service account you just created")
    print("   - Go to 'Keys' tab")
    print("   - Click 'Add Key' > 'Create new key'")
    print("   - Choose 'JSON' format")
    print("   - Download the file")
    print("   - Rename it to 'service_account.json'")
    print("   - Place it in your project root directory")
    
    print("\n5️⃣ Share Drive Folder:")
    print("   - Go to your Google Drive folder:")
    print("   - https://drive.google.com/drive/folders/1iAnksDL91fhW2RfHEoG5o9ID6I9BYCp9")
    print("   - Right-click the folder > 'Share'")
    print("   - Add the service account email (from the JSON file)")
    print("   - Give it 'Editor' access")
    print("   - Click 'Send'")
    
    print("\n6️⃣ Test the setup:")
    print("   - Run: python3 upload_to_drive.py")
    
    print("\n" + "=" * 50)
    print("✅ Setup complete! Your APKs will be automatically uploaded to Google Drive.")
    
    # Check if service account file exists
    if os.path.exists("service_account.json"):
        print("\n🎉 Service account file found! You're ready to upload.")
    else:
        print("\n⚠️  Service account file not found. Please complete the setup steps above.")

if __name__ == "__main__":
    main() 