package bot;

import battlecode.common.*;

import static bot.Communication.Label.DEFEND;
import static bot.Communication.encode;

public class Politician extends Robot {
    static MapLocation goalPos;

    static Direction commandDir;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Politician.rc); // Initialize the nav
        if (assignment == null) {
            commandDir = randomDirection();
            Nav.doGoInDir(commandDir);
            return;
        }

        switch (assignment.label) {
            case EXPLORE:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
            case DEFEND:
                commandDir = fromOrdinal(assignment.data[0]);
                goalPos = initLoc.translate(commandDir.dx*4 + (int) (Math.random() * 5) - 2, commandDir.dx*4 + (int) (Math.random() * 5) - 2);
                Nav.doGoTo(goalPos);
                break;
            case ATTACK:
                System.out.println(assignment.data.length);
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
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
                exploreBehavior(fromOrdinal(assignment.data[0]));
                break;
            case DEFEND:
                defendBehavior();
                break;
            default:
                attackBehavior();
                break;
        }

    }

    void exploreBehavior(Direction dir) throws GameActionException {
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                rc.setFlag(encode(exploreMessage(dir)));
                assignment = null;
                onUpdate();
                return;
            }
        }
        Direction move = Nav.tick();
        if (move != null && rc.canMove(move)) rc.move(move);
        if (move == null) {
            assignment.label = DEFEND;
            Nav.doGoTo(initLoc.translate(
                    commandDir.dx*4 + (int) (Math.random() * 5) - 2,
                    commandDir.dx*4 + (int) (Math.random() * 5) - 2));
        }
        Clock.yield();
    }

    double speechEfficiency() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(9);
        Team myTeam = rc.getTeam();
        Team opponent = myTeam.opponent();
        int numNearby = nearbyRobots.length;
        double usefulInfluence =  rc.getInfluence() - 10;
        double perUnit = usefulInfluence / numNearby;
        double wastedInfluence = 0;
        for(int i=numNearby-1; --i>=0;) {
            RobotInfo info = nearbyRobots[i];
            if(info.getTeam() == opponent && info.getType() == RobotType.MUCKRAKER) {
                wastedInfluence += Math.min(perUnit - info.getConviction(), 0);
            }
            else if (info.getTeam() == myTeam) {
                wastedInfluence += Math.min(perUnit - (info.getInfluence() - info.getConviction()), 0);
            }
        }
        return 1 - (wastedInfluence / usefulInfluence);
    }

    void defendBehavior() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo[] enemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            if (enemies.length > 0 && speechEfficiency() > 0.3) {
                rc.empower(9);
                System.out.println("GIVING SPEECH IN DEFENSE");
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

                }
            }
        }

        // end turn
        Clock.yield();
    }
}
