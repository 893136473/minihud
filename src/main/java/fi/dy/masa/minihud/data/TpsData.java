package fi.dy.masa.minihud.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import fi.dy.masa.malilib.gui.GuiBase;

public class TpsData
{
    private static final Pattern PATTERN_CARPET_TPS = Pattern.compile("TPS: (?<tps>[0-9]+[\\.,][0-9]) MSPT: (?<mspt>[0-9]+[\\.,][0-9])");

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean hasCalculatedTpsData;
    private boolean hasSyncedTpsData;
    private boolean hasPubsubData;
    private double calculatedServerTps;
    private double calculatedServerMspt;
    private double syncedServerTps;
    private double syncedServerMspt;
    private double syncedServerTpsStaging = -1;
    private double syncedServerMsptStaging = -1;
    private long lastSyncTpsStaging;
    private long lastSyncMsptStaging;
    private long lastSync = -1;
    private long lastServerTick = -1;
    private long lastServerTimeUpdate = -1;

    public void clear()
    {
        this.hasCalculatedTpsData = false;
        this.hasSyncedTpsData = false;
        this.hasPubsubData = false;
        this.syncedServerTpsStaging = -1;
        this.syncedServerMsptStaging = -1;
        this.lastSync = -1;
        this.lastServerTick = -1;
        this.lastServerTimeUpdate = -1;
    }

    public boolean getHasValidData()
    {
        return this.hasSyncedTpsData || this.hasCalculatedTpsData;
    }

    public boolean getHasSyncedData()
    {
        return this.hasSyncedTpsData;
    }

    public boolean shouldParsePlayerListData()
    {
        return this.hasPubsubData == false;
    }

    public long getLastSyncedTick()
    {
        return this.lastSync;
    }

    public void handleCarpetServerPubsubTps(double tps)
    {
        this.stageSyncedTps(tps, this.lastServerTick);
    }

    public void handleCarpetServerPubsubMspt(double mspt)
    {
        this.stageSyncedMspt(mspt, this.lastServerTick);
    }

    public void setPlayerListParsedData(double tps, double mspt, long worldTime)
    {
        if (this.hasPubsubData == false)
        {
            this.setSyncedData(tps, mspt, worldTime);
        }
    }

    public void setIntegratedServerData(double tps, double mspt, long worldTime)
    {
        this.setSyncedData(tps, mspt, worldTime);
    }

    private void setSyncedData(double tps, double mspt, long worldTime)
    {
        this.syncedServerTps = tps;
        this.syncedServerMspt = mspt;
        this.lastSync = worldTime;
        this.hasSyncedTpsData = true;
    }

    private void setCalculatedData(double tps, double mspt, long worldTime)
    {
        this.calculatedServerTps = tps;
        this.calculatedServerMspt = mspt;
        this.lastSync = worldTime;
        this.hasCalculatedTpsData = true;
    }

    private void stageSyncedTps(double tps, long worldTime)
    {
        this.syncedServerTpsStaging = tps;
        this.lastSyncTpsStaging = worldTime;
        this.checkStagingComplete();
    }

    private void stageSyncedMspt(double mspt, long worldTime)
    {
        this.syncedServerMsptStaging = mspt;
        this.lastSyncMsptStaging = worldTime;
        this.checkStagingComplete();
    }

    private void checkStagingComplete()
    {
        if (this.syncedServerTpsStaging >= 0 &&
            this.syncedServerMsptStaging >= 0 &&
            Math.abs(this.lastSyncTpsStaging - this.lastSyncMsptStaging) <= 60)
        {
            this.setSyncedData(this.syncedServerTpsStaging, this.syncedServerMsptStaging, this.lastSyncMsptStaging);

            this.hasPubsubData = true;
            this.syncedServerTpsStaging = -1;
            this.syncedServerMsptStaging = -1;
            this.lastSyncTpsStaging = -1;
            this.lastSyncMsptStaging = -1;
        }
    }

    public void onServerTimeUpdate(long totalWorldTime)
    {
        // Carpet server sends the TPS and MSPT values via the player list footer data,
        // and for single player the data is grabbed directly from the integrated server.
        if (this.getHasSyncedData() == false)
        {
            long currentTime = System.nanoTime();

            if (this.lastServerTick != -1 && this.lastServerTimeUpdate != -1)
            {
                long elapsedTicks = totalWorldTime - this.lastServerTick;

                if (elapsedTicks > 0)
                {
                    double mspt = ((double) (currentTime - this.lastServerTimeUpdate) / (double) elapsedTicks) / 1000000D;
                    double tps = mspt <= 50 ? 20D : (1000D / mspt);
                    this.setCalculatedData(tps, mspt, totalWorldTime);
                }
            }

            this.lastServerTimeUpdate = currentTime;
        }
        // Presumably a Carpet server, check that there has been received data recently,
        // otherwise invalidate the data (for example when unsubscribing from the tps logger).
        else if (this.mc.isIntegratedServerRunning() == false && totalWorldTime - this.getLastSyncedTick() > 80)
        {
            this.clear();
            this.lastServerTimeUpdate = System.nanoTime();
        }

        this.lastServerTick = totalWorldTime;
    }

    public void updateIntegratedServerTps()
    {
        if (this.mc.getIntegratedServer() != null && this.mc.world != null)
        {
            double mspt = (double) MathHelper.average(this.mc.getIntegratedServer().tickTimeArray) / 1000000D;
            double tps = mspt <= 50 ? 20D : (1000D / mspt);
            this.setIntegratedServerData(tps, mspt, this.mc.world.getTotalWorldTime());
        }
    }

    public void parsePlayerListFooterTpsData(ITextComponent textComponent)
    {
        if (this.shouldParsePlayerListData() && textComponent.getFormattedText().isEmpty() == false)
        {
            String text = TextFormatting.getTextWithoutFormattingCodes(textComponent.getUnformattedText());
            String[] lines = text.split("\n");

            for (String line : lines)
            {
                Matcher matcher = PATTERN_CARPET_TPS.matcher(line);

                if (matcher.matches())
                {
                    try
                    {
                        this.setPlayerListParsedData(
                                Double.parseDouble(matcher.group("tps")),
                                Double.parseDouble(matcher.group("mspt")),
                                this.lastServerTick);
                    }
                    catch (NumberFormatException e)
                    {
                    }
                }
            }
        }
    }

    public String getFormattedInfoLine()
    {
        if (this.getHasValidData() == false)
        {
            return "Server TPS: <no valid data>";
        }

        boolean isSynced = this.hasSyncedTpsData;
        double tps = isSynced ? this.syncedServerTps : this.calculatedServerTps;
        double mspt = isSynced ? this.syncedServerMspt : this.calculatedServerMspt;
        String rst = GuiBase.TXT_RST;
        String preTps = tps >= 20.0D ? GuiBase.TXT_GREEN : GuiBase.TXT_RED;
        String preMspt;

        // Carpet server and integrated server have actual meaningful MSPT data available
        if (isSynced)
        {
            if      (mspt <= 40) { preMspt = GuiBase.TXT_GREEN; }
            else if (mspt <= 45) { preMspt = GuiBase.TXT_YELLOW; }
            else if (mspt <= 50) { preMspt = GuiBase.TXT_GOLD; }
            else                 { preMspt = GuiBase.TXT_RED; }

            return String.format("Server TPS: %s%.1f%s MSPT: %s%.1f%s", preTps, tps, rst, preMspt, mspt, rst);
        }
        else
        {
            if (mspt <= 51) { preMspt = GuiBase.TXT_GREEN; }
            else            { preMspt = GuiBase.TXT_RED; }

            return String.format("Server TPS: %s%.1f%s (MSPT [est]: %s%.1f%s)", preTps, tps, rst, preMspt, mspt, rst);
        }
    }
}
