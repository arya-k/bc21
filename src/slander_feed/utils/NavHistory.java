package slander_feed.utils;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import slander_feed.Robot;

/**
 * Class to track visited chunks and exploration.
 * Should only be used by the Nav class.
 */
public class NavHistory {
    private static final StringBuilder keys = new StringBuilder();
    private static int SIGHT_RANGE;

    private static int northEdge = 100; // sentinels
    private static int eastEdge = 100;
    private static int southEdge = -100;
    private static int westEdge = -100;


    /**
     * Inits the random var and the sight range.
     */
    public static void init() {
        SIGHT_RANGE = (int) Math.floor(Math.sqrt(Robot.rc.getType().sensorRadiusSquared));
    }

    /**
     * Update the known locations of edges, and the visited chunks. Call on every tick.
     */
    public static void update() throws GameActionException {
        MapLocation m = Robot.rc.getLocation();

        if (northEdge == 100 && !Robot.rc.onTheMap(m.translate(0, SIGHT_RANGE))) {
            northEdge = ((m.y + SIGHT_RANGE - Robot.initLoc.y) / 4) + 15;
            if ((m.y + SIGHT_RANGE - Robot.initLoc.y) % 4 != 0) ++northEdge;
        }
        if (southEdge == -100 && !Robot.rc.onTheMap(m.translate(0, -1 * SIGHT_RANGE))) {
            southEdge = ((m.y - SIGHT_RANGE - Robot.initLoc.y) / 4) + 15;
            if ((m.y - SIGHT_RANGE - Robot.initLoc.y) % 4 != 0) --southEdge;
        }
        if (eastEdge == 100 && !Robot.rc.onTheMap(m.translate(SIGHT_RANGE, 0))) {
            eastEdge = ((m.x + SIGHT_RANGE - Robot.initLoc.x) / 4) + 15;
            if ((m.x + SIGHT_RANGE - Robot.initLoc.x) % 4 != 0) ++eastEdge;
        }
        if (westEdge == -100 && !Robot.rc.onTheMap(m.translate(-1 * SIGHT_RANGE, 0))) {
            westEdge = ((m.x - SIGHT_RANGE - Robot.initLoc.x) / 4) + 15;
            if ((m.x - SIGHT_RANGE - Robot.initLoc.x) % 4 != 0) --westEdge;
        }

        // mark current location as visited.
        int x = ((m.x - Robot.initLoc.x) / 4) + 15; // [-15,15] -> [0,32)
        int y = ((m.y - Robot.initLoc.y) / 4) + 15;

        String key = "^" + (char) x + (char) y;
        if (keys.indexOf(key) == -1)
            keys.append(key);
    }

    /**
     * Forces a location to be marked as visited. Used for when the pathing
     * is unable to get to a certain location...
     */
    public static void mark_visited(MapLocation m) {
        int x = ((m.x - Robot.initLoc.x) / 4) + 15; // [-15,15] -> [0,32)
        int y = ((m.y - Robot.initLoc.y) / 4) + 15;

        String key = "^" + (char) x + (char) y;
        if (keys.indexOf(key) == -1)
            keys.append(key);
    }

    /**
     * Checks whether a location has been visited. True if it is outside the range
     * of valid edges. Accounts for world borders as well.
     *
     * @param m the MapLocation to check
     * @return whether the clamped mapLocation appears in the set of visited locations.
     */
    public static boolean visited(MapLocation m) {
        int x = ((m.x - Robot.initLoc.x) / 4) + 15; // [-15,15] -> [0,32)
        int y = ((m.y - Robot.initLoc.y) / 4) + 15;
        return visited(x, y);
    }

    /**
     * Same as visited, but assumes that dx and dy are already calculated.
     *
     * @param x the x coordinate, with 15, being the center
     * @param y the y coordinate, with 15 being the center
     * @return whether the MapLocation has been visited, accounting for off-the-map errors!
     */
    private static boolean visited(int x, int y) {
        if (y >= northEdge || y <= southEdge || x >= eastEdge || x <= westEdge) return true; // off map
        return keys.indexOf("^" + (char) x + (char) y) != -1;
    }

    public static MapLocation nearestUnexploredLocation() {
        int RAND = (int) (Math.random() * 1024);

        MapLocation m = Robot.rc.getLocation();
        int cx = ((m.x - Robot.initLoc.x) / 4) + 15;
        int cy = ((m.y - Robot.initLoc.y) / 4) + 15;

        // r^2 = 1
        if (!visited(cx + CHUNKS[0][(RAND) % 4][0], cy + CHUNKS[0][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[0][(RAND) % 4][0], 4 * CHUNKS[0][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[0][(1 + RAND) % 4][0], cy + CHUNKS[0][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[0][(1 + RAND) % 4][0], 4 * CHUNKS[0][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[0][(2 + RAND) % 4][0], cy + CHUNKS[0][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[0][(2 + RAND) % 4][0], 4 * CHUNKS[0][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[0][(3 + RAND) % 4][0], cy + CHUNKS[0][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[0][(3 + RAND) % 4][0], 4 * CHUNKS[0][(3 + RAND) % 4][1]);

        // r^2 = 2
        if (!visited(cx + CHUNKS[1][(RAND) % 4][0], cy + CHUNKS[1][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[1][(RAND) % 4][0], 4 * CHUNKS[1][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[1][(1 + RAND) % 4][0], cy + CHUNKS[1][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[1][(1 + RAND) % 4][0], 4 * CHUNKS[1][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[1][(2 + RAND) % 4][0], cy + CHUNKS[1][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[1][(2 + RAND) % 4][0], 4 * CHUNKS[1][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[1][(3 + RAND) % 4][0], cy + CHUNKS[1][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[1][(3 + RAND) % 4][0], 4 * CHUNKS[1][(3 + RAND) % 4][1]);

        // r^2 = 4
        if (!visited(cx + CHUNKS[2][(RAND) % 4][0], cy + CHUNKS[2][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[2][(RAND) % 4][0], 4 * CHUNKS[2][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[2][(1 + RAND) % 4][0], cy + CHUNKS[2][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[2][(1 + RAND) % 4][0], 4 * CHUNKS[2][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[2][(2 + RAND) % 4][0], cy + CHUNKS[2][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[2][(2 + RAND) % 4][0], 4 * CHUNKS[2][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[2][(3 + RAND) % 4][0], cy + CHUNKS[2][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[2][(3 + RAND) % 4][0], 4 * CHUNKS[2][(3 + RAND) % 4][1]);

        // r^2 = 5
        if (!visited(cx + CHUNKS[3][(RAND) % 8][0], cy + CHUNKS[3][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(RAND) % 8][0], 4 * CHUNKS[3][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(1 + RAND) % 8][0], cy + CHUNKS[3][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(1 + RAND) % 8][0], 4 * CHUNKS[3][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(2 + RAND) % 8][0], cy + CHUNKS[3][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(2 + RAND) % 8][0], 4 * CHUNKS[3][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(3 + RAND) % 8][0], cy + CHUNKS[3][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(3 + RAND) % 8][0], 4 * CHUNKS[3][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(4 + RAND) % 8][0], cy + CHUNKS[3][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(4 + RAND) % 8][0], 4 * CHUNKS[3][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(5 + RAND) % 8][0], cy + CHUNKS[3][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(5 + RAND) % 8][0], 4 * CHUNKS[3][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(6 + RAND) % 8][0], cy + CHUNKS[3][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(6 + RAND) % 8][0], 4 * CHUNKS[3][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[3][(7 + RAND) % 8][0], cy + CHUNKS[3][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[3][(7 + RAND) % 8][0], 4 * CHUNKS[3][(7 + RAND) % 8][1]);

        // r^2 = 8
        if (!visited(cx + CHUNKS[4][(RAND) % 4][0], cy + CHUNKS[4][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[4][(RAND) % 4][0], 4 * CHUNKS[4][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[4][(1 + RAND) % 4][0], cy + CHUNKS[4][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[4][(1 + RAND) % 4][0], 4 * CHUNKS[4][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[4][(2 + RAND) % 4][0], cy + CHUNKS[4][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[4][(2 + RAND) % 4][0], 4 * CHUNKS[4][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[4][(3 + RAND) % 4][0], cy + CHUNKS[4][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[4][(3 + RAND) % 4][0], 4 * CHUNKS[4][(3 + RAND) % 4][1]);

        // r^2 = 9
        if (!visited(cx + CHUNKS[5][(RAND) % 4][0], cy + CHUNKS[5][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[5][(RAND) % 4][0], 4 * CHUNKS[5][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[5][(1 + RAND) % 4][0], cy + CHUNKS[5][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[5][(1 + RAND) % 4][0], 4 * CHUNKS[5][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[5][(2 + RAND) % 4][0], cy + CHUNKS[5][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[5][(2 + RAND) % 4][0], 4 * CHUNKS[5][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[5][(3 + RAND) % 4][0], cy + CHUNKS[5][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[5][(3 + RAND) % 4][0], 4 * CHUNKS[5][(3 + RAND) % 4][1]);

        // r^2 = 10
        if (!visited(cx + CHUNKS[6][(RAND) % 8][0], cy + CHUNKS[6][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(RAND) % 8][0], 4 * CHUNKS[6][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(1 + RAND) % 8][0], cy + CHUNKS[6][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(1 + RAND) % 8][0], 4 * CHUNKS[6][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(2 + RAND) % 8][0], cy + CHUNKS[6][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(2 + RAND) % 8][0], 4 * CHUNKS[6][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(3 + RAND) % 8][0], cy + CHUNKS[6][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(3 + RAND) % 8][0], 4 * CHUNKS[6][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(4 + RAND) % 8][0], cy + CHUNKS[6][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(4 + RAND) % 8][0], 4 * CHUNKS[6][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(5 + RAND) % 8][0], cy + CHUNKS[6][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(5 + RAND) % 8][0], 4 * CHUNKS[6][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(6 + RAND) % 8][0], cy + CHUNKS[6][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(6 + RAND) % 8][0], 4 * CHUNKS[6][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[6][(7 + RAND) % 8][0], cy + CHUNKS[6][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[6][(7 + RAND) % 8][0], 4 * CHUNKS[6][(7 + RAND) % 8][1]);

        // r^2 = 13
        if (!visited(cx + CHUNKS[7][(RAND) % 8][0], cy + CHUNKS[7][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(RAND) % 8][0], 4 * CHUNKS[7][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(1 + RAND) % 8][0], cy + CHUNKS[7][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(1 + RAND) % 8][0], 4 * CHUNKS[7][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(2 + RAND) % 8][0], cy + CHUNKS[7][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(2 + RAND) % 8][0], 4 * CHUNKS[7][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(3 + RAND) % 8][0], cy + CHUNKS[7][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(3 + RAND) % 8][0], 4 * CHUNKS[7][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(4 + RAND) % 8][0], cy + CHUNKS[7][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(4 + RAND) % 8][0], 4 * CHUNKS[7][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(5 + RAND) % 8][0], cy + CHUNKS[7][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(5 + RAND) % 8][0], 4 * CHUNKS[7][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(6 + RAND) % 8][0], cy + CHUNKS[7][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(6 + RAND) % 8][0], 4 * CHUNKS[7][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[7][(7 + RAND) % 8][0], cy + CHUNKS[7][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[7][(7 + RAND) % 8][0], 4 * CHUNKS[7][(7 + RAND) % 8][1]);

        // r^2 = 16
        if (!visited(cx + CHUNKS[8][(RAND) % 4][0], cy + CHUNKS[8][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[8][(RAND) % 4][0], 4 * CHUNKS[8][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[8][(1 + RAND) % 4][0], cy + CHUNKS[8][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[8][(1 + RAND) % 4][0], 4 * CHUNKS[8][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[8][(2 + RAND) % 4][0], cy + CHUNKS[8][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[8][(2 + RAND) % 4][0], 4 * CHUNKS[8][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[8][(3 + RAND) % 4][0], cy + CHUNKS[8][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[8][(3 + RAND) % 4][0], 4 * CHUNKS[8][(3 + RAND) % 4][1]);

        // r^2 = 17
        if (!visited(cx + CHUNKS[9][(RAND) % 8][0], cy + CHUNKS[9][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(RAND) % 8][0], 4 * CHUNKS[9][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(1 + RAND) % 8][0], cy + CHUNKS[9][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(1 + RAND) % 8][0], 4 * CHUNKS[9][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(2 + RAND) % 8][0], cy + CHUNKS[9][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(2 + RAND) % 8][0], 4 * CHUNKS[9][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(3 + RAND) % 8][0], cy + CHUNKS[9][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(3 + RAND) % 8][0], 4 * CHUNKS[9][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(4 + RAND) % 8][0], cy + CHUNKS[9][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(4 + RAND) % 8][0], 4 * CHUNKS[9][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(5 + RAND) % 8][0], cy + CHUNKS[9][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(5 + RAND) % 8][0], 4 * CHUNKS[9][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(6 + RAND) % 8][0], cy + CHUNKS[9][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(6 + RAND) % 8][0], 4 * CHUNKS[9][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[9][(7 + RAND) % 8][0], cy + CHUNKS[9][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[9][(7 + RAND) % 8][0], 4 * CHUNKS[9][(7 + RAND) % 8][1]);

        // r^2 = 18
        if (!visited(cx + CHUNKS[10][(RAND) % 4][0], cy + CHUNKS[10][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[10][(RAND) % 4][0], 4 * CHUNKS[10][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[10][(1 + RAND) % 4][0], cy + CHUNKS[10][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[10][(1 + RAND) % 4][0], 4 * CHUNKS[10][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[10][(2 + RAND) % 4][0], cy + CHUNKS[10][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[10][(2 + RAND) % 4][0], 4 * CHUNKS[10][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[10][(3 + RAND) % 4][0], cy + CHUNKS[10][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[10][(3 + RAND) % 4][0], 4 * CHUNKS[10][(3 + RAND) % 4][1]);

        // r^2 = 20
        if (!visited(cx + CHUNKS[11][(RAND) % 8][0], cy + CHUNKS[11][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(RAND) % 8][0], 4 * CHUNKS[11][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(1 + RAND) % 8][0], cy + CHUNKS[11][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(1 + RAND) % 8][0], 4 * CHUNKS[11][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(2 + RAND) % 8][0], cy + CHUNKS[11][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(2 + RAND) % 8][0], 4 * CHUNKS[11][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(3 + RAND) % 8][0], cy + CHUNKS[11][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(3 + RAND) % 8][0], 4 * CHUNKS[11][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(4 + RAND) % 8][0], cy + CHUNKS[11][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(4 + RAND) % 8][0], 4 * CHUNKS[11][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(5 + RAND) % 8][0], cy + CHUNKS[11][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(5 + RAND) % 8][0], 4 * CHUNKS[11][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(6 + RAND) % 8][0], cy + CHUNKS[11][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(6 + RAND) % 8][0], 4 * CHUNKS[11][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[11][(7 + RAND) % 8][0], cy + CHUNKS[11][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[11][(7 + RAND) % 8][0], 4 * CHUNKS[11][(7 + RAND) % 8][1]);

        // r^2 = 25
        if (!visited(cx + CHUNKS[12][(RAND) % 12][0], cy + CHUNKS[12][(RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(RAND) % 12][0], 4 * CHUNKS[12][(RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(1 + RAND) % 12][0], cy + CHUNKS[12][(1 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(1 + RAND) % 12][0], 4 * CHUNKS[12][(1 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(2 + RAND) % 12][0], cy + CHUNKS[12][(2 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(2 + RAND) % 12][0], 4 * CHUNKS[12][(2 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(3 + RAND) % 12][0], cy + CHUNKS[12][(3 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(3 + RAND) % 12][0], 4 * CHUNKS[12][(3 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(4 + RAND) % 12][0], cy + CHUNKS[12][(4 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(4 + RAND) % 12][0], 4 * CHUNKS[12][(4 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(5 + RAND) % 12][0], cy + CHUNKS[12][(5 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(5 + RAND) % 12][0], 4 * CHUNKS[12][(5 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(6 + RAND) % 12][0], cy + CHUNKS[12][(6 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(6 + RAND) % 12][0], 4 * CHUNKS[12][(6 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(7 + RAND) % 12][0], cy + CHUNKS[12][(7 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(7 + RAND) % 12][0], 4 * CHUNKS[12][(7 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(8 + RAND) % 12][0], cy + CHUNKS[12][(8 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(8 + RAND) % 12][0], 4 * CHUNKS[12][(8 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(9 + RAND) % 12][0], cy + CHUNKS[12][(9 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(9 + RAND) % 12][0], 4 * CHUNKS[12][(9 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(10 + RAND) % 12][0], cy + CHUNKS[12][(10 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(10 + RAND) % 12][0], 4 * CHUNKS[12][(10 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[12][(11 + RAND) % 12][0], cy + CHUNKS[12][(11 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[12][(11 + RAND) % 12][0], 4 * CHUNKS[12][(11 + RAND) % 12][1]);

        // r^2 = 26
        if (!visited(cx + CHUNKS[13][(RAND) % 8][0], cy + CHUNKS[13][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(RAND) % 8][0], 4 * CHUNKS[13][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(1 + RAND) % 8][0], cy + CHUNKS[13][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(1 + RAND) % 8][0], 4 * CHUNKS[13][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(2 + RAND) % 8][0], cy + CHUNKS[13][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(2 + RAND) % 8][0], 4 * CHUNKS[13][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(3 + RAND) % 8][0], cy + CHUNKS[13][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(3 + RAND) % 8][0], 4 * CHUNKS[13][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(4 + RAND) % 8][0], cy + CHUNKS[13][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(4 + RAND) % 8][0], 4 * CHUNKS[13][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(5 + RAND) % 8][0], cy + CHUNKS[13][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(5 + RAND) % 8][0], 4 * CHUNKS[13][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(6 + RAND) % 8][0], cy + CHUNKS[13][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(6 + RAND) % 8][0], 4 * CHUNKS[13][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[13][(7 + RAND) % 8][0], cy + CHUNKS[13][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[13][(7 + RAND) % 8][0], 4 * CHUNKS[13][(7 + RAND) % 8][1]);

        // r^2 = 29
        if (!visited(cx + CHUNKS[14][(RAND) % 8][0], cy + CHUNKS[14][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(RAND) % 8][0], 4 * CHUNKS[14][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(1 + RAND) % 8][0], cy + CHUNKS[14][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(1 + RAND) % 8][0], 4 * CHUNKS[14][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(2 + RAND) % 8][0], cy + CHUNKS[14][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(2 + RAND) % 8][0], 4 * CHUNKS[14][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(3 + RAND) % 8][0], cy + CHUNKS[14][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(3 + RAND) % 8][0], 4 * CHUNKS[14][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(4 + RAND) % 8][0], cy + CHUNKS[14][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(4 + RAND) % 8][0], 4 * CHUNKS[14][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(5 + RAND) % 8][0], cy + CHUNKS[14][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(5 + RAND) % 8][0], 4 * CHUNKS[14][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(6 + RAND) % 8][0], cy + CHUNKS[14][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(6 + RAND) % 8][0], 4 * CHUNKS[14][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[14][(7 + RAND) % 8][0], cy + CHUNKS[14][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[14][(7 + RAND) % 8][0], 4 * CHUNKS[14][(7 + RAND) % 8][1]);

        // r^2 = 32
        if (!visited(cx + CHUNKS[15][(RAND) % 4][0], cy + CHUNKS[15][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[15][(RAND) % 4][0], 4 * CHUNKS[15][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[15][(1 + RAND) % 4][0], cy + CHUNKS[15][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[15][(1 + RAND) % 4][0], 4 * CHUNKS[15][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[15][(2 + RAND) % 4][0], cy + CHUNKS[15][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[15][(2 + RAND) % 4][0], 4 * CHUNKS[15][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[15][(3 + RAND) % 4][0], cy + CHUNKS[15][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[15][(3 + RAND) % 4][0], 4 * CHUNKS[15][(3 + RAND) % 4][1]);

        // r^2 = 34
        if (!visited(cx + CHUNKS[16][(RAND) % 8][0], cy + CHUNKS[16][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(RAND) % 8][0], 4 * CHUNKS[16][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(1 + RAND) % 8][0], cy + CHUNKS[16][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(1 + RAND) % 8][0], 4 * CHUNKS[16][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(2 + RAND) % 8][0], cy + CHUNKS[16][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(2 + RAND) % 8][0], 4 * CHUNKS[16][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(3 + RAND) % 8][0], cy + CHUNKS[16][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(3 + RAND) % 8][0], 4 * CHUNKS[16][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(4 + RAND) % 8][0], cy + CHUNKS[16][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(4 + RAND) % 8][0], 4 * CHUNKS[16][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(5 + RAND) % 8][0], cy + CHUNKS[16][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(5 + RAND) % 8][0], 4 * CHUNKS[16][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(6 + RAND) % 8][0], cy + CHUNKS[16][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(6 + RAND) % 8][0], 4 * CHUNKS[16][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[16][(7 + RAND) % 8][0], cy + CHUNKS[16][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[16][(7 + RAND) % 8][0], 4 * CHUNKS[16][(7 + RAND) % 8][1]);

        // r^2 = 36
        if (!visited(cx + CHUNKS[17][(RAND) % 4][0], cy + CHUNKS[17][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[17][(RAND) % 4][0], 4 * CHUNKS[17][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[17][(1 + RAND) % 4][0], cy + CHUNKS[17][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[17][(1 + RAND) % 4][0], 4 * CHUNKS[17][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[17][(2 + RAND) % 4][0], cy + CHUNKS[17][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[17][(2 + RAND) % 4][0], 4 * CHUNKS[17][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[17][(3 + RAND) % 4][0], cy + CHUNKS[17][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[17][(3 + RAND) % 4][0], 4 * CHUNKS[17][(3 + RAND) % 4][1]);

        // r^2 = 37
        if (!visited(cx + CHUNKS[18][(RAND) % 8][0], cy + CHUNKS[18][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(RAND) % 8][0], 4 * CHUNKS[18][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(1 + RAND) % 8][0], cy + CHUNKS[18][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(1 + RAND) % 8][0], 4 * CHUNKS[18][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(2 + RAND) % 8][0], cy + CHUNKS[18][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(2 + RAND) % 8][0], 4 * CHUNKS[18][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(3 + RAND) % 8][0], cy + CHUNKS[18][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(3 + RAND) % 8][0], 4 * CHUNKS[18][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(4 + RAND) % 8][0], cy + CHUNKS[18][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(4 + RAND) % 8][0], 4 * CHUNKS[18][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(5 + RAND) % 8][0], cy + CHUNKS[18][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(5 + RAND) % 8][0], 4 * CHUNKS[18][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(6 + RAND) % 8][0], cy + CHUNKS[18][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(6 + RAND) % 8][0], 4 * CHUNKS[18][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[18][(7 + RAND) % 8][0], cy + CHUNKS[18][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[18][(7 + RAND) % 8][0], 4 * CHUNKS[18][(7 + RAND) % 8][1]);

        // r^2 = 40
        if (!visited(cx + CHUNKS[19][(RAND) % 8][0], cy + CHUNKS[19][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(RAND) % 8][0], 4 * CHUNKS[19][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(1 + RAND) % 8][0], cy + CHUNKS[19][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(1 + RAND) % 8][0], 4 * CHUNKS[19][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(2 + RAND) % 8][0], cy + CHUNKS[19][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(2 + RAND) % 8][0], 4 * CHUNKS[19][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(3 + RAND) % 8][0], cy + CHUNKS[19][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(3 + RAND) % 8][0], 4 * CHUNKS[19][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(4 + RAND) % 8][0], cy + CHUNKS[19][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(4 + RAND) % 8][0], 4 * CHUNKS[19][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(5 + RAND) % 8][0], cy + CHUNKS[19][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(5 + RAND) % 8][0], 4 * CHUNKS[19][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(6 + RAND) % 8][0], cy + CHUNKS[19][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(6 + RAND) % 8][0], 4 * CHUNKS[19][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[19][(7 + RAND) % 8][0], cy + CHUNKS[19][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[19][(7 + RAND) % 8][0], 4 * CHUNKS[19][(7 + RAND) % 8][1]);

        // r^2 = 41
        if (!visited(cx + CHUNKS[20][(RAND) % 8][0], cy + CHUNKS[20][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(RAND) % 8][0], 4 * CHUNKS[20][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(1 + RAND) % 8][0], cy + CHUNKS[20][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(1 + RAND) % 8][0], 4 * CHUNKS[20][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(2 + RAND) % 8][0], cy + CHUNKS[20][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(2 + RAND) % 8][0], 4 * CHUNKS[20][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(3 + RAND) % 8][0], cy + CHUNKS[20][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(3 + RAND) % 8][0], 4 * CHUNKS[20][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(4 + RAND) % 8][0], cy + CHUNKS[20][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(4 + RAND) % 8][0], 4 * CHUNKS[20][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(5 + RAND) % 8][0], cy + CHUNKS[20][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(5 + RAND) % 8][0], 4 * CHUNKS[20][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(6 + RAND) % 8][0], cy + CHUNKS[20][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(6 + RAND) % 8][0], 4 * CHUNKS[20][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[20][(7 + RAND) % 8][0], cy + CHUNKS[20][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[20][(7 + RAND) % 8][0], 4 * CHUNKS[20][(7 + RAND) % 8][1]);

        // r^2 = 45
        if (!visited(cx + CHUNKS[21][(RAND) % 8][0], cy + CHUNKS[21][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(RAND) % 8][0], 4 * CHUNKS[21][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(1 + RAND) % 8][0], cy + CHUNKS[21][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(1 + RAND) % 8][0], 4 * CHUNKS[21][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(2 + RAND) % 8][0], cy + CHUNKS[21][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(2 + RAND) % 8][0], 4 * CHUNKS[21][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(3 + RAND) % 8][0], cy + CHUNKS[21][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(3 + RAND) % 8][0], 4 * CHUNKS[21][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(4 + RAND) % 8][0], cy + CHUNKS[21][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(4 + RAND) % 8][0], 4 * CHUNKS[21][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(5 + RAND) % 8][0], cy + CHUNKS[21][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(5 + RAND) % 8][0], 4 * CHUNKS[21][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(6 + RAND) % 8][0], cy + CHUNKS[21][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(6 + RAND) % 8][0], 4 * CHUNKS[21][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[21][(7 + RAND) % 8][0], cy + CHUNKS[21][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[21][(7 + RAND) % 8][0], 4 * CHUNKS[21][(7 + RAND) % 8][1]);

        // r^2 = 49
        if (!visited(cx + CHUNKS[22][(RAND) % 4][0], cy + CHUNKS[22][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[22][(RAND) % 4][0], 4 * CHUNKS[22][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[22][(1 + RAND) % 4][0], cy + CHUNKS[22][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[22][(1 + RAND) % 4][0], 4 * CHUNKS[22][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[22][(2 + RAND) % 4][0], cy + CHUNKS[22][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[22][(2 + RAND) % 4][0], 4 * CHUNKS[22][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[22][(3 + RAND) % 4][0], cy + CHUNKS[22][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[22][(3 + RAND) % 4][0], 4 * CHUNKS[22][(3 + RAND) % 4][1]);

        // r^2 = 50
        if (!visited(cx + CHUNKS[23][(RAND) % 12][0], cy + CHUNKS[23][(RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(RAND) % 12][0], 4 * CHUNKS[23][(RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(1 + RAND) % 12][0], cy + CHUNKS[23][(1 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(1 + RAND) % 12][0], 4 * CHUNKS[23][(1 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(2 + RAND) % 12][0], cy + CHUNKS[23][(2 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(2 + RAND) % 12][0], 4 * CHUNKS[23][(2 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(3 + RAND) % 12][0], cy + CHUNKS[23][(3 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(3 + RAND) % 12][0], 4 * CHUNKS[23][(3 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(4 + RAND) % 12][0], cy + CHUNKS[23][(4 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(4 + RAND) % 12][0], 4 * CHUNKS[23][(4 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(5 + RAND) % 12][0], cy + CHUNKS[23][(5 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(5 + RAND) % 12][0], 4 * CHUNKS[23][(5 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(6 + RAND) % 12][0], cy + CHUNKS[23][(6 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(6 + RAND) % 12][0], 4 * CHUNKS[23][(6 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(7 + RAND) % 12][0], cy + CHUNKS[23][(7 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(7 + RAND) % 12][0], 4 * CHUNKS[23][(7 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(8 + RAND) % 12][0], cy + CHUNKS[23][(8 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(8 + RAND) % 12][0], 4 * CHUNKS[23][(8 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(9 + RAND) % 12][0], cy + CHUNKS[23][(9 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(9 + RAND) % 12][0], 4 * CHUNKS[23][(9 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(10 + RAND) % 12][0], cy + CHUNKS[23][(10 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(10 + RAND) % 12][0], 4 * CHUNKS[23][(10 + RAND) % 12][1]);
        if (!visited(cx + CHUNKS[23][(11 + RAND) % 12][0], cy + CHUNKS[23][(11 + RAND) % 12][1]))
            return m.translate(4 * CHUNKS[23][(11 + RAND) % 12][0], 4 * CHUNKS[23][(11 + RAND) % 12][1]);

        // r^2 = 52
        if (!visited(cx + CHUNKS[24][(RAND) % 8][0], cy + CHUNKS[24][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(RAND) % 8][0], 4 * CHUNKS[24][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(1 + RAND) % 8][0], cy + CHUNKS[24][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(1 + RAND) % 8][0], 4 * CHUNKS[24][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(2 + RAND) % 8][0], cy + CHUNKS[24][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(2 + RAND) % 8][0], 4 * CHUNKS[24][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(3 + RAND) % 8][0], cy + CHUNKS[24][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(3 + RAND) % 8][0], 4 * CHUNKS[24][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(4 + RAND) % 8][0], cy + CHUNKS[24][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(4 + RAND) % 8][0], 4 * CHUNKS[24][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(5 + RAND) % 8][0], cy + CHUNKS[24][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(5 + RAND) % 8][0], 4 * CHUNKS[24][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(6 + RAND) % 8][0], cy + CHUNKS[24][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(6 + RAND) % 8][0], 4 * CHUNKS[24][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[24][(7 + RAND) % 8][0], cy + CHUNKS[24][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[24][(7 + RAND) % 8][0], 4 * CHUNKS[24][(7 + RAND) % 8][1]);

        // r^2 = 53
        if (!visited(cx + CHUNKS[25][(RAND) % 8][0], cy + CHUNKS[25][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(RAND) % 8][0], 4 * CHUNKS[25][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(1 + RAND) % 8][0], cy + CHUNKS[25][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(1 + RAND) % 8][0], 4 * CHUNKS[25][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(2 + RAND) % 8][0], cy + CHUNKS[25][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(2 + RAND) % 8][0], 4 * CHUNKS[25][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(3 + RAND) % 8][0], cy + CHUNKS[25][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(3 + RAND) % 8][0], 4 * CHUNKS[25][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(4 + RAND) % 8][0], cy + CHUNKS[25][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(4 + RAND) % 8][0], 4 * CHUNKS[25][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(5 + RAND) % 8][0], cy + CHUNKS[25][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(5 + RAND) % 8][0], 4 * CHUNKS[25][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(6 + RAND) % 8][0], cy + CHUNKS[25][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(6 + RAND) % 8][0], 4 * CHUNKS[25][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[25][(7 + RAND) % 8][0], cy + CHUNKS[25][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[25][(7 + RAND) % 8][0], 4 * CHUNKS[25][(7 + RAND) % 8][1]);

        // r^2 = 58
        if (!visited(cx + CHUNKS[26][(RAND) % 8][0], cy + CHUNKS[26][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(RAND) % 8][0], 4 * CHUNKS[26][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(1 + RAND) % 8][0], cy + CHUNKS[26][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(1 + RAND) % 8][0], 4 * CHUNKS[26][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(2 + RAND) % 8][0], cy + CHUNKS[26][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(2 + RAND) % 8][0], 4 * CHUNKS[26][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(3 + RAND) % 8][0], cy + CHUNKS[26][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(3 + RAND) % 8][0], 4 * CHUNKS[26][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(4 + RAND) % 8][0], cy + CHUNKS[26][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(4 + RAND) % 8][0], 4 * CHUNKS[26][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(5 + RAND) % 8][0], cy + CHUNKS[26][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(5 + RAND) % 8][0], 4 * CHUNKS[26][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(6 + RAND) % 8][0], cy + CHUNKS[26][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(6 + RAND) % 8][0], 4 * CHUNKS[26][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[26][(7 + RAND) % 8][0], cy + CHUNKS[26][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[26][(7 + RAND) % 8][0], 4 * CHUNKS[26][(7 + RAND) % 8][1]);

        // r^2 = 61
        if (!visited(cx + CHUNKS[27][(RAND) % 8][0], cy + CHUNKS[27][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(RAND) % 8][0], 4 * CHUNKS[27][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(1 + RAND) % 8][0], cy + CHUNKS[27][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(1 + RAND) % 8][0], 4 * CHUNKS[27][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(2 + RAND) % 8][0], cy + CHUNKS[27][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(2 + RAND) % 8][0], 4 * CHUNKS[27][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(3 + RAND) % 8][0], cy + CHUNKS[27][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(3 + RAND) % 8][0], 4 * CHUNKS[27][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(4 + RAND) % 8][0], cy + CHUNKS[27][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(4 + RAND) % 8][0], 4 * CHUNKS[27][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(5 + RAND) % 8][0], cy + CHUNKS[27][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(5 + RAND) % 8][0], 4 * CHUNKS[27][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(6 + RAND) % 8][0], cy + CHUNKS[27][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(6 + RAND) % 8][0], 4 * CHUNKS[27][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[27][(7 + RAND) % 8][0], cy + CHUNKS[27][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[27][(7 + RAND) % 8][0], 4 * CHUNKS[27][(7 + RAND) % 8][1]);

        // r^2 = 65
        if (!visited(cx + CHUNKS[28][(RAND) % 8][0], cy + CHUNKS[28][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(RAND) % 8][0], 4 * CHUNKS[28][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(1 + RAND) % 8][0], cy + CHUNKS[28][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(1 + RAND) % 8][0], 4 * CHUNKS[28][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(2 + RAND) % 8][0], cy + CHUNKS[28][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(2 + RAND) % 8][0], 4 * CHUNKS[28][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(3 + RAND) % 8][0], cy + CHUNKS[28][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(3 + RAND) % 8][0], 4 * CHUNKS[28][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(4 + RAND) % 8][0], cy + CHUNKS[28][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(4 + RAND) % 8][0], 4 * CHUNKS[28][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(5 + RAND) % 8][0], cy + CHUNKS[28][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(5 + RAND) % 8][0], 4 * CHUNKS[28][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(6 + RAND) % 8][0], cy + CHUNKS[28][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(6 + RAND) % 8][0], 4 * CHUNKS[28][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[28][(7 + RAND) % 8][0], cy + CHUNKS[28][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[28][(7 + RAND) % 8][0], 4 * CHUNKS[28][(7 + RAND) % 8][1]);

        // r^2 = 72
        if (!visited(cx + CHUNKS[29][(RAND) % 4][0], cy + CHUNKS[29][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[29][(RAND) % 4][0], 4 * CHUNKS[29][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[29][(1 + RAND) % 4][0], cy + CHUNKS[29][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[29][(1 + RAND) % 4][0], 4 * CHUNKS[29][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[29][(2 + RAND) % 4][0], cy + CHUNKS[29][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[29][(2 + RAND) % 4][0], 4 * CHUNKS[29][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[29][(3 + RAND) % 4][0], cy + CHUNKS[29][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[29][(3 + RAND) % 4][0], 4 * CHUNKS[29][(3 + RAND) % 4][1]);

        // r^2 = 74
        if (!visited(cx + CHUNKS[30][(RAND) % 8][0], cy + CHUNKS[30][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(RAND) % 8][0], 4 * CHUNKS[30][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(1 + RAND) % 8][0], cy + CHUNKS[30][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(1 + RAND) % 8][0], 4 * CHUNKS[30][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(2 + RAND) % 8][0], cy + CHUNKS[30][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(2 + RAND) % 8][0], 4 * CHUNKS[30][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(3 + RAND) % 8][0], cy + CHUNKS[30][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(3 + RAND) % 8][0], 4 * CHUNKS[30][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(4 + RAND) % 8][0], cy + CHUNKS[30][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(4 + RAND) % 8][0], 4 * CHUNKS[30][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(5 + RAND) % 8][0], cy + CHUNKS[30][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(5 + RAND) % 8][0], 4 * CHUNKS[30][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(6 + RAND) % 8][0], cy + CHUNKS[30][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(6 + RAND) % 8][0], 4 * CHUNKS[30][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[30][(7 + RAND) % 8][0], cy + CHUNKS[30][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[30][(7 + RAND) % 8][0], 4 * CHUNKS[30][(7 + RAND) % 8][1]);

        // r^2 = 85
        if (!visited(cx + CHUNKS[31][(RAND) % 8][0], cy + CHUNKS[31][(RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(RAND) % 8][0], 4 * CHUNKS[31][(RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(1 + RAND) % 8][0], cy + CHUNKS[31][(1 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(1 + RAND) % 8][0], 4 * CHUNKS[31][(1 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(2 + RAND) % 8][0], cy + CHUNKS[31][(2 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(2 + RAND) % 8][0], 4 * CHUNKS[31][(2 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(3 + RAND) % 8][0], cy + CHUNKS[31][(3 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(3 + RAND) % 8][0], 4 * CHUNKS[31][(3 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(4 + RAND) % 8][0], cy + CHUNKS[31][(4 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(4 + RAND) % 8][0], 4 * CHUNKS[31][(4 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(5 + RAND) % 8][0], cy + CHUNKS[31][(5 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(5 + RAND) % 8][0], 4 * CHUNKS[31][(5 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(6 + RAND) % 8][0], cy + CHUNKS[31][(6 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(6 + RAND) % 8][0], 4 * CHUNKS[31][(6 + RAND) % 8][1]);
        if (!visited(cx + CHUNKS[31][(7 + RAND) % 8][0], cy + CHUNKS[31][(7 + RAND) % 8][1]))
            return m.translate(4 * CHUNKS[31][(7 + RAND) % 8][0], 4 * CHUNKS[31][(7 + RAND) % 8][1]);

        // r^2 = 98
        if (!visited(cx + CHUNKS[32][(RAND) % 4][0], cy + CHUNKS[32][(RAND) % 4][1]))
            return m.translate(4 * CHUNKS[32][(RAND) % 4][0], 4 * CHUNKS[32][(RAND) % 4][1]);
        if (!visited(cx + CHUNKS[32][(1 + RAND) % 4][0], cy + CHUNKS[32][(1 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[32][(1 + RAND) % 4][0], 4 * CHUNKS[32][(1 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[32][(2 + RAND) % 4][0], cy + CHUNKS[32][(2 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[32][(2 + RAND) % 4][0], 4 * CHUNKS[32][(2 + RAND) % 4][1]);
        if (!visited(cx + CHUNKS[32][(3 + RAND) % 4][0], cy + CHUNKS[32][(3 + RAND) % 4][1]))
            return m.translate(4 * CHUNKS[32][(3 + RAND) % 4][0], 4 * CHUNKS[32][(3 + RAND) % 4][1]);

        return null;
    }

    private static final int[][][] CHUNKS = {{{-1, 0}, {0, -1}, {0, 1}, {1, 0}}, {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}},
            {{-2, 0}, {0, -2}, {0, 2}, {2, 0}}, {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1},
            {2, 1}}, {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}, {{-3, 0}, {0, -3}, {0, 3}, {3, 0}}, {{-3, -1}, {-3, 1},
            {-1, -3}, {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}}, {{-3, -2}, {-3, 2}, {-2, -3}, {-2, 3}, {2, -3},
            {2, 3}, {3, -2}, {3, 2}}, {{-4, 0}, {0, -4}, {0, 4}, {4, 0}}, {{-4, -1}, {-4, 1}, {-1, -4}, {-1, 4},
            {1, -4}, {1, 4}, {4, -1}, {4, 1}}, {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}, {{-4, -2}, {-4, 2}, {-2, -4},
            {-2, 4}, {2, -4}, {2, 4}, {4, -2}, {4, 2}}, {{-5, 0}, {-4, -3}, {-4, 3}, {-3, -4}, {-3, 4}, {0, -5}, {0, 5},
            {3, -4}, {3, 4}, {4, -3}, {4, 3}, {5, 0}}, {{-5, -1}, {-5, 1}, {-1, -5}, {-1, 5}, {1, -5}, {1, 5}, {5, -1},
            {5, 1}}, {{-5, -2}, {-5, 2}, {-2, -5}, {-2, 5}, {2, -5}, {2, 5}, {5, -2}, {5, 2}}, {{-4, -4}, {-4, 4},
            {4, -4}, {4, 4}}, {{-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}, {5, -3}, {5, 3}}, {{-6, 0},
            {0, -6}, {0, 6}, {6, 0}}, {{-6, -1}, {-6, 1}, {-1, -6}, {-1, 6}, {1, -6}, {1, 6}, {6, -1}, {6, 1}},
            {{-6, -2}, {-6, 2}, {-2, -6}, {-2, 6}, {2, -6}, {2, 6}, {6, -2}, {6, 2}}, {{-5, -4}, {-5, 4}, {-4, -5},
            {-4, 5}, {4, -5}, {4, 5}, {5, -4}, {5, 4}}, {{-6, -3}, {-6, 3}, {-3, -6}, {-3, 6}, {3, -6}, {3, 6}, {6, -3},
            {6, 3}}, {{-7, 0}, {0, -7}, {0, 7}, {7, 0}}, {{-7, -1}, {-7, 1}, {-5, -5}, {-5, 5}, {-1, -7}, {-1, 7},
            {1, -7}, {1, 7}, {5, -5}, {5, 5}, {7, -1}, {7, 1}}, {{-6, -4}, {-6, 4}, {-4, -6}, {-4, 6}, {4, -6}, {4, 6},
            {6, -4}, {6, 4}}, {{-7, -2}, {-7, 2}, {-2, -7}, {-2, 7}, {2, -7}, {2, 7}, {7, -2}, {7, 2}}, {{-7, -3},
            {-7, 3}, {-3, -7}, {-3, 7}, {3, -7}, {3, 7}, {7, -3}, {7, 3}}, {{-6, -5}, {-6, 5}, {-5, -6}, {-5, 6},
            {5, -6}, {5, 6}, {6, -5}, {6, 5}}, {{-7, -4}, {-7, 4}, {-4, -7}, {-4, 7}, {4, -7}, {4, 7}, {7, -4}, {7, 4}},
            {{-6, -6}, {-6, 6}, {6, -6}, {6, 6}}, {{-7, -5}, {-7, 5}, {-5, -7}, {-5, 7}, {5, -7}, {5, 7}, {7, -5},
            {7, 5}}, {{-7, -6}, {-7, 6}, {-6, -7}, {-6, 7}, {6, -7}, {6, 7}, {7, -6}, {7, 6}}, {{-7, -7}, {-7, 7},
            {7, -7}, {7, 7}}};
}