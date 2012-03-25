
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.block.Block;


public class Boat {
    private BoatMod plugin;
    
    private Position position;
    private Block core_block;
    private HashSet<Position> blocks; //Maps positions to material 
    private ArrayList<Engine> engines;
    
    public Boat(BoatMod plugin, Block core, Block connector) throws BoatError {
        
        blocks = new HashSet<Position>();
        
        this.plugin = plugin;
        position = Position.fromBlock(core);
        core_block = core;
        addBlock(core);
        if(!recursive_add(new Position(0,0,0), Position.fromBlock(connector,position))) {
            destroy();
            throw new BoatError();
        }       
    }
    
    public final void addBlock(Block b) {
        Position pos = Position.fromBlock(b,position);
        blocks.add(pos);
        remove_previous_boat(b);
        plugin.belonging.put(pos, this);
    }
    
    private void remove_previous_boat(Block b) {
        Boat prev = plugin.belonging.get(Position.fromBlock(b));
        if(prev != null) {
            prev.destroy();
            plugin.boats.remove(prev);
        }
    }
    
    /**
     * Unlinks this boat
     */
    public final void destroy() {
        for(Position p : blocks) {
            if(plugin.belonging.remove(p.getRelative(position)) != this)
                plugin.log.warning("Warning! Unlinked block from boat that was in another boat!");
        }
    }

    public final boolean recursive_add (Position cur, Position connectorPosition) {   
        for(Position dir : BoatMod.directions) {
            Position pos = dir.getRelative(cur);

            if(!blocks.contains(pos) && !pos.equals(connectorPosition)) {
                Block b = pos.getRelative(core_block);
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
                    addBlock(b);
                    if(!recursive_add(pos, connectorPosition))
                        return false;
                }
            }
        }
        return true;
    }
    
    class Engine {
        Position position;
    }
}
