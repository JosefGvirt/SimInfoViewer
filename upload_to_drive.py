#!/usr/bin/env python3
"""
Automated APK upload to Google Drive using OAuth
Uploads the built APK to Google Drive with version, date, and timestamp in filename
"""

import os
import sys
import datetime
import pickle
import subprocess
from pathlib import Path

from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

# Configuration
DRIVE_FOLDER_ID = "1iAnksDL91fhW2RfHEoG5o9ID6I9BYCp9"
APK_PATH = "app/build/outputs/apk/release/app-release.apk"
CREDENTIALS_FILE = "credentials.json"
TOKEN_FILE = "token.json"
SCOPES = ['https://www.googleapis.com/auth/drive.file']

def get_version_info():
    """Extract version info from build.gradle.kts"""
    try:
        with open("app/build.gradle.kts", "r") as f:
            content = f.read()
            
        # Extract versionCode and versionName
        version_code = None
        version_name = None
        
        for line in content.split('\n'):
            if 'versionCode' in line and '=' in line:
                version_code = line.split('=')[1].strip()
            elif 'versionName' in line and '=' in line:
                version_name = line.split('=')[1].strip().replace('"', '')
                
        return version_code, version_name
    except Exception as e:
        print(f"Error reading version info: {e}")
        return "1", "1.0"

def get_drive_service():
    """Get authenticated Google Drive service using OAuth"""
    creds = None
    
    # Load existing token if available
    if os.path.exists(TOKEN_FILE):
        with open(TOKEN_FILE, 'rb') as token:
            creds = pickle.load(token)
    
    # If no valid credentials available, let the user log in
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            try:
                flow = InstalledAppFlow.from_client_secrets_file(CREDENTIALS_FILE, SCOPES)
                print("🔐 Opening browser for Google authentication...")
                print("📝 If you see 'Access blocked', please:")
                print("   1. Go to Google Cloud Console > OAuth consent screen")
                print("   2. Add your email as a test user")
                print("   3. Try again")
                creds = flow.run_local_server(port=0)
            except Exception as e:
                print(f"❌ Authentication failed: {e}")
                print("\n🔧 To fix 'Access blocked' error:")
                print("1. Go to: https://console.cloud.google.com/apis/credentials/consent")
                print("2. Click on your OAuth consent screen")
                print("3. Scroll to 'Test users' section")
                print("4. Click 'Add Users' and add your email address")
                print("5. Save and try again")
                return None
        
        # Save the credentials for the next run
        with open(TOKEN_FILE, 'wb') as token:
            pickle.dump(creds, token)
    
    return build('drive', 'v3', credentials=creds)

def upload_to_drive():
    """Upload APK to Google Drive"""
    try:
        # Check if APK exists
        if not os.path.exists(APK_PATH):
            print(f"APK not found at {APK_PATH}")
            print("Building APK first...")
            subprocess.run(["./gradlew", "assembleRelease"], check=True)
        
        if not os.path.exists(APK_PATH):
            print("Failed to build APK")
            return False
            
        # Get version info
        version_code, version_name = get_version_info()
        
        # Create filename with version, date, and timestamp
        now = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        filename = f"SimInfoViewer_v{version_name}_build{version_code}_{now}.apk"
        
        print(f"Uploading {filename} to Google Drive...")
        
        # Get authenticated service
        service = get_drive_service()
        if service is None:
            return False
        
        # Upload file
        file_metadata = {
            "name": filename,
            "parents": [DRIVE_FOLDER_ID]
        }
        
        media = MediaFileUpload(APK_PATH, mimetype="application/vnd.android.package-archive")
        file = service.files().create(
            body=file_metadata, 
            media_body=media, 
            fields="id,webViewLink"
        ).execute()
        
        print(f"✅ Successfully uploaded to Google Drive!")
        print(f"📁 File: {filename}")
        print(f"🔗 Link: {file.get('webViewLink')}")
        print(f"🆔 File ID: {file.get('id')}")
        
        return True
        
    except Exception as e:
        print(f"❌ Error uploading to Drive: {e}")
        return False

def main():
    """Main function"""
    print("🚀 Starting APK upload to Google Drive (OAuth)...")
    
    # Upload to Drive
    success = upload_to_drive()
    
    if success:
        print("🎉 Upload completed successfully!")
    else:
        print("💥 Upload failed!")
        sys.exit(1)

if __name__ == "__main__":
    main() 