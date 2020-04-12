package fi.dy.masa.minihud.event;

import java.lang.invoke.MethodHandle;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.BeehiveTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.BlockUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.WorldUtils;
import fi.dy.masa.minihud.MiniHUD;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.InfoToggle;
import fi.dy.masa.minihud.config.RendererToggle;
import fi.dy.masa.minihud.renderer.OverlayRenderer;
import fi.dy.masa.minihud.util.DataStorage;
import fi.dy.masa.minihud.util.MethodHandleUtils;
import fi.dy.masa.minihud.util.MethodHandleUtils.UnableToFindMethodHandleException;
import fi.dy.masa.minihud.util.MiscUtils;
import net.minecraftforge.registries.ForgeRegistries;

public class RenderHandler implements IRenderer
{
    private static final RenderHandler INSTANCE = new RenderHandler();

    private final MethodHandle methodHandle_WorldRenderer_getRenderedChunks;
    private final Minecraft mc;
    private final DataStorage data;
    private final Date date;
    private final Map<ChunkPos, CompletableFuture<Chunk>> chunkFutures = new HashMap<>();
    private int fps;
    private int fpsCounter;
    private long fpsUpdateTime = System.currentTimeMillis();
    private long infoUpdateTime;
    private double fontScale = 0.5d;
    private Set<InfoToggle> addedTypes = new HashSet<>();
    @Nullable private Chunk cachedClientChunk;

    private final List<StringHolder> lineWrappers = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();

    public RenderHandler()
    {
        this.data = DataStorage.getInstance();
        this.date = new Date();
        this.mc = Minecraft.getInstance();
        this.methodHandle_WorldRenderer_getRenderedChunks = this.getMethodHandle_getRenderedChunks();
    }

    private MethodHandle getMethodHandle_getRenderedChunks()
    {
        try
        {
            return MethodHandleUtils.getMethodHandleVirtual(WorldRenderer.class, new String[] { "func_184382_g", "getRenderedChunks" });
        }
        catch (UnableToFindMethodHandleException e)
        {
            MiniHUD.logger.error("Failed to get a MethodHandle for WorldRenderer#getRenderedChunks()", e);
            return null;
        }
    }

    public static RenderHandler getInstance()
    {
        return INSTANCE;
    }

    public DataStorage getDataStorage()
    {
        return this.data;
    }

    public static void fixDebugRendererState()
    {
        if (Configs.Generic.FIX_VANILLA_DEBUG_RENDERERS.getBooleanValue())
        {
            RenderSystem.disableLighting();
            //RenderUtils.color(1, 1, 1, 1);
            //OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        }
    }

    @Override
    public void onRenderGameOverlayPost(float partialTicks)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            this.resetCachedChunks();
            return;
        }

        if (this.mc.gameSettings.showDebugInfo == false &&
            this.mc.player != null &&
            (Configs.Generic.REQUIRE_SNEAK.getBooleanValue() == false || this.mc.player.isShiftKeyDown()) && // seriously wtf is that naming...
            Configs.Generic.REQUIRED_KEY.getKeybind().isKeybindHeld())
        {
            if (InfoToggle.FPS.getBooleanValue())
            {
                this.updateFps();
            }

            long currentTime = System.currentTimeMillis();

            // Only update the text once per game tick
            if (currentTime - this.infoUpdateTime >= 50)
            {
                this.updateLines();
                this.infoUpdateTime = currentTime;
            }

            int x = Configs.Generic.TEXT_POS_X.getIntegerValue();
            int y = Configs.Generic.TEXT_POS_Y.getIntegerValue();
            int textColor = Configs.Colors.TEXT_COLOR.getIntegerValue();
            int bgColor = Configs.Colors.TEXT_BACKGROUND_COLOR.getIntegerValue();
            HudAlignment alignment = (HudAlignment) Configs.Generic.HUD_ALIGNMENT.getOptionListValue();
            boolean useBackground = Configs.Generic.USE_TEXT_BACKGROUND.getBooleanValue();
            boolean useShadow = Configs.Generic.USE_FONT_SHADOW.getBooleanValue();

            RenderUtils.renderText(x, y, this.fontScale, textColor, bgColor, alignment, useBackground, useShadow, this.lines);
        }
    }

    @Override
    public void onRenderTooltipLast(ItemStack stack, int x, int y)
    {
        if (stack.getItem() instanceof FilledMapItem)
        {
            if (Configs.Generic.MAP_PREVIEW.getBooleanValue())
            {
                fi.dy.masa.malilib.render.RenderUtils.renderMapPreview(stack, x, y, Configs.Generic.MAP_PREVIEW_SIZE.getIntegerValue());
            }
        }
        else if (Configs.Generic.SHULKER_BOX_PREVIEW.getBooleanValue())
        {
            boolean render = Configs.Generic.SHULKER_DISPLAY_REQUIRE_SHIFT.getBooleanValue() == false || GuiBase.isShiftDown();

            if (render)
            {
                fi.dy.masa.malilib.render.RenderUtils.renderShulkerBoxPreview(stack, x, y, Configs.Generic.SHULKER_DISPLAY_BACKGROUND_COLOR.getBooleanValue());
            }
        }
    }

    @Override
    public void onRenderWorldLast(float partialTicks, MatrixStack matrixStack)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() && this.mc.world != null && this.mc.player != null)
        {
            OverlayRenderer.renderOverlays(this.mc, partialTicks, matrixStack);
        }
    }

    public void setFontScale(double scale)
    {
        this.fontScale = MathHelper.clamp(scale, 0, 10D);
    }

    public int getSubtitleOffset()
    {
        HudAlignment align = (HudAlignment) Configs.Generic.HUD_ALIGNMENT.getOptionListValue();

        if (align == HudAlignment.BOTTOM_RIGHT)
        {
            int offset = (int) (this.lineWrappers.size() * (StringUtils.getFontHeight() + 2) * this.fontScale);

            return -(offset - 16);
        }

        return 0;
    }

    private void updateFps()
    {
        this.fpsCounter++;
        long time = System.currentTimeMillis();

        if (time >= (this.fpsUpdateTime + 1000L))
        {
            this.fpsUpdateTime = time;
            this.fps = this.fpsCounter;
            this.fpsCounter = 0;
        }
    }

    public void updateData(Minecraft mc)
    {
        if (mc.world != null)
        {
            if (RendererToggle.OVERLAY_STRUCTURE_MAIN_TOGGLE.getBooleanValue())
            {
                DataStorage.getInstance().updateStructureData();
            }
        }
    }

    private void updateLines()
    {
        this.lineWrappers.clear();
        this.addedTypes.clear();

        if (this.chunkFutures.size() >= 4)
        {
            this.resetCachedChunks();
        }

        // Get the info line order based on the configs
        List<LinePos> positions = new ArrayList<LinePos>();

        for (InfoToggle toggle : InfoToggle.values())
        {
            if (toggle.getBooleanValue())
            {
                positions.add(new LinePos(toggle.getIntegerValue(), toggle));
            }
        }

        Collections.sort(positions);

        for (LinePos pos : positions)
        {
            try
            {
                this.addLine(pos.type);
            }
            catch (Exception e)
            {
                this.addLine(pos.type.getName() + ": exception");
            }
        }

        if (Configs.Generic.SORT_LINES_BY_LENGTH.getBooleanValue())
        {
            Collections.sort(this.lineWrappers);

            if (Configs.Generic.SORT_LINES_REVERSED.getBooleanValue())
            {
                Collections.reverse(this.lineWrappers);
            }
        }

        this.lines.clear();

        for (StringHolder holder : this.lineWrappers)
        {
            this.lines.add(holder.str);
        }
    }

    private void addLine(String text)
    {
        this.lineWrappers.add(new StringHolder(text));
    }

    @SuppressWarnings("deprecation")
    private void addLine(InfoToggle type)
    {
        Minecraft mc = this.mc;
        Entity entity = mc.getRenderViewEntity();
        World world = entity.getEntityWorld();
        BlockPos pos = new BlockPos(entity.getPosX(), entity.getBoundingBox().minY, entity.getPosZ());
        ChunkPos chunkPos = new ChunkPos(pos);

        if (type == InfoToggle.FPS)
        {
            this.addLine(String.format("%d fps", this.fps));
        }
        else if (type == InfoToggle.MEMORY_USAGE)
        {
            long memMax = Runtime.getRuntime().maxMemory();
            long memTotal = Runtime.getRuntime().totalMemory();
            long memFree = Runtime.getRuntime().freeMemory();
            long memUsed = memTotal - memFree;

            this.addLine(String.format("Mem: % 2d%% %03d/%03dMB | Allocated: % 2d%% %03dMB",
                    memUsed * 100L / memMax,
                    MiscUtils.bytesToMb(memUsed),
                    MiscUtils.bytesToMb(memMax),
                    memTotal * 100L / memMax,
                    MiscUtils.bytesToMb(memTotal)));
        }
        else if (type == InfoToggle.TIME_REAL)
        {
            try
            {
                SimpleDateFormat sdf = new SimpleDateFormat(Configs.Generic.DATE_FORMAT_REAL.getStringValue());
                this.date.setTime(System.currentTimeMillis());
                this.addLine(sdf.format(this.date));
            }
            catch (Exception e)
            {
                this.addLine("Date formatting failed - Invalid date format string?");
            }
        }
        else if (type == InfoToggle.TIME_WORLD)
        {
            long current = world.getDayTime();
            long total = world.getGameTime();
            this.addLine(String.format("World time: %5d - total: %d", current, total));
        }
        else if (type == InfoToggle.TIME_WORLD_FORMATTED)
        {
            try
            {
                long timeDay = world.getDayTime();
                long day = (int) (timeDay / 24000) + 1;
                // 1 tick = 3.6 seconds in MC (0.2777... seconds IRL)
                int dayTicks = (int) (timeDay % 24000);
                int hour = (int) ((dayTicks / 1000) + 6) % 24;
                int min = (int) (dayTicks / 16.666666) % 60;
                int sec = (int) (dayTicks / 0.277777) % 60;

                String str = Configs.Generic.DATE_FORMAT_MINECRAFT.getStringValue();
                str = str.replace("{DAY}",  String.format("%d", day));
                str = str.replace("{HOUR}", String.format("%02d", hour));
                str = str.replace("{MIN}",  String.format("%02d", min));
                str = str.replace("{SEC}",  String.format("%02d", sec));

                this.addLine(str);
            }
            catch (Exception e)
            {
                this.addLine("Date formatting failed - Invalid date format string?");
            }
        }
        else if (type == InfoToggle.TIME_DAY_MODULO)
        {
            int mod = Configs.Generic.TIME_DAY_DIVISOR.getIntegerValue();
            long current = world.getDayTime() % mod;
            this.addLine(String.format("Day time %% %d: %5d", mod, current));
        }
        else if (type == InfoToggle.TIME_TOTAL_MODULO)
        {
            int mod = Configs.Generic.TIME_TOTAL_DIVISOR.getIntegerValue();
            long current = world.getGameTime() % mod;
            this.addLine(String.format("Total time %% %d: %5d", mod, current));
        }
        else if (type == InfoToggle.SERVER_TPS)
        {
            if (mc.isIntegratedServerRunning() && (mc.getIntegratedServer().getTickCounter() % 10) == 0)
            {
                this.data.updateIntegratedServerTPS();
            }

            if (this.data.isServerTPSValid())
            {
                double tps = this.data.getServerTPS();
                double mspt = this.data.getServerMSPT();
                String rst = GuiBase.TXT_RST;
                String preTps = tps >= 20.0D ? GuiBase.TXT_GREEN : GuiBase.TXT_RED;
                String preMspt;

                // Carpet server and integrated server have actual meaningful MSPT data available
                if (this.data.isCarpetServer() || mc.isSingleplayer())
                {
                    if      (mspt <= 40) { preMspt = GuiBase.TXT_GREEN; }
                    else if (mspt <= 45) { preMspt = GuiBase.TXT_YELLOW; }
                    else if (mspt <= 50) { preMspt = GuiBase.TXT_GOLD; }
                    else                 { preMspt = GuiBase.TXT_RED; }

                    this.addLine(String.format("Server TPS: %s%.1f%s MSPT: %s%.1f%s", preTps, tps, rst, preMspt, mspt, rst));
                }
                else
                {
                    if (mspt <= 51) { preMspt = GuiBase.TXT_GREEN; }
                    else            { preMspt = GuiBase.TXT_RED; }

                    this.addLine(String.format("Server TPS: %s%.1f%s (MSPT*: %s%.1f%s)", preTps, tps, rst, preMspt, mspt, rst));
                }
            }
            else
            {
                this.addLine("Server TPS: <no valid data>");
            }
        }
        else if (type == InfoToggle.PING)
        {
            NetworkPlayerInfo info = mc.player.connection.getPlayerInfo(mc.player.getUniqueID());

            if (info != null)
            {
                this.addLine("Ping: " + info.getResponseTime() + " ms");
            }
        }
        else if (type == InfoToggle.COORDINATES ||
                 type == InfoToggle.DIMENSION)
        {
            // Don't add the same line multiple times
            if (this.addedTypes.contains(InfoToggle.COORDINATES) || this.addedTypes.contains(InfoToggle.DIMENSION))
            {
                return;
            }

            String pre = "";
            StringBuilder str = new StringBuilder(128);

            if (InfoToggle.COORDINATES.getBooleanValue())
            {
                if (Configs.Generic.USE_CUSTOMIZED_COORDINATES.getBooleanValue())
                {
                    try
                    {
                        str.append(String.format(Configs.Generic.COORDINATE_FORMAT_STRING.getStringValue(),
                            entity.getPosX(), entity.getBoundingBox().minY, entity.getPosZ()));
                    }
                    // Uh oh, someone done goofed their format string... :P
                    catch (Exception e)
                    {
                        str.append("broken coordinate format string!");
                    }
                }
                else
                {
                    str.append(String.format("XYZ: %.2f / %.4f / %.2f",
                        entity.getPosX(), entity.getBoundingBox().minY, entity.getPosZ()));
                }

                pre = " / ";
            }

            if (InfoToggle.DIMENSION.getBooleanValue())
            {
                int dimension = world.dimension.getType().getId();
                str.append(String.format(String.format("%sDimType ID: %d", pre, dimension)));
            }

            this.addLine(str.toString());

            this.addedTypes.add(InfoToggle.COORDINATES);
            this.addedTypes.add(InfoToggle.DIMENSION);
        }
        else if (type == InfoToggle.BLOCK_POS ||
                 type == InfoToggle.CHUNK_POS ||
                 type == InfoToggle.REGION_FILE)
        {
            // Don't add the same line multiple times
            if (this.addedTypes.contains(InfoToggle.BLOCK_POS) ||
                this.addedTypes.contains(InfoToggle.CHUNK_POS) ||
                this.addedTypes.contains(InfoToggle.REGION_FILE))
            {
                return;
            }

            String pre = "";
            StringBuilder str = new StringBuilder(256);

            if (InfoToggle.BLOCK_POS.getBooleanValue())
            {
                str.append(String.format("Block: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
                pre = " / ";
            }

            if (InfoToggle.CHUNK_POS.getBooleanValue())
            {
                str.append(pre).append(String.format("Sub-Chunk: %d, %d, %d", chunkPos.x, pos.getY() >> 4, chunkPos.z));
                pre = " / ";
            }

            if (InfoToggle.REGION_FILE.getBooleanValue())
            {
                str.append(pre).append(String.format("Region: r.%d.%d", pos.getX() >> 9, pos.getZ() >> 9));
            }

            this.addLine(str.toString());

            this.addedTypes.add(InfoToggle.BLOCK_POS);
            this.addedTypes.add(InfoToggle.CHUNK_POS);
            this.addedTypes.add(InfoToggle.REGION_FILE);
        }
        else if (type == InfoToggle.BLOCK_IN_CHUNK)
        {
            this.addLine(String.format("Block: %d, %d, %d within Sub-Chunk: %d, %d, %d",
                        pos.getX() & 0xF, pos.getY() & 0xF, pos.getZ() & 0xF,
                        chunkPos.x, pos.getY() >> 4, chunkPos.z));
        }
        else if (type == InfoToggle.DISTANCE)
        {
            Vec3d ref = DataStorage.getInstance().getDistanceReferencePoint();
            double dist = MathHelper.sqrt(ref.squareDistanceTo(entity.getPosX(), entity.getPosY(), entity.getPosZ()));
            this.addLine(String.format("Distance: %.2f (x: %.2f y: %.2f z: %.2f) [to x: %.2f y: %.2f z: %.2f]",
                    dist, entity.getPosX() - ref.x, entity.getPosY() - ref.y, entity.getPosZ() - ref.z, ref.x, ref.y, ref.z));
        }
        else if (type == InfoToggle.FACING)
        {
            Direction facing = entity.getHorizontalFacing();
            String str = "Invalid";

            switch (facing)
            {
                case NORTH: str = "Negative Z"; break;
                case SOUTH: str = "Positive Z"; break;
                case WEST:  str = "Negative X"; break;
                case EAST:  str = "Positive X"; break;
                default:
            }

            this.addLine(String.format("Facing: %s (%s)", facing, str));
        }
        else if (type == InfoToggle.LIGHT_LEVEL)
        {
            // Prevent a crash when outside of world
            if (pos.getY() >= 0 && pos.getY() < 256 && mc.world.isBlockLoaded(pos))
            {
                Chunk clientChunk = this.getClientChunk(chunkPos);

                if (clientChunk.isEmpty() == false)
                {
                    WorldLightManager lightingProvider = world.getChunkProvider().getLightManager();

                    this.addLine(String.format("Client Light: %d (block: %d, sky: %d)",
                            lightingProvider.getLightSubtracted(pos, 0),
                            lightingProvider.getLightEngine(LightType.BLOCK).getLightFor(pos),
                            lightingProvider.getLightEngine(LightType.SKY).getLightFor(pos)));

                    World bestWorld = WorldUtils.getBestWorld(mc);
                    Chunk serverChunk = this.getChunk(chunkPos);

                    if (serverChunk != null && serverChunk != clientChunk)
                    {
                        lightingProvider = bestWorld.getChunkProvider().getLightManager();
                        int total = lightingProvider.getLightSubtracted(pos, 0);
                        int block = lightingProvider.getLightEngine(LightType.BLOCK).getLightFor(pos);
                        int sky = lightingProvider.getLightEngine(LightType.SKY).getLightFor(pos);
                        this.addLine(String.format("Server Light: %d (block: %d, sky: %d)", total, block, sky));
                    }
                }
            }
        }
        else if (type == InfoToggle.BEE_COUNT)
        {
            World bestWorld = WorldUtils.getBestWorld(mc);
            TileEntity be = this.getTargetedBlockEntity(bestWorld, mc);

            if (be instanceof BeehiveTileEntity)
            {
                this.addLine("Bees: " + GuiBase.TXT_AQUA + ((BeehiveTileEntity) be).getBeeCount());
            }
        }
        else if (type == InfoToggle.ROTATION_YAW ||
                 type == InfoToggle.ROTATION_PITCH ||
                 type == InfoToggle.SPEED)
        {
            // Don't add the same line multiple times
            if (this.addedTypes.contains(InfoToggle.ROTATION_YAW) ||
                this.addedTypes.contains(InfoToggle.ROTATION_PITCH) ||
                this.addedTypes.contains(InfoToggle.SPEED))
            {
                return;
            }

            String pre = "";
            StringBuilder str = new StringBuilder(128);

            if (InfoToggle.ROTATION_YAW.getBooleanValue())
            {
                str.append(String.format("yaw: %.1f", MathHelper.wrapDegrees(entity.rotationYaw)));
                pre = " / ";
            }

            if (InfoToggle.ROTATION_PITCH.getBooleanValue())
            {
                str.append(pre).append(String.format("pitch: %.1f", MathHelper.wrapDegrees(entity.rotationPitch)));
                pre = " / ";
            }

            if (InfoToggle.SPEED.getBooleanValue())
            {
                double dx = entity.getPosX() - entity.lastTickPosX;
                double dy = entity.getPosY() - entity.lastTickPosY;
                double dz = entity.getPosZ() - entity.lastTickPosZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                str.append(pre).append(String.format("speed: %.3f m/s", dist * 20));
            }

            this.addLine(str.toString());

            this.addedTypes.add(InfoToggle.ROTATION_YAW);
            this.addedTypes.add(InfoToggle.ROTATION_PITCH);
            this.addedTypes.add(InfoToggle.SPEED);
        }
        else if (type == InfoToggle.SPEED_AXIS)
        {
            double dx = entity.getPosX() - entity.lastTickPosX;
            double dy = entity.getPosY() - entity.lastTickPosY;
            double dz = entity.getPosZ() - entity.lastTickPosZ;
            this.addLine(String.format("speed: x: %.3f y: %.3f z: %.3f m/s", dx * 20, dy * 20, dz * 20));
        }
        else if (type == InfoToggle.CHUNK_SECTIONS)
        {
            this.addLine(String.format("C: %d", this.getRenderedChunks()));
        }
        else if (type == InfoToggle.CHUNK_SECTIONS_FULL)
        {
            this.addLine(mc.worldRenderer.getDebugInfoRenders());
        }
        else if (type == InfoToggle.CHUNK_UPDATES)
        {
            this.addLine("TODO" /*String.format("Chunk updates: %d", ChunkRenderer.chunkUpdateCount)*/);
        }
        else if (type == InfoToggle.LOADED_CHUNKS_COUNT)
        {
            String chunksClient = mc.world.getProviderName();
            World worldServer = WorldUtils.getBestWorld(mc);

            if (worldServer != null && worldServer != mc.world)
            {
                int chunksServer = ((ServerChunkProvider) worldServer.getChunkProvider()).getLoadedChunkCount();
                int chunksServerTot = ((ServerChunkProvider) worldServer.getChunkProvider()).func_217229_b();
                this.addLine(String.format("Server: %d / %d - Client: %s", chunksServer, chunksServerTot, chunksClient));
            }
            else
            {
                this.addLine(chunksClient);
            }
        }
        else if (type == InfoToggle.PARTICLE_COUNT)
        {
            this.addLine(String.format("P: %s", mc.particles.getStatistics()));
        }
        else if (type == InfoToggle.DIFFICULTY)
        {
            if (mc.world.isBlockLoaded(pos))
            {
                long chunkInhabitedTime = 0L;
                float moonPhaseFactor = 0.0F;
                Chunk serverChunk = this.getChunk(chunkPos);

                if (serverChunk != null)
                {
                    moonPhaseFactor = mc.world.getCurrentMoonPhaseFactor();
                    chunkInhabitedTime = serverChunk.getInhabitedTime();
                }

                DifficultyInstance diff = new DifficultyInstance(mc.world.getDifficulty(), mc.world.getDayTime(), chunkInhabitedTime, moonPhaseFactor);
                this.addLine(String.format("Local Difficulty: %.2f // %.2f (Day %d)",
                        diff.getAdditionalDifficulty(), diff.getClampedAdditionalDifficulty(), mc.world.getDayTime() / 24000L));
            }
        }
        else if (type == InfoToggle.BIOME)
        {
            // Prevent a crash when outside of world
            if (pos.getY() >= 0 && pos.getY() < 256 && mc.world.isBlockLoaded(pos))
            {
                Chunk clientChunk = this.getClientChunk(chunkPos);

                if (clientChunk.isEmpty() == false)
                {
                    this.addLine("Biome: " + mc.world.getBiome(pos).getDisplayName().getString());
                }
            }
        }
        else if (type == InfoToggle.BIOME_REG_NAME)
        {
            // Prevent a crash when outside of world
            if (pos.getY() >= 0 && pos.getY() < 256 && mc.world.isBlockLoaded(pos))
            {
                Chunk clientChunk = this.getClientChunk(chunkPos);

                if (clientChunk.isEmpty() == false)
                {
                    Biome biome = mc.world.getBiome(pos);
                    ResourceLocation rl = ForgeRegistries.BIOMES.getKey(biome);
                    String name = rl != null ? rl.toString() : "?";
                    this.addLine("Biome reg name: " + name);
                }
            }
        }
        else if (type == InfoToggle.ENTITIES)
        {
            String ent = mc.worldRenderer.getDebugInfoEntities();

            int p = ent.indexOf(",");

            if (p != -1)
            {
                ent = ent.substring(0, p);
            }

            this.addLine(ent);
        }
        else if (type == InfoToggle.TILE_ENTITIES)
        {
            this.addLine(String.format("Client world TE - L: %d, T: %d", mc.world.loadedTileEntityList.size(), mc.world.tickableTileEntities.size()));
        }
        else if (type == InfoToggle.ENTITIES_CLIENT_WORLD)
        {
            int countClient = mc.world.getCountLoadedEntities();

            /*
            if (mc.isIntegratedServerRunning())
            {
                World serverWorld = WorldUtils.getBestWorld(mc);

                if (serverWorld != null && serverWorld instanceof ServerWorld)
                {
                    int countServer = ((IMixinServerWorld) serverWorld).getEntityList().size();
                    this.addLine(String.format("Entities - Client: %d, Server: %d", countClient, countServer));
                    return;
                }
            }
            */

            this.addLine(String.format("Entities - Client: %d", countClient));
        }
        else if (type == InfoToggle.SLIME_CHUNK)
        {
            if (world.dimension.isSurfaceWorld() == false)
            {
                return;
            }

            String result;
            DimensionType dimension = entity.dimension;

            if (this.data.isWorldSeedKnown(dimension))
            {
                long seed = this.data.getWorldSeed(dimension);

                if (MiscUtils.canSlimeSpawnAt(pos.getX(), pos.getZ(), seed))
                {
                    result = GuiBase.TXT_GREEN + "YES" + GuiBase.TXT_RST;
                }
                else
                {
                    result = GuiBase.TXT_RED + "NO" + GuiBase.TXT_RST;
                }
            }
            else
            {
                result = "<world seed not known>";
            }

            this.addLine("Slime chunk: " + result);
        }
        else if (type == InfoToggle.LOOKING_AT_ENTITY)
        {
            if (mc.objectMouseOver != null &&
                mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY)
            {
                Entity target = ((EntityRayTraceResult) mc.objectMouseOver).getEntity();

                if (target instanceof LivingEntity)
                {
                    LivingEntity living = (LivingEntity) target;
                    this.addLine(String.format("Entity: %s - HP: %.1f / %.1f",
                            target.getName().getString(), living.getHealth(), living.getMaxHealth()));
                }
                else
                {
                    this.addLine(String.format("Entity: %s", target.getName().getString()));
                }
            }
        }
        else if (type == InfoToggle.ENTITY_REG_NAME)
        {
            if (mc.objectMouseOver != null &&
                mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY)
            {
                Entity lookedEntity = ((EntityRayTraceResult) mc.objectMouseOver).getEntity();
                ResourceLocation regName = EntityType.getKey(lookedEntity.getType());

                if (regName != null)
                {
                    this.addLine(String.format("Entity reg name: %s", regName.toString()));
                }
            }
        }
        else if (type == InfoToggle.LOOKING_AT_BLOCK ||
                 type == InfoToggle.LOOKING_AT_BLOCK_CHUNK)
        {
            // Don't add the same line multiple times
            if (this.addedTypes.contains(InfoToggle.LOOKING_AT_BLOCK) ||
                this.addedTypes.contains(InfoToggle.LOOKING_AT_BLOCK_CHUNK))
            {
                return;
            }

            if (mc.objectMouseOver != null &&
                mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK)
            {
                BlockPos lookPos = ((BlockRayTraceResult) mc.objectMouseOver).getPos();
                String pre = "";
                StringBuilder str = new StringBuilder(128);

                if (InfoToggle.LOOKING_AT_BLOCK.getBooleanValue())
                {
                    str.append(String.format("Looking at block: %d, %d, %d", lookPos.getX(), lookPos.getY(), lookPos.getZ()));
                    pre = " // ";
                }

                if (InfoToggle.LOOKING_AT_BLOCK_CHUNK.getBooleanValue())
                {
                    str.append(pre).append(String.format("Block: %d, %d, %d in Sub-Chunk: %d, %d, %d",
                            lookPos.getX() & 0xF, lookPos.getY() & 0xF, lookPos.getZ() & 0xF,
                            lookPos.getX() >> 4, lookPos.getY() >> 4, lookPos.getZ() >> 4));
                }

                this.addLine(str.toString());

                this.addedTypes.add(InfoToggle.LOOKING_AT_BLOCK);
                this.addedTypes.add(InfoToggle.LOOKING_AT_BLOCK_CHUNK);
            }
        }
        else if (type == InfoToggle.BLOCK_PROPS)
        {
            this.getBlockProperties(mc);
        }
    }

    @Nullable
    private TileEntity getTargetedBlockEntity(World world, Minecraft mc)
    {
        if (mc.objectMouseOver != null &&
            mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK)
        {
            BlockPos posLooking = ((BlockRayTraceResult) mc.objectMouseOver).getPos();
            Chunk chunk = this.getChunk(new ChunkPos(posLooking));

            // The method in World now checks that the caller is from the same thread...
            return chunk != null ? chunk.getTileEntity(posLooking) : null;
        }

        return null;
    }

    private <T extends Comparable<T>> void getBlockProperties(Minecraft mc)
    {
        if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK)
        {
            BlockPos posLooking = ((BlockRayTraceResult) mc.objectMouseOver).getPos();
            BlockState state = mc.world.getBlockState(posLooking);

            this.addLine(String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));

            for (String line : BlockUtils.getFormattedBlockStateProperties(state))
            {
                this.addLine(line);
            }
        }
    }

    private int getRenderedChunks()
    {
        try
        {
            return (int) this.methodHandle_WorldRenderer_getRenderedChunks.invokeExact(Minecraft.getInstance().worldRenderer);
        }
        catch (Throwable t)
        {
            MiniHUD.logger.error("Error while trying invoke WorldRenderer#getRenderedChunks()");
            return -1;
        }
    }

    @Nullable
    private Chunk getChunk(ChunkPos chunkPos)
    {
        CompletableFuture<Chunk> future = this.chunkFutures.get(chunkPos);

        if (future == null)
        {
            future = this.setupChunkFuture(chunkPos);
        }

        return future.getNow(null);
    }

    private CompletableFuture<Chunk> setupChunkFuture(ChunkPos chunkPos)
    {
        IntegratedServer server = this.mc.getIntegratedServer();
        CompletableFuture<Chunk> future = null;

        if (server != null)
        {
            ServerWorld world = server.getWorld(this.mc.world.dimension.getType());

            if (world != null)
            {
                future = world.getChunkProvider().func_217232_b(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false)
                        .thenApply((either) -> either.map((chunk) -> (Chunk) chunk, (unloaded) -> null) );
            }
        }

        if (future == null)
        {
            future = CompletableFuture.completedFuture(this.getClientChunk(chunkPos));
        }

        this.chunkFutures.put(chunkPos, future);

        return future;
    }

    private Chunk getClientChunk(ChunkPos chunkPos)
    {
        if (this.cachedClientChunk == null || this.cachedClientChunk.getPos().equals(chunkPos) == false)
        {
            this.cachedClientChunk = this.mc.world.getChunk(chunkPos.x, chunkPos.z);
        }

        return this.cachedClientChunk;
    }

    private void resetCachedChunks()
    {
        this.chunkFutures.clear();
        this.cachedClientChunk = null;
    }

    private class StringHolder implements Comparable<StringHolder>
    {
        public final String str;

        public StringHolder(String str)
        {
            this.str = str;
        }

        @Override
        public int compareTo(StringHolder other)
        {
            int lenThis = this.str.length();
            int lenOther = other.str.length();

            if (lenThis == lenOther)
            {
                return 0;
            }

            return this.str.length() > other.str.length() ? -1 : 1;
        }
    }

    private static class LinePos implements Comparable<LinePos>
    {
        private final int position;
        private final InfoToggle type;

        private LinePos(int position, InfoToggle type)
        {
            this.position = position;
            this.type = type;
        }

        @Override
        public int compareTo(LinePos other)
        {
            if (this.position < 0)
            {
                return other.position >= 0 ? 1 : 0;
            }
            else if (other.position < 0 && this.position >= 0)
            {
                return -1;
            }

            return this.position < other.position ? -1 : (this.position > other.position ? 1 : 0);
        }
    }
}
