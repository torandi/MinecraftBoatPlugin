
package com.torandi.boatmod;


public class BoatRunner implements Runnable {
    public static long RUNNER_DELAY = 10L;
    
    
    private BoatMod plugin;
    
    public BoatRunner(BoatMod p) {
        plugin = p;
    }
    
    @Override
    public void run() {
        for(Boat.Movement mov : plugin.movments) {
            mov.execute(plugin);
        }
        plugin.movments.clear();
    }
    
}
