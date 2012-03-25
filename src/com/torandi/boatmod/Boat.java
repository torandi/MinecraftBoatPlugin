
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;


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
    
    private int max_x=Integer.MIN_VALUE, max_y=Integer.MIN_VALUE, max_z=Integer.MIN_VALUE;
    private int min_x= Integer.MAX_VALUE, min_y=Integer.MAX_VALUE, min_z=Integer.MAX_VALUE;
    
    private int water_line = Integer.MIN_VALUE;
    
    
    private ArrayList<Engine> engines;
    
    public Boat(BoatMod plugin, Block core, Block connector) throws BoatError {
        
        blocks = new HashMap<Position, BlockData>();
        engines = new ArrayList<Engine>();
        
        this.plugin = plugin;
        position = Position.fromBlock(core);
        core_block = core.getState();
        
        //Add the core block:
        addBlock(core);
        
        if(!recursive_add(new Position(0,0,0), Position.fromBlock(connector,position))) {
            destroy();
            throw new BoatError();
        }       
    }
    
    public final void addBlock(Block b) {
        Position pos = Position.fromBlock(b,position);
        BlockData data = new BlockData();
        if(pos.getY() <= water_line || BoatMod.getAdjacentBlock(b, BoatMod.waterMaterials, null, 4)!=null) {
                water_line = Math.max(pos.getY(), water_line);
                ++inWater;
        }
        
        if(b.getType().equals(Material.FURNACE) || b.getType().equals(Material.BURNING_FURNACE)) {
            Engine e = new Engine();
            e.position = pos;
            plugin.log.info("Found engine at "+pos+", powered: "+b.isBlockPowered()+", indirpower: "+b.isBlockIndirectlyPowered());
            plugin.log.info("BT: "+e.getFurnace().getBurnTime());
            if(e.burn()) {
            plugin.log.info("Burning, burntime: "+e.getFurnace().getBurnTime());    
            }
            engines.add(e);
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
        update.core_block = core_block;
        
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
                //TODO: Handle
                return false;
            }
            update.unset_list.add(pos);
        }
        update.clean();
        
        position.add(movement);
        core_block = movement.getRelative(core_block.getBlock()).getState();
        
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
                    addBlock(b);
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
        
        Block getFurnaceBlock() {
            return position.getRelative(core_block.getBlock());
        }
        
        Furnace getFurnace() {
            return (Furnace)position.getRelative(core_block.getBlock()).getState();
        }
        
        boolean burn() {
            Furnace f = getFurnace();
            if(f.getBurnTime() > 0)
                return true;
            else {
                ItemStack fuel = f.getInventory().getFuel();
                short fuelTime = fuelTime(fuel);
                if(fuelTime > 0) {
                    byte data = getFurnaceBlock().getData();
                    ItemStack[] all_content = f.getInventory().getContents().clone();
                    getFurnace().getInventory().clear();
                    getFurnaceBlock().setType(Material.BURNING_FURNACE);
                    getFurnaceBlock().setData(data);
                    getFurnace().getInventory().setContents(all_content);
                    fuel = getFurnace().getInventory().getFuel();
                    fuel.setAmount(fuel.getAmount()-1);
                    getFurnace().setBurnTime(fuelTime);
                    return true;
                } else {
                    return false;
                }
            }
        }
            
        //Copied from server code
        private short fuelTime(ItemStack itemstack) {
            if (itemstack == null) {
                return 0;
            } else {
                int i = itemstack.getTypeId();
                return  (short) (i == Material.WOOD.getId() ? 300 : (i == Material.STICK.getId() ? 100 : (i == Material.COAL.getId() ? 1600 : (i == Material.LAVA_BUCKET.getId() ? 20000 : (i == Material.SAPLING.getId() ? 100 : (i == Material.BLAZE_ROD.getId() ? 2400 : 0))))));
            }
        }
    }
    
    class Movement {
        //Clone list is { new_pos => (clone-this-pos)}
        public HashMap<Position, Position> clone_list =  new HashMap<Position, Position>();
        public HashMap<Position, Material> set_list = new HashMap<Position, Material>();
        public HashSet<Position> unset_list = new HashSet<Position>();
        public BlockState core_block;
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
            TreeSet<Position> positions = new TreeSet<Position>();
            
            Block core = core_block.getBlock();
            
            //Save data
            for(Position p : clone_list.values()) {
                Block b = p.getRelative(core);
                blockdata.put(p, new BlockData(b));
                plugin.log.info("Save: "+p+" is "+b.getType().name());
            }
            
            for(Position p : clone_list.values()) {
                Block b = p.getRelative(core);
                //Remove the block
                //I do this to prevent errors with block not allowed to be next to each other
                // (ex. two chests)
                BlockState s = b.getState();
                if(s instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) s;
                    ih.getInventory().clear();
                }
                s.setType(Material.AIR);
                 
                s.update(true);
            }
            
            positions.addAll(clone_list.keySet());
            positions.addAll(set_list.keySet());
            
            
            byte zero = 0;
            
            //Write data
            for(Position p : positions) {
                Position clone_pos = clone_list.get(p);
                
                if(clone_pos != null) {
                    Block to = p.getRelative(core);
                    BlockData from = blockdata.get(clone_pos);

                    //Clear inventory of to:
                    if(to.getState() instanceof InventoryHolder) {
                        InventoryHolder ih = (InventoryHolder) to.getState();
                        ih.getInventory().clear();
                    }
                    plugin.log.info("Set "+p+" to "+Material.getMaterial(from.type).name());
                    from.set(to);
                    //Update boat data:
                    plugin.belonging.put(clone_pos, boat);
                } else {
                    Material set_mtl = set_list.get(p);
                    if(set_mtl != null) {
                        BlockState block = p.getRelative(core).getState();
                        if(block instanceof InventoryHolder) {
                            InventoryHolder ih = (InventoryHolder) block;
                            ih.getInventory().clear();
                        }
                        block.setData(new MaterialData(0));
                        block.setType(set_mtl);
                        block.update(true);
                    }
                }
            }
                
            for(Position p : unset_list) {
                plugin.belonging.remove(p);
            }
        }
        
        private class BlockData {
            int type;
            MaterialData data;
            ItemStack[] inventory = null;
            
            public BlockData(Block b) {
                BlockState state = b.getState();
                if(state instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) state;
                    inventory = ih.getInventory().getContents().clone();
                }
                type = b.getTypeId();
                data = state.getData().clone();
            }
            
            public void set(Block b) {
                BlockState state = b.getState();
                state.setTypeId(type);
                state.setData(data);
                if(!state.update(true)) {
                    plugin.log.warning("Failed to set block to "+Material.getMaterial(type).name());
                }
                state = state.getBlock().getState();
                if(inventory != null) {
                    InventoryHolder ih =(InventoryHolder) state;
                    ih.getInventory().setContents(inventory);
                    state.update(true);
                }
            }
        }
    }
}
