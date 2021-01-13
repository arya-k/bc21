package refactor;

import battlecode.common.*;

import static refactor.Communication.encode;

public class Muckraker extends Robot {
    static final int CLOG_BEHAVIOR_THRESHOLD = 9; // If we are within this r^2, we manually path instead of using Nav

    static State state = null;

    /* clog vars */
    static MapLocation enemyECLoc;

    @Override
    void onAwake() {
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
                rc.resign(); // TODO: remove this before we submit...
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        transition();
        state.act();
        Clock.yield();
    }

    void transition() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots();

        // Clog -> Explore (when the enemy EC we wanted to attack has been converted)
        if (state == State.Clog && rc.canSenseLocation(enemyECLoc) &&
                rc.senseRobotAtLocation(enemyECLoc).getTeam() != rc.getTeam().opponent()) {
            state = State.Explore;
            Nav.doExplore();
        }

        // Explore -> Clog
        if (state == State.Explore) {
            for (RobotInfo info : enemies) {
                if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    rc.setFlag(encode(dangerMessage(info)));

                    // We have seen an enemy EC: we should clog it:
                    enemyECLoc = info.getLocation();
                    Nav.doGoTo(enemyECLoc);
                    state = State.Clog;
                    break;
                } else if (info.getTeam() == Team.NEUTRAL) {
                    rc.setFlag(encode(neutralECMessage(info)));
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

                if (rc.getLocation().distanceSquaredTo(enemyECLoc) > CLOG_BEHAVIOR_THRESHOLD) {
                    Direction move = Nav.tick();
                    if (move != null && rc.canMove(move)) rc.move(move);

                } else {
                    // Manually try and find the location closest to the enemy EC to target:
                    for (int[] translation : TRANSLATIONS) {
                        MapLocation target = enemyECLoc.translate(translation[0], translation[1]);

                        // If this location is available, and closer than we currently are:
                        if (rc.canDetectLocation(target) && !rc.isLocationOccupied(target) &&
                                enemyECLoc.distanceSquaredTo(target) < enemyECLoc.distanceSquaredTo(rc.getLocation())) {
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
