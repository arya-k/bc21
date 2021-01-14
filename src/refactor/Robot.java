package refactor;

import battlecode.common.*;
import refactor.Communication.Label;
import refactor.Communication.Message;

import static refactor.Communication.decode;
import static refactor.Communication.encode;

abstract public class Robot {
    public static RobotController rc = null;
    public static int firstTurn;
    public static MapLocation initLoc;

    public static MapLocation currentLocation;
    public static RobotInfo[] nearby;
    public static int myInfluence;
    public static int currentRound;

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
                Robot.assignment = decode(flag);
                rc.setFlag(flag); // useful default value: so other bots can know your assignment.
                break;
            }
        }

        Nav.init();
    }

    abstract void onAwake() throws GameActionException;

    void onUpdate() throws GameActionException {
        myInfluence = rc.getInfluence();
        currentLocation = rc.getLocation();
        nearby = rc.senseNearbyRobots();
        currentRound = rc.getRoundNum();
    };

    /* Utility functions */

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