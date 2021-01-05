package bot;

import battlecode.common.*;


public class Muckraker extends Robot {

    static MapLocation goalPos;

    @Override
    void onAwake() {
        Nav.init(Muckraker.rc); // Initialize the nav
        goalPos = rc.getLocation().translate(100, 0);
        Nav.setGoal(goalPos);
    }

    @Override
    void onUpdate() throws GameActionException {
        // early stopping
        if (rc.getLocation().distanceSquaredTo(goalPos) == 0) {
//            rc.resign();
        }

        // check if action possible
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo[] neighbors = rc.senseNearbyRobots(12, rc.getTeam().opponent());
            for (RobotInfo neighbor : neighbors) {
                // expose first slanderer found
                if (neighbor.getType() == RobotType.SLANDERER) {
                    rc.expose(neighbor.getLocation());
                    Clock.yield();
                    return;
                }
            }
            // otherwise move
            Nav.tick();
        }

        // end turn
        Clock.yield();
    }
}
