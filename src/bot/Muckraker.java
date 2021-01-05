package bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Muckraker extends Robot {

    static MapLocation goalPos;

    @Override
    void onAwake() {
        Nav.init(Muckraker.rc); // Initialize the nav
        goalPos = rc.getLocation().translate(3, 3);
        Nav.setGoal(goalPos);
    }

    @Override
    void onUpdate() throws GameActionException {
        Nav.tick();

        // early stopping
        if (rc.getLocation().distanceSquaredTo(goalPos) == 0) {
            rc.resign();
        }
    }
}
