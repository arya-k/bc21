package refactor;

import battlecode.common.RobotController;
import battlecode.common.RobotType;

@SuppressWarnings("unused")
public strictfp class RobotPlayer {

    static RobotType myType;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        Robot robot = null;
        try {
            Robot.init(rc);
            RobotType currType = rc.getType();
            robot = getRobot(currType);
            myType = currType;
            robot.onAwake();
            robot.onUpdate();
        } catch (Exception e) {
        }

        while (true) {
            try {
                while (true) {
                    robot.onUpdate();
                    RobotType currType = rc.getType();
                    if (currType != myType) {
                        int currCenterId = Robot.centerID;
                        Robot.init(rc);
                        Robot.centerID = currCenterId;
                        robot = getRobot(currType);
                        robot.onAwake();
                        myType = currType;
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    private static Robot getRobot(RobotType currType) {
        switch (currType) {
            case ENLIGHTENMENT_CENTER:
                return new EnlightenmentCenter();
            case POLITICIAN:
                return new Politician();
            case SLANDERER:
                return new Slanderer();
            case MUCKRAKER:
                return new Muckraker();
        }
        return null;
    }
}