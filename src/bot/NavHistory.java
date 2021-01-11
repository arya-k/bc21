package bot;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

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
     * Update the known locations of edges, and the visited chunks. Call on every tick.
     */
    public static void update() throws GameActionException {
        MapLocation m = Robot.rc.getLocation();

        // todo: maybe avoid extra checks with some fancy floors and ceilings?
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
     * Inits the random var and the sight range.
     */
    public static void init() {
        SIGHT_RANGE = (int) Math.floor(Math.sqrt(Robot.rc.getType().sensorRadiusSquared));
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

    // TODO: this could be unrolled more
    public static MapLocation nearestUnexploredLocation() {
        int RAND = (int) (Math.random() * 1024);

        MapLocation m = Robot.rc.getLocation();
        int cx = ((m.x - Robot.initLoc.x) / 4) + 15;
        int cy = ((m.y - Robot.initLoc.y) / 4) + 15;
        for (int i = 0; i < 4; i++) { // r^2 = 1
            int x = CHUNKS[0][(i + RAND) % 4][0];
            int y = CHUNKS[0][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 2
            int x = CHUNKS[1][(i + RAND) % 4][0];
            int y = CHUNKS[1][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 4
            int x = CHUNKS[2][(i + RAND) % 4][0];
            int y = CHUNKS[2][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 5
            int x = CHUNKS[3][(i + RAND) % 8][0];
            int y = CHUNKS[3][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 8
            int x = CHUNKS[4][(i + RAND) % 4][0];
            int y = CHUNKS[4][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 9
            int x = CHUNKS[5][(i + RAND) % 4][0];
            int y = CHUNKS[5][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 10
            int x = CHUNKS[6][(i + RAND) % 8][0];
            int y = CHUNKS[6][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 13
            int x = CHUNKS[7][(i + RAND) % 8][0];
            int y = CHUNKS[7][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 16
            int x = CHUNKS[8][(i + RAND) % 4][0];
            int y = CHUNKS[8][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 17
            int x = CHUNKS[9][(i + RAND) % 8][0];
            int y = CHUNKS[9][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 18
            int x = CHUNKS[10][(i + RAND) % 4][0];
            int y = CHUNKS[10][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 20
            int x = CHUNKS[11][(i + RAND) % 8][0];
            int y = CHUNKS[11][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 12; i++) { // r^2 = 25
            int x = CHUNKS[12][(i + RAND) % 12][0];
            int y = CHUNKS[12][(i + RAND) % 12][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 26
            int x = CHUNKS[13][(i + RAND) % 8][0];
            int y = CHUNKS[13][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 29
            int x = CHUNKS[14][(i + RAND) % 8][0];
            int y = CHUNKS[14][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 32
            int x = CHUNKS[15][(i + RAND) % 4][0];
            int y = CHUNKS[15][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 34
            int x = CHUNKS[16][(i + RAND) % 8][0];
            int y = CHUNKS[16][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 36
            int x = CHUNKS[17][(i + RAND) % 4][0];
            int y = CHUNKS[17][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 37
            int x = CHUNKS[18][(i + RAND) % 8][0];
            int y = CHUNKS[18][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 40
            int x = CHUNKS[19][(i + RAND) % 8][0];
            int y = CHUNKS[19][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 41
            int x = CHUNKS[20][(i + RAND) % 8][0];
            int y = CHUNKS[20][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 45
            int x = CHUNKS[21][(i + RAND) % 8][0];
            int y = CHUNKS[21][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 49
            int x = CHUNKS[22][(i + RAND) % 4][0];
            int y = CHUNKS[22][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 12; i++) { // r^2 = 50
            int x = CHUNKS[23][(i + RAND) % 12][0];
            int y = CHUNKS[23][(i + RAND) % 12][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 52
            int x = CHUNKS[24][(i + RAND) % 8][0];
            int y = CHUNKS[24][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 53
            int x = CHUNKS[25][(i + RAND) % 8][0];
            int y = CHUNKS[25][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 58
            int x = CHUNKS[26][(i + RAND) % 8][0];
            int y = CHUNKS[26][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 61
            int x = CHUNKS[27][(i + RAND) % 8][0];
            int y = CHUNKS[27][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 65
            int x = CHUNKS[28][(i + RAND) % 8][0];
            int y = CHUNKS[28][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 72
            int x = CHUNKS[29][(i + RAND) % 4][0];
            int y = CHUNKS[29][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 74
            int x = CHUNKS[30][(i + RAND) % 8][0];
            int y = CHUNKS[30][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 8; i++) { // r^2 = 85
            int x = CHUNKS[31][(i + RAND) % 8][0];
            int y = CHUNKS[31][(i + RAND) % 8][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        for (int i = 0; i < 4; i++) { // r^2 = 98
            int x = CHUNKS[32][(i + RAND) % 4][0];
            int y = CHUNKS[32][(i + RAND) % 4][1];
            if (!visited(cx + x, cy + y)) return m.translate(4 * x, 4 * y);
        }
        return null;
    }

    private static final int[][][] CHUNKS = {{{-1, 0}, {0, -1}, {0, 1}, {1, 0}}, {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}},
            {{-2, 0}, {0, -2}, {0, 2}, {2, 0}}, {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}},
            {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}, {{-3, 0}, {0, -3}, {0, 3}, {3, 0}}, {{-3, -1}, {-3, 1}, {-1, -3},
            {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}}, {{-3, -2}, {-3, 2}, {-2, -3}, {-2, 3}, {2, -3}, {2, 3},
            {3, -2}, {3, 2}}, {{-4, 0}, {0, -4}, {0, 4}, {4, 0}}, {{-4, -1}, {-4, 1}, {-1, -4}, {-1, 4}, {1, -4},
            {1, 4}, {4, -1}, {4, 1}}, {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}, {{-4, -2}, {-4, 2}, {-2, -4}, {-2, 4},
            {2, -4}, {2, 4}, {4, -2}, {4, 2}}, {{-5, 0}, {-4, -3}, {-4, 3}, {-3, -4}, {-3, 4}, {0, -5}, {0, 5}, {3, -4},
            {3, 4}, {4, -3}, {4, 3}, {5, 0}}, {{-5, -1}, {-5, 1}, {-1, -5}, {-1, 5}, {1, -5}, {1, 5}, {5, -1}, {5, 1}},
            {{-5, -2}, {-5, 2}, {-2, -5}, {-2, 5}, {2, -5}, {2, 5}, {5, -2}, {5, 2}}, {{-4, -4}, {-4, 4}, {4, -4},
            {4, 4}}, {{-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}, {5, -3}, {5, 3}}, {{-6, 0}, {0, -6},
            {0, 6}, {6, 0}}, {{-6, -1}, {-6, 1}, {-1, -6}, {-1, 6}, {1, -6}, {1, 6}, {6, -1}, {6, 1}}, {{-6, -2},
            {-6, 2}, {-2, -6}, {-2, 6}, {2, -6}, {2, 6}, {6, -2}, {6, 2}}, {{-5, -4}, {-5, 4}, {-4, -5}, {-4, 5},
            {4, -5}, {4, 5}, {5, -4}, {5, 4}}, {{-6, -3}, {-6, 3}, {-3, -6}, {-3, 6}, {3, -6}, {3, 6}, {6, -3},
            {6, 3}}, {{-7, 0}, {0, -7}, {0, 7}, {7, 0}}, {{-7, -1}, {-7, 1}, {-5, -5}, {-5, 5}, {-1, -7}, {-1, 7},
            {1, -7}, {1, 7}, {5, -5}, {5, 5}, {7, -1}, {7, 1}}, {{-6, -4}, {-6, 4}, {-4, -6}, {-4, 6}, {4, -6}, {4, 6},
            {6, -4}, {6, 4}}, {{-7, -2}, {-7, 2}, {-2, -7}, {-2, 7}, {2, -7}, {2, 7}, {7, -2}, {7, 2}}, {{-7, -3},
            {-7, 3}, {-3, -7}, {-3, 7}, {3, -7}, {3, 7}, {7, -3}, {7, 3}}, {{-6, -5}, {-6, 5}, {-5, -6}, {-5, 6},
            {5, -6}, {5, 6}, {6, -5}, {6, 5}}, {{-7, -4}, {-7, 4}, {-4, -7}, {-4, 7}, {4, -7}, {4, 7}, {7, -4}, {7, 4}},
            {{-6, -6}, {-6, 6}, {6, -6}, {6, 6}}, {{-7, -5}, {-7, 5}, {-5, -7}, {-5, 7}, {5, -7}, {5, 7}, {7, -5},
            {7, 5}}, {{-7, -6}, {-7, 6}, {-6, -7}, {-6, 7}, {6, -7}, {6, 7}, {7, -6}, {7, 6}}, {{-7, -7}, {-7, 7},
            {7, -7}, {7, 7}}};
}