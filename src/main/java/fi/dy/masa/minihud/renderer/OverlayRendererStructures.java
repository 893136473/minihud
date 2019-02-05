package fi.dy.masa.minihud.renderer;

import java.util.Collection;
import java.util.List;
import org.lwjgl.opengl.GL11;
import com.google.common.collect.ArrayListMultimap;
import fi.dy.masa.malilib.util.Color4f;
import fi.dy.masa.minihud.config.RendererToggle;
import fi.dy.masa.minihud.util.DataStorage;
import fi.dy.masa.minihud.util.MiscUtils;
import fi.dy.masa.minihud.util.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

public class OverlayRendererStructures extends OverlayRendererBase
{
    protected BlockPos lastUpdatePos = BlockPos.ORIGIN;

    @Override
    public boolean shouldRender(Minecraft mc)
    {
        if (RendererToggle.OVERLAY_STRUCTURE_MAIN_TOGGLE.getBooleanValue() == false)
        {
            return false;
        }

        int dimId = mc.world.provider.getDimensionType().getId();

        if (dimId == 0)
        {
            return StructureType.DESERT_PYRAMID.isEnabled() ||
                   StructureType.IGLOO.isEnabled() ||
                   StructureType.JUNGLE_TEMPLE.isEnabled() ||
                   StructureType.MANSION.isEnabled() ||
                   StructureType.OCEAN_MONUMENT.isEnabled() ||
                   StructureType.STRONGHOLD.isEnabled() ||
                   StructureType.VILLAGE.isEnabled() ||
                   StructureType.WITCH_HUT.isEnabled();
        }
        else if (dimId == -1)
        {
            return StructureType.NETHER_FORTRESS.isEnabled();
        }
        else
        {
            return dimId == 1 && StructureType.END_CITY.isEnabled();
        }
    }

    @Override
    public boolean needsUpdate(Entity entity, Minecraft mc)
    {
        int hysteresis = 16;

        return DataStorage.getInstance().hasStructureDataChanged() ||
               Math.abs(entity.posX - this.lastUpdatePos.getX()) > hysteresis ||
               Math.abs(entity.posY - this.lastUpdatePos.getY()) > hysteresis ||
               Math.abs(entity.posZ - this.lastUpdatePos.getZ()) > hysteresis;
    }

    @Override
    public void update(Entity entity, Minecraft mc)
    {
        int dim = mc.world.provider.getDimensionType().getId();
        this.lastUpdatePos = new BlockPos(entity);

        RenderObjectBase renderQuads = this.renderObjects.get(0);
        RenderObjectBase renderLines = this.renderObjects.get(1);
        BUFFER_1.begin(renderQuads.getGlMode(), DefaultVertexFormats.POSITION_COLOR);
        BUFFER_2.begin(renderLines.getGlMode(), DefaultVertexFormats.POSITION_COLOR);

        this.updateStructures(dim, this.lastUpdatePos, mc);

        BUFFER_1.finishDrawing();
        BUFFER_2.finishDrawing();

        renderQuads.uploadData(BUFFER_1);
        renderLines.uploadData(BUFFER_2);
    }

    @Override
    public void allocateGlResources()
    {
        this.allocateBuffer(GL11.GL_QUADS);
        this.allocateBuffer(GL11.GL_LINES);
    }

    private void updateStructures(int dimId, BlockPos playerPos, Minecraft mc)
    {
        ArrayListMultimap<StructureType, StructureStart> structures = DataStorage.getInstance().getCopyOfStructureData();
        int maxRange = (mc.gameSettings.renderDistanceChunks + 4) * 16;

        for (StructureType type : StructureType.values())
        {
            if (type.isEnabled() && type.existsInDimension(dimId))
            {
                Collection<StructureStart> structureData = structures.get(type);

                if (structureData.isEmpty() == false)
                {
                    this.renderStructuresWithinRange(type, structureData, playerPos, maxRange);
                }
            }
        }
    }

    private void renderStructuresWithinRange(StructureType type, Collection<StructureStart> structureData, BlockPos playerPos, int maxRange)
    {
        for (StructureStart start : structureData)
        {
            if (MiscUtils.isStructureWithinRange(start, playerPos, maxRange))
            {
                this.renderStructure(type, start);
            }
        }
    }

    private void renderStructure(StructureType type, StructureStart start)
    {
        Color4f color = type.getToggle().getColorMain().getColor();
        List<StructureComponent> components = start.getComponents();

        fi.dy.masa.malilib.render.RenderUtils.drawBox(start.getBoundingBox(), color, BUFFER_1, BUFFER_2);

        if (components.isEmpty() == false)
        {
            if (components.size() > 1 || MiscUtils.areBoxesEqual(components.get(0).getBoundingBox(), start.getBoundingBox()) == false)
            {
                color = type.getToggle().getColorComponents().getColor();

                for (StructureComponent component : components)
                {
                    fi.dy.masa.malilib.render.RenderUtils.drawBox(component.getBoundingBox(), color, BUFFER_1, BUFFER_2);
                }
            }
        }
    }
}
