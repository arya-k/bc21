package slander_feed;

import battlecode.common.*;
import slander_feed.Communication.Label;
import slander_feed.Communication.Message;
import slander_feed.utils.IterableIdSet;

import static slander_feed.Communication.decode;
import static slander_feed.QueueController.*;

public class EnlightenmentCenter extends Robot {

    // set of ids for tracking robot messages
    static IterableIdSet trackedIds = new IterableIdSet(); // NOTE: Shared with QueueController
    static boolean addedFinalDefender = false;  // NOTE: Shared with QueueController
    static int lastDefender = -1; // NOTE: Shared with QueueController

    // build priority queue
    static QueueController qc = new QueueController();

    // production state
    static State state = State.CaptureNeutral;

    // bidding controller
    BidController bidController = new BidController();

    // collected information about the map
    static boolean[] enemyECDirs = new boolean[8];
    static double[] dangerousness = new double[8];
    static int[] dangerUpdateRound = new int[8];
    static boolean[] muckrakerInDir = new boolean[8];


    // Non-self ECs that have been found so far
    static MapLocation[] ECLocs = new MapLocation[11];
    static int[] ECInfluence = new int[11];
    static Team[] ECTeam = new Team[11];
    static int[] ECLastQueued = {-100, -100, -100, -100, -100, -100, -100, -100, -100, -100, -100};
    static int ECFound = 0;

    // TODO: we need to do something with this info!
    static boolean enemyECNearby = false;
    static boolean underAttack = false;

    // whether or not we used to be a neutral EC
    static boolean wasANeutralEC = false;

    @Override
    void onAwake() throws GameActionException {
        if (rc.getRoundNum() > 5) wasANeutralEC = true;
        qc.init(); // Initialize the queue controller!

        for (RobotInfo bot : rc.senseNearbyRobots()) { // Find nearby enlightenment centers
            if (bot.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;
            addOrUpdateEC(bot.getLocation(), bot.getTeam(), bot.getInfluence());

            if (bot.getTeam() == rc.getTeam().opponent())
                enemyECNearby = true;
        }

        // initialize priority queue
        qc.push(RobotType.SLANDERER, 41, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), HIGH); // Econ slanderer
        for (Direction dir : Robot.directions) // Scout politician
            qc.push(RobotType.POLITICIAN, 1, makeMessage(Label.SCOUT, dir.ordinal()), MED);
        qc.pushMany(RobotType.MUCKRAKER, 1, makeMessage(Label.EXPLORE), LOW, 5); // Exploring muckrakers
    }

    void lowPriorityLogging() {
        System.out.println(wasANeutralEC ? "Neutral EC" : "Starter EC");
        System.out.println("Influence: " + myInfluence);
        System.out.println("Production state: " + state);
        System.out.println("Bidding state: " + BidController.state);
        for (int i = 0; i < 8; i++) {
            if (!enemyECDirs[i] && dangerousness[i] < 0.75) continue;
            System.out.println("DANGEROUS DIRECTION: " + fromOrdinal(i));
        }
        for (int i = 0; i < ECFound; i++) {
            String teamMessage = "Neutral";
            if (ECTeam[i] == rc.getTeam()) teamMessage = "Our";
            if (ECTeam[i] == rc.getTeam().opponent()) teamMessage = "Enemy";
            System.out.println(teamMessage + " EC @ " + ECLocs[i] + " with inf = " + ECInfluence[i]);
        }
        qc.logNext();
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        boolean tracked = qc.trackLastBuiltUnit();

        processFlags();
        invalidateOldDangerousness();
        transition();
        immediateDefense();

        // queue the next unit to build
        if (qc.isEmpty()) state.refillQueue();
        boolean built = qc.tryUnitBuild();

        // Consider bidding
        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8))
            bidController.bid();

        if (!tracked && !built) {
            flagMessage(Label.SAFE_DIR, safestDir().ordinal());
        }

        // End turn.
        if (currentRound % 25 == 0) lowPriorityLogging();
        Clock.yield();

    }

    /* Production and Stimulus Logic */

    static void transition() throws GameActionException {
        // * -> Defense
        underAttack = getUnderAttack();
        if (underAttack || bestDangerDir() != null) {
            if (state != State.Defend) {
                state = State.Defend;
            }
            return;
        }

        int targetEnemyEC = getWeakestEnemyEC();
        int targetNeutralEC = getBestNeutralEC();

        // CaptureNeutral -> Defense
        if (state == State.CaptureNeutral) {
            if (targetNeutralEC == -1) {
                state = State.Defend;
            }
        }

        // Defend -> Other
        if (state == State.Defend && qc.isEmpty()) {
            if (rc.getRoundNum() > 350 && targetEnemyEC != -1 && rc.getInfluence() - ECInfluence[targetEnemyEC] > 1000) {
                state = State.AttackEnemy;
            } else if (targetNeutralEC != -1) {
                state = State.CaptureNeutral;
            } else if (bestDangerDir() == null) {
                state = State.SlandererEconomy;
            }
            return;
        }

        // AttackEnemy -> Defense
        if (state == State.AttackEnemy) {
            if (targetEnemyEC == -1 || rc.getInfluence() - ECInfluence[targetEnemyEC] < 500) {
                state = State.Defend;
            }
            return;
        }

        // SlandererEconomy -> Other
        if (state == State.SlandererEconomy && qc.isEmpty()) {
            if (targetEnemyEC != -1 && rc.getInfluence() - ECInfluence[targetEnemyEC] > 1000) {
                state = State.AttackEnemy;
            } else if (bestDangerDir() != null) {
                state = State.Defend;
            } else if (targetNeutralEC != -1) {
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
                            makeMessage(Label.DEFEND, dangerDir.ordinal()), HIGH, required);
                    int leftOrd = dangerDir.rotateLeft().ordinal();
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, leftOrd), HIGH, requiredIn[leftOrd]);
                    int rightOrd = dangerDir.rotateRight().ordinal();
                    qc.pushMany(RobotType.POLITICIAN, politicianInfluence,
                            makeMessage(Label.DEFEND, rightOrd), HIGH, requiredIn[rightOrd]);

//                    int influence = getSlandererInfluence();
//                    if (influence > 0 && !underAttack) {
//                        qc.push(RobotType.SLANDERER, influence, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), MED);
//                        Message msg = makeMessage(Label.EXPLORE);
//                        qc.push(RobotType.MUCKRAKER, 1, msg, MED);
//                    }
                }
            }
        },
        CaptureNeutral {
            @Override
            void refillQueue() {
                int bestNeutralECIndex = getBestNeutralEC();
                if (bestNeutralECIndex == -1) return; // No one to attack right now

                int influence = ECInfluence[bestNeutralECIndex];
                MapLocation neutralECLoc = ECLocs[bestNeutralECIndex];
                qc.push(RobotType.POLITICIAN, influence + GameConstants.EMPOWER_TAX,
                        makeMessage(Label.CAPTURE_NEUTRAL_EC, neutralECLoc.x % 128, neutralECLoc.y % 128), MED);
                System.out.println("QUEUED A CAPTURE OF " + neutralECLoc);

                ECLastQueued[bestNeutralECIndex] = rc.getRoundNum();
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
                int bestEnemyEC = getWeakestEnemyEC();
                if (bestEnemyEC == -1) return; // No enemies to attack right now

                int influence = ECInfluence[bestEnemyEC];
                MapLocation best = ECLocs[bestEnemyEC];
                qc.push(RobotType.POLITICIAN, influence + GameConstants.EMPOWER_TAX,
                        makeMessage(Label.ATTACK_LOC, best.x % 128, best.y % 128), MED);

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

    void updateDangerousness(MapLocation updateLoc, int num_enemies, int sawHeavy, boolean sawMuckraker) {
        if (num_enemies == 0) return;
        Direction dir = rc.getLocation().directionTo(updateLoc);
        int ord = dir.ordinal();
        double distance = Math.sqrt(rc.getLocation().distanceSquaredTo(updateLoc));
        if (sawMuckraker)
            muckrakerInDir[ord] = true;
        double sawHeavyFactor = 36.0 / distance;
        double baseDanger = num_enemies * 4 / distance;
        double newDanger = baseDanger + (sawHeavyFactor * sawHeavy);
        if (newDanger > dangerousness[ord] || rc.getRoundNum() - dangerUpdateRound[ord] > 15) {
            dangerUpdateRound[ord] = rc.getRoundNum();
            dangerousness[ord] = newDanger;
        }
    }

    void invalidateOldDangerousness() {
        for (int i = 0; i < 8; i++) {
            if (rc.getRoundNum() - dangerUpdateRound[i] > 200) {
                dangerousness[i] = 0;
                muckrakerInDir[i] = false;
            }
        }
    }

    static final int MAX_IDS_TO_PROCESS_IN_TURN = 75;
    static int cursor = 0;

    void processFlags() throws GameActionException {
        if (trackedIds.getSize() == 0) return;

        for (int j = 0; j < Math.min(MAX_IDS_TO_PROCESS_IN_TURN, trackedIds.getSize()); j++) {
            int index = (cursor + j) % trackedIds.getSize();
            int id = trackedIds.indexToID(index * 5 + 1);

            if (!rc.canGetFlag(id)) {
                trackedIds.remove(id);
                j--;
                continue;
            }

            int flag = rc.getFlag(id);
            if (flag == 0) continue;

            Message message = decode(flag);
            switch (message.label) {
                case ENEMY_EC:
                    MapLocation enemyECLoc = getLocFromMessage(message.data[0], message.data[1]);
                    addOrUpdateEC(enemyECLoc, rc.getTeam().opponent(), (int) Math.pow(2, message.data[2]));

                    Direction dangerDir = currentLocation.directionTo(enemyECLoc);
                    enemyECDirs[dangerDir.ordinal()] = true;
                    break;

                case NEUTRAL_EC:
                    MapLocation neutralECLoc = getLocFromMessage(message.data[0], message.data[1]);
                    addOrUpdateEC(neutralECLoc, Team.NEUTRAL, (int) Math.pow(2, message.data[2]));
                    break;

                case OUR_EC:
                    MapLocation ourECLoc = getLocFromMessage(message.data[0], message.data[1]);
                    addOrUpdateEC(ourECLoc, rc.getTeam(), 0);

                case DANGER_INFO:
                    MapLocation dangerLoc = getLocFromMessage(message.data[0], message.data[1]);
                    int num_enemies = message.data[2];
                    int sawHeavyTroop = message.data[4];
                    boolean sawMuckraker = message.data[3] == 1;
                    updateDangerousness(dangerLoc, num_enemies, sawHeavyTroop, sawMuckraker);
            }
        }
        if (trackedIds.getSize() != 0)
            cursor = (cursor + Math.min(MAX_IDS_TO_PROCESS_IN_TURN, trackedIds.getSize())) % trackedIds.getSize();
    }

    /* Helpers and Utilities */

    static void addOrUpdateEC(MapLocation loc, Team team, int influence) {
        int idx = ECFound;
        for (int i = 0; i < ECFound; i++)
            if (ECLocs[i].isWithinDistanceSquared(loc, 0))
                idx = i;
        if (idx == ECFound) ECFound++;

        ECLocs[idx] = loc;
        ECTeam[idx] = team;
        ECInfluence[idx] = influence;
    }


    static int getWeakestEnemyEC() {
        Team enemy = rc.getTeam().opponent();

        int weakest = -1;
        for (int i = 0; i < ECFound; i++) {
            if (ECTeam[i] != enemy) continue;
            if (weakest == -1 || ECInfluence[weakest] > ECInfluence[i])
                weakest = i;
        }
        return weakest;
    }

    static final int NEUTRAL_WAITING_ROUNDS = 50; // How long until we try to attack it again

    static int getBestNeutralEC() {
        int best = -1;
        for (int i = 0; i < ECFound; i++) {
            if (ECTeam[i] != Team.NEUTRAL || rc.getRoundNum() - ECLastQueued[i] < NEUTRAL_WAITING_ROUNDS)
                continue;

            MapLocation location = ECLocs[i];
            int distance = location.distanceSquaredTo(currentLocation);
            int influence = ECInfluence[i];

            int actingInfluence = 2 * rc.getInfluence();
            if (actingInfluence - influence <= influenceMinimum())
                continue;

            if (best == -1) {
                best = i;
                continue;
            }

            MapLocation bestLocation = ECLocs[best];
            int bestDistance = bestLocation.distanceSquaredTo(currentLocation);
            int bestInfluence = ECInfluence[best];

            if (distance + influence < bestDistance + bestInfluence)
                best = i;
        }
        return best;
    }

    static int[] requiredInAllDirections() {
        int[] defendersIn = {0, 0, 0, 0, 0, 0, 0, 0};
        return defendersIn;
//        nearby = rc.senseNearbyRobots();
//        for (RobotInfo bot : nearby) {
//            if (bot.getTeam() == rc.getTeam() && bot.getType() == RobotType.POLITICIAN && bot.getInfluence() > GameConstants.EMPOWER_TAX) {
//                defendersIn[rc.getLocation().directionTo(bot.getLocation()).ordinal()]++;
//            }
//        }
//        int[] requiredIn = {0, 0, 0, 0, 0, 0, 0, 0};
//        for (int i = 0; i < 8; i++) {
//            boolean isDangerous = enemyECDirs[i] || dangerousness[i] > 0.75;
//            int required = isDangerous ? (int) ((rc.getRoundNum() / 125 + 1) * dangerousness[i])
//                    : (int) ((rc.getRoundNum() / 250) * dangerousness[i]);
//            requiredIn[i] = Math.min(6, required) - defendersIn[i];
//        }
//        return requiredIn;
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
                if (dangerDir == null || remaining > requiredIn[dangerDir.ordinal()])
                    dangerDir = fromOrdinal(i);
            }
        }
        return dangerDir;
    }

    static Direction safestDir() {
        int dx = 0;
        int dy = 0;
        for (int i = 0; i < 8; i++) {
            Direction dir = fromOrdinal(i);
            if (enemyECDirs[i] || muckrakerInDir[i]) {
                dx += dir.dx;
                dy += dir.dy;
            }
        }
        MapLocation temp = rc.getLocation().translate(dx, dy);
        Direction kindOfOptimalDir = rc.getLocation().directionTo(temp).opposite();
        if (kindOfOptimalDir == Direction.CENTER) {
            // CASE 1: OPPOSITE DIRECTIONS END UP HAVING MUCKRAKERS
            // We choose the direction with least danger
            // and no muckrakers
            int safest = 0;
            for (int i = 1; i < 8; i++) {
                if (muckrakerInDir[i] || dangerousness[safest] > dangerousness[i]) safest = i;
            }
            return fromOrdinal(safest);
        } else {
            // CASE 2: we get the opposite of the average muckraker direction
            // pick the best of the three rotations
            Direction oneLeft = kindOfOptimalDir.rotateLeft();
            Direction oneRight = kindOfOptimalDir.rotateRight();
            Direction ret = kindOfOptimalDir;
            if (dangerousness[ret.ordinal()] > dangerousness[oneLeft.ordinal()]) ret = oneLeft;
            if (dangerousness[ret.ordinal()] > dangerousness[oneRight.ordinal()]) ret = oneRight;
            return ret;
        }

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
        return nearbyEnemyInfluence > rc.getInfluence() / 2;
    }
}
