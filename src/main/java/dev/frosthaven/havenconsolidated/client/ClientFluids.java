package dev.frosthaven.havenconsolidated.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.frosthaven.havenconsolidated.Aliases;
import dev.frosthaven.havenconsolidated.Content;
import dev.frosthaven.havenconsolidated.HavenConsolidated;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import com.mojang.blaze3d.shaders.FogShape;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Our fluids look like the first source mod's: every client extension call (still and flowing textures, tint, fog,
 * overlay) is passed to that fluid's own extensions, looked up at the first call because the source mod registers
 * its extensions in the same event. Nothing of theirs is copied.
 */
@EventBusSubscriber(modid = HavenConsolidated.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientFluids {
    @SubscribeEvent
    static void onRegister(RegisterClientExtensionsEvent event) {
        for (var e : Content.FLUID_TYPE_BY_ID.entrySet()) {
            event.registerFluidType(new Borrowed(e.getKey()), e.getValue().get());
        }
    }

    private static final class Borrowed implements IClientFluidTypeExtensions {
        private final ResourceLocation ours;
        private IClientFluidTypeExtensions source;

        Borrowed(ResourceLocation ours) {
            this.ours = ours;
        }

        private IClientFluidTypeExtensions source() {
            if (source == null) {
                Fluid art = Aliases.fluidArt(ours);
                source = art != null ? IClientFluidTypeExtensions.of(art) : IClientFluidTypeExtensions.DEFAULT;
                if (source == this) {
                    source = IClientFluidTypeExtensions.DEFAULT;
                }
            }
            return source;
        }

        @Override public int getTintColor() { return source().getTintColor(); }
        @Override public ResourceLocation getStillTexture() { return source().getStillTexture(); }
        @Override public ResourceLocation getFlowingTexture() { return source().getFlowingTexture(); }
        @Override public ResourceLocation getOverlayTexture() { return source().getOverlayTexture(); }
        @Override public ResourceLocation getRenderOverlayTexture(Minecraft mc) { return source().getRenderOverlayTexture(mc); }
        @Override public void renderOverlay(Minecraft mc, PoseStack poseStack) { source().renderOverlay(mc, poseStack); }
        @Override public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
            return source().modifyFogColor(camera, partialTick, level, renderDistance, darkenWorldAmount, fluidFogColor);
        }
        @Override public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
            source().modifyFogRender(camera, mode, renderDistance, partialTick, nearDistance, farDistance, shape);
        }
        @Override public ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) { return source().getStillTexture(state, getter, pos); }
        @Override public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) { return source().getFlowingTexture(state, getter, pos); }
        @Override public ResourceLocation getOverlayTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) { return source().getOverlayTexture(state, getter, pos); }
        @Override public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) { return source().getTintColor(state, getter, pos); }
        @Override public int getTintColor(FluidStack stack) { return source().getTintColor(stack); }
        @Override public ResourceLocation getStillTexture(FluidStack stack) { return source().getStillTexture(stack); }
        @Override public ResourceLocation getFlowingTexture(FluidStack stack) { return source().getFlowingTexture(stack); }
        @Override public ResourceLocation getOverlayTexture(FluidStack stack) { return source().getOverlayTexture(stack); }
        @Override public boolean renderFluid(FluidState fluidState, BlockAndTintGetter getter, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState) {
            return source().renderFluid(fluidState, getter, pos, vertexConsumer, blockState);
        }
    }

    private ClientFluids() {}
}
