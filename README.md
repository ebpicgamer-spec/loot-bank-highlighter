# Loot Bank Highlighter

Turn your RuneLite Loot Tracker history into a practical bank view.

Loot Bank Highlighter records loot by source, displays each source and its drops in a RuneLite sidebar panel, and lets you pin a source to show only its matching items in your bank. The filtered view uses the bank's real item widgets, so visible items remain clickable and draggable.

## Features

- Tracks loot from NPCs, players, and supported Loot Tracker activities.
- Groups kill counts, item quantities, and distinct drops by loot source.
- Imports remembered history from RuneLite's built-in Loot Tracker.
- Repeated imports update existing records without doubling their totals.
- Displays loot sources with item icons and quantities in a compact, scrollable panel.
- Pins a source with the eye button and filters the bank to matching items.
- Immediately removes an item from the filtered view when its final copy leaves the bank.
- Stores records in the active RuneLite configuration profile.
- Allows individual source records to be cleared.

## Installation

1. Open RuneLite.
2. Select the configuration wrench.
3. Open **Plugin Hub**.
4. Search for **Loot Bank Highlighter**.
5. Select **Install** and enable the plugin.
6. Open the Loot Bank Highlighter icon in the RuneLite sidebar.

## Import existing Loot Tracker history

1. Log into the character whose history you want to import.
2. Make sure RuneLite's built-in Loot Tracker has **Remember loot** enabled.
3. Open the Loot Bank Highlighter sidebar panel.
4. Select **Import Loot Tracker History**.
5. Confirm the import.

The import reads remembered Loot Tracker data from the active RuneLite character profile. It does not contact an external service. Running it again is safe: existing records are updated rather than counted twice.

## Using the bank filter

1. Open the Loot Bank Highlighter sidebar panel.
2. Select the eye button beside a loot source.
3. Open your bank.
4. The bank will display items associated with that source that are currently present.
5. Select the eye button again to restore the normal bank view.

The filtered results are real bank items—not decorative copies—so normal bank interactions still work.

## Configuration

- **Single source selection** controls whether only one source can be pinned at a time.
- **Persist loot records** controls whether tracked records survive a RuneLite restart.

## Important notes

- Only matching items currently present in the bank appear in the filtered view.
- Loot Tracker history can only be imported when RuneLite has remembered it for the active character profile.
- Disabling the plugin restores the normal bank view.
- Loot Bank Highlighter is a third-party Plugin Hub plugin and is not maintained by the RuneLite developers.

## Support and feedback

Found a reproducible bug or have a feature suggestion? Please [open an issue](https://github.com/ebpicgamer-spec/loot-bank-highlighter/issues).

When reporting a bug, include:

- What you expected to happen.
- What happened instead.
- The loot source and item involved.
- Relevant RuneLite logs or a screenshot with private information removed.

## License

This project is licensed under the [BSD 2-Clause License](LICENSE).
