package bot;

import battlecode.common.*;

import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class Muckraker extends Robot {

    static MapLocation goalPos;

    @Override
    void onAwake() throws GameActionException {
        Message command = decode(rc.getFlag(Muckraker.centerID));
        Nav.init(Muckraker.rc); // Initialize the nav
        switch (command.label) {
            case EXPLORE:
                Direction dir = Muckraker.directions[command.data[0]];
                goalPos = rc.getLocation().translate(4*dir.dx, 4*dir.dy);
                break;
            default:
                goalPos = rc.getLocation().translate(3, 3);
        }
        Nav.setGoal(goalPos);

    }

    @Override
    void onUpdate() throws GameActionException {
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
            if (rc.getLocation().distanceSquaredTo(goalPos) != 0) {
                Nav.tick();
            }
            Clock.yield();
            return;
        }
    }
}
