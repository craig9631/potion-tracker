package com.potiontracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Potion Tracker",
	description = "Tracks potions consumed per hour, potions remaining in your bank, and estimated time until you run out",
	tags = {"potion", "tracker", "bank", "overlay", "herblore", "combat"}
)
public class PotionTrackerPlugin extends Plugin
{
	// Matches item names like "Prayer potion(4)" or "Saradomin brew(3)"
	private static final Pattern DOSE_PATTERN = Pattern.compile("^(.*)\\((\\d)\\)$");

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PotionTrackerConfig config;

	@Inject
	private PotionTrackerOverlay overlay;

	private PotionTrackerPanel panel;
	private NavigationButton navButton;

	/** family name (e.g. "Prayer potion") -> stats */
	private final Map<String, PotionStats> tracked = new LinkedHashMap<>();

	@Provides
	PotionTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PotionTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new PotionTrackerPanel(this, itemManager, config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Potion Tracker")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		tracked.clear();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option == null || !option.equals("Drink"))
		{
			return;
		}

		String target = Text.removeTags(event.getMenuTarget());
		if (target == null || target.isEmpty())
		{
			return;
		}

		registerSip(target, event.getItemId());
	}

	private void registerSip(String itemName, int itemId)
	{
		Matcher matcher = DOSE_PATTERN.matcher(itemName.trim());
		String family;
		int dose = -1;

		if (matcher.matches())
		{
			family = matcher.group(1).trim();
			dose = Integer.parseInt(matcher.group(2));
		}
		else
		{
			// Some potions (rare) have no dose suffix, treat whole name as the family, dose unknown
			family = itemName.trim();
		}

		if (family.isEmpty())
		{
			return;
		}

		PotionStats stats = tracked.computeIfAbsent(family, PotionStats::new);
		if (dose > 0)
		{
			stats.observeDose(dose);
		}
		if (stats.getIconItemId() == -1)
		{
			stats.setIconItemId(itemId);
		}
		stats.recordSip(Instant.now());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId())
		{
			return;
		}

		ItemContainer bank = event.getItemContainer();
		if (bank == null)
		{
			return;
		}

		// Reset bank counts for everything we know about, then re-tally from the bank contents.
		for (PotionStats stats : tracked.values())
		{
			stats.setDosesInBank(0);
		}

		for (Item item : bank.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			ItemComposition comp = itemManager.getItemComposition(item.getId());
			String name = comp.getName();
			if (name == null)
			{
				continue;
			}

			Matcher matcher = DOSE_PATTERN.matcher(name.trim());
			if (!matcher.matches())
			{
				continue;
			}

			String family = matcher.group(1).trim();
			int dose = Integer.parseInt(matcher.group(2));

			// Only tally families we've already seen the player drink this session,
			// so the panel doesn't fill up with every potion in the bank.
			PotionStats stats = tracked.get(family);
			if (stats == null)
			{
				continue;
			}

			stats.observeDose(dose);
			if (stats.getIconItemId() == -1)
			{
				stats.setIconItemId(item.getId());
			}
			stats.setDosesInBank(stats.getDosesInBank() + dose * item.getQuantity());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("potiontracker"))
		{
			return;
		}

		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}
		else
		{
			overlayManager.remove(overlay);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Instant cutoff = Instant.now().minusSeconds(config.rollingWindowMinutes() * 60L);
		for (PotionStats stats : tracked.values())
		{
			stats.trim(cutoff);
		}

		if (panel != null)
		{
			panel.refresh();
		}
	}

	public Map<String, PotionStats> getTracked()
	{
		return tracked;
	}

	public void resetSession()
	{
		tracked.clear();
	}

	public ItemManager getItemManager()
	{
		return itemManager;
	}
}
