package com.hbm.blocks.generic;

import com.google.common.collect.ImmutableMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.render.block.BlockBakeFrame;
import net.minecraft.block.SoundType;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;
// TODO: make BlockConcreteColoredExtStairsSlab if I'll ever manage to meta-fy slabs properly
public class BlockConcreteColoredExtStairs extends BlockStairsEnumMeta {

    public BlockConcreteColoredExtStairs() {
        super(ModBlocks.concrete_colored_ext, SoundType.STONE, "concrete_colored_ext_stairs", BlockConcreteColoredExt.EnumConcreteType.class, true, true);
        this.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
    }
    // to be honest, the only reason I'd do a separated class for colored concrete is because of textures - they are named like concrete_red not concrete.red
    // and I'm lazy af to rename this
    @Override
    protected BlockBakeFrame[] generateBlockFrames(String registryName) {
        BlockBakeFrame[] frames = new BlockBakeFrame[BlockConcreteColoredExt.EnumConcreteType.VALUES.length];
        for (BlockConcreteColoredExt.EnumConcreteType type : BlockConcreteColoredExt.EnumConcreteType.VALUES) {
            String name = "hbm:blocks/concrete_colored_ext." + type.name().toLowerCase(Locale.US);

            frames[type.ordinal()] = new BlockBakeFrame(name) {
                @Override
                public String getBaseModel() {
                    return "minecraft:block/stairs";
                }

                @Override
                public void registerBlockTextures(TextureMap map) {
                    map.registerSprite(new ResourceLocation(name));
                }

                @Override
                public void putTextures(ImmutableMap.Builder<String, String> builder) {
                    builder.put("bottom", name);
                    builder.put("top", name);
                    builder.put("side", name);
                    builder.put("particle", name);
                }
            };
        }
        return frames;
    }
}
