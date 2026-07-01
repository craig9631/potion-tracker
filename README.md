# Potion Tracker

A RuneLite plugin that tracks:

- **Potions consumed per hour** — calculated from a rolling time window (default 60 min, configurable) based on actual "Drink" actions, not just inventory changes (so banking/dropping potions doesn't get miscounted as usage).
- **Potions remaining in your bank** — automatically tallied whenever you open your bank, summed across all dose variants of a potion.
- **Estimated time remaining** — bank stock ÷ consumption rate, shown as `Xh Ym`.

## UI

- **Side panel** (toolbar icon): a card per tracked potion with its icon, rate, bank stock, time remaining, and a color-coded stock bar (green / amber / red based on how soon you'll run out).
- **In-game overlay**: compact live summary in the top-left of the game screen, colored the same way, with an option to only show potions that are running low.

Nothing is tracked until you drink at least one dose of a potion in-game — the panel and overlay start empty by design.

## How consumption is measured

The plugin listens for the "Drink" menu action rather than diffing your inventory, since diffing would also fire when you bank, drop, or trade potions. Each "Drink" click is logged with a timestamp; old entries fall outside the rolling window and stop contributing to the rate.

Potions are grouped by base name (e.g. "Prayer potion(4)" and "Prayer potion(1)" both count as "Prayer potion"), and doses are converted to whole-potion equivalents using the highest dose count observed for that potion this session (falls back to a configurable default, normally 4, if you haven't drunk a fresh one yet).

## Building locally

This project follows the standard RuneLite third-party plugin layout and depends on the `net.runelite:client` artifact from the RuneLite Maven repo (declared in `build.gradle`).

```bash
git clone <your fork of https://github.com/runelite/plugin-hub> plugin-hub
# or, to just compile this plugin standalone against the RuneLite client:
git clone https://github.com/runelite/runelite
cd runelite && mvn install -DskipTests   # or follow RuneLite's own build docs
```

The simplest way to test locally is via the RuneLite Plugin Hub's own tooling — see `CONTRIBUTING` in the `plugin-hub` repo, or run this plugin through IntelliJ with the `client` module on the classpath and `RuneLite.main()` as the entry point with `-Ddeveloper.mode` set, loading this plugin from your local Gradle project.

## License

BSD-2-Clause (matches the RuneLite / Plugin Hub convention — required for Hub submission).
