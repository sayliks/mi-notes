---
name: localization-update-for-new-feature
description: Workflow command scaffold for localization-update-for-new-feature in mi-notes.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /localization-update-for-new-feature

Use this workflow when working on **localization-update-for-new-feature** in `mi-notes`.

## Goal

Translates new or updated string resources for a feature into supported languages (e.g., Chinese).

## Common Files

- `app/src/main/values-zh-rCN/strings.xml`
- `app/src/main/values-zh-rTW/strings.xml`
- `res/values-zh-rCN/strings.xml`
- `res/values-zh-rTW/strings.xml`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Identify new or changed strings in app/src/main/values/strings.xml and res/values/strings.xml
- Update or add translations in app/src/main/values-zh-rCN/strings.xml, app/src/main/values-zh-rTW/strings.xml, res/values-zh-rCN/strings.xml, and res/values-zh-rTW/strings.xml

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.