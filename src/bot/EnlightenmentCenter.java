package bot;

import bot.Communication.*;

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
        System.out.println("Starting influence: " + rc.getInfluence());
        System.out.println("Starting conviction: " + rc.getConviction());
        for (int i = 10; i > 0; i--) {
            if (rc.isReady() && rc.getInfluence() > 3) {
                Direction toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
                while (!rc.canBuildRobot(RobotType.MUCKRAKER, toBuild, 3)) {
                    toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
                }
                System.out.println("Building muckraker at " + rc.getLocation().add(toBuild) + "!");
                setExploreFlag(toBuild);
                rc.buildRobot(RobotType.MUCKRAKER, toBuild, 3);
            }
            Clock.yield();
        }
        System.out.println("Influence after all muckrakers: " + rc.getInfluence());
        System.out.println("Conviction after all muckrakers: " + rc.getConviction());
        while (!(rc.isReady() && rc.getInfluence() > 50)) {
            Clock.yield();
        }
        Direction toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
        while (!rc.canBuildRobot(RobotType.POLITICIAN, toBuild, 50)) {
            toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
        }
        System.out.println("Building politician at " + rc.getLocation().add(toBuild) + "!");
        rc.buildRobot(RobotType.POLITICIAN, toBuild, 50);

        while (!(rc.isReady() && rc.getInfluence() > 40)) {
            Clock.yield();
        }
        toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
        while (!rc.canBuildRobot(RobotType.SLANDERER, toBuild, 40)) {
            toBuild = Direction.allDirections()[(int) (Math.random() * 8)];
        }
        System.out.println("Building slanderer at " + rc.getLocation().add(toBuild) + "!");
        rc.buildRobot(RobotType.SLANDERER, toBuild, 40);

    }

    void setExploreFlag(Direction dir) throws GameActionException {
        int[] data = {dir.ordinal()};
        int flag = Communication.encode(new Message(Label.EXPLORE, data));
        this.rc.setFlag(flag);
    }
}
