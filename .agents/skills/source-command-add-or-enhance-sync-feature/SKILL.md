---
name: "source-command-add-or-enhance-sync-feature"
description: "Workflow command scaffold for add-or-enhance-sync-feature in mi-notes."
---

# source-command-add-or-enhance-sync-feature

Use this skill when the user asks to run the migrated source command `add-or-enhance-sync-feature`.

## Command Template

# /add-or-enhance-sync-feature

Use this workflow when working on **add-or-enhance-sync-feature** in `mi-notes`.

## Goal

Adds a new sync feature (e.g., WebDAV) or enhances an existing sync feature, including code, UI integration, and string resources.

## Common Files

- `app/src/main/java/net/micode/notes/sync/*/*.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/net/micode/notes/ui/*.java`
- `app/src/main/values/strings.xml`
- `res/values/strings.xml`
- `app/src/main/values-zh-rCN/strings.xml`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Create or update Java classes for the sync provider under app/src/main/java/net/micode/notes/sync/[provider]/
- Update AndroidManifest.xml to register new services or permissions
- Modify UI activity classes (e.g., NoteEditActivity, NotesPreferenceActivity, NotesListActivity) to integrate the new sync feature
- Add or update string resources in app/src/main/values/strings.xml and res/values/strings.xml
- If needed, add or update translations in app/src/main/values-zh-rCN/strings.xml, app/src/main/values-zh-rTW/strings.xml, res/values-zh-rCN/strings.xml, and res/values-zh-rTW/strings.xml

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.
