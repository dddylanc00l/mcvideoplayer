package dev.bdinc.minecraft_video_player;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.image.BufferedImage;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoFrameProcessor {

    private final Main plugin;
    private final World world;

    private final int x;
    private final int y;
    private final int z;

    private final Queue<BufferedImage> frames =
            new LinkedBlockingQueue<>(1);


    public VideoFrameProcessor(
            Main plugin,
            World world,
            int x,
            int y,
            int z
    ) {

        this.plugin = plugin;
        this.world = world;

        this.x = x;
        this.y = y;
        this.z = z;

    }


    public void start() {

        new BukkitRunnable() {

            @Override
            public void run() {

                BufferedImage frame =
                        frames.poll();


                if(frame != null) {

                    long start =
                            System.currentTimeMillis();


                    plugin.pasteImage(
                            world,
                            x,
                            y,
                            z,
                            frame
                    );


                    long time =
                            System.currentTimeMillis()
                                    - start;


                    if(time > 30) {

                        plugin.getLogger()
                                .warning(
                                        "Frame paste took "
                                                + time
                                                + "ms"
                                );

                    }

                }

            }

        }.runTaskTimer(
                plugin,
                0,
                1
        );

    }


    public void addFrame(BufferedImage image) {

        frames.clear();

        frames.offer(image);

    }

}