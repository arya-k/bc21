package bot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Slanderer extends Robot {

    static Direction commandDir;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Slanderer.rc); // Initialize the nav
        if (assignment == null) {
            reassignDefault();
        }

        switch (assignment.label) {
            case HIDE:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
        }
    }

    @Override
    void reassignDefault() {
        int[] data = {randomDirection().ordinal()};
        assignment = new Communication.Message(Communication.Label.HIDE, data);
    }

    @Override
    void onUpdate() throws GameActionException {
        switch (assignment.label) {
            case HIDE:
                hideBehavior();
                Clock.yield();
                break;
            default:
                Clock.yield();
        }
    }

    void hideBehavior() throws GameActionException {
        Direction move = Nav.tick();
        if (move != null && rc.canMove(move)) rc.move(move);
        //TODO run from enemies
    }
}
