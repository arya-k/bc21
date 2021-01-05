package bot;

import battlecode.common.*;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

abstract class Robot {
    static RobotController rc = null;
    static RobotType type = null;

    public static void init(RobotController rc) throws GameActionException {
        Robot.rc = rc;
        type = rc.getType();
    }

    abstract void onAwake() throws GameActionException;

    abstract void onUpdate() throws GameActionException;
}