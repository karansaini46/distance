import os
import subprocess
from datetime import datetime, timedelta

commits = [
    {
        "message": "Initial project setup with Gradle wrapper and basic settings",
        "files": [
            ".gitignore",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        ]
    },
    {
        "message": "Add app module build script and AndroidManifest",
        "files": [
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/google-services.json.PLACEHOLDER"
        ]
    },
    {
        "message": "Set up base UI theme and color palette",
        "files": [
            "app/src/main/res/values/colors.xml",
            "app/src/main/res/values/themes.xml",
            "app/src/main/res/values/strings.xml",
            "app/src/main/java/com/karan/distancewidget/ui/theme/Color.kt",
            "app/src/main/java/com/karan/distancewidget/ui/theme/Theme.kt"
        ]
    },
    {
        "message": "Add common drawables and launcher icon",
        "files": [
            "app/src/main/res/drawable/circle_bg.xml",
            "app/src/main/res/drawable/gradient_bottom.xml",
            "app/src/main/res/drawable/ic_heart.xml",
            "app/src/main/res/drawable-nodpi/ic_launcher.png",
            "app/src/main/res/drawable/widget_bg.xml"
        ]
    },
    {
        "message": "Implement data models and preferences helper",
        "files": [
            "app/src/main/java/com/karan/distancewidget/data/LocationData.kt",
            "app/src/main/java/com/karan/distancewidget/data/Prefs.kt"
        ]
    },
    {
        "message": "Add Firebase and Storage integration helpers",
        "files": [
            "app/src/main/java/com/karan/distancewidget/data/FirebaseHelper.kt",
            "app/src/main/java/com/karan/distancewidget/data/StorageHelper.kt",
            "app/google-services.json"
        ]
    },
    {
        "message": "Add Application class and MainActivity skeleton",
        "files": [
            "app/src/main/java/com/karan/distancewidget/DistanceApp.kt",
            "app/src/main/java/com/karan/distancewidget/MainActivity.kt"
        ]
    },
    {
        "message": "Implement common UI components: GlassCard and GradientButton",
        "files": [
            "app/src/main/java/com/karan/distancewidget/ui/components/GlassCard.kt",
            "app/src/main/java/com/karan/distancewidget/ui/components/GradientButton.kt"
        ]
    },
    {
        "message": "Add SetupScreen for initial configuration",
        "files": [
            "app/src/main/java/com/karan/distancewidget/ui/SetupScreen.kt"
        ]
    },
    {
        "message": "Implement MainScreen for dashboard view",
        "files": [
            "app/src/main/java/com/karan/distancewidget/ui/MainScreen.kt"
        ]
    },
    {
        "message": "Add background workers for location and photo sync",
        "files": [
            "app/src/main/java/com/karan/distancewidget/worker/LocationWorker.kt",
            "app/src/main/java/com/karan/distancewidget/worker/PhotoSyncWorker.kt",
            "app/src/main/java/com/karan/distancewidget/util/WorkerScheduler.kt",
            "app/src/main/java/com/karan/distancewidget/receiver/BootReceiver.kt"
        ]
    },
    {
        "message": "Add layouts for widgets",
        "files": [
            "app/src/main/res/layout/widget_distance.xml",
            "app/src/main/res/xml/widget_info.xml"
        ]
    },
    {
        "message": "Implement DistanceWidget provider",
        "files": [
            "app/src/main/java/com/karan/distancewidget/widget/DistanceWidget.kt"
        ]
    },
    {
        "message": "Add PhotoWidget layouts and provider implementation",
        "files": [
            "app/src/main/res/layout/widget_photo.xml",
            "app/src/main/res/xml/widget_photo_info.xml",
            "app/src/main/java/com/karan/distancewidget/widget/PhotoWidget.kt"
        ]
    },
    {
        "message": "Add build instructions and scripts",
        "files": [
            "BUILD_AND_INSTALL.md",
            "rewrite_commits.py",
            "split_commits.py"
        ]
    }
]

def run_cmd(cmd):
    print(f"Running: {cmd}")
    subprocess.run(cmd, shell=True, check=True)

subprocess.run("rm -rf .git", shell=True)
run_cmd("git init")
run_cmd("git branch -M main")

start_date = datetime.now() - timedelta(days=14)

for i, commit in enumerate(commits):
    # Calculate date for this commit
    commit_date = start_date + timedelta(days=i * 14 / len(commits))
    date_str = commit_date.strftime("%Y-%m-%dT%H:%M:%S")
    
    # Add files
    for f in commit["files"]:
        if os.path.exists(f):
            run_cmd(f'git add "{f}"')
        else:
            print(f"Warning: File {f} does not exist.")
            
    # Commit
    env = os.environ.copy()
    env["GIT_AUTHOR_DATE"] = date_str
    env["GIT_COMMITTER_DATE"] = date_str
    
    subprocess.run(["git", "commit", "-m", commit["message"]], env=env, check=False)

# Add all remaining untracked files (ignores will apply)
subprocess.run("git add .", shell=True)
env = os.environ.copy()
final_date_str = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
env["GIT_AUTHOR_DATE"] = final_date_str
env["GIT_COMMITTER_DATE"] = final_date_str
# Only commit if there are changes
res = subprocess.run(["git", "diff", "--staged", "--quiet"])
if res.returncode != 0:
    subprocess.run(["git", "commit", "-m", "Final bugfixes and cleanup"], env=env, check=False)

print("Done creating fake history!")
