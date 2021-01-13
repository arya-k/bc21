package sprint_plus;

import battlecode.common.*;
import sprint_plus.Communication.Label;
import sprint_plus.Communication.Message;

import static sprint_plus.Communication.decode;
import static sprint_plus.Communication.encode;

public class EnlightenmentCenter extends Robot {

    static UnitBuildDPQueue pq = new UnitBuildDPQueue(5);
    static IterableIdSet exploringIds = new IterableIdSet();

    static final int ULTRA_LOW = 4;
    static final int LOW = 3;
    static final int MED = 2;
    static final int HIGH = 1;
    static final int ULTRA_HIGH = 0;

    static UnitBuild nextUnit = null;
    static UnitBuild prevUnit = null;
    static Direction prevDir = null;

    static boolean[] dangerDirs = new boolean[8];
    static boolean[] safeDirs = new boolean[8];
    static int[] edgeOffsets = new int[8]; // only the cardinal directions matter here.
    static int[] directionOpenness = new int[8]; // lower numbers are "safer"
    static MapLocation[] neutralECLocs = new MapLocation[6];
    static int[] neutralECInfluence = new int[6];
    static int neutralECFound = 0;
    static MapLocation[] enemyECLocs = new MapLocation[9];
    static int[] enemyECInfluence = new int[9];
    static int enemyECFound = 0;

    static int slanderersBuilt = 0;
    static boolean keepProducingMuckrakers = true;


    @Override
    void onAwake() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            edgeOffsets[i] = 100; //default to an impossible value
            directionOpenness[i] = 200;
        }
        calcBestSpawnDirs();

        // initialize pq
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 1, scoutMessage(dir)), HIGH);
        }

        pq.push(new UnitBuild(RobotType.SLANDERER, 40, hideMessage()), MED);

        for (int i = 5; i > 0; i--) {
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 1, exploreMessage()), MED);
        }

    }

    void lowPriorityLogging() {
        // only log low priority info every 100 rounds
        if (rc.getRoundNum() % 100 != 0) {
            return;
        }

        // System.out.println("Influence: " + rc.getInfluence());
        // System.out.println("Producing Muckrakers: " + keepProducingMuckrakers);
        for (int i = 0; i < 8; i++) {
            if (!dangerDirs[i]) continue;
            // System.out.println("DANGEROUS: " + fromOrdinal(i));
        }
        for (Direction dir : Direction.cardinalDirections()) {
            int ord = dir.ordinal();
            if (edgeOffsets[ord] == 100) continue;
            // System.out.println(dir + " offset " + edgeOffsets[ord]);
        }
        for (Direction dir : directions) {
            // System.out.println("safety of " + dir + " = " + directionOpenness[dir.ordinal()]);
        }
    }

    @Override
    void onUpdate() throws GameActionException {

        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(prevDir));
            switch (prevUnit.message.label) {
                case SCOUT:
                case EXPLORE:
                    exploringIds.add(info.getID());
            }
            prevUnit = null;
        }

        processFlags();
        immediateDefense();

        lowPriorityLogging();

        // queue the next unit to build
        int max = 0;
        for (int i = 0; i < neutralECFound; i++) {
            max = Math.max(neutralECInfluence[i], max);
        }
        boolean empty = pq.isEmpty() || (pq.index == ULTRA_LOW && rc.getRoundNum() < max);
        if (empty) {
            refillQueue();
        }
        if (nextUnit == null && !empty) {
            nextUnit = pq.pop();
        }

        if (nextUnit != null && ((nextUnit.priority <= HIGH && nextUnit.influence <= rc.getInfluence()) ||
                nextUnit.influence + influenceMinimum() <= rc.getInfluence()) && rc.isReady()) {
            // build a unit
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
                if (nextUnit.type == RobotType.SLANDERER)
                    slanderersBuilt++;
                prevUnit = nextUnit;
                prevDir = buildDir;
                nextUnit = null;
            }
        }

        makeBid();
        Clock.yield();

    }

    void refillQueue() throws GameActionException {
        if (keepProducingMuckrakers) {
            for (int i = 2; --i >= 0; ) {
                pq.push(new UnitBuild(RobotType.MUCKRAKER, 1, exploreMessage()), LOW);
            }
            for (int i = 0; i < enemyECFound; i++) {
                createAttackHorde(RobotType.MUCKRAKER, 3, 1, enemyECLocs[i], LOW);
                createAttackHorde(RobotType.POLITICIAN, 1, enemyECInfluence[i], enemyECLocs[i], MED);
            }
        }

        int threshold = rc.getRoundNum() / 20;
        if (!keepProducingMuckrakers) {
            threshold = rc.getRoundNum() / 5;
        }
        boolean muckrakerFound = false;
        Team myTeam = rc.getTeam();
        for (RobotInfo bot : rc.senseNearbyRobots()) {
            if (bot.getTeam() != myTeam && bot.getType() == RobotType.MUCKRAKER) {
                muckrakerFound = true;
                break;
            }
        }
        if (!muckrakerFound) {
            for (int i = slanderersBuilt; i < threshold; i++) {
                pq.push(new UnitBuild(RobotType.SLANDERER, 150, hideMessage()), MED);
            }
        }

    }

    static int exploderQueuedRound = 0;
    static boolean haltBidding = false;

    void immediateDefense() {
        int enemyConviction = 0;
        int enemies = 0;
        for (RobotInfo info : rc.senseNearbyRobots(12, rc.getTeam().opponent())) {
            enemyConviction += info.getConviction();
            enemies++;
        }
        haltBidding = enemies >= 4;
        if ((enemyConviction > 200 || enemies >= 4) && (rc.getRoundNum() - exploderQueuedRound > 50)) {
            int conv = 10 + enemyConviction * 2;
            pq.push(new UnitBuild(RobotType.POLITICIAN, conv, explodeMessage()), ULTRA_HIGH);
            // System.out.println("EXPLODER QUEUED!!!!");
            exploderQueuedRound = rc.getRoundNum();
        }
    }

    void processFlags() throws GameActionException {
        if (keepProducingMuckrakers) {
            for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam())) {
                int flag = rc.getFlag(bot.getID());
                if (flag != 0 && decode(flag).label == Label.STOP_PRODUCING_MUCKRAKERS) {
                    // System.out.println("STOPPING PRODUCING MUCKRAKERS!");
                    keepProducingMuckrakers = false;
                    break;
                }
            }
        }
        // read the ids of the explorers
        for (int id : exploringIds.getKeys()) {
            if (!rc.canGetFlag(id)) {
                exploringIds.remove(id);
                continue;
            }
            int flag = rc.getFlag(id);
            // got a message!
            if (flag != 0) {
                int known = -1;
                Message message = decode(flag);
                switch (message.label) {
                    case ENEMY_EC:
                        MapLocation enemy_loc = getLocFromMessage(message.data[0], message.data[1]);
                        for (int i = enemyECFound; --i >= 0; ) {
                            if (enemyECLocs[i].equals(enemy_loc)) {
                                known = i;
                                break;
                            }
                        }
                        if (known == -1) {
                            enemyECLocs[enemyECFound] = enemy_loc;
                            createAttackHorde(RobotType.MUCKRAKER, 9, 1, enemy_loc, HIGH);
                            Direction dangerDir = rc.getLocation().directionTo(enemy_loc);
                            int dangerOrd = dangerDir.ordinal();
                            dangerDirs[dangerOrd] = true;
                            int influence = (int) Math.pow(2, message.data[2]);
                            enemyECInfluence[enemyECFound++] = influence;
                            int[] data = {enemy_loc.x % 128, enemy_loc.y % 128};
                            pq.push(new UnitBuild(RobotType.POLITICIAN, influence, new Message(Label.ATTACK_LOC, data)), HIGH);

                            // probably make this next line better
//                            int eastWest = (dangerOrd / 2) % 2;
//                            MapLocation wallCenter = rc.getLocation().translate(dangerDir.dx * 5, dangerDir.dy * 5);
//                            int wallSize = 8;
//                            for (int i = 0; i < wallSize; i++) {
//                                int[] data = {wallCenter.x % 128, wallCenter.y % 128, i, eastWest};
//                                Message msg = new Message(Label.FORM_WALL, data);
//                                pq.push(new UnitBuild(RobotType.MUCKRAKER, 15, msg), HIGH);
//                            }
                        } else {
                            int influence = (int) Math.pow(2, message.data[2]);
                            enemyECInfluence[known] = influence;
                        }
                        exploringIds.remove(id);
                        break;
                    case SAFE_DIR_EDGE:
                        safeDirs[message.data[0]] = true;
                        edgeOffsets[message.data[1]] = message.data[2];
                        updateDirOpenness();
                        exploringIds.remove(id);
                        break;
                    case NEUTRAL_EC:
                        MapLocation neutral_ec_loc = getLocFromMessage(message.data[0], message.data[1]);
                        for (int i = neutralECFound; --i >= 0; ) {
                            if (neutralECLocs[i].equals(neutral_ec_loc)) {
                                known = i;
                                break;
                            }
                        }
                        if (known == -1) {
                            neutralECLocs[neutralECFound] = neutral_ec_loc;
                            int influence = (int) Math.pow(2, message.data[2]);
                            neutralECInfluence[neutralECFound++] = influence;
                            int[] data = {neutral_ec_loc.x % 128, neutral_ec_loc.y % 128};
                            pq.push(new UnitBuild(RobotType.POLITICIAN, influence, new Message(Label.CAPTURE_NEUTRAL_EC, data)), ULTRA_LOW);
                            // System.out.println("attacking a neutral EC @ " + neutral_ec_loc + " with politician of influence " + influence);
                        }
                        exploringIds.remove(id);
                        break;
                }

            }
        }
    }

    void createAttackHorde(RobotType type, int size, int troop_influence, MapLocation attack_target, int priority) throws GameActionException {
        int[] data = {attack_target.x % 128, attack_target.y % 128};
        Message msg = new Message(Label.ATTACK_LOC, data);
        for (int i = size; --i >= 0; ) {
            pq.push(new UnitBuild(type, troop_influence, msg), priority);
        }
    }

    void createAttackHorde(RobotType type, int size, int troop_influence, MapLocation attack_target) throws GameActionException {
        createAttackHorde(type, size, troop_influence, attack_target, MED);
    }

    Message defendECMessage() throws GameActionException {
        int[] data = {randomFrontierDir().ordinal()};
        return new Message(Label.DEFEND, data);
    }

    Message hideMessage() {
        int[] data = {bestSafeDir().ordinal()};
        return new Message(Label.HIDE, data);
    }

    Message exploreMessage() {
        int[] data = {};
        return new Message(Label.EXPLORE, data);
    }

    Message explodeMessage() {
        int[] data = {};
        return new Message(Label.EXPLODE, data);
    }

    /**
     * computes a random dangerous direction, or a random direction if no
     * dangerous ones exist
     *
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
     *
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
     *
     * @return a direction
     */
    Direction randomFrontierDir() {
        int ix = (int) (Math.random() * 8);
        int best = ix;
        for (int i = 1; i < 8; i++) {
            int j = (i + ix) % 8;
            if (directionOpenness[j] > 2 * directionOpenness[best]) {
                best = j;
            } else if (3 * directionOpenness[j] > directionOpenness[best] && Math.random() < 0.3) {
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
                double jPass = 0;
                if (rc.onTheMap(rc.getLocation().add(jd)))
                    jPass = rc.sensePassability(rc.getLocation().add(jd));
                double bPass = 0;
                if (rc.onTheMap(rc.getLocation().add(best)))
                    bPass = rc.sensePassability(rc.getLocation().add(best));

                if (jPass > bPass) {
                    best = jd;
                    besti = j;
                }
            }
            spawnDirs[besti] = spawnDirs[i];
            spawnDirs[i] = best;
        }
        for (int i = 0; i < 8; i++) {
            // System.out.println(spawnDirs[i]);
        }
    }

    int influenceMinimum() {
        return 20 + (int) (rc.getRoundNum() * 0.1);
    }

    int maxBid() {
        return Math.max(Math.min(rc.getInfluence() - 2*influenceMinimum(), rc.getInfluence() / 5), 0);
    }

    static int prevTeamVotes = 0;
    static int prevBid = 2;
    static int lostRounds = 0;
    static int bidlessBreak = 0;

    void makeBid() throws GameActionException {
        if (rc.getRoundNum() == 2999)
            rc.bid(rc.getInfluence());
        if (bidlessBreak > 0) {
            bidlessBreak--;
            return;
        }
        if (haltBidding && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length != 8) {
            return;
        }

        int bid = prevBid;
        if (rc.getRoundNum() != 0 && rc.getTeamVotes() == prevTeamVotes) {
            // we lost the last vote!
            lostRounds++;
            if (lostRounds % 3 == 0 && rc.getRoundNum() < 1500) {
                // take a break :(
                bidlessBreak = 50;
                return;
            }
            if (rc.getRoundNum() > 2500 &&
                    (rc.getInfluence() > 3*influenceMinimum()) &&
                    (1.0 * rc.getTeamVotes() / rc.getRoundNum() < 0.2 && rc.getRoundNum() > 700)) {
                bid = (bid * 3) / 2;
            } else {
                bid += (int) (Math.random() * 4) + 1;
            }
        } else {
            lostRounds = 0;
        }
        bid = Math.min(bid, maxBid());
        prevBid = bid;
        prevTeamVotes = rc.getTeamVotes();
        if (bid != 0) {
            rc.bid(bid);
        }

    }
}
