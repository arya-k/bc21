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
    static final Direction[] directions = Direction.allDirections();


    public static void init(RobotController rc) throws GameActionException {
        Robot.rc = rc;
        Robot.type = rc.getType();
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

    static Direction nextStep;

    public static void exploreDir(Direction dir) throws GameActionException { // This will have to be reworked, sorry :(
        if (rc.isReady()) {
            if (nextStep == null || !rc.canMove(nextStep)) {
                Nav.doGoInDir(dir);
                nextStep = Nav.tick();
            }
            if (nextStep != null && rc.canMove(nextStep)) {
                rc.move(nextStep);
                nextStep = null;
                Clock.yield();
            }
        } else {
            Nav.doGoInDir(dir);
            nextStep = Nav.tick();
            Clock.yield();
        }
    }

    public static Direction fromOrdinal(int ordinal) {
        return Robot.directions[ordinal];
    }

    Message exploreMessage(Direction dir) throws GameActionException {
        int[] data = {dir.ordinal()};
        return new Message(Label.EXPLORE, data);
    }
}