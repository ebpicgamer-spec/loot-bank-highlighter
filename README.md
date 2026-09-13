# Loot Bank Highlighter

Loot Bank Highlighter is a RuneLite plugin that records loot by source and lets
you pin a source from its sidebar panel. When the bank is opened, the plugin
shows the items from that source in a compact bank view using the bank's real
item widgets, so the items remain clickable and draggable.

## Features

- Records loot from NPCs, players, and supported Loot Tracker activities.
- Groups item totals and kill counts by loot source.
- Displays each source with item icons and quantities in the sidebar.
- Pins a loot source with the eye button.
- Filters the bank to items obtained from the pinned source.
- Saves tracked records in the active RuneLite configuration profile.
- Allows individual loot records to be cleared from the panel.

## Usage

1. Enable **Loot Bank Highlighter** in RuneLite.
2. Receive loot from an NPC or another supported activity.
3. Open the plugin's sidebar panel.
4. Select the eye button next to a source.
5. Open your bank to view the matching items.
6. Select the eye again to return to the normal bank view.

The **Single source selection** setting controls whether only one source can be
pinned at a time. **Persist loot records** controls whether records survive a
client restart.

## Important notes

- The plugin starts tracking after it is installed; it does not import previous
  RuneLite Loot Tracker history.
- The filtered bank view only contains matching items that are currently in the
  player's bank.
- Disabling the plugin restores the normal bank view.

## Support

Please report reproducible bugs through this repository's GitHub issue tracker.

## License

This project is licensed under the BSD 2-Clause License. See [LICENSE](LICENSE).
