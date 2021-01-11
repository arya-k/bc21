package good_bot;

import battlecode.common.*;

import java.util.Arrays;

public class Slanderer extends Robot {

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
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int numMuckrakers = 0;
        Direction[] dangerDirs = new Direction[enemies.length];
        for(int i = 0; i < enemies.length; i++) {
            if(enemies[i].getType() == RobotType.MUCKRAKER) {
                dangerDirs[numMuckrakers++] = enemies[i].getLocation().directionTo(rc.getLocation());
            }
        }
        Direction[] realDirs = new Direction[numMuckrakers];
        for(int i=0; i < numMuckrakers; i++) {
            realDirs[i] = dangerDirs[i];
        }
        System.out.println(Arrays.toString(realDirs));
        Direction move = Nav.tick(realDirs);
        if (move != null && rc.canMove(move)) rc.move(move);
        //TODO run from enemies
    }
}
