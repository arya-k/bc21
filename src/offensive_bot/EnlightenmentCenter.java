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
    static boolean[] safeDirs = new boolean[8];
    static int[] edgeOffsets = new int[8]; // only the cardinal directions matter here.
    static int[] directionSafety = new int[8]; // lower numbers are "safer"

    @Override
    void onAwake() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            edgeOffsets[i] = 100; //default to an impossible value
            directionSafety[i] = 200;
        }
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 2, exploreMessage(dir)), MED);
        }
        for(int i=3; i>0; i--) {
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 10, attackMessage()), LOW);
            pq.push(new UnitBuild(RobotType.POLITICIAN, 50, attackMessage()), LOW);
        }
        for (int i = 10; --i > 0; ) {
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 5, hideMessage()), LOW);
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
        switch(rc.getRoundNum() % 50) {
            case 0:
                for (int i = 3; --i > 0; ) {
                    pq.push(new UnitBuild(RobotType.SLANDERER, 40, hideMessage()), MED);
                }
                break;
            default:
                pq.push(new UnitBuild(RobotType.MUCKRAKER, 5, attackMessage()), LOW);
                break;
        }
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
                switch (message.label) {
                    case DANGER_DIR:
                        dangerDirs[message.data[0]] = true;
                        break;
                    case SAFE_DIR_EDGE:
                        safeDirs[message.data[0]] = true;
                        edgeOffsets[message.data[1]] = message.data[2];
                        updateDirSafety(message.data[1], message.data[2]);
                        break;
                }
                exploringIds.remove(id);
            }
        }
    }

    Message attackMessage() throws GameActionException {
        int[] data = {bestDangerDir().ordinal()};
        return new Message(Label.ATTACK, data);
    }

    Message defendECMessage() throws GameActionException {
        int[] data = {bestDangerDir().ordinal()};
        return new Message(Label.DEFEND, data);
    }

    Message hideMessage() {
        int[] data = {bestSafeDir().ordinal()};
        return new Message(Label.HIDE, data);
    }

    Direction bestDangerDir() {
        int ix = (int) (Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            int j = (i + ix) % 8;
            if (!dangerDirs[j]) continue;
            return fromOrdinal(j);
        }
        return fromOrdinal(ix);
    }

    Direction bestSafeDir() {
        int min = 0;
        for (int i = 1; i < 8; i++) {
            if (directionSafety[min] > directionSafety[i])
                min = i;
        }
        return fromOrdinal(min);
    }

    public static void updateDirSafety(int dir, int offset) {
        directionSafety[0] = edgeOffsets[0] + (edgeOffsets[2] + edgeOffsets[6]) / 2;
        directionSafety[1] = edgeOffsets[0] + edgeOffsets[2];
        directionSafety[2] = edgeOffsets[2] + (edgeOffsets[0] + edgeOffsets[4]) / 2;
        directionSafety[3] = edgeOffsets[2] + edgeOffsets[4];
        directionSafety[4] = edgeOffsets[4] + (edgeOffsets[2] + edgeOffsets[6]) / 2;
        directionSafety[5] = edgeOffsets[4] + edgeOffsets[6];
        directionSafety[6] = edgeOffsets[6] + (edgeOffsets[0] + edgeOffsets[4]) / 2;
        directionSafety[7] = edgeOffsets[0] + edgeOffsets[6];
    }
}
