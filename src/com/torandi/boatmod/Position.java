package com.torandi.boatmod;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class Position implements Comparable<Position> {
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
    
    //Performs floor to make the number closer to 0
    public static int fullfloor(double f) {
        return (int) Math.signum(f)*absfloor(f);
    }
    
    public static int absfloor(double f) {
        return (int) Math.floor(Math.abs(f));
    }
    
    public static Position fromVector(Vector v) {
        return new Position(fullfloor(v.getX()), fullfloor(v.getY()), fullfloor(v.getZ()));
    }
    
    public static Position fromVectorAsBlock(Vector v) {
        return new Position(v.getBlockX(), v.getBlockY(), v.getBlockZ());
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

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setZ(int z) {
        this.z = z;
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
    
    public Vector toVector() {
        return new Vector(x, y, z);
    }

    @Override
    public int compareTo(Position p) {
        //First order by y
        if(y < p.y) {
            return -1;
        } else if(y > p.y) {
            return 1;
        } else {
            if(x < p.x) {
                return -1;
            } else if(x > p.x) {
                return 1;
            } else {
                if(z < p.z) {
                    return -1;
                } else if(z > p.z) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }
    }
}
