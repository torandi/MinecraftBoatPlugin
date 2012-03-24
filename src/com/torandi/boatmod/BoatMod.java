
package com.torandi.boatmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BoatMod extends JavaPlugin implements Listener {
    Logger log;
    
    ArrayList<Boat> boats;
    
    //Stores which block belongs to which boat:
    HashMap<Position, Boat> belonging; 
    
    
    /**
     * Blocks to use when searching for constructor
     */
    static Material constructorCoreBlock = Material.GOLD_BLOCK;
    static Material constructorSurroundBlock = Material.BRICK;
    //These two are arrays since adjacent block takes an array.
    static Material[] constructorArmBlock = {Material.STONE};
    static Material[] constructorConnectBlock = {Material.IRON_BLOCK};
    
    static Material[] hullMaterials = {Material.LOG, Material.WOOD, Material.IRON_BLOCK};
    
    static int constructor_surround[][] = {{1,0}, {0,1}, {-1, 0}, {0, -1}};
    
           
    static int[][] directions = {
            {-1, 0, 0},
            {0, 0, -1},
            {1, 0, 0},
            {0, 0, 1},
            {0, 1, 0},
            {0, -1, 0},
            
            {-1, 0, -1},
            {-1, 0, 1},
            {1, 0, -1},
            {1, 0, 1},
            
            {-1, 1, 0},
            {0, 1, -1},
            {1, 1, 0},
            {0, 1, 1},                   
            {-1, 1, -1},
            {-1, 1, 1},
            {1, 1, -1},
            {1, 1, 1},  
            {-1, -1, 0},
            {0, -1, -1},
            {1, -1, 0},
            {0, -1, 1},                   
            {-1, -1, -1},
            {-1, -1, 1},
            {1, -1, -1},
            {1, -1, 1}
        };
    
    
    @Override
    public void onEnable() {
        boats = new ArrayList<Boat>();
        belonging  = new HashMap<Position, Boat>();
        
        log = this.getLogger();
        log.info("Enabled BoatMod.");
        getServer().getPluginManager().registerEvents(this, this);
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
                    connectorBlock = getAdjacentBlock(curArmBlock, constructorConnectBlock, prev, false);
                    if(connectorBlock != null)
                        break;
                    nextArmBlock = getAdjacentBlock(curArmBlock, constructorArmBlock, prev, false);
                    prev = curArmBlock.getState();
                    curArmBlock = nextArmBlock;
                } while(nextArmBlock != null);
                
                if(connectorBlock == null) {
                    log.info("No connector found");
                    return;
                }
                
                //We found a connector block, check for boat material:
                Block hull_core = getAdjacentBlock(connectorBlock, hullMaterials, prev, false);
                if(hull_core != null) {
                    log.info("Found hull core! ("+hull_core.getType().name()+")");
                } else {
                    log.info("No boat connected");
                }
                    
            } 
        }
    }
    
    /**
     * Tries to find a adjacent block to the given block of the given material
     * @param prev Previous block (block to ignore)
     * @param all_directions Set to false to only include straight directions
     * @return The block or null
     */
    public static Block getAdjacentBlock(Block cur, Material[] materials, BlockState prev, boolean all_directions) {   
        for(int i = 0; i<(all_directions?directions.length:6); ++i) {
            Block b = cur.getRelative(directions[i][0], directions[i][1], directions[i][2]);
            if(prev == null || !b.getLocation().equals(prev.getLocation())) {
                for(Material mat : materials) {
                    if(b.getTypeId() == mat.getId())
                        return b;
                }
            }
        }
        return null;
    }
    
}
