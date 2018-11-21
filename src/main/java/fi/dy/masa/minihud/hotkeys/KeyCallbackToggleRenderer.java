package fi.dy.masa.minihud.hotkeys;

import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.minihud.config.RendererToggle;
import fi.dy.masa.minihud.event.RenderHandler;
import fi.dy.masa.minihud.renderer.OverlayRenderer;
import fi.dy.masa.minihud.renderer.OverlayRendererRandomTickableChunks;
import fi.dy.masa.minihud.renderer.OverlayRendererSlimeChunks;
import fi.dy.masa.minihud.renderer.OverlayRendererSpawnableChunks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;

public class KeyCallbackToggleRenderer extends KeyCallbackToggleBooleanConfigWithMessage
{
    protected final RendererToggle rendererConfig;

    public KeyCallbackToggleRenderer(RendererToggle config)
    {
        super(config);

        this.rendererConfig = config;
    }

    @Override
    public boolean onKeyAction(KeyAction action, IKeybind key)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc != null && mc.player != null && super.onKeyAction(action, key))
        {
            if (key == RendererToggle.OVERLAY_CHUNK_UNLOAD_BUCKET.getKeybind())
            {
                OverlayRenderer.chunkUnloadBucketOverlayY = mc.player.posY - 2;
            }
            else if (key == RendererToggle.OVERLAY_SLIME_CHUNKS_OVERLAY.getKeybind())
            {
                OverlayRendererSlimeChunks.overlayTopY = mc.player.posY;
            }
            else if (key == RendererToggle.OVERLAY_SPAWN_CHUNK_OVERLAY_REAL.getKeybind() && this.rendererConfig.getBooleanValue())
            {
                final boolean enabled = this.config.getBooleanValue();
                String pre = enabled ? TextFormatting.GREEN.toString() : TextFormatting.RED.toString();
                String status = I18n.format("malilib.message.value." + (enabled ? "on" : "off"));
                String message = I18n.format("malilib.message.toggled", this.config.getPrettyName(), pre + status + TextFormatting.RESET);

                BlockPos spawn = mc.world.getSpawnPoint();
                RenderHandler.getInstance().getDataStorage().setWorldSpawn(spawn);
                String str = String.format(", using the world spawn x: %d, y: %d, z: %d", spawn.getX(), spawn.getY(), spawn.getZ());

                StringUtils.printActionbarMessage(message + str);
            }
            else if (key == RendererToggle.OVERLAY_RANDOM_TICKS_FIXED.getKeybind() && this.rendererConfig.getBooleanValue())
            {
                final boolean enabled = this.config.getBooleanValue();
                String pre = enabled ? TextFormatting.GREEN.toString() : TextFormatting.RED.toString();
                String status = I18n.format("malilib.message.value." + (enabled ? "on" : "off"));
                String message = I18n.format("malilib.message.toggled", this.config.getPrettyName(), pre + status + TextFormatting.RESET);

                Vec3d pos = mc.player.getPositionVector();
                OverlayRendererRandomTickableChunks.newPos = pos;
                String str = String.format(", using the position x: %.2f, y: %.2f, z: %.2f", pos.x, pos.y, pos.z);

                StringUtils.printActionbarMessage(message + str);
            }
            else if (key == RendererToggle.OVERLAY_SPAWNABLE_CHUNKS_PLAYER.getKeybind() && this.rendererConfig.getBooleanValue())
            {
                OverlayRendererSpawnableChunks.overlayTopY = mc.player.posY;
            }
            else if (key == RendererToggle.OVERLAY_SPAWNABLE_CHUNKS_FIXED.getKeybind() && this.rendererConfig.getBooleanValue())
            {
                final boolean enabled = this.config.getBooleanValue();
                String pre = enabled ? TextFormatting.GREEN.toString() : TextFormatting.RED.toString();
                String status = I18n.format("malilib.message.value." + (enabled ? "on" : "off"));
                String message = I18n.format("malilib.message.toggled", this.config.getPrettyName(), pre + status + TextFormatting.RESET);

                BlockPos pos = new BlockPos(mc.player);
                OverlayRendererSpawnableChunks.newPos = pos;
                OverlayRendererSpawnableChunks.overlayTopY = mc.player.posY;
                String str = String.format(", using the position x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());

                StringUtils.printActionbarMessage(message + str);
            }

            return true;
        }

        return false;
    }
}
