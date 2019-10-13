package fi.dy.masa.minihud.renderer.shapes;

import java.util.HashSet;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.Color4f;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.renderer.RenderObjectBase;

public class ShapeCircle extends ShapeCircleBase
{
    protected int height = 1;

    public ShapeCircle()
    {
        super(ShapeType.CIRCLE, Configs.Colors.SHAPE_CIRCLE.getColor(), 16);
    }

    @Override
    public void update(Entity entity, Minecraft mc)
    {
        this.renderCircleShape();
        this.onPostUpdate(entity.getPositionVector());
    }

    public int getHeight()
    {
        return this.height;
    }

    public void setHeight(int height)
    {
        this.height = MathHelper.clamp(height, 1, 260);
        this.setNeedsUpdate();
    }

    protected void renderCircleShape()
    {
        RenderObjectBase renderQuads = this.renderObjects.get(0);
        BUFFER_1.begin(renderQuads.getGlMode(), DefaultVertexFormats.POSITION_COLOR);

        Color4f colorQuad = this.color;
        BlockPos posCenter = this.getCenterBlock();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();
        HashSet<BlockPos> circlePositions = new HashSet<>();

        this.setPosition(posCenter);

        EnumFacing.Axis axis = this.mainAxis.getAxis();

        for (int i = 0; i < this.height; ++i)
        {
            posMutable.setPos(  posCenter.getX() + this.mainAxis.getXOffset() * i,
                                posCenter.getY() + this.mainAxis.getYOffset() * i,
                                posCenter.getZ() + this.mainAxis.getZOffset() * i);

            if (axis == EnumFacing.Axis.Y)
            {
                this.addPositionsOnHorizontalRing(circlePositions, posMutable, EnumFacing.NORTH);
            }
            else
            {
                this.addPositionsOnVerticalRing(circlePositions, posMutable, EnumFacing.UP, this.mainAxis);
            }
        }

        EnumFacing mainAxis = this.mainAxis;

        for (BlockPos pos : circlePositions)
        {
            for (int i = 0; i < 6; ++i)
            {
                EnumFacing side = FACING_ALL[i];
                posMutable.setPos(pos.getX() + side.getXOffset(), pos.getY() + side.getYOffset(), pos.getZ() + side.getZOffset());

                if (this.layerRange.isPositionWithinRange(pos) &&
                    circlePositions.contains(posMutable) == false &&
                    this.isAdjacentPositionOutside(pos, side, mainAxis))
                {
                    fi.dy.masa.malilib.render.RenderUtils.drawBlockSpaceSideBatchedQuads(pos, side, colorQuad, 0, BUFFER_1);
                }
            }
        }

        BUFFER_1.finishDrawing();

        renderQuads.uploadData(BUFFER_1);
    }

    @Override
    protected boolean isPositionOnOrInsideRing(int blockX, int blockY, int blockZ, EnumFacing outSide, EnumFacing mainAxis)
    {
        EnumFacing.Axis axis = mainAxis.getAxis();

        double x = axis == EnumFacing.Axis.X ? this.effectiveCenter.x : (double) blockX + 0.5;
        double y = axis == EnumFacing.Axis.Y ? this.effectiveCenter.y : (double) blockY + 0.5;
        double z = axis == EnumFacing.Axis.Z ? this.effectiveCenter.z : (double) blockZ + 0.5;
        double dist = this.effectiveCenter.squareDistanceTo(x, y, z);
        double diff = this.radiusSq - dist;

        if (diff > 0)
        {
            return true;
        }

        double xAdj = axis == EnumFacing.Axis.X ? this.effectiveCenter.x : (double) blockX + outSide.getXOffset() + 0.5;
        double yAdj = axis == EnumFacing.Axis.Y ? this.effectiveCenter.y : (double) blockY + outSide.getYOffset() + 0.5;
        double zAdj = axis == EnumFacing.Axis.Z ? this.effectiveCenter.z : (double) blockZ + outSide.getZOffset() + 0.5;
        double distAdj = this.effectiveCenter.squareDistanceTo(xAdj, yAdj, zAdj);
        double diffAdj = this.radiusSq - distAdj;

        return diffAdj > 0 && Math.abs(diff) < Math.abs(diffAdj);
    }

    @Override
    public List<String> getWidgetHoverLines()
    {
        List<String> lines = super.getWidgetHoverLines();

        String aq = GuiBase.TXT_AQUA;
        String gl = GuiBase.TXT_GOLD;
        String gr = GuiBase.TXT_GRAY;
        String rst = GuiBase.TXT_GRAY;

        lines.add(1, gr + StringUtils.translate("minihud.gui.label.height_value", gl + this.getHeight() + rst));
        lines.add(2, gr + StringUtils.translate("minihud.gui.label.cicle.main_axis_value",
                aq + org.apache.commons.lang3.StringUtils.capitalize(this.getMainAxis().toString().toLowerCase()) + rst));

        return lines;
    }

    @Override
    public JsonObject toJson()
    {
        JsonObject obj = super.toJson();

        obj.add("height", new JsonPrimitive(this.height));

        return obj;
    }

    @Override
    public void fromJson(JsonObject obj)
    {
        super.fromJson(obj);

        this.setHeight(JsonUtils.getInteger(obj, "height"));
    }
}
