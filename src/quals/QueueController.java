package quals;

import battlecode.common.*;
import quals.utils.UnitBuild;
import quals.utils.UnitBuildDPQueue;

import static quals.Communication.encode;

/**
 * Helps enlightenment center with spawning units optimally, keeping track of who
 * was spawned in the past turns too.
 */
public class QueueController {
    private static RobotController rc;

    private static final int LEVELS = 4;
    public static final int ULTRA_HIGH = 0, HIGH = 1, MED = 2, LOW = 3;
    private static final UnitBuildDPQueue pq = new UnitBuildDPQueue(LEVELS);

    // tracking builds
    private static UnitBuild prevUnit = null;
    private static Direction prevDir = null;

    static Direction[] spawnDirs = new Direction[8];

    // good influences to build slanderers at
    static final int[] slandererInfluences = {85, 107, 130, 154, 178, 203, 229, 255, 282, 339, 399, 431, 498, 569, 605, 683, 724, 949};

    public static void init() throws GameActionException {
        rc = Robot.rc;
        calcBestSpawnDirs();
    }

    /* Managing the Queue */
    public static void push(RobotType type, Communication.Message message, double significance, int minInfluence, int level) {
        EnlightenmentCenter.numQueued[type.ordinal()]++;
        pq.push(new UnitBuild(type, message, significance, minInfluence), level);
    }

    public static UnitBuild peek() {
        if (pq.isEmpty()) return null;
        return pq.peek();
    }

    public static void pushMany(RobotType type, Communication.Message message, double significance, int minInfluence, int level, int count) {
        for (int i = count; --i >= 0; )
            push(type, message, significance, minInfluence, level);
    }

    public static boolean isEmpty() {
        return pq.isEmpty();
    }

    public static void clear() {
        pq.clear();
    }

    /* Managing Unit Building */
    public static boolean trackLastBuiltUnit() throws GameActionException {
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            if (info != null) {
                switch (prevUnit.message.label) {
                    case SCOUT:
                    case EXPLORE:
                        EnlightenmentCenter.trackedIds.add(info.getID()); // NOTE: Shared with EnlightenmentCenter
                        break;
                    case UNCLOG:
                        EnlightenmentCenter.unclogAdded = false;
                        EnlightenmentCenter.unclogID = info.getID();
                        break;
                }
            }
            prevUnit = null;
            return true;
        }
        return false;
    }

    public static boolean tryUnitBuild() throws GameActionException {
        if (!rc.isReady() || pq.isEmpty()) return false;
        UnitBuild nextUnit = pq.peek(); // We need to find a new unit to build!

        int myInfluence = rc.getInfluence();

        // dynamically chose next units influence
        int usableInfluence = rc.getInfluence() - influenceMinimum();
        int unitCap = (int) (usableInfluence * nextUnit.significance);
        int nextUnitInfluence = -1;
        switch (nextUnit.type) {
            case SLANDERER:
                nextUnitInfluence = getSlandererInfluence(unitCap);
                if (nextUnitInfluence < nextUnit.minInfluence) {
                    nextUnitInfluence = getSlandererInfluence(getNextSlandererInfluence(nextUnit.minInfluence));
                }
                break;
            case MUCKRAKER:
                nextUnitInfluence = nextUnit.significance > 0.5 ? unitCap : 1;
                break;
            case POLITICIAN:
                nextUnitInfluence = unitCap;
                break;
        }
        // override influence
        if (nextUnit.minInfluence != -1) nextUnitInfluence = Math.max(nextUnit.minInfluence, nextUnitInfluence);

        if (nextUnitInfluence <= 0 || myInfluence - nextUnitInfluence < influenceMinimum()) {
            return false;
        }

        Direction buildDir = null;
        for (Direction spawnDir : spawnDirs) {
            if (rc.canBuildRobot(nextUnit.type, spawnDir, nextUnitInfluence)) {
                buildDir = spawnDir;
                break;
            }
        }

        // Exit conditions
        if (nextUnit.type == RobotType.SLANDERER && muckrakerNearby()) {
            pq.pop();
            return tryUnitBuild();
        }
        if (buildDir == null) return false;

        rc.setFlag(encode(nextUnit.message)); // Do the build!
        rc.buildRobot(nextUnit.type, buildDir, nextUnitInfluence);
        pq.pop();

        prevUnit = nextUnit;
        prevDir = buildDir;
        return true;
    }


    /* Utility Functions */
    public static void logNext() {
        if (!pq.isEmpty()) {
            UnitBuild next = pq.peek();
            System.out.println("Next unit: " + next.type + " " + next.priority);
        } else {
            System.out.println("PQ empty");
        }
    }

    public static boolean muckrakerNearby() {
        for (RobotInfo bot : rc.senseNearbyRobots(-1))
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER)
                return true;
        return false;
    }

    public static int getSlandererInfluence(int useInfluence) {
        if (useInfluence < slandererInfluences[0]) return -1;
        for (int i = 0; i < slandererInfluences.length - 1; i++)
            if (useInfluence < slandererInfluences[i + 1])
                return slandererInfluences[i];
        return slandererInfluences[slandererInfluences.length - 1];
    }

    public static int getNextSlandererInfluence(int floorInfluence) {
        for (int i = 0; i < slandererInfluences.length; i++) {
            int candidate = slandererInfluences[i];
            if (floorInfluence < candidate)
                return candidate;
        }
        return slandererInfluences[slandererInfluences.length - 1];
    }

    public static int influenceMinimum() {
        return 5 + (int) (rc.getRoundNum() * 0.1);
    }

    private static void calcBestSpawnDirs() throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        System.arraycopy(Robot.directions, 0, spawnDirs, 0, 8);

        for (int i = 0; i < 7; i++) {
            Direction best = spawnDirs[i];
            int besti = i;
            for (int j = i + 1; j < 8; j++) {
                Direction jd = spawnDirs[j];
                double jPass = 0;
                if (rc.onTheMap(currentLocation.add(jd)))
                    jPass = rc.sensePassability(currentLocation.add(jd));
                double bPass = 0;
                if (rc.onTheMap(currentLocation.add(best)))
                    bPass = rc.sensePassability(currentLocation.add(best));

                if (jPass > bPass) {
                    best = jd;
                    besti = j;
                }
            }
            spawnDirs[besti] = spawnDirs[i];
            spawnDirs[i] = best;
        }
    }
}
