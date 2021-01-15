package seeding;

import battlecode.common.*;

import static seeding.Communication.decode;

public class Politician extends Robot {
    public static final int NEUTRAL_EC_WAIT_ROUNDS = 15;

    static State state = null;

    /* NeutralEC vars */
    static int waitingRounds = 0;
    static boolean foundNeutralNeighbor = false;

    /* Scout vars */
    static Direction scoutDir;

    /* Defense vars */
    static Direction defendDir;
    static MapLocation defendLocation;
    static int defendRadius = 4;

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    @Override
    void onAwake() throws GameActionException {
        state = State.Explore; // By default, we just explore!
        Nav.doExplore();

        if (assignment == null) return;

        switch (assignment.label) {
            case SCOUT:
                state = State.Scout;
                scoutDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(scoutDir);
                break;

            case ATTACK_LOC:
                state = State.AttackLoc;
                targetECLoc = getLocFromMessage(assignment.data[0], assignment.data[1]);
                Nav.doGoTo(targetECLoc);
                break;

            case CAPTURE_NEUTRAL_EC:
                state = State.CaptureNeutralEC;
                targetECLoc = getLocFromMessage(assignment.data[0], assignment.data[1]);
                Nav.doGoTo(targetECLoc);
                break;

            case FINAL_FRONTIER:
                state = State.FinalFrontier;
                break;
            case DEFEND:
                state = State.Defend;
                defendDir = fromOrdinal(assignment.data[0]);
                MapLocation targetLoc = getTargetLoc();
                if (rc.onTheMap(centerLoc.translate(defendDir.dx * 2, defendDir.dy * 2))) {
                    defendLocation = targetLoc;
                    Nav.doGoTo(defendLocation);
                } else {
                    Nav.doExplore();
                    state = State.Explore;
                }
                break;

            case HIDE: // We were once a slanderer! TODO return to sender?
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

        // Scout -> Explore
        if (state == State.Scout && Nav.currentGoal == Nav.NavGoal.Nothing) {
            state = State.Explore;
            Nav.doExplore();
        }

        // Explore -> Explode
        if (state == State.Explore && Nav.currentGoal == Nav.NavGoal.Nothing) {
            state = State.FinalFrontier; // TODO: we should do something more intelligent here probably.
        }

        if (state == State.Explore && rc.canGetFlag(centerID)) {
            int flag = rc.getFlag(centerID);
            if (flag != 0) {
                Communication.Message msg = decode(flag);
                if (msg.label == Communication.Label.ATTACK_LOC) {
                    state = State.AttackLoc;
                    targetECLoc = getLocFromMessage(msg.data[0], msg.data[1]);
                    Nav.doGoTo(targetECLoc);
                }
            }
        }

        // CaptureNeutralEC -> Explore 
        if (state == State.CaptureNeutralEC && waitingRounds >= NEUTRAL_EC_WAIT_ROUNDS && !foundNeutralNeighbor) {
            state = State.Explore;
            Nav.doExplore();
        }

        // TODO: AttackLoc -> Explore if our target EC is now on our team!
    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException { // TODO: Our EC should not remove explorers until they explode!
                // Note enemy & neutral ECs:
                boolean alreadySetFlag = false;
                for (RobotInfo info : nearby) {
                    if (info.getTeam() == rc.getTeam().opponent() &&
                            info.getType() == RobotType.ENLIGHTENMENT_CENTER) { // Enemy EC message...
                        MapLocation loc = info.getLocation();
                        double log = Math.log(info.getConviction()) / Math.log(2);
                        flagMessage(
                                Communication.Label.ENEMY_EC,
                                loc.x % 128,
                                loc.y % 128,
                                (int) log + 1
                        );
                        alreadySetFlag = true;
                    } else if (info.getTeam() == Team.NEUTRAL) { // Neutral EC message...
                        MapLocation loc = info.getLocation();
                        double log = Math.log(info.getConviction()) / Math.log(2);
                        flagMessage(
                                Communication.Label.NEUTRAL_EC,
                                loc.x % 128,
                                loc.y % 128,
                                (int) log + 1
                        );
                        alreadySetFlag = true;
                    }
                }
                if (!alreadySetFlag) {
                    flagMessage(Communication.Label.SCOUT_LOCATION, currentLocation.x % 128, currentLocation.y % 128);
                }

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        },
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
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        },
        CaptureNeutralEC {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                Direction move = Nav.tick();
                if (move != null) {
                    if (rc.canMove(move)) rc.move(move);
                    return;
                }

                // We are at the destination- hopefully adjacent!
                waitingRounds++;
                RobotInfo[] neighbors = rc.senseNearbyRobots(2);
                for (RobotInfo nbor : neighbors) {
                    if (nbor.getTeam() == Team.NEUTRAL) {
                        foundNeutralNeighbor = true;
                        if ((waitingRounds > NEUTRAL_EC_WAIT_ROUNDS ||
                                nbor.getConviction() < (myInfluence - 10) / neighbors.length / 2) && rc.canEmpower(2)) {
                            rc.empower(2);
                        }
                    }
                }
            }
        },
        AttackLoc {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);

                if (move == null) { // TODO: part the seas! also don't give up on Nav movement...
                    int radius = getBestEmpowerRadius(0.6);
                    if (radius != -1 && rc.canEmpower(radius))
                        rc.empower(radius);
                }
            }
        },
        FinalFrontier {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;
                RobotInfo[] enemiesNearby;

                // Figure out which radius maximizes our kills
                int maxKills = 0, maxKillRadius = 0;
                for (int radius = 1; radius <= 9; radius++) {
                    enemiesNearby = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
                    if (enemiesNearby.length == 0) continue;
                    int attackPower = (int) (
                            (rc.getInfluence() * rc.getEmpowerFactor(rc.getTeam(), 0)
                                    - GameConstants.EMPOWER_TAX) / enemiesNearby.length
                    );

                    int currentKills = 0;
                    for (RobotInfo enemy : enemiesNearby)
                        if (attackPower > enemy.getConviction())
                            currentKills++;

                    if (currentKills > maxKills) {
                        maxKills = currentKills;
                        maxKillRadius = radius;
                    }
                }
                if (maxKills > 0) rc.empower(maxKillRadius);

                // We can't kill anybody- but if we could be converted, or can hit three enemies, then we attack anyways.
                enemiesNearby = rc.senseNearbyRobots(9, rc.getTeam().opponent());
                if (enemiesNearby.length >= 3 || canBeConverted()) {
                    int radius = getBestEmpowerRadius(0.1);
                    if (radius == -1) radius = 9; // default to 9.
                    rc.empower(radius);
                    return;
                }

            }
        },
        Defend {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                if (Nav.currentGoal == Nav.NavGoal.Nothing) {
                    int numTroopsCloser = 0;
                    int myDist = centerLoc.distanceSquaredTo(currentLocation);
                    for (RobotInfo bot : nearby) {
                        if (bot.getTeam() == rc.getTeam() && myDist > bot.getLocation().distanceSquaredTo(centerLoc)) {
                            numTroopsCloser++;
                        }
                    }
                    if (numTroopsCloser > 5) {
                        defendRadius += 2;
                        defendLocation = getTargetLoc();
                    }
                    Nav.doGoTo(defendLocation); // Don't allow Nav to quit
                }


                RobotInfo closestEnemy = null;
                for (RobotInfo enemy : nearby) {
                    if ((enemy.getType() == RobotType.MUCKRAKER || enemy.getType() == RobotType.POLITICIAN) &&
                            enemy.getTeam() != rc.getTeam()) { // Enemy <Muckraker | Politician>
                        if (closestEnemy == null ||
                                enemy.getLocation().distanceSquaredTo(currentLocation)
                                        < closestEnemy.getLocation().distanceSquaredTo(currentLocation)) {
                            Nav.doFollow(enemy.getID());
                            closestEnemy = enemy;
                        }
                    }
                }

                if (closestEnemy != null) {
                    if (closestEnemy.getType() == RobotType.MUCKRAKER) {
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
                                && closestEnemy.getLocation().isWithinDistanceSquared(currentLocation, 2)) {
                            flagMessage(Communication.Label.CURRENTLY_DEFENDING);
                        }
                    }
                }

                if (closestEnemy == null) { // Generic defense.
                    Nav.doGoTo(defendLocation);
                    flagMessage(Communication.Label.DEFEND, defendDir.ordinal());
                }

                // Consider exploding
                int radius = getBestEmpowerRadius(0.5);
                if (radius != -1) rc.empower(radius);

                // Consider moving
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        };

        public abstract void act() throws GameActionException;

    }

    static MapLocation getTargetLoc() {
        int radius = defendRadius;
        MapLocation targetLoc = rc.getLocation().translate(defendDir.dx * radius, defendDir.dy * radius);
        targetLoc = targetLoc.translate(defendDir.dx * (int) (Math.random() * (radius / 2)), defendDir.dy * (int) (Math.random() * (radius / 2)));
        if ((targetLoc.x + targetLoc.y) % 2 != 0) {
            targetLoc = targetLoc.translate(defendDir.dx, 0);
        }
        return targetLoc;
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


    static double empowerEfficiency(int range) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        if (nearbyRobots.length == 0) {
            return 0;
        }

        double effectiveInfluence = myInfluence * rc.getEmpowerFactor(rc.getTeam(), 0) - 10;
        if (effectiveInfluence < 0) {
            return 0;
        }
        double baseInfluence = myInfluence - 10;
        double perUnit = effectiveInfluence / nearbyRobots.length;

        double usefulInfluence = 0;
        int enemies = 0;

        for (int i = 0; i < nearbyRobots.length; i++) {
            RobotInfo info = nearbyRobots[i];
            if (info.getTeam() == rc.getTeam().opponent()) {
                // opponent unit
                enemies++;
                if (info.getType() == RobotType.MUCKRAKER &&
                        !info.getLocation().isWithinDistanceSquared(centerLoc, 65)) {
                    // killing muckrakers far away from our EC is a waste
                    // 65 ~= (4.5 + sqrt(12))^2
                    usefulInfluence += info.getConviction();
                } else {
                    usefulInfluence += perUnit;
                }
            } else {
                // friendly / neutral unit
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    usefulInfluence += perUnit;
                } else {
                    usefulInfluence += info.getInfluence() - info.getConviction();
                }
            }
        }

        // calculate efficiency out of non-buff influence
        double efficiency = usefulInfluence / baseInfluence;
        if (enemies == 0 && efficiency < 3) {
            // only do a friendly explosion with a large buff
            return 0;
        }

        return efficiency;
    }

    /**
     * Picks the most efficient speech radius. Returns -1 if no radius is better
     * than the provided threshold
     *
     * @param threshold the minimum speech efficiency to consider
     * @return the best speech radius (-1 if no radius is good)
     */
    static int getBestEmpowerRadius(double threshold) {
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
}