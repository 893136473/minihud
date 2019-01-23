package fi.dy.masa.minihud.util;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import net.minecraft.client.resource.language.I18n;

public enum BlockGridMode implements IConfigOptionListEntry
{
    ALL         ("all",         "minihud.label.blockgridmode.all"),
    NON_AIR     ("non_air",     "minihud.label.blockgridmode.non_air"),
    ADJACENT    ("adjacent",    "minihud.label.blockgridmode.adjacent");

    private final String configString;
    private final String unlocName;

    private BlockGridMode(String configString, String unlocName)
    {
        this.configString = configString;
        this.unlocName = unlocName;
    }

    @Override
    public String getStringValue()
    {
        return this.configString;
    }

    @Override
    public String getDisplayName()
    {
        return I18n.translate(this.unlocName);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward)
    {
        int id = this.ordinal();

        if (forward)
        {
            if (++id >= values().length)
            {
                id = 0;
            }
        }
        else
        {
            if (--id < 0)
            {
                id = values().length - 1;
            }
        }

        return values()[id % values().length];
    }

    @Override
    public BlockGridMode fromString(String name)
    {
        return fromStringStatic(name);
    }

    public static BlockGridMode fromStringStatic(String name)
    {
        for (BlockGridMode aligment : BlockGridMode.values())
        {
            if (aligment.configString.equalsIgnoreCase(name))
            {
                return aligment;
            }
        }

        return BlockGridMode.ADJACENT;
    }

}
