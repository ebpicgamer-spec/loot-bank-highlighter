package com.lootbankhighlighter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated loot for a single source ("Zulrah", "Barrows chest", clue name, etc).
 * We keep a running total of item id -> quantity seen, mirroring what the
 * built-in Loot Tracker panel would show for that source.
 */
public class LootRecord
{
	private final String sourceName;
	// itemId -> total quantity ever received from this source
	private final Map<Integer, Integer> items = new LinkedHashMap<>();
	private long lastUpdatedMillis;
	private int killCount;

	public LootRecord(String sourceName)
	{
		this.sourceName = sourceName;
	}

	public int getKillCount()
	{
		return killCount;
	}

	public void incrementKillCount()
	{
		killCount++;
	}

	public String getSourceName()
	{
		return sourceName;
	}

	public Map<Integer, Integer> getItems()
	{
		return items;
	}

	public long getLastUpdatedMillis()
	{
		return lastUpdatedMillis;
	}

	public void addItem(int itemId, int quantity)
	{
		items.merge(itemId, quantity, Integer::sum);
		lastUpdatedMillis = System.currentTimeMillis();
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}
}