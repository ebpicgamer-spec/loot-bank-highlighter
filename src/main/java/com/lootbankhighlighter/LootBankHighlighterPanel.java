package com.lootbankhighlighter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Lists tracked loot sources the way the built-in Loot Tracker does: source
 * name, kill count, and every distinct item received with its icon and
 * quantity - plus an eye toggle to pin a source for the bank filter view.
 */
public class LootBankHighlighterPanel extends PluginPanel
{
	private final LootBankHighlighterPlugin plugin;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final JPanel listContainer = new ViewportWidthPanel();

	private BufferedImage eyeOpenIcon;
	private BufferedImage eyeClosedIcon;
	private BufferedImage panelIcon;

	public LootBankHighlighterPanel(LootBankHighlighterPlugin plugin, ItemManager itemManager, ClientThread clientThread)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.clientThread = clientThread;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		try
		{
			eyeOpenIcon = ImageUtil.loadImageResource(getClass(), "eye_open.png");
			eyeClosedIcon = ImageUtil.loadImageResource(getClass(), "eye_closed.png");
			panelIcon = ImageUtil.loadImageResource(getClass(), "loot_bank_icon.png");
		}
		catch (Exception ignored)
		{
		}

		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		if (panelIcon != null)
		{
			JLabel iconLabel = new JLabel(new ImageIcon(panelIcon));
			titlePanel.add(iconLabel, BorderLayout.WEST);
		}

		JLabel title = new JLabel("Pin a source to filter it into your bank");
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(Color.WHITE);
		titlePanel.add(title, BorderLayout.CENTER);

		JButton importButton = new JButton("Import Loot Tracker History");
		importButton.setToolTipText("Import remembered loot from RuneLite's active character profile");
		importButton.addActionListener(e -> importLootTrackerHistory());
		titlePanel.add(importButton, BorderLayout.SOUTH);

		add(titlePanel, BorderLayout.NORTH);

		listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
		listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(listContainer);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		rebuild();
	}

	private void importLootTrackerHistory()
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"<html>Import RuneLite's remembered Loot Tracker history for the active character?<br>"
				+ "Existing records will be kept, and repeated imports will not double their totals.</html>",
			"Import Loot Tracker History",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}

		LootBankHighlighterPlugin.ImportResult result = plugin.importLootTrackerHistory();
		JOptionPane.showMessageDialog(
			this,
			result.getMessage(),
			"Loot Tracker Import",
			result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
	}

	public void rebuild()
	{
		listContainer.removeAll();

		plugin.getLootRecords().values().stream()
			.filter(r -> !r.isEmpty())
			.sorted((a, b) -> Long.compare(b.getLastUpdatedMillis(), a.getLastUpdatedMillis()))
			.forEach(record -> listContainer.add(buildSourcePanel(record)));

		if (plugin.getLootRecords().isEmpty())
		{
			JLabel empty = new JLabel("<html>No loot tracked yet.<br>Kill something and it'll show up here.</html>");
			empty.setForeground(Color.GRAY);
			empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			listContainer.add(empty);
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildSourcePanel(LootRecord record)
	{
		String source = record.getSourceName();
		boolean selected = plugin.isSelected(source);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		wrapper.setBackground(selected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);

		// Header row: name + kill count, eye toggle, clear button
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);

		String kills = record.getKillCount() + (record.getKillCount() == 1 ? " kill" : " kills");
		JLabel nameLabel = new JLabel(source + "  (" + kills + ")");
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(selected ? Color.YELLOW : Color.WHITE);
		header.add(nameLabel, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttons.setOpaque(false);

		JButton eyeButton = new JButton();
		eyeButton.setToolTipText(selected
			? "Unpin: stop filtering this loot into your bank"
			: "Pin: filter this loot into its own bank view");
		eyeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		BufferedImage icon = selected ? eyeOpenIcon : eyeClosedIcon;
		if (icon != null)
		{
			eyeButton.setIcon(new ImageIcon(icon));
		}
		else
		{
			eyeButton.setText(selected ? "\uD83D\uDC41" : "○");
		}
		eyeButton.addActionListener(e ->
		{
			plugin.toggleSelected(source);
			rebuild();
		});
		buttons.add(eyeButton);

		JButton clearButton = new JButton("x");
		clearButton.setToolTipText("Forget this loot record");
		clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clearButton.addActionListener(e -> plugin.clearRecord(source));
		buttons.add(clearButton);

		header.add(buttons, BorderLayout.EAST);
		wrapper.add(header, BorderLayout.NORTH);

		// Item grid: icon + quantity for every distinct item from this source
		JPanel itemGrid = new JPanel(new GridLayout(0, 6, 2, 2));
		itemGrid.setOpaque(false);
		for (Map.Entry<Integer, Integer> entry : record.getItems().entrySet())
		{
			itemGrid.add(buildItemIcon(entry.getKey(), entry.getValue()));
		}
		wrapper.add(itemGrid, BorderLayout.CENTER);

		return wrapper;
	}

	private JLabel buildItemIcon(int itemId, int quantity)
	{
		JLabel label = new JLabel();
		label.setToolTipText("Loading item details...");
		clientThread.invoke(() ->
		{
			// Item definitions must be read on the client thread; Swing updates belong on the EDT.
			int canonicalId = itemManager.canonicalize(itemId);
			ItemComposition item = itemManager.getItemComposition(canonicalId);
			String tooltip = buildToolTip(canonicalId, item.getMembersName(), quantity,
				itemManager.getItemPrice(canonicalId), item.getHaPrice());
			SwingUtilities.invokeLater(() -> label.setToolTipText(tooltip));
		});
		label.setVerticalAlignment(SwingConstants.CENTER);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setPreferredSize(new Dimension(40, 32));

		AsyncBufferedImage image = itemManager.getImage(itemId, quantity, quantity > 1);
		image.addTo(label);

		return label;
	}

	static String buildToolTip(int itemId, String name, int quantity, int gePrice, int haPrice)
	{
		String escapedName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		StringBuilder tooltip = new StringBuilder("<html>");
		tooltip.append(escapedName).append(" x ").append(QuantityFormatter.formatNumber(quantity));
		if (itemId != ItemID.COINS)
		{
			appendPrice(tooltip, "GE", quantity, gePrice);
			if (itemId != ItemID.PLATINUM)
			{
				appendPrice(tooltip, "HA", quantity, haPrice);
			}
		}
		return tooltip.append("</html>").toString();
	}

	private static void appendPrice(StringBuilder tooltip, String type, int quantity, int unitPrice)
	{
		tooltip.append("<br>").append(type).append(": ")
			.append(QuantityFormatter.quantityToStackSize((long) unitPrice * quantity));
		if (quantity > 1)
		{
			tooltip.append(" (").append(QuantityFormatter.quantityToStackSize(unitPrice)).append(" ea)");
		}
	}

	private static class ViewportWidthPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
