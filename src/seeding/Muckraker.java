package seeding;

import battlecode.common.*;

import static seeding.Communication.decode;

public class Muckraker extends Robot {
    static final int CLOG_BEHAVIOR_THRESHOLD = 9; // If we are within this r^2, we manually path instead of using Nav

    static State state = null;

    /* clog vars */
    static MapLocation enemyECLoc;

    /* ec tracking vars */
    static int[] seenECs = new int[12];
    static int numSeenECs = 0;

    @Override
    void onAwake() {
        seenECs[numSeenECs++] = centerID;
        state = State.ChillAroundEC; // by default, just sit around the EC

        switch (assignment.label) {
            case EXPLORE:
                state = State.Explore;
                Nav.doExplore();
                break;
            case ATTACK_LOC:
                state = State.Clog;
                enemyECLoc = getLocFromMessage(assignment.data[0], assignment.data[1]);
                Nav.doGoTo(enemyECLoc);
                break;
            default:
                System.out.println("ERROR: Muckraker has been given a bad assignment");
//                rc.resign(); // TODO: remove this before we submit...
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        transition();
        state.act();
        Clock.yield();
    }

    void transition() throws GameActionException {
        RobotInfo[] enemies = nearby;

        // Chase a Slanderer if possible
        int slandererNearby = -1;
        int distanceToSlanderer = -1;
        for (RobotInfo info : enemies) {
            if (info.getTeam() != rc.getTeam() && info.getType() == RobotType.SLANDERER) {
                int distance = rc.getLocation().distanceSquaredTo(info.getLocation());
                if (slandererNearby == -1 || distance < distanceToSlanderer) {
                    slandererNearby = info.getID();
                    distanceToSlanderer = distance;
                }
            }
        }
        if (slandererNearby == -1 && state == State.ChaseSlanderer) {
            state = State.Explore;
            Nav.doExplore();
        } else if (slandererNearby != -1) {
            state = State.ChaseSlanderer;
            Nav.doFollow(slandererNearby);
        }

        // Clog -> Explore (when the enemy EC we wanted to attack has been converted)
        if (state == State.Clog && rc.canSenseLocation(enemyECLoc) &&
                rc.senseRobotAtLocation(enemyECLoc).getTeam() != rc.getTeam().opponent()) {
            state = State.Explore;
            Nav.doExplore();
        }

        // Explore -> Clog
        if (state == State.Explore) {
            for (RobotInfo info : nearby) {
                if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    MapLocation loc = info.getLocation();
                    flagMessage(
                            Communication.Label.ENEMY_EC,
                            loc.x % 128,
                            loc.y % 128,
                            Math.min(15, (int) (Math.log(info.getInfluence()) / Math.log(2) + 1))
                    );

                    // We have seen an enemy EC: we should clog it:
                    enemyECLoc = info.getLocation();
                    Nav.doGoTo(enemyECLoc);
                    state = State.Clog;
                    break;
                } else if (info.getTeam() == Team.NEUTRAL) {
                    MapLocation loc = info.getLocation();
                    double log = Math.log(info.getConviction()) / Math.log(2);
                    flagMessage(
                            Communication.Label.NEUTRAL_EC,
                            loc.x % 128,
                            loc.y % 128,
                            (int) log + 1
                    );
                }
            }
            // If creating EC says attack location, then switch to Clog
            if (rc.canGetFlag(centerID)) {
                int flag = rc.getFlag(centerID);
                if (flag != 0) {
                    Communication.Message msg = decode(flag);
                    if (msg.label == Communication.Label.ATTACK_LOC) {
                        enemyECLoc = getLocFromMessage(msg.data[0], msg.data[1]);
                        state = State.Clog;
                        Nav.doGoTo(enemyECLoc);
                    }
                }
            }
        }

        // Explore -> ChillAroundEC
        if (state == State.Explore && Nav.currentGoal == Nav.NavGoal.Nothing)
            state = State.ChillAroundEC;
    }

    private enum State {
        Clog {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                if (Nav.currentGoal != Nav.NavGoal.GoTo)
                    Nav.doGoTo(enemyECLoc); // don't allow Nav.goTo to quit

                if (currentLocation.distanceSquaredTo(enemyECLoc) > CLOG_BEHAVIOR_THRESHOLD) {
                    Direction move = Nav.tick();
                    if (move != null && rc.canMove(move)) rc.move(move);

                } else {
                    // Manually try and find the location closest to the enemy EC to target:
                    for (int[] translation : TRANSLATIONS) {
                        MapLocation target = enemyECLoc.translate(translation[0], translation[1]);

                        // If this location is available, and closer than we currently are:
                        if (rc.canDetectLocation(target) && !rc.isLocationOccupied(target) &&
                                enemyECLoc.distanceSquaredTo(target) < enemyECLoc.distanceSquaredTo(currentLocation)) {
                            Nav.doGoTo(target);
                            Direction move = Nav.tick();
                            if (move != null && rc.canMove(move)) {
                                rc.move(move);
                                break;
                            }
                        }
                    }
                }
            }
        },
        Explore {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);

                // Inform about nearby ECs
                for (RobotInfo info : rc.senseNearbyRobots()) {
                    if (info.getType() != RobotType.ENLIGHTENMENT_CENTER) continue;

                    if (seenECs[0] == info.ID || seenECs[1] == info.ID || seenECs[2] == info.ID || seenECs[3] == info.ID ||
                            seenECs[4] == info.ID || seenECs[5] == info.ID || seenECs[6] == info.ID || seenECs[7] == info.ID ||
                            seenECs[8] == info.ID || seenECs[9] == info.ID || seenECs[10] == info.ID || seenECs[11] == info.ID) {
                        continue; // we don't want to note it again
                    }
                    seenECs[(numSeenECs++) % 12] = info.getID();

                    MapLocation loc = info.getLocation();

                    if (info.getTeam() == rc.getTeam().opponent()) { // Enemy EC message...
                        double log = Math.log(info.getConviction()) / Math.log(2);
                        flagMessage(Communication.Label.ENEMY_EC, loc.x % 128, loc.y % 128, (int) log + 1);
                    } else if (info.getTeam() == Team.NEUTRAL) { // Neutral EC message...
                        double log = Math.log(info.getConviction()) / Math.log(2);
                        flagMessage(Communication.Label.NEUTRAL_EC, loc.x % 128, loc.y % 128, (int) log + 1);
                    } else {
                        flagMessage(Communication.Label.OUR_EC, loc.x % 128, loc.y % 128);
                    }

                    return;
                }
            }
        },
        ChillAroundEC {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Nav.doGoInDir(randomDirection());
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        },
        ChaseSlanderer {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        };

        public abstract void act() throws GameActionException;
    }

    static boolean trySlandererKill() throws GameActionException {
        RobotInfo[] neighbors = rc.senseNearbyRobots(RobotType.MUCKRAKER.actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo best = null;
        int bestInfluence = 0;
        for (RobotInfo info : neighbors) {
            if (info.getType() == RobotType.SLANDERER) {
                int influence = info.getInfluence();
                if (influence > bestInfluence) {
                    bestInfluence = influence;
                    best = info;
                }
            }
        }

        if (best != null && rc.canExpose(best.ID)) {
            rc.expose(best.getLocation());
            return true;
        }
        return false;
    }

    public static final int[][] TRANSLATIONS = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
            {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1},
            {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-3, 0}, {0, -3}, {0, 3}, {3, 0}};
}
