package com.torandi.boatmod;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Position {
    private int x, y, z;
    
    public static Position fromBlockFace(BlockFace bf) {
        return new Position(bf.getModX(), bf.getModY(), bf.getModZ());
    }
    
    public static Position fromBlock(Block b) {
        return new Position(b.getX(), b.getY(), b.getZ());
    }
    
    public static Position fromBlock(Block b, Position relativeTo) {
        return new Position(b.getX() - relativeTo.getX(),
                b.getY() - relativeTo.getY(),
                b.getZ() - relativeTo.getZ());
    }
     
    public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public Block getRelative(Block b) {
        return b.getRelative(x, y, z);
    }

    public Position getRelative(Position p) {
        return add(p);
    }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof Position) {
            Position p = (Position) o;
            return (p.x == x) && (p.y == y) && (p.z == z);
        } else {
            return false;
        }
    }
    
    public Position subtract(Position p) {
        return new Position(x - p.x, y - p.y, z - p.z);
    }
    
    public Position add(Position p) {
        return new Position(x + p.x, y + p.y, z + p.z);
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
    
    public String toString() {
        return "["+x+", "+y+", "+z+"]";
    }
    
    public int hashCode() {
        return x ^ y ^ z;
    }
}
