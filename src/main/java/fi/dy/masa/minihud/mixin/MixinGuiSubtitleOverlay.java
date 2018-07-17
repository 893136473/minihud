package fi.dy.masa.minihud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.minihud.event.RenderEventHandler;
import net.minecraft.client.gui.GuiSubtitleOverlay;
import net.minecraft.client.renderer.GlStateManager;

@Mixin(GuiSubtitleOverlay.class)
public class MixinGuiSubtitleOverlay
{
    @Inject(method = "renderSubtitles", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;tryBlendFuncSeparate(" +
            "Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;" +
            "Lnet/minecraft/client/renderer/GlStateManager$DestFactor;" +
            "Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;" +
            "Lnet/minecraft/client/renderer/GlStateManager$DestFactor;)V",
            shift = Shift.AFTER))
    private void nudgeSubtitleOverlay(CallbackInfo ci)
    {
        int offset = RenderEventHandler.getInstance().getSubtitleOffset();

        if (offset != 0)
        {
            GlStateManager.translate(0, offset, 0);
        }
    }
}
