# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK (ProGuard enabled but minifyEnabled=false)
./gradlew assembleRelease

# Clean build outputs
./gradlew clean

# Check dependencies (no lint/tests configured)
./gradlew app:dependencies
```

Gradle 9.5.1, AGP 9.0.0, Java 8, Groovy DSL. Single module `:app`. Aliyun mirrors in settings.gradle for China-based development.

No test infrastructure exists — no `src/test/`, no test dependencies. No CI/CD config.

## Project Identity

MiCode Notes — open-source Android note-taking app (Apache 2.0). Package: `net.micode.notes`. Legacy pre-Jetpack architecture. App name: "Notes" (appears as 小米便签 in Chinese localization).

## Resource Layout (Non-Standard)

Resources live at **repo root `res/`**, not under `app/src/main/res/`. Configured via `sourceSets { main { res.srcDirs = ['../res'] } }` in `app/build.gradle:14`. All drawables are in `drawable-hdpi` only — no other density buckets. Locales: English (default), zh-rCN, zh-rTW.

## Architecture: ContentProvider + SQLite (No ViewModel)

All data access goes through `NotesProvider` (authority: `micode_notes`), a `ContentProvider` with `multiprocess=true`. No Room, no Repository pattern.

### Two-Table Database (`note.db`, version 4)

Defined in `app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java`.

| Table | Purpose |
|-------|---------|
| `note` | Note/folder metadata (parent_id, type, snippet, bg_color_id, sync fields) |
| `data` | Actual content rows with MIME type discrimination (`text_note`, `call_note`) |

Modeled after Android ContactsContract: each note is a `note` row with child `data` rows. Content types live in `Notes.java` as constants.

**12 SQLite triggers** maintain integrity: auto-updating folder note counts, note snippets from data changes, cascade deletes, trash moves, sync version bumps.

### System Folders (Negative IDs)

Four special folders identified by negative IDs in `Notes.java:33-36`:
- `0` = Root folder (default)
- `-1` = Temporary folder (notes with no folder assignment)
- `-2` = Call Record folder
- `-3` = Trash folder

### Content URIs

```
content://micode_notes/note        — all notes/folders
content://micode_notes/data        — all data rows
content://micode_notes/text_note   — text note data
content://micode_notes/call_note   — call note data
```

## Package Layout

```
net.micode.notes/
  data/       — Notes.java (constants/URIs), NotesProvider, NotesDatabaseHelper, Contact
  model/      — Note (CRUD via ContentValues), WorkingNote (editing session)
  ui/         — Activities, adapters, custom views, alarm receivers
  widget/     — 2x2 and 4x4 AppWidgetProviders
  tool/       — BackupUtils, DataUtils, GTaskStringUtils, ResourceParser
  gtask/
    data/     — Node/Task/TaskList/MetaData/SqlNote/SqlData (sync model)
    exception/— ActionFailureException, NetworkFailureException
    remote/   — GTaskClient (Apache HttpClient), GTaskManager, GTaskSyncService, GTaskASyncTask
```

## UI Flow

- `NotesListActivity` — launcher activity, shows note list via `ListView` + `CursorAdapter`. FAB creates new note.
- `NoteEditActivity` — note editor with custom `NoteEditText`. Handles checklists, color themes, alarms. Registered for VIEW/INSERT_OR_EDIT/SEARCH intents.
- `AlarmAlertActivity` — singleInstance popup for note alarms, runs in `:remote` process.
- `NotesPreferenceActivity` — settings (sync account picker, random background toggle).

## Google Tasks Sync

Legacy sync implementation using Apache HttpClient (`org.apache.http.legacy`). Auth via Android `AccountManager`. Sync is bidirectional with version columns for conflict detection.

Key classes:
- `GTaskClient` — HTTP client for Google Tasks REST API JSON
- `GTaskManager` — sync logic orchestrator
- `GTaskSyncService` — background `Service` for sync
- `GTaskASyncTask` — `AsyncTask` with notification progress

## Permissions

`INTERNET`, `WRITE_EXTERNAL_STORAGE`, `READ_CONTACTS`, `RECEIVE_BOOT_COMPLETED`, `INSTALL_SHORTCUT`, and 4 Google account permissions (`MANAGE_ACCOUNTS`, `AUTHENTICATE_ACCOUNTS`, `GET_ACCOUNTS`, `USE_CREDENTIALS`).

## Five Color Themes

YELLOW (default), BLUE, WHITE, GREEN, RED. Separate drawable resources for list items (up/middle/down/single segments), widgets (2x/4x), and editor. `ResourceParser.java` maps background IDs to resources.

## Checklist Mode

Text notes support checklist mode. Content split by `\n` into items, prefixed with `\u221A` (✓) or `\u25A1` (□). Controlled by `TextNote.MODE = DATA1`, value 1 = checklist mode.
