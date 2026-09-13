package com.lootbankhighlighter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Loot Bank Highlighter",
		description = "Pin a Loot Tracker entry with the eye icon and see it filtered into its own view in your bank, like an Inventory Setups loadout",
		tags = {"loot", "tracker", "bank", "inventory", "setups", "highlight"}
)
public class LootBankHighlighterPlugin extends Plugin
{
	private static final String CONFIG_KEY_RECORDS = "lootRecordsJson";

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
	private LootBankHighlighterConfig config;

	@Inject
	private Gson gson;

	@Inject
	private BankSearch bankSearch;

	private LootBankHighlighterPanel panel;
	private NavigationButton navButton;

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
		clientToolbar.removeNavigation(navButton);
		saveRecords();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// no-op hook kept for future use (e.g. clearing per-session state on login)
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		String source = event.getNpc().getName() != null ? event.getNpc().getName() : "Unknown NPC";
		record(source, event.getItems().stream()
				.collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i.getQuantity(), Integer::sum)));
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		String source = event.getPlayer().getName() != null ? event.getPlayer().getName() : "Unknown player";
		record(source, event.getItems().stream()
				.collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i.getQuantity(), Integer::sum)));
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		String source = event.getName();
		record(source, event.getItems().stream()
				.collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i.getQuantity(), Integer::sum)));
	}

	private void record(String source, Map<Integer, Integer> items)
	{
		LootRecord rec = lootRecords.computeIfAbsent(source, LootRecord::new);
		rec.incrementKillCount();
		items.forEach(rec::addItem);
		saveRecords();
		SwingUtilities.invokeLater(this::refreshPanel);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == WidgetID.BANK_GROUP_ID && !selectedSources.isEmpty() && activeTabSource == null)
		{
			activateTabView(selectedSources.iterator().next());
		}
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

			if (matchIds.contains(itemId))
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

	// ---- activation, driven by the panel's eye-icon buttons ----

	public boolean isSelected(String sourceName)
	{
		return selectedSources.contains(sourceName);
	}

	public void toggleSelected(String sourceName)
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
			if (config.onlyOneSourceAtATime())
			{
				selectedSources.clear();
			}

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
		lootRecords.remove(sourceName);
		selectedSources.remove(sourceName);
		if (sourceName.equals(activeTabSource))
		{
			deactivateTabView();
		}
		saveRecords();
		refreshPanel();
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
