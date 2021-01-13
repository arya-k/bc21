package refactor;

import battlecode.common.*;
import refactor.Communication.Label;
import refactor.Communication.Message;
import refactor.utils.IterableIdSet;
import refactor.utils.UnitBuild;
import refactor.utils.UnitBuildDPQueue;

import static refactor.Communication.decode;
import static refactor.Communication.encode;

public class EnlightenmentCenter extends Robot {

    // set of ids for tracking robot messages
    static IterableIdSet trackedIds = new IterableIdSet();

    // build priority queue
    static UnitBuildDPQueue pq = new UnitBuildDPQueue(5);
    static final int ULTRA_LOW = 4;
    static final int LOW = 3;
    static final int MED = 2;
    static final int HIGH = 1;
    static final int ULTRA_HIGH = 0;

    // collected information about the map
    static boolean[] dangerDirs = new boolean[8];
    static boolean[] safeDirs = new boolean[8];
    static int[] edgeOffsets = new int[8]; // only the cardinal directions matter here.
    static int[] directionOpenness = new int[8]; // lower numbers are "safer"

    // neutral ECs that have been found so far
    static MapLocation[] neutralECLocs = new MapLocation[6];
    static int[] neutralECInfluence = new int[6];
    static int neutralECFound = 0;

    // enemy ECs that have been found so far
    static MapLocation[] enemyECLocs = new MapLocation[9];
    static int[] enemyECInfluence = new int[9];
    static int enemyECFound = 0;

    @Override
    void onAwake() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            edgeOffsets[i] = 100; //default to an impossible value
            directionOpenness[i] = 200;
        }
        calcBestSpawnDirs();

        // initialize priority queue
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 1,
                    makeMessage(Label.SCOUT, dir.ordinal())), HIGH);
        }

        pq.push(new UnitBuild(RobotType.SLANDERER, 40,
                makeMessage(Label.HIDE, bestSafeDir().ordinal())), MED);

        for (int i = 5; i > 0; i--) {
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 1,
                    makeMessage(Label.EXPLORE)), MED);
        }

    }

    void lowPriorityLogging() {
        System.out.println("Influence: " + myInfluence);
        System.out.println("Production state: " + prodState);
        System.out.println("Bidding state: " + bidController.state);
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

    // tracking builds
    static UnitBuild nextUnit = null;
    static UnitBuild prevUnit = null;
    static Direction prevDir = null;
    static int slanderersBuilt = 0;

    // production state
    static State prodState = State.MuckrakerSpam;

    // bidding controller
    BidController bidController = new BidController();

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(currentLocation.add(prevDir));
            switch (prevUnit.message.label) {
                case SCOUT:
                case EXPLORE:
                    trackedIds.add(info.getID());
            }
            prevUnit = null;
        }

        processFlags();

        productionTransition();

        immediateDefense();

        if (currentRound % 100 == 0) {
            lowPriorityLogging();
        }

        // queue the next unit to build
        boolean empty = pq.isEmpty();
        if (empty)
            prodState.refillQueue();
        if (nextUnit == null && !empty)
            nextUnit = pq.pop();
        if (nextUnit != null && ((nextUnit.priority <= HIGH && nextUnit.influence <= myInfluence) ||
                nextUnit.influence + influenceMinimum() <= myInfluence) && rc.isReady()) {
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

                // tracking what we built;
                if (nextUnit.type == RobotType.SLANDERER)
                    slanderersBuilt++;

                prevUnit = nextUnit;
                prevDir = buildDir;
                nextUnit = null;
            }
        }

        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8)) {
            bidController.bid();
        }


        if (currentRound % 100 == 0) {
            lowPriorityLogging();
        }
        Clock.yield();

    }

    /* Production and Stimulus Logic */

    static void productionTransition() throws GameActionException {
        switch (prodState) {
            case MuckrakerSpam:
                for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam())) {
                    int flag = rc.getFlag(bot.getID());
                    if (flag != 0 && decode(flag).label == Label.STOP_PRODUCING_MUCKRAKERS) {
                        prodState = State.Winning;
                        break;
                    }
                }
                break;
        }
    }

    private enum State {
        MuckrakerSpam {
            @Override
            void refillQueue() {
                for (int i = 2; --i >= 0; ) {
                    pq.push(new UnitBuild(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE)), LOW);
                }
                for (int i = 0; i < enemyECFound; i++) {
                    createAttackHorde(RobotType.MUCKRAKER, 3, 1, enemyECLocs[i], LOW);
                    createAttackHorde(RobotType.POLITICIAN, 1, enemyECInfluence[i], enemyECLocs[i], MED);
                }

                if (!muckrakerNearby()) {
                    for (int i = slanderersBuilt; i < currentRound / 20; i++) {
                        pq.push(new UnitBuild(RobotType.SLANDERER, 150,
                                makeMessage(Label.HIDE, bestSafeDir().ordinal())), MED);
                    }
                }
            }
        },
        Winning {
            @Override
            void refillQueue() {
                if (!muckrakerNearby()) {
                    for (int i = slanderersBuilt; i < currentRound / 5; i++) {
                        pq.push(new UnitBuild(RobotType.SLANDERER, 150,
                                makeMessage(Label.HIDE, bestSafeDir().ordinal())), MED);
                    }
                }
            }
        };

        abstract void refillQueue();
    }

    static int exploderQueuedRound = 0;
    static boolean underAttack = false;

    void immediateDefense() {
        int enemyConviction = 0;
        int enemies = 0;
        for (RobotInfo info : rc.senseNearbyRobots(12, rc.getTeam().opponent())) {
            enemyConviction += info.getConviction();
            enemies++;
        }
        underAttack = enemies >= 4;
        if ((enemyConviction > 200 || enemies >= 4) && (currentRound - exploderQueuedRound > 50)) {
            int conv = 10 + enemyConviction * 2;
            pq.push(new UnitBuild(RobotType.POLITICIAN, conv, makeMessage(Label.EXPLODE)), ULTRA_HIGH);
            System.out.println("EXPLODER QUEUED!!!!");
            exploderQueuedRound = currentRound;
        }
    }

    void processFlags() throws GameActionException {
        for (int id : trackedIds.getKeys()) {
            if (!rc.canGetFlag(id)) {
                trackedIds.remove(id);
                continue;
            }

            int flag = rc.getFlag(id);
            if (flag == 0) continue;

            Message message = decode(flag);
            switch (message.label) {
                case ENEMY_EC:
                    MapLocation enemyECLoc = getLocFromMessage(message.data[0], message.data[1]);

                    int knownEnemyEC = -1;
                    for (int i = enemyECFound; --i >= 0; ) {
                        if (enemyECLocs[i].equals(enemyECLoc)) {
                            knownEnemyEC = i;
                            break;
                        }
                    }

                    if (knownEnemyEC == -1) {
                        Direction dangerDir = currentLocation.directionTo(enemyECLoc);
                        int influence = (int) Math.pow(2, message.data[2]);

                        dangerDirs[dangerDir.ordinal()] = true;
                        enemyECLocs[enemyECFound] = enemyECLoc;
                        enemyECInfluence[enemyECFound++] = (int) Math.pow(2, message.data[2]);


                        pq.push(new UnitBuild(RobotType.POLITICIAN, influence,
                                makeMessage(Label.ATTACK_LOC, enemyECLoc.x % 128, enemyECLoc.y % 128)), HIGH);
                        createAttackHorde(RobotType.MUCKRAKER, 9, 1, enemyECLoc, HIGH);
                    } else {
                        int influence = (int) Math.pow(2, message.data[2]);
                        enemyECInfluence[knownEnemyEC] = influence;
                    }
                    trackedIds.remove(id);
                    break;

                case SAFE_DIR_EDGE:
                    safeDirs[message.data[0]] = true;
                    edgeOffsets[message.data[1]] = message.data[2];
                    updateDirOpenness();

                    trackedIds.remove(id);
                    break;

                case NEUTRAL_EC:
                    MapLocation neutralECLoc = getLocFromMessage(message.data[0], message.data[1]);

                    int knownNeutralEC = -1;
                    for (int i = neutralECFound; --i >= 0; ) {
                        if (neutralECLocs[i].equals(neutralECLoc)) {
                            knownNeutralEC = i;
                            break;
                        }
                    }

                    if (knownNeutralEC == -1) {
                        int influence = (int) Math.pow(2, message.data[2]);
                        neutralECLocs[neutralECFound] = neutralECLoc;
                        neutralECInfluence[neutralECFound++] = influence;

                        pq.push(new UnitBuild(RobotType.POLITICIAN, influence,
                                makeMessage(
                                        Label.CAPTURE_NEUTRAL_EC,
                                        neutralECLoc.x % 128, neutralECLoc.y % 128)
                        ), ULTRA_LOW);

                        System.out.println("attacking a neutral EC @ " + neutralECLoc + " with politician of influence " + influence);
                    }
                    trackedIds.remove(id);
                    break;
            }
        }
    }

    /* Helpers and Utilites */

    static int influenceMinimum() {
        return 20 + (int) (currentRound * 0.1);
    }

    static void createAttackHorde(RobotType type, int size, int troop_influence, MapLocation attack_target, int priority) {
        int[] data = {attack_target.x % 128, attack_target.y % 128};
        Message msg = new Message(Label.ATTACK_LOC, data);
        for (int i = size; --i >= 0; ) {
            pq.push(new UnitBuild(type, troop_influence, msg), priority);
        }
    }

    static boolean muckrakerNearby() {
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER) {
                return true;
            }
        }
        return false;
    }

    /**
     * computes a random dangerous direction, or a random direction if no
     * dangerous ones exist
     *
     * @return a direction
     */
    static Direction bestDangerDir() {
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
    static Direction bestSafeDir() {
        int min = 0;
        for (int i = 1; i < 8; i++) {
            if (directionOpenness[min] > directionOpenness[i])
                min = i;
        }
        return fromOrdinal(min);
    }

    /**
     * updates directionOpenness based on new edge offset information.
     */
    static void updateDirOpenness() {
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

    static void calcBestSpawnDirs() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            spawnDirs[i] = directions[i];
        }
        for (int i = 0; i < 7; i++) {
            Direction best = spawnDirs[i];
            int besti = i;
            for (int j = i + 1; j < 8; j++) {
                Direction jd = spawnDirs[j];
                double jPass = 0;
                if (rc.onTheMap(currentLocation.add(jd)))
                    jPass = rc.sensePassability(currentLocation.add(jd));
                double bPass = 0;
                if (rc.onTheMap(currentLocation.add(best)))
                    bPass = rc.sensePassability(currentLocation.add(best));

                if (jPass > bPass) {
                    best = jd;
                    besti = j;
                }
            }
            spawnDirs[besti] = spawnDirs[i];
            spawnDirs[i] = best;
        }
    }
}
