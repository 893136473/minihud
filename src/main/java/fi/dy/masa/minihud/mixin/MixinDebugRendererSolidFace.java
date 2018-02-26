package fi.dy.masa.minihud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.minihud.event.RenderEventHandler;
import net.minecraft.client.renderer.debug.DebugRendererSolidFace;

@Mixin(DebugRendererSolidFace.class)
public class MixinDebugRendererSolidFace
{
    @Inject(method = "render", at = @At("HEAD"))
    public void fixDebugRendererState(CallbackInfo ci)
    {
        RenderEventHandler.fixDebugRendererState();
    }
}
