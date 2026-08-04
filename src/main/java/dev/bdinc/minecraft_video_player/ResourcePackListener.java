package dev.bdinc.minecraft_video_player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.entity.Player;

public class ResourcePackListener implements Listener {


    @EventHandler
    public void onPackLoaded(PlayerResourcePackStatusEvent event) {


        if(event.getStatus()
                == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {


            Main.getInstance()
                    .startVideoAfterPack(
                            event.getPlayer()
                    );

        }

    }

}