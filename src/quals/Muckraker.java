package quals;

import battlecode.common.*;

import static quals.Communication.Label.ATTACKING;
import static quals.Communication.decode;

public class Muckraker extends Robot {
    static final int CLOG_BEHAVIOR_THRESHOLD = 9; // If we are within this r^2, we manually path instead of using Nav

    static State state = null;

    /* Scout vars */
    static Direction scoutDir;

    /* clog vars */
    static MapLocation enemyECLoc;
    static MapLocation[] cloggedEnemies = new MapLocation[12];
    static int numCloggedEnemies = 0;

    /* defend vars */
    static Direction defendDir;
    static int followingTurns;
    static int followingCooldown = 5;

    @Override
    void onAwake() {
        seenECs[numSeenECs++] = centerID;
        state = State.ChillAroundEC; // by default, just sit around the EC

        switch (assignment.label) {
            case SCOUT:
                state = State.Scout;
                scoutDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(scoutDir);
                break;
            case EXPLORE:
                state = State.Explore;
                Nav.doExplore();
                break;
            case SAFE_DIR:
                state = State.Defend;
                Nav.doGoInDir(fromOrdinal(assignment.data[0]));
                break;
            case ATTACK_LOC:
                state = State.Clog;
                enemyECLoc = getLocFromMessage(assignment.data[0], assignment.data[1]);
                Nav.doGoTo(enemyECLoc);
                break;
            default:
                System.out.println("ERROR: Muckraker has been given a bad assignment");
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
        // Scout -> Explore
        if (state == State.Scout && Nav.currentGoal == Nav.NavGoal.Nothing) {
            state = State.Explore;
            Nav.doExplore();
        }

        int slandererNearby = closestSlanderer();
        if (slandererNearby == -1 && state == State.ChaseSlanderer) { // ChaseSlanderer -> Explore
            state = State.Explore;
            Nav.doExplore();
        } else if (slandererNearby != -1 && state != State.Scout) { // Explore -> ChaseSlanderer
            state = State.ChaseSlanderer;
            Nav.doFollow(slandererNearby);
        }

        // update defend direction
        if (state == State.Defend) {
            if (centerID != rc.getID() && rc.canGetFlag(centerID)) {
                int flag = rc.getFlag(centerID);
                if (flag != 0) {
                    Communication.Message msg = decode(flag);
                    if (msg.label == Communication.Label.SAFE_DIR) {
                        defendDir = fromOrdinal(msg.data[0]);
                        Nav.doGoInDir(defendDir);
                    }
                }
            }
        }

        // Defend -> Explore
        if (state == State.Defend && Nav.currentGoal == Nav.NavGoal.GoInDir) {
            MapLocation checkLoc = rc.getLocation().translate(defendDir.dx * 3, defendDir.dy * 3);
            if (!rc.onTheMap(checkLoc)) {
                followingTurns = 0;
                followingCooldown = 5;
                state = State.Explore;
                Nav.doExplore();
            }
        }

        // Explore -> Defend
        boolean canSeeMuckraker = false;
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER) {
                canSeeMuckraker = true;
                break;
            }
        }
        if (state == State.Explore && followingCooldown <= 0 && canSeeMuckraker) {
            followingCooldown = 5;
            state = State.Defend;
            defendDir = randomDirection();
        }

        if (state == State.Defend && followingTurns > 5) {
            followingTurns = 0;
            followingCooldown = 5;
            state = State.Explore;
            Nav.doExplore();
        }

        // Clog -> Explore (when the enemy EC we wanted to attack has been converted or clogged)
        if (state == State.Clog && (rc.canSenseLocation(enemyECLoc) &&
                rc.senseRobotAtLocation(enemyECLoc).getTeam() != rc.getTeam().opponent()) || isKnownClogged(enemyECLoc)) {
            state = State.Explore;
            Nav.doExplore();
        }

        // Explore -> Clog
        if (state == State.Explore) {
            // If we see a nearby enemy EC we should clog it:
            MapLocation closestEnemy = getNearbyEnemyEC();
            if (closestEnemy != null) {
                state = State.Clog;
            }
        }

        // update clogging locations
        if (state == State.Clog) {
            MapLocation closestAttackLoc = getClosestAttackLoc();
            if (closestAttackLoc != null && isKnownClogged(closestAttackLoc)) {
                removeAttackLoc(closestAttackLoc);
            }
        }

        // Clog -> Defend
        MapLocation closestAttackLoc = getClosestAttackLoc();
        if (state == State.Clog && closestAttackLoc == null) {
            state = State.Defend;
            defendDir = randomDirection();
            Nav.doGoInDir(defendDir);
        } else if (state == State.Clog) {
            Nav.doGoTo(closestAttackLoc);
        }


        // Explore -> ChillAroundEC
        if (state == State.Explore && Nav.currentGoal == Nav.NavGoal.Nothing)
            state = State.ChillAroundEC;
    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException {
                noteNearbyECs();

                if (!rc.isReady()) return;

                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        Clog {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                // See if we need to move out of the way for a politician
                RobotInfo attackingPolitician = adjacentAttackingPolitician();
                if (attackingPolitician != null) { // adjacent
                    MapLocation loc = attackingPolitician.getLocation();
                    int awayDir = enemyECLoc.directionTo(loc).ordinal();

                    for (int rot : new int[]{6, 2, 7, 1, 0}) { // ideally we move perpendicular.
                        Direction targetDir = fromOrdinal((awayDir + rot) % 8);
                        if (rc.canMove(targetDir)) {
                            takeMove(targetDir);
                            return;
                        }
                    }
                }

                if (Nav.currentGoal != Nav.NavGoal.GoTo) Nav.doGoTo(enemyECLoc); // don't allow Nav to quit!

                // Just move in the direction of the enemyEC.
                if (currentLocation.distanceSquaredTo(enemyECLoc) > CLOG_BEHAVIOR_THRESHOLD) {
                    Direction move = Nav.tick();
                    if (move != null && rc.canMove(move)) takeMove(move);
                    return;
                }

                // Manually try and find the location closest to the enemy EC to target:
                int currDistToEnemyLoc = enemyECLoc.distanceSquaredTo(currentLocation);
                for (int[] translation : TRANSLATIONS) { // closest to furthest away
                    MapLocation target = enemyECLoc.translate(translation[0], translation[1]);
                    if (target.distanceSquaredTo(enemyECLoc) >= currDistToEnemyLoc) break;

                    if (rc.canDetectLocation(target) && !rc.isLocationOccupied(target)) {
                        Nav.doGoTo(target);
                        Direction move = Nav.tick();
                        if (move != null && rc.canMove(move)) {
                            takeMove(move);
                            break;
                        }
                    }
                }
            }
        },

        Explore {
            @Override
            public void act() throws GameActionException {
                noteNearbyECs();
                if (trySlandererKill()) return;
                Direction move = Nav.tick();
                followingCooldown--;
                if (move != null && rc.canMove(move)) takeMove(move);

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
                    int log = (int) (Math.log(info.getConviction()) / Math.log(2)) + 1;
                    if (info.getTeam() == rc.getTeam().opponent()) { // Enemy EC message...
                        flagMessage(Communication.Label.ENEMY_EC, loc.x % 128, loc.y % 128, Math.min(log, 15));
                    } else if (info.getTeam() == Team.NEUTRAL) { // Neutral EC message...
                        flagMessage(Communication.Label.NEUTRAL_EC, loc.x % 128, loc.y % 128, Math.min(log, 15));
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
        Defend {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                RobotInfo closestEnemy = null;
                for (RobotInfo enemy : nearby) {
                    if (enemy.getType() == RobotType.MUCKRAKER &&
                            enemy.getTeam() != rc.getTeam()) { // Enemy Muckraker
                        if (closestEnemy == null ||
                                enemy.getLocation().distanceSquaredTo(currentLocation)
                                        < closestEnemy.getLocation().distanceSquaredTo(currentLocation)) {
                            Nav.doFollow(enemy.getID());
                            closestEnemy = enemy;
                        }
                    }
                }
                if (closestEnemy == null) { // Generic defense.
                    Nav.doGoInDir(defendDir);
                }
                if (Nav.currentGoal == Nav.NavGoal.Follow)
                    followingTurns++;
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) {
                    takeMove(move);
                }
            }
        };

        public abstract void act() throws GameActionException;

    }

    static RobotInfo adjacentAttackingPolitician() throws GameActionException {
        RobotInfo biggestPolitician = null;
        int myDir = currentLocation.directionTo(enemyECLoc).ordinal();

        for (RobotInfo info : rc.senseNearbyRobots(2, rc.getTeam())) {
            // Must be an attacking politician on our team
            int flag = rc.getFlag(info.ID);
            if (flag == 0 || decode(flag).label != ATTACKING) continue;

            // must not already be adjacent to an enemy EC:
            if (info.location.isWithinDistanceSquared(enemyECLoc, 2)) continue;

            // must be within 45 degrees of us
            if (Math.abs(myDir - info.location.directionTo(enemyECLoc).ordinal()) >= 2) continue;

            // pick the closest one
            if (biggestPolitician == null || info.influence > biggestPolitician.influence)
                biggestPolitician = info;
        }
        return biggestPolitician;
    }

    static int closestSlanderer() {
        int slandererNearby = -1, distanceToSlanderer = -1;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam() && info.getType() == RobotType.SLANDERER) {
                int distance = rc.getLocation().distanceSquaredTo(info.getLocation());
                if (slandererNearby == -1 || distance < distanceToSlanderer) {
                    slandererNearby = info.getID();
                    distanceToSlanderer = distance;
                }
            }
        }
        return slandererNearby;
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

    static boolean isKnownClogged(MapLocation targetEC) {
        if (targetEC == null || currentLocation.isWithinDistanceSquared(targetEC, 2))
            return false; // invalid target OR part of the essential clog

        boolean[] cloggedInDir = new boolean[8];
        for (RobotInfo info : nearby)
            if (info.getTeam() == rc.getTeam() && info.location.isWithinDistanceSquared(targetEC, 2) &&
                    info.getType() != RobotType.ENLIGHTENMENT_CENTER)
                cloggedInDir[targetEC.directionTo(info.location).ordinal()] = true;

        // can see it- update the cache
        if (cloggedInDir[0] && cloggedInDir[1] && cloggedInDir[2] && cloggedInDir[3] &&
                cloggedInDir[4] && cloggedInDir[5] && cloggedInDir[6] && cloggedInDir[7]) {
            cloggedEnemies[(numCloggedEnemies++) % 12] = targetEC;
            return true;
        }

        // can't see it- check the cache
        for (int i = 0; i < Math.min(numCloggedEnemies, 12); i++)
            if (cloggedEnemies[i].distanceSquaredTo(targetEC) == 0)
                return true;
        return false;
    }

    public static final int[][] TRANSLATIONS = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
            {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1},
            {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-3, 0}, {0, -3}, {0, 3}, {3, 0}};
}
