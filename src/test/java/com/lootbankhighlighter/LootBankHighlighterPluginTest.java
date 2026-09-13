package com.lootbankhighlighter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LootBankHighlighterPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LootBankHighlighterPlugin.class);
        RuneLite.main(args);
    }
}