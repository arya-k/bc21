package bot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Slanderer extends Robot {
    static MapLocation goalPos;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Slanderer.rc); // Initialize the nav
        goalPos = rc.getLocation().translate(0, 100);
        Nav.doGoTo(goalPos);
    }

    @Override
    void onUpdate() throws GameActionException {
        if (rc.isReady()) {
            Direction move = Nav.tick();
            if (move != null && rc.canMove(move)) rc.move(move);
        }
        Clock.yield();
    }
}
