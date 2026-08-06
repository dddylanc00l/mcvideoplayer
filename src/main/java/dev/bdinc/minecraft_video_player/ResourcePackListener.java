package dev.bdinc.minecraft_video_player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public class ResourcePackListener implements Listener {

    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {

        if (event.getStatus() ==
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {

            TextComponent message =
                    new TextComponent("§a✔ Resource pack loaded! §e[CLICK TO PLAY]");

            message.setClickEvent(
                    new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/playvideo"
                    )
            );

            event.getPlayer().spigot().sendMessage(message);
        }
    }
}