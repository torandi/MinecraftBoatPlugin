
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
    
    private Position position;
    private BlockState core_block;
    private HashMap<Position, BlockData> blocks;
    
    private float speed = 0;
    
    
    private ArrayList<Engine> engines;
    
    public Boat(BoatMod plugin, Block core, Block connector) throws BoatError {
        
        blocks = new HashMap<Position, BlockData>();
        
        this.plugin = plugin;
        position = Position.fromBlock(core);
        core_block = core.getState();
        
        //Add the core block:
        Block water = BoatMod.getAdjacentBlock(core, BoatMod.waterMaterials, null, 6);
        addBlock(core, water);
        
        if(!recursive_add(new Position(0,0,0), Position.fromBlock(connector,position))) {
            destroy();
            throw new BoatError();
        }       
    }
    
    public final void addBlock(Block b, Block water) {
        Position pos = Position.fromBlock(b,position);
        BlockData data = new BlockData();
        if(water != null) {
            data.waterContact = Position.fromBlock(water, position);
        }
        
        blocks.put(pos, data);
        remove_previous_boat(b);
        plugin.belonging.put(Position.fromBlock(b), this);
    }
    
    private void remove_previous_boat(Block b) {
        Boat prev = plugin.belonging.get(Position.fromBlock(b));
        if(prev != null) {
            prev.destroy();
            plugin.boats.remove(prev);
            plugin.log.info("Destroyed old boat");
        }
    }
    
    public void move(Position movement) {
        
    }
    
    /**
     * Unlinks this boat
     */
    public final void destroy() {
        for(Position p : blocks.keySet()) {
            if(plugin.belonging.remove(p.getRelative(position)) != this)
                plugin.log.warning("Warning! Unlinked block from boat that was in another boat!");
        }
    }

    public final boolean recursive_add (Position cur, Position connectorPosition) {   
        for(Position dir : BoatMod.directions) {
            Position pos = dir.getRelative(cur);

            if(!blocks.containsKey(pos) && !pos.equals(connectorPosition)) {
                Block b = pos.getRelative(core_block.getBlock());
                if(b.getTypeId() != Material.AIR.getId() 
                        && !BoatMod.contains_material(b.getType(), BoatMod.waterMaterials)) {
                    plugin.log.info("Adding block "+pos+" type: "+b.getType().name());
                    //Check if adjacent to water:
                    Block water = BoatMod.getAdjacentBlock(b, BoatMod.waterMaterials, null, 6);
                    if(water != null) {
                        boolean is_hull = false;
                        for(Material m : BoatMod.hullMaterials) {
                            if(b.getTypeId() == m.getId()) {
                                is_hull = true;
                                break;
                            }
                        }
                        if(!is_hull) {
                            plugin.getServer().broadcastMessage("Boat Creation: Block at position "+pos+" is not of a valid hull material.");
                            return false;
                        }
                    }
                    //Everything is fine, let's add and recurse!
                    addBlock(b, water);
                    if(!recursive_add(pos, connectorPosition))
                        return false;
                }
            }
        }
        return true;
    }
    
    class BlockData {
        Position waterContact = null;
        
        boolean hasWaterContact() {
            return (waterContact!=null);
        }
    }
    
    class Engine {
        Position position;
    }
}
