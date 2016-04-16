package net.foxdenstudio.sponge.foxguard.plugin.listener;

import com.flowpowered.math.GenericMath;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import net.foxdenstudio.sponge.foxcore.plugin.command.CommandHUD;
import net.foxdenstudio.sponge.foxcore.plugin.util.CacheMap;
import net.foxdenstudio.sponge.foxguard.plugin.FGManager;
import net.foxdenstudio.sponge.foxguard.plugin.Flag;
import net.foxdenstudio.sponge.foxguard.plugin.event.FGUpdateEvent;
import net.foxdenstudio.sponge.foxguard.plugin.handler.IHandler;
import net.foxdenstudio.sponge.foxguard.plugin.object.IFGObject;
import net.foxdenstudio.sponge.foxguard.plugin.region.IRegion;
import net.foxdenstudio.sponge.foxguard.plugin.util.FGUtil;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.scoreboard.Score;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.scoreboard.critieria.Criteria;
import org.spongepowered.api.scoreboard.displayslot.DisplaySlots;
import org.spongepowered.api.scoreboard.objective.Objective;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;

import java.util.*;

/**
 * Created by Fox on 1/4/2016.
 * Project: SpongeForge
 */
public class PlayerMoveListener implements EventListener<DisplaceEntityEvent> {

    private static final LastWrapper EMPTY_LAST_WRAPPER = new LastWrapper(null, null);

    private static PlayerMoveListener instance;

    public final boolean full;

    private final Map<Player, LastWrapper> last = new CacheMap<>((key, map) -> EMPTY_LAST_WRAPPER);
    private final Map<Player, Scoreboard> scoreboardMap = new CacheMap<>((k, m) -> {
        if (k instanceof Player) {
            Scoreboard s = Scoreboard.builder().build();
            Objective o = Objective.builder().criterion(Criteria.DUMMY).name("foxguardhere").build();
            s.addObjective(o);
            s.updateDisplaySlot(o, DisplaySlots.SIDEBAR);
            m.put((Player) k, s);
            return s;
        } else return null;
    });
    private final Map<Player, HUDConfig> hudConfigMap = new CacheMap<>((k, m) -> new HUDConfig());

    public PlayerMoveListener(boolean full) {
        this.full = full;
        if (instance == null) instance = this;
    }

    @Override
    public void handle(DisplaceEntityEvent event) throws Exception {

        if (event.isCancelled()) return;
        Player player;
        if (event.getTargetEntity().getPassenger().isPresent() && event.getTargetEntity().getPassenger().get() instanceof Player) {
            player = (Player) event.getTargetEntity().getPassenger().get();
        } else if (event instanceof DisplaceEntityEvent.TargetPlayer) {
            player = ((DisplaceEntityEvent.TargetPlayer) event).getTargetEntity();
        } else return;

        final boolean hud = CommandHUD.instance().getIsHUDEnabled().get(player) && player.getScoreboard() == scoreboardMap.get(player);
        final HUDConfig config = this.hudConfigMap.get(player);
        final boolean regionHUD = hud && config.regions;

        World world = event.getTargetEntity().getWorld();
        List<IHandler> fromList = last.get(player).list, toList = new ArrayList<>();
        List<IRegion> regionList = new ArrayList<>();
        Vector3d to = event.getToTransform().getPosition().add(0, 0.1, 0);
        if (fromList == null) {
            fromList = new ArrayList<>();
            final List<IHandler> temp = fromList;
            Vector3d from = event.getFromTransform().getPosition().add(0, 0.1, 0);
            FGManager.getInstance().getAllRegions(world, new Vector3i(
                    GenericMath.floor(from.getX() / 16.0),
                    GenericMath.floor(from.getY() / 16.0),
                    GenericMath.floor(from.getZ() / 16.0))).stream()
                    .filter(region -> region.contains(from, world))
                    .forEach(region -> region.getHandlers().stream()
                            .filter(IFGObject::isEnabled)
                            .filter(handler -> !temp.contains(handler))
                            .forEach(temp::add));
        } else {
            fromList = new ArrayList<>(fromList);
        }
        FGManager.getInstance().getAllRegions(world, new Vector3i(
                GenericMath.floor(to.getX() / 16.0),
                GenericMath.floor(to.getY() / 16.0),
                GenericMath.floor(to.getZ() / 16.0))).stream()
                .filter(region -> region.contains(to, world))
                .forEach(region -> {
                    if (regionHUD) regionList.add(region);
                    region.getHandlers().stream()
                            .filter(IFGObject::isEnabled)
                            .filter(handler -> !toList.contains(handler))
                            .forEach(toList::add);
                });

        final List<IHandler> toComplete = new ArrayList<>(toList);

        final List<IHandler> temp = fromList;
        ImmutableList.copyOf(fromList).stream()
                .filter(toList::contains)
                .forEach(handler -> {
                    temp.remove(handler);
                    toList.remove(handler);
                });
        List<HandlerWrapper> finalList = new ArrayList<>();
        fromList.stream()
                .map(handler -> new HandlerWrapper(handler, Type.FROM))
                .forEach(finalList::add);
        toList.stream()
                .map(handler -> new HandlerWrapper(handler, Type.TO))
                .forEach(finalList::add);
        if (finalList.size() == 0) {
            this.last.put(player, new LastWrapper(toComplete, event.getToTransform().getPosition()));
            return;
        }
        if (full) {
            Collections.sort(finalList);
            int currPriority = finalList.get(0).handler.getPriority();
            Tristate flagState = Tristate.UNDEFINED;
            for (HandlerWrapper wrap : finalList) {
                if (wrap.handler.getPriority() < currPriority && flagState != Tristate.UNDEFINED) {
                    break;
                }
                if (wrap.type == Type.FROM) {
                    flagState = flagState.and(wrap.handler.handle(player, Flag.PLAYER_EXIT, Optional.of(event)).getState());
                } else {
                    flagState = flagState.and(wrap.handler.handle(player, Flag.PLAYER_ENTER, Optional.of(event)).getState());
                }
                currPriority = wrap.handler.getPriority();
            }
            flagState = Flag.PLAYER_PASS.resolve(flagState);

            if (flagState == Tristate.FALSE) {
                player.sendMessage(ChatTypes.ACTION_BAR, Text.of("You don't have permission to pass!"));
                Vector3d position = this.last.get(player).position;
                if (position == null) position = event.getFromTransform().getPosition();
                event.setToTransform(event.getToTransform().setPosition(position));
            } else {
                this.last.put(player, new LastWrapper(toComplete, event.getToTransform().getPosition()));
                //makes sure that handlers are unable to cancel the event directly.
                event.setCancelled(false);
                if (hud) {
                    renderHUD(player, regionList, toComplete, config);
                }
            }
        } else if (hud) {
            renderHUD(player, regionList, toComplete, config);
        }
    }

    public void renderHUD(Player player, List<IRegion> regions, List<IHandler> handlers, HUDConfig config) {
        this.scoreboardMap.remove(player);
        Scoreboard scoreboard = this.scoreboardMap.get(player);
        Objective objective = scoreboard.getObjective("foxguardhere").get();
        int slot = 15;
        if (config.regions) {
            Collections.sort(regions, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
            if (config.handlers) {
                if (config.priority) {
                    Collections.sort(handlers, (o1, o2) -> o1.getPriority() - o2.getPriority());
                } else {
                    Collections.sort(handlers, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                }
                objective.setDisplayName(Text.of(TextColors.GOLD, "Regions and Handlers Located Here"));
                final int total = regions.size() + handlers.size();
                final int regionCount = (int) Math.round(13.0 * regions.size() / total);
                final int handlerCount = (int) Math.round(13.0 * handlers.size() / total);
                Score regionsScore = objective.getOrCreateScore(Text.of(TextColors.GREEN, "Regions (" + player.getWorld().getName() + ") ",
                        TextColors.YELLOW, "(" + regions.size() + ")"));
                regionsScore.setScore(slot--);
                for (int i = 0; i < regionCount; i++) {
                    IRegion region = regions.get(i);
                    Score score = objective.getOrCreateScore(Text.of(FGUtil.getColorForObject(region),
                            FGUtil.getRegionName(region, false)));
                    score.setScore(slot--);
                    objective.addScore(score);
                }
                Score handlersScore = objective.getOrCreateScore(Text.of(TextColors.GREEN, "Handlers ",
                        TextColors.YELLOW, "(" + handlers.size() + ")"));
                handlersScore.setScore(slot--);
                for (int i = 0; i < handlerCount; i++) {
                    IHandler handler = handlers.get(i);
                    Score score = objective.getOrCreateScore(Text.of(FGUtil.getColorForObject(handler),
                            handler.getShortTypeName() + " : " + handler.getName()));
                    score.setScore(slot--);
                    objective.addScore(score);
                }
                
            } else {
                objective.setDisplayName(Text.of(TextColors.GOLD, "Regions Located Here (" + player.getWorld().getName() + ")"));
                for (IRegion region : regions) {
                    Score score = objective.getOrCreateScore(Text.of(FGUtil.getColorForObject(region),
                            FGUtil.getRegionName(region, false)));
                    score.setScore(slot--);
                    objective.addScore(score);
                    if (slot <= 0) break;
                }
            }
        } else if (config.handlers) {
            if (config.priority) {
                objective.setDisplayName(Text.of(TextColors.GOLD, "Handlers Located Here by Priority"));
                for (IHandler handler : handlers) {
                    Score score = objective.getOrCreateScore(Text.of(FGUtil.getColorForObject(handler),
                            handler.getShortTypeName() + " : " + handler.getName()));
                    score.setScore(handler.getPriority());
                    objective.addScore(score);
                }
            } else {
                objective.setDisplayName(Text.of(TextColors.GOLD, "Handlers Located Here"));
                Collections.sort(handlers, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                for (IHandler handler : handlers) {
                    Score score = objective.getOrCreateScore(Text.of(FGUtil.getColorForObject(handler),
                            handler.getShortTypeName() + " : " + handler.getName()));
                    score.setScore(slot--);
                    objective.addScore(score);
                    if (slot <= 0) break;
                }
            }
        }
    }

    public Map<Player, HUDConfig> getHudConfigMap() {
        return hudConfigMap;
    }

    public void showScoreboard(Player player) {
        player.setScoreboard(this.scoreboardMap.get(player));
    }

    public static PlayerMoveListener getInstance() {
        return instance;
    }

    private enum Type {
        FROM, TO
    }

    private class HandlerWrapper implements Comparable<HandlerWrapper> {
        public IHandler handler;
        public Type type;

        public HandlerWrapper(IHandler handler, Type type) {
            this.handler = handler;
            this.type = type;
        }

        @Override
        public int compareTo(HandlerWrapper w) {
            int val = handler.compareTo(w.handler);
            return val != 0 ? val : type.compareTo(w.type);
        }

        @Override
        public String toString() {
            return this.type + ":" + this.handler;
        }
    }

    private static class LastWrapper {
        public List<IHandler> list;
        public Vector3d position;

        public LastWrapper(List<IHandler> list, Vector3d position) {
            this.list = list;
            this.position = position;
        }
    }

    public static class HUDConfig {
        public boolean regions;
        public boolean handlers;
        public boolean priority;

        public HUDConfig() {
            this(true, true, false);
        }

        public HUDConfig(boolean regions, boolean handlers, boolean priority) {
            this.regions = regions;
            this.handlers = handlers;
            this.priority = priority;
        }
    }

    public class Listeners {
        @Listener
        public void onJoin(ClientConnectionEvent.Join event) {
            last.put(event.getTargetEntity(), new LastWrapper(null, event.getTargetEntity().getTransform().getPosition()));
        }

        /*@Listener
        public void onPlayerChangeWorld() {

        }*/

        @Listener
        public void onChange(FGUpdateEvent event) {
            last.clear();
        }
    }
}
