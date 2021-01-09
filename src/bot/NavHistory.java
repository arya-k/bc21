package bot;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

/**
 * Class to track visited chunks and exploration.
 */
public class NavHistory {
    private static StringBuilder keys = new StringBuilder();
    private static int RANDOM_SEED, SIGHT_RANGE;

    private static int northEdge = 100; // sentinels
    private static int eastEdge = 100;
    private static int southEdge = -100;
    private static int westEdge = -100;

    /**
     * Inits the random var and the sight range.
     */
    public static void init() {
        RANDOM_SEED = (int) (Math.random() * 1024);
        SIGHT_RANGE = (int) Math.floor(Math.sqrt(Robot.rc.getType().sensorRadiusSquared));
    }

    /**
     * Checks whether a location has been visited. If it is outside the range
     * of valid edges. Accounts for world borders as well.
     *
     * @param m the MapLocation to check
     * @return whether the clamped mapLocation appears in the set of visited locations.
     */
    public static boolean visited(MapLocation m) {
        int dx = ((m.x - Robot.initLoc.x) / 8) + 7; // between 0 and 15
        int dy = ((m.y - Robot.initLoc.y) / 8) + 7;

        if (dy >= northEdge || dy <= southEdge || dx >= eastEdge || dx <= westEdge) return true; // off map

        return keys.indexOf("" + (char) ((dy << 4) + dx)) != -1; // whoa one char arya you're so cool
    }


    /**
     * Update the known locations of edges, and the visited chunks. Call on every tick.
     */
    public static void update() throws GameActionException {
        MapLocation m = Robot.rc.getLocation();

        // todo: maybe avoid extra checks with some fancy floors and ceilings?
        if (northEdge == 100 && !Robot.rc.onTheMap(m.translate(0, SIGHT_RANGE))) {
            northEdge = ((m.y + SIGHT_RANGE - Robot.initLoc.y) / 8) + 7;
            if ((m.y + SIGHT_RANGE - Robot.initLoc.y) % 8 != 0) ++northEdge;
        }
        if (southEdge == -100 && !Robot.rc.onTheMap(m.translate(0, -1 * SIGHT_RANGE))) {
            southEdge = ((m.y - SIGHT_RANGE - Robot.initLoc.y) / 8) + 7;
            if ((m.y - SIGHT_RANGE - Robot.initLoc.y) % 8 != 0) --southEdge;
        }
        if (eastEdge == 100 && !Robot.rc.onTheMap(m.translate(SIGHT_RANGE, 0))) {
            eastEdge = ((m.x + SIGHT_RANGE - Robot.initLoc.x) / 8) + 7;
            if ((m.x + SIGHT_RANGE - Robot.initLoc.x) % 8 != 0) ++eastEdge;
        }
        if (westEdge == -100 && !Robot.rc.onTheMap(m.translate(-1 * SIGHT_RANGE, 0))) {
            westEdge = ((m.x - SIGHT_RANGE - Robot.initLoc.x) / 8) + 7;
            if ((m.x - SIGHT_RANGE - Robot.initLoc.x) % 8 != 0) --westEdge;
        }

        // mark current location as visited.
        int dx = ((m.x - Robot.initLoc.x) / 8) + 7;
        int dy = ((m.y - Robot.initLoc.y) / 8) + 7;

        String key = "" + (char) ((dy << 4) + dx);

        if (keys.indexOf(key) == -1)
            keys.append(key);
    }


    public static MapLocation nearestUnexploredLocation() {
        // TODO: some sort of random component would be cool here...

        int bc = Clock.getBytecodeNum();
        int roundNum = Robot.rc.getRoundNum();

        MapLocation m = Robot.rc.getLocation();
        if (!visited(m.translate(-8, 0))) return m.translate(-8, 0);
        if (!visited(m.translate(0, -8))) return m.translate(0, -8);
        if (!visited(m.translate(0, 8))) return m.translate(0, 8);
        if (!visited(m.translate(8, 0))) return m.translate(8, 0);
        if (!visited(m.translate(-8, -8))) return m.translate(-8, -8);
        if (!visited(m.translate(-8, 8))) return m.translate(-8, 8);
        if (!visited(m.translate(8, -8))) return m.translate(8, -8);
        if (!visited(m.translate(8, 8))) return m.translate(8, 8);
        if (!visited(m.translate(-16, 0))) return m.translate(-16, 0);
        if (!visited(m.translate(0, -16))) return m.translate(0, -16);
        if (!visited(m.translate(0, 16))) return m.translate(0, 16);
        if (!visited(m.translate(16, 0))) return m.translate(16, 0);
        if (!visited(m.translate(-16, -8))) return m.translate(-16, -8);
        if (!visited(m.translate(-16, 8))) return m.translate(-16, 8);
        if (!visited(m.translate(-8, -16))) return m.translate(-8, -16);
        if (!visited(m.translate(-8, 16))) return m.translate(-8, 16);
        if (!visited(m.translate(8, -16))) return m.translate(8, -16);
        if (!visited(m.translate(8, 16))) return m.translate(8, 16);
        if (!visited(m.translate(16, -8))) return m.translate(16, -8);
        if (!visited(m.translate(16, 8))) return m.translate(16, 8);
        if (!visited(m.translate(-16, -16))) return m.translate(-16, -16);
        if (!visited(m.translate(-16, 16))) return m.translate(-16, 16);
        if (!visited(m.translate(16, -16))) return m.translate(16, -16);
        if (!visited(m.translate(16, 16))) return m.translate(16, 16);
        if (!visited(m.translate(-24, 0))) return m.translate(-24, 0);
        if (!visited(m.translate(0, -24))) return m.translate(0, -24);
        if (!visited(m.translate(0, 24))) return m.translate(0, 24);
        if (!visited(m.translate(24, 0))) return m.translate(24, 0);
        if (!visited(m.translate(-24, -8))) return m.translate(-24, -8);
        if (!visited(m.translate(-24, 8))) return m.translate(-24, 8);
        if (!visited(m.translate(-8, -24))) return m.translate(-8, -24);
        if (!visited(m.translate(-8, 24))) return m.translate(-8, 24);
        if (!visited(m.translate(8, -24))) return m.translate(8, -24);
        if (!visited(m.translate(8, 24))) return m.translate(8, 24);
        if (!visited(m.translate(24, -8))) return m.translate(24, -8);
        if (!visited(m.translate(24, 8))) return m.translate(24, 8);
        if (!visited(m.translate(-24, -16))) return m.translate(-24, -16);
        if (!visited(m.translate(-24, 16))) return m.translate(-24, 16);
        if (!visited(m.translate(-16, -24))) return m.translate(-16, -24);
        if (!visited(m.translate(-16, 24))) return m.translate(-16, 24);
        if (!visited(m.translate(16, -24))) return m.translate(16, -24);
        if (!visited(m.translate(16, 24))) return m.translate(16, 24);
        if (!visited(m.translate(24, -16))) return m.translate(24, -16);
        if (!visited(m.translate(24, 16))) return m.translate(24, 16);
        if (!visited(m.translate(-32, 0))) return m.translate(-32, 0);
        if (!visited(m.translate(0, -32))) return m.translate(0, -32);
        if (!visited(m.translate(0, 32))) return m.translate(0, 32);
        if (!visited(m.translate(32, 0))) return m.translate(32, 0);
        if (!visited(m.translate(-32, -8))) return m.translate(-32, -8);
        if (!visited(m.translate(-32, 8))) return m.translate(-32, 8);
        if (!visited(m.translate(-8, -32))) return m.translate(-8, -32);
        if (!visited(m.translate(-8, 32))) return m.translate(-8, 32);
        if (!visited(m.translate(8, -32))) return m.translate(8, -32);
        if (!visited(m.translate(8, 32))) return m.translate(8, 32);
        if (!visited(m.translate(32, -8))) return m.translate(32, -8);
        if (!visited(m.translate(32, 8))) return m.translate(32, 8);
        if (!visited(m.translate(-24, -24))) return m.translate(-24, -24);
        if (!visited(m.translate(-24, 24))) return m.translate(-24, 24);
        if (!visited(m.translate(24, -24))) return m.translate(24, -24);
        if (!visited(m.translate(24, 24))) return m.translate(24, 24);
        if (!visited(m.translate(-32, -16))) return m.translate(-32, -16);
        if (!visited(m.translate(-32, 16))) return m.translate(-32, 16);
        if (!visited(m.translate(-16, -32))) return m.translate(-16, -32);
        if (!visited(m.translate(-16, 32))) return m.translate(-16, 32);
        if (!visited(m.translate(16, -32))) return m.translate(16, -32);
        if (!visited(m.translate(16, 32))) return m.translate(16, 32);
        if (!visited(m.translate(32, -16))) return m.translate(32, -16);
        if (!visited(m.translate(32, 16))) return m.translate(32, 16);
        if (!visited(m.translate(-40, 0))) return m.translate(-40, 0);
        if (!visited(m.translate(-32, -24))) return m.translate(-32, -24);
        if (!visited(m.translate(-32, 24))) return m.translate(-32, 24);
        if (!visited(m.translate(-24, -32))) return m.translate(-24, -32);
        if (!visited(m.translate(-24, 32))) return m.translate(-24, 32);
        if (!visited(m.translate(0, -40))) return m.translate(0, -40);
        if (!visited(m.translate(0, 40))) return m.translate(0, 40);
        if (!visited(m.translate(24, -32))) return m.translate(24, -32);
        if (!visited(m.translate(24, 32))) return m.translate(24, 32);
        if (!visited(m.translate(32, -24))) return m.translate(32, -24);
        if (!visited(m.translate(32, 24))) return m.translate(32, 24);
        if (!visited(m.translate(40, 0))) return m.translate(40, 0);
        if (!visited(m.translate(-40, -8))) return m.translate(-40, -8);
        if (!visited(m.translate(-40, 8))) return m.translate(-40, 8);
        if (!visited(m.translate(-8, -40))) return m.translate(-8, -40);
        if (!visited(m.translate(-8, 40))) return m.translate(-8, 40);
        if (!visited(m.translate(8, -40))) return m.translate(8, -40);
        if (!visited(m.translate(8, 40))) return m.translate(8, 40);
        if (!visited(m.translate(40, -8))) return m.translate(40, -8);
        if (!visited(m.translate(40, 8))) return m.translate(40, 8);
        if (!visited(m.translate(-40, -16))) return m.translate(-40, -16);
        if (!visited(m.translate(-40, 16))) return m.translate(-40, 16);
        if (!visited(m.translate(-16, -40))) return m.translate(-16, -40);
        if (!visited(m.translate(-16, 40))) return m.translate(-16, 40);
        if (!visited(m.translate(16, -40))) return m.translate(16, -40);
        if (!visited(m.translate(16, 40))) return m.translate(16, 40);
        if (!visited(m.translate(40, -16))) return m.translate(40, -16);
        if (!visited(m.translate(40, 16))) return m.translate(40, 16);
        if (!visited(m.translate(-32, -32))) return m.translate(-32, -32);
        if (!visited(m.translate(-32, 32))) return m.translate(-32, 32);
        if (!visited(m.translate(32, -32))) return m.translate(32, -32);
        if (!visited(m.translate(32, 32))) return m.translate(32, 32);
        if (!visited(m.translate(-40, -24))) return m.translate(-40, -24);
        if (!visited(m.translate(-40, 24))) return m.translate(-40, 24);
        if (!visited(m.translate(-24, -40))) return m.translate(-24, -40);
        if (!visited(m.translate(-24, 40))) return m.translate(-24, 40);
        if (!visited(m.translate(24, -40))) return m.translate(24, -40);
        if (!visited(m.translate(24, 40))) return m.translate(24, 40);
        if (!visited(m.translate(40, -24))) return m.translate(40, -24);
        if (!visited(m.translate(40, 24))) return m.translate(40, 24);
        if (!visited(m.translate(-48, 0))) return m.translate(-48, 0);
        if (!visited(m.translate(0, -48))) return m.translate(0, -48);
        if (!visited(m.translate(0, 48))) return m.translate(0, 48);
        if (!visited(m.translate(48, 0))) return m.translate(48, 0);
        if (!visited(m.translate(-48, -8))) return m.translate(-48, -8);
        if (!visited(m.translate(-48, 8))) return m.translate(-48, 8);
        if (!visited(m.translate(-8, -48))) return m.translate(-8, -48);
        if (!visited(m.translate(-8, 48))) return m.translate(-8, 48);
        if (!visited(m.translate(8, -48))) return m.translate(8, -48);
        if (!visited(m.translate(8, 48))) return m.translate(8, 48);
        if (!visited(m.translate(48, -8))) return m.translate(48, -8);
        if (!visited(m.translate(48, 8))) return m.translate(48, 8);
        if (!visited(m.translate(-48, -16))) return m.translate(-48, -16);
        if (!visited(m.translate(-48, 16))) return m.translate(-48, 16);
        if (!visited(m.translate(-16, -48))) return m.translate(-16, -48);
        if (!visited(m.translate(-16, 48))) return m.translate(-16, 48);
        if (!visited(m.translate(16, -48))) return m.translate(16, -48);
        if (!visited(m.translate(16, 48))) return m.translate(16, 48);
        if (!visited(m.translate(48, -16))) return m.translate(48, -16);
        if (!visited(m.translate(48, 16))) return m.translate(48, 16);
        if (!visited(m.translate(-40, -32))) return m.translate(-40, -32);
        if (!visited(m.translate(-40, 32))) return m.translate(-40, 32);
        if (!visited(m.translate(-32, -40))) return m.translate(-32, -40);
        if (!visited(m.translate(-32, 40))) return m.translate(-32, 40);
        if (!visited(m.translate(32, -40))) return m.translate(32, -40);
        if (!visited(m.translate(32, 40))) return m.translate(32, 40);
        if (!visited(m.translate(40, -32))) return m.translate(40, -32);
        if (!visited(m.translate(40, 32))) return m.translate(40, 32);
        if (!visited(m.translate(-48, -24))) return m.translate(-48, -24);
        if (!visited(m.translate(-48, 24))) return m.translate(-48, 24);
        if (!visited(m.translate(-24, -48))) return m.translate(-24, -48);
        if (!visited(m.translate(-24, 48))) return m.translate(-24, 48);
        if (!visited(m.translate(24, -48))) return m.translate(24, -48);
        if (!visited(m.translate(24, 48))) return m.translate(24, 48);
        if (!visited(m.translate(48, -24))) return m.translate(48, -24);
        if (!visited(m.translate(48, 24))) return m.translate(48, 24);
        if (!visited(m.translate(-56, 0))) return m.translate(-56, 0);
        if (!visited(m.translate(0, -56))) return m.translate(0, -56);
        if (!visited(m.translate(0, 56))) return m.translate(0, 56);
        if (!visited(m.translate(56, 0))) return m.translate(56, 0);
        if (!visited(m.translate(-56, -8))) return m.translate(-56, -8);
        if (!visited(m.translate(-56, 8))) return m.translate(-56, 8);
        if (!visited(m.translate(-40, -40))) return m.translate(-40, -40);
        if (!visited(m.translate(-40, 40))) return m.translate(-40, 40);
        if (!visited(m.translate(-8, -56))) return m.translate(-8, -56);
        if (!visited(m.translate(-8, 56))) return m.translate(-8, 56);
        if (!visited(m.translate(8, -56))) return m.translate(8, -56);
        if (!visited(m.translate(8, 56))) return m.translate(8, 56);
        if (!visited(m.translate(40, -40))) return m.translate(40, -40);
        if (!visited(m.translate(40, 40))) return m.translate(40, 40);
        if (!visited(m.translate(56, -8))) return m.translate(56, -8);
        if (!visited(m.translate(56, 8))) return m.translate(56, 8);
        if (!visited(m.translate(-48, -32))) return m.translate(-48, -32);
        if (!visited(m.translate(-48, 32))) return m.translate(-48, 32);
        if (!visited(m.translate(-32, -48))) return m.translate(-32, -48);
        if (!visited(m.translate(-32, 48))) return m.translate(-32, 48);
        if (!visited(m.translate(32, -48))) return m.translate(32, -48);
        if (!visited(m.translate(32, 48))) return m.translate(32, 48);
        if (!visited(m.translate(48, -32))) return m.translate(48, -32);
        if (!visited(m.translate(48, 32))) return m.translate(48, 32);
        if (!visited(m.translate(-56, -16))) return m.translate(-56, -16);
        if (!visited(m.translate(-56, 16))) return m.translate(-56, 16);
        if (!visited(m.translate(-16, -56))) return m.translate(-16, -56);
        if (!visited(m.translate(-16, 56))) return m.translate(-16, 56);
        if (!visited(m.translate(16, -56))) return m.translate(16, -56);
        if (!visited(m.translate(16, 56))) return m.translate(16, 56);
        if (!visited(m.translate(56, -16))) return m.translate(56, -16);
        if (!visited(m.translate(56, 16))) return m.translate(56, 16);
        if (!visited(m.translate(-56, -24))) return m.translate(-56, -24);
        if (!visited(m.translate(-56, 24))) return m.translate(-56, 24);
        if (!visited(m.translate(-24, -56))) return m.translate(-24, -56);
        if (!visited(m.translate(-24, 56))) return m.translate(-24, 56);
        if (!visited(m.translate(24, -56))) return m.translate(24, -56);
        if (!visited(m.translate(24, 56))) return m.translate(24, 56);
        if (!visited(m.translate(56, -24))) return m.translate(56, -24);
        if (!visited(m.translate(56, 24))) return m.translate(56, 24);
        if (!visited(m.translate(-48, -40))) return m.translate(-48, -40);
        if (!visited(m.translate(-48, 40))) return m.translate(-48, 40);
        if (!visited(m.translate(-40, -48))) return m.translate(-40, -48);
        if (!visited(m.translate(-40, 48))) return m.translate(-40, 48);
        if (!visited(m.translate(40, -48))) return m.translate(40, -48);
        if (!visited(m.translate(40, 48))) return m.translate(40, 48);
        if (!visited(m.translate(48, -40))) return m.translate(48, -40);
        if (!visited(m.translate(48, 40))) return m.translate(48, 40);
        if (!visited(m.translate(-56, -32))) return m.translate(-56, -32);
        if (!visited(m.translate(-56, 32))) return m.translate(-56, 32);
        if (!visited(m.translate(-32, -56))) return m.translate(-32, -56);
        if (!visited(m.translate(-32, 56))) return m.translate(-32, 56);
        if (!visited(m.translate(32, -56))) return m.translate(32, -56);
        if (!visited(m.translate(32, 56))) return m.translate(32, 56);
        if (!visited(m.translate(56, -32))) return m.translate(56, -32);
        if (!visited(m.translate(56, 32))) return m.translate(56, 32);
        if (!visited(m.translate(-48, -48))) return m.translate(-48, -48);
        if (!visited(m.translate(-48, 48))) return m.translate(-48, 48);
        if (!visited(m.translate(48, -48))) return m.translate(48, -48);
        if (!visited(m.translate(48, 48))) return m.translate(48, 48);
        if (!visited(m.translate(-56, -40))) return m.translate(-56, -40);
        if (!visited(m.translate(-56, 40))) return m.translate(-56, 40);
        if (!visited(m.translate(-40, -56))) return m.translate(-40, -56);
        if (!visited(m.translate(-40, 56))) return m.translate(-40, 56);
        if (!visited(m.translate(40, -56))) return m.translate(40, -56);
        if (!visited(m.translate(40, 56))) return m.translate(40, 56);
        if (!visited(m.translate(56, -40))) return m.translate(56, -40);
        if (!visited(m.translate(56, 40))) return m.translate(56, 40);
        if (!visited(m.translate(-56, -48))) return m.translate(-56, -48);
        if (!visited(m.translate(-56, 48))) return m.translate(-56, 48);
        if (!visited(m.translate(-48, -56))) return m.translate(-48, -56);
        if (!visited(m.translate(-48, 56))) return m.translate(-48, 56);
        if (!visited(m.translate(48, -56))) return m.translate(48, -56);
        if (!visited(m.translate(48, 56))) return m.translate(48, 56);
        if (!visited(m.translate(56, -48))) return m.translate(56, -48);
        if (!visited(m.translate(56, 48))) return m.translate(56, 48);
        if (!visited(m.translate(-56, -56))) return m.translate(-56, -56);
        if (!visited(m.translate(-56, 56))) return m.translate(-56, 56);
        if (!visited(m.translate(56, -56))) return m.translate(56, -56);
        if (!visited(m.translate(56, 56))) return m.translate(56, 56);
//
//        for (int i = 0; i < keys.length(); i++) {
//            int c = (int) keys.charAt(i);
//            int dx = (c % 16);
//            int dy = (c / 16);
//            System.out.println("(" + dy + "," + dx + ")");
//        }
//
//        Robot.rc.resign();
        return null;
    }
}