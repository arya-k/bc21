package bot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;

public class Slanderer extends Robot {
    @Override
    void onAwake() throws GameActionException {

    }

    @Override
    void onUpdate() throws GameActionException {
        if (rc.isReady()) {
            Direction toMove = Direction.allDirections()[(int) (Math.random() * 8)];
            while (!rc.canMove(toMove)) {
                toMove = Direction.allDirections()[(int) (Math.random() * 8)];
            }
            rc.move(toMove);
        }
        Clock.yield();
    }
}
