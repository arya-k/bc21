package offensive_bot;

import battlecode.common.*;
import offensive_bot.Communication.Label;
import offensive_bot.Communication.Message;

import static offensive_bot.Communication.decode;
import static offensive_bot.Communication.encode;

public class EnlightenmentCenter extends Robot {

    static UnitBuildDPQueue pq = new UnitBuildDPQueue(3);
    static IterableIdSet exploringIds = new IterableIdSet();

    static final int LOW = 2;
    static final int MED = 1;
    static final int HIGH = 0;

    static UnitBuild nextUnit = null;
    static UnitBuild prevUnit = null;
    static Direction prevDir = null;

    static boolean[] dangerDirs = new boolean[8];

    @Override
    void onAwake() throws GameActionException {
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 2, exploreMessage(dir)), MED);
        }
    }

    @Override
    void onUpdate() throws GameActionException {

        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            switch(prevUnit.message.label) {
                case EXPLORE:
                    exploringIds.add(info.getID());
            }
            prevUnit = null;
        }

        processFlags();

        // queue the next unit to build
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
        int dangerDir = bestDangerDir();
        if (dangerDir != -1)
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 50, attackMessage(dangerDir)), LOW);
    }

    void processFlags() throws  GameActionException {
        // read the ids of the explorers
        for (int id : exploringIds.getKeys()) {
            if (!rc.canGetFlag(id)) {
                exploringIds.remove(id);
                continue;
            }
            int flag = rc.getFlag(id);

            // got a message!
            if (flag != 0) {
                Message message = decode(flag);
                if (message.label == Label.DANGER_DIR)
                    dangerDirs[message.data[0]] = true;
                exploringIds.remove(id);
            }
        }
    }

    Message attackMessage(int dangerDir) throws GameActionException {
        return new Message(Label.ATTACK, new int[]{dangerDir});
    }

    int bestDangerDir() {
        for (int i = 0; i < 8; i++) {
            if (!dangerDirs[i]) continue;
            return i;
        }
        return -1;
    }
}
