package bot;

import battlecode.common.*;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

abstract class Robot {
    static RobotController rc = null;
    static RobotType type = null;
    public static int centerID;
    static Message assignment = null;
    static final Direction[] directions = Direction.values();


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

    public static void exploreDir(Direction dir) throws GameActionException {
        if (rc.isReady()) {
            if (nextStep == null || !rc.canMove(nextStep)) {
                nextStep = Nav.goInDir(dir);
            }
            if (nextStep != null && rc.canMove(nextStep)) {
                rc.move(nextStep);
                nextStep = null;
                Clock.yield();
            }
        } else {
            nextStep = Nav.goInDir(dir);
            Clock.yield();
        }
    }

    public static Direction fromOrdinal(int ordinal) {
        return Robot.directions[ordinal];
    }
}