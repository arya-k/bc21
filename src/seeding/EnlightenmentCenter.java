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
    static boolean underAttack = false;

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
        if (state == State.Defend) qc.cancelNeutralECAttacker();

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
        for (int i = 1; i < enemyECFound; i++)
            if (enemyECInfluence[weakest] > enemyECInfluence[i])
                weakest = i;
        return weakest;
    }

    /* Production and Stimulus Logic */

    static void transition() throws GameActionException {
        // * -> Defense
        if (rc.senseNearbyRobots(16, rc.getTeam().opponent()).length > 0) {
            if (state != State.Defend) {
                qc.clear();
                state = State.Defend;
            }
            return;
        }

        // CaptureNeutral -> Defend
        if (state == State.CaptureNeutral) {
            if (neutralECFound <= 0) {
                state = State.Defend;
                return;
            }
            int[] ids = neutralCapturers.getKeys();
            if (ids.length == 0) return;
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
            return;
        }

        int weakest = getWeakestEnemyEC();

        // Defend -> Other
        if (state == State.Defend && qc.isEmpty()) {
            if (rc.getRoundNum() > 350 && weakest != -1 && rc.getInfluence() - enemyECInfluence[weakest] > 1000) {
                state = State.AttackEnemy;
            } else if (neutralECFound > 0 && 2 * rc.getInfluence() - neutralECInfluence[getBestNeutralEC()] > influenceMinimum()) {
                state = State.CaptureNeutral;
            } else if (bestDangerDir() == null) {
                state = State.SlandererEconomy;
            }
            return;
        }

        // AttackEnemy -> Defense
        if (state == State.AttackEnemy) {
            if (weakest == -1 || rc.getInfluence() - enemyECInfluence[weakest] < 500) {
                state = State.Defend;
                qc.clear();
            }
        }

        // SlandererEconomy -> Other
        if (state == State.SlandererEconomy && qc.isEmpty()) {
            if (weakest != -1 && rc.getInfluence() - enemyECInfluence[weakest] > 1000) {
                state = State.AttackEnemy;
            } else if (bestDangerDir() != null) {
                state = State.Defend;
            } else if (neutralECFound > 0 && 2 * rc.getInfluence() - neutralECInfluence[getBestNeutralEC()] > influenceMinimum()) {
                state = State.CaptureNeutral;
            }
        }
    }

    private enum State {
        Defend {
            @Override
            void refillQueue() throws GameActionException {
                Direction dangerDir = bestDangerDir();
                if (dangerDir != null) {
                    System.out.println("Defending in direction " + dangerDir);
                    int politicianInfluence = getPoliticianInfluence();
                    int required = dangerDirs[dangerDir.ordinal()] ? 3 : 1;
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, dangerDir.ordinal()), MED, required);

                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, dangerDir.rotateLeft().ordinal()), MED, required - 2);
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
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
            void refillQueue() throws GameActionException {
                int influence = getSlandererInfluence();
                if (influence > 0) {
                    qc.push(RobotType.SLANDERER, influence, makeMessage(Label.HIDE), MED);
                    int politicianInfluence = getPoliticianInfluence();
                    Direction politicianDir = bestDangerDir();
                    if (politicianDir == null)
                        politicianDir = randomDirection();
                    qc.push(RobotType.POLITICIAN, politicianInfluence, makeMessage(Label.DEFEND, politicianDir.ordinal()), MED);
                    qc.pushMany(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE), LOW, 2);
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
                            makeMessage(Label.ATTACK_LOC,
                                    best.x % 128,
                                    best.y % 128,
                                    rc.getRoundNum() / 6, 0),
                            MED);
                }
            }
        };

        abstract void refillQueue() throws GameActionException;
    }

    void immediateDefense() {
        if (addedFinalDefender) return;
        if (lastDefender == -1 || !rc.canGetFlag(lastDefender)) {
            qc.push(RobotType.POLITICIAN, getPoliticianInfluence(), makeMessage(Label.FINAL_FRONTIER), ULTRA_HIGH);
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
                        push(RobotType.MUCKRAKER, 1,
                                makeMessage(Label.ATTACK_LOC,
                                        enemyECLoc.x % 128,
                                        enemyECLoc.y % 128,
                                        rc.getRoundNum() / 6, 0),
                                HIGH);
                    } else {
                        int influence = (int) Math.pow(2, message.data[2]);
                        enemyECInfluence[knownEnemyEC] = influence;
                    }
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
                    } else {
                        int influence = (int) Math.pow(2, message.data[2]);
                        neutralECInfluence[knownNeutralEC] = influence;
                    }
                    trackedIds.remove(id);
                    break;

                case SCOUT_LOCATION:
                    MapLocation loc = getLocFromMessage(message.data[0], message.data[1]);
                    int ord = rc.getLocation().directionTo(loc).ordinal();
                    int offset = (int) (Math.sqrt(rc.getLocation().distanceSquaredTo(loc)));
                    edgeOffsets[ord] = offset;
                    updateDirOpenness();
                    break;
            }
        }
    }

    /* Helpers and Utilities */

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

    /**
     * computes the most dangerous direction
     *
     * @return a direction
     */
    static Direction bestDangerDir() throws GameActionException {
        int[] defendersIn = {0, 0, 0, 0, 0, 0, 0, 0};
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() == rc.getTeam() && rc.canGetFlag(bot.getID())) {
                int flag = rc.getFlag(bot.getID());
                if (flag != 0 && decode(flag).label == Label.DEFEND) {
                    defendersIn[currentLocation.directionTo(bot.getLocation()).ordinal()]++;
                }
            }
        }
        double mostOpen = mostOpenDirectionValue();
        int[] requiredIn = {0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < 8; i++) {
            boolean isDangerous = dangerDirs[i];
            int toAdd = isDangerous ? 4 : 0;
            double openness = directionOpenness[i];
            double normalized = openness / mostOpen;
            int required = (int) (normalized * 3) + toAdd;
            requiredIn[i] = required;
        }

        Direction dangerDir = null;
        for (int i = 0; i < 8; i++) {
            int remaining = requiredIn[i] - defendersIn[i];
            if (remaining > 0) {
                if (dangerDir == null
                        || remaining > requiredIn[dangerDir.ordinal()] - defendersIn[dangerDir.ordinal()])
                    dangerDir = fromOrdinal(i);
            }
        }
        return dangerDir;
    }

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

    static int mostOpenDirectionValue() {
        int best = directionOpenness[7];
        for (int i = 7; --i >= 0; ) {
            best = Math.max(best, directionOpenness[i]);
        }
        return best;
    }
}
