
package com.torandi.boatmod;

import com.torandi.boatmod.Boat.Engine.EngineError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;


public class Boat implements Runnable{
    public final static long RUN_DELAY = 10L; //server ticks / boat tick
    private final static float ENGINE_POWER = 10; //The weigth one engine can move per tick
    
    private final static boolean debug_movement = true;
    
    static enum EngineType {
        DIRECTIONAL,
        ROTATIONAL
    };
    
    public boolean dirty = false; //Set to true if it has movements that are not yet executed
    
    private BoatMod plugin;
    private Player creator;
    
    private Position position;
    private BlockState core_block;
    private HashMap<Position, BlockData> blocks;
    
    private int inWater = 0;
    
    private int max_x=Integer.MIN_VALUE, max_y=Integer.MIN_VALUE, max_z=Integer.MIN_VALUE;
    private int min_x= Integer.MAX_VALUE, min_y=Integer.MAX_VALUE, min_z=Integer.MAX_VALUE;
    
    private Position min, max;
    
    private int water_line = Integer.MIN_VALUE;
    private int weigth = 0;
    private int movement_cost;
    
    private Position engine_momentum;
    
    
    private ArrayList<Engine> engines;
    private ArrayList<Position> speed_signs; //Update signs at these position with info
    
    public Boat(BoatMod plugin, Block core, Block connector) throws BoatError {
        
        blocks = new HashMap<Position, BlockData>();
        engines = new ArrayList<Engine>();
        speed_signs = new ArrayList<Position>();
        engine_momentum = new Position(0, 0, 0);
        
        this.plugin = plugin;
        position = Position.fromBlock(core);
        core_block = core.getState();
        
        //Add the core block:
        addBlock(core);
        
        if(!recursive_add(new Position(0,0,0), Position.fromBlock(connector,position))) {
            destroy();
            throw new BoatError();
        }
        
        min = new Position(min_x, min_y, min_z);
        max = new Position(max_x, max_y, max_z);
        
        movement_cost = (int)Math.ceil(weigth/ENGINE_POWER);
    }
    
    public final void addBlock(Block b) {
        Position pos = Position.fromBlock(b,position);
        BlockData data = new BlockData();
        if(pos.getY() <= water_line || BoatMod.getAdjacentBlock(b, BoatMod.waterMaterials, null, 4)!=null) {
                water_line = Math.max(pos.getY(), water_line);
                ++inWater;
        }
        
        if(BoatMod.contains_material(b.getType(), BoatMod.furnaceMaterials)) {
            Engine e;
            try {
                plugin.log.info("Found engine at "+pos+", powered: "+b.isBlockIndirectlyPowered());
                e = new Engine(pos);
                if(e.isRotational()) {
                    plugin.rotational_engines.put(e.getRealPosition(), e);
                }
                plugin.log.info("Type: "+e.getType()+", Direction: "+e.getDirection());
                engines.add(e);
            } catch (BoatError ex) {}
        }
        
        if(BoatMod.contains_material(b.getType(), BoatMod.signMaterial)) {
            Sign sign = (Sign)b.getState();
            if(sign.getLine(0).toLowerCase().startsWith("speed")) {
                plugin.log.info("Found speed sign");
                speed_signs.add(pos);
            }
        }
       
        min_x = Math.min(min_x, pos.getX());
        min_y = Math.min(min_y, pos.getY());
        min_z = Math.min(min_z, pos.getZ());

        max_x = Math.max(max_x, pos.getX());
        max_y = Math.max(max_y, pos.getY());
        max_z = Math.max(max_z, pos.getZ());
        
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
        if(dirty) {
            plugin.log.warning("Trying to move again before previous movement has executed! Performing movements to slow!");
            return false;
        } 

                //Clone list is { new_pos => (clone-this-pos)}
        HashMap<Position, Position> clone_list =  new HashMap<Position, Position>();
        HashMap<Position, Material> set_list = new HashMap<Position, Material>();
        
        Movement update = new Movement(this);
        update.core_block = core_block;
        update.movement = movement;

        
        for(Position pos : blocks.keySet()) {
            
            
            Position newpos = pos.add(movement);
            
            if(blocks.containsKey(newpos)
                    || BoatMod.contains_material(newpos.getRelative(core_block.getBlock()).getType(), BoatMod.movableSpace)
                    ) {
                clone_list.put(newpos, pos);
                if(pos.getY() <= water_line) {
                    set_list.put(pos, Material.STATIONARY_WATER);
                } else {
                   set_list.put(pos, Material.AIR);
                }
            } else {
                //Collision!
                plugin.log.info("COLLISION!");
                //TODO: Handle
                return false;
            }
            update.unset_list.add(pos);
        }
        
        //Build lists to update:
        
        for(Position p : clone_list.keySet()) {
            set_list.remove(p); //Remove this block from the set list
            update.clone_list.add(new Position2(p, clone_list.get(p)));
        }
        
        for(Position p : set_list.keySet()) {
            update.set_list.add(new PositionMaterial(p, set_list.get(p)));
        }
        
        Collections.sort(update.clone_list);
        Collections.sort(update.set_list,Collections.reverseOrder()); //Sort in reverse, we want to set stuff above first
        
        position.add(movement);
        core_block = movement.getRelative(core_block.getBlock()).getState();
        
        
        dirty = true;
        plugin.movments.push(update);
        return true;
    }
     
    /**
     * Unlinks this boat
     */
    public final void destroy() {
        //Remove rotational engines:
        for(Engine e : engines) {
            if(e.isRotational()) {
                plugin.rotational_engines.remove(e.getRealPosition());
            }
        }
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
    
    @Override
    public String toString() {
        return "{ Boat, weigth: "+weigth+", in water: "+inWater+" }";
    }
    
    @Override
    public void run() {
        int rotation = 0;
        boolean any_directional = false;
        
        for(Engine e : engines) {
            if(e.run()) {
                if(e.isDirectional()) {
                    engine_momentum =  engine_momentum.add(e.getDirection());
                    any_directional = true;
                } else if(e.isRotational()) {
                    rotation += e.getRotation();
                }
            }
        }
        
        //At the moment we can't do both rotational and normal movement at once
        if(rotation != 0) {
            plugin.log.info("Rotate "+rotation);
        } else if(any_directional) {
            int x=0;
            int z=0;
            if(Math.abs(engine_momentum.getX()) >= movement_cost ) {
                x=(int) Math.signum(engine_momentum.getX());
            }
            if(Math.abs(engine_momentum.getZ()) >= movement_cost ) {
                z=(int) Math.signum(engine_momentum.getZ());
            }
            
            //Remove current movement, keep overflow, but never more than movement_cost:
            engine_momentum = new Position(
                   engine_momentum.getX()%movement_cost,
                   0,
                   engine_momentum.getZ()%movement_cost);
            if(x != 0 || z != 0) {
                //Move!
                move(new Position(x, 0, z));
            }
        } else {
            //Stop totaly if no engine are on
            engine_momentum.setX(0);
            engine_momentum.setZ(0);
        }
        
        
    }
    
    public void update_speed_signs() {
        //DecimalFormat df = new DecimalFormat("#.##");
        /*for(Position p : speed_signs) {
            BlockState state = p.getRelative(core_block.getBlock()).getState();
            if(state instanceof Sign) {
                Sign sign = (Sign) state;
                sign.setLine(0, "Speed:");
                sign.setLine(1, "X: "
                sign.setLine(2, "Y: "+df.format(speed.getZ()).toString());
                sign.update();
            }
        }*/
    }
    
    static class BlockData {
        Position waterContact = null;
        
        boolean hasWaterContact() {
            return (waterContact!=null);
        }
    }
    
    class Engine {
        
        private Position pos;
        private Position direction;
        private EngineType type;
        
        public Engine(Position pos) throws BoatError {
            this.pos = pos;
            Block b = BoatMod.getAdjacentBlock(pos.getRelative(core_block.getBlock()),
                    BoatMod.engineMaterials, null, 6);
            if(b == null) {
                throw new BoatError();
            }
            direction = pos.subtract(Position.fromBlock(b,position));
            if(direction.getY() == 0) {
                type = EngineType.DIRECTIONAL;
            } else {
                type = EngineType.ROTATIONAL;
            }
            last_power_state = getFurnaceBlock().isBlockIndirectlyPowered();
        }

        //Is set to the last power state that was registred
        //  Is used to detect rising_edge
        //  Only relevant for rotational engine
        boolean last_power_state = false;
            
        public Position getDirection() {
            return direction;
        }
        
        public int getRotation() {
            return direction.getY();
        }

        public Position getRealPosition() {
            return pos.getRelative(position);
        }
        
        public Position getPosition() {
            return pos;
        }

        public EngineType getType() {
            return type;
        }
        
        public boolean isDirectional() {
            return type == EngineType.DIRECTIONAL;
        }
       
        public boolean isRotational() {
            return type == EngineType.ROTATIONAL;
        }
        
        //Detects rising edge
        public boolean detectRisingEdge() {
            boolean current_power_state = getFurnaceBlock().isBlockIndirectlyPowered();
            if(last_power_state == false && current_power_state==true) {
                last_power_state = current_power_state;
                return true;
            } else {
                last_power_state = current_power_state;
                return false;
            }
        }
        
        Block getFurnaceBlock() {
            return pos.getRelative(core_block.getBlock());
        }
        
        Furnace getFurnace() throws EngineError {
            BlockState state = pos.getRelative(core_block.getBlock()).getState();
            if(state instanceof Furnace) {
                return (Furnace)state;
            } else {
                throw new EngineError("No longer a furnace");
            }
        }
        
        /*
         * Checks if powered and makes sure it is burning
         * @return True if the engine is running.
         * @throws EngineError if the engine has moved
         */
        boolean run() {
            try {
                if(isDirectional()) {
                    return getFurnaceBlock().isBlockIndirectlyPowered() && burn();
                } else {//Rotational 
                    return detectRisingEdge() && burn();
                }
            } catch(EngineError e) {
                plugin.log.warning("Caught EngineError: "+e.getMessage());
                return false;
            }
        }
        
        boolean burn() throws EngineError {
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
        
        class EngineError extends Exception {

            private EngineError(String string) {
                super(string);
            }
            
        }
    }
    
    /*
     * Contains two positions, sorted by the first
     */
    static class Position2 implements Comparable<Position2> {
        public Position p1,p2;
        
        public Position2(Position pos1, Position pos2) {
            p1 = pos1;
            p2 = pos2;
        }
        
        @Override
        public int compareTo(Position2 p) {
            return p1.compareTo(p.p1);
        }
    }
    
    static class PositionMaterial implements Comparable<PositionMaterial> {
        public Position pos;
        public Material mtl;
        
        public PositionMaterial(Position p, Material m ){
            pos = p;
            mtl = m;
        }
        
        @Override
        public int compareTo(PositionMaterial p) {
            return pos.compareTo(p.pos);
        }
    }
    
    class Movement {

        public BlockState core_block;
        public Boat boat;
        public Position movement;
        public ArrayList<Position2> clone_list = new ArrayList<Position2>(); //Clone these positions
        public ArrayList<PositionMaterial> set_list = new ArrayList<PositionMaterial>(); //Set these positions to these materials
        public HashSet<Position> unset_list = new HashSet<Position>();

        
        Movement(Boat b) {
            boat = b;
        }
        
        //Execute the movement, must be run from synchronized thread
        void execute(BoatMod plugin) {
            if(debug_movement) {
                plugin.log.info("Moving boat "+boat.toString());
            }
            HashMap<Position, BlockData> blockdata = new HashMap<Position, BlockData>();
            
            Block core = core_block.getBlock();
            
            //Save data
            for(Position2 p2 : clone_list) {
                Block b = p2.p2.getRelative(core);
                blockdata.put(p2.p2, new BlockData(b));
                if(debug_movement)
                    plugin.log.info("Save: "+p2.p2+" is "+b.getType().name());
            }
            
            
            //Run the clone list in reverse:
            for(int i=clone_list.size()-1;i>=0;--i) {
                Position p = clone_list.get(i).p2;
                
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
            
            
            
            byte zero = 0;
            
            for(Position2 pos : clone_list) {
                Block to = pos.p1.getRelative(core);
                BlockData from = blockdata.get(pos.p2);

                //Clear inventory of to:
                if(to.getState() instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) to.getState();
                    ih.getInventory().clear();
                }
                if(debug_movement)
                    plugin.log.info("Set "+pos.p1+" to "+Material.getMaterial(from.type).name());
                from.set(to);
                //Update boat data:
                plugin.belonging.put(pos.p2, boat);
            }
            
            for(PositionMaterial pm : set_list) {
                BlockState block = pm.pos.getRelative(core).getState();
                if(block instanceof InventoryHolder) {
                    InventoryHolder ih = (InventoryHolder) block;
                    ih.getInventory().clear();
                }
                block.setData(new MaterialData(0));
                block.setType(pm.mtl);
                block.update(true);
            }
            
            
            List<Player> players = core_block.getWorld().getPlayers();
            
            Vector v_min, v_max;
            v_min = min.add(position).toVector();
            v_max = max.add(position).toVector();
            
            //Capture players that are on the edge+a bit above
            v_max = v_max.add(new Vector(0.5, 5, 0.5));
            v_min = v_min.subtract(new Vector(0.5, 0, 0.5));
            
            for(Player p : players) {
                if(p.getLocation().toVector().isInAABB(v_min, v_max)) {
                    plugin.log.info("Move player "+p.getDisplayName());
                    Location newLocation = p.getLocation().add(movement.toVector());
                    p.teleport(newLocation, TeleportCause.PLUGIN);
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
