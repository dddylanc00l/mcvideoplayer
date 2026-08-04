package dev.bdinc.minecraft_video_player;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URL;

public class ProcessVideoCommand implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {


        if (!(sender instanceof Player)) {

            sender.sendMessage(
                    "Only players can use this command."
            );

            return true;
        }


        if (args.length != 1) {

            sender.sendMessage(
                    "Usage: /processvideo <file.mp4 or url>"
            );

            return true;
        }


        Player player = (Player)sender;


        String input = args[0];


        try {


            File folder =
                    new File("plugins/videos");


            if (!folder.exists()) {
                folder.mkdirs();
            }



            File localFile =
                    new File(folder, input);



            // Local MP4 exists
            if (localFile.exists()) {


                sender.sendMessage(
                        "§aUsing local file: "
                                + localFile.getName()
                );


                Main.getInstance()
                        .processVideo(
                                localFile,
                                player.getLocation()
                        );


            }

            // URL
            else {


                sender.sendMessage(
                        "§aDownloading video..."
                );


                File downloaded =
                        Main.getInstance()
                                .getVideoFromURL(
                                        new URL(input)
                                );


                Main.getInstance()
                        .processVideo(
                                downloaded,
                                player.getLocation()
                        );

            }



        } catch (Exception e) {

            e.printStackTrace();

            sender.sendMessage(
                    "§cCould not load video."
            );
        }


        return true;
    }
}