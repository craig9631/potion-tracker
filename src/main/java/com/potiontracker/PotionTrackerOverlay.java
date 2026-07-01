package com.potiontracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class PotionTrackerOverlay extends OverlayPanel
{
	private final PotionTrackerPlugin plugin;
	private final PotionTrackerConfig config;

	@Inject
	private PotionTrackerOverlay(PotionTrackerPlugin plugin, PotionTrackerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPreferredSize(new Dimension(200, 0));
	}

	@Override
	public java.awt.Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || plugin.getTracked().isEmpty())
		{
			return null;
		}

		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Potion Tracker")
			.color(Color.WHITE)
			.build());

		int urgentMinutes = config.urgentThresholdMinutes();

		List<PotionStats> rows = plugin.getTracked().values().stream()
			.sorted(Comparator.comparingDouble(s -> {
				double h = s.getHoursRemaining(config.rollingWindowMinutes(), config.defaultMaxDose());
				return h < 0 ? Double.MAX_VALUE : h;
			}))
			.filter(s -> {
				if (!config.onlyShowUrgent())
				{
					return true;
				}
				double h = s.getHoursRemaining(config.rollingWindowMinutes(), config.defaultMaxDose());
				return h >= 0 && (h * 60) <= urgentMinutes;
			})
			.collect(Collectors.toList());

		if (rows.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("All stocked up")
				.leftColor(new Color(80, 200, 100))
				.build());
			return super.render(graphics);
		}

		for (PotionStats stats : rows)
		{
			double hoursRemaining = stats.getHoursRemaining(config.rollingWindowMinutes(), config.defaultMaxDose());
			double perHour = stats.getPotionsPerHour(config.rollingWindowMinutes(), config.defaultMaxDose());

			String timeText;
			Color color;
			if (hoursRemaining < 0)
			{
				timeText = "--";
				color = Color.LIGHT_GRAY;
			}
			else
			{
				int totalMinutes = (int) Math.round(hoursRemaining * 60);
				timeText = totalMinutes >= 60
					? String.format("%dh%02dm", totalMinutes / 60, totalMinutes % 60)
					: String.format("%dm", totalMinutes);

				if (totalMinutes <= urgentMinutes)
				{
					color = new Color(220, 60, 60);
				}
				else if (totalMinutes <= urgentMinutes * 3)
				{
					color = new Color(230, 180, 40);
				}
				else
				{
					color = new Color(80, 200, 100);
				}
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left(stats.getName())
				.right(timeText)
				.rightColor(color)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left(String.format("  %.1f/hr, %.0f in bank", perHour, stats.getPotionsInBank(config.defaultMaxDose())))
				.leftColor(Color.LIGHT_GRAY)
				.build());
		}

		return super.render(graphics);
	}
}
