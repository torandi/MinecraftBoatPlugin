
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;


public class Boat implements Runnable{
    public final static long RUN_DELAY = 10L;
    
    private BoatMod plugin;
    private Player creator;
    
    private Position position;
    private BlockState core_block;
    private HashMap<Position, BlockData> blocks;
    
    private int inWater = 0;
    private int weigth = 0;
    private float speed = 0;
    
    private int water_line = Integer.MIN_VALUE;
    
    
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
            ++inWater;
            if(pos.getY() <= water_line || BoatMod.getAdjacentBlock(b, BoatMod.waterMaterials, null, 4)!=null) {
                water_line = Math.max(pos.getY(), water_line);
            }
        }
       
        ++weigth;
        
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
    
    public boolean move(Position movement) {     
        Movement update = new Movement(this);
        
        for(Position pos : blocks.keySet()) {
            Position newpos = pos.add(movement);
            if(blocks.containsKey(newpos)
                    || BoatMod.contains_material(newpos.getRelative(core_block.getBlock()).getType(), BoatMod.movableSpace)
                    ) {
                update.clone_list.put(newpos, pos);
                if(pos.getY() <= water_line) {
                    update.set_list.put(pos, Material.STATIONARY_WATER);
                } else {
                    update.set_list.put(pos, Material.AIR);
                }
            } else {
                //Collision!
                plugin.log.info("COLLISION!");
                return false;
            }
            update.unset_list.add(pos);
        }
        update.clean();
        
        //Update blockdata
        HashMap<Position, BlockData> newblocks = new HashMap<Position, BlockData>();
        for(Position p : update.clone_list.keySet()) {
            Position oldpos = update.clone_list.get(p);
            newblocks.put(p, blocks.get(oldpos));
        }
        
        blocks = newblocks;
        
        plugin.movments.push(update);
        return true;
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
                if(!BoatMod.contains_material(b.getType(), BoatMod.movableSpace)) {
                    plugin.log.info("Adding block "+pos+" type: "+b.getType().name());
                    //Check if adjacent to water:
                    Block water = BoatMod.getAdjacentBlock(b, BoatMod.waterMaterials, null, 6);
                    if(water != null) {
                        if(!BoatMod.contains_material(b.getType(), BoatMod.hullMaterials)) {
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
    
    public String toString() {
        return "{ Boat, weigth: "+weigth+", in water: "+inWater+" }";
    }

    @Override
    public void run() {
        //Do update logic here!
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
    
    class Movement {
        //Clone list is { new_pos => (clone-this-pos)}
        public HashMap<Position, Position> clone_list =  new HashMap<Position, Position>();
        public HashMap<Position, Material> set_list = new HashMap<Position, Material>();
        public HashSet<Position> unset_list = new HashSet<Position>();
        public Boat boat;
        
        Movement(Boat b) {
            boat = b;
        }
        
        
        void clean() {
            for(Position p : clone_list.keySet()) {
                set_list.remove(p);
                unset_list.remove(p);
            }
        }
        
        void debug() {
            plugin.log.info("Boat.Movement:\nClone list:");
            for(Position p : clone_list.keySet()) {
                plugin.log.info(clone_list.get(p) + " => "+p);
            }
            plugin.log.info("Set_list:");
            for(Position p : set_list.keySet()) {
                plugin.log.info(p + " to "+set_list.get(p));
            }
            plugin.log.info("unset_list:");
            for(Position p : unset_list) {
                plugin.log.info(p.toString());
            }
        }
        
        //Execute the movement, must be run from synchronized thread
        void execute(BoatMod plugin) {
            plugin.log.info("Moving boat "+boat.toString());
            debug();
            HashMap<Position, BlockData> blockdata = new HashMap<Position, BlockData>();
            
            Block core = boat.core_block.getBlock();
            
            //Save data
            for(Position p : clone_list.values()) {
                Block b = p.getRelative(core);
                blockdata.put(p, new BlockData(b));
                //Remove the block:
                BlockState s = b.getState();
                if(s instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) s;
                    ih.getInventory().clear();
                }
                s.setType(Material.AIR);
                s.update(true);
            }
            
            //Clear
            for(Position p : set_list.keySet()) {
                BlockState b = p.getRelative(core).getState();
                if(b instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) b;
                    ih.getInventory().clear();
                }
                b.setType(set_list.get(p));
                b.update(true);
            }
            
            //Write data
            for(Position p : clone_list.keySet()) {
                BlockState to = p.getRelative(core).getState();
                BlockData from = blockdata.get(clone_list.get(p));
                
                //Clear inventory of to:
                if(to instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) to;
                    ih.getInventory().clear();
                }
                
                from.set(to);
            }
        }
        
        private class BlockData {
            BlockState block;
            ItemStack[] inventory = null;
            
            public BlockData(Block b) {
                block = b.getState();
                if(block instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) block;
                    inventory = ih.getInventory().getContents().clone();
                }
            }
            
            public void set(BlockState b) {
                b.setType(block.getType());
                b.setData(block.getData());
                b.update(true);
                if(inventory != null) {
                    BlockState updated = b.getBlock().getState();
                    InventoryHolder ih =(InventoryHolder) updated;
                    ih.getInventory().setContents(inventory);
                    updated.update(true);
                }
            }
        }
    }
}
