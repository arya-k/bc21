package quals;

import battlecode.common.*;

import static quals.Communication.Message;
import static quals.Communication.decode;

public class Muckraker extends Robot {
    static State state = null;

    /* Scout vars */
    static Direction scoutDir;

    /* Shouldering vars */
    static MapLocation enemyLoc;
    static Direction shoulderingDirection;

    /* attack slanderer vars */
    static MapLocation enemySlanderLoc = null;

    static final int CENTER_DEFEND_RADIUS = 100; // shoulder within 10 blocks of our own EC
    static final int BLOCKING_TURN_LIMIT = 100; // only block for the first 100 turns of existence.
    static int[] OFFSETS = {0, 1, 7, 2, 6};

    @Override
    void onAwake() {
        state = State.Explore; // by default, explore!
        Nav.doExplore();

        switch (assignment.label) {
            case SCOUT:
                state = State.Scout;
                scoutDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(scoutDir);
                break;
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
        // See if the EC has given us an enemy slanderer location...
        if (centerID != rc.getID() && rc.canGetFlag(centerID)) {
            int flag = rc.getFlag(centerID);
            if (flag != 0) {
                Message msg = decode(flag);
                if (msg.label == Communication.Label.SLANDERERS_SEEN) {
//                    enemySlanderLoc = getLocFromMessage(msg.data[0], msg.data[1]);
                }
            }
        }

        // Check if we see enough enemy slanderers:
        int slandererCount = 0, maxSlandererInfluence = -1;
        MapLocation maxSlandererLoc = null;
        for (RobotInfo info : nearby) {
            if (info.getType() == RobotType.SLANDERER && info.getTeam() != rc.getTeam()) {
                slandererCount++;
                if (info.getInfluence() > maxSlandererInfluence) {
                    maxSlandererLoc = info.getLocation();
                    maxSlandererInfluence = info.getInfluence();
                }
            }
        }

        if (slandererCount > 3) // Phone home that there are slanderers at a certain location
            flagMessage(Communication.Label.SLANDERERS_SEEN, maxSlandererLoc.x % 128, maxSlandererLoc.y % 128);

        // If we expect to have seen a slanderer here we need to note that they are no longer there:
        if (slandererCount < 3 && enemySlanderLoc != null &&
                rc.getLocation().isWithinDistanceSquared(enemySlanderLoc, 4))
            enemySlanderLoc = null;

        // Scout -> Explore (or exit)
        if (state == State.Scout) {
            if (Nav.currentGoal == Nav.NavGoal.Nothing) {
                state = State.Explore;
                Nav.doExplore();
            } else {
                return; // Scouts should not get distracted
            }
        }

        // * -> ChaseSlanderer (immediately nearby)
        int nearestSlandererID = closestSlanderer();
        if (nearestSlandererID != -1) {
            state = State.ChaseSlanderer;
            Nav.doFollow(nearestSlandererID);
            return;
        }

        // * -> Shoulder
        if (shouldShoulder() && rc.getRoundNum() <= firstTurn + BLOCKING_TURN_LIMIT) {
            state = State.Shoulder;
            return;
        }
        
        // * -> ChaseSlanderer (from far away)
        if (enemySlanderLoc != null) {
            state = State.ChaseSlanderer;
            Nav.doGoTo(enemySlanderLoc);
            return;
        }

        // * -> Explore
        if (state != State.Explore) {
            state = State.Explore;
            Nav.doExplore();
        }
    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        Explore {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        ChaseSlanderer {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        Shoulder {
            @Override
            public void act() throws GameActionException {
                for (int i : OFFSETS) {
                    MapLocation potential = enemyLoc.add(fromOrdinal((shoulderingDirection.ordinal() + i) % 8));
                    if (rc.canDetectLocation(potential) && !rc.isLocationOccupied(potential)) {
                        Nav.doGoTo(potential);
                        Direction move = Nav.tick();
                        if (move != null && rc.canMove(move)) {
                            takeMove(move);
                            return;
                        }
                    }
                }

                // We can't help shoulder- just explore
                Nav.doExplore();
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        AttackSlanderer {
            @Override
            public void act() throws GameActionException {

            }
        };

        public abstract void act() throws GameActionException;
    }

    /**
     * Determines whether or not an enemy is worth shouldering-
     * if so it sets the enemyMuckraker and shoulderingDirection accordingly.
     *
     * @return whether or not to shoulder someone!
     */
    static boolean shouldShoulder() throws GameActionException {
        enemyLoc = null; // Find the closest enemy muckraker
        for (RobotInfo info : nearby) {
            if (info.getType() != RobotType.MUCKRAKER || info.getTeam() != rc.getTeam().opponent()) continue;
            if (enemyLoc != null && rc.getLocation().distanceSquaredTo(info.getLocation()) >=
                    rc.getLocation().distanceSquaredTo(enemyLoc)) continue;

            // check to make sure there is at least one available spot around the enemy muckraker...
            for (Direction dir : Robot.directions) {
                MapLocation potential = info.getLocation().add(dir);
                if (rc.canDetectLocation(potential) && !rc.isLocationOccupied(potential)) {
                    enemyLoc = info.getLocation();
                    break;
                }
            }
        }

        if (enemyLoc == null) return false;

        // Find our slanderer & EC closest to the enemy
        MapLocation nearestEC = null, nearestSlanderer = null;
        if (centerID != rc.getID() && rc.getLocation().isWithinDistanceSquared(centerLoc, CENTER_DEFEND_RADIUS))
            nearestEC = centerLoc;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam()) continue;
            if (info.getType() == RobotType.SLANDERER) {
                if (nearestSlanderer == null || enemyLoc.distanceSquaredTo(info.getLocation()) <
                        enemyLoc.distanceSquaredTo(nearestSlanderer))
                    nearestSlanderer = info.getLocation();
            }

            if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                if (nearestEC == null || enemyLoc.distanceSquaredTo(info.getLocation()) <
                        enemyLoc.distanceSquaredTo(nearestEC))
                    nearestEC = info.getLocation();
            }
        }

        if (nearestSlanderer != null) {
            shoulderingDirection = enemyLoc.directionTo(nearestSlanderer);
            return true;
        } else if (nearestEC != null) {
            shoulderingDirection = enemyLoc.directionTo(nearestEC);
            return true;
        }
        return false; // there is nothing to really protect.
    }

    static int closestSlanderer() {
        int closestSlandererID = -1, distanceToSlanderer = -1;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam() && info.getType() == RobotType.SLANDERER) {
                int distance = rc.getLocation().distanceSquaredTo(info.getLocation());
                if (closestSlandererID == -1 || distance < distanceToSlanderer) {
                    closestSlandererID = info.getID();
                    distanceToSlanderer = distance;
                }
            }
        }
        return closestSlandererID;
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
}
