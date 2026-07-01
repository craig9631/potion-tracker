package com.potiontracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class PotionTrackerPanel extends PluginPanel
{
	private final PotionTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final PotionTrackerConfig config;

	private final JPanel listPanel = new JPanel();
	private final JLabel emptyLabel = new JLabel("<html><center>No potions tracked yet.<br/>Drink a potion in-game to start tracking!</center></html>");

	public PotionTrackerPanel(PotionTrackerPlugin plugin, ItemManager itemManager, PotionTrackerConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Potion Tracker", SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

		JButton resetButton = new JButton("Reset session");
		resetButton.addActionListener(e -> {
			plugin.resetSession();
			refresh();
		});

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(title, BorderLayout.NORTH);
		header.add(resetButton, BorderLayout.SOUTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

		add(header, BorderLayout.NORTH);
		add(listPanel, BorderLayout.CENTER);

		refresh();
	}

	public void refresh()
	{
		listPanel.removeAll();

		Map<String, PotionStats> tracked = plugin.getTracked();

		if (tracked.isEmpty())
		{
			listPanel.add(emptyLabel);
		}
		else
		{
			List<PotionStats> sorted = tracked.values().stream()
				.sorted(Comparator.comparingDouble(s -> {
					double h = s.getHoursRemaining(config.rollingWindowMinutes(), config.defaultMaxDose());
					return h < 0 ? Double.MAX_VALUE : h;
				}))
				.collect(Collectors.toList());

			for (PotionStats stats : sorted)
			{
				listPanel.add(buildRow(stats));
				listPanel.add(Box.createVerticalStrut(6));
			}
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private JPanel buildRow(PotionStats stats)
	{
		JPanel row = new JPanel();
		row.setLayout(new BorderLayout(8, 4));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// icon
		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		if (stats.getIconItemId() != -1)
		{
			AsyncBufferedImage img = itemManager.getImage(stats.getIconItemId());
			img.addTo(iconLabel);
		}

		// name + numbers
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel nameLabel = new JLabel(stats.getName());
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);

		double perHour = stats.getPotionsPerHour(config.rollingWindowMinutes(), config.defaultMaxDose());
		double inBank = stats.getPotionsInBank(config.defaultMaxDose());
		double hoursRemaining = stats.getHoursRemaining(config.rollingWindowMinutes(), config.defaultMaxDose());

		JLabel rateLabel = new JLabel(String.format("Rate: %.1f/hr", perHour));
		rateLabel.setFont(FontManager.getRunescapeSmallFont());
		rateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel bankLabel = new JLabel(String.format("In bank: %.1f", inBank));
		bankLabel.setFont(FontManager.getRunescapeSmallFont());
		bankLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel timeLabel = new JLabel(formatTimeRemaining(hoursRemaining));
		timeLabel.setFont(FontManager.getRunescapeSmallFont());
		timeLabel.setForeground(colorForTime(hoursRemaining));

		textPanel.add(nameLabel);
		textPanel.add(rateLabel);
		textPanel.add(bankLabel);
		textPanel.add(timeLabel);

		// visual progress bar: how "full" the bank stock is relative to a 1-hour supply cushion,
		// capped at 100%, colored by urgency. Gives an at-a-glance "stock health" bar.
		JProgressBar bar = new JProgressBar(0, 100);
		bar.setStringPainted(false);
		bar.setPreferredSize(new Dimension(100, 8));
		int percent;
		if (hoursRemaining < 0)
		{
			percent = 100; // no consumption yet / unknown rate, show full/neutral
		}
		else
		{
			percent = (int) Math.max(0, Math.min(100, (hoursRemaining / 2.0) * 100));
		}
		bar.setValue(percent);
		bar.setForeground(colorForTime(hoursRemaining));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel south = new JPanel(new BorderLayout());
		south.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		south.add(bar, BorderLayout.CENTER);

		row.add(iconLabel, BorderLayout.WEST);
		row.add(textPanel, BorderLayout.CENTER);
		row.add(south, BorderLayout.SOUTH);

		return row;
	}

	private String formatTimeRemaining(double hours)
	{
		if (hours < 0)
		{
			return "Time left: --";
		}
		int totalMinutes = (int) Math.round(hours * 60);
		int h = totalMinutes / 60;
		int m = totalMinutes % 60;
		if (h > 0)
		{
			return String.format("Time left: %dh %02dm", h, m);
		}
		return String.format("Time left: %dm", m);
	}

	private Color colorForTime(double hours)
	{
		if (hours < 0)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		int urgentMinutes = config.urgentThresholdMinutes();
		double totalMinutes = hours * 60;
		if (totalMinutes <= urgentMinutes)
		{
			return new Color(220, 60, 60); // red
		}
		else if (totalMinutes <= urgentMinutes * 3)
		{
			return new Color(230, 180, 40); // amber
		}
		return new Color(80, 200, 100); // green
	}
}
