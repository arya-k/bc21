package bot;

import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotType;

public class EnlightenmentCenter extends Robot {

    static UnitBuildDPQueue pq = new UnitBuildDPQueue(3);
    static final int LOW = 2;
    static final int MED = 1;
    static final int HIGH = 0;

    static UnitBuild nextUnit = null;



    @Override
    void onAwake() throws GameActionException {
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 2, exploreMessage(dir)), LOW);
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        System.out.println("Influence: " + rc.getInfluence());
        boolean empty = pq.isEmpty();
        if (empty) {
            refillQueue();
        }
        if (nextUnit == null && !empty) {
            nextUnit = pq.pop();
        }

        if (nextUnit != null && nextUnit.influence <= rc.getInfluence() && rc.isReady()) {
            // build a unit
            System.out.println("Trying to build a " + nextUnit.type);
            Direction buildDir = null;
            for (Direction dir : Robot.directions) {
                if (rc.canBuildRobot(nextUnit.type, dir, nextUnit.influence)) {
                    buildDir = dir;
                    break;
                }
            }
            if (buildDir != null) {
                rc.setFlag(encode(nextUnit.message));
                rc.buildRobot(nextUnit.type, buildDir, nextUnit.influence);
                nextUnit = null;
            }
        }

        Clock.yield();

    }

    void refillQueue() {
        return;
    }

    Message exploreMessage(Direction dir) throws GameActionException {
        int[] data = {dir.ordinal()};
        return new Message(Label.EXPLORE, data);
    }
}
