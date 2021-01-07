package bot;

import battlecode.common.*;
import bot.Communication.Label;
import bot.Communication.Message;

import static bot.Communication.decode;

abstract class Robot {
    static RobotController rc = null;
    static RobotType type = null;
    static int centerID;
    static Message assignment = null;
    static MapLocation initLoc;

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
        Robot.rc = rc;
        Robot.type = rc.getType();
        Robot.initLoc = rc.getLocation();

        // find the EC
        if (Robot.type != RobotType.ENLIGHTENMENT_CENTER) {
            for (RobotInfo info : rc.senseNearbyRobots(2)) {
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER && info.getTeam() == rc.getTeam()) {
                    Robot.centerID = info.getID();
                    Robot.assignment = decode(rc.getFlag(Robot.centerID));
                    System.out.println("Found assignment!");
                    break;
                }
            }
            if (Robot.assignment == null)
                System.out.println("Didnt find assignment!");
        }

    }

    abstract void onAwake() throws GameActionException;

    abstract void onUpdate() throws GameActionException;

    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    public static Direction fromOrdinal(int ordinal) {
        return Robot.directions[ordinal];
    }

    Message exploreMessage(Direction dir) throws GameActionException {
        int[] data = {dir.ordinal()};
        return new Message(Label.EXPLORE, data);
    }

    /**
     * Utility function to log the bytecodes used, accounting for the rounds possibly ticking over.
     */
    static void logBytecodeUse(int startRound, int startBC) {
        int limit = rc.getType().bytecodeLimit;
        int byteCount = (limit - startBC) + (rc.getRoundNum() - startRound - 1) * limit + Clock.getBytecodeNum();
        System.out.println("@@@Bytecodes used: " + byteCount);
    }
}