package dev.drav3n.bestinslot;

import java.io.File;
import java.net.URLClassLoader;

import org.slf4j.Logger;

import emu.lunarcore.LunarCore;
import emu.lunarcore.command.Command;
import emu.lunarcore.plugin.Plugin;

public class BestInSlotPlugin extends Plugin {

    public BestInSlotPlugin(Identifier identifier, URLClassLoader classLoader, File dataFolder, Logger logger) {
        super(identifier, classLoader, dataFolder, logger);
    }

    public void onLoad() {
        
    }
    
    public void onEnable() {
        LunarCore.getCommandManager().registerCommand(new BestInSlotCommand());
    }
    
    public void onDisable() {
        LunarCore.getCommandManager().unregisterCommand(BestInSlotCommand.class.getAnnotation(Command.class).label());
    }

}
