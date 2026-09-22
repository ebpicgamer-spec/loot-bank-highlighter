package com.lootbankhighlighter;

import java.util.Arrays;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;

public class LootEventTest
{
    public static class RecordingPlugin extends LootBankHighlighterPlugin
    {
        final LootRecord result = new LootRecord("Test");
        @Override
        void record(String source, Map<Integer, Integer> items, int amount)
        {
            result.addKillCount(amount);
            items.forEach(result::addItem);
        }
    }

    @Test
    public void rawAndTrackerEventsCountOnceAndIdenticalKillsRemainDistinct()
    {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.config = new LootBankHighlighterConfig() {};
        EventBus bus = new EventBus();
        bus.register(plugin);
        java.util.List<ItemStack> items = Arrays.asList(new ItemStack(526, 1), new ItemStack(526, 1));
        for (int i = 0; i < 2; i++)
        {
            bus.post(new NpcLootReceived(null, items));
            bus.post(new PlayerLootReceived(null, items));
            bus.post(new LootReceived("Test", 1, LootRecordType.NPC, items, 1, null));
        }
        assertEquals(2, plugin.result.getKillCount());
        assertEquals(Integer.valueOf(4), plugin.result.getItems().get(526));
    }

    @Test
    public void batchCountAndPauseAreRespected()
    {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.config = new LootBankHighlighterConfig() {};
        LootReceived event = new LootReceived("Test", 1, LootRecordType.EVENT,
            Arrays.asList(new ItemStack(526, 6)), 3, null);
        plugin.onLootReceived(event);
        assertEquals(3, plugin.result.getKillCount());
        assertEquals(Integer.valueOf(6), plugin.result.getItems().get(526));
        plugin.config = new LootBankHighlighterConfig()
        {
            @Override public boolean trackNewLoot() { return false; }
        };
        plugin.onLootReceived(event);
        assertEquals(3, plugin.result.getKillCount());
        assertEquals(Integer.valueOf(6), plugin.result.getItems().get(526));
    }
}
