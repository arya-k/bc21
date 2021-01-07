package bot;

import battlecode.common.*;

import static bot.Communication.Label.DEFEND;
import static bot.Communication.encode;

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

    double speechEfficiency() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(9);
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
                wastedInfluence += Math.max(perUnit - info.getConviction(), 0);
            }
            else if (info.getTeam() == myTeam) {
                wastedInfluence += Math.max(perUnit - (info.getInfluence() - info.getConviction()), 0);
            }
        }
        return 1 - (wastedInfluence / usefulInfluence);
    }

    void defendBehavior() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo[] enemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            double efficiency = speechEfficiency();
            if (enemies.length > 0 && efficiency > 0.4) {
                System.out.println("GIVING SPEECH IN DEFENSE!");
                rc.empower(9);
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
            // get all enemy nearby robots, might be better to manually filter
            RobotInfo[] enemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            RobotInfo[] allies = rc.senseNearbyRobots(9, rc.getTeam());
            // if there are two enemies nearby, empower
            if ((enemies.length > 2 || allies.length > 6) && speechEfficiency() > 0.5) {
                rc.empower(9);
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
