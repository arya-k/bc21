package quals;

import battlecode.common.*;

public class Muckraker extends Robot {
    static State state = null;

    /* Scout vars */
    static Direction scoutDir;

    @Override
    void onAwake() {
        state = State.Explore; // by default, explore!
        Nav.doExplore();

        switch (assignment.label) {
            case SCOUT:
                state = State.Scout;
                scoutDir = fromOrdinal(assignment.data[0]);
                Nav.doGoInDir(scoutDir);
                break;
        }
    }

    @Override
    void onUpdate() throws GameActionException {
        super.onUpdate();
        transition();
        state.act();
        Clock.yield();
    }

    void transition() {
        // TODO: Update Safe_Dir estimations...

        // Scout -> Explore (or exit)
        if (state == State.Scout) {
            if (Nav.currentGoal == Nav.NavGoal.Nothing) {
                state = State.Explore;
                Nav.doExplore();
            } else {
                return; // Scouts should not get distracted
            }
        }

        // If we should be Clogging, clog + return:
        // TODO: clogging case

        // Explore -> ChaseSlanderer
        int nearestSlandererID = closestSlanderer();
        if (nearestSlandererID != -1) {
            state = State.ChaseSlanderer;
            Nav.doFollow(nearestSlandererID);
            return;
        }

        // ChaseSlanderer, Clog -> Explore
        if (state != State.Explore) {
            state = State.Explore;
            Nav.doExplore();
        }
    }

    private enum State {
        Scout {
            @Override
            public void act() throws GameActionException {
                if (!rc.isReady()) return;
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        Explore {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        },
        ChaseSlanderer {
            @Override
            public void act() throws GameActionException {
                if (trySlandererKill()) return;

                Direction move = Nav.tick();
                if (move != null && rc.canMove(move)) takeMove(move);
            }
        };

        // TODO: clog

        public abstract void act() throws GameActionException;
    }

    static int closestSlanderer() {
        int closestSlandererID = -1, distanceToSlanderer = -1;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam() && info.getType() == RobotType.SLANDERER) {
                int distance = rc.getLocation().distanceSquaredTo(info.getLocation());
                if (closestSlandererID == -1 || distance < distanceToSlanderer) {
                    closestSlandererID = info.getID();
                    distanceToSlanderer = distance;
                }
            }
        }
        return closestSlandererID;
    }

    static boolean trySlandererKill() throws GameActionException {
        RobotInfo[] neighbors = rc.senseNearbyRobots(RobotType.MUCKRAKER.actionRadiusSquared, rc.getTeam().opponent());
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

        if (best != null && rc.canExpose(best.ID)) {
            rc.expose(best.getLocation());
            return true;
        }
        return false;
    }
}
