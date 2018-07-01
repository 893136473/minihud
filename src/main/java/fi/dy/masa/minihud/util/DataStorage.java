package fi.dy.masa.minihud.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import fi.dy.masa.minihud.LiteModMiniHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class DataStorage
{
    private static final Pattern PATTERN_CARPET_TPS = Pattern.compile("TPS: (?<tps>[0-9]+[\\.,][0-9]) MSPT: (?<mspt>[0-9]+[\\.,][0-9])");
    private static final DataStorage INSTANCE = new DataStorage();

    private boolean worldSeedValid;
    private boolean serverTPSValid;
    private boolean carpetServer;
    private boolean worldSpawnValid;
    private long worldSeed;
    private long lastServerTick;
    private long lastServerTimeUpdate;
    private double serverTPS;
    private double serverMSPT;
    private BlockPos worldSpawn = BlockPos.ORIGIN;
    private final Set<ChunkPos> chunkHeightmapsToCheck = new HashSet<>();
    private final Map<ChunkPos, Integer> spawnableSubChunks = new HashMap<>();
    private final Minecraft mc = Minecraft.getMinecraft();

    public static DataStorage getInstance()
    {
        return INSTANCE;
    }

    public void onWorldLoad()
    {
        this.worldSeedValid = false;
        this.serverTPSValid = false;
        this.carpetServer = false;
        this.worldSpawnValid = false;
    }

    public void setWorldSeed(long seed)
    {
        this.worldSeed = seed;
        this.worldSeedValid = true;
    }

    public void setWorldSpawn(BlockPos spawn)
    {
        this.worldSpawn = spawn;
        this.worldSpawnValid = true;
    }

    public boolean isWorldSeedKnown(int dimension)
    {
        if (this.worldSeedValid)
        {
            return true;
        }
        else if (this.mc.isSingleplayer())
        {
            MinecraftServer server = this.mc.getIntegratedServer();
            World worldTmp = server.getWorld(dimension);
            return worldTmp != null;
        }

        return false;
    }

    public long getWorldSeed(int dimension)
    {
        if (this.worldSeedValid)
        {
            return this.worldSeed;
        }
        else if (this.mc.isSingleplayer())
        {
            MinecraftServer server = this.mc.getIntegratedServer();
            World worldTmp = server.getWorld(dimension);
            return worldTmp != null ? worldTmp.getSeed() : 0;
        }

        return 0;
    }

    public boolean isWorldSpawnKnown()
    {
        return this.worldSpawnValid;
    }

    public BlockPos getWorldSpawn()
    {
        return this.worldSpawn;
    }

    public boolean isServerTPSValid()
    {
        return this.serverTPSValid;
    }

    public boolean isCarpetServer()
    {
        return this.carpetServer;
    }

    public double getServerTPS()
    {
        return this.serverTPS;
    }

    public double getServerMSPT()
    {
        return this.serverMSPT;
    }

    public void markChunkForHightmapCheck(int chunkX, int chunkZ)
    {
        this.chunkHeightmapsToCheck.add(new ChunkPos(chunkX, chunkZ));
    }

    public void checkQueuedDirtyChunkHightmaps()
    {
        WorldClient world = this.mc.world;

        if (world != null)
        {
            if (this.chunkHeightmapsToCheck.isEmpty() == false)
            {
                for (ChunkPos pos : this.chunkHeightmapsToCheck)
                {
                    Chunk chunk = world.getChunkFromChunkCoords(pos.x, pos.z);
                    int[] heightMap = chunk.getHeightMap();
                    int maxHeight = -1;

                    for (int i = 0; i < heightMap.length; ++i)
                    {
                        if (heightMap[i] > maxHeight)
                        {
                            maxHeight = heightMap[i];
                        }
                    }

                    int subChunks;

                    if (maxHeight >= 0)
                    {
                        subChunks = MathHelper.clamp((maxHeight / 16) + 1, 1, 16);
                    }
                    // Void world? Use the topFilledSegment, see WorldEntitySpawner.getRandomChunkPosition()
                    else
                    {
                        subChunks = MathHelper.clamp((chunk.getTopFilledSegment() + 16) / 16, 1, 16);
                    }

                    //System.out.printf("@ %d, %d - subChunks: %d, maxHeight: %d\n", pos.x, pos.z, subChunks, maxHeight);

                    this.spawnableSubChunks.put(pos, subChunks);
                }
            }
        }
        else
        {
            this.spawnableSubChunks.clear();
        }

        this.chunkHeightmapsToCheck.clear();
    }

    public void onChunkUnload(int chunkX, int chunkZ)
    {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        this.chunkHeightmapsToCheck.remove(pos);
        this.spawnableSubChunks.remove(pos);
    }

    public int getSpawnableSubChunkCountFor(int chunkX, int chunkZ)
    {
        Integer count = this.spawnableSubChunks.get(new ChunkPos(chunkX, chunkZ));
        return count != null ? count.intValue() : -1;
    }

    public boolean onSendChatMessage(EntityPlayer player, String message)
    {
        String[] parts = message.split(" ");

        if (parts.length >= 2 && parts[0].equals("minihud-seed"))
        {
            try
            {
                long seed = Long.parseLong(parts[1]);
                this.worldSeed = seed;
                this.worldSeedValid = true;
                MiscUtils.printInfoMessage("minihud.message.seed_set", Long.valueOf(seed));
                return true;
            }
            catch (NumberFormatException e)
            {
                player.sendMessage(new TextComponentTranslation("minihud.message.error.invalid_seed"));
            }
        }

        return false;
    }

    public void onChatMessage(ITextComponent message)
    {
        if (message instanceof TextComponentTranslation)
        {
            TextComponentTranslation text = (TextComponentTranslation) message;

            // The vanilla "/seed" command
            if ("commands.seed.success".equals(text.getKey()))
            {
                try
                {
                    this.worldSeed = Long.parseLong(text.getFormatArgs()[0].toString());
                    this.worldSeedValid = true;
                    LiteModMiniHud.logger.info("Received world seed from the vanilla /seed command: {}", this.worldSeed);
                    MiscUtils.printInfoMessage("minihud.message.seed_set", Long.valueOf(this.worldSeed));
                }
                catch (Exception e)
                {
                    LiteModMiniHud.logger.warn("Failed to read the world seed from '{}'", text.getFormatArgs()[0], e);
                }
            }
            // The "/jed seed" command
            else if ("jed.commands.seed.success".equals(text.getKey()))
            {
                try
                {
                    this.worldSeed = Long.parseLong(text.getFormatArgs()[1].toString());
                    this.worldSeedValid = true;
                    LiteModMiniHud.logger.info("Received world seed from the JED '/jed seed' command: {}", this.worldSeed);
                    MiscUtils.printInfoMessage("minihud.message.seed_set", Long.valueOf(this.worldSeed));
                }
                catch (Exception e)
                {
                    LiteModMiniHud.logger.warn("Failed to read the world seed from '{}'", text.getFormatArgs()[1], e);
                }
            }
            else if ("commands.setworldspawn.success".equals(text.getKey()) && text.getFormatArgs().length == 3)
            {
                try
                {
                    Object[] o = text.getFormatArgs();
                    int x = Integer.parseInt(o[0].toString());
                    int y = Integer.parseInt(o[1].toString());
                    int z = Integer.parseInt(o[2].toString());

                    this.worldSpawn = new BlockPos(x, y, z);
                    this.worldSpawnValid = true;

                    LiteModMiniHud.logger.info("Received world spawn from the vanilla /setworlspawn command: {}", this.worldSpawn);
                    MiscUtils.printInfoMessage("minihud.message.spawn_set", this.worldSpawn);
                }
                catch (Exception e)
                {
                    LiteModMiniHud.logger.warn("Failed to read the world spawn point from '{}'", text.getFormatArgs(), e);
                }
            }
        }
    }

    public void onServerTimeUpdate(long totalWorldTime)
    {
        // Carpet server sends the TPS and MSPT values via the player list footer data,
        // and for single player the data is grabbed directly from the integrated server.
        if (this.carpetServer == false && this.mc.isSingleplayer() == false)
        {
            long currentTime = System.nanoTime();

            if (this.serverTPSValid)
            {
                long elapsedTicks = totalWorldTime - this.lastServerTick;

                if (elapsedTicks > 0)
                {
                    this.serverMSPT = ((double) (currentTime - this.lastServerTimeUpdate) / (double) elapsedTicks) / 1000000D;
                    this.serverTPS = this.serverMSPT <= 50 ? 20D : (1000D / this.serverMSPT);
                }
            }

            this.lastServerTick = totalWorldTime;
            this.lastServerTimeUpdate = currentTime;
            this.serverTPSValid = true;
        }
    }

    public void updateIntegratedServerTPS()
    {
        if (this.mc != null && this.mc.player != null && this.mc.getIntegratedServer() != null)
        {
            this.serverMSPT = (double) MathHelper.average(this.mc.getIntegratedServer().tickTimeArray) / 1000000D;
            this.serverTPS = this.serverMSPT <= 50 ? 20D : (1000D / this.serverMSPT);
            this.serverTPSValid = true;
        }
    }

    public void handleCarpetServerTPSData(ITextComponent textComponent)
    {
        if (textComponent.getFormattedText().isEmpty() == false)
        {
            String text = TextFormatting.getTextWithoutFormattingCodes(textComponent.getUnformattedText());
            Matcher matcher = PATTERN_CARPET_TPS.matcher(text);

            if (matcher.matches())
            {
                try
                {
                    this.serverTPS = Double.parseDouble(matcher.group("tps"));
                    this.serverMSPT = Double.parseDouble(matcher.group("mspt"));
                    this.serverTPSValid = true;
                    this.carpetServer = true;
                    return;
                }
                catch (NumberFormatException e)
                {
                }
            }
        }

        this.serverTPSValid = false;
    }
}
