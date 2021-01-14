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
    static IterableIdSet neutralCapturers = new IterableIdSet();

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

    static boolean enemyECNearby = false;
    static boolean addedExplode = false;
    int lastDefender = -1;

    // good influences to build slanderers at
    static int[] slandererInfluences = {41, 107, 178, 399, 605, 949};

    @Override
    void onAwake() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            edgeOffsets[i] = 100; //default to an impossible value
            directionOpenness[i] = 200;
        }
        calcBestSpawnDirs();

        RobotInfo[] nearby = rc.senseNearbyRobots();
        Team enemyTeam = rc.getTeam().opponent();
        for (RobotInfo bot : nearby) {
            if (bot.getType() == RobotType.ENLIGHTENMENT_CENTER && bot.getTeam() == enemyTeam) {
                enemyECInfluence[enemyECFound] = bot.getInfluence();
                enemyECLocs[enemyECFound++] = bot.getLocation();
                enemyECNearby = true;
            } else if (bot.getType() == RobotType.ENLIGHTENMENT_CENTER && bot.getTeam() == Team.NEUTRAL) {
                neutralECInfluence[neutralECFound] = bot.getInfluence() + GameConstants.EMPOWER_TAX * 2;
                neutralECLocs[neutralECFound++] = bot.getLocation();
            }
        }

        // initialize priority queue
        for (Direction dir : Robot.directions) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 1,
                    makeMessage(Label.SCOUT, dir.ordinal())), HIGH);
            pq.push(new UnitBuild(RobotType.POLITICIAN, 18, makeMessage(Label.DEFEND, dir.ordinal())), MED);
        }

        pq.push(new UnitBuild(RobotType.SLANDERER, 41, makeMessage(Label.HIDE)), ULTRA_HIGH);

        for (int i = 5; --i >= 0; ) {
            pq.push(new UnitBuild(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE)), LOW);
        }

    }

    void lowPriorityLogging() {
        System.out.println("Influence: " + myInfluence);
        System.out.println("Production state: " + state);
        System.out.println("Bidding state: " + BidController.state);
        for (int i = 0; i < 8; i++) {
            if (!dangerDirs[i]) continue;
            System.out.println("DANGEROUS: " + fromOrdinal(i));
        }
//        for (Direction dir : Direction.cardinalDirections()) {
//            int ord = dir.ordinal();
//            if (edgeOffsets[ord] == 100) continue;
//            System.out.println(dir + " offset " + edgeOffsets[ord]);
//        }
//        for (Direction dir : directions) {
//            System.out.println("safety of " + dir + " = " + directionOpenness[dir.ordinal()]);
//        }
        for (int i = 0; i < neutralECFound; i++) {
            System.out.println("neutral EC @ " + neutralECLocs[i] + " with inf = " + neutralECInfluence[i]);
        }
        if (!pq.isEmpty()) {
            UnitBuild next = pq.peek();
            System.out.println("Next unit: " + next.type + " " + next.priority + " " + next.influence);
        } else {
            System.out.println("PQ empty");
        }

    }

    // tracking builds
    static UnitBuild nextUnit = null;
    static UnitBuild prevUnit = null;
    static Direction prevDir = null;

    // production state
    static State state = State.CaptureNeutral;

    // bidding controller
    BidController bidController = new BidController();

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        // System.out.println("Currently in state " + state);
        // get the id of the previously build unit
        if (prevUnit != null) {
            RobotInfo info = rc.senseRobotAtLocation(currentLocation.add(prevDir));
            if (info != null) {
                switch (prevUnit.message.label) {
                    case SCOUT:
                    case EXPLORE:
                        trackedIds.add(info.getID());
                        break;
                    case CAPTURE_NEUTRAL_EC:
                        neutralCapturers.add(info.getID());
                        break;
                    case EXPLODE:
                        lastDefender = info.getID();
                        break;
                }
            }
            prevUnit = null;
        }

        processFlags();

        transition();

        immediateDefense();

        if (currentRound % 25 == 0) {
            lowPriorityLogging();
        }

        // queue the next unit to build
        boolean empty = pq.isEmpty();
        if (empty)
            state.refillQueue();
        if (nextUnit == null && !empty)
            nextUnit = pq.pop();
        myInfluence = rc.getInfluence();
        if (nextUnit != null && ((nextUnit.priority <= HIGH && nextUnit.influence <= myInfluence) ||
                nextUnit.influence + influenceMinimum() <= myInfluence) && rc.isReady()) {
            // build a unit
            Direction buildDir = null;
            if (nextUnit.message.label == Label.EXPLODE) {
                for (int i = spawnDirs.length - 1; i >= 0; i--) {
                    if (rc.canBuildRobot(nextUnit.type, spawnDirs[i], nextUnit.influence)) {
                        buildDir = spawnDirs[i];
                        break;
                    }
                }
                addedExplode = false;
            } else {
                for (int i = 0; i < spawnDirs.length; i++) {
                    if (rc.canBuildRobot(nextUnit.type, spawnDirs[i], nextUnit.influence)) {
                        buildDir = spawnDirs[i];
                        break;
                    }
                }
            }
            if (buildDir != null) {
                boolean skipCurrent = false;
                if (nextUnit.type == RobotType.SLANDERER) {
                    nextUnit.influence = getSlandererInfluence();
                    if (muckrakerNearby())
                        skipCurrent = true;
                }
                if (nextUnit.influence < 0)
                    skipCurrent = true;
                if (!skipCurrent) {
                    rc.setFlag(encode(nextUnit.message));
                    rc.buildRobot(nextUnit.type, buildDir, nextUnit.influence);


                    prevUnit = nextUnit;
                    prevDir = buildDir;
                }
                nextUnit = null;
            }
        }

        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8)) {
            bidController.bid();
        }


        if (currentRound % 25 == 0) {
            lowPriorityLogging();
        }
        Clock.yield();

    }

    public static int getSlandererInfluence() {
        int useInfluence = rc.getInfluence() - influenceMinimum();
        if (useInfluence < slandererInfluences[0]) return -1;
        for (int i = 0; i < slandererInfluences.length - 1; i++) {
            if (useInfluence < slandererInfluences[i + 1])
                return slandererInfluences[i];
        }
        return slandererInfluences[slandererInfluences.length - 1];
    }

    static int getWeakestEnemyEC() {
        if (enemyECFound <= 0) return -1;
        int weakest = 0;
        for (int i = 1; i < enemyECFound; i++) {
            if (enemyECInfluence[weakest] > enemyECInfluence[i])
                weakest = i;
        }
        return weakest;
    }

    /* Production and Stimulus Logic */

    static void transition() throws GameActionException {
        int weakest;
        switch (state) {
            case Defend:
                if (pq.isEmpty()) {
                    weakest = getWeakestEnemyEC();
                    if (weakest != -1 && rc.getInfluence() - enemyECInfluence[weakest] > 1000) {
                        state = State.AttackEnemy;
                    } else if (neutralECFound > 0 && 2 * rc.getInfluence() - neutralECInfluence[getBestNeutralEC()] > influenceMinimum()) {
                        state = State.CaptureNeutral;
                    } else if (bestDangerDir() == null) {
                        state = State.SlandererEconomy;
                    }
                }
                break;
            case CaptureNeutral:
                if (neutralECFound <= 0) {
                    state = State.Defend;
                    return;
                }
                int[] ids = neutralCapturers.getKeys();
                if (ids.length == 0) break;
                boolean stillCapturing = false;
                for (int id : neutralCapturers.getKeys()) {
                    if (rc.canGetFlag(id)) {
                        int flag = rc.getFlag(id);
                        if (flag != 0 && decode(flag).label == Label.CAPTURE_NEUTRAL_EC) {
                            stillCapturing = true;
                        } else {
                            neutralCapturers.remove(id);
                        }
                    } else {
                        neutralCapturers.remove(id);
                    }
                }
                if (!stillCapturing) {
                    int closest = getBestNeutralEC();
                    neutralECFound--;
                    neutralECLocs[closest] = neutralECLocs[neutralECFound];
                    neutralECInfluence[closest] = neutralECInfluence[neutralECFound];
                    state = State.Defend;
                    pq.clear();
                }
                break;
            case AttackEnemy:
                weakest = getWeakestEnemyEC();
                if (weakest == -1 || rc.getInfluence() - enemyECInfluence[weakest] < 500) {
                    state = State.Defend;
                }
                break;
            case SlandererEconomy:
                if (pq.isEmpty()) {
                    weakest = getWeakestEnemyEC();
                    if (weakest != -1 && rc.getInfluence() - enemyECInfluence[weakest] > 1000) {
                        state = State.AttackEnemy;
                    } else if (bestDangerDir() != null) {
                        state = State.Defend;
                    } else if (neutralECFound > 0 && 2 * rc.getInfluence() - neutralECInfluence[getBestNeutralEC()] > influenceMinimum()) {
                        state = State.CaptureNeutral;
                    }
                }
                break;
        }
    }

    static int getBestNeutralEC() {
        if (neutralECFound <= 0) return -1;
        int best = 0;
        for (int i = 1; i < neutralECFound; i++) {
            MapLocation newLoc = neutralECLocs[i];
            MapLocation oldLoc = neutralECLocs[best];
            int newDist = newLoc.distanceSquaredTo(currentLocation);
            int oldDist = oldLoc.distanceSquaredTo(currentLocation);
            int newInf = neutralECInfluence[i];
            int oldInf = neutralECInfluence[best];
            if (newDist + newInf < oldDist + oldInf) {
                best = i;
            }
        }
        return best;
    }


    private enum State {
        Defend {
            @Override
            void refillQueue() throws GameActionException {
                Direction dangerDir = bestDangerDir();
                if (dangerDir != null) {
                    System.out.println("Defending in direction " + dangerDir);
                    int required = dangerDirs[dangerDir.ordinal()] ? 3 : 1;
                    for (int i = required; --i >= 0; ) {
                        pq.push(new UnitBuild(RobotType.POLITICIAN, 18,
                                makeMessage(Label.DEFEND, dangerDir.ordinal())), MED);
                    }
                    Direction dir1 = dangerDir.rotateLeft();
                    Direction dir2 = dangerDir.rotateRight();
                    for (int i = required - 2; --i >= 0; ) {
                        pq.push(new UnitBuild(RobotType.POLITICIAN, 18,
                                makeMessage(Label.DEFEND, dir1.ordinal())), MED);
                        pq.push(new UnitBuild(RobotType.POLITICIAN, 18,
                                makeMessage(Label.DEFEND, dir2.ordinal())), MED);
                    }
                    int influence = getSlandererInfluence();
                    if (influence > 0)
                        pq.push(new UnitBuild(RobotType.SLANDERER, influence, makeMessage(Label.HIDE)), MED);
                }
            }
        },
        CaptureNeutral {
            @Override
            void refillQueue() {
                System.out.println("number neutral found: " + neutralECFound);
                if (neutralECFound > 0) {
                    int closest = getBestNeutralEC();
                    int influence = neutralECInfluence[closest];
                    MapLocation best = neutralECLocs[closest];
                    pq.push(new UnitBuild(RobotType.POLITICIAN, influence + GameConstants.EMPOWER_TAX,
                            makeMessage(Label.CAPTURE_NEUTRAL_EC, best.x % 128, best.y % 128)), MED);
                }
            }
        },
        SlandererEconomy {
            @Override
            void refillQueue() {
                int influence = getSlandererInfluence();
                if (influence > 0) {
                    pq.push(new UnitBuild(RobotType.SLANDERER, influence, makeMessage(Label.HIDE)), MED);
                    for (int i = 5; i > 0; i--)
                        pq.push(new UnitBuild(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE)), LOW);
                }
            }
        },
        AttackEnemy {
            @Override
            void refillQueue() {
                if (enemyECFound > 0) {
                    int closest = 0;
                    for (int i = 1; i < enemyECFound; i++) {
                        MapLocation loc = enemyECLocs[i];
                        MapLocation curr = enemyECLocs[closest];
                        int newDist = loc.distanceSquaredTo(currentLocation);
                        int oldDist = curr.distanceSquaredTo(currentLocation);
                        if (oldDist > newDist) {
                            closest = i;
                        }
                    }
                    int influence = enemyECInfluence[closest];
                    MapLocation best = enemyECLocs[closest];
                    pq.push(new UnitBuild(RobotType.POLITICIAN,
                            influence + GameConstants.EMPOWER_TAX,
                            makeMessage(Label.ATTACK_LOC, best.x % 128, best.y % 128)
                    ), MED);
                }
            }
        };

        abstract void refillQueue() throws GameActionException;
    }

    static boolean underAttack = false;

    void immediateDefense() throws GameActionException {
        if (addedExplode) return;
        if (lastDefender == -1 || !rc.canGetFlag(lastDefender)) {
            pq.push(new UnitBuild(RobotType.POLITICIAN, 40, makeMessage(Label.EXPLODE)), ULTRA_HIGH);
            addedExplode = true;
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


//                        pq.push(new UnitBuild(RobotType.POLITICIAN, influence,
//                                makeMessage(Label.ATTACK_LOC, enemyECLoc.x % 128, enemyECLoc.y % 128)), HIGH);
//                        createAttackHorde(RobotType.MUCKRAKER, 9, 1, enemyECLoc, HIGH);
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
                    System.out.println("Found Neutral EC @ " + neutralECLoc);

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
                    } else {
                        int influence = (int) Math.pow(2, message.data[2]);
                        neutralECInfluence[knownNeutralEC] = influence;
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
        for (RobotInfo bot : rc.senseNearbyRobots(24)) {
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
    static Direction bestDangerDirHelper() {
        int ix = (int) (Math.random() * 8);
        for (int i = 0; i < 8; i++) {
            int j = (i + ix) % 8;
            if (!dangerDirs[j]) continue;
            return fromOrdinal(j);
        }
        return fromOrdinal(ix);
    }

    static Direction bestDangerDir() throws GameActionException {
        Direction dangerDir = bestDangerDirHelper();
        int[] defenders_in = {0, 0, 0, 0, 0, 0, 0, 0};
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() == rc.getTeam() && rc.canGetFlag(bot.getID())) {
                int flag = rc.getFlag(bot.getID());
                if (flag != 0 && decode(flag).label == Label.DEFEND) {
                    defenders_in[currentLocation.directionTo(bot.getLocation()).ordinal()]++;
                }
            }
        }
        boolean sufficient_defense = true;
        for (int i = 0; i < 8; i++) {
            boolean isDangerous = dangerDirs[i];
            int required = isDangerous ? 4 : 0;
            if (defenders_in[i] < required)
                sufficient_defense = false;
        }
        if (sufficient_defense) return null;
        return dangerDir;
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
        currentLocation = rc.getLocation();
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
