package com.torandi.boatmod;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Position {
    private int x, y, z;
    
    public static Position fromBlock(Block b) {
        return new Position(b.getX(), b.getY(), b.getZ());
    }
    
    public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public Block getRelative(Block b) {
        return b.getRelative(x, y, z);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
