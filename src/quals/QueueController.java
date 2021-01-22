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
    private static int[] queuedInfluence = new int[LEVELS];

    // tracking builds
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
        queuedInfluence[level] += influence;
    }

    public static UnitBuild peek() {
        if (pq.isEmpty()) return null;
        return pq.peek();
    }

    public static void pushMany(RobotType type, int influence, Communication.Message message, int level, int count) {
        for (int i = count; --i >= 0; )
            pq.push(new UnitBuild(type, influence, message), level);
        queuedInfluence[level] += count * influence;
    }

    public static boolean isEmpty() {
        return pq.isEmpty();
    }

    public static void clear() {
        pq.clear();
        queuedInfluence = new int[LEVELS];
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
                    case CAPTURE_NEUTRAL_EC:
                        break;
                    case FINAL_FRONTIER:
                        EnlightenmentCenter.finalDefenderID = info.getID(); // NOTE: Shared with EnlightenmentCenter
                        break;
                    case BUFF:
                        EnlightenmentCenter.bufferID = info.getID(); // NOTE: Shared with EnlightenmentCenter
                        break;
                }
            }
            prevUnit = null;
            return true;
        }
        return false;
    }

    public boolean tryUnitBuild() throws GameActionException {
        if (!rc.isReady() || pq.isEmpty()) return false;
        UnitBuild nextUnit = pq.peek(); // We need to find a new unit to build!

        int myInfluence = rc.getInfluence();
        int nextUnitInfluence = nextUnit.influence;
        if (nextUnit.type == RobotType.SLANDERER)
            nextUnitInfluence = getSlandererInfluence(); // Slanderer influence is always chosen dynamically.

        if ((nextUnit.priority <= ULTRA_HIGH && nextUnitInfluence <= myInfluence) ||
                nextUnitInfluence + influenceMinimum() <= myInfluence) { // build a unit

            Direction buildDir = null;
            for (Direction spawnDir : spawnDirs) {
                if (rc.canBuildRobot(nextUnit.type, spawnDir, nextUnitInfluence)) {
                    buildDir = spawnDir;
                    break;
                }
            }
            if (nextUnit.message.label == Communication.Label.FINAL_FRONTIER)
                EnlightenmentCenter.addedFinalDefender = false; // NOTE: Shared with EnlightenmentCenter

            // Exit conditions
            if (nextUnit.type == RobotType.SLANDERER && muckrakerNearby()) {
                pq.pop();
                return tryUnitBuild();
            }
            if (buildDir == null || nextUnitInfluence < 0) return false;

            rc.setFlag(encode(nextUnit.message)); // Do the build!
            rc.buildRobot(nextUnit.type, buildDir, nextUnitInfluence);
            pq.pop();
            queuedInfluence[nextUnit.priority] -= nextUnit.influence;

            prevUnit = nextUnit;
            prevDir = buildDir;
            return true;
        }
        return true;
    }

    /* Some Helpful Access Functions */

    /**
     * @param above an int specifying a priority level
     * @return total influence minus influence in the queue at least the given `level`
     */
    public static int effectiveInfluenceAbove(int above) {
        return rc.getInfluence() - queuedInfluenceAbove(above);
    }

    /**
     * @return total influence minus influence in the queue
     */
    public static int effectiveInfluence() {
        return rc.getInfluence() - queuedInfluence();
    }

    /**
     * @return the amount of influence in the queue
     */
    public static int queuedInfluence() {
        return queuedInfluenceAbove(LEVELS - 1);
    }

    /**
     * @param above an int specifying a priority level
     * @return the amount of influence queued at a priority of at least `level`
     */
    public static int queuedInfluenceAbove(int above) {
        int sum = 0;
        for (int i = 0; i <= above; i++) {
            sum += queuedInfluence[i];
        }
        return sum;
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

    public static boolean muckrakerNearby() {
        for (RobotInfo bot : rc.senseNearbyRobots(24))
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER)
                return true;
        return false;
    }

    public static int getSlandererInfluence() {
        int useInfluence = rc.getInfluence() - influenceMinimum();
        if (useInfluence < slandererInfluences[0]) return -1;

        for (int i = 0; i < slandererInfluences.length - 1; i++)
            if (useInfluence < slandererInfluences[i + 1])
                return slandererInfluences[i];
        return slandererInfluences[slandererInfluences.length - 1];
    }

    public static int getPoliticianInfluence() {
        return Math.max(18, rc.getInfluence() / 30);
    }

    public static int influenceMinimum() {
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
