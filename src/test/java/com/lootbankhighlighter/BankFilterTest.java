package com.lootbankhighlighter;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BankFilterTest
{
	@Mock private Client client;
	@Mock private ClientThread clientThread;
	@Mock private ItemManager itemManager;
	@InjectMocks private LootBankHighlighterPlugin plugin;
	private LootRecord record;

	@Before
	public void pinSource()
	{
		record = new LootRecord("Chest");
		plugin.getLootRecords().put("Chest", record);
		plugin.getSelectedSources().add("Chest");
		WidgetLoaded loaded = new WidgetLoaded();
		loaded.setGroupId(InterfaceID.BANKMAIN);
		plugin.onWidgetLoaded(loaded);
	}

	@Test
	public void notedDropsMatchDepositedItemsWithoutChangingHistory()
	{
		record.addItem(1001, 25); // Synthetic noted ID and its unnoted counterpart.
		record.addItem(1000, 5);
		when(itemManager.canonicalize(1001)).thenReturn(1000);
		when(itemManager.canonicalize(1000)).thenReturn(1000);
		Widget item = bankItem(0, 1000);
		buildBank(new Item[]{new Item(1000, 30)}, item);

		verify(item).setHidden(false);
		assertEquals(Integer.valueOf(25), record.getItems().get(1001));
		assertEquals(Integer.valueOf(5), record.getItems().get(1000));
	}

	@Test
	public void nativeSearchExclusionsStayHiddenAndClearingSearchRestoresLoot()
	{
		record.addItem(1000, 1);
		when(itemManager.canonicalize(1000)).thenReturn(1000);
		Widget item = bankItem(0, 1000);
		when(item.isSelfHidden()).thenReturn(true);
		Item[] bank = {new Item(1000, 1)};
		buildBank(bank, item);
		verify(item).setHidden(true);
		verify(item, never()).setHidden(false);

		// The next native rebuild shows the item again after the user clears search.
		when(item.isSelfHidden()).thenReturn(false);
		buildBank(bank, item);
		verify(item).setHidden(false);
	}

	@Test
	public void staleSlotsAndUnrelatedItemsRemainHidden()
	{
		record.addItem(1000, 1);
		when(itemManager.canonicalize(1000)).thenReturn(1000);
		when(itemManager.canonicalize(2000)).thenReturn(2000);
		Widget stale = bankItem(0, 1000);
		Widget current = bankItem(1, 1000);
		Widget unrelated = bankItem(2, 2000);
		buildBank(new Item[]{new Item(-1, 0), new Item(1000, 1), new Item(2000, 1)},
			stale, current, unrelated);
		verify(stale).setHidden(true);
		verify(current).setHidden(false);
		verify(unrelated).setHidden(true);
		verify(current).setOriginalY(0);
	}

	@Test
	public void pinDoesNotForceNativeSearchOn()
	{
		ScriptPostFired event = new ScriptPostFired(ScriptID.BANKMAIN_SEARCHING);
		plugin.onScriptPostFired(event);
		verifyNoInteractions(client);
	}

	private Widget bankItem(int slot, int id)
	{
		Widget widget = mock(Widget.class);
		when(widget.getItemId()).thenReturn(id);
		// Search-excluded and unrelated items never need slot validation.
		lenient().when(widget.getIndex()).thenReturn(slot);
		return widget;
	}

	private void buildBank(Item[] items, Widget... widgets)
	{
		Widget container = mock(Widget.class);
		when(client.getWidget(ComponentID.BANK_ITEM_CONTAINER)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(widgets);
		ItemContainer bank = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
		when(bank.getItems()).thenReturn(items);
		ScriptPostFired event = new ScriptPostFired(ScriptID.BANKMAIN_FINISHBUILDING);
		plugin.onScriptPostFired(event);
	}
}
