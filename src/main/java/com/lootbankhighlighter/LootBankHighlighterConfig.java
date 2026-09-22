package com.lootbankhighlighter;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LootBankHighlighterConfig.GROUP)
public interface LootBankHighlighterConfig extends Config
{
	String GROUP = "lootbankhighlighter";

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Color used to outline matching items in the bank",
		hidden = true,
		position = 1
	)
	default Color highlightColor()
	{
		return new Color(255, 215, 0, 200); // gold, like a loadout highlight
	}

	@ConfigItem(
		keyName = "fillColor",
		name = "Fill color",
		description = "Fill color used behind matching items in the bank",
		hidden = true,
		position = 2
	)
	default Color fillColor()
	{
		return new Color(255, 215, 0, 40);
	}

	@ConfigItem(
		keyName = "onlyOneSourceAtATime",
		name = "Single source selection",
		description = "Only one loot source can be pinned/highlighted at a time (like a single loadout)",
		hidden = true,
		position = 3
	)
	default boolean onlyOneSourceAtATime()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackNewLoot",
		name = "Track new loot",
		description = "Record new drops from Loot Tracker. Disable to pause tracking without removing saved records or bank filtering.",
		position = 0
	)
	default boolean trackNewLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "persistRecordsAcrossSessions",
		name = "Save loot between sessions",
		description = "Save aggregated loot records to the config profile so they survive a client restart",
		position = 4
	)
	default boolean persistRecords()
	{
		return true;
	}
}
