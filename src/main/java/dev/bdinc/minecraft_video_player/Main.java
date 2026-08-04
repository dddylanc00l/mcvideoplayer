package dev.bdinc.minecraft_video_player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;

public final class Main extends JavaPlugin {

    private static Main instance;
    private HttpServer packServer;
    public static int MAX_WIDTH = 250;
    public static int MAX_HEIGHT = 250;
    public static int MAX_FPS = 30;
    public static boolean speedMode = true;
    public static boolean videoRunning = false;
    private static final File RESOURCE_PACK =
            new File("ResourcePack");

    private static final File SOUND_FOLDER =
            new File(RESOURCE_PACK,
                    "assets/minecraft/sounds/videos");

    private static final File SOUNDS_JSON =
            new File(RESOURCE_PACK,
                    "assets/minecraft/sounds.json");
    private boolean videoStarted = false;
    private File pendingVideo;
    private World pendingWorld;
    private int pendingX;
    private int pendingY;
    private int pendingZ;
    private int[][] lastFramePixels;
    private int cacheWidth = 0;
    private int cacheHeight = 0;
    public static ArrayList<Location> latestLocation = new ArrayList<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        getServer()
                .getPluginManager()
                .registerEvents(
                        new ResourcePackListener(),
                        this
                );
        startResourcePackServer();
        ColorManager.setupColorMap();
        Objects.requireNonNull(getCommand("processimage")).setExecutor(new ProcessImageCommand());
        Objects.requireNonNull(getCommand("processvideo")).setExecutor(new ProcessVideoCommand());
        Objects.requireNonNull(getCommand("processstream")).setExecutor(new ProcessStreamCommand());
        Objects.requireNonNull(getCommand("setres")).setExecutor(new SetResCommand());
        Objects.requireNonNull(getCommand("undoimage")).setExecutor(new UndoCommand());
        Objects.requireNonNull(getCommand("stopvideo")).setExecutor(new StopVideoCommand());
    }

    @Override
    public void onDisable() {

        if(packServer != null) {
            packServer.stop(0);
        }

    }

    public static Main getInstance() {
        return instance;
    }

    public void processImage(BufferedImage image, Location location) {

        World world = location.getWorld();
        int x = location.getBlockX() - MAX_WIDTH / 2;
        int y = location.getBlockY() - 5;
        int z = location.getBlockZ() - MAX_HEIGHT / 2;

        latestLocation.add(location);

        int width = image.getWidth(null);
        int height = image.getHeight(null);

        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            double ratio = (double) width / (double) height;
            if (ratio > 1) {
                width = MAX_WIDTH;
                height = (int) (width / ratio);
            } else {
                height = MAX_HEIGHT;
                width = (int) (height * ratio);
            }
            Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();
            image = bufferedImage;
        }

        pasteImage(world, x, y, z, image);
    }

    public void processStream(File streamDirectory, Location location) {
        StreamFrameProcessor streamFrameProcessor = new StreamFrameProcessor(this, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        streamFrameProcessor.start(streamDirectory);
    }

    public File extractAudio(File video) throws Exception {

        String name = video.getName();

        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }

        File audioFile =
                new File(
                        SOUND_FOLDER,
                        name + ".ogg"
                );

        // Already extracted
        if (audioFile.exists()) {
            Bukkit.broadcastMessage(
                    "§aUsing cached audio: " + audioFile.getName()
            );
            return audioFile;
        }


        Bukkit.broadcastMessage("§eExtracting audio...");

        FFmpegFrameGrabber grabber =
                new FFmpegFrameGrabber(video);

        grabber.start();


        FFmpegFrameRecorder recorder =
                new FFmpegFrameRecorder(
                        audioFile,
                        grabber.getAudioChannels()
                );


        recorder.setFormat("ogg");
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_VORBIS);
        recorder.setSampleRate(grabber.getSampleRate());
        recorder.setAudioChannels(grabber.getAudioChannels());


        recorder.start();


        Frame frame;

        while ((frame = grabber.grabSamples()) != null) {
            recorder.record(frame);
        }


        recorder.stop();
        grabber.stop();


        Bukkit.broadcastMessage("§aAudio extracted!");

        return audioFile;
    }

    public void installSound(File oggFile) throws IOException {

        if (!SOUND_FOLDER.exists()) {
            SOUND_FOLDER.mkdirs();
        }

        File destination =
                new File(SOUND_FOLDER, oggFile.getName());

        if (!destination.exists()) {
            FileUtils.copyFile(oggFile, destination);
        }
    }

    public void rebuildSoundsJson() throws IOException {

        String json =
                "{\n" +
                        "  \"videos.current\": {\n" +
                        "    \"sounds\": [\n" +
                        "      {\n" +
                        "        \"name\": \"videos/current\",\n" +
                        "        \"stream\": true\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}";


        FileUtils.writeStringToFile(
                SOUNDS_JSON,
                json,
                "UTF-8"
        );

        Bukkit.broadcastMessage("§aUpdated sounds.json");
    }

    public File createResourcePackZip() throws IOException {

        File zip = new File("VideoResourcePack.zip");


        FileUtils.deleteQuietly(zip);


        java.util.zip.ZipOutputStream zos =
                new java.util.zip.ZipOutputStream(
                        new java.io.FileOutputStream(zip)
                );


        zipFolder(
                RESOURCE_PACK,
                RESOURCE_PACK,
                zos
        );


        zos.close();


        return zip;
    }



    private void zipFolder(
            File root,
            File source,
            java.util.zip.ZipOutputStream zos
    ) throws IOException {


        for(File file : Objects.requireNonNull(source.listFiles())){


            if(file.isDirectory()){

                zipFolder(
                        root,
                        file,
                        zos
                );

            } else {


                String path =
                        root.toPath()
                                .relativize(file.toPath())
                                .toString()
                                .replace("\\","/");


                zos.putNextEntry(
                        new java.util.zip.ZipEntry(path)
                );


                java.nio.file.Files.copy(
                        file.toPath(),
                        zos
                );


                zos.closeEntry();
            }
        }
    }

    public void processVideo(File video, Location location) {
        World world = location.getWorld();

        int x = location.getBlockX() - MAX_WIDTH / 2;
        int y = location.getBlockY() - 10;
        int z = location.getBlockZ() - MAX_HEIGHT / 2;

        latestLocation.add(location);


        Bukkit.broadcastMessage("§aLoading video: " + video.getName());


        File resizedFile;

        try {

            Bukkit.broadcastMessage("§aResizing video...");

            resizedFile = resizeVideo(
                    video,
                    MAX_WIDTH,
                    MAX_HEIGHT
            );

            FileUtils.cleanDirectory(SOUND_FOLDER);

            File extracted = extractAudio(video);

            File current =
                    new File(SOUND_FOLDER, "current.ogg");

            Files.copy(
                    extracted.toPath(),
                    current.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

// Build the pack
            rebuildSoundsJson();
            createResourcePackZip();

        } catch (Exception e) {

            e.printStackTrace();
            Bukkit.broadcastMessage("§cVideo resize failed!");
            return;
        }

        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        String url = "http://" + ip + ":8080/VideoResourcePack.zip?v=" + System.currentTimeMillis();

        Bukkit.getLogger().info("Resource pack URL: " + url);

        pendingVideo = resizedFile;
        pendingWorld = world;
        pendingX = x;
        pendingY = y;
        pendingZ = z;

        for(Player player : Bukkit.getOnlinePlayers()){


            player.setResourcePack(
                    url,
                    null,
                    "Video Player Audio Pack",
                    false
            );


        }

    }



    public File resizeVideo(File video, int width, int height)
            throws FrameGrabber.Exception, FrameRecorder.Exception {


        String name = video.getName();

        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }


        File cachedVideo = new File(
                video.getParentFile(),
                name + "_" + width + "x" + height + ".mp4"
        );


        // Use cached resize
        if (cachedVideo.exists()) {

            Bukkit.broadcastMessage(
                    "§aUsing cached resize: "
                            + cachedVideo.getName()
            );

            return cachedVideo;
        }



        Bukkit.broadcastMessage(
                "§eCreating resized video..."
        );


        long startTime = System.currentTimeMillis();



        FFmpegFrameGrabber frameGrabber =
                new FFmpegFrameGrabber(video);


        frameGrabber.start();



        double aspectRatio =
                (double) frameGrabber.getImageWidth()
                        /
                        frameGrabber.getImageHeight();



        int newWidth;
        int newHeight;


        double targetRatio =
                (double) width / height;



        if (aspectRatio > targetRatio) {

            newWidth = width;
            newHeight =
                    (int)(width / aspectRatio);

        } else {

            newHeight = height;
            newWidth =
                    (int)(height * aspectRatio);

        }



        FFmpegFrameRecorder recorder =
                new FFmpegFrameRecorder(
                        cachedVideo,
                        newWidth,
                        newHeight
                );


        recorder.setVideoCodec(
                frameGrabber.getVideoCodec()
        );

        recorder.setFormat("mp4");

        recorder.setFrameRate(
                frameGrabber.getFrameRate()
        );

        recorder.setPixelFormat(
                avutil.AV_PIX_FMT_YUV420P
        );


        recorder.start();



        OpenCVFrameConverter.ToMat converter =
                new OpenCVFrameConverter.ToMat();



        Frame frame;


        while ((frame = frameGrabber.grab()) != null) {


            Mat mat =
                    converter.convertToMat(frame);


            if (mat == null)
                continue;



            Mat resized =
                    new Mat();


            org.bytedeco.opencv.global.opencv_imgproc.resize(
                    mat,
                    resized,
                    new Size(
                            newWidth,
                            newHeight
                    )
            );


            Frame resizedFrame =
                    converter.convert(resized);


            recorder.record(resizedFrame);

        }



        frameGrabber.stop();

        recorder.stop();



        long endTime =
                System.currentTimeMillis();



        Bukkit.broadcastMessage(
                "§aResize complete: "
                        + (endTime-startTime)
                        + "ms"
        );


        return cachedVideo;
    }


    void pasteImage(World world, int x, int y, int z, BufferedImage image) {


        if(lastFramePixels == null
                || cacheWidth != image.getWidth()
                || cacheHeight != image.getHeight()) {


            cacheWidth = image.getWidth();
            cacheHeight = image.getHeight();

            lastFramePixels =
                    new int[cacheWidth][cacheHeight];
        }



        for(int i = 0; i < cacheWidth; i++) {

            for(int j = 0; j < cacheHeight; j++) {


                int rgb =
                        image.getRGB(i,j);



                if(lastFramePixels[i][j] == rgb) {
                    continue;
                }


                lastFramePixels[i][j] = rgb;



                Material material =
                        ColorManager.getBlock(rgb);



                setBlockInNativeWorld(
                        world,
                        x+i,
                        y,
                        z+j,
                        material,
                        false
                );

            }
        }
    }

    public void undoLastImage() {
        Location location = latestLocation.get(latestLocation.size() - 1);
        World world = location.getWorld();
        int x = location.getBlockX() - MAX_WIDTH / 2;
        int y = location.getBlockY() - 10;
        int z = location.getBlockZ() - MAX_HEIGHT / 2;

        for (int i = 0; i < MAX_WIDTH; i++) {
            for (int j = 0; j < MAX_HEIGHT; j++) {
                assert world != null;
                setBlockInNativeWorld(world, x + i, y, z + j, Material.AIR, false);
            }
        }
    }

    public BufferedImage getImageFromURL(URL url) {
        try {
            return ImageIO.read(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Download video file from URL and save it then return the file path
    public File getVideoFromURL(URL url) {
        long startTime = System.currentTimeMillis();
        try {
            // Create random file name
            String fileName = "video" + System.currentTimeMillis() + ".mp4";
            File file = new File(fileName);
            FileUtils.copyURLToFile(url, file);
            long endTime = System.currentTimeMillis();
            Bukkit.broadcastMessage("Time taken to download: " + (endTime - startTime) + "ms");
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void setBlockInNativeWorld(World world, int x, int y, int z, Material material, boolean applyPhysics) {
        ServerLevel nmsWorld = ((CraftWorld) world).getHandle();
        BlockPos blockPos = new BlockPos(x, y, z);
        nmsWorld.setBlock(blockPos, CraftMagicNumbers.getBlock(material).defaultBlockState(), applyPhysics ? 3 : 2);
    }

    public void startResourcePackServer() {

        try {

            packServer = HttpServer.create(
                    new InetSocketAddress(8080),
                    0
            );


            packServer.createContext(
                    "/VideoResourcePack.zip",
                    exchange -> {


                        File file =
                                new File("VideoResourcePack.zip");


                        if(!file.exists()) {

                            exchange.sendResponseHeaders(
                                    404,
                                    0
                            );

                            exchange.close();
                            return;
                        }


                        byte[] data =
                                Files.readAllBytes(
                                        file.toPath()
                                );


                        exchange.getResponseHeaders()
                                .add(
                                        "Content-Type",
                                        "application/zip"
                                );


                        exchange.sendResponseHeaders(
                                200,
                                data.length
                        );


                        exchange.getResponseBody()
                                .write(data);


                        exchange.close();

                    }
            );


            packServer.start();


            Bukkit.broadcastMessage(
                    "§aResource pack server started on port 8080"
            );


        } catch(Exception e) {

            e.printStackTrace();

        }

    }

    private void startVideoThread(
            File resizedFile,
            World world,
            int x,
            int y,
            int z
    ) {


        try {


            if(resizedFile == null || !resizedFile.exists()) {

                Bukkit.broadcastMessage(
                        "§cVideo file missing!"
                );

                return;
            }


            FFmpegFrameGrabber grabber =
                    new FFmpegFrameGrabber(resizedFile);


            Java2DFrameConverter converter =
                    new Java2DFrameConverter();


            VideoFrameProcessor processor =
                    new VideoFrameProcessor(
                            this,
                            world,
                            x,
                            y,
                            z
                    );


            processor.start();



            new Thread(() -> {


                try {


                    grabber.start();


                    long lastFrameTime =
                            System.currentTimeMillis();


                    Frame frame;


                    Main.videoRunning = true;



                    while(Main.videoRunning) {


                        if(grabber.getLengthInFrames()
                                <= grabber.getFrameNumber()) {

                            break;

                        }



                        double fps =
                                grabber.getFrameRate();


                        if(fps <= 0)
                            fps = MAX_FPS;



                        long frameDelay =
                                (long)(1000 /
                                        Math.min(
                                                fps,
                                                MAX_FPS
                                        ));



                        if(System.currentTimeMillis()
                                - lastFrameTime
                                >= frameDelay) {



                            frame =
                                    grabber.grab();



                            BufferedImage image =
                                    converter.getBufferedImage(frame);



                            if(image != null) {


                                processor.addFrame(image);


                                lastFrameTime =
                                        System.currentTimeMillis();

                            }

                        }

                    }


                    grabber.stop();

                    Main.videoRunning = false;
                    videoStarted = false;

                    for(Player player : Bukkit.getOnlinePlayers()) {

                        stopVideoSound(player);

                    }

                    Bukkit.broadcastMessage(
                            "§aVideo finished!"

                    );
                    lastFramePixels = null;

                } catch(Exception e) {

                    e.printStackTrace();

                }


            }).start();



        } catch(Exception e) {

            e.printStackTrace();

        }

    }

    public void startVideoAfterPack(Player player) {
        if(videoStarted)
            return;

        videoStarted = true;

        if(pendingVideo == null)
            return;


        stopVideoSound(player);


        player.playSound(
                player.getLocation(),
                "videos.current",
                SoundCategory.RECORDS,
                1f,
                1f
        );


        File video = pendingVideo;
        World world = pendingWorld;
        int x = pendingX;
        int y = pendingY;
        int z = pendingZ;


        Bukkit.getScheduler().runTaskLater(
                this,
                () -> {

                    startVideoThread(
                            video,
                            world,
                            x,
                            y,
                            z
                    );

                },
                10L
        );


        pendingVideo = null;

    }

    private void stopVideoSound(Player player) {

        for(SoundCategory category : SoundCategory.values()) {

            player.stopSound(
                    "videos.current",
                    category
            );

        }

    }

}
