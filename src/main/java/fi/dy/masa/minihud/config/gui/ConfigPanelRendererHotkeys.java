package fi.dy.masa.minihud.config.gui;

import fi.dy.masa.malilib.config.IConfigBoolean;
import fi.dy.masa.malilib.config.gui.ConfigPanelHotkeysBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.RendererToggle;

public class ConfigPanelRendererHotkeys extends ConfigPanelHotkeysBase
{
    public ConfigPanelRendererHotkeys(MiniHudConfigPanel parent)
    {
        super("Renderer Hotkeys", RendererToggle.values(), parent);
    }

    @Override
    protected String getHotkeyComment(IHotkey hotkey)
    {
        return "Hotkey to toggle the '" + ((IConfigBoolean) hotkey).getPrettyName() + "' renderer";
    }

    @Override
    protected void onSettingsChanged()
    {
        super.onSettingsChanged();

        Configs.save();
        Configs.load();
    }
}
