package dev.lolihub.obsidian;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Obsidian implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("obsidian")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("summon")
                            .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                                    .suggests((context, builder) -> {
                                        // find all bot players' names and suggest smallest unused index
                                        var srv = context.getSource().getPlayer().getServer();
                                        if (srv == null) {
                                            return builder.buildFuture();
                                        }
                                        for (int i = 0; i < 100; i++) {
                                            if (srv.getPlayerManager().getPlayer("bot_" + i) == null) {
                                                builder.suggest(String.valueOf(i));
                                                break;
                                            }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                            .then(CommandManager.argument("lookingat", Vec3ArgumentType.vec3())
                                                    .executes(context -> {
                                                        int index = IntegerArgumentType.getInteger(context, "index");
                                                        Vec3d pos = Vec3ArgumentType.getVec3(context, "pos");
                                                        Vec3d lookingAt = Vec3ArgumentType.getVec3(context, "lookingat");
                                                        return spawnBot(context, index, pos, lookingAt, 1, null);
                                                    })
                                                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                                            .then(CommandManager.argument("expand", StringArgumentType.string())
                                                                    .suggests((context, builder) -> {
                                                                        builder.suggest("N").suggest("S").suggest("E").suggest("W");
                                                                        return builder.buildFuture();
                                                                    })
                                                                    .executes(context -> {
                                                                        int index = IntegerArgumentType.getInteger(context, "index");
                                                                        Vec3d pos = Vec3ArgumentType.getVec3(context, "pos");
                                                                        Vec3d lookingAt = Vec3ArgumentType.getVec3(context, "lookingat");
                                                                        int count = IntegerArgumentType.getInteger(context, "count");
                                                                        String direction = StringArgumentType.getString(context, "expand");
                                                                        return spawnBot(context, index, pos, lookingAt, count, direction);
                                                                    })))))))
                    .then(CommandManager.literal("start")
                            .executes(context -> {
                                var bots = context.getSource().getPlayer().getServer().getPlayerManager().getPlayerList()
                                        .stream()
                                        .filter(player -> player.getName().getString().toLowerCase().startsWith("bot_"))
                                        .toList();
                                for (var bot : bots) {
                                    var cmd = String.format("player %s attack continuous", bot.getName().getString());
                                    context.getSource().getServer().getCommandManager().execute(
                                            context.getSource().getServer().getCommandManager().getDispatcher().parse(cmd, context.getSource()), cmd);
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(CommandManager.literal("stop")
                            .executes(context -> {
                                var bots = context.getSource().getPlayer().getServer().getPlayerManager().getPlayerList()
                                        .stream()
                                        .filter(player -> player.getName().getString().toLowerCase().startsWith("bot_"))
                                        .toList();
                                for (var bot : bots) {
                                    var cmd = String.format("player %s attack once", bot.getName().getString());
                                    context.getSource().getServer().getCommandManager().execute(
                                            context.getSource().getServer().getCommandManager().getDispatcher().parse(cmd, context.getSource()), cmd);
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(CommandManager.literal("kill")
                            .executes(context -> {
                                var bots = context.getSource().getPlayer().getServer().getPlayerManager().getPlayerList()
                                        .stream()
                                        .filter(player -> player.getName().getString().toLowerCase().startsWith("bot_"))
                                        .toList();
                                for (var bot : bots) {
                                    var cmd = String.format("player %s kill", bot.getName().getString());
                                    context.getSource().getServer().getCommandManager().execute(
                                            context.getSource().getServer().getCommandManager().getDispatcher().parse(cmd, context.getSource()), cmd);
                                }
                                return Command.SINGLE_SUCCESS;
                            })));
        });
    }

    private static int spawnBot(CommandContext<ServerCommandSource> context,
                                int index, Vec3d pos, Vec3d lookingAt, int count, String direction) {
        var t = new Thread(() -> {
            try {
                var cm = context.getSource().getServer().getCommandManager();
                var pm = context.getSource().getPlayer().getServer().getPlayerManager();

                for (int i = 0; i < count; i++) {
                    Vec3d spawnPos = calculatePosition(pos, i, direction);
                    Vec3d lookingPos = calculatePosition(lookingAt, i, direction);
                    var botName = "bot_" + (index + i);
                    var cmd1 = String.format("player %s spawn at %f %f %f",
                            botName, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                    cm.execute(cm.getDispatcher().parse(cmd1, context.getSource()), cmd1);

                    while (pm.getPlayerList().stream().noneMatch(player -> player.getName().getString().toLowerCase().equals(botName))) {
                        // Wait for the bot to spawn
                        Thread.sleep(100);
                        LOGGER.debug(String.format("Waiting for %s to spawn...", botName));
                    }
                    var bot = pm.getPlayerList().stream()
                            .filter(player -> player.getName().getString().toLowerCase().equals(botName))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Bot not found after spawning")).getName().getString();

                    LOGGER.debug(String.format("Bot %s spawned at %f %f %f", bot, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));
                    var cmd2 = String.format("player %s sneak", bot);
                    var cmd3 = String.format("player %s look at %f %f %f",
                            bot, lookingPos.getX(), lookingPos.getY(), lookingPos.getZ());

                    cm.execute(cm.getDispatcher().parse(cmd2, context.getSource()), cmd2);
                    cm.execute(cm.getDispatcher().parse(cmd3, context.getSource()), cmd3);
                }

            } catch (Exception e) {
                context.getSource().sendError(Text.literal("Error spawning bots: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();

        return Command.SINGLE_SUCCESS;
    }

    private static Vec3d calculatePosition(Vec3d basePos, int offset, String direction) {
        if (direction == null || offset == 0) {
            return basePos;
        }

        return switch (direction.toUpperCase()) {
            case "N" -> basePos.add(0, 0, -offset);
            case "S" -> basePos.add(0, 0, offset);
            case "E" -> basePos.add(offset, 0, 0);
            case "W" -> basePos.add(-offset, 0, 0);
            default -> basePos;
        };
    }
}
