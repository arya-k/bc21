package refactor;

import battlecode.common.*;

import static refactor.Communication.decode;

public class Politician extends Robot {
    public static final int NEUTRAL_EC_WAIT_ROUNDS = 10;

    static State state = null;

    /* NeutralEC vars */
    static int waitingRounds = 0;
    static boolean foundNeutralNeighbor = false;

    /* Scout vars */
    static Direction scoutDir;

    /* Defense vars */
    static Direction defendDir;
    static MapLocation defendLocation;

    /* Attack & Neutral EC vars */
    static MapLocation targetECLoc;

    @Override
    void onAwake() throws GameActionException {
        state = State.Explore; // By default, we just explore!
        Nav.doExplore();

        if (assignment == null) return;

        switch (assignment.label) {
            case EXPLORE:
                break; // already handled above!

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

            case EXPLODE:
                state = State.Explode;
                break;
            case DEFEND:
                state = State.Defend;
                defendDir = fromOrdinal(assignment.data[0]);
                MapLocation targetLoc = rc.getLocation().translate(defendDir.dx * 4, defendDir.dy * 4);
                targetLoc = targetLoc.translate(defendDir.dx * (int) (Math.random() * 2), defendDir.dy * (int) (Math.random() * 2));
                if ((targetLoc.x + targetLoc.y) % 2 != 0) {
                    targetLoc = targetLoc.translate(defendDir.dx, 0);
                }
                if(rc.onTheMap(centerLoc.translate(defendDir.dx * 2, defendDir.dy * 2))) {
                    defendLocation = targetLoc;
                    Nav.doGoTo(defendLocation);
                } else {
                    Nav.doExplore();
                    state = State.Explore;
                }
                break;

            case HIDE: // We were once a slanderer! TODO return to sender?
                break;

            default:
                System.out.println("ERROR: Politician has been given a bad assignment: " + assignment.label);
//                rc.resign(); // TODO: remove before uploading...
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

        // Scout -> Explode
        if (state == State.Scout && Nav.currentGoal == Nav.NavGoal.Nothing) {
            for (Direction d : Direction.cardinalDirections()) {
                if (!rc.onTheMap(currentLocation.add(d))) {
                    int offset;
                    switch (d) {
                        case EAST:
                        case WEST:
                            offset = Math.abs(currentLocation.x - centerLoc.x);
                            break;
                        default:
                            offset = Math.abs(currentLocation.y - centerLoc.y);
                    }
                    flagMessage(
                            Communication.Label.SAFE_DIR_EDGE,
                            scoutDir.ordinal(),
                            d.ordinal(),
                            offset
                    );
                    break;
                }
            }

            state = State.Explore;
            Nav.doExplore();
        }

        // Explore -> Explode
        if (state == State.Explore && Nav.currentGoal == Nav.NavGoal.Nothing) {
            state = State.Explode; // TODO: we should do something more intelligent here probably.
        }

        // CaptureNeutralEC -> Explore 
        if (state == State.CaptureNeutralEC && waitingRounds >= NEUTRAL_EC_WAIT_ROUNDS && !foundNeutralNeighbor) {
            state = State.Explore;
            Nav.doExplore();
        }

    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException { // TODO: Our EC should not remove explorers until they explode!
                // Note enemy & neutral ECs:
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
                    } else if (info.getTeam() == Team.NEUTRAL) { // Neutral EC message...
                        MapLocation loc = info.getLocation();
                        double log = Math.log(info.getConviction()) / Math.log(2);
                        flagMessage(
                                Communication.Label.NEUTRAL_EC,
                                loc.x % 128,
                                loc.y % 128,
                                (int) log + 1
                        );
                    }
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
                int radius = getEfficientSpeech(0.8);
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
                if (move != null && rc.canMove(move)) rc.move(move);

                if (move != null) return;

                // We are at the destination- hopefully adjacent!
                waitingRounds++;
                RobotInfo[] neighbors = rc.senseNearbyRobots(2);
                for (RobotInfo nbor : neighbors) {
                    if (nbor.getTeam() == Team.NEUTRAL) {
                        foundNeutralNeighbor = true;
                        if ((waitingRounds > NEUTRAL_EC_WAIT_ROUNDS ||
                                nbor.getConviction() < (myInfluence - 10) / neighbors.length / 2) && rc.canEmpower(2)) {
                            rc.empower(2);
                            Clock.yield();
                            return;
                        }
                    }
                }
                if (!foundNeutralNeighbor) {
                    Nav.doExplore();
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
                    int radius = getEfficientSpeech(0.6);
                    if (radius != -1 && rc.canEmpower(radius))
                        rc.empower(radius);
                }
            }
        },
        Explode {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                RobotInfo[] enemiesNearby = rc.senseNearbyRobots(9, rc.getTeam().opponent());
                if (enemiesNearby.length >= 3) {
                    int radius = getEfficientSpeech(0.1);
                    if (radius == -1) {
                        rc.empower(9);
                        return;
                    }
                    rc.empower(radius);
                    return;
                }
                int closestPolitician = -1;
                for (RobotInfo enemy: enemiesNearby) {
                    if (enemy.getType() == RobotType.POLITICIAN) {
                        int distanceAway = rc.getLocation().distanceSquaredTo(enemy.getLocation());
                        if (closestPolitician == -1 || distanceAway < closestPolitician)
                            closestPolitician = distanceAway;
                    }
                }
                if (closestPolitician >= 1)
                    rc.empower(closestPolitician);
            }
        },
        Defend {
            public void act() throws GameActionException {
                if (!rc.isReady()) return;

                if (Nav.currentGoal == Nav.NavGoal.Nothing) {
                    Nav.doGoTo(defendLocation);
                }


                RobotInfo closestEnemy = null;
                for (RobotInfo bot : nearby) {
                    if ((bot.getType() == RobotType.MUCKRAKER || bot.getType() == RobotType.POLITICIAN) && bot.getTeam() != rc.getTeam()) {
                        if (closestEnemy == null ||
                                bot.getLocation().distanceSquaredTo(currentLocation)
                                        < closestEnemy.getLocation().distanceSquaredTo(currentLocation)) {
                            Nav.doFollow(bot.getID());
                            closestEnemy = bot;
                        }
                    }
                }
                if (closestEnemy == null) {
                    Nav.doGoTo(defendLocation);
                    flagMessage(Communication.Label.DEFEND, defendDir.ordinal());
                } else {
                    if(closestEnemy.getType() == RobotType.MUCKRAKER) {
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
                        if (closestEnemy != null
                                && closestEnemy.getLocation().isWithinDistanceSquared(currentLocation, 2)) {
                            flagMessage(Communication.Label.CURRENTLY_DEFENDING);
                        } else if (closestEnemy == null) {
                            Nav.doGoTo(defendLocation);
                            flagMessage(Communication.Label.DEFEND, defendDir.ordinal());
                        }
                    }
                }
                int radius = getEfficientSpeech(0.5);
                if (radius != -1) {
                    rc.empower(radius);
                    return;
                }
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
        };

        public abstract void act() throws GameActionException;

    }


    static double speechEfficiency(int range) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        Team myTeam = rc.getTeam();
        Team opponent = myTeam.opponent();
        int numNearby = nearbyRobots.length;
        if (numNearby == 0) return 0;
        double usefulInfluence = myInfluence * rc.getEmpowerFactor(rc.getTeam(), 0) - 10;
        if (usefulInfluence < 0) return 0;
        double perUnit = usefulInfluence / numNearby;
        double wastedInfluence = 0;
        int kills = 0;
        int enemies = 0;
        for (int i = 0; i < numNearby; i++) {
            RobotInfo info = nearbyRobots[i];
            if (info.getTeam() == opponent)
                enemies++;
            if (info.getTeam() == opponent && info.getConviction() < perUnit)
                kills++;
            if (info.getTeam() == opponent && info.getType() == RobotType.MUCKRAKER &&
                    !info.getLocation().isWithinDistanceSquared(centerLoc, 15)) {
                wastedInfluence += Math.max(perUnit - info.getConviction(), 0) / 2;
            } else if (info.getTeam() == myTeam && info.getType() != RobotType.ENLIGHTENMENT_CENTER) {
                double wasted = Math.max(perUnit - (info.getInfluence() - info.getConviction()), 0);
                wastedInfluence += wasted;
            } else if (info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                if (perUnit >= info.getInfluence() / 4) return 1;
            }
        }
        if (enemies == 0)
            return 0;
        double efficiency = 1 - (wastedInfluence / usefulInfluence);
        if (kills >= 3)
            efficiency += kills;
        return efficiency;
    }

    /**
     * Picks the most efficient speech radius. Returns -1 if no radius is better
     * than the provided threshold
     *
     * @param threshold the minimum speech efficiency to consider
     * @return the best speech radius (-1 if no radius is good)
     */
    static int getEfficientSpeech(double threshold) {
        int bestRad = -1;
        double bestEff = threshold;
        // TODO: check from radius 1
        for (int i = 1; i <= 9; i++) {
            double efficiency = speechEfficiency(i);
            if (efficiency > bestEff) {
                bestEff = efficiency;
                bestRad = i;
            }
        }

        return bestRad;
    }
}