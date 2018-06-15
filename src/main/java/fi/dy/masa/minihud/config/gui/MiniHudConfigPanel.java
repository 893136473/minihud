package fi.dy.masa.minihud.config.gui;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.gui.ConfigPanelBase;
import fi.dy.masa.minihud.Reference;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.InfoToggle;

public class MiniHudConfigPanel extends ConfigPanelBase
{
    @Override
    protected String getPanelTitlePrefix()
    {
        return Reference.MOD_NAME + " options";
    }

    @Override
    protected void createSubPanels()
    {
        this.addSubPanel(new ConfigPanelSubMinihud("Generic", Configs.Generic.OPTIONS.toArray(new IConfigValue[Configs.Generic.OPTIONS.size()]), this));
        this.addSubPanel(new ConfigPanelSubMinihud("Info Line Order", ConfigUtils.createConfigWrapperForType(ConfigType.INTEGER, InfoToggle.values()), this));
        this.addSubPanel((new ConfigPanelSubMinihud("Info Toggles", InfoToggle.values(), this)).setElementWidth(120));
        this.addSubPanel(new ConfigPanelInfoHotkeys(this));
        this.addSubPanel(new ConfigPanelRendererHotkeys(this));
    }
}
