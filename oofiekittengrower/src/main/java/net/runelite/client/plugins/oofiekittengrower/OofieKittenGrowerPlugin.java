package net.runelite.client.plugins.oofiekittengrower;

import com.google.inject.Injector;
import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameTick;
import net.runelite.api.queries.PlayerQuery;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import com.owain.chinbreakhandler.ChinBreakHandler;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.NPCUtils;
import net.runelite.client.plugins.oofiekittengrower.tasks.FeedTask;
import net.runelite.client.plugins.oofiekittengrower.tasks.MovingTask;
import net.runelite.client.plugins.oofiekittengrower.tasks.PetTask;
import net.runelite.client.plugins.oofiekittengrower.tasks.TimeoutTask;
import net.runelite.client.plugins.iutils.TimeoutUntil;
import net.runelite.client.plugins.iutils.ConditionTimeout;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
    name = "Oofie's Kitten Grower",
    enabledByDefault = false,
    description = "Grows Kitty to Kat :)",
    tags = {"kitten", "cat", "grower", "kat", "oofie"}
)
@Slf4j
public class OofieKittenGrowerPlugin extends Plugin
{
    @Inject
    private Injector injector;

    @Inject
    private Client client;

    @Inject
    private OofieKittenGrowerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private OofieKittenGrowerOverlay overlay;

    @Inject
    private iUtils utils;

    @Inject
    private NPCUtils npc;

    @Inject
    private MouseUtils mouse;

    @Inject
    public ChinBreakHandler chinBreakHandler;

    @Inject
    private ConfigManager configManager;

    private TaskSet tasks = new TaskSet();
    public static LocalPoint beforeLoc = new LocalPoint(0, 0);

    MenuEntry targetMenu;
    Instant botTimer;
    Player player;

    public static boolean needToFeed = false;
    public static boolean needToPet = false;

    public static boolean startBot;
    public static long sleepLength;
    public static int tickLength;
    public static int timeout;
    public static String status = "starting...";
    public static ConditionTimeout conditionTimeout;
    public static boolean timeoutFinished;

    @Provides
    OofieKittenGrowerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OofieKittenGrowerConfig.class);
    }

    @Override
    protected void startUp()
    {
        chinBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown()
    {
        resetVals();
        chinBreakHandler.unregisterPlugin(this);
    }


    private void loadTasks()
    {
        tasks.clear();
        tasks.addAll(
            injector.getInstance(TimeoutTask.class),
            injector.getInstance(MovingTask.class),
            injector.getInstance(FeedTask.class),
            injector.getInstance(PetTask.class)
        );
    }

    public void resetVals()
    {
        log.debug("stopping Task Template plugin");
        overlayManager.remove(overlay);
        chinBreakHandler.stopPlugin(this);
        startBot = false;
        botTimer = null;
        tasks.clear();
    }

    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
    {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("OofieKittenGrower"))
        {
            return;
        }
        log.debug("button {} pressed!", configButtonClicked.getKey());
        if (configButtonClicked.getKey().equals("startButton"))
        {
            if (!startBot)
            {
                Player player = client.getLocalPlayer();
                if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
                {
                    log.info("starting Task Template plugin");
                    loadTasks();
                    startBot = true;
                    chinBreakHandler.startPlugin(this);
                    timeout = 0;
                    targetMenu = null;
                    botTimer = Instant.now();
                    overlayManager.add(overlay);
                    beforeLoc = client.getLocalPlayer().getLocalLocation();
                }
                else
                {
                    log.info("Start logged in");
                }
            }
            else
            {
                resetVals();
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getMessage().equals("Your kitten is very hungry.") || event.getMessage().equals("Your kitten is hungry.") || event.getMessage().toLowerCase().contains("hungry")) {
            needToFeed = true;
            log.info("need to feed  is true");
            return;
        }
        if (event.getMessage().equals("Your kitten wants attention.") || event.getMessage().contains("attention") || event.getMessage().toLowerCase().contains("attention")) {
            needToPet = true;
            log.info("need to pet is true");
            return;
        }
    }


    @Subscribe
    private void onGameTick(GameTick event)
    {
        if (!startBot || chinBreakHandler.isBreakActive(this))
        {
            return;
        }
        player = client.getLocalPlayer();
        if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
        {
            if (chinBreakHandler.shouldBreak(this))
            {
                status = "Taking a break";
                chinBreakHandler.startBreak(this);
                timeout = 5;
            }

            Task task = tasks.getValidTask();
            if (task != null)
            {
                status = task.getTaskDescription();
                task.onGameTick(event);

                if (timeoutFinished)
                {
                    if (timeout > 0)
                    {
                        return;
                    }

                    Task newTask = tasks.getValidTask();
                    if (newTask != null)
                    {
                        newTask.onGameTick(event);
                        status = task.getTaskDescription();
                    } else
                    {
                        status = "Idle";
                    }

                    timeoutFinished = false;
                }
            }
            else
            {
                status = "Task not found";
                log.debug(status);
            }
            beforeLoc = player.getLocalLocation();
        }
    }


    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        NPC kitten = npc.findNearestNpc("Kitten");
        Widget whistle = client.getWidget(387, 8);

        if (event.getCommand().equalsIgnoreCase("pet")) needToPet = true;
        if (event.getCommand().equalsIgnoreCase("feed")) needToFeed = true;
        if (event.getCommand().equalsIgnoreCase("pick"))
        {
            targetMenu = new MenuEntry("", "", kitten.getIndex(), MenuAction.NPC_FIRST_OPTION.getId(), 0, 0, false);
            utils.doActionGameTick(targetMenu, new Point(0, 0), timeout = 2);
        }

        if (event.getCommand().equalsIgnoreCase("call"))
        {
            targetMenu = new MenuEntry("", "", 1, MenuAction.CC_OP.getId(), -1, 25362439, false);
            utils.doActionMsTime(targetMenu, whistle.getBounds(), sleepLength);
        }

        if (event.getCommand().equalsIgnoreCase("trade"))
        {
            Player targetPlayer = new PlayerQuery()
                    .nameContains("Legend")
                    .result(client)
                    .nearestTo(client.getLocalPlayer());
            if (targetPlayer != null) {
                targetMenu = new MenuEntry("", "", targetPlayer.getPlayerId(), MenuAction.PLAYER_FOURTH_OPTION.getId(), 0, 0, false);
                utils.doActionMsTime(targetMenu, targetPlayer.getConvexHull().getBounds(), 50);
            }
        }
    }
}