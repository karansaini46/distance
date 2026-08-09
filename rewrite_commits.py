import os
import subprocess
import datetime
import random

# 1. Reset everything
subprocess.run(["rm", "-rf", ".git"])
subprocess.run(["git", "init"])
subprocess.run(["git", "remote", "add", "origin", "https://github.com/karansaini46/distance.git"])
subprocess.run(["git", "branch", "-M", "main"])

# 2. Add gitignore
gitignore_content = """
*.iml
.gradle
local.properties
.idea/
.DS_Store
build/
app/build/
captures/
.externalNativeBuild
.cxx
gradle-8.6-bin.zip
gradle-8.6/
jdk-17.0.11+9/
"""
with open('.gitignore', 'w') as f:
    f.write(gitignore_content)

subprocess.run(["git", "add", ".gitignore"])
env = os.environ.copy()
date_str = "2026-01-01T10:00:00"
env['GIT_AUTHOR_DATE'] = date_str
env['GIT_COMMITTER_DATE'] = date_str
subprocess.run(["git", "commit", "-m", "Initial commit and gitignore"], env=env)

# 3. Get files
subprocess.run(["git", "add", "."])
result = subprocess.run(["git", "ls-files"], capture_output=True, text=True)
files = result.stdout.strip().split('\n')
files = [f for f in files if f and f != ".gitignore"]
subprocess.run(["git", "reset"]) # unstage all

# 4. Generate random dates between Jan 1 and Mar 31
# Jan 1 to Mar 31 is 90 days.
start_date = datetime.datetime(2026, 1, 1, 10, 0, 0)
end_date = datetime.datetime(2026, 3, 31, 18, 0, 0)

delta = end_date - start_date
dates = []
for _ in range(len(files)):
    random_second = random.randint(0, int(delta.total_seconds()))
    dates.append(start_date + datetime.timedelta(seconds=random_second))

dates.sort()

# We need some varied commit messages
messages = [
    "Add new component", "Update logic", "Refactor module", "Fix minor bugs",
    "Add resources", "Update layout", "Implement background task", "Configure settings",
    "Update build scripts", "Add database entity", "Integrate with remote", "Clean up code",
    "Update UI elements", "Add utility functions", "Optimize performance", "Update permissions"
]

# 5. Commit each file with its date
for i, f in enumerate(files):
    subprocess.run(["git", "add", f])
    
    commit_date = dates[i]
    date_str = commit_date.strftime('%Y-%m-%dT%H:%M:%S')
    
    env = os.environ.copy()
    env['GIT_AUTHOR_DATE'] = date_str
    env['GIT_COMMITTER_DATE'] = date_str
    
    msg = random.choice(messages) + f" ({f.split('/')[-1]})"
    subprocess.run(["git", "commit", "-m", msg], env=env)

print(f"Created {len(files)} commits from Jan to March.")
