package refactor;

import battlecode.common.*;

public class Slanderer extends Robot {
    static final int ENEMY_FEAR_ROUNDS = 5; // how long until we go back to hiding instead of fleeing

    static State state = null;

    /* hide vars */
    static Direction hideDir = null;

    /* flee vars */
    static RobotInfo[] enemies = null;
    static int[] dangerByDir = new int[8];
    static int enemyLastSeen = 0;

    @Override
    void onAwake() {
        state = State.Hide; // Slanderers always initialize to hiding!
        if (assignment != null && assignment.label == Communication.Label.HIDE)
            hideDir = fromOrdinal(assignment.data[0]);
        else
            hideDir = randomDirection();
        Nav.doGoInDir(hideDir);
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
    void transition() {
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        int numMuckrakers = 0;
        for (int i = 0; i < enemies.length; i++)
            if (enemies[i].getType() == RobotType.MUCKRAKER)
                numMuckrakers++;

        // track when we last saw enemies
        enemyLastSeen++;
        if (numMuckrakers > 0)
            enemyLastSeen = 0;

        // Hide -> Flee
        if (state == State.Hide && numMuckrakers > 0) {
            state = State.Flee;
            dangerByDir = new int[8]; // zero out the dangers!
        }

        // Flee -> Hide
        if (state == State.Flee && enemyLastSeen > ENEMY_FEAR_ROUNDS) {
            state = State.Hide; // TODO: Do I want to change the hide direction?
            Nav.doGoTo(randomHoverLocation(5));
        }
    }

    private enum State {
        Hide {
            @Override
            public void act() throws GameActionException {
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
                if (move == null) {
                    Nav.doGoTo(randomHoverLocation(5));
                }
            }
        },
        Flee {
            @Override
            public void act() throws GameActionException {
                // Update the danger estimation
                for (RobotInfo enemy : enemies) {
                    if (enemy.type != RobotType.MUCKRAKER) continue;

                    int dangerDir = currentLocation.directionTo(enemy.location).ordinal();

                    dangerByDir[(dangerDir + 7) % 8]++;
                    dangerByDir[dangerDir] += 2;
                    dangerByDir[(dangerDir + 1) % 8]++;
                }

                // Calculate the minimum danger available
                int minDanger = Integer.MAX_VALUE;
                for (int i = 0; i < 8; i++)
                    minDanger = Math.min(minDanger, dangerByDir[i]);

                // Of the minimum dangers, pick the one with the highest passability.
                int dir = -1;
                double dirPassability = 0.0;
                for (int i = 0; i < 8; i++) {
                    if (rc.canMove(fromOrdinal(i)) && dangerByDir[i] == minDanger) {
                        double passability = rc.sensePassability(currentLocation.add(fromOrdinal(i)));
                        if (passability > dirPassability) {
                            dirPassability = passability;
                            dir = i;
                        }
                    }
                }

                // Move in direction of least danger!
                if (dir != -1) rc.move(fromOrdinal(dir));
            }
        };

        public abstract void act() throws GameActionException; // Take a single action in accordance with the state
    }

    static MapLocation randomHoverLocation(double radius) {
        double angle = 2 * Math.PI * Math.random();
        int x = (int) (radius * Math.cos(angle));
        int y = (int) (radius * Math.sin(angle));
        System.out.println("angle " + angle);
        return centerLoc.translate(x, y);
    }
}
