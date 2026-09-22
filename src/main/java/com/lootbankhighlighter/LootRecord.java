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

	public void addKillCount(int amount)
	{
		killCount = saturatedAdd(killCount, Math.max(0, amount));
	}

	public boolean mergeSnapshot(int importedKillCount, Map<Integer, Integer> importedItems)
	{
		boolean changed = false;
		if (importedKillCount > killCount)
		{
			killCount = importedKillCount;
			changed = true;
		}

		for (Map.Entry<Integer, Integer> entry : importedItems.entrySet())
		{
			Integer current = items.get(entry.getKey());
			if (current == null || entry.getValue() > current)
			{
				items.put(entry.getKey(), entry.getValue());
				changed = true;
			}
		}

		if (changed)
		{
			lastUpdatedMillis = System.currentTimeMillis();
		}
		return changed;
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

	public LootRecord copy()
	{
		LootRecord copy = new LootRecord(sourceName);
		copy.items.putAll(items);
		copy.killCount = killCount;
		copy.lastUpdatedMillis = lastUpdatedMillis;
		return copy;
	}

	/** Restore deleted history without losing drops received after deletion. */
	public void restoreDeleted(LootRecord deleted)
	{
		killCount = saturatedAdd(killCount, deleted.killCount);
		deleted.items.forEach((id, quantity) -> items.merge(id, quantity, LootRecord::saturatedAdd));
		lastUpdatedMillis = Math.max(lastUpdatedMillis, deleted.lastUpdatedMillis);
	}

	private static int saturatedAdd(int a, int b)
	{
		return (int) Math.min(Integer.MAX_VALUE, (long) a + b);
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}
}
