# WebDAV Sync Snapshot Schema

This document describes the JSON format used by the WebDAV sync snapshot. The schema is owned by `WebDavSyncManager` in `app/src/main/java/net/micode/notes/sync/webdav/`.

## Top-Level Structure

```json
{
  "version": 1,
  "generated_at": 1718000000000,
  "notes": [ ... ],
  "data": [ ... ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `version` | int | Yes | Schema version. Must be `1`. Validated on import; mismatched or missing versions are rejected. |
| `generated_at` | long | No | Epoch millis when the snapshot was exported. Used for sync decision logic (comparing remote vs local freshness). Not validated; defaults to `0` if missing. |
| `notes` | array | Yes | All note/folder metadata rows. Validated on import. |
| `data` | array | Yes | All content data rows. Validated on import. |

Validation is in `WebDavSyncManager.validateSnapshot()`: `version` must equal `1`, and both `notes` and `data` keys must be present.

## Notes Array

Each element corresponds to a row in the `note` table. Fields are serialized from `NoteColumns` constants defined in `data/Notes.java`.

| JSON Key | Column | Type | Description |
|---|---|---|---|
| `_id` | `_id` | long | Primary key. Positive for user notes/folders; negative for system folders. Exports skip `_id <= 0` (system folders are not exported). |
| `parent_id` | `parent_id` | long | Parent folder ID. `0` = root, `-3` = trash. |
| `alert_date` | `alert_date` | long | Reminder timestamp in epoch millis. `0` = no reminder. |
| `bg_color_id` | `bg_color_id` | int | Background color theme index. |
| `created_date` | `created_date` | long | Creation time in epoch millis. Defaults to `now` on insert. |
| `has_attachment` | `has_attachment` | int | `0` or `1`. |
| `modified_date` | `modified_date` | long | Last modification time in epoch millis. Defaults to `now` on insert. |
| `snippet` | `snippet` | String | Text preview. Auto-maintained by DB triggers when `data` rows of type `NOTE` change. |
| `type` | `type` | int | `0` = note, `1` = folder, `2` = system folder. |
| `widget_id` | `widget_id` | int | Bound Android widget ID. `0` = none. Reset to `INVALID_APPWIDGET_ID` when widget is deleted. |
| `widget_type` | `widget_type` | int | Widget size type. `-1` = none. |
| `sync_id` | `sync_id` | long | Legacy Google Tasks sync ID. `0` = unsynced. |
| `local_modified` | `local_modified` | int | Dirty flag. `1` = modified locally since last sync. Reset to `0` after import. |
| `origin_parent_id` | `origin_parent_id` | long | Original parent before temporary folder move. `0` = no temporary reassignment. |
| `gtask_id` | `gtask_id` | String | Legacy Google Tasks task ID. Empty string = none. |
| `version` | `version` | long | Note version counter. Incremented by `NotesProvider.update()`. |

**Import behavior:** `local_modified` is forced to `0` during import (line 782). `NOTES_COUNT` is not in the snapshot; it is reset to `0` for all folders after import and re-counted by DB triggers as notes are inserted.

## Data Array

Each element corresponds to a row in the `data` table. Fields are serialized from `DataColumns` constants defined in `data/Notes.java`.

| JSON Key | Column | Type | Description |
|---|---|---|---|
| `_id` | `_id` | long | Primary key. |
| `mime_type` | `mime_type` | String | Content type. `"vnd.android.cursor.item/text_note"` for text/checklist notes, `"vnd.android.cursor.item/call_note"` for call records. |
| `note_id` | `note_id` | long | Foreign key to `note._id`. Only data rows whose `note_id` matches an exported note are included. |
| `created_date` | `created_date` | long | Creation time in epoch millis. |
| `modified_date` | `modified_date` | long | Last modification time in epoch millis. |
| `content` | `content` | String | Full text content. For text notes, this is the note body. |
| `data1` | `data1` | long | Generic integer. For text notes: checklist mode (`0` = plain, `1` = checklist). For call notes: call date (epoch millis). |
| `data2` | `data2` | long | Generic integer. |
| `data3` | `data3` | String | Generic text. For call notes: phone number. |
| `data4` | `data4` | String | Generic text. |
| `data5` | `data5` | String | Generic text. |

**Export filtering:** Only data rows whose `note_id` is in the set of exported note IDs are included. This means trash notes' data rows are excluded by default (since trash notes are excluded from export unless `includeTrash` is set).

## Snapshot File Names

| Context | File Name |
|---|---|
| Folder URL (e.g. `https://example.com/dav/notes/`) | `mi-notes-sync.json` |
| Direct JSON URL (e.g. `https://example.com/dav/小米便签同步.json`) | User-configured filename |

## Backup File Naming

Backup files use `.backup.json` suffix, written in the same directory as the main snapshot:

| Context | Backup File Name |
|---|---|
| Folder URL | `mi-notes-sync.backup.json` |
| Direct JSON URL `小米便签同步.json` | `小米便签同步.backup.json` |

The backup is written **before** the main snapshot PUT. If the backup write fails, the entire sync aborts with `SnapshotBackupException`. The backup is a verbatim copy of the previous snapshot (either the existing remote snapshot before overwrite, or the current local snapshot before import).

## Backward Compatibility Policy

- **Current version:** `1`. This is the only valid version.
- **Future versions:** When a new version is introduced, the import code must handle version negotiation. Older clients that only understand version `1` will reject newer snapshots. Newer clients should be able to read version `1` snapshots.
- **Field additions:** Adding new optional fields to an existing version is backward-compatible (older clients use `opt*` getters with defaults). Removing or renaming fields is a breaking change that requires a version bump.
- **`NOTES_COUNT`:** Intentionally excluded from the snapshot. It is a derived value maintained by DB triggers and reset on import. Including it would create a consistency risk.

## What Is NOT in the Snapshot

- **System folders** (`_id <= 0`): root (`0`), temporary (`-1`), call-record (`-2`), trash (`-3`). These are seeded by `NotesDatabaseHelper.createSystemFolder()` and not exported.
- **Trashed notes**: Excluded by default. The export filter is `_id > 0 AND parent_id != -3`.
- **`NOTES_COUNT`**: Derived from DB triggers, reset to `0` on import, re-counted as notes are inserted.
- **Room-specific fields**: The snapshot is provider-native; Room read model is rebuilt from provider after import.
