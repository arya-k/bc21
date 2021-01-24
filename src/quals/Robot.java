package quals;

import battlecode.common.*;
import quals.Communication.Label;
import quals.Communication.Message;

import static quals.Communication.decode;
import static quals.Communication.encode;

abstract public class Robot {
    public static RobotController rc = null;
    public static int firstTurn;
    public static MapLocation initLoc;

    public static RobotInfo[] nearby;

    public static MapLocation nearbyBufferLoc = null;

    static int centerID;
    static MapLocation centerLoc;

    static Message assignment = null;

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    /* EC tracking vars */
    static MapLocation closestECLoc;
    static int[] seenECs = new int[12];
    static int numSeenECs = 0;

    /* Attack Location vars */
    static MapLocation[] attackLocs = new MapLocation[12];
    static int numAttackLocs = 0;


    public static void init(RobotController rc) throws GameActionException {
        Robot.firstTurn = rc.getRoundNum();
        Robot.rc = rc;
        Robot.initLoc = rc.getLocation();

        if (rc.getType() == RobotType.ENLIGHTENMENT_CENTER) return; // Everything below here is for non-buildings:

        // find the EC
        for (RobotInfo info : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                Robot.centerID = info.getID();
                Robot.centerLoc = info.getLocation();
                int flag = rc.getFlag(Robot.centerID);
                if (flag != 0)
                    Robot.assignment = decode(flag);
                rc.setFlag(flag); // useful default value: so other bots can know your assignment.
                break;
            }
        }
        if (centerLoc == null) {
            centerLoc = initLoc;
            centerID = rc.getID();
        }
        closestECLoc = centerLoc;

        Nav.init();
    }

    abstract void onAwake() throws GameActionException;

    void onUpdate() throws GameActionException {
        nearby = rc.senseNearbyRobots();

        if (rc.getType() == RobotType.ENLIGHTENMENT_CENTER) return;

        // send back information about danger to your EC
        if (assignment == null || assignment.label != Label.BUFF) {
            int num_muckrakers = 0;
            MapLocation locationToSend = rc.getLocation();
            for (RobotInfo bot : nearby) {
                if (bot.getTeam() == rc.getTeam()) continue;
                if (bot.getType() != RobotType.MUCKRAKER) continue;
                if (locationToSend.equals(rc.getLocation())) locationToSend = bot.getLocation();
                if (locationToSend.distanceSquaredTo(centerLoc) > bot.getLocation().distanceSquaredTo(centerLoc))
                    locationToSend = bot.getLocation();
                num_muckrakers++;
            }
            flagMessage(Label.DANGER_INFO, locationToSend.x % 128, locationToSend.y % 128,
                    Math.min(num_muckrakers, 31));
        }

        // inform ECs of nearby enlightenment centers
        noteNearbyECs();

        // gather locations to attack
        if (rc.getID() != centerID && rc.canGetFlag(centerID)) {
            int flag = rc.getFlag(centerID);
            if (flag != 0) {
                Message msg = decode(flag);
                if (msg.label == Label.ATTACK_LOC)
                    addAttackLoc(getLocFromMessage(msg.data[0], msg.data[1]));
            }
        }

        // move away from a buffing politician ASAP
        if (rc.isReady()) {
            for (RobotInfo info : rc.senseNearbyRobots(8, rc.getTeam())) {
                if (info.getType() != RobotType.POLITICIAN) continue;
                int flag = rc.getFlag(info.getID());
                if (flag != 0 && decode(flag).label == Label.BUFF) {
                    nearbyBufferLoc = info.getLocation();
                    if (nearbyBufferLoc.distanceSquaredTo(rc.getLocation()) > 2)
                        break;
                    int runDir = nearbyBufferLoc.directionTo(rc.getLocation()).ordinal();
                    boolean diagonal = (runDir % 2) == 1;
                    for (int i = (diagonal ? 6 : 7); i <= (diagonal ? 10 : 9); i++) {
                        Direction moveDir = fromOrdinal((i + runDir) % 8);
                        if (rc.canMove(moveDir)) {
                            rc.move(moveDir);
                            break;
                        }
                    }
                }
                if (nearbyBufferLoc != null)
                    break;
                nearbyBufferLoc = null;
            }
        }
    }

    static void addAttackLoc(MapLocation loc) {
        int idx = numAttackLocs;
        for (int i = 0; i < numAttackLocs; i++)
            if (attackLocs[i].isWithinDistanceSquared(loc, 0))
                idx = i;
        if (idx == numAttackLocs) numAttackLocs++;
        attackLocs[idx] = loc;
    }

    static void removeAttackLoc(MapLocation loc) {
        int idx = numAttackLocs;
        for (int i = 0; i < numAttackLocs; i++)
            if (attackLocs[i].isWithinDistanceSquared(loc, 0))
                idx = i;
        if (idx == numAttackLocs) return;
        numAttackLocs--;
        attackLocs[idx] = attackLocs[numAttackLocs];
    }

    static MapLocation getClosestAttackLoc() {
        MapLocation closest = null;
        for (int i = 0; i < numAttackLocs; i++) {
            if (closest == null
                    || rc.getLocation().distanceSquaredTo(closest) > rc.getLocation().distanceSquaredTo(attackLocs[i]))
                closest = attackLocs[i];
        }
        return closest;
    }


    static MapLocation getNearbyEnemyEC() {
        MapLocation closest = null;
        for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (bot.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;
            if (closest == null
                    || rc.getLocation().distanceSquaredTo(closest) > rc.getLocation().distanceSquaredTo(bot.getLocation()))
                closest = bot.getLocation();
        }
        return closest;
    }


    static void noteNearbyECs() throws GameActionException {
        for (RobotInfo info : nearby) {
            if (info.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;

            if (info.getTeam() == rc.getTeam()) {
                if (centerLoc == null) centerLoc = info.getLocation();
                if (centerID == rc.getID()) {
                    centerID = info.getID();
                    centerLoc = info.getLocation();
                }
                if (centerLoc.distanceSquaredTo(rc.getLocation()) > info.getLocation().distanceSquaredTo(rc.getLocation())) {
                    centerLoc = info.getLocation();
                    centerID = info.getID();
                }

                // Consider removing it as an attack location:
                removeAttackLoc(info.getLocation());
            }

            if (seenECs[0] == info.ID || seenECs[1] == info.ID || seenECs[2] == info.ID || seenECs[3] == info.ID ||
                    seenECs[4] == info.ID || seenECs[5] == info.ID || seenECs[6] == info.ID || seenECs[7] == info.ID ||
                    seenECs[8] == info.ID || seenECs[9] == info.ID || seenECs[10] == info.ID || seenECs[11] == info.ID) {
                continue; // we don't want to note it again
            }
            seenECs[(numSeenECs++) % 12] = info.getID();

            MapLocation loc = info.getLocation();
            int log = (int) (Math.log(info.getConviction()) / Math.log(2)) + 1;
            if (info.getTeam() == rc.getTeam().opponent()) { // Enemy EC message...
                addAttackLoc(loc); // this is now a target
                flagMessage(Communication.Label.ENEMY_EC, loc.x % 128, loc.y % 128, Math.min(log, 15));
            } else if (info.getTeam() == Team.NEUTRAL) { // Neutral EC message...
                flagMessage(Communication.Label.NEUTRAL_EC, loc.x % 128, loc.y % 128, Math.min(log, 15));
            } else {
                removeAttackLoc(loc); // could have flipped enemy -> us
                flagMessage(Communication.Label.OUR_EC, loc.x % 128, loc.y % 128);
            }
        }
    }

    /* Utility functions */

    static void takeMove(Direction dir) throws GameActionException {
        if (nearbyBufferLoc != null) {
            MapLocation newLoc = rc.getLocation().add(dir);
            if (newLoc.distanceSquaredTo(nearbyBufferLoc) <= 2)
                return;
        }
        rc.move(dir);
    }

    static Message makeMessage(Label label, int... data) {
        return new Message(label, data);
    }

    static void flagMessage(Label label, int... data) throws GameActionException {
        rc.setFlag(encode(makeMessage(label, data)));
    }

    static MapLocation getLocFromMessage(int xMod, int yMod) {
        MapLocation currentLocation = rc.getLocation();
        int x = currentLocation.x % 128;
        int y = currentLocation.y % 128;
        int xOff = xMod - x;
        int yOff = yMod - y;
        if (Math.abs(xOff) >= 64)
            xOff = xOff > 0 ? xOff - 128 : xOff + 128;
        if (Math.abs(yOff) >= 64)
            yOff = yOff > 0 ? yOff - 128 : yOff + 128;
        return currentLocation.translate(xOff, yOff);
    }

    static Direction fromOrdinal(int i) {
        return directions[i];
    }

    static Direction randomDirection() {
        return directions[(rc.getRoundNum() + rc.getID()) % 8];
    }

    static void logBytecodeUse(int startRound, int startBC) {
        int limit = rc.getType().bytecodeLimit;
        int byteCount = (limit - startBC) + (rc.getRoundNum() - startRound - 1) * limit + Clock.getBytecodeNum();
        System.out.println("@@@Bytecodes used: " + byteCount);
    }
}