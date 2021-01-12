package refactor;

import battlecode.common.*;
import refactor.Communication.Label;
import refactor.Communication.Message;

import static refactor.Communication.decode;
import static refactor.Communication.encode;

abstract public class Robot {
    public static RobotController rc = null;
    public static int firstTurn;
    public static MapLocation initLoc;

    static int centerID;
    static MapLocation centerLoc;

    static Message assignment = null;

    static Direction commandDir;
    static MapLocation commandLoc;

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };


    public static void init(RobotController rc) throws GameActionException {
        Robot.firstTurn = rc.getRoundNum();
        Robot.rc = rc;
        Robot.initLoc = rc.getLocation();

        // find the EC
        if (rc.getType() != RobotType.ENLIGHTENMENT_CENTER) {
            for (RobotInfo info : rc.senseNearbyRobots(2)) {
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER && info.getTeam() == rc.getTeam()) {
                    Robot.centerID = info.getID();
                    Robot.centerLoc = info.getLocation();
                    int flag = rc.getFlag(Robot.centerID);
                    Robot.assignment = decode(flag);
                    rc.setFlag(flag); // TODO: why this line?
                    break;
                }
            }
            if (Robot.assignment == null) {
                System.out.println("@@@ERROR: Didnt find assignment!");
                rc.resign();
            }
        }

    }

    abstract void onAwake() throws GameActionException;

    abstract void onUpdate() throws GameActionException;

    // TODO: I don't like where this code is- find a way to move it!
    void scoutLogic(Direction commandDir) throws GameActionException {
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                rc.setFlag(encode(dangerMessage(info)));
                assignment = null;
                onUpdate();
                return;
            } else if (info.getTeam() == Team.NEUTRAL) {
                rc.setFlag(encode(neutralECMessage(info)));
            }
        }
        Direction move = Nav.tick();
        if (move != null && rc.canMove(move)) rc.move(move);
        if (move == null) {
            // send safety message
            for (Direction d : Direction.cardinalDirections()) {
                if (!rc.onTheMap(rc.getLocation().add(d))) {
                    int offset;
                    switch (d) {
                        case EAST:
                        case WEST:
                            offset = Math.abs(rc.getLocation().x - centerLoc.x);
                            break;
                        default:
                            offset = Math.abs(rc.getLocation().y - centerLoc.y);
                    }
                    rc.setFlag(encode(safeDirMessage(commandDir, d, offset)));
                    break;
                }
            }

            // TODO: reassign is broken!
        }
        Clock.yield();
    }

    /* MESSAGE CREATION FUNCTIONS */

    Message scoutMessage(Direction dir) {
        int[] data = {dir.ordinal()};
        return new Message(Label.SCOUT, data);
    }

    Message dangerMessage(RobotInfo enemy) {
        MapLocation enemy_loc = enemy.getLocation();
        int[] data = {enemy_loc.x % 128, enemy_loc.y % 128, (int) (Math.log(enemy.getInfluence()) / Math.log(2) + 1)};
        return new Message(Label.ENEMY_EC, data);
    }

    Message neutralECMessage(RobotInfo info) {
        MapLocation loc = info.getLocation();
        double log = Math.log(info.getConviction()) / Math.log(2);
        int[] data = {loc.x % 128, loc.y % 128, (int) log + 1};
        return new Message(Label.NEUTRAL_EC, data);
    }

    Message safeDirMessage(Direction commandDir, Direction edgeDir, int offset) {
        int[] data = {commandDir.ordinal(), edgeDir.ordinal(), offset};
        return new Message(Label.SAFE_DIR_EDGE, data);
    }

    static MapLocation getLocFromMessage(int xMod, int yMod) {
        MapLocation myLoc = rc.getLocation();
        int x = myLoc.x % 128;
        int y = myLoc.y % 128;
        int xOff = xMod - x;
        int yOff = yMod - y;
        if (Math.abs(xOff) >= 64)
            xOff = xOff > 0 ? xOff - 128 : xOff + 128;
        if (Math.abs(yOff) >= 64)
            yOff = yOff > 0 ? yOff - 128 : yOff + 128;
        return myLoc.translate(xOff, yOff);
    }

    /* Utility functions */

    static Direction fromOrdinal(int i) {
        return directions[i];
    }

    static Direction randomDirection() {
        return directions[(rc.getRoundNum() + rc.getID()) % 8];
    }

    static void logBytecodeUse(int startRound, int startBC) {
        int limit = rc.getType().bytecodeLimit;
        int byteCount = (limit - startBC) + (rc.getRoundNum() - startRound - 1) * limit + Clock.getBytecodeNum();
        System.out.println("@@@Bytecodes used: " + byteCount);
    }
}