package bot;

import battlecode.common.*;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class Politician extends Robot{
    static MapLocation goalPos;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Muckraker.rc); // Initialize the nav
        goalPos = rc.getLocation().translate(100, 0);
        Nav.setGoal(goalPos);
    }

    @Override
    void onUpdate() throws GameActionException {
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo[] neighbors = rc.senseNearbyRobots(9, rc.getTeam().opponent());
            // if there are two enemies nearby, give speech
            System.out.println(neighbors.length);
            if (neighbors.length > 2) {
                rc.empower(9);
                System.out.println("GIVING SPEECH");
                rc.resign();
            }
            else {
                // otherwise move
                Nav.tick();
            }
        }

        // end turn
        Clock.yield();
    }
}
