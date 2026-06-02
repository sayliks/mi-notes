# AGENTS.md

## Project Snapshot

- Legacy Android Java notes app (`net.micode.notes`) with one active Gradle module: `app`.
- Canonical Java and manifest source tree is `app/src/main/**`.
- Runtime Android resources are configured from root-level `res/` via `app/build.gradle`.
- Some mirrored `app/src/main/values*` resources may exist; keep mirrors synchronized only when a patch already touches those files.
- Current roadmap and migration notes live in `DEVELOPMENT_PLAN.md` and `MIGRATION_VERIFICATION.md`.

## Architecture (Read This First)

- UI layer: `ui/` (`NotesListActivity`, `NoteEditActivity`, `NotesPreferenceActivity`, alarm UIs).
- Model layer: `model/WorkingNote.java` orchestrates editing state and persistence decisions; `model/Note.java` performs diff-style provider writes.
- Provider boundary: `data/NotesProvider.java` (`content://micode_notes/...`) remains the authoritative persistence boundary during migration.
- Storage: SQLite in `data/NotesDatabaseHelper.java`, with triggers enforcing folder counts, snippets, cascaded deletes/moves, and trash behavior.
- Room boundary: `data/repository/NotesRepository.java` mirrors provider data into Room for list UI; Room is not authoritative yet.
- WebDAV sync boundary: `sync/webdav/` is the current supported sync path.
- Legacy Google Tasks boundary: `gtask/remote/` is retained for compatibility/history and should not be broadly refactored without explicit scope.
- Widget boundary: `widget/NoteWidgetProvider*.java` reads notes by widget id and routes intents into `NoteEditActivity`.

## Core Data Flow and Invariants

- Create/edit flow: `NotesListActivity` -> `NoteEditActivity` -> `WorkingNote.saveNote()` -> `Note.syncNote()` -> `NotesProvider`.
- List read flow: `NotesListActivity` -> `NotesViewModel` -> `NotesRepository` -> Room read model refreshed from `NotesProvider`.
- WebDAV import/export flow: `WebDavSyncManager` -> `NotesProvider`; do not silently change WebDAV conflict, backup, or snapshot semantics.
- `NotesProvider.update()` increments `NoteColumns.VERSION` before note updates; do not bypass provider with direct DB writes.
- System folders are special IDs in `data/Notes.java`: root `0`, temporary `-1`, call-record `-2`, trash `-3`.
- Delete is often a move to trash (`PARENT_ID = ID_TRASH_FOLER`); hard delete is batch-delete via `DataUtils.batchDeleteNotes`.
- Folder counts and snippet sync are DB-trigger driven; schema changes must preserve trigger behavior.
- Room read model migration must leave legacy `note.db` intact and retryable on failure.

## Build and Developer Workflow

Primary module is `:app`; typical commands from project root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.sync.webdav.*
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.data.repository.*
.\gradlew.bat clean
```

Existing local unit tests cover WebDAV behavior and migration validation. Instrumentation coverage is still limited; use `MIGRATION_VERIFICATION.md` for manual acceptance cases.

## Project-Specific Coding Conventions

- Reuse constants from `data/Notes.java` for URIs, MIME types, extras, and folder IDs; avoid hardcoded strings.
- Persist note edits through `WorkingNote` / `Note` during the provider-authoritative stage.
- Route list-facing provider/Room compatibility through `NotesRepository`; avoid adding scattered direct Room writes.
- Batch note operations use `ContentProviderOperation` helpers (`DataUtils.batchMoveToFolder`, `batchDeleteNotes`) instead of per-row loops.
- Search behavior is provider-backed (`NotesProvider` raw query over snippet) and wired to `xml/searchable.xml` + `NoteEditActivity` search intent handling.
- Runtime resources are under root `res/`; update root resources first.

## Integrations and Gotchas

- WebDAV sync supports folder URLs, direct JSON URLs, Chinese paths/filenames, and predictable `.backup.json` snapshots.
- Google Tasks sync uses old account/auth flow (`AccountManager` token type `goanna_mobile`) and legacy endpoints in `GTaskClient`; treat it as legacy unless explicitly modernizing it.
- Sync account changes in `NotesPreferenceActivity` intentionally clear local `GTASK_ID` and `SYNC_ID` for all notes.
- Alarm reminders are re-scheduled on boot by `AlarmInitReceiver`; alert note IDs are encoded in `PendingIntent` data URI.
- Widgets and note editor are tightly coupled through intent extras (`INTENT_EXTRA_WIDGET_ID`, `INTENT_EXTRA_WIDGET_TYPE`, `INTENT_EXTRA_BACKGROUND_ID`).
