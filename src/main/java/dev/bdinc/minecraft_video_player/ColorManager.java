package dev.bdinc.minecraft_video_player;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_19_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftMagicNumbers;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ColorManager {

    // Materials that can be used
    private static Material[] materials;

    // RGB values (0xRRGGBB)
    private static int[] materialColors;

    // 32 * 32 * 32 lookup table
    private static final Material[] pixelCache =
            new Material[32 * 32 * 32];

    public static Color getColor(Block block) {
        CraftBlock cb = (CraftBlock) block;
        net.minecraft.world.level.block.state.BlockState bs = cb.getNMS();
        net.minecraft.world.level.material.MaterialColor mc =
                bs.getMapColor(
                        cb.getCraftWorld().getHandle(),
                        cb.getPosition()
                );

        return new Color(mc.col);
    }

    public static Color getColor(Material material) {
        net.minecraft.world.level.block.Block block =
                CraftMagicNumbers.getBlock(material);

        net.minecraft.world.level.material.MaterialColor mc =
                block.defaultMaterialColor();

        return new Color(mc.col);
    }

    public static void setupColorMap() {

        List<Material> materialList = new ArrayList<>();
        List<Integer> colorList = new ArrayList<>();

        World world = Bukkit.getWorlds().get(0);
        Block defaultBlock = world.getBlockAt(0, 0, 0);

        for (Material material : Material.values()) {

            if (!material.isBlock()
                    || material.isAir()
                    || !material.isSolid())
                continue;

            if (material.name().contains("GLASS"))
                continue;

            if (material == Material.BARRIER)
                continue;

            if (material.name().contains("POWDER"))
                continue;

            if (material.name().contains("SAND"))
                continue;

            if (material.name().contains("GRAVEL"))
                continue;

            if (material == Material.REDSTONE_LAMP)
                continue;

            if (material.name().contains("SHULKER"))
                continue;

            if (material.name().contains("GLAZED"))
                continue;

            if (Main.speedMode) {

                String name = material.name();

                if (!(name.contains("WOOL")
                        || name.contains("CONCRETE")
                        || name.contains("TERRACOTTA")))
                    continue;
            }

            materialList.add(material);

            Color c = getColor(material);

            colorList.add(
                    (c.getRed() << 16)
                            | (c.getGreen() << 8)
                            | c.getBlue()
            );
        }

        materials =
                materialList.toArray(new Material[0]);

        materialColors =
                new int[colorList.size()];

        for (int i = 0; i < colorList.size(); i++) {
            materialColors[i] = colorList.get(i);
        }

        world.getBlockAt(0, 0, 0).setType(defaultBlock.getType());
        world.getBlockAt(0, 0, 0).setBlockData(defaultBlock.getBlockData(), false);
    }

    public static Material getBlock(int rgb) {

        int r = ((rgb >> 16) & 0xFF) & 0xF8;
        int g = ((rgb >> 8) & 0xFF) & 0xF8;
        int b = (rgb & 0xFF) & 0xF8;

        int index =
                ((r >> 3) << 10)
                        | ((g >> 3) << 5)
                        | (b >> 3);

        Material cached = pixelCache[index];

        if (cached != null) {
            return cached;
        }

        int bestDistance = Integer.MAX_VALUE;
        Material bestMaterial = Material.AIR;

        for (int i = 0; i < materials.length; i++) {

            int mc = materialColors[i];

            int mr = (mc >> 16) & 0xFF;
            int mg = (mc >> 8) & 0xFF;
            int mb = mc & 0xFF;

            int dr = r - mr;
            int dg = g - mg;
            int db = b - mb;

            int distance =
                    dr * dr +
                            dg * dg +
                            db * db;

            if (distance < bestDistance) {

                bestDistance = distance;
                bestMaterial = materials[i];

            }
        }

        pixelCache[index] = bestMaterial;

        return bestMaterial;
    }

    public static boolean isCube(Block block) {
        VoxelShape voxelShape = block.getCollisionShape();
        BoundingBox boundingBox = block.getBoundingBox();

        return voxelShape.getBoundingBoxes().size() == 1
                && boundingBox.getWidthX() == 1.0
                && boundingBox.getHeight() == 1.0
                && boundingBox.getWidthZ() == 1.0;
    }
}