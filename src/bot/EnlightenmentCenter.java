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

    static boolean[] dangerDirs = new boolean[8];
    static boolean[] safeDirs = new boolean[8];
    static int[] edgeOffsets = new int[8]; // only the cardinal directions matter here.
    static int[] directionOpenness = new int[8]; // lower numbers are "safer"

    @Override
    void onAwake() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            edgeOffsets[i] = 100; //default to an impossible value
            directionOpenness[i] = 200;
        }
        calcBestSpawnDirs();

        // initialize pq
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 2, scoutMessage(dir)), HIGH);
        }

        for(int i=3; i>0; i--) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 40, defendECMessage()), MED);
        }
        pq.push(new UnitBuild(RobotType.SLANDERER, 40, hideMessage()), LOW);
    }

    void lowPriorityLogging() {
        // only log low priority info every 100 rounds
        if (rc.getRoundNum() % 100 != 0) {
            return;
        }

        System.out.println("Influence: " + rc.getInfluence());
        for (int i = 0; i < 8; i++) {
            if (!dangerDirs[i]) continue;
            System.out.println("DANGEROUS: " + fromOrdinal(i));
        }
        for (Direction dir : Direction.cardinalDirections()) {
            int ord = dir.ordinal();
            if (edgeOffsets[ord] == 100) continue;
            System.out.println(dir + " offset " + edgeOffsets[ord]);
        }
        for (Direction dir : directions) {
            System.out.println("safety of " + dir + " = " + directionOpenness[dir.ordinal()]);
        }
    }

    @Override
    void onUpdate() throws GameActionException {

        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            switch(prevUnit.message.label) {
                case SCOUT:
                    exploringIds.add(info.getID());
            }
            prevUnit = null;
        }

        processFlags();

        lowPriorityLogging();

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
            for (Direction dir : spawnDirs) {
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
        for (int i = 3; --i > 0; ) {
            pq.push(new UnitBuild(RobotType.SLANDERER, 40, hideMessage()), LOW);
        }
        for (int i = 10; --i > 0; ) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 100, defendECMessage()), MED);
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
                        updateDirOpenness();
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
        int[] data = {randomFrontierDir().ordinal()};
        return new Message(Label.DEFEND, data);
    }

    Message hideMessage() {
        int[] data = {bestSafeDir().ordinal()};
        return new Message(Label.HIDE, data);
    }

    /**
     * computes a random dangerous direction, or a random direction if no
     * dangerous ones exist
     * @return a direction
     */
    Direction bestDangerDir() {
        int ix = (int) (Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            int j = (i + ix) % 8;
            if (!dangerDirs[j]) continue;
            return fromOrdinal(j);
        }
        return fromOrdinal(ix);
    }

    /**
     * returns a "safe" direction for slanderers to go. Is determined by
     * directionOpenness (i.e. it will return a direction that is near an
     * edge)
     * @return a direction
     */
    Direction bestSafeDir() {
        int min = 0;
        for (int i = 1; i < 8; i++) {
            if (directionOpenness[min] > directionOpenness[i])
                min = i;
        }
        return fromOrdinal(min);
    }

    /**
     * returns a direction on the "frontier" of the EC. skews towards
     * more dangerous and open directions
     * @return a direction
     */
    Direction randomFrontierDir() {
        int ix = (int) (Math.random() * 8);
        int best = ix;
        for (int i = 1; i < 8; i++) {
            int j = (i + ix) % 8;
            if (directionOpenness[j] > 2 * directionOpenness[best]) {
                best = j;
            } else if (3 * directionOpenness[j] > directionOpenness[best] && Math.random() < 0.3){
                best = j;
            } else if (dangerDirs[j] && Math.random() < 0.5) {
                best = j;
            }
        }
        return fromOrdinal(best);
    }

    /**
     * updates directionOpenness based on new edge offset information.
     */
    public static void updateDirOpenness() {
        directionOpenness[0] = edgeOffsets[0] + (edgeOffsets[2] + edgeOffsets[6]) / 2;
        directionOpenness[1] = edgeOffsets[0] + edgeOffsets[2];
        directionOpenness[2] = edgeOffsets[2] + (edgeOffsets[0] + edgeOffsets[4]) / 2;
        directionOpenness[3] = edgeOffsets[2] + edgeOffsets[4];
        directionOpenness[4] = edgeOffsets[4] + (edgeOffsets[2] + edgeOffsets[6]) / 2;
        directionOpenness[5] = edgeOffsets[4] + edgeOffsets[6];
        directionOpenness[6] = edgeOffsets[6] + (edgeOffsets[0] + edgeOffsets[4]) / 2;
        directionOpenness[7] = edgeOffsets[0] + edgeOffsets[6];
    }

    static Direction[] spawnDirs = new Direction[8];
    void calcBestSpawnDirs() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            spawnDirs[i] = directions[i];
        }
        for (int i = 0; i < 7; i++) {
            Direction best = spawnDirs[i];
            int besti = i;
            for (int j = i + 1; j < 8; j++) {
                Direction jd = spawnDirs[j];
                if (rc.sensePassability(rc.getLocation().add(jd)) >
                        rc.sensePassability(rc.getLocation().add(best))) {
                    best = jd;
                    besti = j;
                }
            }
            spawnDirs[besti] = spawnDirs[i];
            spawnDirs[i] = best;
        }
        for (int i = 0; i < 8; i++) {
            System.out.println(spawnDirs[i]);
        }
    }
}
