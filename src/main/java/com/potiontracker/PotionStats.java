package com.potiontracker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks consumption history and bank stock for a single "potion family"
 * (e.g. all dose variants of Prayer potion are grouped under "Prayer potion").
 */
public class PotionStats
{
	private final String name;

	/** icon item id of the highest-dose variant seen so far, used for display */
	private int iconItemId = -1;

	/** highest dose count ever observed for this family this session (e.g. 4 for a 4-dose potion) */
	private int maxDoseSeen = 0;

	/** timestamps of individual "Drink" actions, used for the rolling rate calculation */
	private final Deque<Instant> sipTimestamps = new ArrayDeque<>();

	/** total sips (drink actions) this session, never trimmed - for lifetime stats */
	private int totalSipsSession = 0;

	/** total doses currently sitting in the player's bank (dose value * stack quantity, summed) */
	private int dosesInBank = 0;

	private final Instant firstSeen = Instant.now();

	public PotionStats(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public int getIconItemId()
	{
		return iconItemId;
	}

	public void setIconItemId(int iconItemId)
	{
		this.iconItemId = iconItemId;
	}

	public int getMaxDoseSeen()
	{
		return maxDoseSeen;
	}

	public void observeDose(int dose)
	{
		if (dose > maxDoseSeen)
		{
			maxDoseSeen = dose;
		}
	}

	public void recordSip(Instant when)
	{
		sipTimestamps.addLast(when);
		totalSipsSession++;
	}

	public void trim(Instant cutoff)
	{
		while (!sipTimestamps.isEmpty() && sipTimestamps.peekFirst().isBefore(cutoff))
		{
			sipTimestamps.pollFirst();
		}
	}

	public int getSipsInWindow()
	{
		return sipTimestamps.size();
	}

	public int getTotalSipsSession()
	{
		return totalSipsSession;
	}

	public Instant getFirstSeen()
	{
		return firstSeen;
	}

	public int getDosesInBank()
	{
		return dosesInBank;
	}

	public void setDosesInBank(int dosesInBank)
	{
		this.dosesInBank = dosesInBank;
	}

	/**
	 * @param fallbackMaxDose used if we haven't observed a max dose yet this session
	 * @return estimated whole potions remaining in bank
	 */
	public double getPotionsInBank(int fallbackMaxDose)
	{
		int denom = maxDoseSeen > 0 ? maxDoseSeen : fallbackMaxDose;
		return denom <= 0 ? 0 : (double) dosesInBank / denom;
	}

	/**
	 * @param windowMinutes configured rolling window size
	 * @param fallbackMaxDose used if we haven't observed a max dose yet this session
	 * @return estimated potions consumed per hour based on recent activity
	 */
	public double getPotionsPerHour(int windowMinutes, int fallbackMaxDose)
	{
		if (sipTimestamps.isEmpty())
		{
			return 0;
		}

		long elapsedSeconds = Math.max(1, java.time.Duration.between(firstSeen, Instant.now()).getSeconds());
		double windowSeconds = Math.min(windowMinutes * 60L, elapsedSeconds);
		if (windowSeconds <= 0)
		{
			windowSeconds = elapsedSeconds;
		}

		double dosesPerHour = (sipTimestamps.size() / (windowSeconds / 3600.0));
		int denom = maxDoseSeen > 0 ? maxDoseSeen : fallbackMaxDose;
		return denom <= 0 ? 0 : dosesPerHour / denom;
	}

	/**
	 * @return estimated hours remaining before bank stock is depleted, or -1 if rate is 0 (unknown/infinite)
	 */
	public double getHoursRemaining(int windowMinutes, int fallbackMaxDose)
	{
		double rate = getPotionsPerHour(windowMinutes, fallbackMaxDose);
		if (rate <= 0)
		{
			return -1;
		}
		return getPotionsInBank(fallbackMaxDose) / rate;
	}
}
