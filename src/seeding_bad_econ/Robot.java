package seeding_bad_econ;

import battlecode.common.*;
import seeding_bad_econ.Communication.Label;
import seeding_bad_econ.Communication.Message;

import static seeding_bad_econ.Communication.decode;
import static seeding_bad_econ.Communication.encode;

abstract public class Robot {
    public static RobotController rc = null;
    public static int firstTurn;
    public static MapLocation initLoc;

    public static MapLocation currentLocation;
    public static RobotInfo[] nearby;
    public static int myInfluence;
    public static int currentRound;

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


    public static void init(RobotController rc) throws GameActionException {
        Robot.firstTurn = rc.getRoundNum();
        Robot.rc = rc;
        Robot.initLoc = rc.getLocation();
        myInfluence = rc.getInfluence();

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

        Nav.init();
    }

    abstract void onAwake() throws GameActionException;

    void onUpdate() throws GameActionException {
        myInfluence = rc.getInfluence();
        currentLocation = rc.getLocation();
        nearby = rc.senseNearbyRobots();
        currentRound = rc.getRoundNum();

        if (rc.getType() != RobotType.ENLIGHTENMENT_CENTER) {
            // send back information about danger to your EC
            if (assignment == null || assignment.label != Label.BUFF) {
                int num_enemies = 0;
                boolean sawMuckraker = false;
                boolean sawHeavy = false;
                MapLocation locationToSend = currentLocation;
                for (RobotInfo bot : nearby) {
                    if (bot.getTeam() == rc.getTeam()) continue;
                    if (bot.getType() == RobotType.ENLIGHTENMENT_CENTER || bot.getType() == RobotType.SLANDERER)
                        continue;
                    if (bot.getType() == RobotType.POLITICIAN && bot.getInfluence() < GameConstants.EMPOWER_TAX)
                        continue;
                    if (bot.getType() == RobotType.MUCKRAKER) sawMuckraker = true;
                    if (bot.getInfluence() > Math.max(40, rc.getRoundNum() / 4)) sawHeavy = true;
                    if (locationToSend.equals(currentLocation)) locationToSend = bot.getLocation();
                    if (locationToSend.distanceSquaredTo(centerLoc) > bot.getLocation().distanceSquaredTo(centerLoc))
                        locationToSend = bot.getLocation();
                    num_enemies++;
                }
                flagMessage(Label.DANGER_INFO, locationToSend.x % 128, locationToSend.y % 128,
                        Math.min(num_enemies, 15), sawMuckraker ? 1 : 0, sawHeavy ? 1 : 0);
            }


            // move away from a buffing politician ASAP
            if (rc.isReady()) {
                for (RobotInfo info : rc.senseNearbyRobots(8, rc.getTeam())) {
                    if (info.getType() != RobotType.POLITICIAN) continue;
                    int flag = rc.getFlag(info.getID());
                    if (flag != 0 && decode(flag).label == Label.BUFF) {
                        nearbyBufferLoc = info.getLocation();
                        if (nearbyBufferLoc.distanceSquaredTo(currentLocation) > 2)
                            break;
                        int runDir = nearbyBufferLoc.directionTo(currentLocation).ordinal();
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
    }

    /* Utility functions */

    static void takeMove(Direction dir) throws GameActionException {
        if (nearbyBufferLoc != null) {
            MapLocation newLoc = currentLocation.add(dir);
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
        currentLocation = rc.getLocation();
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
        return directions[(currentRound + rc.getID()) % 8];
    }

    static void logBytecodeUse(int startRound, int startBC) {
        int limit = rc.getType().bytecodeLimit;
        int byteCount = (limit - startBC) + (currentRound - startRound - 1) * limit + Clock.getBytecodeNum();
        System.out.println("@@@Bytecodes used: " + byteCount);
    }
}