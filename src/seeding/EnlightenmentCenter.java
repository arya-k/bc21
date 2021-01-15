package seeding;

import battlecode.common.*;
import seeding.Communication.Label;
import seeding.Communication.Message;
import seeding.utils.IterableIdSet;

import static seeding.Communication.decode;
import static seeding.QueueController.*;

public class EnlightenmentCenter extends Robot {

    // set of ids for tracking robot messages
    static IterableIdSet trackedIds = new IterableIdSet(); // NOTE: Shared with QueueController
    static IterableIdSet neutralCapturers = new IterableIdSet(); // NOTE: Shared with QueueController
    static boolean addedFinalDefender = false;  // NOTE: Shared with QueueController
    static int lastDefender = -1; // NOTE: Shared with QueueController

    // build priority queue
    static QueueController qc = new QueueController();

    // production state
    static State state = State.CaptureNeutral;

    // bidding controller
    BidController bidController = new BidController();

    // collected information about the map
    static boolean[] dangerDirs = new boolean[8];

    // neutral ECs that have been found so far
    static MapLocation[] neutralECLocs = new MapLocation[6];
    static int[] neutralECInfluence = new int[6];
    static int neutralECFound = 0;

    // enemy ECs that have been found so far
    static MapLocation[] enemyECLocs = new MapLocation[9];
    static int[] enemyECInfluence = new int[9];
    static int enemyECFound = 0;

    static boolean enemyECNearby = false;

    @Override
    void onAwake() throws GameActionException {
        qc.init(); // Initialize the queue controller!

        for (RobotInfo bot : rc.senseNearbyRobots()) { // Find nearby enlightenment centers
            if (bot.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;
            if (bot.getTeam() == rc.getTeam().opponent()) {
                enemyECInfluence[enemyECFound] = bot.getInfluence();
                enemyECLocs[enemyECFound++] = bot.getLocation();
                enemyECNearby = true;
            } else if (bot.getTeam() == Team.NEUTRAL) {
                neutralECInfluence[neutralECFound] = bot.getInfluence();
                neutralECLocs[neutralECFound++] = bot.getLocation();
            }
        }

        // initialize priority queue
        for (Direction dir : Robot.directions) {
            qc.push(RobotType.POLITICIAN, 1, makeMessage(Label.SCOUT, dir.ordinal()), HIGH); // Scout politician
            qc.push(RobotType.POLITICIAN, 18, makeMessage(Label.DEFEND, dir.ordinal()), MED); // Defense politician
        }
        qc.push(RobotType.SLANDERER, 41, makeMessage(Label.HIDE), ULTRA_HIGH); // Econ slanderer
        qc.pushMany(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE), LOW, 5); // Exploring muckrakers
    }

    void lowPriorityLogging() {
        System.out.println("Influence: " + myInfluence);
        System.out.println("Production state: " + state);
        System.out.println("Bidding state: " + BidController.state);
        for (int i = 0; i < 8; i++) {
            if (!dangerDirs[i]) continue;
            System.out.println("DANGEROUS: " + fromOrdinal(i));
        }
        for (int i = 0; i < neutralECFound; i++) {
            System.out.println("neutral EC @ " + neutralECLocs[i] + " with inf = " + neutralECInfluence[i]);
        }
        qc.logNext();
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        qc.trackLastBuiltUnit();

        processFlags(); // TODO: check this!

        transition(); // TODO: check this!

        immediateDefense(); // TODO: check this!

        // queue the next unit to build
        if (qc.isEmpty()) state.refillQueue();
        qc.tryUnitBuild();

        // Cancel the next build unit
        if (state == State.Defend)
            qc.cancelNeutralECAttacker();

        // Consider bidding
        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8))
            bidController.bid();

        // End turn.
        if (currentRound % 25 == 0) lowPriorityLogging();
        Clock.yield();

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
                if (qc.isEmpty()) {
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
                if (rc.senseNearbyRobots(16, rc.getTeam().opponent()).length > 0) {
                    qc.clear();
                    state = State.Defend;
                    return;
                }
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
                    qc.clear();
                }
                break;
            case AttackEnemy:
                weakest = getWeakestEnemyEC();
                if (weakest == -1 || rc.getInfluence() - enemyECInfluence[weakest] < 500) {
                    state = State.Defend;
                }
                break;
            case SlandererEconomy:
                if (qc.isEmpty()) {
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

    private enum State {
        Defend {
            @Override
            void refillQueue() throws GameActionException {
                Direction dangerDir = bestDangerDir();
                if (dangerDir != null) {
                    System.out.println("Defending in direction " + dangerDir);
                    int required = dangerDirs[dangerDir.ordinal()] ? 3 : 1;
                    qc.pushMany(RobotType.POLITICIAN, 18,
                            makeMessage(Label.DEFEND, dangerDir.ordinal()), MED, required);

                    qc.pushMany(RobotType.POLITICIAN, 18,
                            makeMessage(Label.DEFEND, dangerDir.rotateLeft().ordinal()), MED, required - 2);
                    qc.pushMany(RobotType.POLITICIAN, 18,
                            makeMessage(Label.DEFEND, dangerDir.rotateRight().ordinal()), MED, required - 2);
                    int influence = getSlandererInfluence();
                    if (influence > 0)
                        qc.push(RobotType.SLANDERER, influence, makeMessage(Label.HIDE), MED);
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
                    qc.push(RobotType.POLITICIAN, influence + GameConstants.EMPOWER_TAX,
                            makeMessage(Label.CAPTURE_NEUTRAL_EC, best.x % 128, best.y % 128), MED);
                }
            }
        },
        SlandererEconomy {
            @Override
            void refillQueue() {
                int influence = getSlandererInfluence();
                if (influence > 0) {
                    qc.push(RobotType.SLANDERER, influence, makeMessage(Label.HIDE), MED);
                    qc.pushMany(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE), LOW, 5);
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
                    qc.push(RobotType.POLITICIAN, influence + GameConstants.EMPOWER_TAX,
                            makeMessage(Label.ATTACK_LOC, best.x % 128, best.y % 128), MED);
                }
            }
        };

        abstract void refillQueue() throws GameActionException;
    }

    static boolean underAttack = false;

    void immediateDefense() {
        if (addedFinalDefender) return;
        if (lastDefender == -1 || !rc.canGetFlag(lastDefender)) {
            qc.push(RobotType.POLITICIAN, 40, makeMessage(Label.FINAL_FRONTIER), ULTRA_HIGH);
            addedFinalDefender = true;
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
                        dangerDirs[dangerDir.ordinal()] = true;
                        enemyECLocs[enemyECFound] = enemyECLoc;
                        enemyECInfluence[enemyECFound++] = (int) Math.pow(2, message.data[2]);
                    } else {
                        int influence = (int) Math.pow(2, message.data[2]);
                        enemyECInfluence[knownEnemyEC] = influence;
                    }
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

    /* Helpers and Utilities */

    static int getBestNeutralEC() { // TODO: why is this like this
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

    /**
     * computes a random dangerous direction, or a random direction if no
     * dangerous ones exist
     *
     * @return a direction
     */
    static Direction bestDangerDirHelper() {
        int ix = rc.getRoundNum(); // pseudo-random
        for (int i = 0; i < 8; i++) {
            int j = (i + ix) % 8;
            if (!dangerDirs[j]) continue;
            return fromOrdinal(j);
        }
        return fromOrdinal(ix);
    }

    static Direction bestDangerDir() throws GameActionException {
        Direction dangerDir = bestDangerDirHelper();
        int[] defendersIn = {0, 0, 0, 0, 0, 0, 0, 0};
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() == rc.getTeam() && rc.canGetFlag(bot.getID())) {
                int flag = rc.getFlag(bot.getID());
                if (flag != 0 && decode(flag).label == Label.DEFEND) {
                    defendersIn[currentLocation.directionTo(bot.getLocation()).ordinal()]++;
                }
            }
        }
        boolean sufficientDefense = true;
        for (int i = 0; i < 8; i++) {
            boolean isDangerous = dangerDirs[i];
            int required = isDangerous ? 4 : 0;
            if (defendersIn[i] < required) {
                sufficientDefense = false;
                break;
            }
        }
        if (sufficientDefense) return null;
        return dangerDir;
    }
}
