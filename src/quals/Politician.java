package quals;

import battlecode.common.*;

import static quals.Communication.decode;

public class Politician extends Robot {
    static State state = null;

    /* Defense vars */
    static Direction defendDir;
    static int followingTurns = 0;
    static int defendRadius = 4;
    static int lag = 0;
    static final int DEFEND_ROUND = 50;

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    @Override
    void onAwake() throws GameActionException {
        state = State.Explore; // By default, we explore!
        Nav.doExplore();

        if (assignment != null && assignment.label == Communication.Label.BUFF) {
            state = State.Buffer; // Buffers are a special case...
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
        if (state == State.Buffer) return; // Buffers should NEVER change state.

        // TODO: move this?
        // update defend direction
        if (state == State.Defend && (Nav.currentGoal == Nav.NavGoal.Nothing || Nav.currentGoal == Nav.NavGoal.GoInDir)) {
            if (centerID != rc.getID() && rc.canGetFlag(centerID)) {
                int flag = rc.getFlag(centerID);
                if (flag != 0) {
                    Communication.Message msg = decode(flag);
                    if (msg.label == Communication.Label.EC_UPDATE) {
                        Direction newDir = fromOrdinal(msg.data[2]);
                        if (!newDir.equals(defendDir)) {
                            lag = 5;
                            defendDir = newDir;
                        }
                        if (lag <= 0)
                            Nav.doGoInDir(defendDir);
                    }
                }
            }
        }

        // TODO: we need to tune this so that we always have SOME politicians around our ECs & slanderers?å
        // consider defense
        if (rc.getRoundNum() < DEFEND_ROUND) {
            state = State.Defend;
            return;
        }

        // consider attack
        if (numAttackLocs > 0) {
            state = State.AttackLoc;
            targetECLoc = getClosestAttackLoc();
            Nav.doGoTo(targetECLoc);
            System.out.println("ATTACKING " + targetECLoc);
            return;
        }

        // Defend if you see an enemy muckraker- explore otherwise.
        boolean canSeeMuckraker = false;
        for (RobotInfo bot : nearby) {
            if (bot.getTeam() != rc.getTeam() && bot.getType() == RobotType.MUCKRAKER) {
                canSeeMuckraker = true;
                break;
            }
        }

        if (canSeeMuckraker) {
            state = State.Defend;
        } else {
            state = State.Explore;
            Nav.doExplore();
        }
    }

    private enum State {
        Explore {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                // See if offensive speech is possible.
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
        Defend {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;
                lag--;

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
                    Nav.doGoInDir(defendDir);
                }

                // Consider exploding
                int alliesNearby = 0;
                for (RobotInfo bot : nearby) {
                    if (bot.getTeam() == rc.getTeam() && bot.getType() == RobotType.POLITICIAN)
                        alliesNearby++;
                }
                double threshold = alliesNearby > 15 ? 0.1 : 0.6;
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
        },
        Buffer {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;
                int dist = rc.getLocation().distanceSquaredTo(centerLoc);
                if (rc.senseNearbyRobots(dist).length == 1) {
                    rc.empower(dist);
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


    static double empowerEfficiency(int range) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        if (nearbyRobots.length == 0) {
            return 0;
        }

        double effectiveInfluence = rc.getInfluence() * rc.getEmpowerFactor(rc.getTeam(), 0) - 10;
        if (effectiveInfluence < 0) {
            return 0;
        }
        double baseInfluence = rc.getInfluence() - 10;
        double perUnit = effectiveInfluence / nearbyRobots.length;

        double usefulInfluence = 0;
        int enemies = 0;
        int num_conversions = 0;

        for (int i = 0; i < nearbyRobots.length; i++) {
            RobotInfo info = nearbyRobots[i];
            if (info.getTeam() == rc.getTeam().opponent()) {
                // opponent unit
                enemies++;
                if (info.getType() == RobotType.MUCKRAKER) {
                    // killing muckrakers far away from our EC is a waste
                    // 65 ~= (4.5 + sqrt(12))^2
                    usefulInfluence += info.getConviction();
                    if (followingTurns > 4) return 1 * range;
                } else if (info.getType() == RobotType.ENLIGHTENMENT_CENTER
                        && range <= 2 && shouldAttackEC(info)) {
                    // test if enemy EC is surrounded and we have backup
                    return 1;
                } else if (info.getType() != RobotType.POLITICIAN || info.getInfluence() > GameConstants.EMPOWER_TAX) {
                    usefulInfluence += perUnit;
                    if (info.getType() == RobotType.POLITICIAN && info.getConviction() < perUnit)
                        num_conversions++;
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
        // also, if we can be converted, exploding is better (avoids getting lightninged)
        return num_conversions > 1 || canBeConverted() ? 1 : efficiency;
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