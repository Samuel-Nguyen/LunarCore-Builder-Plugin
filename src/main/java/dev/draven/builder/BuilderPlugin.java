package dev.draven.builder;

import java.io.File;
import java.net.URLClassLoader;

import org.slf4j.Logger;

import emu.lunarcore.LunarCore;
import emu.lunarcore.command.Command;
import emu.lunarcore.plugin.Plugin;

public class BuilderPlugin extends Plugin {

    public BuilderPlugin(Identifier identifier, URLClassLoader classLoader, File dataFolder, Logger logger) {
        super(identifier, classLoader, dataFolder, logger);
    }

    public void onLoad() {
    }
    
    public void onEnable() {
        LunarCore.getCommandManager().registerCommand(new BuilderCommand());
        LunarCore.getCommandManager().registerCommand(new TeamBuilderCommand());
    }

    public void onDisable() {
        LunarCore.getCommandManager().unregisterCommand(BuilderCommand.class.getAnnotation(Command.class).label());
        LunarCore.getCommandManager().unregisterCommand(TeamBuilderCommand.class.getAnnotation(Command.class).label());
    }

}
