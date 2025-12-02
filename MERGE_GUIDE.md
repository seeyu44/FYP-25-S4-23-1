# Step-by-Step Guide: Merging Master into Test Branch

## Prerequisites
- Git installed on your local machine
- Access to the repository with proper permissions
- Local clone of the repository

## Step 1: Prepare Your Local Environment

```bash
# Clone the repository (if you haven't already)
git clone https://github.com/seeyu44/FYP-25-S4-23-1.git
cd FYP-25-S4-23-1

# Fetch all branches
git fetch origin

# Verify you can see both branches
git branch -a
```

## Step 2: Checkout the Test Branch

```bash
# Switch to the test branch (this will be your base branch)
git checkout test

# Make sure you have the latest changes
git pull origin test
```

## Step 3: Merge Master into Test

```bash
# Merge master branch into test
git merge origin/master --no-ff -m "Merge master into test"
```

### What to Expect:
- **If no conflicts**: The merge will complete automatically. Skip to Step 5.
- **If conflicts occur**: Git will pause and show you which files have conflicts. Continue to Step 4.

## Step 4: Resolve Conflicts (If Any)

When conflicts occur, Git will mark the conflicting sections in your files:

```
<<<<<<< HEAD
// Code from test branch
=======
// Code from master branch
>>>>>>> origin/master
```

### Common Conflict Areas (Based on Analysis):

1. **MainActivity.kt** - Both branches modified this file
2. **Audio/ML components** - New features in master
3. **DAO files** - CallRecordDao added in test

### Resolution Strategy:

For each conflicting file:

```bash
# Open the file in your editor
# Look for conflict markers (<<<<<<< HEAD)

# For MainActivity.kt:
# - Keep both sets of changes if they affect different parts
# - If same method modified, favor master for audio-related code
# - Favor test for call history/dashboard code

# For Audio/ML files:
# - Generally favor master branch changes (newer audio features)

# For CallRecordDao and database files:
# - Generally favor test branch changes (your call history feature)
```

After resolving conflicts in each file:

```bash
# Mark file as resolved
git add path/to/conflicted/file

# Check remaining conflicts
git status
```

## Step 5: Test the Merge

Before committing, test that everything works:

### Test Kotlin/Android Project:
```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test
```

### Test Python ML Components (if applicable):
```bash
cd ml/training
pip install -r requirements.txt
# Run any test scripts if they exist
```

### What to Look For:
- ✅ Build completes without errors
- ✅ All tests pass
- ✅ App runs without crashes
- ✅ Audio features work (from master)
- ✅ Call history works (from test)

## Step 6: Complete the Merge

```bash
# If all conflicts are resolved and tests pass
git commit -m "Merge master into test - resolved conflicts"

# Push to test branch
git push origin test
```

## Step 7: Verify the Merge

```bash
# Check the merge commit
git log --oneline -5

# Verify both features are present
git log --graph --oneline --all | head -20
```

## Quick Reference: Conflict Resolution Commands

```bash
# Check which files have conflicts
git status

# Accept all changes from master for a specific file
git checkout --theirs path/to/file
git add path/to/file

# Accept all changes from test for a specific file
git checkout --ours path/to/file
git add path/to/file

# Edit manually and then mark as resolved
git add path/to/file

# Abort merge if needed (start over)
git merge --abort
```

## Important Notes

1. **Test Thoroughly**: After merging, test both the audio deepfake features (from master) and call history features (from test)

2. **Branch Differences**:
   - **Master**: 10+ commits with audio deepfake detection, audio picker, ML model updates
   - **Test**: 15+ commits with call history (UC#32), dashboard, daily/weekly summaries

3. **Backup**: Consider creating a backup branch before merging:
   ```bash
   git checkout test
   git checkout -b test-backup
   git checkout test
   ```

4. **Get Help**: If you're stuck on a specific conflict, you can:
   - View the full file from master: `git show origin/master:path/to/file`
   - View the full file from test: `git show origin/test:path/to/file`
   - Compare the files side-by-side in your editor

## Troubleshooting

### "I have too many conflicts"
- Consider using a merge tool: `git mergetool`
- Or use a GUI tool like GitKraken, SourceTree, or VS Code's built-in Git support

### "Build fails after merge"
- Check if dependencies need updating in `build.gradle.kts`
- Ensure all import statements are correct
- Look for duplicate method definitions

### "Need to undo the merge"
```bash
# If you haven't pushed yet
git merge --abort  # (if merge in progress)
# OR
git reset --hard HEAD~1  # (if already committed)

# If you already pushed
git revert -m 1 HEAD
git push origin test
```

## Summary

The key steps are:
1. ✅ Checkout test branch
2. ✅ Merge master into test
3. ✅ Resolve any conflicts (favor master for audio, test for call history)
4. ✅ Test thoroughly (build + run tests)
5. ✅ Commit and push

This will combine Zavier's audio deepfake detection features (master) with your call history and dashboard features (test).
