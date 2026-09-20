package com.lootbankhighlighter;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LootTooltipTest
{
	@Test
	public void stackShowsTotalsAndUnitPrices()
	{
		assertEquals("<html>Chaos rune x 903<br>GE: 94.8K (105 ea)<br>HA: 48.7K (54 ea)</html>",
			LootBankHighlighterPanel.buildToolTip(-1, "Chaos rune", 903, 105, 54));
	}

	@Test
	public void singleItemOmitsUnitPrices()
	{
		assertEquals("<html>Example x 1<br>GE: 100<br>HA: 60</html>",
			LootBankHighlighterPanel.buildToolTip(-1, "Example", 1, 100, 60));
	}

	@Test
	public void largeStacksDoNotOverflow()
	{
		String tooltip = LootBankHighlighterPanel.buildToolTip(-1, "Example", 2_000_000_000, 3, 2);
		assertTrue(tooltip.contains("GE: 6B (3 ea)"));
		assertTrue(tooltip.contains("HA: 4B (2 ea)"));
	}

	@Test
	public void coinsOnlyShowQuantity()
	{
		assertEquals("<html>Coins x 1,000</html>",
			LootBankHighlighterPanel.buildToolTip(ItemID.COINS, "Coins", 1000, 1, 0));
	}

	@Test
	public void platinumOmitsAlchemyValue()
	{
		String tooltip = LootBankHighlighterPanel.buildToolTip(ItemID.PLATINUM, "Platinum token", 20, 1000, 0);
		assertTrue(tooltip.contains("GE: 20K (1,000 ea)"));
		assertFalse(tooltip.contains("HA:"));
	}

	@Test
	public void zeroPricesAndHtmlCharactersAreSafe()
	{
		assertEquals("<html>A &amp; &lt;B&gt; x 1<br>GE: 0<br>HA: 0</html>",
			LootBankHighlighterPanel.buildToolTip(-1, "A & <B>", 1, 0, 0));
	}
}
