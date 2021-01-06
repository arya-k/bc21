package bot;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

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
                exploreDir(fromOrdinal(assignment.data[0]));
                break;
            default:
                defaultBehavior();
                break;
        }

    }

    void defaultBehavior() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo[] enemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            RobotInfo[] allies = rc.senseNearbyRobots(9, rc.getTeam());
            // if there are two enemies nearby, give speech
            if (enemies.length > 2 || allies.length > 7) {
                rc.empower(9);
                System.out.println("GIVING SPEECH");
                // rc.resign();
            } else {
                // otherwise move
                Nav.tick();
            }
        }

        // end turn
        Clock.yield();
    }
}
