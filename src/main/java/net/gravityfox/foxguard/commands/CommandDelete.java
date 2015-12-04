/*
 * This file is part of FoxGuard, licensed under the MIT License (MIT).
 *
 * Copyright (c) gravityfox - https://gravityfox.net/
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.gravityfox.foxguard.commands;


import com.google.common.collect.ImmutableList;
import net.gravityfox.foxguard.FGManager;
import net.gravityfox.foxguard.FoxGuardMain;
import net.gravityfox.foxguard.commands.util.AdvCmdParse;
import net.gravityfox.foxguard.handlers.GlobalHandler;
import net.gravityfox.foxguard.regions.GlobalRegion;
import net.gravityfox.foxguard.util.FGHelper;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.command.CommandCallable;
import org.spongepowered.api.util.command.CommandException;
import org.spongepowered.api.util.command.CommandResult;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.args.ArgumentParseException;
import org.spongepowered.api.world.World;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.gravityfox.foxguard.util.Aliases.*;

public class CommandDelete implements CommandCallable {

    private static final Function<Map<String, String>, Function<String, Consumer<String>>> MAPPER = map -> key -> value -> {
        if (isAlias(WORLD_ALIASES, key) && !map.containsKey("world")) {
            map.put("world", value);
        }
    };

    @Override
    public CommandResult process(CommandSource source, String arguments) throws CommandException {
        if (!testPermission(source)) {
            source.sendMessage(Texts.of(TextColors.RED, "You don't have permission to use this command!"));
            return CommandResult.empty();
        }
        AdvCmdParse parse = AdvCmdParse.builder().arguments(arguments).flagMapper(MAPPER).build();
        String[] args = parse.getArgs();
        if (source instanceof Player) {
            Player player = (Player) source;
            if (args.length == 0) {
                source.sendMessage(Texts.builder()
                        .append(Texts.of(TextColors.GREEN, "Usage: "))
                        .append(getUsage(source))
                        .build());
                return CommandResult.empty();
            } else if (isAlias(REGIONS_ALIASES, args[0])) {
                if (args.length < 2) throw new CommandException(Texts.of("Must specify a name!"));
                String worldName = parse.getFlagmap().get("world");
                World world = player.getWorld();
                if (!worldName.isEmpty()) {
                    Optional<World> optWorld = FoxGuardMain.getInstance().getGame().getServer().getWorld(worldName);
                    if (optWorld.isPresent()) {
                        world = optWorld.get();
                    } else world = player.getWorld();
                }
                if (args.length < 2) throw new CommandException(Texts.of("Must specify a name!"));
                if (args[1].equalsIgnoreCase(GlobalRegion.NAME))
                    throw new CommandException(Texts.of("You may not delete the global Region!"));
                boolean success = FGManager.getInstance().removeRegion(world, args[1]);
                if (!success)
                    throw new ArgumentParseException(Texts.of("No Region exists with that name!"), args[1], 1);

                player.sendMessage(Texts.of(TextColors.GREEN, "Region deleted successfully!"));
            } else if (isAlias(HANDLERS_ALIASES, args[0])) {
                if (args.length < 2) throw new CommandException(Texts.of("Must specify a name!"));
                if (args[1].equalsIgnoreCase(GlobalHandler.NAME))
                    throw new CommandException(Texts.of("You may not delete the global Handler!"));
                boolean success = FGManager.getInstance().removeHandler(args[1]);
                if (!success)
                    throw new ArgumentParseException(Texts.of("No Handler exists with that name!"), args[1], 1);
                player.sendMessage(Texts.of(TextColors.GREEN, "Handler deleted successfully!"));

            } else throw new ArgumentParseException(Texts.of("Not a valid category!"), args[0], 0);
        } else {

        }
        return CommandResult.empty();
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String arguments) throws CommandException {
        return ImmutableList.of();
    }

    @Override
    public boolean testPermission(CommandSource source) {
        return source.hasPermission("foxguard.command.modify.objects.delete");
    }

    @Override
    public Optional<? extends Text> getShortDescription(CommandSource source) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends Text> getHelp(CommandSource source) {
        return Optional.empty();
    }

    @Override
    public Text getUsage(CommandSource source) {
        return Texts.of("delete <region [--w:<world>] | handler> <name>");
    }
}
