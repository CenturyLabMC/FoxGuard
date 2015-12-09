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

package net.foxdenstudio.foxguard;

import com.google.inject.Inject;
import net.foxdenstudio.foxcore.FoxCoreMain;
import net.foxdenstudio.foxcore.state.FCStateRegistry;
import net.foxdenstudio.foxcore.util.Aliases;
import net.foxdenstudio.foxguard.commands.*;
import net.foxdenstudio.foxguard.commands.handlers.CommandPriority;
import net.foxdenstudio.foxguard.listener.BlockEventListener;
import net.foxdenstudio.foxguard.listener.InteractListener;
import net.foxdenstudio.foxguard.listener.PlayerEventListener;
import net.foxdenstudio.foxguard.listener.SpawnEntityEventListener;
import net.foxdenstudio.foxguard.state.HandlersStateField;
import net.foxdenstudio.foxguard.state.RegionsStateField;
import net.foxdenstudio.foxguard.state.factory.HandlersStateFieldFactory;
import net.foxdenstudio.foxguard.state.factory.RegionsStateFieldFactory;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.InteractEvent;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.entity.living.player.TargetPlayerEvent;
import org.spongepowered.api.event.game.state.*;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.event.world.UnloadWorldEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.service.sql.SqlService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.TextBuilder;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;

import javax.sql.DataSource;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static net.foxdenstudio.foxcore.util.Aliases.HANDLERS_ALIASES;

@Plugin(id = "foxguard", name = "FoxGuard", version = FoxGuardMain.PLUGIN_VERSION, dependencies = "required-after:foxcore")
public class FoxGuardMain {

    /**
     * String object containing the current version of the plugin.
     */
    public static final String PLUGIN_VERSION = "SNAPSHOT";

    /**
     * FoxGuardMain instance object.
     */
    private static FoxGuardMain instance;


    @Inject
    private Logger logger;
    @Inject
    private Game game;
    @Inject
    private EventManager eventManager;
    @Inject
    @ConfigDir(sharedRoot = true)
    private File configDirectory;

    private SqlService sql;
    private UserStorageService userStorage;

    private boolean loaded = false;

    /**
     * @return The current instance of the FoxGuardMain object.
     */
    public static FoxGuardMain instance() {
        return instance;
    }

    //my uuid - f275f223-1643-4fac-9fb8-44aaf5b4b371

    /**
     * Used to create a new ReadWriteLock. Depending on config option, will either load a real one, or a "spoof" one.
     *
     * @return A ReadWriteLock Object that can be used.
     */
    public static ReadWriteLock getNewLock() {
        if (FGConfigManager.getInstance().threadSafe()) {
            return new ReentrantReadWriteLock();
        } else {
            return new ReadWriteLock() {

                private final Lock lock = new Lock() {
                    @Override
                    public void lock() {

                    }

                    @Override
                    public void lockInterruptibly() throws InterruptedException {

                    }

                    @Override
                    public boolean tryLock() {
                        return true;
                    }

                    @Override
                    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                        return true;
                    }

                    @Override
                    public void unlock() {

                    }

                    @Override
                    public Condition newCondition() {
                        throw new UnsupportedOperationException();
                    }
                };

                @Override
                public Lock readLock() {
                    return this.lock;
                }

                @Override
                public Lock writeLock() {
                    return this.lock;
                }
            };
        }
    }

    @Listener
    public void gameConstruct(GameConstructionEvent event) {
        instance = this;
    }

    @Listener
    public void gamePreInit(GamePreInitializationEvent event) {
        logger.info("Loading configs");
        new FGConfigManager();
        FGConfigManager.getInstance().save();
    }

    @Listener
    public void gameInit(GameInitializationEvent event) {
        userStorage = game.getServiceManager().provide(UserStorageService.class).get();
        logger.info("Initializing FoxGuard Manager instance");
        FGManager.init();

        logger.info("Registering commands");
        registerCommands();
        logger.info("Registering event listeners");
        registerListeners();
        logger.info("Setting default player permissions");
        configurePermissions();

        FCStateRegistry.instance().registerStateFactory(new RegionsStateFieldFactory(), RegionsStateField.ID, Aliases.REGIONS_ALIASES);
        FCStateRegistry.instance().registerStateFactory(new HandlersStateFieldFactory(), HandlersStateField.ID, Aliases.HANDLERS_ALIASES);
    }

    @Listener
    public void serverStarted(GameStartedServerEvent event) {
        logger.info("Initializing Handlers database");
        FGStorageManager.getInstance().initHandlers();
        logger.info("Loading Handlers");
        FGStorageManager.getInstance().loadHandlers();
        loaded = true;
        logger.info("Loading Links");
        FGStorageManager.getInstance().loadLinks();
        if (FGConfigManager.getInstance().forceLoad()) {
            logger.info("Resolving deferred objects");
            FGStorageManager.getInstance().resolveDeferredObjects();
        }
        logger.info("Saving Handlers");
        FGStorageManager.getInstance().writeHandlers();
        logger.info("Saving World data");
        for (World world : game.getServer().getWorlds()) {
            logger.info("Saving data for World: \"" + world.getName() + "\"");
            FGStorageManager.getInstance().writeWorld(world);
        }
    }

    @Listener
    public void serverStopping(GameStoppingServerEvent event) {
        FGStorageManager.getInstance().writeHandlers();
        logger.info("Saving Handlers");
    }

    @Listener
    public void serverStopped(GameStoppedServerEvent event) {
        if (FGConfigManager.getInstance().purgeDatabases()) {
            FoxGuardMain.instance().logger().info("Purging databases");
            FGStorageManager.getInstance().purgeDatabases();
        }
        logger.info("Saving configs");
        FGConfigManager.getInstance().save();
    }

    @Listener
    public void worldUnload(UnloadWorldEvent event) {
        logger.info("Saving data for World: \"" + event.getTargetWorld().getName() + "\"");
        FGStorageManager.getInstance().writeWorld(event.getTargetWorld());
    }

    @Listener
    public void worldLoad(LoadWorldEvent event) {
        logger.info("Constructing Regions for World: \"" + event.getTargetWorld().getName() + "\"");
        FGManager.getInstance().populateWorld(event.getTargetWorld());
        logger.info("Initializing Regions database for World: \"" + event.getTargetWorld().getName() + "\"");
        FGStorageManager.getInstance().initWorld(event.getTargetWorld());
        logger.info("Loading Regions for World: \"" + event.getTargetWorld().getName() + "\"");
        FGStorageManager.getInstance().loadWorldRegions(event.getTargetWorld());
        if (loaded) {
            if (FGConfigManager.getInstance().forceLoad()) {
                logger.info("Resolving deferred objects for World: " + event.getTargetWorld().getName() + "\"");
                FGStorageManager.getInstance().resolveDeferredObjects();
            }
            logger.info("Loading links for World: \"" + event.getTargetWorld().getName() + "\"");
            FGStorageManager.getInstance().loadWorldLinks(event.getTargetWorld());
        }
    }

    /**
     * @param jdbcUrl A String representation of the connection url for the database.
     * @return DataSource Object that is retrieved based off of the url from the SqlService.
     * @throws SQLException
     */
    public DataSource getDataSource(String jdbcUrl) throws SQLException {
        if (sql == null) {
            sql = game.getServiceManager().provide(SqlService.class).get();
        }
        try {
            return sql.getDataSource(jdbcUrl);
        } catch (SQLException e) {
            e.printStackTrace();
            File file = new File(jdbcUrl.split(":", 3)[2]);
            if (file.exists()) {
                if (!file.delete()) {
                    file.deleteOnExit();
                    throw e;
                } else {
                    return sql.getDataSource(jdbcUrl);
                }
            } else {
                throw e;
            }
        }
    }

    /**
     * A private method that registers the list of commands, their aliases, and the command class.
     */
    private void registerCommands() {
        TextBuilder builder = Texts.builder();
        builder.append(Texts.of(TextColors.GOLD, "FoxGuard World Protection Plugin\n"));
        builder.append(Texts.of("Version: " + FoxGuardMain.PLUGIN_VERSION + "\n"));
        builder.append(Texts.of("Author: gravityfox"));

        FCCommandMainDispatcher fgDispatcher = new FCCommandMainDispatcher("/foxguard");
        FCCommandDispatcher fgRegionDispatcher = new FCCommandDispatcher("/foxguard regions");
        FCCommandDispatcher fgHandlerDispatcher = new FCCommandDispatcher("/foxguard handlers",
                "Commands spcifically meant for managing Handlers.");

        fgDispatcher.register(new CommandCreate(), "create", "construct", "new", "make", "define", "mk", "cr");
        fgDispatcher.register(new CommandDelete(), "delete", "del", "remove", "rem", "rm", "destroy");
        fgDispatcher.register(new CommandModify(), "modify", "mod", "change", "edit", "update", "md", "ch");
        fgDispatcher.register(new CommandLink(), "link", "connect", "attach");
        fgDispatcher.register(new CommandUnlink(), "unlink", "disconnect", "detach");
        fgDispatcher.register(new CommandEnableDisable(true), "enable", "activate", "engage", "on");
        fgDispatcher.register(new CommandEnableDisable(false), "disable", "deactivate", "disengage", "off");
        fgDispatcher.register(new CommandList(), "list", "ls");
        fgDispatcher.register(new CommandDetail(), "detail", "det", "show");
        registerCommonCommands(fgDispatcher);
        fgDispatcher.register(new CommandAbout(builder.build()), "about", "info");
        fgDispatcher.register(new CommandTest(), "test");
        fgDispatcher.register(new CommandSave(), "save", "saveall", "save-all");

        fgHandlerDispatcher.register(new CommandPriority(), "priority", "prio", "level", "rank");

        fgDispatcher.register(fgHandlerDispatcher, HANDLERS_ALIASES);

        game.getCommandManager().register(this, fgDispatcher, "foxguard", "foxg", "fguard", "fg");
    }

    private void registerCommonCommands(FCCommandDispatcher dispatcher) {
        for (CommandMapping mapping : FoxCoreMain.instance().getFCDispatcher().getCommands()) {
            Set<String> secondary = new HashSet<>(mapping.getAllAliases());
            secondary.remove(mapping.getPrimaryAlias());
            dispatcher.register(mapping.getCallable(), mapping.getPrimaryAlias(), new ArrayList<>(secondary));
        }
    }

    /**
     * A private method that registers the Listener class and the corresponding event class.
     */
    private void registerListeners() {
        eventManager.registerListener(this, TargetPlayerEvent.class, new PlayerEventListener());
        eventManager.registerListener(this, ChangeBlockEvent.class, new BlockEventListener());
        eventManager.registerListener(this, InteractEvent.class, new InteractListener());
        eventManager.registerListener(this, SpawnEntityEvent.class, new SpawnEntityEventListener());
    }

    /**
     * A private method that sets up the permissions.
     */
    private void configurePermissions() {
        getPermissionService().getDefaultData().setPermission(SubjectData.GLOBAL_CONTEXT, "foxguard.command.info", Tristate.TRUE);
        getPermissionService().getDefaultData().setPermission(SubjectData.GLOBAL_CONTEXT, "foxguard.override", Tristate.FALSE);
    }

    /**
     * @return A Logger instance for this plugin.
     */
    public Logger logger() {
        return logger;
    }

    public Game game() {
        return game;
    }

    /**
     * Method that when called will return a UserStorage object that can be used to store or retrieve data corresponding to a specific player.
     *
     * @return
     */
    public UserStorageService
    getUserStorage() {
        return userStorage;
    }

    /**
     * @return A File object corresponding to the config of the plugin.
     */
    public File getConfigDirectory() {
        return configDirectory;
    }

    /**
     * A method that when called will return a PermissionService object, which can be used for permission creation/checking
     *
     * @return A PermissionService Object Instance
     */
    public PermissionService getPermissionService() {
        return game.getServiceManager().provide(PermissionService.class).get();
    }

    /**
     * Will return true or false depending on if the plugin has loaded properly or not.
     *
     * @return Depending on the loaded variable
     */
    public boolean isLoaded() {
        return loaded;
    }
}