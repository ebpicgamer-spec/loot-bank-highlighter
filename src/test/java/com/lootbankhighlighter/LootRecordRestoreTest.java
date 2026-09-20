package com.lootbankhighlighter;

import org.junit.Test;
import static org.junit.Assert.*;

public class LootRecordRestoreTest
{
	@Test
	public void restorePreservesNewDropsAndKillCounts()
	{
		LootRecord deleted = new LootRecord("Hydra");
		deleted.incrementKillCount();
		deleted.addItem(1, 5);
		LootRecord current = new LootRecord("Hydra");
		current.incrementKillCount();
		current.addItem(1, 3);
		current.addItem(2, 1);
		current.restoreDeleted(deleted);
		assertEquals(2, current.getKillCount());
		assertEquals(Integer.valueOf(8), current.getItems().get(1));
		assertEquals(Integer.valueOf(1), current.getItems().get(2));
	}

	@Test
	public void snapshotIsIndependentAndRestoreSaturates()
	{
		LootRecord original = new LootRecord("Hydra");
		original.addItem(1, Integer.MAX_VALUE);
		LootRecord copy = original.copy();
		original.addItem(2, 3);
		assertFalse(copy.getItems().containsKey(2));
		LootRecord current = new LootRecord("Hydra");
		current.addItem(1, 10);
		current.restoreDeleted(copy);
		assertEquals(Integer.valueOf(Integer.MAX_VALUE), current.getItems().get(1));
	}
}
