# AGENTS.md

This file provides additional guidance for code agents working in this repository. For build commands, architecture overview, resource layout, and testing, see [CLAUDE.md](../CLAUDE.md).

## Project Snapshot

- Legacy Android Java notes app (`net.micode.notes`) with one active Gradle module: `app`.
- Canonical Java and manifest source tree is `app/src/main/**`.
- Runtime Android resources are configured from root-level `res/` via `app/build.gradle`.
- Current roadmap and migration notes live in `DEVELOPMENT_PLAN.md` and `MIGRATION_VERIFICATION.md`.

## Core Data Flow and Invariants

- Create/edit flow: `NotesListActivity` -> `NoteEditActivity` -> `WorkingNote.saveNote()` -> `Note.syncNote()` -> `NotesProvider`.
- List read flow: `NotesListActivity` -> `NotesViewModel` -> `NotesRepository` -> Room read model refreshed from `NotesProvider`.
- WebDAV import/export flow: `WebDavSyncManager` -> `NotesProvider`; do not silently change WebDAV conflict, backup, or snapshot semantics.
- `NotesProvider.update()` increments `NoteColumns.VERSION` before note updates; do not bypass provider with direct DB writes.
- System folders are special IDs in `data/Notes.java`: root `0`, temporary `-1`, call-record `-2`, trash `-3`.
- Delete is often a move to trash (`PARENT_ID = ID_TRASH_FOLER`); hard delete is batch-delete via `DataUtils.batchDeleteNotes`.
- Folder counts and snippet sync are DB-trigger driven; schema changes must preserve trigger behavior.
- Room read model migration must leave legacy `note.db` intact and retryable on failure.

## Project-Specific Coding Conventions

- Reuse constants from `data/Notes.java` for URIs, MIME types, extras, and folder IDs; avoid hardcoded strings.
- Persist note edits through `WorkingNote` / `Note` during the provider-authoritative stage.
- Route list-facing provider/Room compatibility through `NotesRepository`; avoid adding scattered direct Room writes.
- Batch note operations use `ContentProviderOperation` helpers (`DataUtils.batchMoveToFolder`, `batchDeleteNotes`) instead of per-row loops.
- Search behavior is provider-backed (`NotesProvider` raw query over snippet) and wired to `xml/searchable.xml` + `NoteEditActivity` search intent handling.
- Runtime resources are under root `res/`; update root resources first.

## Integrations and Gotchas

- WebDAV sync supports folder URLs, direct JSON URLs, Chinese paths/filenames, and predictable `.backup.json` snapshots. Snapshot schema details in [SNAPSHOT_SCHEMA.md](SNAPSHOT_SCHEMA.md).
- Google Tasks sync uses old account/auth flow (`AccountManager` token type `goanna_mobile`) and legacy endpoints in `GTaskClient`; treat it as legacy unless explicitly modernizing it.
- Sync account changes in `NotesPreferenceActivity` intentionally clear local `GTASK_ID` and `SYNC_ID` for all notes.
- Alarm reminders are re-scheduled on boot by `AlarmInitReceiver`; alert note IDs are encoded in `PendingIntent` data URI.
- Widgets and note editor are tightly coupled through intent extras (`INTENT_EXTRA_WIDGET_ID`, `INTENT_EXTRA_WIDGET_TYPE`, `INTENT_EXTRA_BACKGROUND_ID`).
