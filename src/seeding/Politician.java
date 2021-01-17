package seeding;

import battlecode.common.*;

import static seeding.Communication.decode;

public class Politician extends Robot {
    static State state = null;

    /* Scout vars */
    static Direction scoutDir;

    /* Defense vars */
    static Direction defendDir;
    static MapLocation defendLocation;
    static int defendRadius = 4;
    static int followingTurns = 0;
    static int failedToMoveTurns = 0;

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    /* EC tracking vars */
    static MapLocation closestECLoc = centerLoc;
    static int[] seenECs = new int[12];
    static int numSeenECs = 0;

    @Override
    void onAwake() throws GameActionException {
        seenECs[numSeenECs++] = centerID;

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
                defendLocation = getTargetLoc();
                Nav.doGoTo(defendLocation);
                break;
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();

        calculateClosestEC();

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

        // Explore -> AttackLoc
        if (state == State.Explore && rc.getInfluence() > GameConstants.EMPOWER_TAX && rc.canGetFlag(centerID)) {
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
        if (state == State.CaptureNeutralEC && rc.canSenseLocation(targetECLoc) &&
                rc.senseRobotAtLocation(targetECLoc).getTeam() != Team.NEUTRAL) {
            state = State.Explore;
            Nav.doExplore();
        }

        // AttackLoc -> Explore if the EC is on our team!
        if (state == State.AttackLoc) {
            if (Nav.currentGoal == Nav.NavGoal.Nothing
                    || (rc.canSenseLocation(targetECLoc) && rc.senseRobotAtLocation(targetECLoc).getTeam() == rc.getTeam())) {
                state = State.Explore;
                Nav.doExplore();
            }
        }
    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException { // TODO: Our EC should not remove explorers until they explode!
                boolean alreadySetFlag = noteNearbyECs();

                if (!alreadySetFlag)
                    flagMessage(Communication.Label.SCOUT_LOCATION, currentLocation.x % 128, currentLocation.y % 128);

                if (!rc.isReady()) return;

                int radius = getBestEmpowerRadius(0.3);
                if (radius != -1 && rc.isReady())
                    rc.empower(radius);

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        },
        Explore {
            @Override
            public void act() throws GameActionException {
                noteNearbyECs();
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
                } else if (rc.getLocation().distanceSquaredTo(targetECLoc) > 2) {
                    Nav.doGoTo(targetECLoc); // Nav not allowed to quit
                }

                if (!rc.getLocation().isWithinDistanceSquared(targetECLoc, rc.getType().actionRadiusSquared)) return;

                // should be able to convert the EC
                int radius = getBestEmpowerRadius(1);
                if (radius != -1) rc.empower(radius);
            }
        },
        AttackLoc {
            @Override
            public void act() throws GameActionException {
                // if we are in range of our attacking location, switch to attacking message
                if (currentLocation.isWithinDistanceSquared(targetECLoc, 36)) {
                    int readyToGo = (rc.getCooldownTurns() <= 1) ? 1 : 0;
                    flagMessage(Communication.Label.ATTACKING, firstTurn / 6, readyToGo);
                }
                if (!rc.isReady()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);

                if (move == null) {
                    int radius = getBestEmpowerRadius(0.6);
                    if (radius != -1 && rc.canEmpower(radius))
                        rc.empower(radius);
                    else
                        Nav.doGoTo(targetECLoc);
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

                if (failedToMoveTurns > 20) {
                    defendDir = defendDir.rotateLeft();
                    defendLocation = getTargetLoc();
                }

                if (Nav.currentGoal == Nav.NavGoal.Nothing) {
                    int numAlliesCloser = 0;
                    int myDist = centerLoc.distanceSquaredTo(currentLocation);
                    for (RobotInfo bot : nearby) {
                        if (bot.getTeam() == rc.getTeam() && myDist > bot.getLocation().distanceSquaredTo(centerLoc)) {
                            numAlliesCloser++;
                        }
                    }
                    if (numAlliesCloser > 12) {
                        defendRadius++;
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
                            if (enemy.getType() == RobotType.POLITICIAN && enemy.getInfluence() < GameConstants.EMPOWER_TAX)
                                continue;
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
                    followingTurns = 0;
                    Nav.doGoTo(defendLocation);
                    flagMessage(Communication.Label.DEFEND, defendDir.ordinal());
                }

                // Consider exploding
                int alliesNearby = 0;
                for (RobotInfo bot : nearby) {
                    if (bot.getTeam() == rc.getTeam() && bot.getType() == RobotType.POLITICIAN)
                        alliesNearby++;
                }
                double threshold = followingTurns > 4 || alliesNearby > 4 ? 0.0 : 0.5;
                int radius = getBestEmpowerRadius(threshold);
                if (radius != -1) rc.empower(radius);

                // Consider moving
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) {
                    failedToMoveTurns = 0;
                    if (Nav.currentGoal == Nav.NavGoal.Follow)
                        followingTurns++;
                    rc.move(move);
                } else {
                    failedToMoveTurns++;
                }
            }
        };

        public abstract void act() throws GameActionException;

    }

    static MapLocation getTargetLoc() throws GameActionException {
        int radius = defendRadius;
        MapLocation targetLoc = centerLoc.translate(defendDir.dx * radius, defendDir.dy * radius);
        targetLoc = targetLoc.translate(defendDir.dx * (int) (Math.random() * (radius / 2)), defendDir.dy * (int) (Math.random() * (radius / 2)));
        if ((targetLoc.x + targetLoc.y) % 2 != 0) {
            targetLoc = targetLoc.translate(defendDir.dx, 0);
        }
        if (radius > 4 && !rc.onTheMap(rc.getLocation().translate(defendDir.dx * 2, defendDir.dy * 2))) {
            defendDir = randomDirection();
            defendRadius = 4;
            return getTargetLoc();
        }
        return targetLoc;
    }

    static boolean noteNearbyECs() throws GameActionException {
        for (RobotInfo info : nearby) {
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
            return true; // noted an EC
        }
        return false; // did not note an EC
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
                        !info.getLocation().isWithinDistanceSquared(closestECLoc, 65)) {
                    // killing muckrakers far away from our EC is a waste
                    // 65 ~= (4.5 + sqrt(12))^2
                    usefulInfluence += info.getConviction();
                } else if (info.getType() == RobotType.ENLIGHTENMENT_CENTER
                        && range <= 2 && shouldAttackEC(info)) {
                    // test if enemy EC is surrounded and we have backup
                    return 1;
                } else if (info.getType() != RobotType.POLITICIAN || info.getInfluence() > GameConstants.EMPOWER_TAX) {
                    usefulInfluence += perUnit;
                }
            } else {
                // friendly / neutral unit
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    if (info.getTeam() == Team.NEUTRAL && perUnit > info.getConviction())
                        return 100;
                    usefulInfluence += perUnit;
                } else {
                    usefulInfluence += info.getInfluence() - info.getConviction();
                }
            }
        }

        // calculate efficiency out of non-buff influence
        double efficiency = usefulInfluence / baseInfluence;
        if (enemies == 0 && efficiency < 10) {
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
        if (!(currentLocation.distanceSquaredTo(ec.getLocation()) <= 2))
            return false;
        MapLocation ecLoc = ec.getLocation();
        for (Direction dir : directions) {
            MapLocation adjLoc = ecLoc.add(dir);
            if (rc.onTheMap(adjLoc) && rc.senseRobotAtLocation(adjLoc) == null)
                return false;
        }
        int reverseDir = ecLoc.directionTo(currentLocation).ordinal();
        Direction[] backupDirs = {
                directions[reverseDir],
                directions[(reverseDir + 1) % 8],
                directions[(reverseDir + 7) % 8]
        };
        for (Direction dir : backupDirs) {
            RobotInfo info = rc.senseRobotAtLocation(currentLocation.add(dir));
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

    static void calculateClosestEC() {
        int closestECDist = rc.getLocation().distanceSquaredTo(closestECLoc);
        for (RobotInfo n : nearby) {
            if (n.getType() == RobotType.ENLIGHTENMENT_CENTER
                    && n.getTeam() == rc.getTeam()) {
                MapLocation curLoc = n.getLocation();
                int curDist = rc.getLocation().distanceSquaredTo(curLoc);
                if (curDist < closestECDist) {
                    closestECLoc = curLoc;
                    closestECDist = curDist;
                }
            }
        }
    }
}