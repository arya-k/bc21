package bot;

import battlecode.common.*;

import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class Muckraker extends Robot {

    static MapLocation goalPos;
    static RobotInfo[] nearby;

    @Override
    void onAwake() throws GameActionException {
        Nav.init(Muckraker.rc); // Initialize the nav
    }

    @Override
    void onUpdate() throws GameActionException {
        nearby = rc.senseNearbyRobots();
        if (assignment == null) {
            defaultBehavior();
            return;
        }

        switch (assignment.label) {
            case EXPLORE:
                exploreBehavior(fromOrdinal(assignment.data[0]));
                break;
            case LATCH:
                latchBehavior(fromOrdinal(assignment.data[0]));
                break;
            default:
                defaultBehavior();
                break;
        }
    }

    void exploreBehavior(Direction dir) throws GameActionException {
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                rc.setFlag(encode(exploreMessage(dir)));
                assignment = null;
                onUpdate();
                return;
            }
        }
        exploreDir(dir);
    }

    void latchBehavior(Direction dir) throws GameActionException {
        if (rc.isReady()) {
            RobotInfo kill = bestSlandererKill();
            if (kill != null) {
                rc.expose(kill.getLocation());
                Clock.yield();
                return;
            }
            //TODO actually do something here
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
            // TODO move somewhere
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
