package bot;

import battlecode.common.*;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class EnlightenmentCenter extends Robot {

    static UnitBuildDPQueue pq = new UnitBuildDPQueue(3);
    static IterableIdSet exploringIds = new IterableIdSet();

    static final int LOW = 2;
    static final int MED = 1;
    static final int HIGH = 0;

    static UnitBuild nextUnit = null;
    static UnitBuild prevUnit = null;
    static Direction prevDir = null;

    static Direction[] dangerDirs = new Direction[8];
    static int dangerDirSize = 0;

    @Override
    void onAwake() throws GameActionException {
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 2, exploreMessage(dir)), MED);
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 3, exploreMessage(dir)), LOW);
        }
        pq.push(new UnitBuild(RobotType.SLANDERER, 40, defendMessage()), LOW);
        pq.push(new UnitBuild(RobotType.POLITICIAN, 50, defendMessage()), LOW);
        for(int i=3; i>0; i--) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 50, attackMessage()), LOW);
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        System.out.println("Influence: " + rc.getInfluence());

        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            if (prevUnit.message.label == Label.EXPLORE) {
                exploringIds.add(info.getID());
            }
            prevUnit = null;
        }
        // iterate through exploring robot ids
        for (int id : exploringIds.getKeys()) {
            if (!rc.canGetFlag(id)) {
                exploringIds.remove(id);
                continue;
            }
            int flag = rc.getFlag(id);
            if (flag != 0) {
                Message message = decode(flag);
                dangerDirs[dangerDirSize++] = fromOrdinal(message.data[0]);
                exploringIds.remove(id);
            }
        }
        for (int i = 0; i < dangerDirSize; i++) {
            System.out.println("DANGEROUS: " + dangerDirs[i]);
        }

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
                prevUnit = nextUnit;
                prevDir = buildDir;
                nextUnit = null;
            }
        }

        Clock.yield();

    }

    void refillQueue() throws GameActionException {
        pq.push(new UnitBuild(RobotType.SLANDERER, 40, defendMessage()), MED);
        pq.push(new UnitBuild(RobotType.POLITICIAN, 50, defendMessage()), MED);
        for(int i=3; i>0; i--) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 50, attackMessage()), LOW);
        }
    }

    Message attackMessage() throws GameActionException {
        int[] data = {};
        return new Message(Label.ATTACK, data);
    }

    Message defendMessage() throws GameActionException {
        int[] data = {};
        return new Message(Label.DEFEND, data);
    }
}
