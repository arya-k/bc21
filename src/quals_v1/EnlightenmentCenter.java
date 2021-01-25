package quals_v1;

import battlecode.common.*;
import quals_v1.Communication.Label;
import quals_v1.Communication.Message;
import quals_v1.utils.IterableIdSet;

import static quals_v1.Communication.decode;
import static quals_v1.Communication.encode;
import static quals_v1.QueueController.*;
import static quals_v1.QueueController.influenceMinimum;

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
    static int ECFound = 0;
    static int broadcastECIndex = 0;

    static boolean underAttack = false;

    // whether or not we used to be a neutral EC
    static boolean wasANeutralEC = false;

    @Override
    void onAwake() throws GameActionException {
        QueueController.init(); // Initialize the queue controller!

        for (RobotInfo bot : rc.senseNearbyRobots()) { // Find nearby enlightenment centers
            if (bot.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;
            addOrUpdateEC(bot.getLocation(), bot.getTeam(), bot.getInfluence());
        }

        if (rc.getRoundNum() > 5) {
            wasANeutralEC = true;
            return;
        }

        // initialize priority queue
        if (!wasANeutralEC)
            QueueController.push(RobotType.SLANDERER, makeMessage(Label.HIDE), 0.9, 130, HIGH); // Econ slanderer
        for (Direction dir : Robot.directions) // Scout Muckraker
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
        if (!newSafeDir)
            built = QueueController.tryUnitBuild();

        // Consider bidding
        bidController.update();
        if (!(underAttack && rc.senseNearbyRobots(2, rc.getTeam().opponent()).length < 8))
            bidController.bid();

        if (!tracked && !built) {
            Message msg = makeUpdateMessage();
            rc.setFlag(encode(msg));
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
        } else if (numQueued[RobotType.SLANDERER.ordinal()] < 50) {
            state = State.MidGame;
        } else {
            state = State.LateGame;
        }
    }

    private enum State {
        EarlyGame {
            @Override
            void refillQueue() {
                Message msg = makeUpdateMessage();
                QueueController.push(RobotType.SLANDERER, msg, 0.9, 85, MED);
                QueueController.push(RobotType.POLITICIAN, msg, 0.05, 17, MED);
            }
        },
        MidGame {
            @Override
            void refillQueue() {
                QueueController.push(RobotType.MUCKRAKER, makeMessage(Label.EXPLORE), 0.0, 1, MED);

                int best = getBestNeutralEC();
                if (best != -1) {
                    int bestInfluence = ECInfluence[best] + GameConstants.EMPOWER_TAX;
                    if (bestInfluence < rc.getInfluence()) {
                        QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), 0.8, bestInfluence, MED);
                    }
                } else if (rc.getInfluence() > 1000) {
                    QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), 0.8, 20, MED);
                } else {
                    QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), 0, 20, MED);
                }

                QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), 0, 20, MED);
                QueueController.push(RobotType.MUCKRAKER, makeMessage(Label.EXPLORE), 0.0, 1, MED);
                QueueController.push(RobotType.SLANDERER, makeUpdateMessage(), 0.5, 130, MED);

            }
        },
        LateGame {
            @Override
            void refillQueue() throws GameActionException {
                QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), rc.getInfluence() > 1000 ? 0.8 : 0.5, 20, MED);
                QueueController.push(RobotType.SLANDERER, makeUpdateMessage(), 0.5, 130, MED);
                QueueController.push(RobotType.POLITICIAN, makeMessage(Label.EXPLORE), 0.1, 20, MED);
                QueueController.push(RobotType.MUCKRAKER, makeMessage(Label.EXPLORE), 0.0, 1, MED);
            }
        };

        abstract void refillQueue() throws GameActionException;

    }

    void urgentQueueing() {
        return;
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

    static Message makeUpdateMessage() {
        MapLocation loc = rc.getLocation();
        for (int i = 0; i < ECFound; i++) {
            broadcastECIndex = (broadcastECIndex + 1) % ECFound;
            if (ECTeam[broadcastECIndex] != rc.getTeam()) {
                loc = ECLocs[broadcastECIndex];
                break;
            }
        }
        return makeMessage(Label.EC_UPDATE, loc.x % 128, loc.y % 128, safestDir().ordinal());
    }

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

    static int getBestNeutralEC() {
        int best = -1;
        for (int i = 0; i < ECFound; i++) {
            if (ECTeam[i] != Team.NEUTRAL)
                continue;

            MapLocation location = ECLocs[i];
            int distance = location.distanceSquaredTo(rc.getLocation());
            int influence = ECInfluence[i];

            int actingInfluence = (int) (1.7 * rc.getInfluence());
            if (actingInfluence - influence <= influenceMinimum())
                continue;

            if (best == -1) {
                best = i;
                continue;
            }

            MapLocation bestLocation = ECLocs[best];
            int bestDistance = bestLocation.distanceSquaredTo(rc.getLocation());
            int bestInfluence = ECInfluence[best];

            if (distance + influence < bestDistance + bestInfluence)
                best = i;
        }
        return best;
    }
}
