package com.lootbankhighlighter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDependency(LootTrackerPlugin.class)
@PluginDescriptor(
		name = "Loot Bank Highlighter",
		description = "Pin a Loot Tracker entry with the eye icon and see it filtered into its own view in your bank, like an Inventory Setups loadout",
		tags = {"loot", "tracker", "bank", "inventory", "setups", "highlight"}
)
public class LootBankHighlighterPlugin extends Plugin
{
	private static final String CONFIG_KEY_RECORDS = "lootRecordsJson";
	private static final String LOOT_TRACKER_CONFIG_GROUP = "loottracker";
	private static final String LOOT_TRACKER_DROP_PREFIX = "drops_";

	// Mirrors the real bank's own item grid layout so repositioned items line up correctly.
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int EMPTY_BANK_SLOT_ID = 6512;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	LootBankHighlighterConfig config;

	@Inject
	private Gson gson;

	@Inject
	private BankSearch bankSearch;

	private LootBankHighlighterPanel panel;
	private NavigationButton navButton;
	private final Deque<DeletedRecord> deletedRecords = new ArrayDeque<>();

	/** sourceName -> aggregated loot */
	private final Map<String, LootRecord> lootRecords = new LinkedHashMap<>();

	/** Currently pinned source(s) whose items should be highlighted/tabbed in the bank. */
	private final Set<String> selectedSources = new java.util.LinkedHashSet<>();

	/** The source currently being filtered into its own bank view, or null for the normal bank. */
	private String activeTabSource;

	@Provides
	LootBankHighlighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootBankHighlighterConfig.class);
	}

	@Override
	protected void startUp()
	{
		deletedRecords.clear();
		loadRecords();

		panel = new LootBankHighlighterPanel(this, itemManager, clientThread);
		refreshPanel();

		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		}
		catch (Exception e)
		{
			// no custom icon asset provided - draw a simple placeholder so the plugin still loads
			icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			g.setColor(Color.YELLOW);
			g.fillOval(2, 2, 12, 12);
			g.dispose();
		}

		NavigationButton.NavigationButtonBuilder builder = NavigationButton.builder()
				.tooltip("Loot Bank Highlighter")
				.icon(icon)
				.priority(6)
				.panel(panel);
		navButton = builder.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		if (activeTabSource != null)
		{
			deactivateTabView();
		}
		deletedRecords.clear();
		clientToolbar.removeNavigation(navButton);
		saveRecords();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// no-op hook kept for future use (e.g. clearing per-session state on login)
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.trackNewLoot())
		{
			return;
		}
		String source = event.getName();
		record(source, event.getItems().stream()
				.collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i.getQuantity(), Integer::sum)), event.getAmount());
	}

	void record(String source, Map<Integer, Integer> items, int amount)
	{
		LootRecord rec = lootRecords.computeIfAbsent(source, LootRecord::new);
		rec.addKillCount(amount);
		items.forEach(rec::addItem);
		saveRecords();
		SwingUtilities.invokeLater(this::refreshPanel);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && !selectedSources.isEmpty() && activeTabSource == null)
		{
			activateTabView(selectedSources.iterator().next());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId() || activeTabSource == null)
		{
			return;
		}

		// Rebuild native widgets first: deposits can move stacks to different slots.
		// BANKMAIN_FINISHBUILDING then applies our filter to the rebuilt widgets.
		clientThread.invokeLater(() ->
		{
			if (activeTabSource != null)
			{
				bankSearch.layoutBank();
			}
		});
	}

	/**
	 * The real workhorse: fires right when the game finishes laying out the bank grid.
	 * We hide every real item widget that doesn't match the pinned loot, and pack the
	 * matching real widgets into a clean grid - so everything stays natively
	 * draggable/clickable, because it IS the real bank doing the work.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			if (activeTabSource != null)
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1; // keep bank in "searching" state
			}
			return;
		}

		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING || activeTabSource == null)
		{
			return;
		}

		applyFilteredBankLayout();
	}

	private void applyFilteredBankLayout()
	{
		if (activeTabSource == null)
		{
			return;
		}

		Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
		if (itemContainer == null)
		{
			return;
		}

		Widget[] children = itemContainer.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		LootRecord record = lootRecords.get(activeTabSource);
		Set<Integer> matchIds = record == null ? Collections.emptySet() : record.getItems().keySet();
		net.runelite.api.ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		Item[] bankItems = bank == null ? new Item[0] : bank.getItems();

		Widget bankTitle = client.getWidget(ComponentID.BANK_TITLE_BAR);
		if (bankTitle != null)
		{
			bankTitle.setText(activeTabSource + " - loot");
		}

		int placed = 0;
		for (Widget widget : children)
		{
			if (widget == null)
			{
				continue;
			}

			int itemId = widget.getItemId();
			boolean isRealItem = itemId > 0 && itemId != EMPTY_BANK_SLOT_ID;
			if (!isRealItem)
			{
				continue; // leave bank furniture (tab buttons, backgrounds, etc.) alone
			}

			// A stale widget may still have this item ID even though its slot is empty.
			// Validate the exact slot so redepositing the item cannot revive that widget.
			if (matchIds.contains(itemId) && isCurrentBankSlot(bankItems, widget.getIndex(), itemId))
			{
				int adjX = (placed % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;
				int adjY = (placed / ITEMS_PER_ROW) * ITEM_VERTICAL_SPACING;

				widget.setHidden(false);
				widget.setOriginalX(adjX);
				widget.setOriginalY(adjY);
				widget.revalidate();
				placed++;
			}
			else
			{
				widget.setHidden(true);
			}
		}
	}

	static boolean isCurrentBankSlot(Item[] bankItems, int slot, int itemId)
	{
		if (slot < 0 || slot >= bankItems.length)
		{
			return false;
		}
		Item item = bankItems[slot];
		return item != null && item.getId() == itemId && item.getQuantity() > 0;
	}

	// ---- activation, driven by the panel's eye-icon buttons ----

	public boolean isSelected(String sourceName)
	{
		return selectedSources.contains(sourceName);
	}

	public void toggleSelected(String sourceName)
	{
		clientThread.invoke(() -> toggleSelectedOnClient(sourceName));
	}

	private void toggleSelectedOnClient(String sourceName)
	{
		if (selectedSources.contains(sourceName))
		{
			selectedSources.remove(sourceName);

			if (sourceName.equals(activeTabSource))
			{
				clientThread.invoke(() -> deactivateTabView());
			}
		}
		else
		{
			selectedSources.clear();

			selectedSources.add(sourceName);

			clientThread.invoke(() ->
			{
				Widget bankMain = client.getWidget(ComponentID.BANK_CONTAINER);

				if (bankMain != null && !bankMain.isHidden())
				{
					activateTabView(sourceName);
				}
			});
		}

		SwingUtilities.invokeLater(this::refreshPanel);
	}

	private void activateTabView(String source)
	{
		activeTabSource = source;
		clientThread.invokeLater(() ->
		{
			client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
			bankSearch.reset(true);
		});
	}

	private void deactivateTabView()
	{
		activeTabSource = null;
		clientThread.invokeLater(() ->
		{
			client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
			bankSearch.reset(true);
		});
	}

	public Set<String> getSelectedSources()
	{
		return selectedSources;
	}

	public Map<String, LootRecord> getLootRecords()
	{
		return lootRecords;
	}

	/**
	 * Combined item-id -> quantity map across every currently-selected/pinned source.
	 * Used by the optional highlight-style overlay.
	 */
	public Map<Integer, Integer> getHighlightedItems()
	{
		Map<Integer, Integer> combined = new LinkedHashMap<>();
		for (String source : selectedSources)
		{
			LootRecord rec = lootRecords.get(source);
			if (rec != null)
			{
				rec.getItems().forEach((id, qty) -> combined.merge(id, qty, Integer::sum));
			}
		}
		return combined;
	}

	public void clearRecord(String sourceName)
	{
		clientThread.invoke(() ->
		{
			LootRecord removed = lootRecords.remove(sourceName);
			if (removed == null)
			{
				return;
			}
			deletedRecords.push(new DeletedRecord(removed, selectedSources.remove(sourceName),
				configManager.getRSProfileKey()));
			if (sourceName.equals(activeTabSource))
			{
				deactivateTabView();
			}
			saveRecords();
			refreshPanel();
		});
	}

	public String getUndoSource()
	{
		// Never restore a deletion into a different character's session.
		if (!deletedRecords.isEmpty()
			&& !Objects.equals(deletedRecords.peek().profile, configManager.getRSProfileKey()))
		{
			deletedRecords.clear();
		}
		return deletedRecords.isEmpty() ? null : deletedRecords.peek().record.getSourceName();
	}

	public void undoDelete()
	{
		clientThread.invoke(() ->
		{
			if (getUndoSource() == null)
			{
				refreshPanel();
				return;
			}
			DeletedRecord deleted = deletedRecords.pop();
			String source = deleted.record.getSourceName();
			LootRecord current = lootRecords.get(source);
			if (current == null)
			{
				lootRecords.put(source, deleted.record);
			}
			else
			{
				current.restoreDeleted(deleted.record);
			}
			if (deleted.pinned && selectedSources.isEmpty()
				&& !selectedSources.contains(source))
			{
				toggleSelected(source);
			}
			saveRecords();
			refreshPanel();
		});
	}

	private static class DeletedRecord
	{
		private final LootRecord record;
		private final boolean pinned;
		private final String profile;

		private DeletedRecord(LootRecord record, boolean pinned, String profile)
		{
			this.record = record;
			this.pinned = pinned;
			this.profile = profile;
		}
	}

	public void refreshPanel()
	{
		if (panel != null)
		{
			panel.rebuild();
		}
	}

	public ItemComposition getItemComposition(int itemId)
	{
		return itemManager.getItemComposition(itemId);
	}

	public ImportResult importLootTrackerHistory()
	{
		String profileKey = configManager.getRSProfileKey();
		if (profileKey == null || profileKey.isEmpty())
		{
			return new ImportResult(false, "Log into a character before importing Loot Tracker history.");
		}

		Map<String, ImportedLoot> bySource = new LinkedHashMap<>();
		int readableRecords = 0;
		for (String key : configManager.getRSProfileConfigurationKeys(
			LOOT_TRACKER_CONFIG_GROUP, profileKey, LOOT_TRACKER_DROP_PREFIX))
		{
			String json = configManager.getConfiguration(LOOT_TRACKER_CONFIG_GROUP, profileKey, key);
			if (json == null || json.isEmpty())
			{
				continue;
			}

			try
			{
				ImportedLoot imported = gson.fromJson(json, ImportedLoot.class);
				if (imported == null || imported.name == null || imported.name.isEmpty()
					|| imported.drops == null || imported.drops.length < 2)
				{
					continue;
				}

				readableRecords++;
				ImportedLoot combined = bySource.computeIfAbsent(imported.name, ImportedLoot::new);
				combined.kills = saturatedAdd(combined.kills, Math.max(0, imported.kills));
				for (int i = 0; i + 1 < imported.drops.length; i += 2)
				{
					int itemId = imported.drops[i];
					int quantity = imported.drops[i + 1];
					if (itemId > 0 && quantity > 0)
					{
						combined.items.merge(itemId, quantity, LootBankHighlighterPlugin::saturatedAdd);
					}
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("Skipping unreadable Loot Tracker record {}", key, ex);
			}
		}

		if (readableRecords == 0)
		{
			return new ImportResult(false,
				"No remembered Loot Tracker history was found for the active character. "
					+ "Make sure Loot Tracker's Remember loot setting is enabled.");
		}

		// Imported snapshots overlap deleted history; invalidate undo to avoid counting it twice.
		deletedRecords.clear();
		int changedSources = 0;
		for (ImportedLoot imported : bySource.values())
		{
			if (imported.items.isEmpty())
			{
				continue;
			}
			LootRecord destination = lootRecords.computeIfAbsent(imported.name, LootRecord::new);
			if (destination.mergeSnapshot(imported.kills, imported.items))
			{
				changedSources++;
			}
		}

		saveRecords();
		refreshPanel();
		if (changedSources == 0)
		{
			return new ImportResult(true, "Everything in Loot Tracker was already imported.");
		}
		return new ImportResult(true,
			"Imported or updated " + changedSources + (changedSources == 1 ? " loot source." : " loot sources."));
	}

	private static int saturatedAdd(int first, int second)
	{
		long result = (long) first + second;
		return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
	}

	private static class ImportedLoot
	{
		private String name;
		private int kills;
		private int[] drops;
		private final Map<Integer, Integer> items = new LinkedHashMap<>();

		private ImportedLoot()
		{
		}

		private ImportedLoot(String name)
		{
			this.name = name;
		}
	}

	public static class ImportResult
	{
		private final boolean success;
		private final String message;

		private ImportResult(boolean success, String message)
		{
			this.success = success;
			this.message = message;
		}

		public boolean isSuccess()
		{
			return success;
		}

		public String getMessage()
		{
			return message;
		}
	}

	// ---- persistence ----

	private void saveRecords()
	{
		if (!config.persistRecords())
		{
			return;
		}
		configManager.setConfiguration(LootBankHighlighterConfig.GROUP, CONFIG_KEY_RECORDS, gson.toJson(lootRecords));
	}

	private void loadRecords()
	{
		if (!config.persistRecords())
		{
			return;
		}
		String json = configManager.getConfiguration(LootBankHighlighterConfig.GROUP, CONFIG_KEY_RECORDS);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Type type = new TypeToken<LinkedHashMap<String, LootRecord>>() {}.getType();
			Map<String, LootRecord> saved = gson.fromJson(json, type);
			if (saved != null)
			{
				lootRecords.putAll(saved);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load saved loot records", e);
		}
	}
}
