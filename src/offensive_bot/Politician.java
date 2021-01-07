package offensive_bot;

import battlecode.common.*;

import static offensive_bot.Communication.Label.DEFEND;

public class Politician extends Robot {

    static Direction commandDir = null;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Politician.rc); // Initialize the nav
        if (assignment == null) {
            reassignDefault();
            return;
        }

        switch (assignment.label) {
            case EXPLORE:
            case ATTACK:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
            case DEFEND:
                commandDir = fromOrdinal(assignment.data[0]);
                System.out.print("DEFENDING to the " + commandDir);
                reassignDefault(); // default is defense!
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
            case EXPLORE:
                exploreBehavior();
                break;
            case DEFEND:
                defendBehavior();
                break;
            default:
                attackBehavior();
                break;
        }

    }

    void exploreBehavior() throws GameActionException {
        if (!rc.onTheMap(rc.getLocation().add(fromOrdinal(assignment.data[0])))) {
            rc.empower(0);
        }
        exploreLogic(commandDir);
    }
    @Override
    void reassignDefault() {
        if (commandDir == null) {
            commandDir = randomDirection();
        }
        if (assignment == null) {
            int[] data = {};
            assignment = new Communication.Message(DEFEND, data);
        }
        assignment.label = DEFEND;
        Nav.doGoTo(initLoc.translate(
                commandDir.dx*5 + (int) (Math.random() * 5) - 2,
                commandDir.dy*5 + (int) (Math.random() * 5) - 2));
    }

    double speechEfficiency(int range) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(range);
        Team myTeam = rc.getTeam();
        Team opponent = myTeam.opponent();
        int numNearby = nearbyRobots.length;
        if (numNearby == 0) return 0;
        double usefulInfluence =  rc.getInfluence() - 10;
        double perUnit = usefulInfluence / numNearby;
        double wastedInfluence = 0;
        for(int i=numNearby-1; --i>=0;) {
            RobotInfo info = nearbyRobots[i];
            if(info.getTeam() == opponent && info.getType() == RobotType.MUCKRAKER) {
                // TODO reconsider this
                // wastedInfluence += Math.max(perUnit - info.getConviction(), 0);
            }
            else if (info.getTeam() == myTeam) {
                wastedInfluence += Math.max(perUnit - (info.getInfluence() - info.getConviction()), 0);
            }
        }
        return 1 - (wastedInfluence / usefulInfluence);
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
        for (int i = 2; i <= 9; i++) {
            double efficiency = speechEfficiency(i);
            if (efficiency > bestEff) {
                bestEff = efficiency;
                bestRad = i;
            }
        }
        return bestRad;
    }

    void defendBehavior() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots
            int radius = getEfficientSpeech(0.5);
            if (radius != -1) {
                System.out.println("GIVING SPEECH IN DEFENSE!");
                rc.empower(radius);
            } else {
                // otherwise move
                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) rc.move(move);
            }
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
