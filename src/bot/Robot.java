package bot;

import battlecode.common.*;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

abstract class Robot {
    static RobotController rc = null;
    static RobotType type = null;
    static int centerID;
    static Direction[] directions = Direction.values();

    public static void init(RobotController rc) throws GameActionException {
        Robot.rc = rc;
        Robot.type = rc.getType();
        // find the EC
        for (RobotInfo info : rc.senseNearbyRobots(2)) {
            if (info.getType() == RobotType.ENLIGHTENMENT_CENTER && info.getTeam() == rc.getTeam()) {
                Robot.centerID = info.getID();
            }
        }
    }

    abstract void onAwake() throws GameActionException;

    abstract void onUpdate() throws GameActionException;
}