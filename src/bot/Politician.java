package bot;

import battlecode.common.*;

import static bot.Communication.encode;

public class Politician extends Robot {
    static MapLocation goalPos;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Politician.rc); // Initialize the nav
        if (assignment != null && assignment.label == Communication.Label.DEFEND) {
            goalPos = rc.getLocation().translate((int) (Math.random() * 5) + 1, (int) (Math.random() * 5) + 1);
        } else {
            goalPos = rc.getLocation().translate(25, 0);
        }
        Nav.setGoal(goalPos);
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
        exploreDir(dir);
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
            if (enemies.length > 0 && speechEfficiency() > 0.5) {
                rc.empower(9);
                System.out.println("GIVING SPEECH IN DEFENSE");
            } else {
                // otherwise move
                Nav.tick();
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
                Nav.tick();
            }
        }

        // end turn
        Clock.yield();
    }
}
