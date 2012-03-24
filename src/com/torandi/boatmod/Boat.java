
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;


public class Boat {
    private BoatMod plugin;
    
    private Player creator;
    private BlockState core_block; //The block that touched the connector
    private HashMap<Position, Material> blocks; //Maps positions to material 
    private ArrayList<Engine> engines;
    
    public Boat(BoatMod plugin, Block core) {
        this.plugin = plugin;
        plugin.belonging.put(Position.fromBlock(core), this);
        core_block = core.getState();
        blocks.put(new Position(0,0,0), core.getType());
    }
    
    public void addBlock(Block b) {
        Position pos = new Position(
                b.getX() - core_block.getX(),
                b.getY() - core_block.getY(),
                b.getZ() - core_block.getZ()
        );
        blocks.put(pos, b.getType());
    }
    
    /**
     * Unlinks this boat
     */
    public void destroy() {
        Block core = core_block.getBlock();
        for(Position p : blocks.keySet()) {
            if(plugin.belonging.remove(p.getRelative(core)) != this)
                plugin.log.warning("Warning! Unlinked block from boat that was in another boat!");
        }
    }
    
    class Engine {
        Position position;
    }
}
