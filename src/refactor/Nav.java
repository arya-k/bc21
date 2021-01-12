package refactor;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Clock;

/**
 * Navigation class, to manage robot movement in a certain direction at each turn.
 *
 * <b>Usage:</b>
 * Initialize when the robot is created with Nav.init().
 * <p>
 * Set goals with `do{SOMETHING}`. These goals will be worked towards every turn.
 * <p>
 * If tick returns CENTER, it can't improve. If it returns `null`, nav has stopped trying for that goal.
 * <p>
 * Every round, if you want to make a move along a navigational goal, call Nav.tick().
 */
public class Nav {
    // Constants:
    static final int FAILURE_TURNS = 10;

    // Variables:
    private static RobotController rc;
    private static int minDistToGoal = 0;
    private static int turnsSinceImprovement = 0;

    // State Machine:
    public enum NavGoal {
        Nothing,
        Explore,
        GoTo,
        GoInDir,
        Follow,
    }

    public static NavGoal currentGoal = NavGoal.Nothing;
    private static MapLocation goalPos = null; // associated with GoTo
    private static int goalID = -1; // associated with follow

    /**
     * Call when a robot is created to use the navigation module
     *
     * @param rc RobotController.
     */
    public static void init(RobotController rc) {
        Nav.rc = rc;
        NavHistory.init();
    }

    /**
     * Sets the robot to explore mode. Will try to visit each unexplored chunk
     * in the map, noting walls as it gets to them.
     */
    public static void doExplore() {
        currentGoal = NavGoal.Explore;

        goalPos = null;
        minDistToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Replaces setGoal. Tells the robot to head to the given tile. Robot will
     * stop this goal if it fails to make progress for FAILURE_TURNS turns, is adjacent to the goal
     * and realizes is is impossible, or reaches the goal tile.
     */
    public static void doGoTo(MapLocation target) {
        currentGoal = NavGoal.GoTo;
        goalPos = target;

        minDistToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Tells the robot to head in the given direction. Robot will stop this goal if it fails to make
     * progress for FAILURE_TURNS turns, or it realizes it has reached the end of the map.
     */
    public static void doGoInDir(Direction dir) {
        doGoInDir(dir.dx, dir.dy);
    }

    public static void doGoInDir(int dx, int dy) {
        currentGoal = Nav.NavGoal.GoInDir;
        goalPos = rc.getLocation().translate(100 * dx, 100 * dy);

        minDistToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Robot attempts to follow around the robot with the given ID. Will stop this goal if it can no longer
     * see the robot in range.
     */
    public static void doFollow(int targetID) {
        currentGoal = NavGoal.Follow;
        goalID = targetID; // If we start using this, it would be helpful to track it going out of sight...
    }

    /**
     * Avoid moving in the directions of danger. Apart from that, works exactly like tick.
     * Returns CENTER if no improvement is possible this turn. Returns null if nav has given up.
     *
     * @param dangerDirs list of directions where enemies have been spotted. Nav will REFUSE to move in a direction
     *                   where dangerDirs is set. Note that if you include Direction.CENTER, it may break.
     * @return the best direction to move in.
     */
    public static Direction tick(Direction[] dangerDirs) throws GameActionException {
        NavHistory.update(); // keep track of where we've been for the explore function

        // if you can't move, this should not count towards the turnsSinceImprovement counter.
        if (!rc.isReady()) return Direction.CENTER;

        switch (currentGoal) {
            case Nothing:
                return null;
            case GoTo:
            case GoInDir:
                // update distance to goal
                int newDistToGoal = rc.getLocation().distanceSquaredTo(goalPos);
                if (newDistToGoal < minDistToGoal) {
                    turnsSinceImprovement = 0;
                    minDistToGoal = newDistToGoal;
                } else {
                    turnsSinceImprovement++;
                }

                // failure conditions
                if (turnsSinceImprovement >= FAILURE_TURNS || // lack of improvement
                        rc.getLocation() == goalPos || // reached goal
                        (newDistToGoal < 4 && !rc.canMove(rc.getLocation().directionTo(goalPos)))) { // adj and impossible
                    currentGoal = NavGoal.Nothing;
                }

                return currentGoal == NavGoal.Nothing ? null : goTo(goalPos, dangerDirs);

            case Follow:
                if (rc.canSenseRobot(goalID))
                    goalPos = rc.senseRobot(goalID).location;
                else
                    currentGoal = NavGoal.Nothing;

                return currentGoal == NavGoal.Nothing ? null : goTo(goalPos, dangerDirs);

            case Explore:
                if (goalPos != null) {
                    int currDistToGoal = rc.getLocation().distanceSquaredTo(goalPos);
                    if (currDistToGoal < minDistToGoal) {
                        turnsSinceImprovement = 0;
                        minDistToGoal = currDistToGoal;
                    } else {
                        turnsSinceImprovement++;
                    }

                    if (turnsSinceImprovement >= 5)
                        NavHistory.mark_visited(goalPos);
                }

                if (goalPos == null || NavHistory.visited(goalPos)) {// pick a new location to visit if necessary
                    goalPos = NavHistory.nearestUnexploredLocation();
                    turnsSinceImprovement = 0;
                    minDistToGoal = Integer.MAX_VALUE;
                }

                if (goalPos == null) // there are no more unexplored locations to visit :(
                    currentGoal = NavGoal.Nothing;

                return currentGoal == NavGoal.Nothing ? null : goTo(goalPos, dangerDirs);
        }
        return null; // should never get here!
    }

    /**
     * Attempts to find a move that works towards the current goals. Could result in currentGoal being reset
     * to Nothing if the robot determines that no move could be made too many times.
     * <p>
     * If tick returns CENTER, it cannot improve on the position temporarily. If it returns null, then it
     * has stopped the current goal (see the do methods' docstrings)
     *
     * @return the direction of the move to make, or null.
     */
    public static Direction tick() throws GameActionException {
        return tick(new Direction[]{});
    }

    /**
     * Picks the best goTo to use, based on the number of cooldown turns available.
     *
     * @param target the mapLocation to head towards.
     * @return the best direction to head in
     * @throws GameActionException hopefully never.
     */
    private static Direction goTo(MapLocation target, Direction[] dangerDirs) throws GameActionException {
        // setup dangerDirections:
        int danger = 0;
        for (int i = 0; i < dangerDirs.length; i++)
            danger |= 1 << dangerDirs[i].ordinal();

        int budget = Clock.getBytecodeNum() +
                ((int)Math.floor(rc.getCooldownTurns()) * rc.getType().bytecodeLimit)
                - 1000;

        int sightRadius = rc.getType().sensorRadiusSquared;

        if (sightRadius >= 30 && budget >= 12600)
            return goTo30(target, danger);
        else if (sightRadius >= 25 && budget >= 10500)
            return goTo25(target, danger);
        else if (sightRadius >= 20 && budget >= 7600)
            return goTo20(target, danger);

        return goTo8(target, danger);
    }


    // MAX COST: 2250
    private static Direction goTo8(MapLocation target, int danger) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params R_SQUARED=8, NAV_ITERATIONS=2 */

        RobotController rc_ = rc; // move into local scope

        // POPULATE COSTS AND MOVEMENT COSTS
        MapLocation tile = rc_.getLocation().translate(-2, -2);
        double cost_0_0 = tile.distanceSquaredTo(target);
        double move_cost_0_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_0 = Double.MAX_VALUE;
        else
            move_cost_0_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -2);
        double cost_0_1 = tile.distanceSquaredTo(target);
        double move_cost_0_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_1 = Double.MAX_VALUE;
        else
            move_cost_0_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -2);
        double cost_0_2 = tile.distanceSquaredTo(target);
        double move_cost_0_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_2 = Double.MAX_VALUE;
        else
            move_cost_0_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -2);
        double cost_0_3 = tile.distanceSquaredTo(target);
        double move_cost_0_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_3 = Double.MAX_VALUE;
        else
            move_cost_0_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -2);
        double cost_0_4 = tile.distanceSquaredTo(target);
        double move_cost_0_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_4 = Double.MAX_VALUE;
        else
            move_cost_0_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -1);
        double cost_1_0 = tile.distanceSquaredTo(target);
        double move_cost_1_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_0 = Double.MAX_VALUE;
        else
            move_cost_1_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -1);
        double cost_1_1 = tile.distanceSquaredTo(target);
        double move_cost_1_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_1 = Double.MAX_VALUE;
        else
            move_cost_1_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -1);
        double cost_1_2 = tile.distanceSquaredTo(target);
        double move_cost_1_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_2 = Double.MAX_VALUE;
        else
            move_cost_1_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -1);
        double cost_1_3 = tile.distanceSquaredTo(target);
        double move_cost_1_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_3 = Double.MAX_VALUE;
        else
            move_cost_1_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -1);
        double cost_1_4 = tile.distanceSquaredTo(target);
        double move_cost_1_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_4 = Double.MAX_VALUE;
        else
            move_cost_1_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 0);
        double cost_2_0 = tile.distanceSquaredTo(target);
        double move_cost_2_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_0 = Double.MAX_VALUE;
        else
            move_cost_2_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 0);
        double cost_2_1 = tile.distanceSquaredTo(target);
        double move_cost_2_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_1 = Double.MAX_VALUE;
        else
            move_cost_2_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation();
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 0);
        double cost_2_3 = tile.distanceSquaredTo(target);
        double move_cost_2_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_3 = Double.MAX_VALUE;
        else
            move_cost_2_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 0);
        double cost_2_4 = tile.distanceSquaredTo(target);
        double move_cost_2_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_4 = Double.MAX_VALUE;
        else
            move_cost_2_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 1);
        double cost_3_0 = tile.distanceSquaredTo(target);
        double move_cost_3_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_0 = Double.MAX_VALUE;
        else
            move_cost_3_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 1);
        double cost_3_1 = tile.distanceSquaredTo(target);
        double move_cost_3_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_1 = Double.MAX_VALUE;
        else
            move_cost_3_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 1);
        double cost_3_2 = tile.distanceSquaredTo(target);
        double move_cost_3_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_2 = Double.MAX_VALUE;
        else
            move_cost_3_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 1);
        double cost_3_3 = tile.distanceSquaredTo(target);
        double move_cost_3_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_3 = Double.MAX_VALUE;
        else
            move_cost_3_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 1);
        double cost_3_4 = tile.distanceSquaredTo(target);
        double move_cost_3_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_4 = Double.MAX_VALUE;
        else
            move_cost_3_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 2);
        double cost_4_0 = tile.distanceSquaredTo(target);
        double move_cost_4_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_0 = Double.MAX_VALUE;
        else
            move_cost_4_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 2);
        double cost_4_1 = tile.distanceSquaredTo(target);
        double move_cost_4_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_1 = Double.MAX_VALUE;
        else
            move_cost_4_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 2);
        double cost_4_2 = tile.distanceSquaredTo(target);
        double move_cost_4_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_2 = Double.MAX_VALUE;
        else
            move_cost_4_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 2);
        double cost_4_3 = tile.distanceSquaredTo(target);
        double move_cost_4_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_3 = Double.MAX_VALUE;
        else
            move_cost_4_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 2);
        double cost_4_4 = tile.distanceSquaredTo(target);
        double move_cost_4_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_4 = Double.MAX_VALUE;
        else
            move_cost_4_4 = 1 / rc_.sensePassability(tile);
        // iteration 1
        cost_0_0 = Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_0_1, cost_0_0 - move_cost_0_0))) + move_cost_0_0;
        cost_0_1 = Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_0_2, cost_0_1 - move_cost_0_1))))) + move_cost_0_1;
        cost_0_2 = Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2))))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, cost_0_4 - move_cost_0_4))) + move_cost_0_4;
        cost_1_0 = Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_1_1, cost_1_0 - move_cost_1_0))))) + move_cost_1_0;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1)))))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, cost_1_4 - move_cost_1_4))))) + move_cost_1_4;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0))))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, cost_2_4 - move_cost_2_4))))) + move_cost_2_4;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, cost_3_4 - move_cost_3_4))))) + move_cost_3_4;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_2, cost_4_1 - move_cost_4_1))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_3, cost_4_2 - move_cost_4_2))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_4, cost_4_3 - move_cost_4_3))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, cost_4_4 - move_cost_4_4))) + move_cost_4_4;

        // iteration 2
        cost_0_0 = Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_0_1, cost_0_0 - move_cost_0_0))) + move_cost_0_0;
        cost_0_1 = Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_0_2, cost_0_1 - move_cost_0_1))))) + move_cost_0_1;
        cost_0_2 = Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2))))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, cost_0_4 - move_cost_0_4))) + move_cost_0_4;
        cost_1_0 = Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_1_1, cost_1_0 - move_cost_1_0))))) + move_cost_1_0;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1)))))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, cost_1_4 - move_cost_1_4))))) + move_cost_1_4;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0))))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, cost_2_4 - move_cost_2_4))))) + move_cost_2_4;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, cost_3_4 - move_cost_3_4))))) + move_cost_3_4;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_2, cost_4_1 - move_cost_4_1))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_3, cost_4_2 - move_cost_4_2))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_4, cost_4_3 - move_cost_4_3))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, cost_4_4 - move_cost_4_4))) + move_cost_4_4;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = cost_2_2;

        if (cost_2_3 < minCost && (danger & 4) == 0) {
            minCost = cost_2_3;ret = Direction.EAST;
        }
        if (cost_3_3 < minCost && (danger & 2) == 0) {
            minCost = cost_3_3;ret = Direction.NORTHEAST;
        }
        if (cost_3_2 < minCost && (danger & 1) == 0) {
            minCost = cost_3_2;ret = Direction.NORTH;
        }
        if (cost_3_1 < minCost && (danger & 128) == 0) {
            minCost = cost_3_1;ret = Direction.NORTHWEST;
        }
        if (cost_2_1 < minCost && (danger & 64) == 0) {
            minCost = cost_2_1;ret = Direction.WEST;
        }
        if (cost_1_1 < minCost && (danger & 32) == 0) {
            minCost = cost_1_1;ret = Direction.SOUTHWEST;
        }
        if (cost_1_2 < minCost && (danger & 16) == 0) {
            minCost = cost_1_2;ret = Direction.SOUTH;
        }
        if (cost_1_3 < minCost && (danger & 8) == 0) {
            ret = Direction.SOUTHEAST;
        }
        return ret;
    }

    // COST: 7600
    private static Direction goTo20(MapLocation target, int danger) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params R_SQUARED=20, NAV_ITERATIONS=3 */

        RobotController rc_ = rc; // move into local scope

        // POPULATE COSTS AND MOVEMENT COSTS
        MapLocation tile = rc_.getLocation().translate(-2, -4);
        double cost_0_2 = tile.distanceSquaredTo(target);
        double move_cost_0_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_2 = Double.MAX_VALUE;
        else
            move_cost_0_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -4);
        double cost_0_3 = tile.distanceSquaredTo(target);
        double move_cost_0_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_3 = Double.MAX_VALUE;
        else
            move_cost_0_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -4);
        double cost_0_4 = tile.distanceSquaredTo(target);
        double move_cost_0_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_4 = Double.MAX_VALUE;
        else
            move_cost_0_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -4);
        double cost_0_5 = tile.distanceSquaredTo(target);
        double move_cost_0_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_5 = Double.MAX_VALUE;
        else
            move_cost_0_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -4);
        double cost_0_6 = tile.distanceSquaredTo(target);
        double move_cost_0_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_6 = Double.MAX_VALUE;
        else
            move_cost_0_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -3);
        double cost_1_1 = tile.distanceSquaredTo(target);
        double move_cost_1_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_1 = Double.MAX_VALUE;
        else
            move_cost_1_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -3);
        double cost_1_2 = tile.distanceSquaredTo(target);
        double move_cost_1_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_2 = Double.MAX_VALUE;
        else
            move_cost_1_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -3);
        double cost_1_3 = tile.distanceSquaredTo(target);
        double move_cost_1_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_3 = Double.MAX_VALUE;
        else
            move_cost_1_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -3);
        double cost_1_4 = tile.distanceSquaredTo(target);
        double move_cost_1_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_4 = Double.MAX_VALUE;
        else
            move_cost_1_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -3);
        double cost_1_5 = tile.distanceSquaredTo(target);
        double move_cost_1_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_5 = Double.MAX_VALUE;
        else
            move_cost_1_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -3);
        double cost_1_6 = tile.distanceSquaredTo(target);
        double move_cost_1_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_6 = Double.MAX_VALUE;
        else
            move_cost_1_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -3);
        double cost_1_7 = tile.distanceSquaredTo(target);
        double move_cost_1_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_7 = Double.MAX_VALUE;
        else
            move_cost_1_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -2);
        double cost_2_0 = tile.distanceSquaredTo(target);
        double move_cost_2_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_0 = Double.MAX_VALUE;
        else
            move_cost_2_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -2);
        double cost_2_1 = tile.distanceSquaredTo(target);
        double move_cost_2_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_1 = Double.MAX_VALUE;
        else
            move_cost_2_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -2);
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_2 = Double.MAX_VALUE;
        else
            move_cost_2_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -2);
        double cost_2_3 = tile.distanceSquaredTo(target);
        double move_cost_2_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_3 = Double.MAX_VALUE;
        else
            move_cost_2_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -2);
        double cost_2_4 = tile.distanceSquaredTo(target);
        double move_cost_2_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_4 = Double.MAX_VALUE;
        else
            move_cost_2_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -2);
        double cost_2_5 = tile.distanceSquaredTo(target);
        double move_cost_2_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_5 = Double.MAX_VALUE;
        else
            move_cost_2_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -2);
        double cost_2_6 = tile.distanceSquaredTo(target);
        double move_cost_2_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_6 = Double.MAX_VALUE;
        else
            move_cost_2_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -2);
        double cost_2_7 = tile.distanceSquaredTo(target);
        double move_cost_2_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_7 = Double.MAX_VALUE;
        else
            move_cost_2_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -2);
        double cost_2_8 = tile.distanceSquaredTo(target);
        double move_cost_2_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_8 = Double.MAX_VALUE;
        else
            move_cost_2_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -1);
        double cost_3_0 = tile.distanceSquaredTo(target);
        double move_cost_3_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_0 = Double.MAX_VALUE;
        else
            move_cost_3_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -1);
        double cost_3_1 = tile.distanceSquaredTo(target);
        double move_cost_3_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_1 = Double.MAX_VALUE;
        else
            move_cost_3_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -1);
        double cost_3_2 = tile.distanceSquaredTo(target);
        double move_cost_3_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_2 = Double.MAX_VALUE;
        else
            move_cost_3_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -1);
        double cost_3_3 = tile.distanceSquaredTo(target);
        double move_cost_3_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_3 = Double.MAX_VALUE;
        else
            move_cost_3_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -1);
        double cost_3_4 = tile.distanceSquaredTo(target);
        double move_cost_3_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_4 = Double.MAX_VALUE;
        else
            move_cost_3_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -1);
        double cost_3_5 = tile.distanceSquaredTo(target);
        double move_cost_3_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_5 = Double.MAX_VALUE;
        else
            move_cost_3_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -1);
        double cost_3_6 = tile.distanceSquaredTo(target);
        double move_cost_3_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_6 = Double.MAX_VALUE;
        else
            move_cost_3_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -1);
        double cost_3_7 = tile.distanceSquaredTo(target);
        double move_cost_3_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_7 = Double.MAX_VALUE;
        else
            move_cost_3_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -1);
        double cost_3_8 = tile.distanceSquaredTo(target);
        double move_cost_3_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_8 = Double.MAX_VALUE;
        else
            move_cost_3_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 0);
        double cost_4_0 = tile.distanceSquaredTo(target);
        double move_cost_4_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_0 = Double.MAX_VALUE;
        else
            move_cost_4_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 0);
        double cost_4_1 = tile.distanceSquaredTo(target);
        double move_cost_4_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_1 = Double.MAX_VALUE;
        else
            move_cost_4_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 0);
        double cost_4_2 = tile.distanceSquaredTo(target);
        double move_cost_4_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_2 = Double.MAX_VALUE;
        else
            move_cost_4_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 0);
        double cost_4_3 = tile.distanceSquaredTo(target);
        double move_cost_4_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_3 = Double.MAX_VALUE;
        else
            move_cost_4_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation();
        double cost_4_4 = tile.distanceSquaredTo(target);
        double move_cost_4_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 0);
        double cost_4_5 = tile.distanceSquaredTo(target);
        double move_cost_4_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_5 = Double.MAX_VALUE;
        else
            move_cost_4_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 0);
        double cost_4_6 = tile.distanceSquaredTo(target);
        double move_cost_4_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_6 = Double.MAX_VALUE;
        else
            move_cost_4_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 0);
        double cost_4_7 = tile.distanceSquaredTo(target);
        double move_cost_4_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_7 = Double.MAX_VALUE;
        else
            move_cost_4_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 0);
        double cost_4_8 = tile.distanceSquaredTo(target);
        double move_cost_4_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_8 = Double.MAX_VALUE;
        else
            move_cost_4_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 1);
        double cost_5_0 = tile.distanceSquaredTo(target);
        double move_cost_5_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_0 = Double.MAX_VALUE;
        else
            move_cost_5_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 1);
        double cost_5_1 = tile.distanceSquaredTo(target);
        double move_cost_5_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_1 = Double.MAX_VALUE;
        else
            move_cost_5_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 1);
        double cost_5_2 = tile.distanceSquaredTo(target);
        double move_cost_5_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_2 = Double.MAX_VALUE;
        else
            move_cost_5_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 1);
        double cost_5_3 = tile.distanceSquaredTo(target);
        double move_cost_5_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_3 = Double.MAX_VALUE;
        else
            move_cost_5_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 1);
        double cost_5_4 = tile.distanceSquaredTo(target);
        double move_cost_5_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_4 = Double.MAX_VALUE;
        else
            move_cost_5_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 1);
        double cost_5_5 = tile.distanceSquaredTo(target);
        double move_cost_5_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_5 = Double.MAX_VALUE;
        else
            move_cost_5_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 1);
        double cost_5_6 = tile.distanceSquaredTo(target);
        double move_cost_5_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_6 = Double.MAX_VALUE;
        else
            move_cost_5_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 1);
        double cost_5_7 = tile.distanceSquaredTo(target);
        double move_cost_5_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_7 = Double.MAX_VALUE;
        else
            move_cost_5_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 1);
        double cost_5_8 = tile.distanceSquaredTo(target);
        double move_cost_5_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_8 = Double.MAX_VALUE;
        else
            move_cost_5_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 2);
        double cost_6_0 = tile.distanceSquaredTo(target);
        double move_cost_6_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_0 = Double.MAX_VALUE;
        else
            move_cost_6_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 2);
        double cost_6_1 = tile.distanceSquaredTo(target);
        double move_cost_6_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_1 = Double.MAX_VALUE;
        else
            move_cost_6_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 2);
        double cost_6_2 = tile.distanceSquaredTo(target);
        double move_cost_6_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_2 = Double.MAX_VALUE;
        else
            move_cost_6_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 2);
        double cost_6_3 = tile.distanceSquaredTo(target);
        double move_cost_6_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_3 = Double.MAX_VALUE;
        else
            move_cost_6_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 2);
        double cost_6_4 = tile.distanceSquaredTo(target);
        double move_cost_6_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_4 = Double.MAX_VALUE;
        else
            move_cost_6_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 2);
        double cost_6_5 = tile.distanceSquaredTo(target);
        double move_cost_6_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_5 = Double.MAX_VALUE;
        else
            move_cost_6_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 2);
        double cost_6_6 = tile.distanceSquaredTo(target);
        double move_cost_6_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_6 = Double.MAX_VALUE;
        else
            move_cost_6_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 2);
        double cost_6_7 = tile.distanceSquaredTo(target);
        double move_cost_6_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_7 = Double.MAX_VALUE;
        else
            move_cost_6_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 2);
        double cost_6_8 = tile.distanceSquaredTo(target);
        double move_cost_6_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_8 = Double.MAX_VALUE;
        else
            move_cost_6_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 3);
        double cost_7_1 = tile.distanceSquaredTo(target);
        double move_cost_7_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_1 = Double.MAX_VALUE;
        else
            move_cost_7_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 3);
        double cost_7_2 = tile.distanceSquaredTo(target);
        double move_cost_7_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_2 = Double.MAX_VALUE;
        else
            move_cost_7_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 3);
        double cost_7_3 = tile.distanceSquaredTo(target);
        double move_cost_7_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_3 = Double.MAX_VALUE;
        else
            move_cost_7_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 3);
        double cost_7_4 = tile.distanceSquaredTo(target);
        double move_cost_7_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_4 = Double.MAX_VALUE;
        else
            move_cost_7_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 3);
        double cost_7_5 = tile.distanceSquaredTo(target);
        double move_cost_7_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_5 = Double.MAX_VALUE;
        else
            move_cost_7_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 3);
        double cost_7_6 = tile.distanceSquaredTo(target);
        double move_cost_7_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_6 = Double.MAX_VALUE;
        else
            move_cost_7_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 3);
        double cost_7_7 = tile.distanceSquaredTo(target);
        double move_cost_7_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_7 = Double.MAX_VALUE;
        else
            move_cost_7_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 4);
        double cost_8_2 = tile.distanceSquaredTo(target);
        double move_cost_8_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_2 = Double.MAX_VALUE;
        else
            move_cost_8_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 4);
        double cost_8_3 = tile.distanceSquaredTo(target);
        double move_cost_8_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_3 = Double.MAX_VALUE;
        else
            move_cost_8_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 4);
        double cost_8_4 = tile.distanceSquaredTo(target);
        double move_cost_8_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_4 = Double.MAX_VALUE;
        else
            move_cost_8_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 4);
        double cost_8_5 = tile.distanceSquaredTo(target);
        double move_cost_8_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_5 = Double.MAX_VALUE;
        else
            move_cost_8_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 4);
        double cost_8_6 = tile.distanceSquaredTo(target);
        double move_cost_8_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_6 = Double.MAX_VALUE;
        else
            move_cost_8_6 = 1 / rc_.sensePassability(tile);
        // iteration 1
        cost_0_2 = Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2)))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, cost_0_6 - move_cost_0_6)))) + move_cost_0_6;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0)))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, cost_2_8 - move_cost_2_8)))) + move_cost_2_8;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, cost_3_8 - move_cost_3_8))))) + move_cost_3_8;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, cost_4_8 - move_cost_4_8))))) + move_cost_4_8;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, cost_5_8 - move_cost_5_8))))) + move_cost_5_8;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0)))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_8, cost_6_7 - move_cost_6_7))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, cost_6_8 - move_cost_6_8)))) + move_cost_6_8;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_7, cost_7_6 - move_cost_7_6))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, cost_7_7 - move_cost_7_7))))) + move_cost_7_7;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_3, cost_8_2 - move_cost_8_2)))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_4, cost_8_3 - move_cost_8_3))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_5, cost_8_4 - move_cost_8_4))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_6, cost_8_5 - move_cost_8_5))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, cost_8_6 - move_cost_8_6)))) + move_cost_8_6;

        // iteration 2
        cost_0_2 = Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2)))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, cost_0_6 - move_cost_0_6)))) + move_cost_0_6;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0)))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, cost_2_8 - move_cost_2_8)))) + move_cost_2_8;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, cost_3_8 - move_cost_3_8))))) + move_cost_3_8;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, cost_4_8 - move_cost_4_8))))) + move_cost_4_8;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, cost_5_8 - move_cost_5_8))))) + move_cost_5_8;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0)))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_8, cost_6_7 - move_cost_6_7))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, cost_6_8 - move_cost_6_8)))) + move_cost_6_8;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_7, cost_7_6 - move_cost_7_6))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, cost_7_7 - move_cost_7_7))))) + move_cost_7_7;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_3, cost_8_2 - move_cost_8_2)))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_4, cost_8_3 - move_cost_8_3))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_5, cost_8_4 - move_cost_8_4))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_6, cost_8_5 - move_cost_8_5))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, cost_8_6 - move_cost_8_6)))) + move_cost_8_6;

        // iteration 3
        cost_0_2 = Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2)))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, cost_0_6 - move_cost_0_6)))) + move_cost_0_6;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0)))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, cost_2_8 - move_cost_2_8)))) + move_cost_2_8;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, cost_3_8 - move_cost_3_8))))) + move_cost_3_8;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, cost_4_8 - move_cost_4_8))))) + move_cost_4_8;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, cost_5_8 - move_cost_5_8))))) + move_cost_5_8;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0)))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_8, cost_6_7 - move_cost_6_7))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, cost_6_8 - move_cost_6_8)))) + move_cost_6_8;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_7, cost_7_6 - move_cost_7_6))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, cost_7_7 - move_cost_7_7))))) + move_cost_7_7;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_3, cost_8_2 - move_cost_8_2)))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_4, cost_8_3 - move_cost_8_3))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_5, cost_8_4 - move_cost_8_4))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_6, cost_8_5 - move_cost_8_5))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, cost_8_6 - move_cost_8_6)))) + move_cost_8_6;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = cost_4_4;

        if (cost_4_5 < minCost && (danger & 4) == 0) {
            minCost = cost_4_5;ret = Direction.EAST;
        }
        if (cost_5_5 < minCost && (danger & 2) == 0) {
            minCost = cost_5_5;ret = Direction.NORTHEAST;
        }
        if (cost_5_4 < minCost && (danger & 1) == 0) {
            minCost = cost_5_4;ret = Direction.NORTH;
        }
        if (cost_5_3 < minCost && (danger & 128) == 0) {
            minCost = cost_5_3;ret = Direction.NORTHWEST;
        }
        if (cost_4_3 < minCost && (danger & 64) == 0) {
            minCost = cost_4_3;ret = Direction.WEST;
        }
        if (cost_3_3 < minCost && (danger & 32) == 0) {
            minCost = cost_3_3;ret = Direction.SOUTHWEST;
        }
        if (cost_3_4 < minCost && (danger & 16) == 0) {
            minCost = cost_3_4;ret = Direction.SOUTH;
        }
        if (cost_3_5 < minCost && (danger & 8) == 0) {
            ret = Direction.SOUTHEAST;
        }
        return ret;
    }

    // COST: 10500
    private static Direction goTo25(MapLocation target, int danger) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params R_SQUARED=25, NAV_ITERATIONS=4 */

        RobotController rc_ = rc; // move into local scope

        // POPULATE COSTS AND MOVEMENT COSTS
        MapLocation tile = rc_.getLocation().translate(0, -5);
        double cost_0_5 = tile.distanceSquaredTo(target);
        double move_cost_0_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_5 = Double.MAX_VALUE;
        else
            move_cost_0_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -4);
        double cost_1_2 = tile.distanceSquaredTo(target);
        double move_cost_1_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_2 = Double.MAX_VALUE;
        else
            move_cost_1_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -4);
        double cost_1_3 = tile.distanceSquaredTo(target);
        double move_cost_1_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_3 = Double.MAX_VALUE;
        else
            move_cost_1_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -4);
        double cost_1_4 = tile.distanceSquaredTo(target);
        double move_cost_1_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_4 = Double.MAX_VALUE;
        else
            move_cost_1_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -4);
        double cost_1_5 = tile.distanceSquaredTo(target);
        double move_cost_1_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_5 = Double.MAX_VALUE;
        else
            move_cost_1_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -4);
        double cost_1_6 = tile.distanceSquaredTo(target);
        double move_cost_1_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_6 = Double.MAX_VALUE;
        else
            move_cost_1_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -4);
        double cost_1_7 = tile.distanceSquaredTo(target);
        double move_cost_1_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_7 = Double.MAX_VALUE;
        else
            move_cost_1_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -4);
        double cost_1_8 = tile.distanceSquaredTo(target);
        double move_cost_1_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_8 = Double.MAX_VALUE;
        else
            move_cost_1_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -3);
        double cost_2_1 = tile.distanceSquaredTo(target);
        double move_cost_2_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_1 = Double.MAX_VALUE;
        else
            move_cost_2_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -3);
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_2 = Double.MAX_VALUE;
        else
            move_cost_2_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -3);
        double cost_2_3 = tile.distanceSquaredTo(target);
        double move_cost_2_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_3 = Double.MAX_VALUE;
        else
            move_cost_2_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -3);
        double cost_2_4 = tile.distanceSquaredTo(target);
        double move_cost_2_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_4 = Double.MAX_VALUE;
        else
            move_cost_2_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -3);
        double cost_2_5 = tile.distanceSquaredTo(target);
        double move_cost_2_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_5 = Double.MAX_VALUE;
        else
            move_cost_2_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -3);
        double cost_2_6 = tile.distanceSquaredTo(target);
        double move_cost_2_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_6 = Double.MAX_VALUE;
        else
            move_cost_2_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -3);
        double cost_2_7 = tile.distanceSquaredTo(target);
        double move_cost_2_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_7 = Double.MAX_VALUE;
        else
            move_cost_2_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -3);
        double cost_2_8 = tile.distanceSquaredTo(target);
        double move_cost_2_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_8 = Double.MAX_VALUE;
        else
            move_cost_2_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -3);
        double cost_2_9 = tile.distanceSquaredTo(target);
        double move_cost_2_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_9 = Double.MAX_VALUE;
        else
            move_cost_2_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -2);
        double cost_3_1 = tile.distanceSquaredTo(target);
        double move_cost_3_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_1 = Double.MAX_VALUE;
        else
            move_cost_3_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -2);
        double cost_3_2 = tile.distanceSquaredTo(target);
        double move_cost_3_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_2 = Double.MAX_VALUE;
        else
            move_cost_3_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -2);
        double cost_3_3 = tile.distanceSquaredTo(target);
        double move_cost_3_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_3 = Double.MAX_VALUE;
        else
            move_cost_3_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -2);
        double cost_3_4 = tile.distanceSquaredTo(target);
        double move_cost_3_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_4 = Double.MAX_VALUE;
        else
            move_cost_3_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -2);
        double cost_3_5 = tile.distanceSquaredTo(target);
        double move_cost_3_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_5 = Double.MAX_VALUE;
        else
            move_cost_3_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -2);
        double cost_3_6 = tile.distanceSquaredTo(target);
        double move_cost_3_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_6 = Double.MAX_VALUE;
        else
            move_cost_3_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -2);
        double cost_3_7 = tile.distanceSquaredTo(target);
        double move_cost_3_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_7 = Double.MAX_VALUE;
        else
            move_cost_3_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -2);
        double cost_3_8 = tile.distanceSquaredTo(target);
        double move_cost_3_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_8 = Double.MAX_VALUE;
        else
            move_cost_3_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -2);
        double cost_3_9 = tile.distanceSquaredTo(target);
        double move_cost_3_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_9 = Double.MAX_VALUE;
        else
            move_cost_3_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -1);
        double cost_4_1 = tile.distanceSquaredTo(target);
        double move_cost_4_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_1 = Double.MAX_VALUE;
        else
            move_cost_4_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -1);
        double cost_4_2 = tile.distanceSquaredTo(target);
        double move_cost_4_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_2 = Double.MAX_VALUE;
        else
            move_cost_4_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -1);
        double cost_4_3 = tile.distanceSquaredTo(target);
        double move_cost_4_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_3 = Double.MAX_VALUE;
        else
            move_cost_4_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -1);
        double cost_4_4 = tile.distanceSquaredTo(target);
        double move_cost_4_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_4 = Double.MAX_VALUE;
        else
            move_cost_4_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -1);
        double cost_4_5 = tile.distanceSquaredTo(target);
        double move_cost_4_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_5 = Double.MAX_VALUE;
        else
            move_cost_4_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -1);
        double cost_4_6 = tile.distanceSquaredTo(target);
        double move_cost_4_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_6 = Double.MAX_VALUE;
        else
            move_cost_4_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -1);
        double cost_4_7 = tile.distanceSquaredTo(target);
        double move_cost_4_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_7 = Double.MAX_VALUE;
        else
            move_cost_4_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -1);
        double cost_4_8 = tile.distanceSquaredTo(target);
        double move_cost_4_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_8 = Double.MAX_VALUE;
        else
            move_cost_4_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -1);
        double cost_4_9 = tile.distanceSquaredTo(target);
        double move_cost_4_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_9 = Double.MAX_VALUE;
        else
            move_cost_4_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, 0);
        double cost_5_0 = tile.distanceSquaredTo(target);
        double move_cost_5_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_0 = Double.MAX_VALUE;
        else
            move_cost_5_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 0);
        double cost_5_1 = tile.distanceSquaredTo(target);
        double move_cost_5_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_1 = Double.MAX_VALUE;
        else
            move_cost_5_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 0);
        double cost_5_2 = tile.distanceSquaredTo(target);
        double move_cost_5_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_2 = Double.MAX_VALUE;
        else
            move_cost_5_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 0);
        double cost_5_3 = tile.distanceSquaredTo(target);
        double move_cost_5_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_3 = Double.MAX_VALUE;
        else
            move_cost_5_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 0);
        double cost_5_4 = tile.distanceSquaredTo(target);
        double move_cost_5_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_4 = Double.MAX_VALUE;
        else
            move_cost_5_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation();
        double cost_5_5 = tile.distanceSquaredTo(target);
        double move_cost_5_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 0);
        double cost_5_6 = tile.distanceSquaredTo(target);
        double move_cost_5_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_6 = Double.MAX_VALUE;
        else
            move_cost_5_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 0);
        double cost_5_7 = tile.distanceSquaredTo(target);
        double move_cost_5_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_7 = Double.MAX_VALUE;
        else
            move_cost_5_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 0);
        double cost_5_8 = tile.distanceSquaredTo(target);
        double move_cost_5_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_8 = Double.MAX_VALUE;
        else
            move_cost_5_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 0);
        double cost_5_9 = tile.distanceSquaredTo(target);
        double move_cost_5_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_9 = Double.MAX_VALUE;
        else
            move_cost_5_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, 0);
        double cost_5_10 = tile.distanceSquaredTo(target);
        double move_cost_5_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_10 = Double.MAX_VALUE;
        else
            move_cost_5_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 1);
        double cost_6_1 = tile.distanceSquaredTo(target);
        double move_cost_6_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_1 = Double.MAX_VALUE;
        else
            move_cost_6_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 1);
        double cost_6_2 = tile.distanceSquaredTo(target);
        double move_cost_6_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_2 = Double.MAX_VALUE;
        else
            move_cost_6_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 1);
        double cost_6_3 = tile.distanceSquaredTo(target);
        double move_cost_6_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_3 = Double.MAX_VALUE;
        else
            move_cost_6_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 1);
        double cost_6_4 = tile.distanceSquaredTo(target);
        double move_cost_6_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_4 = Double.MAX_VALUE;
        else
            move_cost_6_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 1);
        double cost_6_5 = tile.distanceSquaredTo(target);
        double move_cost_6_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_5 = Double.MAX_VALUE;
        else
            move_cost_6_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 1);
        double cost_6_6 = tile.distanceSquaredTo(target);
        double move_cost_6_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_6 = Double.MAX_VALUE;
        else
            move_cost_6_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 1);
        double cost_6_7 = tile.distanceSquaredTo(target);
        double move_cost_6_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_7 = Double.MAX_VALUE;
        else
            move_cost_6_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 1);
        double cost_6_8 = tile.distanceSquaredTo(target);
        double move_cost_6_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_8 = Double.MAX_VALUE;
        else
            move_cost_6_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 1);
        double cost_6_9 = tile.distanceSquaredTo(target);
        double move_cost_6_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_9 = Double.MAX_VALUE;
        else
            move_cost_6_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 2);
        double cost_7_1 = tile.distanceSquaredTo(target);
        double move_cost_7_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_1 = Double.MAX_VALUE;
        else
            move_cost_7_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 2);
        double cost_7_2 = tile.distanceSquaredTo(target);
        double move_cost_7_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_2 = Double.MAX_VALUE;
        else
            move_cost_7_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 2);
        double cost_7_3 = tile.distanceSquaredTo(target);
        double move_cost_7_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_3 = Double.MAX_VALUE;
        else
            move_cost_7_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 2);
        double cost_7_4 = tile.distanceSquaredTo(target);
        double move_cost_7_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_4 = Double.MAX_VALUE;
        else
            move_cost_7_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 2);
        double cost_7_5 = tile.distanceSquaredTo(target);
        double move_cost_7_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_5 = Double.MAX_VALUE;
        else
            move_cost_7_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 2);
        double cost_7_6 = tile.distanceSquaredTo(target);
        double move_cost_7_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_6 = Double.MAX_VALUE;
        else
            move_cost_7_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 2);
        double cost_7_7 = tile.distanceSquaredTo(target);
        double move_cost_7_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_7 = Double.MAX_VALUE;
        else
            move_cost_7_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 2);
        double cost_7_8 = tile.distanceSquaredTo(target);
        double move_cost_7_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_8 = Double.MAX_VALUE;
        else
            move_cost_7_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 2);
        double cost_7_9 = tile.distanceSquaredTo(target);
        double move_cost_7_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_9 = Double.MAX_VALUE;
        else
            move_cost_7_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 3);
        double cost_8_1 = tile.distanceSquaredTo(target);
        double move_cost_8_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_1 = Double.MAX_VALUE;
        else
            move_cost_8_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 3);
        double cost_8_2 = tile.distanceSquaredTo(target);
        double move_cost_8_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_2 = Double.MAX_VALUE;
        else
            move_cost_8_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 3);
        double cost_8_3 = tile.distanceSquaredTo(target);
        double move_cost_8_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_3 = Double.MAX_VALUE;
        else
            move_cost_8_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 3);
        double cost_8_4 = tile.distanceSquaredTo(target);
        double move_cost_8_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_4 = Double.MAX_VALUE;
        else
            move_cost_8_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 3);
        double cost_8_5 = tile.distanceSquaredTo(target);
        double move_cost_8_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_5 = Double.MAX_VALUE;
        else
            move_cost_8_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 3);
        double cost_8_6 = tile.distanceSquaredTo(target);
        double move_cost_8_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_6 = Double.MAX_VALUE;
        else
            move_cost_8_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 3);
        double cost_8_7 = tile.distanceSquaredTo(target);
        double move_cost_8_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_7 = Double.MAX_VALUE;
        else
            move_cost_8_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 3);
        double cost_8_8 = tile.distanceSquaredTo(target);
        double move_cost_8_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_8 = Double.MAX_VALUE;
        else
            move_cost_8_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 3);
        double cost_8_9 = tile.distanceSquaredTo(target);
        double move_cost_8_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_9 = Double.MAX_VALUE;
        else
            move_cost_8_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 4);
        double cost_9_2 = tile.distanceSquaredTo(target);
        double move_cost_9_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_2 = Double.MAX_VALUE;
        else
            move_cost_9_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 4);
        double cost_9_3 = tile.distanceSquaredTo(target);
        double move_cost_9_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_3 = Double.MAX_VALUE;
        else
            move_cost_9_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 4);
        double cost_9_4 = tile.distanceSquaredTo(target);
        double move_cost_9_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_4 = Double.MAX_VALUE;
        else
            move_cost_9_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 4);
        double cost_9_5 = tile.distanceSquaredTo(target);
        double move_cost_9_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_5 = Double.MAX_VALUE;
        else
            move_cost_9_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 4);
        double cost_9_6 = tile.distanceSquaredTo(target);
        double move_cost_9_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_6 = Double.MAX_VALUE;
        else
            move_cost_9_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 4);
        double cost_9_7 = tile.distanceSquaredTo(target);
        double move_cost_9_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_7 = Double.MAX_VALUE;
        else
            move_cost_9_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 4);
        double cost_9_8 = tile.distanceSquaredTo(target);
        double move_cost_9_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_8 = Double.MAX_VALUE;
        else
            move_cost_9_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 5);
        double cost_10_5 = tile.distanceSquaredTo(target);
        double move_cost_10_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_5 = Double.MAX_VALUE;
        else
            move_cost_10_5 = 1 / rc_.sensePassability(tile);
        // iteration 1
        cost_0_5 = Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, cost_0_5 - move_cost_0_5))) + move_cost_0_5;
        cost_1_2 = Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8)))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, cost_2_9 - move_cost_2_9)))) + move_cost_2_9;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, cost_3_9 - move_cost_3_9))))) + move_cost_3_9;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_9 - move_cost_4_9)))))) + move_cost_4_9;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, cost_5_10 - move_cost_5_10))) + move_cost_5_10;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, cost_6_9 - move_cost_6_9)))))) + move_cost_6_9;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, cost_7_9 - move_cost_7_9))))) + move_cost_7_9;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1)))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9)))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_9_3, cost_9_2 - move_cost_9_2)))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_5, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, cost_9_8 - move_cost_9_8)))) + move_cost_9_8;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, cost_10_5 - move_cost_10_5))) + move_cost_10_5;

        // iteration 2
        cost_0_5 = Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, cost_0_5 - move_cost_0_5))) + move_cost_0_5;
        cost_1_2 = Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8)))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, cost_2_9 - move_cost_2_9)))) + move_cost_2_9;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, cost_3_9 - move_cost_3_9))))) + move_cost_3_9;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_9 - move_cost_4_9)))))) + move_cost_4_9;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, cost_5_10 - move_cost_5_10))) + move_cost_5_10;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, cost_6_9 - move_cost_6_9)))))) + move_cost_6_9;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, cost_7_9 - move_cost_7_9))))) + move_cost_7_9;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1)))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9)))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_9_3, cost_9_2 - move_cost_9_2)))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_5, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, cost_9_8 - move_cost_9_8)))) + move_cost_9_8;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, cost_10_5 - move_cost_10_5))) + move_cost_10_5;

        // iteration 3
        cost_0_5 = Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, cost_0_5 - move_cost_0_5))) + move_cost_0_5;
        cost_1_2 = Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8)))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, cost_2_9 - move_cost_2_9)))) + move_cost_2_9;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, cost_3_9 - move_cost_3_9))))) + move_cost_3_9;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_9 - move_cost_4_9)))))) + move_cost_4_9;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, cost_5_10 - move_cost_5_10))) + move_cost_5_10;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, cost_6_9 - move_cost_6_9)))))) + move_cost_6_9;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, cost_7_9 - move_cost_7_9))))) + move_cost_7_9;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1)))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9)))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_9_3, cost_9_2 - move_cost_9_2)))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_5, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, cost_9_8 - move_cost_9_8)))) + move_cost_9_8;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, cost_10_5 - move_cost_10_5))) + move_cost_10_5;

        // iteration 4
        cost_0_5 = Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, cost_0_5 - move_cost_0_5))) + move_cost_0_5;
        cost_1_2 = Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8)))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, cost_2_9 - move_cost_2_9)))) + move_cost_2_9;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, cost_3_9 - move_cost_3_9))))) + move_cost_3_9;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_9 - move_cost_4_9)))))) + move_cost_4_9;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, cost_5_10 - move_cost_5_10))) + move_cost_5_10;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, cost_6_9 - move_cost_6_9)))))) + move_cost_6_9;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, cost_7_9 - move_cost_7_9))))) + move_cost_7_9;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1)))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9)))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_9_3, cost_9_2 - move_cost_9_2)))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_5, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, cost_9_8 - move_cost_9_8)))) + move_cost_9_8;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, cost_10_5 - move_cost_10_5))) + move_cost_10_5;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = cost_5_5;

        if (cost_5_6 < minCost && (danger & 4) == 0) {
            minCost = cost_5_6;ret = Direction.EAST;
        }
        if (cost_6_6 < minCost && (danger & 2) == 0) {
            minCost = cost_6_6;ret = Direction.NORTHEAST;
        }
        if (cost_6_5 < minCost && (danger & 1) == 0) {
            minCost = cost_6_5;ret = Direction.NORTH;
        }
        if (cost_6_4 < minCost && (danger & 128) == 0) {
            minCost = cost_6_4;ret = Direction.NORTHWEST;
        }
        if (cost_5_4 < minCost && (danger & 64) == 0) {
            minCost = cost_5_4;ret = Direction.WEST;
        }
        if (cost_4_4 < minCost && (danger & 32) == 0) {
            minCost = cost_4_4;ret = Direction.SOUTHWEST;
        }
        if (cost_4_5 < minCost && (danger & 16) == 0) {
            minCost = cost_4_5;ret = Direction.SOUTH;
        }
        if (cost_4_6 < minCost && (danger & 8) == 0) {
            ret = Direction.SOUTHEAST;
        }
        return ret;
    }

    // COST: 12600
    private static Direction goTo30(MapLocation target, int danger) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params R_SQUARED=30, NAV_ITERATIONS=4 */

        RobotController rc_ = rc; // move into local scope

        // POPULATE COSTS AND MOVEMENT COSTS
        MapLocation tile = rc_.getLocation().translate(-2, -5);
        double cost_0_3 = tile.distanceSquaredTo(target);
        double move_cost_0_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_3 = Double.MAX_VALUE;
        else
            move_cost_0_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -5);
        double cost_0_4 = tile.distanceSquaredTo(target);
        double move_cost_0_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_4 = Double.MAX_VALUE;
        else
            move_cost_0_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -5);
        double cost_0_5 = tile.distanceSquaredTo(target);
        double move_cost_0_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_5 = Double.MAX_VALUE;
        else
            move_cost_0_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -5);
        double cost_0_6 = tile.distanceSquaredTo(target);
        double move_cost_0_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_6 = Double.MAX_VALUE;
        else
            move_cost_0_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -5);
        double cost_0_7 = tile.distanceSquaredTo(target);
        double move_cost_0_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_7 = Double.MAX_VALUE;
        else
            move_cost_0_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -4);
        double cost_1_2 = tile.distanceSquaredTo(target);
        double move_cost_1_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_2 = Double.MAX_VALUE;
        else
            move_cost_1_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -4);
        double cost_1_3 = tile.distanceSquaredTo(target);
        double move_cost_1_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_3 = Double.MAX_VALUE;
        else
            move_cost_1_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -4);
        double cost_1_4 = tile.distanceSquaredTo(target);
        double move_cost_1_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_4 = Double.MAX_VALUE;
        else
            move_cost_1_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -4);
        double cost_1_5 = tile.distanceSquaredTo(target);
        double move_cost_1_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_5 = Double.MAX_VALUE;
        else
            move_cost_1_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -4);
        double cost_1_6 = tile.distanceSquaredTo(target);
        double move_cost_1_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_6 = Double.MAX_VALUE;
        else
            move_cost_1_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -4);
        double cost_1_7 = tile.distanceSquaredTo(target);
        double move_cost_1_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_7 = Double.MAX_VALUE;
        else
            move_cost_1_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -4);
        double cost_1_8 = tile.distanceSquaredTo(target);
        double move_cost_1_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_8 = Double.MAX_VALUE;
        else
            move_cost_1_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -3);
        double cost_2_1 = tile.distanceSquaredTo(target);
        double move_cost_2_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_1 = Double.MAX_VALUE;
        else
            move_cost_2_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -3);
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_2 = Double.MAX_VALUE;
        else
            move_cost_2_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -3);
        double cost_2_3 = tile.distanceSquaredTo(target);
        double move_cost_2_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_3 = Double.MAX_VALUE;
        else
            move_cost_2_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -3);
        double cost_2_4 = tile.distanceSquaredTo(target);
        double move_cost_2_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_4 = Double.MAX_VALUE;
        else
            move_cost_2_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -3);
        double cost_2_5 = tile.distanceSquaredTo(target);
        double move_cost_2_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_5 = Double.MAX_VALUE;
        else
            move_cost_2_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -3);
        double cost_2_6 = tile.distanceSquaredTo(target);
        double move_cost_2_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_6 = Double.MAX_VALUE;
        else
            move_cost_2_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -3);
        double cost_2_7 = tile.distanceSquaredTo(target);
        double move_cost_2_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_7 = Double.MAX_VALUE;
        else
            move_cost_2_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -3);
        double cost_2_8 = tile.distanceSquaredTo(target);
        double move_cost_2_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_8 = Double.MAX_VALUE;
        else
            move_cost_2_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -3);
        double cost_2_9 = tile.distanceSquaredTo(target);
        double move_cost_2_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_9 = Double.MAX_VALUE;
        else
            move_cost_2_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, -2);
        double cost_3_0 = tile.distanceSquaredTo(target);
        double move_cost_3_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_0 = Double.MAX_VALUE;
        else
            move_cost_3_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -2);
        double cost_3_1 = tile.distanceSquaredTo(target);
        double move_cost_3_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_1 = Double.MAX_VALUE;
        else
            move_cost_3_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -2);
        double cost_3_2 = tile.distanceSquaredTo(target);
        double move_cost_3_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_2 = Double.MAX_VALUE;
        else
            move_cost_3_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -2);
        double cost_3_3 = tile.distanceSquaredTo(target);
        double move_cost_3_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_3 = Double.MAX_VALUE;
        else
            move_cost_3_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -2);
        double cost_3_4 = tile.distanceSquaredTo(target);
        double move_cost_3_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_4 = Double.MAX_VALUE;
        else
            move_cost_3_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -2);
        double cost_3_5 = tile.distanceSquaredTo(target);
        double move_cost_3_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_5 = Double.MAX_VALUE;
        else
            move_cost_3_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -2);
        double cost_3_6 = tile.distanceSquaredTo(target);
        double move_cost_3_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_6 = Double.MAX_VALUE;
        else
            move_cost_3_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -2);
        double cost_3_7 = tile.distanceSquaredTo(target);
        double move_cost_3_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_7 = Double.MAX_VALUE;
        else
            move_cost_3_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -2);
        double cost_3_8 = tile.distanceSquaredTo(target);
        double move_cost_3_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_8 = Double.MAX_VALUE;
        else
            move_cost_3_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -2);
        double cost_3_9 = tile.distanceSquaredTo(target);
        double move_cost_3_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_9 = Double.MAX_VALUE;
        else
            move_cost_3_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, -2);
        double cost_3_10 = tile.distanceSquaredTo(target);
        double move_cost_3_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_10 = Double.MAX_VALUE;
        else
            move_cost_3_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, -1);
        double cost_4_0 = tile.distanceSquaredTo(target);
        double move_cost_4_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_0 = Double.MAX_VALUE;
        else
            move_cost_4_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, -1);
        double cost_4_1 = tile.distanceSquaredTo(target);
        double move_cost_4_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_1 = Double.MAX_VALUE;
        else
            move_cost_4_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -1);
        double cost_4_2 = tile.distanceSquaredTo(target);
        double move_cost_4_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_2 = Double.MAX_VALUE;
        else
            move_cost_4_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -1);
        double cost_4_3 = tile.distanceSquaredTo(target);
        double move_cost_4_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_3 = Double.MAX_VALUE;
        else
            move_cost_4_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -1);
        double cost_4_4 = tile.distanceSquaredTo(target);
        double move_cost_4_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_4 = Double.MAX_VALUE;
        else
            move_cost_4_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -1);
        double cost_4_5 = tile.distanceSquaredTo(target);
        double move_cost_4_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_5 = Double.MAX_VALUE;
        else
            move_cost_4_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -1);
        double cost_4_6 = tile.distanceSquaredTo(target);
        double move_cost_4_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_6 = Double.MAX_VALUE;
        else
            move_cost_4_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -1);
        double cost_4_7 = tile.distanceSquaredTo(target);
        double move_cost_4_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_7 = Double.MAX_VALUE;
        else
            move_cost_4_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -1);
        double cost_4_8 = tile.distanceSquaredTo(target);
        double move_cost_4_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_8 = Double.MAX_VALUE;
        else
            move_cost_4_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, -1);
        double cost_4_9 = tile.distanceSquaredTo(target);
        double move_cost_4_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_9 = Double.MAX_VALUE;
        else
            move_cost_4_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, -1);
        double cost_4_10 = tile.distanceSquaredTo(target);
        double move_cost_4_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_10 = Double.MAX_VALUE;
        else
            move_cost_4_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, 0);
        double cost_5_0 = tile.distanceSquaredTo(target);
        double move_cost_5_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_0 = Double.MAX_VALUE;
        else
            move_cost_5_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 0);
        double cost_5_1 = tile.distanceSquaredTo(target);
        double move_cost_5_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_1 = Double.MAX_VALUE;
        else
            move_cost_5_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 0);
        double cost_5_2 = tile.distanceSquaredTo(target);
        double move_cost_5_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_2 = Double.MAX_VALUE;
        else
            move_cost_5_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 0);
        double cost_5_3 = tile.distanceSquaredTo(target);
        double move_cost_5_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_3 = Double.MAX_VALUE;
        else
            move_cost_5_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 0);
        double cost_5_4 = tile.distanceSquaredTo(target);
        double move_cost_5_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_4 = Double.MAX_VALUE;
        else
            move_cost_5_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation();
        double cost_5_5 = tile.distanceSquaredTo(target);
        double move_cost_5_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 0);
        double cost_5_6 = tile.distanceSquaredTo(target);
        double move_cost_5_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_6 = Double.MAX_VALUE;
        else
            move_cost_5_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 0);
        double cost_5_7 = tile.distanceSquaredTo(target);
        double move_cost_5_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_7 = Double.MAX_VALUE;
        else
            move_cost_5_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 0);
        double cost_5_8 = tile.distanceSquaredTo(target);
        double move_cost_5_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_8 = Double.MAX_VALUE;
        else
            move_cost_5_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 0);
        double cost_5_9 = tile.distanceSquaredTo(target);
        double move_cost_5_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_9 = Double.MAX_VALUE;
        else
            move_cost_5_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, 0);
        double cost_5_10 = tile.distanceSquaredTo(target);
        double move_cost_5_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_10 = Double.MAX_VALUE;
        else
            move_cost_5_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, 1);
        double cost_6_0 = tile.distanceSquaredTo(target);
        double move_cost_6_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_0 = Double.MAX_VALUE;
        else
            move_cost_6_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 1);
        double cost_6_1 = tile.distanceSquaredTo(target);
        double move_cost_6_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_1 = Double.MAX_VALUE;
        else
            move_cost_6_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 1);
        double cost_6_2 = tile.distanceSquaredTo(target);
        double move_cost_6_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_2 = Double.MAX_VALUE;
        else
            move_cost_6_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 1);
        double cost_6_3 = tile.distanceSquaredTo(target);
        double move_cost_6_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_3 = Double.MAX_VALUE;
        else
            move_cost_6_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 1);
        double cost_6_4 = tile.distanceSquaredTo(target);
        double move_cost_6_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_4 = Double.MAX_VALUE;
        else
            move_cost_6_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 1);
        double cost_6_5 = tile.distanceSquaredTo(target);
        double move_cost_6_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_5 = Double.MAX_VALUE;
        else
            move_cost_6_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 1);
        double cost_6_6 = tile.distanceSquaredTo(target);
        double move_cost_6_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_6 = Double.MAX_VALUE;
        else
            move_cost_6_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 1);
        double cost_6_7 = tile.distanceSquaredTo(target);
        double move_cost_6_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_7 = Double.MAX_VALUE;
        else
            move_cost_6_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 1);
        double cost_6_8 = tile.distanceSquaredTo(target);
        double move_cost_6_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_8 = Double.MAX_VALUE;
        else
            move_cost_6_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 1);
        double cost_6_9 = tile.distanceSquaredTo(target);
        double move_cost_6_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_9 = Double.MAX_VALUE;
        else
            move_cost_6_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, 1);
        double cost_6_10 = tile.distanceSquaredTo(target);
        double move_cost_6_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_10 = Double.MAX_VALUE;
        else
            move_cost_6_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-5, 2);
        double cost_7_0 = tile.distanceSquaredTo(target);
        double move_cost_7_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_0 = Double.MAX_VALUE;
        else
            move_cost_7_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 2);
        double cost_7_1 = tile.distanceSquaredTo(target);
        double move_cost_7_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_1 = Double.MAX_VALUE;
        else
            move_cost_7_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 2);
        double cost_7_2 = tile.distanceSquaredTo(target);
        double move_cost_7_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_2 = Double.MAX_VALUE;
        else
            move_cost_7_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 2);
        double cost_7_3 = tile.distanceSquaredTo(target);
        double move_cost_7_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_3 = Double.MAX_VALUE;
        else
            move_cost_7_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 2);
        double cost_7_4 = tile.distanceSquaredTo(target);
        double move_cost_7_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_4 = Double.MAX_VALUE;
        else
            move_cost_7_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 2);
        double cost_7_5 = tile.distanceSquaredTo(target);
        double move_cost_7_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_5 = Double.MAX_VALUE;
        else
            move_cost_7_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 2);
        double cost_7_6 = tile.distanceSquaredTo(target);
        double move_cost_7_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_6 = Double.MAX_VALUE;
        else
            move_cost_7_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 2);
        double cost_7_7 = tile.distanceSquaredTo(target);
        double move_cost_7_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_7 = Double.MAX_VALUE;
        else
            move_cost_7_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 2);
        double cost_7_8 = tile.distanceSquaredTo(target);
        double move_cost_7_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_8 = Double.MAX_VALUE;
        else
            move_cost_7_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 2);
        double cost_7_9 = tile.distanceSquaredTo(target);
        double move_cost_7_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_9 = Double.MAX_VALUE;
        else
            move_cost_7_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(5, 2);
        double cost_7_10 = tile.distanceSquaredTo(target);
        double move_cost_7_10 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_7_10 = Double.MAX_VALUE;
        else
            move_cost_7_10 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-4, 3);
        double cost_8_1 = tile.distanceSquaredTo(target);
        double move_cost_8_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_1 = Double.MAX_VALUE;
        else
            move_cost_8_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 3);
        double cost_8_2 = tile.distanceSquaredTo(target);
        double move_cost_8_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_2 = Double.MAX_VALUE;
        else
            move_cost_8_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 3);
        double cost_8_3 = tile.distanceSquaredTo(target);
        double move_cost_8_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_3 = Double.MAX_VALUE;
        else
            move_cost_8_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 3);
        double cost_8_4 = tile.distanceSquaredTo(target);
        double move_cost_8_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_4 = Double.MAX_VALUE;
        else
            move_cost_8_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 3);
        double cost_8_5 = tile.distanceSquaredTo(target);
        double move_cost_8_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_5 = Double.MAX_VALUE;
        else
            move_cost_8_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 3);
        double cost_8_6 = tile.distanceSquaredTo(target);
        double move_cost_8_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_6 = Double.MAX_VALUE;
        else
            move_cost_8_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 3);
        double cost_8_7 = tile.distanceSquaredTo(target);
        double move_cost_8_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_7 = Double.MAX_VALUE;
        else
            move_cost_8_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 3);
        double cost_8_8 = tile.distanceSquaredTo(target);
        double move_cost_8_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_8 = Double.MAX_VALUE;
        else
            move_cost_8_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(4, 3);
        double cost_8_9 = tile.distanceSquaredTo(target);
        double move_cost_8_9 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_8_9 = Double.MAX_VALUE;
        else
            move_cost_8_9 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 4);
        double cost_9_2 = tile.distanceSquaredTo(target);
        double move_cost_9_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_2 = Double.MAX_VALUE;
        else
            move_cost_9_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 4);
        double cost_9_3 = tile.distanceSquaredTo(target);
        double move_cost_9_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_3 = Double.MAX_VALUE;
        else
            move_cost_9_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 4);
        double cost_9_4 = tile.distanceSquaredTo(target);
        double move_cost_9_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_4 = Double.MAX_VALUE;
        else
            move_cost_9_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 4);
        double cost_9_5 = tile.distanceSquaredTo(target);
        double move_cost_9_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_5 = Double.MAX_VALUE;
        else
            move_cost_9_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 4);
        double cost_9_6 = tile.distanceSquaredTo(target);
        double move_cost_9_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_6 = Double.MAX_VALUE;
        else
            move_cost_9_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 4);
        double cost_9_7 = tile.distanceSquaredTo(target);
        double move_cost_9_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_7 = Double.MAX_VALUE;
        else
            move_cost_9_7 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 4);
        double cost_9_8 = tile.distanceSquaredTo(target);
        double move_cost_9_8 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_9_8 = Double.MAX_VALUE;
        else
            move_cost_9_8 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 5);
        double cost_10_3 = tile.distanceSquaredTo(target);
        double move_cost_10_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_3 = Double.MAX_VALUE;
        else
            move_cost_10_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 5);
        double cost_10_4 = tile.distanceSquaredTo(target);
        double move_cost_10_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_4 = Double.MAX_VALUE;
        else
            move_cost_10_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 5);
        double cost_10_5 = tile.distanceSquaredTo(target);
        double move_cost_10_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_5 = Double.MAX_VALUE;
        else
            move_cost_10_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 5);
        double cost_10_6 = tile.distanceSquaredTo(target);
        double move_cost_10_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_6 = Double.MAX_VALUE;
        else
            move_cost_10_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 5);
        double cost_10_7 = tile.distanceSquaredTo(target);
        double move_cost_10_7 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_10_7 = Double.MAX_VALUE;
        else
            move_cost_10_7 = 1 / rc_.sensePassability(tile);
        // iteration 1
        cost_0_3 = Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3)))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_0_7, cost_0_6 - move_cost_0_6))))) + move_cost_0_6;
        cost_0_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_1_8, cost_0_7 - move_cost_0_7)))) + move_cost_0_7;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_0_7, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8))))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_3_10, cost_2_9 - move_cost_2_9))))) + move_cost_2_9;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0)))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_4_10, Math.min(cost_3_10, cost_3_9 - move_cost_3_9))))))) + move_cost_3_9;
        cost_3_10 = Math.min(cost_2_9, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_4_10, cost_3_10 - move_cost_3_10)))) + move_cost_3_10;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, Math.min(cost_4_10, cost_4_9 - move_cost_4_9)))))))) + move_cost_4_9;
        cost_4_10 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_10 - move_cost_4_10))))) + move_cost_4_10;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_6_10, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_6_10, cost_5_10 - move_cost_5_10))))) + move_cost_5_10;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0))))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_7_10, Math.min(cost_6_10, cost_6_9 - move_cost_6_9)))))))) + move_cost_6_9;
        cost_6_10 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_7_10, cost_6_10 - move_cost_6_10))))) + move_cost_6_10;
        cost_7_0 = Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_1, Math.min(cost_7_1, cost_7_0 - move_cost_7_0)))) + move_cost_7_0;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_10, cost_7_9 - move_cost_7_9))))))) + move_cost_7_9;
        cost_7_10 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_8_9, cost_7_10 - move_cost_7_10)))) + move_cost_7_10;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_7_0, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1))))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_10, Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9))))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_10_3, Math.min(cost_9_3, cost_9_2 - move_cost_9_2))))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_10_7, cost_9_8 - move_cost_9_8))))) + move_cost_9_8;
        cost_10_3 = Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_9_2, Math.min(cost_10_4, cost_10_3 - move_cost_10_3)))) + move_cost_10_3;
        cost_10_4 = Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_5, cost_10_4 - move_cost_10_4))))) + move_cost_10_4;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_6, cost_10_5 - move_cost_10_5))))) + move_cost_10_5;
        cost_10_6 = Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_7, cost_10_6 - move_cost_10_6))))) + move_cost_10_6;
        cost_10_7 = Math.min(cost_9_8, Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_10_6, cost_10_7 - move_cost_10_7)))) + move_cost_10_7;

        // iteration 2
        cost_0_3 = Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3)))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_0_7, cost_0_6 - move_cost_0_6))))) + move_cost_0_6;
        cost_0_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_1_8, cost_0_7 - move_cost_0_7)))) + move_cost_0_7;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_0_7, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8))))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_3_10, cost_2_9 - move_cost_2_9))))) + move_cost_2_9;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0)))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_4_10, Math.min(cost_3_10, cost_3_9 - move_cost_3_9))))))) + move_cost_3_9;
        cost_3_10 = Math.min(cost_2_9, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_4_10, cost_3_10 - move_cost_3_10)))) + move_cost_3_10;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, Math.min(cost_4_10, cost_4_9 - move_cost_4_9)))))))) + move_cost_4_9;
        cost_4_10 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_10 - move_cost_4_10))))) + move_cost_4_10;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_6_10, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_6_10, cost_5_10 - move_cost_5_10))))) + move_cost_5_10;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0))))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_7_10, Math.min(cost_6_10, cost_6_9 - move_cost_6_9)))))))) + move_cost_6_9;
        cost_6_10 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_7_10, cost_6_10 - move_cost_6_10))))) + move_cost_6_10;
        cost_7_0 = Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_1, Math.min(cost_7_1, cost_7_0 - move_cost_7_0)))) + move_cost_7_0;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_10, cost_7_9 - move_cost_7_9))))))) + move_cost_7_9;
        cost_7_10 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_8_9, cost_7_10 - move_cost_7_10)))) + move_cost_7_10;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_7_0, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1))))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_10, Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9))))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_10_3, Math.min(cost_9_3, cost_9_2 - move_cost_9_2))))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_10_7, cost_9_8 - move_cost_9_8))))) + move_cost_9_8;
        cost_10_3 = Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_9_2, Math.min(cost_10_4, cost_10_3 - move_cost_10_3)))) + move_cost_10_3;
        cost_10_4 = Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_5, cost_10_4 - move_cost_10_4))))) + move_cost_10_4;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_6, cost_10_5 - move_cost_10_5))))) + move_cost_10_5;
        cost_10_6 = Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_7, cost_10_6 - move_cost_10_6))))) + move_cost_10_6;
        cost_10_7 = Math.min(cost_9_8, Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_10_6, cost_10_7 - move_cost_10_7)))) + move_cost_10_7;

        // iteration 3
        cost_0_3 = Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3)))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_0_7, cost_0_6 - move_cost_0_6))))) + move_cost_0_6;
        cost_0_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_1_8, cost_0_7 - move_cost_0_7)))) + move_cost_0_7;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_0_7, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8))))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_3_10, cost_2_9 - move_cost_2_9))))) + move_cost_2_9;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0)))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_4_10, Math.min(cost_3_10, cost_3_9 - move_cost_3_9))))))) + move_cost_3_9;
        cost_3_10 = Math.min(cost_2_9, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_4_10, cost_3_10 - move_cost_3_10)))) + move_cost_3_10;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, Math.min(cost_4_10, cost_4_9 - move_cost_4_9)))))))) + move_cost_4_9;
        cost_4_10 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_10 - move_cost_4_10))))) + move_cost_4_10;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_6_10, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_6_10, cost_5_10 - move_cost_5_10))))) + move_cost_5_10;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0))))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_7_10, Math.min(cost_6_10, cost_6_9 - move_cost_6_9)))))))) + move_cost_6_9;
        cost_6_10 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_7_10, cost_6_10 - move_cost_6_10))))) + move_cost_6_10;
        cost_7_0 = Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_1, Math.min(cost_7_1, cost_7_0 - move_cost_7_0)))) + move_cost_7_0;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_10, cost_7_9 - move_cost_7_9))))))) + move_cost_7_9;
        cost_7_10 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_8_9, cost_7_10 - move_cost_7_10)))) + move_cost_7_10;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_7_0, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1))))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_10, Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9))))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_10_3, Math.min(cost_9_3, cost_9_2 - move_cost_9_2))))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_10_7, cost_9_8 - move_cost_9_8))))) + move_cost_9_8;
        cost_10_3 = Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_9_2, Math.min(cost_10_4, cost_10_3 - move_cost_10_3)))) + move_cost_10_3;
        cost_10_4 = Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_5, cost_10_4 - move_cost_10_4))))) + move_cost_10_4;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_6, cost_10_5 - move_cost_10_5))))) + move_cost_10_5;
        cost_10_6 = Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_7, cost_10_6 - move_cost_10_6))))) + move_cost_10_6;
        cost_10_7 = Math.min(cost_9_8, Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_10_6, cost_10_7 - move_cost_10_7)))) + move_cost_10_7;

        // iteration 4
        cost_0_3 = Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3)))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_0_7, cost_0_6 - move_cost_0_6))))) + move_cost_0_6;
        cost_0_7 = Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_1_7, Math.min(cost_1_8, cost_0_7 - move_cost_0_7)))) + move_cost_0_7;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_1_7, cost_1_6 - move_cost_1_6)))))))) + move_cost_1_6;
        cost_1_7 = Math.min(cost_0_7, Math.min(cost_0_6, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_1_8, cost_1_7 - move_cost_1_7))))))) + move_cost_1_7;
        cost_1_8 = Math.min(cost_0_7, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_2_8, Math.min(cost_2_9, cost_1_8 - move_cost_1_8))))) + move_cost_1_8;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_2_7, cost_2_6 - move_cost_2_6)))))))) + move_cost_2_6;
        cost_2_7 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_1_6, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_2_8, cost_2_7 - move_cost_2_7)))))))) + move_cost_2_7;
        cost_2_8 = Math.min(cost_1_8, Math.min(cost_1_7, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_2_9, cost_2_8 - move_cost_2_8))))))) + move_cost_2_8;
        cost_2_9 = Math.min(cost_1_8, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_3_9, Math.min(cost_3_10, cost_2_9 - move_cost_2_9))))) + move_cost_2_9;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0)))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_3_7, cost_3_6 - move_cost_3_6)))))))) + move_cost_3_6;
        cost_3_7 = Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_2_6, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_3_8, cost_3_7 - move_cost_3_7)))))))) + move_cost_3_7;
        cost_3_8 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_2_7, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_3_9, cost_3_8 - move_cost_3_8)))))))) + move_cost_3_8;
        cost_3_9 = Math.min(cost_2_9, Math.min(cost_2_8, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_4_9, Math.min(cost_4_10, Math.min(cost_3_10, cost_3_9 - move_cost_3_9))))))) + move_cost_3_9;
        cost_3_10 = Math.min(cost_2_9, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_4_10, cost_3_10 - move_cost_3_10)))) + move_cost_3_10;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_4_7, cost_4_6 - move_cost_4_6)))))))) + move_cost_4_6;
        cost_4_7 = Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_3_6, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_4_8, cost_4_7 - move_cost_4_7)))))))) + move_cost_4_7;
        cost_4_8 = Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_3_7, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_4_9, cost_4_8 - move_cost_4_8)))))))) + move_cost_4_8;
        cost_4_9 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_3_8, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_5_9, Math.min(cost_5_10, Math.min(cost_4_10, cost_4_9 - move_cost_4_9)))))))) + move_cost_4_9;
        cost_4_10 = Math.min(cost_3_10, Math.min(cost_3_9, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_5_10, cost_4_10 - move_cost_4_10))))) + move_cost_4_10;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_5_7, cost_5_6 - move_cost_5_6)))))))) + move_cost_5_6;
        cost_5_7 = Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_4_6, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_5_8, cost_5_7 - move_cost_5_7)))))))) + move_cost_5_7;
        cost_5_8 = Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_4_7, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_5_9, cost_5_8 - move_cost_5_8)))))))) + move_cost_5_8;
        cost_5_9 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_4_8, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_6_9, Math.min(cost_6_10, Math.min(cost_5_10, cost_5_9 - move_cost_5_9)))))))) + move_cost_5_9;
        cost_5_10 = Math.min(cost_4_10, Math.min(cost_4_9, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_6_10, cost_5_10 - move_cost_5_10))))) + move_cost_5_10;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_6_1, cost_6_0 - move_cost_6_0))))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_6_2, cost_6_1 - move_cost_6_1)))))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_6_3, cost_6_2 - move_cost_6_2)))))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_6_4, cost_6_3 - move_cost_6_3)))))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_6_5, cost_6_4 - move_cost_6_4)))))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_6_6, cost_6_5 - move_cost_6_5)))))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_6_7, cost_6_6 - move_cost_6_6)))))))) + move_cost_6_6;
        cost_6_7 = Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_5_6, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_6_8, cost_6_7 - move_cost_6_7)))))))) + move_cost_6_7;
        cost_6_8 = Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_5_7, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_6_9, cost_6_8 - move_cost_6_8)))))))) + move_cost_6_8;
        cost_6_9 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_5_8, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_7_9, Math.min(cost_7_10, Math.min(cost_6_10, cost_6_9 - move_cost_6_9)))))))) + move_cost_6_9;
        cost_6_10 = Math.min(cost_5_10, Math.min(cost_5_9, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_7_10, cost_6_10 - move_cost_6_10))))) + move_cost_6_10;
        cost_7_0 = Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_8_1, Math.min(cost_7_1, cost_7_0 - move_cost_7_0)))) + move_cost_7_0;
        cost_7_1 = Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_6_0, Math.min(cost_7_0, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_7_2, cost_7_1 - move_cost_7_1))))))) + move_cost_7_1;
        cost_7_2 = Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_6_1, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_7_3, cost_7_2 - move_cost_7_2)))))))) + move_cost_7_2;
        cost_7_3 = Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_6_2, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_7_4, cost_7_3 - move_cost_7_3)))))))) + move_cost_7_3;
        cost_7_4 = Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_6_3, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_7_5, cost_7_4 - move_cost_7_4)))))))) + move_cost_7_4;
        cost_7_5 = Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_6_4, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_7_6, cost_7_5 - move_cost_7_5)))))))) + move_cost_7_5;
        cost_7_6 = Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_6_5, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_7_7, cost_7_6 - move_cost_7_6)))))))) + move_cost_7_6;
        cost_7_7 = Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_6_6, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_7_8, cost_7_7 - move_cost_7_7)))))))) + move_cost_7_7;
        cost_7_8 = Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_6_7, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_9, cost_7_8 - move_cost_7_8)))))))) + move_cost_7_8;
        cost_7_9 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_6_8, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_8_9, Math.min(cost_7_10, cost_7_9 - move_cost_7_9))))))) + move_cost_7_9;
        cost_7_10 = Math.min(cost_6_10, Math.min(cost_6_9, Math.min(cost_7_9, Math.min(cost_8_9, cost_7_10 - move_cost_7_10)))) + move_cost_7_10;
        cost_8_1 = Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_7_0, Math.min(cost_9_2, Math.min(cost_8_2, cost_8_1 - move_cost_8_1))))) + move_cost_8_1;
        cost_8_2 = Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_7_1, Math.min(cost_8_1, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_8_3, cost_8_2 - move_cost_8_2))))))) + move_cost_8_2;
        cost_8_3 = Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_7_2, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_8_4, cost_8_3 - move_cost_8_3)))))))) + move_cost_8_3;
        cost_8_4 = Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_7_3, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_8_5, cost_8_4 - move_cost_8_4)))))))) + move_cost_8_4;
        cost_8_5 = Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_7_4, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_8_6, cost_8_5 - move_cost_8_5)))))))) + move_cost_8_5;
        cost_8_6 = Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_7_5, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_8_7, cost_8_6 - move_cost_8_6)))))))) + move_cost_8_6;
        cost_8_7 = Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_7_6, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_8, cost_8_7 - move_cost_8_7)))))))) + move_cost_8_7;
        cost_8_8 = Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_7_7, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_9_8, Math.min(cost_8_9, cost_8_8 - move_cost_8_8))))))) + move_cost_8_8;
        cost_8_9 = Math.min(cost_7_10, Math.min(cost_7_9, Math.min(cost_7_8, Math.min(cost_8_8, Math.min(cost_9_8, cost_8_9 - move_cost_8_9))))) + move_cost_8_9;
        cost_9_2 = Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_8_1, Math.min(cost_10_3, Math.min(cost_9_3, cost_9_2 - move_cost_9_2))))) + move_cost_9_2;
        cost_9_3 = Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_8_2, Math.min(cost_9_2, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_9_4, cost_9_3 - move_cost_9_3))))))) + move_cost_9_3;
        cost_9_4 = Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_8_3, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_9_5, cost_9_4 - move_cost_9_4)))))))) + move_cost_9_4;
        cost_9_5 = Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_8_4, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_9_6, cost_9_5 - move_cost_9_5)))))))) + move_cost_9_5;
        cost_9_6 = Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_8_5, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_7, cost_9_6 - move_cost_9_6)))))))) + move_cost_9_6;
        cost_9_7 = Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_8_6, Math.min(cost_9_6, Math.min(cost_10_6, Math.min(cost_10_7, Math.min(cost_9_8, cost_9_7 - move_cost_9_7))))))) + move_cost_9_7;
        cost_9_8 = Math.min(cost_8_9, Math.min(cost_8_8, Math.min(cost_8_7, Math.min(cost_9_7, Math.min(cost_10_7, cost_9_8 - move_cost_9_8))))) + move_cost_9_8;
        cost_10_3 = Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_9_2, Math.min(cost_10_4, cost_10_3 - move_cost_10_3)))) + move_cost_10_3;
        cost_10_4 = Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_9_3, Math.min(cost_10_3, Math.min(cost_10_5, cost_10_4 - move_cost_10_4))))) + move_cost_10_4;
        cost_10_5 = Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_9_4, Math.min(cost_10_4, Math.min(cost_10_6, cost_10_5 - move_cost_10_5))))) + move_cost_10_5;
        cost_10_6 = Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_9_5, Math.min(cost_10_5, Math.min(cost_10_7, cost_10_6 - move_cost_10_6))))) + move_cost_10_6;
        cost_10_7 = Math.min(cost_9_8, Math.min(cost_9_7, Math.min(cost_9_6, Math.min(cost_10_6, cost_10_7 - move_cost_10_7)))) + move_cost_10_7;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = cost_5_5;

        if (cost_5_6 < minCost && (danger & 4) == 0) {
            minCost = cost_5_6;ret = Direction.EAST;
        }
        if (cost_6_6 < minCost && (danger & 2) == 0) {
            minCost = cost_6_6;ret = Direction.NORTHEAST;
        }
        if (cost_6_5 < minCost && (danger & 1) == 0) {
            minCost = cost_6_5;ret = Direction.NORTH;
        }
        if (cost_6_4 < minCost && (danger & 128) == 0) {
            minCost = cost_6_4;ret = Direction.NORTHWEST;
        }
        if (cost_5_4 < minCost && (danger & 64) == 0) {
            minCost = cost_5_4;ret = Direction.WEST;
        }
        if (cost_4_4 < minCost && (danger & 32) == 0) {
            minCost = cost_4_4;ret = Direction.SOUTHWEST;
        }
        if (cost_4_5 < minCost && (danger & 16) == 0) {
            minCost = cost_4_5;ret = Direction.SOUTH;
        }
        if (cost_4_6 < minCost && (danger & 8) == 0) {
            ret = Direction.SOUTHEAST;
        }
        return ret;
    }
}