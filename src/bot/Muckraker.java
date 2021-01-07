package bot;

import battlecode.common.*;

import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class Muckraker extends Robot {

    static Direction commandDir;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Muckraker.rc); // Initialize the nav
        if (assignment == null) {
            reassignDefault();
        }

        switch (assignment.label) {
            case EXPLORE:
            case LATCH:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
            default:
                reassignDefault();
                break;
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        if (assignment == null) {
            defaultBehavior();
            return;
        }

        switch (assignment.label) {
            case EXPLORE:
                exploreBehavior();
                break;
            case LATCH:
                latchBehavior();
                break;
            default:
                defaultBehavior();
                break;
        }
    }

    void exploreBehavior() throws GameActionException {
        exploreLogic(commandDir);
    }
    
    @Override
    void reassignDefault() {
        assignment = null;
        MapLocation goalPos = initLoc.translate((int) (Math.random() * 4) + 1, (int) (Math.random() * 4) + 1);
        Nav.doGoTo(goalPos);
    }

    void latchBehavior() throws GameActionException {
        if (rc.isReady()) {
            RobotInfo kill = bestSlandererKill();
            if (kill != null) {
                rc.expose(kill.getLocation());
                Clock.yield();
                return;
            }
            //TODO actually do something here
            //Waiting for the follow command to exist
        }
    }

    void defaultBehavior() throws GameActionException {
        // check if action possible
        if (rc.isReady()) {
            // get all enemy nearby robots
            RobotInfo kill = bestSlandererKill();
            if (kill != null) {
                rc.expose(kill.getLocation());
                Clock.yield();
                return;
            }
            Direction move = Nav.tick();
            if (move != null && rc.canMove(move)) rc.move(move);
            Clock.yield();
        }
    }

    RobotInfo bestSlandererKill() {
        RobotInfo[] neighbors = rc.senseNearbyRobots(12, rc.getTeam().opponent());
        RobotInfo best = null;
        int bestInfluence = 0;
        for (RobotInfo info : neighbors) {
            if (info.getType() == RobotType.SLANDERER) {
                int influence = info.getInfluence();
                if (influence > bestInfluence) {
                    bestInfluence = influence;
                    best = info;
                }
            }
        }
        return best;
    }
}
