package quals;

import battlecode.common.*;
import quals.Communication.Label;
import quals.Communication.Message;
import quals.utils.IterableIdSet;

import static quals.Communication.decode;
import static quals.QueueController.*;

public class EnlightenmentCenter extends Robot {

    // set of ids for tracking robot messages
    static IterableIdSet trackedIds = new IterableIdSet(); // NOTE: Shared with QueueController
    static boolean addedBuffer = false;
    static int bufferID = -1; // NOTE: Shared with QueueController

    // production state
    static State state = State.EarlyGame;
    static int[] numQueued = new int[4];

    // bidding controller
    BidController bidController = new BidController();

    // collected muckraker info
    static int[] muckrakerLastUpdate = new int[8];
    static int[] muckrakersInDir = new int[8];
    static boolean newSafeDir = false;

    // Non-self ECs that have been found so far
    static MapLocation[] ECLocs = new MapLocation[11];
    static Team[] ECTeam = new Team[11];
    static int[] ECInfluence = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    static int[] ECLastQueued = {-100, -100, -100, -100, -100, -100, -100, -100, -100, -100, -100};
    static int ECFound = 0;

    static boolean enemyECNearby = false;
    static boolean underAttack = false;

    // whether or not we used to be a neutral EC
    static boolean wasANeutralEC = false;

    @Override
    void onAwake() throws GameActionException {
        QueueController.init(); // Initialize the queue controller!

        for (RobotInfo bot : rc.senseNearbyRobots()) { // Find nearby enlightenment centers
            if (bot.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;
            addOrUpdateEC(bot.getLocation(), bot.getTeam(), bot.getInfluence());

            if (!enemyECNearby && bot.getTeam() == rc.getTeam().opponent()) {
                enemyECNearby = true;
                MapLocation best = bot.getLocation();
                for (int i = 0; i < 15; i++) {
                    QueueController.push(RobotType.MUCKRAKER,
                            makeMessage(Label.ATTACK_LOC, best.x % 128, best.y % 128), 0.0, 1, ULTRA_HIGH);
                }
            }
        }

        if (rc.getRoundNum() > 5) {
            wasANeutralEC = true;
            return;
        }

        // initialize priority queue
        if (!wasANeutralEC)
            QueueController.push(RobotType.SLANDERER, makeMessage(Label.HIDE), 0.9, 130, HIGH); // Econ slanderer
        for (Direction dir : Robot.directions) // Scout politician
            QueueController.push(RobotType.MUCKRAKER, makeMessage(Label.SCOUT, dir.ordinal()), 0.0, 1, MED);
    }

    void lowPriorityLogging() {
        System.out.println(wasANeutralEC ? "Neutral EC" : "Starter EC");
        System.out.println("Influence: " + rc.getInfluence());
        System.out.println("Production state: " + state);
        System.out.println("Bidding state: " + BidController.state);
        for (int i = 0; i < 8; i++) {
            if (muckrakersInDir[i] < 1) continue;
            System.out.println("MUCKRAKER DIRECTION: " + fromOrdinal(i));
        }
        for (int i = 0; i < ECFound; i++) {
            String teamMessage = "Neutral";
            if (ECTeam[i] == rc.getTeam()) teamMessage = "Our";
            else if (ECTeam[i] == rc.getTeam().opponent()) teamMessage = "Enemy";
            System.out.println(teamMessage + " EC @ " + ECLocs[i]);
        }
        QueueController.logNext();
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        boolean tracked = QueueController.trackLastBuiltUnit();

        processFlags();
        transition();
        urgentQueueing();

        // queue the next unit to build
        boolean built = false;
        if (QueueController.isEmpty()) {
            state.refillQueue();
        }
        if (bufferID == -1 && !newSafeDir) // dont build anything while a buffer is out!
            built = QueueController.tryUnitBuild();

        // Consider bidding
        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8))
            bidController.bid();

        if (!tracked && !built) {
            flagMessage(Label.SAFE_DIR, safestDir().ordinal());
            newSafeDir = false;
        }

        // End turn.
        if (rc.getRoundNum() % 25 == 0) lowPriorityLogging();
        Clock.yield();

    }

    /* Production and Stimulus Logic */

    static void transition() {
        if (!wasANeutralEC && numQueued[RobotType.SLANDERER.ordinal()] < 15) {
            state = State.EarlyGame;
        } else {
            int bestIdx = getBestEC();
            state = State.AttackLoc;
            if (bestIdx == -1
                    || (ECTeam[bestIdx] == Team.NEUTRAL && ECInfluence[bestIdx] > rc.getInfluence() * 2)
                    || (ECTeam[bestIdx] != Team.NEUTRAL && rc.getRoundNum() < 200)) state = State.MidGame;
        }
    }

    private enum State {
        EarlyGame {
            @Override
            void refillQueue() {
                Message msg = makeMessage(Label.SAFE_DIR, safestDir().ordinal());
                QueueController.push(RobotType.SLANDERER, msg, 0.9, 100, MED);
                QueueController.push(RobotType.POLITICIAN, msg, 0.05, 20, MED);
            }
        },
        MidGame {
            @Override
            void refillQueue() {
                QueueController.pushMany(RobotType.MUCKRAKER, makeMessage(Label.EXPLORE), 0.0, 1, MED, 3);
                QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), rc.getInfluence() > 1000 ? 0.8 : 0.3, 20, MED);
                QueueController.push(RobotType.SLANDERER, makeMessage(Label.SAFE_DIR, safestDir().ordinal()), 0.5, 100, MED);
            }
        },
        AttackLoc {
            @Override
            void refillQueue() {
                int bestIdx = getBestEC();
                MapLocation ecLoc = ECLocs[bestIdx];
                Message msg = makeMessage(Label.ATTACK_LOC, ecLoc.x % 128, ecLoc.y % 128);
                if (ECTeam[bestIdx] == Team.NEUTRAL) {
                    ECLastQueued[bestIdx] = rc.getRoundNum();
                    QueueController.push(RobotType.POLITICIAN, msg, 0.7, ECInfluence[bestIdx], MED);
                } else {
                    QueueController.pushMany(RobotType.POLITICIAN, msg, 0.5, 20, MED, 2);
                    QueueController.pushMany(RobotType.MUCKRAKER, msg, 0.0, -1, MED, 2);
                }
            }
        },

        ;

        abstract void refillQueue() throws GameActionException;

    }

    static int getBestEC() {
        int best = -1;
        for (int i = 0; i < ECFound; i++) {
            if (ECTeam[i] == rc.getTeam()) continue;
            if (rc.getRoundNum() - ECLastQueued[i] < 100) continue;
            MapLocation ecLoc = ECLocs[i];
            int ecInfluence = ECInfluence[i];
            if (best == -1
                    || rc.getLocation().distanceSquaredTo(ecLoc) + ecInfluence < rc.getLocation().distanceSquaredTo(ECLocs[best]) + ECInfluence[best])
                best = i;
        }
        return best;
    }

    void urgentQueueing() {
        // buffer business
        if (!underAttack && !addedBuffer) {
            int turnsUntilEmpower = (int) (RobotType.POLITICIAN.initialCooldown + (int) rc.getCooldownTurns());
            double factor = rc.getEmpowerFactor(rc.getTeam(), turnsUntilEmpower);
            int influence = rc.getInfluence() - influenceMinimum();
            if (factor * influence - GameConstants.EMPOWER_TAX > 2 * rc.getInfluence()) {
                System.out.println("BUFFING MYSELF!");
                QueueController.push(RobotType.POLITICIAN, makeMessage(Label.BUFF), 1, -1, ULTRA_HIGH);
                addedBuffer = true;
            }
        }
        if (bufferID != -1 && !rc.canGetFlag(bufferID)) {
            addedBuffer = false;
            bufferID = -1;
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
                    break;

                case NEUTRAL_EC:
                    MapLocation neutralECLoc = getLocFromMessage(message.data[0], message.data[1]);
                    addOrUpdateEC(neutralECLoc, Team.NEUTRAL, (int) Math.pow(2, message.data[2]));
                    break;

                case OUR_EC:
                    MapLocation ourECLoc = getLocFromMessage(message.data[0], message.data[1]);
                    addOrUpdateEC(ourECLoc, rc.getTeam(), 0);
                    break;

                case DANGER_INFO:
                    MapLocation dangerLoc = getLocFromMessage(message.data[0], message.data[1]);
                    if (dangerLoc.isWithinDistanceSquared(rc.getLocation(), 9))
                        continue;
                    int relevant = rc.getLocation().directionTo(dangerLoc).ordinal();
                    int num_muckrakers = message.data[2];
                    if (num_muckrakers > muckrakersInDir[relevant]
                            || rc.getRoundNum() - muckrakerLastUpdate[relevant] > 10) {
                        newSafeDir = true;
                        muckrakerLastUpdate[relevant] = rc.getRoundNum();
                        muckrakersInDir[relevant] = num_muckrakers;
                    }
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
        if (idx == ECFound) {
            ECFound++;
        }

        ECLocs[idx] = loc;
        ECTeam[idx] = team;
        ECInfluence[idx] = influence;
    }

    static Direction safestDir() {
        int dx = 0;
        int dy = 0;
        for (int i = 0; i < 8; i++) {
            Direction dir = fromOrdinal(i);
            dx += dir.dx * muckrakersInDir[i];
            dy += dir.dy * muckrakersInDir[i];
        }
        MapLocation temp = rc.getLocation().translate(dx, dy);
        Direction optimalDir = rc.getLocation().directionTo(temp).opposite();
        if (optimalDir == Direction.CENTER) {
            // CASE 1: OPPOSITE DIRECTIONS END UP HAVING EQUAL MUCKRAKERS
            // We choose a direction with no muckrakers
            int safest = 0;
            for (int i = 1; i < 8; i++) {
                if (muckrakersInDir[safest] > 0) safest = i;
            }
            return fromOrdinal(safest);
        }
        return optimalDir;
    }
}
