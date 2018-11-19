package fi.dy.masa.minihud.event;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.minihud.Reference;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.InfoToggle;
import fi.dy.masa.minihud.config.RendererToggle;

public class InputHandler implements IKeybindProvider
{
    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler()
    {
        super();
    }

    public static InputHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager)
    {
        for (InfoToggle toggle : InfoToggle.values())
        {
            manager.addKeybindToMap(toggle.getKeybind());
        }

        for (RendererToggle toggle : RendererToggle.values())
        {
            manager.addKeybindToMap(toggle.getKeybind());
        }

        manager.addKeybindToMap(Configs.Generic.TOGGLE_KEY.getKeybind());
        manager.addKeybindToMap(Configs.Generic.REQUIRED_KEY.getKeybind());
        manager.addKeybindToMap(Configs.Generic.OPEN_CONFIG_GUI.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager)
    {
        List<? extends IHotkey> hotkeys = ImmutableList.of( Configs.Generic.TOGGLE_KEY, Configs.Generic.REQUIRED_KEY, Configs.Generic.OPEN_CONFIG_GUI );
        manager.addHotkeysForCategory(Reference.MOD_NAME, "minihud.hotkeys.category.generic_hotkeys", hotkeys);
        manager.addHotkeysForCategory(Reference.MOD_NAME, "minihud.hotkeys.category.info_toggle_hotkeys", ImmutableList.copyOf(InfoToggle.values()));
        manager.addHotkeysForCategory(Reference.MOD_NAME, "minihud.hotkeys.category.renderer_toggle_hotkeys", ImmutableList.copyOf(RendererToggle.values()));
    }
}
