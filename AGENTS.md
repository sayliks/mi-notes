# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build Commands

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.sync.webdav.*
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.data.repository.*
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.data.NotesProviderContractTest
.\gradlew.bat clean
```

The active module is `:app`. Gradle uses Android Gradle Plugin 9.2.1, Kotlin 2.2.10, Java 8 bytecode, and a Groovy DSL. `compileSdk 36`, `minSdk 21`, `targetSdk 36`. Core library desugaring is enabled. Repositories include Aliyun mirrors for Chinese network environments.

## Project Identity

MiCode Notes is an Apache 2.0 Android note-taking app. Package: `net.micode.notes`. App name appears as "Notes" in English and 小米便签 in Chinese localization.

## Resource Layout

The active Android resources are rooted at repository-level `res/`, configured by `app/build.gradle`:

```groovy
sourceSets {
    main {
        res.srcDirs = ['../res']
    }
}
```

Some mirrored resources may exist under `app/src/main/values*`; keep mirrors in sync only when the surrounding patch already touches them. The canonical runtime resource tree is `res/`.

## Architecture

### Migration Boundary

The current migration stage is provider-authoritative:

- `NotesProvider` and legacy `note.db` remain the source of truth.
- `NotesRepository` is the compatibility layer between provider and Room.
- Room is a background-refreshed read model for the RecyclerView list.
- Editing, WebDAV sync, widgets, alarms, and search still depend on provider semantics.

Do not write directly to Room for user data unless the change explicitly updates the repository contract and preserves provider compatibility.

### Legacy Database

`NotesDatabaseHelper` owns `note.db`, version 4. The schema has two main tables:

| Table | Purpose |
|---|---|
| `note` | Note/folder metadata: parent, type, snippet, color, sync fields, versions. |
| `data` | Content rows keyed by MIME type, including text notes and call notes. |

SQLite triggers maintain folder counts, snippets, cascade deletes, trash moves, and content synchronization. Provider updates also maintain `VERSION` and `LOCAL_MODIFIED` behavior. Avoid bypassing these invariants.

System folder IDs from `data/Notes.java`: root `0`, temporary `-1`, call-record `-2`, trash `-3`. Delete is often a move to trash (`PARENT_ID = ID_TRASH_FOLER`); hard delete uses `DataUtils.batchDeleteNotes`.

### Room Read Model

Room entities and DAO live under:

- `app/src/main/java/net/micode/notes/data/entity/`
- `app/src/main/java/net/micode/notes/data/dao/`
- `app/src/main/java/net/micode/notes/data/database/`

`NotesRepository` rebuilds the Room read model from `NotesProvider` in the background and records migration validation metadata only after successful validation. The legacy database is not deleted, renamed, or overwritten during this stage.

## UI Flow

- `NotesListActivity` shows the RecyclerView list and delegates list data/mutations through `NotesViewModel` -> `NotesRepository`.
- `NoteEditActivity` uses legacy `WorkingNote` / `Note` so edits continue through `NotesProvider`.
- `NotesPreferenceActivity` owns sync settings and WebDAV configuration.
- `AlarmReceiver`, `AlarmAlertActivity`, and `AlarmInitReceiver` continue to use provider note ids and provider-backed utilities. `AlarmReceiver` runs in a remote process and must not access Room directly. Alarm `PendingIntent` identity uses data URI `content://micode_notes/note/<NOTE_ID>`, not requestCode. `AlarmScheduler` is the single scheduling boundary.
- `NoteWidgetProvider*` reads notes by widget id through provider queries.

## Sync

WebDAV is the current supported sync direction:

- folder URLs and direct JSON snapshot URLs are supported
- Chinese paths and snapshot filenames are supported
- already encoded paths must not be double-encoded
- `.backup.json` snapshots are written before local import or remote overwrite
- provider import/export remains the WebDAV storage boundary
- snapshot schema details and backward compatibility policy: [SNAPSHOT_SCHEMA.md](docs/SNAPSHOT_SCHEMA.md)

Legacy Google Tasks code remains under `gtask/` and should be treated as historical compatibility code unless a task explicitly modernizes it with current OAuth/REST behavior.

## Testing

Current local unit tests cover WebDAV URL/snapshot safety, migration validation, and provider contract (schema, triggers, folder counts, snippet sync, cascade deletes). Add focused tests for any change that affects deletion, import/export, migration, conflict handling, or backup behavior.

Release manifest must not contain debug-only alarm triggers; verify with:

```powershell
.\gradlew.bat :app:verifyReleaseManifestNoDebugAlarm
```

Debug-only alarm trigger (debug source set only):

```powershell
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver --el android.intent.extra.UID <NOTE_ID>
```

Manual migration acceptance cases live in [MIGRATION_VERIFICATION.md](docs/MIGRATION_VERIFICATION.md).
Alarm/widget code boundaries and debug trigger commands live in [ALARM_WIDGET_VERIFICATION.md](docs/ALARM_WIDGET_VERIFICATION.md).
Snapshot schema and backward compatibility policy live in [SNAPSHOT_SCHEMA.md](docs/SNAPSHOT_SCHEMA.md).
