```markdown
# mi-notes Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill provides guidance for contributing to the **mi-notes** Java codebase, a note-taking application with support for multiple sync providers and localization. You'll learn the project's coding conventions, how to add or enhance sync features, update localizations, and understand the repository's workflow and testing patterns.

## Coding Conventions

- **File Naming:**  
  Java source files use **PascalCase** (e.g., `NoteEditActivity.java`, `WebDavSyncProvider.java`).

- **Import Style:**  
  Relative imports are used within the Java package structure.
  ```java
  import net.micode.notes.sync.WebDavSyncProvider;
  ```

- **Export Style:**  
  Named exports via Java's `public` classes and methods.
  ```java
  public class NoteEditActivity extends Activity {
      // ...
  }
  ```

- **String Resources:**  
  User-facing strings are placed in `strings.xml` files under various language-specific directories.

- **Commit Messages:**  
  - Freeform style, sometimes prefixed with `fix`
  - Average length: ~34 characters

## Workflows

### Add or Enhance Sync Feature
**Trigger:** When adding a new sync provider (e.g., WebDAV) or significantly enhancing sync functionality  
**Command:** `/add-sync-provider`

1. **Create or update sync provider classes:**  
   Add new Java classes under  
   `app/src/main/java/net/micode/notes/sync/[provider]/`
   ```java
   // Example: WebDavSyncProvider.java
   package net.micode.notes.sync.webdav;

   public class WebDavSyncProvider implements SyncProvider {
       // Implementation...
   }
   ```
2. **Update AndroidManifest:**  
   Register new services or permissions in  
   `app/src/main/AndroidManifest.xml`
   ```xml
   <service android:name=".sync.webdav.WebDavSyncService" />
   ```
3. **Modify UI activities:**  
   Update classes like `NoteEditActivity`, `NotesPreferenceActivity`, and `NotesListActivity` to integrate the new sync feature.
   ```java
   // Example: Add sync option to preferences
   if (isWebDavEnabled()) {
       // Show WebDAV sync option
   }
   ```
4. **Update string resources:**  
   Add or update user-facing strings in  
   `app/src/main/values/strings.xml` and `res/values/strings.xml`
   ```xml
   <string name="sync_webdav">WebDAV Sync</string>
   ```
5. **Update translations (if needed):**  
   Add or update corresponding translations in:
   - `app/src/main/values-zh-rCN/strings.xml`
   - `app/src/main/values-zh-rTW/strings.xml`
   - `res/values-zh-rCN/strings.xml`
   - `res/values-zh-rTW/strings.xml`
6. **Update layout files:**  
   Modify or create XML layouts (e.g., `res/layout/activity_preferences.xml`) to reflect new sync options.

### Localization Update for New Feature
**Trigger:** When new or updated user-facing strings require translation  
**Command:** `/translate-strings`

1. **Identify new or changed strings:**  
   Check `app/src/main/values/strings.xml` and `res/values/strings.xml` for updates.
2. **Update translations:**  
   Add or update translations in:
   - `app/src/main/values-zh-rCN/strings.xml`
   - `app/src/main/values-zh-rTW/strings.xml`
   - `res/values-zh-rCN/strings.xml`
   - `res/values-zh-rTW/strings.xml`
   ```xml
   <!-- Example: Chinese (Simplified) -->
   <string name="sync_webdav">WebDAV 同步</string>
   ```

## Testing Patterns

- **Framework:** Unknown (not explicitly detected)
- **File Pattern:** Test files match `*.test.*`
- **Location:** Typically alongside source files or in dedicated test directories
- **Example:**
  ```java
  // NoteEditActivity.test.java
  public class NoteEditActivityTest {
      // Test methods...
  }
  ```

## Commands

| Command              | Purpose                                           |
|----------------------|--------------------------------------------------|
| /add-sync-provider   | Scaffold and integrate a new sync provider       |
| /translate-strings   | Update or add translations for string resources  |
```
