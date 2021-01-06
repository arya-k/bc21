package bot;

import battlecode.common.*;

import static bot.Communication.encode;

public class Politician extends Robot {
    static MapLocation goalPos;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Politician.rc); // Initialize the nav
        goalPos = rc.getLocation().translate(100, 0);
        Nav.setGoal(goalPos);
    }

    @Override
    void onUpdate() throws GameActionException {
        if (assignment == null) {
            defaultBehavior();
            return;
        }

        switch (assignment.label) {
            case EXPLORE:
                exploreBehavior(fromOrdinal(assignment.data[0]));
                break;
            default:
                defaultBehavior();
                break;
        }

    }

    static boolean seenDanger = false;

    void exploreBehavior(Direction dir) throws GameActionException {
        if (!seenDanger) {
            for (RobotInfo info : rc.senseNearbyRobots()) {
                if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    rc.setFlag(encode(exploreMessage(dir)));
                    seenDanger = true;
                }
            }
        }
        exploreDir(dir);
    }

    void defaultBehavior() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots, might be better to manually filter
            RobotInfo[] enemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            RobotInfo[] allies = rc.senseNearbyRobots(9, rc.getTeam());
            // if there are two enemies nearby, empower
            if (enemies.length > 2 || allies.length > 6) {
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
