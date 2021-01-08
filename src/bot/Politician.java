package bot;

import battlecode.common.*;

import static bot.Communication.Label.DEFEND;
import bot.Communication.Message;

public class Politician extends Robot {

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Politician.rc); // Initialize the nav
        if (assignment == null) {
            reassignDefault();
            return;
        }

        switch (assignment.label) {
            case SCOUT:
            case ATTACK:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
            case DEFEND:
                commandDir = fromOrdinal(assignment.data[0]);
                System.out.print("DEFENDING to the " + commandDir);
                reassignDefault(); // default is defense!
                break;
            case FORM_WALL:
                wallAwake();
                break;
            case EXPAND:
                expandAwake();
                break;

        }
    }

    @Override
    void onUpdate() throws GameActionException {
        if (assignment == null) {
            attackBehavior();
            return;
        }

        switch (assignment.label) {
            case SCOUT:
                scoutBehavior();
                break;
            case DEFEND:
                defendBehavior();
                break;
            case FORM_WALL:
                wallBehavior();
                break;
            case EXPAND:
                expandBehavior();
            default:
                attackBehavior();
                break;
        }

    }

    void scoutBehavior() throws GameActionException {
        scoutLogic(commandDir);
    }

    @Override
    void reassignDefault() {
        System.out.println("REASSIGNED TO DEFENSE!");
        if (commandDir == null) {
            commandDir = randomDirection();
        }
        if (assignment == null) {
            int[] data = {};
            assignment = new Communication.Message(DEFEND, data);
        }
        assignment.label = DEFEND;
        Nav.doGoInDir(commandDir);
    }

    double speechEfficiency(int range) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        Team myTeam = rc.getTeam();
        Team opponent = myTeam.opponent();
        int numNearby = nearbyRobots.length;
        if (numNearby == 0) return 0;
        double usefulInfluence =  rc.getInfluence() - 10;
        if (usefulInfluence < 0) return 0;
        double perUnit = usefulInfluence / numNearby;
        double wastedInfluence = 0;
        for(int i=0; i < numNearby; i++) {
            RobotInfo info = nearbyRobots[i];
            if(info.getTeam() == opponent && info.getType() == RobotType.MUCKRAKER) {
                // TODO reconsider this
                wastedInfluence += Math.max(perUnit - info.getConviction(), 0) / 2;
            } else if (info.getTeam() == myTeam) {
                double wasted = Math.max(perUnit - (info.getInfluence() - info.getConviction()), 0);
                wastedInfluence += wasted;
            }
        }
        double efficiency = 1 - (wastedInfluence / usefulInfluence);
        return efficiency;
    }

    /**
     * Picks the most efficient speech radius. Returns -1 if no radius is better
     * than the provided threshhold
     *
     * @param threshold the minimum speech efficiency to consider
     * @return the best speech radius (-1 if no radius is good)
     */
    int getEfficientSpeech(double threshold) {
        int bestRad = -1;
        double bestEff = threshold;
        // TODO: check from radius 1
        for (int i = 2; i <= 9; i++) {
            double efficiency = speechEfficiency(i);
            if (efficiency > bestEff) {
                bestEff = efficiency;
                bestRad = i;
            }
        }
        return bestRad;
    }

    static boolean stayGrounded = false;
    static int reorientingMoves = -1;
    void defendBehavior() throws GameActionException {
        if (!rc.isReady()) {
            Clock.yield();
            return;
        }

        // get all enemy nearby robots
        int radius = getEfficientSpeech(0.8);
        if (radius != -1) {
            System.out.println("GIVING SPEECH IN DEFENSE (radius " + radius + ")!");
            rc.empower(radius);
            Clock.yield();
            return;
        } else if (stayGrounded) {
            // TODO some micro here? idk
            Clock.yield();
            return;
        }

        if (reorientingMoves <= 0) {
            RobotInfo[] closeFriends = rc.senseNearbyRobots(9);
            RobotInfo[] allFriends = rc.senseNearbyRobots();
            if (closeFriends.length == 0 && allFriends.length != 0) {
                stayGrounded = true;
                Clock.yield();
                return;
            } else if (allFriends.length == 1) {
                int dy = 0;
                int dx = 0;
                for (RobotInfo info : allFriends) {
                    dx += rc.getLocation().x - info.getLocation().x;
                    dy += rc.getLocation().y - info.getLocation().y;
                }
                if (Math.abs(dx) < 3*Math.abs(dy))
                    dx += (Math.random() < 0.5) ? -dy : dy;
                else if (Math.abs(dy) < 3*Math.abs(dx))
                    dy += (Math.random() < 0.5) ? -dx : dx;
                else if (Math.abs(dx) < 2 && Math.abs(dy) < 2) {
                    dx += (int) (Math.random() * 11) - 5;
                    dy += (int) (Math.random() * 11) - 5;
                }
                Nav.doGoInDir(dx, dy);
            }
        }

        // move
        Direction move = Nav.tick();
        if (reorientingMoves > 0) reorientingMoves--;
        if (move != null && rc.canMove(move)) rc.move(move);
        else if (move == null) {
            reorientingMoves = 20;
            commandDir = randomDirection();
            Nav.doGoInDir(commandDir);
        }
        // end turn
        Clock.yield();
    }

    void attackBehavior() throws GameActionException {
        if (rc.isReady()) {
            int radius = getEfficientSpeech(0.8);
            if (radius != -1) {
                System.out.println("GIVING OFFENSIVE SPEECH!");
                rc.empower(radius);
            } else {
                // otherwise move
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
                if (move == null) {
                    reassignDefault(); //TODO improve this
                }
            }
        }

        // end turn
        Clock.yield();
    }
}
