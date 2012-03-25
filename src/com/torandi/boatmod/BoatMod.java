
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BoatMod extends JavaPlugin implements Listener {
    Logger log;
    
    ArrayList<Boat> boats;
    
    //Stores which block belongs to which boat:
    HashMap<Position, Boat> belonging; 
    
    LinkedList<Boat.Movement> movments; //Movements to execute
    
    
    /**
     * Blocks to use when searching for constructor
     */
    static Material constructorCoreBlock = Material.GOLD_BLOCK;
    static Material constructorSurroundBlock = Material.BRICK;
    //These two are arrays since adjacent block takes an array.
    static Material[] constructorArmBlock = {Material.STONE};
    static Material[] constructorConnectBlock = {Material.IRON_BLOCK};
    
    static Material[] hullMaterials = {Material.LOG, Material.WOOD, Material.IRON_BLOCK};
    static Material[] waterMaterials = {Material.WATER, Material.STATIONARY_WATER};
    static Material[] movableSpace = {Material.WATER, Material.STATIONARY_WATER, Material.AIR};
    
    static Material[] signMaterial = {Material.SIGN, Material.SIGN_POST};
    static Material[] engineMaterials = {Material.IRON_BLOCK};
    
    static int constructor_surround[][] = {{1,0}, {0,1}, {-1, 0}, {0, -1}};
    
           
    static Position[] directions = {
            new Position(-1, 0, 0),
            new Position(0, 0, -1),
            new Position(1, 0, 0),
            new Position(0, 0, 1),
            new Position(0, 1, 0),
            new Position(0, -1, 0),
            
            new Position(-1, 0, -1),
            new Position(-1, 0, 1),
            new Position(1, 0, -1),
            new Position(1, 0, 1),
            
            new Position(-1, 1, 0),
            new Position(0, 1, -1),
            new Position(1, 1, 0),
            new Position(0, 1, 1),                   
            new Position(-1, 1, -1),
            new Position(-1, 1, 1),
            new Position(1, 1, -1),
            new Position(1, 1, 1),  
            new Position(-1, -1, 0),
            new Position(0, -1, -1),
            new Position(1, -1, 0),
            new Position(0, -1, 1),                   
            new Position(-1, -1, -1),
            new Position(-1, -1, 1),
            new Position(1, -1, -1),
            new Position(1, -1, 1)
        };
    
    
    @Override
    public void onEnable() {
        
        log = this.getLogger();
        
        boats = new ArrayList<Boat>();
        belonging  = new HashMap<Position, Boat>();
        movments = new LinkedList<Boat.Movement>();

        log.info("Enabled BoatMod.");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new BoatRunner(this) , BoatRunner.RUNNER_DELAY, BoatRunner.RUNNER_DELAY);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("movboat")) {
            if(args.length != 2) {
                sender.sendMessage("Usage: /movboat {boatid} [xyz][+-]?");
                return true;
            }
            int id;
            Boat boat = null;
            try {
                id = Integer.parseInt(args[0]);
                boat = boats.get(id);
            } catch (NumberFormatException ex) {
                sender.sendMessage("Invalid boat id ("+args[0]+")");
                return true;
            }
            
            if(boat == null) {
                sender.sendMessage("Invalid boat id");
                return true;
            }
            
            if(args[1].length() > 2) {
                sender.sendMessage("Invalid movement. Valid movements are [xyz][+-]?");
                return true;
            }
            
            BlockFace dir=null;
            switch(args[1].charAt(0)) {
                case 'x':
                    dir= BlockFace.SOUTH;
                    break;
                case 'y':
                    dir = BlockFace.UP;
                    break;
                case 'z':
                    dir = BlockFace.WEST;
                    break;
                default:
                    sender.sendMessage("Invalid direction: "+args[1].charAt(0));
                    return true;
            }
            
            if(args[1].length() == 2) {
                switch(args[1].charAt(1)) {
                    case '+':
                        break;
                    case '-':
                        dir = dir.getOppositeFace();
                        break;
                    default:
                        sender.sendMessage("Invalid direction modification: "+args[1].charAt(1));
                        return true;
                }
            }
            
            if(!boat.move(Position.fromBlockFace(dir))) {
                sender.sendMessage("Failed to move boat :(");
            } else {
                sender.sendMessage("Move boat 1 step in direction "+dir.name());
            }
            
            return true;
        } else if(cmd.getName().equalsIgnoreCase("boats")) {
            for(int i=0; i < boats.size(); ++i) {
                sender.sendMessage(i+":  "+boats.get(i));
            }
        }
        
        return false;
    }

    @Override
    public void onDisable() {
        log.info("Disabled BoatMod.");
    }
    
    @EventHandler
    public void redstoneCurrentChange(BlockRedstoneEvent event) {
        //Only trigger if a the current block is an redstone torch:
        if(event.getBlock().getTypeId() == Material.REDSTONE_TORCH_OFF.getId() 
                || event.getBlock().getTypeId() == Material.REDSTONE_TORCH_ON.getId()) {
            
            //Check if block above is a constructor:
            Block core = event.getBlock().getRelative(0, 1, 0);
            
            if(core.getTypeId() == constructorCoreBlock.getId()) {
                //Now verify that we have core surrounding:
                for(int[] pos : constructor_surround) {
                    if(core.getRelative(pos[0], 0, pos[1]).getTypeId() != constructorSurroundBlock.getId()) {
                        log.info("Wrong block type @"+pos[0]+", "+pos[1]);
                        return;
                    }
                }
                Block curArmBlock = core.getRelative(0,1,0);
                //Verify that block above core is of arm-type:
                if(curArmBlock.getTypeId() != constructorArmBlock[0].getId()) {
                    return;
                }
                log.info("All valid!");
                //Core of constructor is correct, now follow the arm to the 
                //connector block:
                BlockState prev = core.getState();
                Block connectorBlock = null;
                Block nextArmBlock = null;
                do {
                    connectorBlock = getAdjacentBlock(curArmBlock, constructorConnectBlock, prev, 6);
                    if(connectorBlock != null)
                        break;
                    nextArmBlock = getAdjacentBlock(curArmBlock, constructorArmBlock, prev, 6);
                    prev = curArmBlock.getState();
                    curArmBlock = nextArmBlock;
                } while(nextArmBlock != null);
                
                if(connectorBlock == null) {
                    log.info("No connector found");
                    return;
                }
                
                //We found a connector block, check for boat material:
                Block hull_core = getAdjacentBlock(connectorBlock, hullMaterials, prev, 6);
                if(hull_core != null) {
                    log.info("Found hull core! ("+hull_core.getType().name()+")");
                    try {
                        Boat boat = new Boat(this, hull_core, connectorBlock);
                        boats.add(boat);
                        getServer().getScheduler().scheduleAsyncRepeatingTask(this, boat, Boat.RUN_DELAY, Boat.RUN_DELAY);
                    } catch (BoatError ex) {
                        log.warning("Failed to create boat");
                    }
                } else {
                    log.info("No boat connected");
                }
                    
            } 
        }
    }
    
    /**
     * Tries to find a adjacent block to the given block of the given material
     * @param prev Previous block (block to ignore)
     * @return The block or null
     */
    public static Block getAdjacentBlock(Block cur, Material[] materials, BlockState prev, int num_directions) {   
        for(int i = 0; i<num_directions; ++i) {
            Block b = directions[i].getRelative(cur);
            if(prev == null || !b.getLocation().equals(prev.getLocation())) {
                if(contains_material(b.getType(), materials))
                    return b;
            }
        }
        return null;
    }
    
    public static boolean contains_material(Material mat, Material[] materials) {
        for(Material m : materials) {
            if(mat.equals(m))
                return true;
        }
        return false;
    }
    
}
