package good_bot;

import battlecode.common.*;

import static good_bot.Communication.decode;
import static good_bot.Communication.encode;

public class Muckraker extends Robot {
    @Override
    void onAwake() throws GameActionException {
        Nav.init(Muckraker.rc); // Initialize the nav
        if (assignment == null) {
            reassignDefault();
        }

        switch (assignment.label) {
            case SCOUT:
            case LATCH:
            case HIDE:
                commandDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(commandDir);
                break;
            case EXPLORE:
                Nav.doExplore();
                break;
            case FORM_WALL:
                wallAwake();
                break;
            case EXPAND:
                expandAwake();
                break;
            case ATTACK_LOC:
                commandLoc = getLocFromMessage(assignment.data[0], assignment.data[1]);
                Nav.doGoTo(commandLoc);
                break;
            default:
                reassignDefault();
                break;
        }
    }

    @Override
    void onUpdate() throws GameActionException {
//        checkExpansion();
        if (assignment == null) {
            defaultBehavior();
            return;
        }
        switch (assignment.label) {
            case SCOUT:
                scoutBehavior();
                break;
            case LATCH:
                latchBehavior();
                break;
            case HIDE:
                hideBehavior();
                break;
            case FORM_WALL:
                wallBehavior();
                break;
            case EXPAND:
                expandBehavior();
                break;
            case EXPLORE:
            case ATTACK_LOC:
            default:
                defaultBehavior();
                break;
        }
    }

    void scoutBehavior() throws GameActionException {
        scoutLogic(commandDir);
    }

    @Override
    void reassignDefault() {
        assignment = null;
        MapLocation goalPos = initLoc.translate((int) (Math.random() * 4) + 1, (int) (Math.random() * 4) + 1);
        Nav.doGoTo(goalPos);
    }

    void reassignDefault(boolean setGoal) {
        assignment = null;
        if(setGoal) {
            MapLocation goalPos = initLoc.translate((int) (Math.random() * 4) + 1, (int) (Math.random() * 4) + 1);
            Nav.doGoTo(goalPos);
        }
    }

    @Override
    void wallBehavior() throws GameActionException {
        super.wallBehavior();
        if(rc.isReady()) {
            RobotInfo kill = bestSlandererKill();
            if (kill != null) {
                rc.expose(kill.getLocation());
            }
        }
        Clock.yield();
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

    void hideBehavior() throws GameActionException {
        if (Nav.currentGoal != Nav.NavGoal.GoTo) {
            RobotInfo[] allies = rc.senseNearbyRobots(25, rc.getTeam());
            RobotInfo closest_slanderer = null;
            MapLocation myLocation = rc.getLocation();
            for (RobotInfo info : allies) {
                if (info.getType() == RobotType.SLANDERER) {
                    if (closest_slanderer == null || info.getLocation().distanceSquaredTo(myLocation) < closest_slanderer.getLocation().distanceSquaredTo(myLocation)) {
                        closest_slanderer = info;
                    }
                }
            }
            if (closest_slanderer != null) {
                MapLocation target = closest_slanderer.getLocation();
                Direction opposite = commandDir.opposite();
                for (int i = 3; --i > 0; ) {
                    target = target.translate(opposite.dx * (int) (Math.random() + 0.5), opposite.dy * (int) (Math.random() + 0.5));
                }
                Nav.doGoTo(target);
            }
        }

        if (rc.isReady()) {
            Direction move = Nav.tick();
            if(move == null || move == Direction.CENTER) {
                reassignDefault(false);
            }
            else if (rc.canMove(move)) rc.move(move);

        }
        Clock.yield();
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
            for (RobotInfo info : rc.senseNearbyRobots()) {
                if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    rc.setFlag(encode(dangerMessage(info)));
                    MapLocation loc = info.getLocation();
                    int[] data = {loc.x % 128, loc.y % 128};
                    assignment = new Communication.Message(Communication.Label.ATTACK_LOC, data);
                }
                else if(info.getTeam() == Team.NEUTRAL) {
                    rc.setFlag(encode(neutralECMessage(info)));
                }
            }
        }
        Clock.yield();
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
