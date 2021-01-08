package bot;

import battlecode.common.*;
import bot.Communication.Label;
import bot.Communication.Message;

import static bot.Communication.decode;
import static bot.Communication.encode;

abstract class Robot {
    static RobotController rc = null;
    static RobotType type = null;
    static int centerID;
    static MapLocation centerLoc;
    static Message assignment = null;
    static MapLocation initLoc;
    static int firstTurn;
    static int rounds_left = 0;
    static Message prev_assignment = null;
    static Direction commandDir;

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
        Robot.type = rc.getType();
        Robot.initLoc = rc.getLocation();

        // find the EC
        if (Robot.type != RobotType.ENLIGHTENMENT_CENTER) {
            for (RobotInfo info : rc.senseNearbyRobots(2)) {
                if (info.getType() == RobotType.ENLIGHTENMENT_CENTER && info.getTeam() == rc.getTeam()) {
                    Robot.centerID = info.getID();
                    Robot.centerLoc = info.getLocation();
                    int flag = rc.getFlag(Robot.centerID);
                    Robot.assignment = decode(flag);
                    rc.setFlag(flag);
                    break;
                }
            }
            if (Robot.assignment == null)
                System.out.println("Didnt find assignment!");
        }

    }

    abstract void onAwake() throws GameActionException;

    abstract void onUpdate() throws GameActionException;

    void reassignDefault() {
        assignment = null;
    }

    void scoutLogic(Direction commandDir) throws GameActionException {
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                rc.setFlag(encode(dangerMessage(commandDir)));
                assignment = null;
                onUpdate();
                return;
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
            // reassign
            reassignDefault();
        }
        Clock.yield();
    }

    /**
     * returns direction that moves away from friendly robots within square
     * distance of variable space, null if already away
     *
     * @param space square distance to consider
     * @return the best direction to spread out in, null if should stay put
     */
    static Direction spreadDirection(int space) throws GameActionException {
        RobotInfo[] neighbors = rc.senseNearbyRobots(space, rc.getTeam());
        // already spread out
        if (neighbors.length == 0) return null;

        int d = -1;
        int distance = -1;
        for (int i = 8; --i >= 0; ) {
            MapLocation curr = rc.getLocation().add(directions[i]);
            int tempDistance = 0;
            for (RobotInfo neighbor : neighbors) {
                MapLocation location = neighbor.getLocation();
                tempDistance += curr.distanceSquaredTo(location);
            }
            if (tempDistance > distance) {
                distance = tempDistance;
                d = i;
            }
        }
        return directions[d];
    }

    void expandAwake() throws GameActionException {
        rounds_left = assignment.data[0];
        commandDir = spreadDirection(assignment.data[1]);
        // done expanding
        if (commandDir == null || rounds_left == 0) {
            assignment = null;
            reassignDefault();
        }
        else
            Nav.doGoInDir(commandDir);
    }

    void expandBehavior() throws GameActionException{
        rounds_left--;
        commandDir = spreadDirection(assignment.data[1]);
        // done expanding
        if (commandDir == null || rounds_left == 0) {
            assignment = prev_assignment;
            if (assignment == null)
                reassignDefault();
        }
        else
            Nav.doGoInDir(commandDir);
    }

    void checkExpansion() throws GameActionException {
        RobotInfo[] neighbors = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo neighbor: neighbors) {
            int flag = rc.getFlag(neighbor.getID());
            Message m = decode(flag);
            if (m.label == Label.EXPAND) {
                prev_assignment = assignment;
                assignment = m;
                expandAwake();
                break;
            }
        }

    }

    static MapLocation getLocFromMessage(int xMod, int yMod) {
        MapLocation myLoc = rc.getLocation();
        int x = myLoc.x % 128;
        int y = myLoc.y % 128;
        int xOff = xMod - x;
        int yOff = yMod - y;
        if(Math.abs(xOff) >= 64)
            xOff = xOff > 0 ? xOff - 128 : xOff + 128;
        if (Math.abs(yOff) >= 64)
            yOff = yOff > 0 ? yOff - 128 : yOff + 128;
        return myLoc.translate(xOff, yOff);
    }

    static MapLocation getWallGoalFrom(int[] messageData) {
        MapLocation wallCenter = getLocFromMessage(messageData[0], messageData[1]);
        System.out.println("center wall @ " + wallCenter);
        int wallIx = messageData[2];
        Direction wallDir = messageData[3] == 0 ? Direction.EAST : Direction.NORTH;
        int numIter = (wallIx + 1) / 2;
        if (wallIx % 2 == 0) {
            wallDir = wallDir.opposite();
        }
        wallCenter = wallCenter.translate(wallDir.dx * numIter, wallDir.dy * numIter);
        return wallCenter;
    }

    void wallAwake() throws GameActionException {
        Nav.doGoTo(getWallGoalFrom(assignment.data));
    }

    void wallBehavior() throws GameActionException {
        if (rc.isReady()) {
            Direction move = Nav.tick();
            if (move != null && rc.canMove(move)) rc.move(move);
            Clock.yield();
        }
    }

    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    public static Direction fromOrdinal(int ordinal) {
        return Robot.directions[ordinal];
    }

    Message scoutMessage(Direction dir) throws GameActionException {
        int[] data = {dir.ordinal()};
        return new Message(Label.SCOUT, data);
    }

    Message dangerMessage(Direction dir) {
        int[] data = {dir.ordinal()};
        return new Message(Label.DANGER_DIR, data);
    }

    Message safeDirMessage(Direction commandDir, Direction edgeDir, int offset) {
        int[] data = {commandDir.ordinal(), edgeDir.ordinal(), offset};
        return new Message(Label.SAFE_DIR_EDGE, data);
    }

    static int roundsAlive() {
        return rc.getRoundNum() - firstTurn;
    }

    /**
     * Utility function to log the bytecodes used, accounting for the rounds possibly ticking over.
     */
    static void logBytecodeUse(int startRound, int startBC) {
        int limit = rc.getType().bytecodeLimit;
        int byteCount = (limit - startBC) + (rc.getRoundNum() - startRound - 1) * limit + Clock.getBytecodeNum();
        System.out.println("@@@Bytecodes used: " + byteCount);
    }
}