# AGENTS.md

## Project Snapshot
- Legacy Android Java notes app (`net.micode.notes`) with a single active Gradle module: `app`.
- Canonical source tree is `app/src/main/**` (contains manifest, java, resources, xml).
- Root-level `src/`, `res/`, and `AndroidManifest.xml` mirror older layout; prefer editing `app/src/main/**` unless you are intentionally syncing both.
- No existing AI instruction files were found by glob search; historical project context is in `README` (no `.md` extension).

## Architecture (Read This First)
- UI layer: `ui/` (`NotesListActivity`, `NoteEditActivity`, `NotesPreferenceActivity`, alarm UIs).
- Model layer: `model/WorkingNote.java` orchestrates editing state and persistence decisions; `model/Note.java` performs diff-style writes.
- Data boundary: all persistence flows through `data/NotesProvider.java` (`content://micode_notes/...`).
- Storage: SQLite in `data/NotesDatabaseHelper.java` with triggers enforcing folder counts, note snippets, and cascaded deletes/moves.
- External sync boundary: `gtask/remote/` (`GTaskSyncService`, `GTaskManager`, `GTaskClient`) handles Google Tasks sync.
- Widget boundary: `widget/NoteWidgetProvider*.java` reads notes by widget id and routes intents into `NoteEditActivity`.

## Core Data Flow and Invariants
- Create/edit flow: `NotesListActivity` -> `NoteEditActivity` -> `WorkingNote.saveNote()` -> `Note.syncNote()` -> `NotesProvider`.
- `NotesProvider.update()` increments `NoteColumns.VERSION` before note updates; do not bypass provider with direct DB writes.
- System folders are special IDs in `data/Notes.java`: root `0`, temporary `-1`, call-record `-2`, trash `-3`.
- "Delete" is often a move to trash (`PARENT_ID = ID_TRASH_FOLER`); hard delete is batch-delete via `DataUtils.batchDeleteNotes`.
- Folder counts and snippet sync are DB-trigger driven (`NotesDatabaseHelper`), so schema changes must update trigger logic too.

## Build and Developer Workflow
- `gradlew`/`gradlew.bat` is not present in this repo; use Android Studio Gradle integration or local `gradle`.
- Primary module is `:app`; typical commands from project root:

```powershell
gradle :app:assembleDebug
gradle :app:installDebug
gradle :app:lintDebug
gradle clean
```

- No automated unit/instrumentation tests were found (`*Test*.java` absent).

## Project-Specific Coding Conventions
- Reuse constants from `data/Notes.java` for URIs, MIME types, extras, and folder IDs; avoid hardcoded strings.
- Persist note edits through `WorkingNote`/`Note` (keeps `LOCAL_MODIFIED` and `MODIFIED_DATE` behavior consistent).
- Batch note operations use `ContentProviderOperation` (`DataUtils.batchMoveToFolder`, `batchDeleteNotes`) instead of per-row loops.
- Search behavior is provider-backed (`NotesProvider` raw query over snippet) and wired to `xml/searchable.xml` + `NoteEditActivity` search intent handling.
- UI resources are legacy-located under `app/src/main/layout`, `app/src/main/drawable*`, `app/src/main/values*` (not `res/...`).

## Integrations and Gotchas
- Google Tasks sync uses old account/auth flow (`AccountManager` token type `goanna_mobile`) and legacy endpoints in `GTaskClient`; avoid broad refactors without end-to-end device validation.
- Sync account changes in `NotesPreferenceActivity` intentionally clear local `GTASK_ID` and `SYNC_ID` for all notes.
- Alarm reminders are re-scheduled on boot by `AlarmInitReceiver`; alert note IDs are encoded in `PendingIntent` data URI.
- Widgets and note editor are tightly coupled through intent extras (`INTENT_EXTRA_WIDGET_ID`, `INTENT_EXTRA_WIDGET_TYPE`, `INTENT_EXTRA_BACKGROUND_ID`).

