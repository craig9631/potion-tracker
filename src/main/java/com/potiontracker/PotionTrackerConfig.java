package com.potiontracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("potiontracker")
public interface PotionTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "rollingWindowMinutes",
		name = "Rate window (minutes)",
		description = "How far back to look when calculating your potions-per-hour rate.",
		position = 0
	)
	@Range(min = 5, max = 240)
	default int rollingWindowMinutes()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "defaultMaxDose",
		name = "Fallback max dose",
		description = "Assumed number of doses per potion if the plugin hasn't seen a full (4) dose potion yet this session.",
		position = 1
	)
	@Range(min = 1, max = 6)
	default int defaultMaxDose()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show in-game overlay",
		description = "Show a small overlay in-game with your potion usage summary.",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlyShowUrgent",
		name = "Overlay: only show potions running low",
		description = "If enabled, the overlay only lists potions estimated to run out within the threshold below.",
		position = 3
	)
	default boolean onlyShowUrgent()
	{
		return false;
	}

	@ConfigItem(
		keyName = "urgentThresholdMinutes",
		name = "Overlay: low stock threshold (minutes)",
		description = "Potions estimated to run out within this many minutes are highlighted red.",
		position = 4
	)
	@Range(min = 1, max = 600)
	default int urgentThresholdMinutes()
	{
		return 20;
	}
}
