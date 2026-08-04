package dev.bdinc.minecraft_video_player;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StopVideoCommand implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {


        Main.videoRunning = false;

        for (Player player : Bukkit.getOnlinePlayers()) {

            player.stopSound(
                    "videos.current"
            );

        }


        sender.sendMessage(
                "§cVideo stopped."
        );


        return true;
    }
}