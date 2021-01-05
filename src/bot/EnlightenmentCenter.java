package bot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotType;

public class EnlightenmentCenter extends Robot {
    @Override
    void onAwake() throws GameActionException {
    }

    @Override
    void onUpdate() throws GameActionException {
        for (int i = 20; i > 0; i--) {
            if (rc.isReady() && rc.getInfluence() > 3) {
                Direction toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
                while (!rc.canBuildRobot(RobotType.MUCKRAKER, toBuild, 3)) {
                    toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
                }
                System.out.println("Building muckraker at " + rc.getLocation().add(toBuild) + "!");
                rc.buildRobot(RobotType.MUCKRAKER, toBuild, 3);
            }
            Clock.yield();
        }
        if (rc.isReady() && rc.getInfluence() > 20) {
            Direction toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
            while (!rc.canBuildRobot(RobotType.SLANDERER, toBuild, 20)) {
                toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
            }
            System.out.println("Building slanderer at " + rc.getLocation().add(toBuild) + "!");
            rc.buildRobot(RobotType.SLANDERER, toBuild, 20);
        }
    }
}
