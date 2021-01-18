package seeding_bad_econ;

import battlecode.common.*;

import static seeding_bad_econ.Communication.decode;

public class Slanderer extends Robot {
    static State state = null;

    /* hide vars */
    static final int HIDE_RAD = 4;
    static Direction safeDir;

    /* flee vars */
    static RobotInfo[] enemies = null;
    static double[] safetyByDir = new double[8];
    static boolean[] viableLoc = new boolean[8];
    static final double SAFETY_DECAY = 0.9;
    static final int MAX_DIST_FROM_EC = 50;
    static int enemyLastSeen = 0;

    @Override
    void onAwake() throws GameActionException {
        state = State.Hide; // Slanderers always initialize to hiding!
        safeDir = fromOrdinal(assignment.data[0]);
        Nav.doGoTo(getTargetLoc());
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        transition(); // Consider state switches
        state.act(); // Take action based on current state
        Clock.yield();
    }

    /**
     * Takes into account the factors around it to determine whether or not to switch state.
     * If switching state, updates the state variables accordingly.
     */
    void transition() throws GameActionException {
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (centerID != rc.getID() && rc.canGetFlag(centerID)) {
            int flag = rc.getFlag(centerID);
            if (flag != 0) {
                Communication.Message msg = decode(flag);
                if (msg.label == Communication.Label.SAFE_DIR) {
                    safeDir = fromOrdinal(msg.data[0]);
                    Nav.doGoTo(getTargetLoc());
                    state = State.Hide;
                }
            }
        }

        // track when we last saw enemies
        enemyLastSeen++;
        int numEnemies = 0;

        for (RobotInfo bot : enemies) {
            if (bot.getType() == RobotType.MUCKRAKER)
                numEnemies++;
        }


        // Hide -> Flee
        if (numEnemies > 0) {
            enemyLastSeen = 0;
            state = State.Flee;
        } else {
            state = State.Hide;
        }
    }

    private enum State {
        Hide {
            @Override
            public void act() throws GameActionException {
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
                if (move == null) {
                    Nav.doGoTo(getTargetLoc());
                }
            }
        },
        Flee {
            @Override
            public void act() throws GameActionException {
                // Update the danger estimation
                for (RobotInfo enemy : enemies) {
                    int threat = enemy.type == RobotType.MUCKRAKER ? 2 : 1;
                    int safeDir = centerLoc.directionTo(enemy.location).opposite().ordinal();

                    safetyByDir[(safeDir + 6) % 8] += threat;
                    safetyByDir[(safeDir + 7) % 8] += 3 * threat;
                    safetyByDir[(safeDir) % 8] += 5 * threat;
                    safetyByDir[(safeDir + 1) % 8] += 3 * threat;
                    safetyByDir[(safeDir + 2) % 8] += threat;
                }

                if (rc.isReady()) {
                    // Calculate the maximum safety available
                    double maxSafety = 0;
                    for (int i = 0; i < 8; i++) {
                        safetyByDir[i] = safetyByDir[i] * SAFETY_DECAY;
                        if (safetyByDir[i] < 0.1)
                            safetyByDir[i] = 0;

                        viableLoc[i] = rc.canMove(fromOrdinal(i)) && (enemies.length > 0
                                || rc.getLocation()
                                .add(fromOrdinal(i))
                                .isWithinDistanceSquared(centerLoc, MAX_DIST_FROM_EC)
                        );
                        if (viableLoc[i])
                            maxSafety = Math.max(maxSafety, safetyByDir[i]);
                    }

                    // Of the maximum safeties, pick the one with the highest passability.
                    int dir = -1;
                    double dirPassability = 0.0;
                    for (int i = 0; i < 8; i++) {
                        if (viableLoc[i] && Math.abs(safetyByDir[i] - maxSafety) < 0.5) {
                            double passability = rc.sensePassability(currentLocation.add(fromOrdinal(i)));
                            if (passability > dirPassability) {
                                dirPassability = passability;
                                dir = i;
                            }
                        }
                    }

                    // Move in direction of max safety
                    if (dir != -1) takeMove(fromOrdinal(dir));
                }


            }
        };

        public abstract void act() throws GameActionException; // Take a single action in accordance with the state
    }

    static MapLocation randomHoverLocation(double radius) {
        double angle = 2 * Math.PI * Math.random();
        int x = (int) (radius * Math.cos(angle));
        int y = (int) (radius * Math.sin(angle));
        return centerLoc.translate(x, y);
    }

    static MapLocation getTargetLoc() throws GameActionException {
        MapLocation targetLoc = centerLoc.translate(safeDir.dx * HIDE_RAD, safeDir.dy * HIDE_RAD);
        targetLoc = targetLoc.translate(safeDir.dx * (int) (Math.random() * (HIDE_RAD / 2)), safeDir.dy * (int) (Math.random() * (HIDE_RAD / 2)));
        if ((targetLoc.x + targetLoc.y) % 2 != 0) {
            targetLoc = targetLoc.translate(safeDir.dx, 0);
        }
        return targetLoc;
    }
}
