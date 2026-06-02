# CLAUDE.md

This file provides guidance for Claude Code when working in this repository.

## Build Commands

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.sync.webdav.*
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.data.repository.*
.\gradlew.bat clean
```

The active module is `:app`. Gradle uses Android Gradle Plugin 9.x, Java 8 bytecode, Kotlin plugin/kapt, and a Groovy DSL.

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
- `AlarmAlertActivity` and `AlarmInitReceiver` continue to read notes through provider-backed utilities.
- `NoteWidgetProvider*` reads notes by widget id through provider queries.

## Sync

WebDAV is the current supported sync direction:

- folder URLs and direct JSON snapshot URLs are supported
- Chinese paths and snapshot filenames are supported
- already encoded paths must not be double-encoded
- `.backup.json` snapshots are written before local import or remote overwrite
- provider import/export remains the WebDAV storage boundary

Legacy Google Tasks code remains under `gtask/` and should be treated as historical compatibility code unless a task explicitly modernizes it with current OAuth/REST behavior.

## Testing

Current local unit tests cover WebDAV URL/snapshot safety and migration validation. Add focused tests for any change that affects deletion, import/export, migration, conflict handling, or backup behavior.

Manual migration acceptance cases live in [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md).
