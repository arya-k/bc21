package seeding;

import battlecode.common.*;

import static seeding.Communication.decode;
import static seeding.Communication.encode;

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
    static int followingTurns = 0;
    static int failedToMoveTurns = 0;

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    /* EC tracking vars */
    static MapLocation closestECLoc = centerLoc;

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
            flagMessage(Communication.Label.EXPLORE);
        }

        if (state == State.Defend && failedToMoveTurns > 20) {
            defendDir = defendDir.rotateLeft();
            defendLocation = getTargetLoc();
        }

        if (state == State.Defend && Nav.currentGoal == Nav.NavGoal.Nothing) {
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

        // TODO: AttackLoc -> Explore if our target EC is now on our team!
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
                if (rc.isReady()) {
                    Direction move = Nav.tick();
                    if (move != null && rc.canMove(move)) rc.move(move);

                    if (move == null) { // TODO: part the seas! also don't give up on Nav movement...
                        int radius = getBestEmpowerRadius(0.6);
                        if (radius != -1 && rc.canEmpower(radius))
                            rc.empower(radius);
                        else
                            Nav.doGoTo(targetECLoc);
                    }
                }
                assignment.data[3] = (rc.getCooldownTurns() <= 1) ? 1 : 0;
                rc.setFlag(encode(assignment));
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
            if (message.label == Communication.Label.ATTACK_LOC
                    && message.data[0] == assignment.data[0]
                    && message.data[1] == assignment.data[1]
                    && message.data[2] > assignment.data[2]
                    && message.data[3] == 1) {
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