package fi.dy.masa.minihud.renderer;

import org.lwjgl.opengl.GL11;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos.PooledMutable;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.malilib.util.Color4f;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.RendererToggle;
import fi.dy.masa.minihud.util.DataStorage;
import fi.dy.masa.minihud.util.MiscUtils;

public class OverlayRendererSlimeChunks extends OverlayRendererBase
{
    public static double overlayTopY;

    protected double topY;

    @Override
    public boolean shouldRender(MinecraftClient mc)
    {
        return RendererToggle.OVERLAY_SLIME_CHUNKS_OVERLAY.getBooleanValue() &&
                DataStorage.getInstance().isWorldSeedKnown(mc.world.dimension.getType()) &&
                mc.world.dimension.hasVisibleSky();
    }

    @Override
    public boolean needsUpdate(Entity entity, MinecraftClient mc)
    {
        if (this.topY != overlayTopY)
        {
            return true;
        }

        int ex = (int) Math.floor(entity.x);
        int ez = (int) Math.floor(entity.z);
        int lx = this.lastUpdatePos.getX();
        int lz = this.lastUpdatePos.getZ();

        return Math.abs(lx - ex) > 16 || Math.abs(lz - ez) > 16;
    }

    @Override
    public void update(Entity entity, MinecraftClient mc)
    {
        DataStorage data = DataStorage.getInstance();
        this.topY = overlayTopY;

        if (data.isWorldSeedKnown(entity.dimension))
        {
            final int centerX = ((int) MathHelper.floor(entity.x)) >> 4;
            final int centerZ = ((int) MathHelper.floor(entity.z)) >> 4;
            final long worldSeed = data.getWorldSeed(entity.dimension);
            final Color4f colorLines = Configs.Colors.SLIME_CHUNKS_OVERLAY_COLOR.getColor();
            final Color4f colorSides = Color4f.fromColor(colorLines, colorLines.a / 6);
            PooledMutable pos1 = PooledMutable.get();
            PooledMutable pos2 = PooledMutable.get();
            int r = MathHelper.clamp(Configs.Generic.SLIME_CHUNK_OVERLAY_RADIUS.getIntegerValue(), -1, 40);

            if (r == -1)
            {
                r = mc.options.viewDistance;
            }

            RenderObjectBase renderQuads = this.renderObjects.get(0);
            RenderObjectBase renderLines = this.renderObjects.get(1);
            BUFFER_1.begin(renderQuads.getGlMode(), VertexFormats.POSITION_COLOR);
            BUFFER_2.begin(renderLines.getGlMode(), VertexFormats.POSITION_COLOR);
            int topY = (int) Math.floor(this.topY);

            for (int xOff = -r; xOff <= r; xOff++)
            {
                for (int zOff = -r; zOff <= r; zOff++)
                {
                    int cx = centerX + xOff;
                    int cz = centerZ + zOff;

                    if (MiscUtils.canSlimeSpawnInChunk(cx, cz, worldSeed))
                    {
                        pos1.set( cx << 4,          0,  cz << 4);
                        pos2.set((cx << 4) + 15, topY, (cz << 4) + 15);
                        fi.dy.masa.malilib.render.RenderUtils.drawBoxWithEdgesBatched(pos1, pos2, colorLines, colorSides, BUFFER_1, BUFFER_2);
                    }
                }
            }

            pos1.close();
            pos2.close();

            BUFFER_1.end();
            BUFFER_2.end();

            renderQuads.uploadData(BUFFER_1);
            renderLines.uploadData(BUFFER_2);
        }
    }

    @Override
    public void allocateGlResources()
    {
        this.allocateBuffer(GL11.GL_QUADS);
        this.allocateBuffer(GL11.GL_LINES);
    }

    @Override
    public String getSaveId()
    {
        return "slime_chunks";
    }

    @Override
    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.add("y_top", new JsonPrimitive(overlayTopY));
        return obj;
    }

    @Override
    public void fromJson(JsonObject obj)
    {
        overlayTopY = JsonUtils.getFloat(obj, "y_top");
    }
}
