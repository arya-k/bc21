package seeding;

import battlecode.common.*;
import seeding.utils.UnitBuild;
import seeding.utils.UnitBuildDPQueue;

import static seeding.Communication.encode;

/**
 * Helps enlightenment center with spawning units optimally, keeping track of who
 * was spawned in the past turns too.
 */
public class QueueController {
    private static RobotController rc;

    private static UnitBuildDPQueue pq = new UnitBuildDPQueue(4);
    public static final int ULTRA_HIGH = 0, HIGH = 1, MED = 2, LOW = 3;

    // tracking builds
    private static UnitBuild nextUnit = null;
    private static UnitBuild prevUnit = null;
    private static Direction prevDir = null;

    static Direction[] spawnDirs = new Direction[8];

    // good influences to build slanderers at
    static final int[] slandererInfluences = {41, 107, 178, 399, 605, 949};

    public void init() throws GameActionException {
        rc = Robot.rc;
        calcBestSpawnDirs();
    }

    /* Managing the Queue */
    public static void push(RobotType type, int influence, Communication.Message message, int level) {
        pq.push(new UnitBuild(type, influence, message), level);
    }

    public static void pushMany(RobotType type, int influence, Communication.Message message, int level, int count) {
        for (int i = count; --i >= 0; )
            pq.push(new UnitBuild(type, influence, message), level);
    }

    public static boolean isEmpty() {
        return pq.isEmpty();
    }

    public static void clear() {
        pq.clear();
    }

    public void cancelNeutralECAttacker() {
        if (nextUnit != null && nextUnit.message.label == Communication.Label.CAPTURE_NEUTRAL_EC)
            nextUnit = null;
    }

    /* Managing Unit Building */
    public static void trackLastBuiltUnit() throws GameActionException {
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            if (info != null) {
                switch (prevUnit.message.label) {
                    case SCOUT:
                    case EXPLORE:
                        EnlightenmentCenter.trackedIds.add(info.getID()); // NOTE: Shared with EnlightenmentCenter
                        break;
                    case CAPTURE_NEUTRAL_EC:
                        EnlightenmentCenter.neutralCapturers.add(info.getID()); // NOTE: Shared with EnlightenmentCenter
                        break;
                    case FINAL_FRONTIER:
                        EnlightenmentCenter.lastDefender = info.getID(); // NOTE: Shared with EnlightenmentCenter
                        break;
                }
            }
            prevUnit = null;
        }
    }

    public void tryUnitBuild() throws GameActionException {
        if (nextUnit == null && !pq.isEmpty()) nextUnit = pq.pop(); // We need to find a new unit to build!

        int myInfluence = rc.getInfluence();
        if (nextUnit != null && nextUnit.type == RobotType.SLANDERER)
            nextUnit.influence = getSlandererInfluence();
        if (nextUnit != null && ((nextUnit.priority <= HIGH && nextUnit.influence <= myInfluence) ||
                nextUnit.influence + influenceMinimum() <= myInfluence) && rc.isReady()) {
            // build a unit
            Direction buildDir = null;
            if (nextUnit.message.label == Communication.Label.FINAL_FRONTIER) {
                for (int i = spawnDirs.length - 1; i >= 0; i--) {
                    if (rc.canBuildRobot(nextUnit.type, spawnDirs[i], nextUnit.influence)) {
                        buildDir = spawnDirs[i];
                        break;
                    }
                }
                EnlightenmentCenter.addedFinalDefender = false; // NOTE: Shared with EnlightenmentCenter
            } else {
                for (Direction spawnDir : spawnDirs) {
                    if (rc.canBuildRobot(nextUnit.type, spawnDir, nextUnit.influence)) {
                        buildDir = spawnDir;
                        break;
                    }
                }
            }

            if (buildDir != null) {
                boolean skipCurrent = false;
                if (nextUnit.type == RobotType.SLANDERER && muckrakerNearby() || nextUnit.influence < 0) {
                    skipCurrent = true;
                }
                if (!skipCurrent) {
                    rc.setFlag(encode(nextUnit.message));
                    rc.buildRobot(nextUnit.type, buildDir, nextUnit.influence);


                    prevUnit = nextUnit;
                    prevDir = buildDir;
                }
                nextUnit = null;
            }
        }
    }

    /* Utility Functions */
    public static void logNext() {
        if (!pq.isEmpty()) {
            UnitBuild next = pq.peek();
            System.out.println("Next unit: " + next.type + " " + next.priority + " " + next.influence);
        } else {
            System.out.println("PQ empty");
        }
    }

    static boolean muckrakerNearby() {
        for (RobotInfo bot : rc.senseNearbyRobots(24)) {
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER) {
                return true;
            }
        }
        return false;
    }

    public static int getSlandererInfluence() {
        int useInfluence = rc.getInfluence() - influenceMinimum();
        if (useInfluence < slandererInfluences[0]) return -1;
        for (int i = 0; i < slandererInfluences.length - 1; i++) {
            if (useInfluence < slandererInfluences[i + 1])
                return slandererInfluences[i];
        }
        return slandererInfluences[slandererInfluences.length - 1];
    }

    public static int getPoliticianInfluence() {
        return Math.max(18, rc.getInfluence() / 30);
    }

    static int influenceMinimum() {
        return 20 + (int) (rc.getRoundNum() * 0.1);
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
