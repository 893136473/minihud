package fi.dy.masa.minihud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.minihud.event.InputEventHandler;
import fi.dy.masa.minihud.util.IMinecraftAccessor;
import fi.dy.masa.minihud.util.Tweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Mixin(Minecraft.class)
public class MixinMinecraft implements IMinecraftAccessor
{
    @Shadow
    private boolean actionKeyF3;
    @Shadow
    private int rightClickDelayTimer;

    //@Inject(method = "runTickKeyboard", at = @At(value = "JUMP", opcode = Opcodes.GOTO, ordinal = ??))
    @Inject(method = "dispatchKeypresses", at = @At(value = "HEAD"))
    public void onKeyboardInput(CallbackInfo ci)
    {
        InputEventHandler.getInstance().onKeyInput();
    }

    @Override
    public void setActionKeyF3(boolean value)
    {
        this.actionKeyF3 = value;
    }

    @Override
    public void setRightClickDelayTimer(int value)
    {
        this.rightClickDelayTimer = value;
    }

    @Redirect(method = "rightClickMouse()V", at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;processRightClickBlock(" +
                "Lnet/minecraft/client/entity/EntityPlayerSP;" +
                "Lnet/minecraft/client/multiplayer/WorldClient;" +
                "Lnet/minecraft/util/math/BlockPos;" +
                "Lnet/minecraft/util/EnumFacing;" +
                "Lnet/minecraft/util/math/Vec3d;" +
                "Lnet/minecraft/util/EnumHand;)" +
                "Lnet/minecraft/util/EnumActionResult;"))
    private EnumActionResult onProcessRightClickBlock(
            PlayerControllerMP controller,
            EntityPlayerSP player,
            WorldClient world,
            BlockPos pos,
            EnumFacing side,
            Vec3d hitVec,
            EnumHand hand)
    {
        return Tweaks.onProcessRightClickBlock(controller, player, world, pos, side, hitVec, hand);
    }
}
