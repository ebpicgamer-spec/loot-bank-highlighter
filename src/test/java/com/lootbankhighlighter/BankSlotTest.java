package com.lootbankhighlighter;

import net.runelite.api.Item;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankSlotTest
{
	@Test
	public void redepositDoesNotReviveOldSlot()
	{
		Item[] bank = {new Item(-1, 0), new Item(1149, 2)};
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 0, 1149));
		assertTrue(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 1, 1149));
	}

	@Test
	public void rejectsEmptyPlaceholderAndExtraWidgets()
	{
		Item[] bank = {new Item(1149, 0), new Item(1201, 1), null};
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 0, 1149));
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 1, 1149));
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 2, 1149));
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, 3, 1149));
		assertFalse(LootBankHighlighterPlugin.isCurrentBankSlot(bank, -1, 1149));
	}
}
