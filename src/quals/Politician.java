package quals;

import battlecode.common.*;
import quals.utils.IterableIdSet;

import static quals.Communication.*;

public class Politician extends Robot {
    static State state = null;

    /* Defense vars */
    static Direction defendDir;
    static Direction prevSafeDir;
    static int followingTurns = 0;
    static final int DEFEND_ROUNDS = 50;
    static int lag = 10;
    static int lastMessage = 0;
    // set of ids for tracking slanderers
    static IterableIdSet trackedIds = new IterableIdSet();

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    @Override
    void onAwake() throws GameActionException {
        state = State.Explore; // By default, we explore!
        if (assignment != null && assignment.label == Label.UNCLOG) {
            state = State.Unclog;
            rc.setFlag(encode(makeMessage(Label.UNCLOG)));
        }
        Nav.doExplore();
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        updateLastMessage();
        transition();
        state.act();
        Clock.yield();
    }

    void transition() throws GameActionException {
        if (state == State.Unclog) return;

        // consider defense
        // TODO: make DEFEND_ROUND depend on politician influence
        if (rc.getRoundNum() < firstTurn + (DEFEND_ROUNDS * 2 - rc.getInfluence())) {
            if (rc.getID() % 2 == 0) {
                state = State.DefendSlanderer;
            } else {
                state = State.DefendEC;
                if (defendDir == null) defendDir = randomDirection();
                Nav.doGoTo(getTargetLoc());
            }
            return;
        }

        if (state == State.DefendSlanderer && lastMessage < 10) {
            return;
        }

        // consider attack loc
        if (numAttackLocs > 0 && rc.getInfluence() > 100) {
            state = State.AttackLoc;
            targetECLoc = getClosestAttackLoc();
            Nav.doGoTo(targetECLoc);
            return;
        }

        state = State.Explore;
        Nav.doExplore();
    }

    private enum State {
        Explore {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                int radius = getBestEmpowerRadius(0.7);
                if (radius != -1) {
                    rc.empower(radius);
                    return;
                }

                // otherwise move
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        AttackLoc {
            @Override
            public void act() throws GameActionException {
                // if we are in range of our attacking location, switch to attacking message
                if (rc.getLocation().isWithinDistanceSquared(targetECLoc, 36)) {
                    int readyToGo = (rc.getCooldownTurns() <= 1) ? 1 : 0;
                    flagMessage(Communication.Label.ATTACKING, firstTurn / 6, readyToGo);
                }
                if (!rc.isReady()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);

                if (rc.getLocation().isWithinDistanceSquared(targetECLoc, 36)) {
                    int radius = getBestEmpowerRadius(0.5);
                    if (radius != -1 && rc.canEmpower(radius))
                        rc.empower(radius);
                }
                if (move == null) // don't give up
                    Nav.doGoTo(targetECLoc);
            }
        },
        DefendSlanderer {
            public void act() throws GameActionException {
                // update defend direction
                updateDefendDir();

                if (defendDir == null) {
                    defendDir = randomDirection();
                }

                if (prevSafeDir != null && centerLoc.directionTo(rc.getLocation()).equals(defendDir)) {
                    Nav.doGoInDir(prevSafeDir);
                } else {
                    prevSafeDir = null;
                    Nav.doGoInDir(defendDir);
                }

                if (Nav.currentGoal == Nav.NavGoal.Nothing && centerLoc.isWithinDistanceSquared(rc.getLocation(), 36)) {
                    Nav.doGoTo(getTargetLoc());
                }

                if (!rc.isReady()) return;

                defenseLogic(false);
            }
        },
        DefendEC {
            public void act() throws GameActionException {
                updateDefendDir();
                Nav.doGoTo(getTargetLoc());
                if (!rc.isReady()) return;

                defenseLogic(true);
            }
        },
        Unclog {
            public void act() throws GameActionException {
                Team opponent = rc.getTeam().opponent();
                int bestKills = 0;
                int bestRadius = 0;
                for (int radius = 1; radius <= RobotType.POLITICIAN.actionRadiusSquared; radius++) {
                    RobotInfo[] neighbors = rc.senseNearbyRobots(radius);
                    if (neighbors.length == 0) continue;
                    int attackConviction = (rc.getConviction() - GameConstants.EMPOWER_TAX) / neighbors.length;
                    int kills = 0;
                    for (RobotInfo neighbor : neighbors) {
                        if (neighbor.getTeam() == opponent && neighbor.getConviction() <= attackConviction)
                            kills++;
                    }
                    if (kills > bestKills) {
                        bestKills = kills;
                        bestRadius = radius;
                    }
                }
                if (bestKills >= 4 && rc.canEmpower(bestRadius)) {
                    rc.empower(bestRadius);
                    return;
                }
            }
        };

        public abstract void act() throws GameActionException;

    }

    /**
     * Whether we can be converted by enemy forces, if they focus solely on us.
     *
     * @return boolean
     */
    static boolean canBeConverted() {
        RobotInfo[] enemiesNearby = rc.senseNearbyRobots(9, rc.getTeam().opponent());
        int maximumPossibleAttack = 0;
        for (RobotInfo enemy : enemiesNearby) {
            if (enemy.getType() == RobotType.POLITICIAN) {
                maximumPossibleAttack += enemy.getInfluence() - GameConstants.EMPOWER_TAX;
            }
        }
        return maximumPossibleAttack >= rc.getConviction();
    }

    static void updateLastMessage() throws GameActionException {
        lastMessage++;
        RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo robot: friendly) {
            int id = robot.getID();
            if (rc.canGetFlag(id)) {
                int flag = rc.getFlag(id);
                if (flag != 0) {
                    Message message = decode(flag);
                    if (message != null && message.label == Label.SLANDERER) {
                        lastMessage = 0;
                        trackedIds.add(id);
                    }
                }
            }
        }
    }

    static void updateDefendDir() throws GameActionException {
        if (centerID != rc.getID() && rc.canGetFlag(centerID)) {
            int flag = rc.getFlag(centerID);
            if (flag != 0) {
                Communication.Message msg = decode(flag);
                if (msg.label == Communication.Label.EC_UPDATE) {
                    Direction newDir = fromOrdinal(msg.data[2]);
                    if (!newDir.equals(defendDir)) {
                        prevSafeDir = defendDir;
                        defendDir = newDir;
                        Nav.doGoInDir(defendDir);
                    }
                }
            }
        }
    }

    static void defenseLogic(boolean ecDefense) throws GameActionException {
        RobotInfo closestEnemy = null;
        for (RobotInfo enemy : nearby) {
            if (enemy.getType() == RobotType.MUCKRAKER &&
                    enemy.getTeam() != rc.getTeam()) { // Enemy Muckraker
                if (closestEnemy == null ||
                        enemy.getLocation().distanceSquaredTo(rc.getLocation())
                                < closestEnemy.getLocation().distanceSquaredTo(rc.getLocation())) {
                    Nav.doFollow(enemy.getID());
                    closestEnemy = enemy;
                }
            }
        }

        if (closestEnemy != null) {
            // See if another defending robot can get to the enemy first
            for (RobotInfo bot : nearby) {
                if (bot.getType() == RobotType.POLITICIAN
                        && bot.getTeam() == rc.getTeam()
                        && bot.getLocation().isWithinDistanceSquared(closestEnemy.getLocation(), 2)
                ) {
                    int flag = rc.getFlag(bot.getID());
                    if (flag != 0 && decode(flag).label == Communication.Label.CURRENTLY_DEFENDING) {
                        closestEnemy = null;
                        break;
                    }
                }
            }
            if (closestEnemy != null // Note that we are the one to defend against this robot!
                    && closestEnemy.getLocation().isWithinDistanceSquared(rc.getLocation(), 2)) {
                flagMessage(Communication.Label.CURRENTLY_DEFENDING);
            }
        }

        if (closestEnemy == null && (Nav.currentGoal == Nav.NavGoal.Nothing || Nav.currentGoal == Nav.NavGoal.Follow)) { // Generic defense.
            followingTurns = 0;
            if (ecDefense) {
                Nav.doGoTo(getTargetLoc());
            } else {
                if (prevSafeDir != null && centerLoc.directionTo(rc.getLocation()).equals(defendDir)) {
                    Nav.doGoInDir(prevSafeDir);
                } else {
                    Nav.doGoInDir(defendDir);
                }
                if (centerLoc.isWithinDistanceSquared(rc.getLocation(), 36)) {
                    Nav.doGoInDir(defendDir.opposite());
                }
            }
        }

        // Consider exploding
        double threshold = 0.6;
        if (!ecDefense) {
            // defend slanders aggressively
            threshold = 0.1;
        }
        int radius = getBestEmpowerRadius(threshold);
        if (radius != -1) rc.empower(radius);

        // Consider moving
        Direction move = Nav.tick();
        if (Nav.currentGoal == Nav.NavGoal.Follow)
            followingTurns++;
        if (move != null && rc.canMove(move)) {
            takeMove(move);
        }
    }


    static double empowerEfficiency(int range) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        if (nearbyRobots.length == 0) {
            return 0;
        }

        double effectiveInfluence = (rc.getConviction() - GameConstants.EMPOWER_TAX) * rc.getEmpowerFactor(rc.getTeam(), 0);
        if (effectiveInfluence <= 0) {
            return 0;
        }
        double baseInfluence = rc.getConviction() - GameConstants.EMPOWER_TAX;
        double perUnit = effectiveInfluence / nearbyRobots.length;

        double usefulInfluence = 0;
        int numMuckrakersKilled = 0;
        int num_conversions = 0;

        for (int i = 0; i < nearbyRobots.length; i++) {
            RobotInfo info = nearbyRobots[i];
            if (info.getTeam() == rc.getTeam().opponent()) {
                // opponent unit
                if (info.getType() == RobotType.MUCKRAKER) {
                    // killing muckrakers far away from our EC is a waste
                    // 65 ~= (4.5 + sqrt(12))^2
                    usefulInfluence += info.getConviction();
                    if (perUnit >= info.getConviction())
                        numMuckrakersKilled++;
                } else if (info.getType() == RobotType.ENLIGHTENMENT_CENTER
                        && range <= 2 && shouldAttackEC(info)) {
                    // test if enemy EC is surrounded and we have backup
                    return 1;
                } else if (info.getType() != RobotType.POLITICIAN || info.getInfluence() > GameConstants.EMPOWER_TAX) {
                    usefulInfluence += perUnit;
                    if (info.getType() == RobotType.POLITICIAN && info.getConviction() < perUnit) {
                        num_conversions++;
                        usefulInfluence += Math.max(0,
                                Math.min(perUnit - info.getConviction(), info.getInfluence())
                                        - GameConstants.EMPOWER_TAX);
                    }
                }
            } else {
                // friendly / neutral unit
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    if (info.getTeam() == Team.NEUTRAL && rc.getLocation().isWithinDistanceSquared(info.getLocation(), 2))
                        return 100;
                } else {
                    usefulInfluence += info.getInfluence() - info.getConviction();
                }
            }
        }

        // calculate efficiency out of non-buff influence
        double efficiency = usefulInfluence / baseInfluence;

        // if we can convert two politicians, it is worth it to get 2 more turns
        // also, if following turns > 4, explode
        if (num_conversions > 1 || followingTurns > 4)
            return numMuckrakersKilled;
        if (state == State.DefendEC || state == state.DefendSlanderer)
            return numMuckrakersKilled * efficiency;
        return efficiency;
//            return num_conversions > 1 || followingTurns > 4 ? numMuckrakersKilled : efficiency;
    }

    static MapLocation getTargetLoc() throws GameActionException {
        int radius = 4;
        Direction dangerDir = defendDir.opposite();
        MapLocation targetLoc = centerLoc.translate(dangerDir.dx * radius, dangerDir.dy * radius);
        targetLoc = targetLoc.translate(dangerDir.dx * (int) (Math.random() * (radius / 2)), dangerDir.dy * (int) (Math.random() * (radius / 2)));
        if ((targetLoc.x + targetLoc.y) % 2 != 0) {
            targetLoc = targetLoc.translate(dangerDir.dx, 0);
        }
        return targetLoc;
    }

    /**
     * Picks the most efficient speech radius. Returns -1 if no radius is better
     * than the provided threshold
     *
     * @param threshold the minimum speech efficiency to consider
     * @return the best speech radius (-1 if no radius is good)
     */
    static int getBestEmpowerRadius(double threshold) throws GameActionException {
        int bestRad = -1;
        double bestEff = threshold;

        for (int i = 1; i <= RobotType.POLITICIAN.actionRadiusSquared; i++) {
            double efficiency = empowerEfficiency(i);
            if (efficiency > bestEff) {
                bestEff = efficiency;
                bestRad = i;
            }
        }

        return bestRad;
    }

    static boolean shouldAttackEC(RobotInfo ec) throws GameActionException {
        if (!(rc.getLocation().distanceSquaredTo(ec.getLocation()) <= 2))
            return false;
        MapLocation ecLoc = ec.getLocation();
        for (Direction dir : directions) {
            MapLocation adjLoc = ecLoc.add(dir);
            if (rc.onTheMap(adjLoc) && rc.senseRobotAtLocation(adjLoc) == null)
                return false;
        }
        int reverseDir = ecLoc.directionTo(rc.getLocation()).ordinal();
        Direction[] backupDirs = {
                directions[reverseDir],
                directions[(reverseDir + 1) % 8],
                directions[(reverseDir + 7) % 8]
        };
        for (Direction dir : backupDirs) {
            RobotInfo info = rc.senseRobotAtLocation(rc.getLocation().add(dir));
            if (info == null || info.getTeam() != rc.getTeam()) continue;
            Communication.Message message = decode(rc.getFlag(info.getID()));
            if (message.label == Communication.Label.ATTACKING
                    && message.data[0] > assignment.data[0]
                    && message.data[1] == 1) {
                System.out.println("expecting backup from " + info.getID());
                return true;
            }

        }
        return false;
    }
}