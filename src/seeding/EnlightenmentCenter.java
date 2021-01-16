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
    static boolean[] currentDangerDirs = new boolean[8];
    static int[] edgeOffsets = new int[8]; // only the cardinal directions matter here.
    static int[] directionOpenness = new int[8]; // lower numbers are "safer"
    static int[] lastSeenInDirection = new int[8];
    static int[] seenConsecutiveInDir = new int[8];
    static int[] lastUpdated = new int[8];


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

    // whether or not we used to be a neutral EC
    static boolean wasANeutralEC = false;

    @Override
    void onAwake() throws GameActionException {
        if (rc.getInfluence() != 150 || rc.senseNearbyRobots().length != 0)
            wasANeutralEC = true;
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


        qc.push(RobotType.SLANDERER, 41, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), ULTRA_HIGH); // Econ slanderer
        for (Direction dir : Robot.directions) {
            qc.push(RobotType.POLITICIAN, 1,
                    makeMessage(Label.SCOUT, dir.ordinal()), HIGH); // Scout politician
        }

        qc.pushMany(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE), LOW, 5); // Exploring muckrakers
    }

    void lowPriorityLogging() {
        System.out.println(wasANeutralEC ? "Neutral EC" : "Starter EC");
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

        updateDangerDirs();

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
        underAttack = getUnderAttack();
        if (underAttack) {
            if (state != State.Defend) {
                qc.clear();
                state = State.Defend;
            }
            return;
        }

        // CaptureNeutral -> Defend
        if (state == State.CaptureNeutral) {
            if (neutralECFound <= 0 || rc.getRoundNum() > 500) {
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
                int[] requiredIn = requiredInAllDirections();
                if (dangerDir != null) {
                    System.out.println("Defending in direction " + dangerDir);
                    int politicianInfluence = getPoliticianInfluence();
                    int required = requiredIn[dangerDir.ordinal()];
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, dangerDir.ordinal()), MED, required);
                    int leftOrd = dangerDir.rotateLeft().ordinal();
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, leftOrd), MED, requiredIn[leftOrd]);
                    int rightOrd = dangerDir.rotateRight().ordinal();
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, rightOrd), MED, requiredIn[rightOrd]);
                    int influence = getSlandererInfluence();
                    if (influence > 0 && !underAttack) {
                        qc.push(RobotType.SLANDERER, influence, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), MED);
                        Message msg = makeMessage(Label.EXPLORE);
                        qc.push(RobotType.MUCKRAKER, 1, msg, MED);
                    }
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
                    qc.push(RobotType.SLANDERER, influence, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), MED);
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

    void updateDangerDirs() {
        for (RobotInfo bot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (bot.getType() == RobotType.POLITICIAN && bot.getInfluence() <= GameConstants.EMPOWER_TAX)
                continue;
            int ord = currentLocation.directionTo(bot.getLocation()).ordinal();
            if (lastSeenInDirection[ord] == rc.getRoundNum() - 1)
                seenConsecutiveInDir[ord]++;
            lastSeenInDirection[ord] = rc.getRoundNum();
        }
        for (int i = 0; i < 8; i++) {
            if (lastSeenInDirection[i] != rc.getRoundNum())
                seenConsecutiveInDir[i] = 0;
            boolean update = seenConsecutiveInDir[i] > 2;
            if (currentDangerDirs[i] != update)
                lastUpdated[i] = rc.getRoundNum();
            if (update || rc.getRoundNum() - lastUpdated[i] > 100) {
                lastUpdated[i] = rc.getRoundNum();
                currentDangerDirs[i] = update;
            }
        }
    }

    static boolean dangerNearby() throws GameActionException {
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() != rc.getTeam()) {
                if (bot.getType() == RobotType.MUCKRAKER
                        || (bot.getType() == RobotType.POLITICIAN && bot.getInfluence() > GameConstants.EMPOWER_TAX))
                    return true;
            }
        }
        return false;
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

                    int knownNeutralEC = -1;
                    for (int i = neutralECFound; --i >= 0; ) {
                        if (neutralECLocs[i].equals(neutralECLoc)) {
                            knownNeutralEC = i;
                            break;
                        }
                    }

                    int influence = (int) Math.pow(2, message.data[2]);
                    if (knownNeutralEC == -1) {
                        neutralECLocs[neutralECFound] = neutralECLoc;
                        neutralECInfluence[neutralECFound++] = influence;
                    } else {
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

    static int[] requiredInAllDirections() throws GameActionException {
        int[] defendersIn = {0, 0, 0, 0, 0, 0, 0, 0};
        nearby = rc.senseNearbyRobots();
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() == rc.getTeam() && rc.canGetFlag(bot.getID())) {
                int flag = rc.getFlag(bot.getID());
                if (flag == 0) continue;
                Label label = decode(flag).label;
                if (label == Label.DEFEND || label == Label.CURRENTLY_DEFENDING) {
                    defendersIn[currentLocation.directionTo(bot.getLocation()).ordinal()]++;
                }
            }
        }
        double mostOpen = mostOpenDirectionValue();
        int[] requiredIn = {0, 0, 0, 0, 0, 0, 0, 0};
        int multiplier = (rc.getRoundNum() / 250) + 1;
        int dangerAdd = (rc.getRoundNum() / 125) + 2;
        for (int i = 0; i < 8; i++) {
            boolean isDangerous = dangerDirs[i] || currentDangerDirs[i];
            int toAdd = isDangerous ? dangerAdd : 0;
            double openness = directionOpenness[i];
            double normalized = openness / mostOpen;
            int required = (int) (normalized * multiplier) + toAdd;
            requiredIn[i] = Math.min(6, required) - defendersIn[i];
        }
        return requiredIn;
    }

    /**
     * computes the most dangerous direction
     *
     * @return a direction
     */

    static Direction bestDangerDir() throws GameActionException {
        int[] requiredIn = requiredInAllDirections();
        Direction dangerDir = null;
        for (int i = 0; i < 8; i++) {
            int remaining = requiredIn[i];
            if (remaining > 0) {
                if (dangerDir == null
                        || remaining > requiredIn[dangerDir.ordinal()])
                    dangerDir = fromOrdinal(i);
            }
        }
        return dangerDir;
    }

    static Direction safestDir() throws GameActionException {
        int[] requiredIn = requiredInAllDirections();
        int safest = 0;
        for (int i = 0; i < 8; i++) {
            if (currentDangerDirs[i] || dangerDirs[i]) continue;
            if (requiredIn[safest] > requiredIn[i]) safest = i;
        }
        return fromOrdinal(safest);
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

    static boolean getUnderAttack() {
        int nearbyEnemyInfluence = 0;
        for (RobotInfo info : nearby) {
            if (info.getTeam() == rc.getTeam()) continue;
            if (info.getType() == RobotType.MUCKRAKER)
                return true;
            if (info.getType() == RobotType.POLITICIAN)
                nearbyEnemyInfluence += info.getInfluence();
        }
        if (nearbyEnemyInfluence > rc.getInfluence() / 2)
            return true;
        return false;
    }
}
