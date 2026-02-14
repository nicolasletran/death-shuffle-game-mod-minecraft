package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@SuppressWarnings("SpellCheckingInspection")
public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "deathgame";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// ============== EXACT DEATH MESSAGES FROM USER ==============
	public static final List<String> DEATH_MESSAGES = Arrays.asList(
			// No other entity involved
			"was pricked to death",
			"drowned",
			"experienced kinetic energy",
			"blew up",
			"was killed by [Intentional Game Design]",
			"hit the ground too hard",
			"fell off a ladder",
			"fell off some vines",
			"fell off scaffolding",
			"was impaled on a stalagmite",
			"was skewered by a falling stalactite",
			"was squashed by a falling anvil",
			"went up in flames",
			"burned to death",
			"went off with a bang",
			"tried to swim in lava",
			"was struck by lightning",
			"was killed by magic",
			"froze to death",
			"starved to death",
			"suffocated in a wall",
			"was squished too much",
			"was poked to death by a sweet berry bush",
			"fell out of the world",
			"withered away",

			// Bee involved
			"was stung to death",

			// Blaze involved
			"was fireballed by Blaze",
			"was slain by Blaze",

			// Cave Spider involved
			"was slain by Cave Spider",

			// Creeper involved
			"was blown up by Creeper",

			// Drowned involved
			"was slain by Drowned",
			"was impaled by Drowned",

			// Enderman involved
			"was slain by Enderman",

			// Ghast involved
			"was fireballed by Ghast",

			// Hoglin involved
			"was slain by Hoglin",

			// Iron Golem involved
			"was slain by Iron Golem",

			// Magma Cube involved
			"was slain by Magma Cube",

			// Piglin involved
			"was slain by Piglin",
			"was slain by Piglin Brute",

			// Skeleton involved
			"was shot by Skeleton",

			// Spider involved
			"was slain by Spider",

			// Trident involved
			"was impaled by Trident",

			// Wither Skeleton involved
			"was slain by Wither Skeleton",

			// Zombie involved
			"was slain by Zombie",

			// Extra
			"was squished too much"
	);
	// ==============================================

	private final GameState gameState = new GameState();
	private int tickCounter = 0;

	public static class GameState {
		private boolean gameActive = false;
		private int roundNumber = 0;
		private int timeRemaining = 300; // 5 minutes
		private boolean warnedOneMinute = false;

		private ServerPlayer player1;
		private ServerPlayer player2;
		private String deathMessage1;
		private String deathMessage2;
		private boolean player1Completed = false;
		private boolean player2Completed = false;

		public void startGame(ServerPlayer p1, ServerPlayer p2) {
			this.player1 = p1;
			this.player2 = p2;
			this.gameActive = true;
			this.roundNumber = 0;

			// Reset completed flags
			this.player1Completed = false;
			this.player2Completed = false;

			broadcast(Component.literal("§6§l╔══════════════════════════════════════╗"));
			broadcast(Component.literal("§6§l║     DEATH CHALLENGE GAME STARTED    ║"));
			broadcast(Component.literal("§6§l╚══════════════════════════════════════╝"));
			broadcast(Component.literal("§e§lComplete your assigned death within 5 minutes!"));
			broadcast(Component.literal("§7Good luck and... die well! §c§l💀"));

			startNewRound();
		}

		public void startNewRound() {
			// Reset completed status FIRST
			this.player1Completed = false;
			this.player2Completed = false;

			this.roundNumber++;
			this.timeRemaining = 300;
			this.warnedOneMinute = false;

			// Assign random death messages
			Random random = new Random();
			this.deathMessage1 = DEATH_MESSAGES.get(random.nextInt(DEATH_MESSAGES.size()));
			this.deathMessage2 = DEATH_MESSAGES.get(random.nextInt(DEATH_MESSAGES.size()));

			// Make sure they don't get the same message
			if (deathMessage1.equals(deathMessage2)) {
				do {
					this.deathMessage2 = DEATH_MESSAGES.get(random.nextInt(DEATH_MESSAGES.size()));
				} while (deathMessage1.equals(deathMessage2));
			}

			// Send assignments to players
			sendDeathAssignment(player1, deathMessage1);
			sendDeathAssignment(player2, deathMessage2);

			broadcast(Component.literal("§6§l═══════════ ROUND " + roundNumber + " ═══════════"));
			broadcast(Component.literal("§eYou have §c5 minutes §eto complete your death!"));
		}

		private void sendDeathAssignment(ServerPlayer player, String deathMessage) {
			if (player == null) return;

			player.sendSystemMessage(Component.literal("§6§l╔══════════════════════════════╗"));
			player.sendSystemMessage(Component.literal("§6§l║     YOUR DEATH CHALLENGE     ║"));
			player.sendSystemMessage(Component.literal("§6§l╚══════════════════════════════╝"));
			player.sendSystemMessage(Component.literal("§c§lYou must: §f§l" + deathMessage));
			player.sendSystemMessage(Component.literal("§eComplete this in §c§l5:00§e!"));
			player.sendSystemMessage(Component.literal("§7(Round " + roundNumber + ")"));

			// Send action bar message
			player.displayClientMessage(Component.literal("§c§l💀 DEATH CHALLENGE: §f§l" + deathMessage), true);
		}

		public void tick() {
			if (!gameActive) return;

			timeRemaining--;

			// Check if both players completed
			if (player1Completed && player2Completed) {
				handleRoundComplete();
				return;
			}

			// Check time out
			if (timeRemaining <= 0) {
				handleTimeOut();
				return;
			}

			// Warning at 1 minute
			if (timeRemaining == 60 && !warnedOneMinute) {
				warnedOneMinute = true;
				broadcast(Component.literal("§c§l⚠⚠⚠ ONLY 1 MINUTE REMAINING! ⚠⚠⚠"));
				broadcast(Component.literal("§c§lHURRY UP!"));
			}

			// Countdown from 10 seconds
			if (timeRemaining <= 10 && timeRemaining > 0) {
				for (ServerPlayer player : getActivePlayers()) {
					String color = timeRemaining <= 3 ? "§c§l" : "§e§l";
					player.displayClientMessage(Component.literal(color + timeRemaining + "..."), true);
				}
			}

			// Update action bar with remaining time every 5 seconds
			if (timeRemaining % 5 == 0 && timeRemaining > 10 && timeRemaining < 300) {
				int minutes = timeRemaining / 60;
				int seconds = timeRemaining % 60;
				String timeString = String.format("%d:%02d", minutes, seconds);

				for (ServerPlayer player : getActivePlayers()) {
					String assignedDeath = (player == player1) ? deathMessage1 : deathMessage2;
					player.displayClientMessage(Component.literal("§c§l" + timeString + " §8| §f§l" + assignedDeath), true);
				}
			}
		}

		public void onPlayerDeath(ServerPlayer player, DamageSource source) {
			if (!gameActive) return;

			String deathMessage = source.getLocalizedDeathMessage(player).getString();
			String assignedDeath = (player == player1) ? deathMessage1 : deathMessage2;

			// Clean death message (remove player name)
			deathMessage = deathMessage.replace(player.getName().getString(), "").trim();

			ExampleMod.LOGGER.info("Player died: {}", deathMessage);
			ExampleMod.LOGGER.info("Assigned: {}", assignedDeath);

			if (isDeathMatch(deathMessage, assignedDeath)) {
				if (player == player1 && !player1Completed) {
					player1Completed = true;
					player1.sendSystemMessage(Component.literal("§a§l✓ SUCCESS! You completed your death challenge!"));
					broadcast(Component.literal("§a§l✦ PLAYER 1 COMPLETED THEIR DEATH! ✦"));
					broadcast(Component.literal("§7[" + deathMessage1 + "]"));
					checkRoundEnd();
				} else if (player == player2 && !player2Completed) {
					player2Completed = true;
					player2.sendSystemMessage(Component.literal("§a§l✓ SUCCESS! You completed your death challenge!"));
					broadcast(Component.literal("§a§l✦ PLAYER 2 COMPLETED THEIR DEATH! ✦"));
					broadcast(Component.literal("§7[" + deathMessage2 + "]"));
					checkRoundEnd();
				}
			} else {
				// Wrong death message
				player.sendSystemMessage(Component.literal("§c§l✗ Wrong death! You need: §f§l" + assignedDeath));
				player.sendSystemMessage(Component.literal("§7You died from: " + deathMessage));
			}
		}

		private boolean isDeathMatch(String actual, String assigned) {
			String actualLower = actual.toLowerCase();
			String assignedLower = assigned.toLowerCase();

			// Direct contains check
			if (actualLower.contains(assignedLower)) {
				return true;
			}

			// Check for key words (split into key parts)
			String[] keyWords = assignedLower.split(" ");
			int matches = 0;
			for (String word : keyWords) {
				if (word.length() > 3 && actualLower.contains(word)) {
					matches++;
				}
			}

			// If more than half of the key words match, consider it successful
			return matches >= Math.max(1, keyWords.length / 2);
		}

		private void checkRoundEnd() {
			if (player1Completed && player2Completed) {
				handleRoundComplete();
			}
		}

		private void handleRoundComplete() {
			broadcast(Component.literal("§6§l═══════════ ROUND " + roundNumber + " COMPLETE! ═══════════"));
			broadcast(Component.literal("§a§l✓ Both players succeeded!"));
			broadcast(Component.literal("§7Player 1: " + deathMessage1));
			broadcast(Component.literal("§7Player 2: " + deathMessage2));
			broadcast(Component.literal("§e§lStarting next round in 3 seconds..."));

			// Simple delay without server reference
			if (player1 != null && player2 != null) {
				new Thread(() -> {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						ExampleMod.LOGGER.error("Interrupted while waiting for next round", e);
						Thread.currentThread().interrupt();
						return;
					}

					// Make sure game is still active and players are still valid
					if (gameActive && player1 != null && player2 != null) {
						startNewRound();
					}
				}).start();
			}
		}

		private void handleTimeOut() {
			broadcast(Component.literal("§6§l╔══════════════════════════════════════╗"));
			broadcast(Component.literal("§6§l║            GAME OVER!               ║"));
			broadcast(Component.literal("§6§l╚══════════════════════════════════════╝"));

			if (player1Completed && !player2Completed) {
				broadcast(Component.literal("§a§l✦✦✦ PLAYER 1 WINS! ✦✦✦"));
				broadcast(Component.literal("§7Player 1 completed: §a" + deathMessage1));
				broadcast(Component.literal("§7Player 2 failed: §c" + deathMessage2));
			} else if (player2Completed && !player1Completed) {
				broadcast(Component.literal("§a§l✦✦✦ PLAYER 2 WINS! ✦✦✦"));
				broadcast(Component.literal("§7Player 2 completed: §a" + deathMessage2));
				broadcast(Component.literal("§7Player 1 failed: §c" + deathMessage1));
			} else {
				// Draw - both failed
				broadcast(Component.literal("§e§l✦✦✦ IT'S A DRAW! ✦✦✦"));
				broadcast(Component.literal("§7Both players failed to complete their deaths"));
				broadcast(Component.literal("§7Player 1 needed: §c" + deathMessage1));
				broadcast(Component.literal("§7Player 2 needed: §c" + deathMessage2));
			}

			broadcast(Component.literal("§6§lThanks for playing! Use /deathgame start to play again"));

			gameActive = false;
		}

		private void broadcast(Component message) {
			if (player1 != null) player1.sendSystemMessage(message);
			if (player2 != null) player2.sendSystemMessage(message);
		}

		private List<ServerPlayer> getActivePlayers() {
			List<ServerPlayer> players = new ArrayList<>();
			if (player1 != null) players.add(player1);
			if (player2 != null) players.add(player2);
			return players;
		}

		public boolean isGameActive() { return gameActive; }
		public int getRoundNumber() { return roundNumber; }
		public int getTimeRemaining() { return timeRemaining; }

		public void reset() {
			gameActive = false;
			player1 = null;
			player2 = null;
			player1Completed = false;
			player2Completed = false;
			timeRemaining = 300;
			roundNumber = 0;
			warnedOneMinute = false;
			deathMessage1 = null;
			deathMessage2 = null;
		}
	}

	@Override
	public void onInitialize() {
		ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
			if (entity instanceof ServerPlayer player && gameState.isGameActive()) {
				gameState.onPlayerDeath(player, source);
			}
			return true;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				registerCommands(dispatcher)
		);

		LOGGER.info("======================================");
		LOGGER.info("Death Challenge Mod v2.0 Initialized!");
		LOGGER.info("Loaded {} death messages", DEATH_MESSAGES.size());
		LOGGER.info("======================================");
	}

	private void onServerTick(MinecraftServer server) {
		if (gameState.isGameActive()) {
			tickCounter++;
			if (tickCounter >= 20) {
				tickCounter = 0;
				gameState.tick();
			}
		}
	}

	private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("deathgame")
				.then(Commands.literal("start")
						.then(Commands.argument("player1", StringArgumentType.word())
								.then(Commands.argument("player2", StringArgumentType.word())
										.executes(context -> {
											String p1Name = StringArgumentType.getString(context, "player1");
											String p2Name = StringArgumentType.getString(context, "player2");

											ServerPlayer player1 = context.getSource().getServer().getPlayerList().getPlayerByName(p1Name);
											ServerPlayer player2 = context.getSource().getServer().getPlayerList().getPlayerByName(p2Name);

											if (player1 == null || player2 == null) {
												context.getSource().sendFailure(Component.literal("§cPlayer not found!"));
												return 0;
											}

											if (gameState.isGameActive()) {
												context.getSource().sendFailure(Component.literal("§cA game is already in progress! Use /deathgame stop first."));
												return 0;
											}

											gameState.startGame(player1, player2);
											context.getSource().sendSuccess(() -> Component.literal("§aDeath challenge started between " + p1Name + " and " + p2Name), true);
											return 1;
										}))))
				.then(Commands.literal("stop")
						.executes(context -> {
							if (!gameState.isGameActive()) {
								context.getSource().sendFailure(Component.literal("§cNo active game to stop!"));
								return 0;
							}
							gameState.reset();
							context.getSource().sendSuccess(() -> Component.literal("§cGame stopped"), true);
							return 1;
						}))
				.then(Commands.literal("status")
						.executes(context -> {
							if (gameState.isGameActive()) {
								int minutes = gameState.getTimeRemaining() / 60;
								int seconds = gameState.getTimeRemaining() % 60;
								String timeString = String.format("%d:%02d", minutes, seconds);

								context.getSource().sendSuccess(() -> Component.literal("§6=== GAME STATUS ==="), false);
								context.getSource().sendSuccess(() -> Component.literal("§eRound: §f" + gameState.getRoundNumber()), false);
								context.getSource().sendSuccess(() -> Component.literal("§eTime remaining: §c" + timeString), false);
							} else {
								context.getSource().sendSuccess(() -> Component.literal("§7No active game"), false);
							}
							return 1;
						}))
				.then(Commands.literal("deaths")
						.executes(context -> {
							context.getSource().sendSuccess(() -> Component.literal("§6=== POSSIBLE DEATH MESSAGES (" + DEATH_MESSAGES.size() + ") ==="), false);
							for (int i = 0; i < DEATH_MESSAGES.size(); i++) {
								int index = i;
								context.getSource().sendSuccess(() ->
										Component.literal("§7" + (index + 1) + ". §f" + DEATH_MESSAGES.get(index)), false);
							}
							return 1;
						}))
				.then(Commands.literal("help")
						.executes(context -> {
							context.getSource().sendSuccess(() -> Component.literal("§6=== DEATH CHALLENGE COMMANDS ==="), false);
							context.getSource().sendSuccess(() -> Component.literal("§e/deathgame start <player1> <player2> §7- Start a new game"), false);
							context.getSource().sendSuccess(() -> Component.literal("§e/deathgame stop §7- Stop current game"), false);
							context.getSource().sendSuccess(() -> Component.literal("§e/deathgame status §7- Check game status"), false);
							context.getSource().sendSuccess(() -> Component.literal("§e/deathgame deaths §7- List all " + DEATH_MESSAGES.size() + " possible deaths"), false);
							context.getSource().sendSuccess(() -> Component.literal("§e/deathgame help §7- Show this help"), false);
							return 1;
						}))
		);
	}
}