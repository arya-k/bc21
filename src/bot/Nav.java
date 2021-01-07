package bot;

import battlecode.common.*;

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
 * Every round, if you want to make a move along a navigational goal, call Nav.tick(). Otherwise,
 * call Nav.update().
 */
public class Nav {
    // Constants:
    static final int FAILURE_TURNS = 5;

    // Variables:
    private static RobotController rc;
    private static int distToGoal = 0;
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
    private static Direction goalDir = null; // associated with GoInDir
    private static int goalID = -1; // associated with follow

    /**
     * Call when a robot is created to use the navigation module
     *
     * @param rc RobotController.
     */
    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    /**
     * Sets the robot to explore mode. Will try to visit each unexplored chunk
     * in the map, noting walls as it gets to them.
     * #unimplementedGang
     */
    public static void doExplore() {
        currentGoal = NavGoal.Explore; // TODO: this

        distToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Replaces setGoal. Tells the robot to head to the given tile. Robot will
     * stop this goal if it fails to make progress for FAILURE_TURNS turns, realizes the spot is off the map or occupied,
     * or reaches the goal tile.
     */
    public static void doGoTo(MapLocation target) {
        currentGoal = NavGoal.GoTo;
        goalPos = target;

        distToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Tells the robot to head in the given direction. Robot will stop this goal if it fails to make
     * progress for FAILURE_TURNS turns, or it realizes it has reached the end of the map.
     */
    public static void doGoInDir(Direction dir) {
        currentGoal = NavGoal.GoInDir;
        goalDir = dir;

        distToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Robot attempts to follow around the robot with the given ID. will stop this goal if it can no longer
     * see the robot in range.
     * #unimplementedGang
     */
    public static void doFollow(int targetID) {
        // TODO: support a memory so it can try and find the robot even if it has gone out of sight!
        currentGoal = NavGoal.Follow; // TODO: this
        goalID = targetID;

        distToGoal = Integer.MAX_VALUE;
        turnsSinceImprovement = 0;
    }

    /**
     * Calls nav utilities, planning for exploring the world around by noting visited chunks and edges found.
     * CALL EITHER THIS, OR TICK, EVERY TURN.
     */
    public static void update() {
        // TODO: this!
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
        // TODO: higher level planning
        // TODO: check if we can see that the target is off the map

        update();

        switch (currentGoal) {
            case Nothing:
                return null;
            case GoTo:
                // Cancel goal because of lack of improvement
                int newDistToGoal = rc.getLocation().distanceSquaredTo(goalPos);
                if (newDistToGoal >= distToGoal) {
                    turnsSinceImprovement++;
                } else {
                    turnsSinceImprovement = 0;
                    distToGoal = newDistToGoal;
                }

                if (turnsSinceImprovement >= FAILURE_TURNS) {
                    currentGoal = NavGoal.Nothing;
                }

                // Cancel because we have reached goal:
                if (rc.getLocation() == goalPos) {
                    System.out.println("@@@Cancelling nav goal because we have reached our destination");
                    currentGoal = NavGoal.Nothing;
                }

                // Cancel because we are adjacent and it is impossible?

                return currentGoal == NavGoal.Nothing ? null : goTo(goalPos);

            case GoInDir:
                if (!rc.onTheMap(rc.getLocation().add(goalDir))) return null;
                return goTo(rc.getLocation().translate(5 * goalDir.dx, 5 * goalDir.dy));
            case Follow:
            case Explore:
                throw new GameActionException(GameActionExceptionType.INTERNAL_ERROR, "Unimplemented!");
        }
        return null; // should never get here!
    }

    /**
     * Picks the best goTo to use, based on the number of cooldown turns available.
     *
     * @param target the mapLocation to head towards.
     * @return the best direction to head in
     * @throws GameActionException hopefully never.
     */
    private static Direction goTo(MapLocation target) throws GameActionException {
        // TODO: try more complex computation if we have more of a cool down?

//        int startRound = rc.getRoundNum(); // TODO: remove this eventually...
//        int startBC = Clock.getBytecodeNum();

        Direction dir = goTo7(target);

//        Robot.logBytecodeUse(startRound, startBC);

        return dir;
    }


    private static Direction goTo5(MapLocation target) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params NAV_GRID_SIZE=5, NAV_ITERATIONS=2 */

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
        tile = rc_.getLocation().translate(0, 0);
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_2 = Double.MAX_VALUE;
        else
            move_cost_2_2 = 1 / rc_.sensePassability(tile);
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
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1)))))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = Double.MAX_VALUE;

        if (cost_2_3 < minCost) {
            minCost = cost_2_3;
            ret = Direction.EAST;
        }
        if (cost_3_3 < minCost) {
            minCost = cost_3_3;
            ret = Direction.NORTHEAST;
        }
        if (cost_3_2 < minCost) {
            minCost = cost_3_2;
            ret = Direction.NORTH;
        }
        if (cost_3_1 < minCost) {
            minCost = cost_3_1;
            ret = Direction.NORTHWEST;
        }
        if (cost_2_1 < minCost) {
            minCost = cost_2_1;
            ret = Direction.WEST;
        }
        if (cost_1_1 < minCost) {
            minCost = cost_1_1;
            ret = Direction.SOUTHWEST;
        }
        if (cost_1_2 < minCost) {
            minCost = cost_1_2;
            ret = Direction.SOUTH;
        }
        if (cost_1_3 < minCost) {
            ret = Direction.SOUTHEAST;
        }
        return minCost > 1073741824 ? Direction.CENTER : ret;
    }


    private static Direction goTo7(MapLocation target) throws GameActionException {
        /* AUTOGENERATED with `nav.py`, with params NAV_GRID_SIZE=7, NAV_ITERATIONS=3 */

        RobotController rc_ = rc; // move into local scope

        // POPULATE COSTS AND MOVEMENT COSTS
        MapLocation tile = rc_.getLocation().translate(-3, -3);
        double cost_0_0 = tile.distanceSquaredTo(target);
        double move_cost_0_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_0 = Double.MAX_VALUE;
        else
            move_cost_0_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -3);
        double cost_0_1 = tile.distanceSquaredTo(target);
        double move_cost_0_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_1 = Double.MAX_VALUE;
        else
            move_cost_0_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -3);
        double cost_0_2 = tile.distanceSquaredTo(target);
        double move_cost_0_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_2 = Double.MAX_VALUE;
        else
            move_cost_0_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -3);
        double cost_0_3 = tile.distanceSquaredTo(target);
        double move_cost_0_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_3 = Double.MAX_VALUE;
        else
            move_cost_0_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -3);
        double cost_0_4 = tile.distanceSquaredTo(target);
        double move_cost_0_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_4 = Double.MAX_VALUE;
        else
            move_cost_0_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -3);
        double cost_0_5 = tile.distanceSquaredTo(target);
        double move_cost_0_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_5 = Double.MAX_VALUE;
        else
            move_cost_0_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -3);
        double cost_0_6 = tile.distanceSquaredTo(target);
        double move_cost_0_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_0_6 = Double.MAX_VALUE;
        else
            move_cost_0_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -2);
        double cost_1_0 = tile.distanceSquaredTo(target);
        double move_cost_1_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_0 = Double.MAX_VALUE;
        else
            move_cost_1_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -2);
        double cost_1_1 = tile.distanceSquaredTo(target);
        double move_cost_1_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_1 = Double.MAX_VALUE;
        else
            move_cost_1_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -2);
        double cost_1_2 = tile.distanceSquaredTo(target);
        double move_cost_1_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_2 = Double.MAX_VALUE;
        else
            move_cost_1_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -2);
        double cost_1_3 = tile.distanceSquaredTo(target);
        double move_cost_1_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_3 = Double.MAX_VALUE;
        else
            move_cost_1_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -2);
        double cost_1_4 = tile.distanceSquaredTo(target);
        double move_cost_1_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_4 = Double.MAX_VALUE;
        else
            move_cost_1_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -2);
        double cost_1_5 = tile.distanceSquaredTo(target);
        double move_cost_1_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_5 = Double.MAX_VALUE;
        else
            move_cost_1_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -2);
        double cost_1_6 = tile.distanceSquaredTo(target);
        double move_cost_1_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_1_6 = Double.MAX_VALUE;
        else
            move_cost_1_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, -1);
        double cost_2_0 = tile.distanceSquaredTo(target);
        double move_cost_2_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_0 = Double.MAX_VALUE;
        else
            move_cost_2_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, -1);
        double cost_2_1 = tile.distanceSquaredTo(target);
        double move_cost_2_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_1 = Double.MAX_VALUE;
        else
            move_cost_2_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, -1);
        double cost_2_2 = tile.distanceSquaredTo(target);
        double move_cost_2_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_2 = Double.MAX_VALUE;
        else
            move_cost_2_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, -1);
        double cost_2_3 = tile.distanceSquaredTo(target);
        double move_cost_2_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_3 = Double.MAX_VALUE;
        else
            move_cost_2_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, -1);
        double cost_2_4 = tile.distanceSquaredTo(target);
        double move_cost_2_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_4 = Double.MAX_VALUE;
        else
            move_cost_2_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, -1);
        double cost_2_5 = tile.distanceSquaredTo(target);
        double move_cost_2_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_5 = Double.MAX_VALUE;
        else
            move_cost_2_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, -1);
        double cost_2_6 = tile.distanceSquaredTo(target);
        double move_cost_2_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_2_6 = Double.MAX_VALUE;
        else
            move_cost_2_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 0);
        double cost_3_0 = tile.distanceSquaredTo(target);
        double move_cost_3_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_0 = Double.MAX_VALUE;
        else
            move_cost_3_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 0);
        double cost_3_1 = tile.distanceSquaredTo(target);
        double move_cost_3_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_1 = Double.MAX_VALUE;
        else
            move_cost_3_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 0);
        double cost_3_2 = tile.distanceSquaredTo(target);
        double move_cost_3_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_2 = Double.MAX_VALUE;
        else
            move_cost_3_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 0);
        double cost_3_3 = tile.distanceSquaredTo(target);
        double move_cost_3_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_3 = Double.MAX_VALUE;
        else
            move_cost_3_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 0);
        double cost_3_4 = tile.distanceSquaredTo(target);
        double move_cost_3_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_4 = Double.MAX_VALUE;
        else
            move_cost_3_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 0);
        double cost_3_5 = tile.distanceSquaredTo(target);
        double move_cost_3_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_5 = Double.MAX_VALUE;
        else
            move_cost_3_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 0);
        double cost_3_6 = tile.distanceSquaredTo(target);
        double move_cost_3_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_3_6 = Double.MAX_VALUE;
        else
            move_cost_3_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 1);
        double cost_4_0 = tile.distanceSquaredTo(target);
        double move_cost_4_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_0 = Double.MAX_VALUE;
        else
            move_cost_4_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 1);
        double cost_4_1 = tile.distanceSquaredTo(target);
        double move_cost_4_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_1 = Double.MAX_VALUE;
        else
            move_cost_4_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 1);
        double cost_4_2 = tile.distanceSquaredTo(target);
        double move_cost_4_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_2 = Double.MAX_VALUE;
        else
            move_cost_4_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 1);
        double cost_4_3 = tile.distanceSquaredTo(target);
        double move_cost_4_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_3 = Double.MAX_VALUE;
        else
            move_cost_4_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 1);
        double cost_4_4 = tile.distanceSquaredTo(target);
        double move_cost_4_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_4 = Double.MAX_VALUE;
        else
            move_cost_4_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 1);
        double cost_4_5 = tile.distanceSquaredTo(target);
        double move_cost_4_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_5 = Double.MAX_VALUE;
        else
            move_cost_4_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 1);
        double cost_4_6 = tile.distanceSquaredTo(target);
        double move_cost_4_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_4_6 = Double.MAX_VALUE;
        else
            move_cost_4_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 2);
        double cost_5_0 = tile.distanceSquaredTo(target);
        double move_cost_5_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_0 = Double.MAX_VALUE;
        else
            move_cost_5_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 2);
        double cost_5_1 = tile.distanceSquaredTo(target);
        double move_cost_5_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_1 = Double.MAX_VALUE;
        else
            move_cost_5_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 2);
        double cost_5_2 = tile.distanceSquaredTo(target);
        double move_cost_5_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_2 = Double.MAX_VALUE;
        else
            move_cost_5_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 2);
        double cost_5_3 = tile.distanceSquaredTo(target);
        double move_cost_5_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_3 = Double.MAX_VALUE;
        else
            move_cost_5_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 2);
        double cost_5_4 = tile.distanceSquaredTo(target);
        double move_cost_5_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_4 = Double.MAX_VALUE;
        else
            move_cost_5_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 2);
        double cost_5_5 = tile.distanceSquaredTo(target);
        double move_cost_5_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_5 = Double.MAX_VALUE;
        else
            move_cost_5_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 2);
        double cost_5_6 = tile.distanceSquaredTo(target);
        double move_cost_5_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_5_6 = Double.MAX_VALUE;
        else
            move_cost_5_6 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-3, 3);
        double cost_6_0 = tile.distanceSquaredTo(target);
        double move_cost_6_0 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_0 = Double.MAX_VALUE;
        else
            move_cost_6_0 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-2, 3);
        double cost_6_1 = tile.distanceSquaredTo(target);
        double move_cost_6_1 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_1 = Double.MAX_VALUE;
        else
            move_cost_6_1 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(-1, 3);
        double cost_6_2 = tile.distanceSquaredTo(target);
        double move_cost_6_2 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_2 = Double.MAX_VALUE;
        else
            move_cost_6_2 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(0, 3);
        double cost_6_3 = tile.distanceSquaredTo(target);
        double move_cost_6_3 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_3 = Double.MAX_VALUE;
        else
            move_cost_6_3 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(1, 3);
        double cost_6_4 = tile.distanceSquaredTo(target);
        double move_cost_6_4 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_4 = Double.MAX_VALUE;
        else
            move_cost_6_4 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(2, 3);
        double cost_6_5 = tile.distanceSquaredTo(target);
        double move_cost_6_5 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_5 = Double.MAX_VALUE;
        else
            move_cost_6_5 = 1 / rc_.sensePassability(tile);
        tile = rc_.getLocation().translate(3, 3);
        double cost_6_6 = tile.distanceSquaredTo(target);
        double move_cost_6_6 = Double.MAX_VALUE;
        if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
            cost_6_6 = Double.MAX_VALUE;
        else
            move_cost_6_6 = 1 / rc_.sensePassability(tile);
        // iteration 1
        cost_0_0 = Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_0_1, cost_0_0 - move_cost_0_0))) + move_cost_0_0;
        cost_0_1 = Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_0_2, cost_0_1 - move_cost_0_1))))) + move_cost_0_1;
        cost_0_2 = Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_0_3, cost_0_2 - move_cost_0_2))))) + move_cost_0_2;
        cost_0_3 = Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_0_4, cost_0_3 - move_cost_0_3))))) + move_cost_0_3;
        cost_0_4 = Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_0_5, cost_0_4 - move_cost_0_4))))) + move_cost_0_4;
        cost_0_5 = Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_1_5, Math.min(cost_1_6, Math.min(cost_0_6, cost_0_5 - move_cost_0_5))))) + move_cost_0_5;
        cost_0_6 = Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_1_6, cost_0_6 - move_cost_0_6))) + move_cost_0_6;
        cost_1_0 = Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_1_1, cost_1_0 - move_cost_1_0))))) + move_cost_1_0;
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1)))))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_1_6 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_2_6, cost_1_6 - move_cost_1_6))))) + move_cost_1_6;
        cost_2_0 = Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_2_1, cost_2_0 - move_cost_2_0))))) + move_cost_2_0;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_2_6 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_3_6, cost_2_6 - move_cost_2_6))))) + move_cost_2_6;
        cost_3_0 = Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_3_1, cost_3_0 - move_cost_3_0))))) + move_cost_3_0;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_3_6 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_4_6, cost_3_6 - move_cost_3_6))))) + move_cost_3_6;
        cost_4_0 = Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_4_1, cost_4_0 - move_cost_4_0))))) + move_cost_4_0;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_4_6 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_5_6, cost_4_6 - move_cost_4_6))))) + move_cost_4_6;
        cost_5_0 = Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_5_1, cost_5_0 - move_cost_5_0))))) + move_cost_5_0;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;
        cost_5_6 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_5_5, Math.min(cost_6_5, Math.min(cost_6_6, cost_5_6 - move_cost_5_6))))) + move_cost_5_6;
        cost_6_0 = Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_1, cost_6_0 - move_cost_6_0))) + move_cost_6_0;
        cost_6_1 = Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_2, cost_6_1 - move_cost_6_1))))) + move_cost_6_1;
        cost_6_2 = Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_3, cost_6_2 - move_cost_6_2))))) + move_cost_6_2;
        cost_6_3 = Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_4, cost_6_3 - move_cost_6_3))))) + move_cost_6_3;
        cost_6_4 = Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_5, cost_6_4 - move_cost_6_4))))) + move_cost_6_4;
        cost_6_5 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_6, cost_6_5 - move_cost_6_5))))) + move_cost_6_5;
        cost_6_6 = Math.min(cost_5_6, Math.min(cost_5_5, Math.min(cost_6_5, cost_6_6 - move_cost_6_6))) + move_cost_6_6;

        // iteration 2
        cost_1_1 = Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_0_0, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_1_2, cost_1_1 - move_cost_1_1)))))))) + move_cost_1_1;
        cost_1_2 = Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_0_1, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_1_3, cost_1_2 - move_cost_1_2)))))))) + move_cost_1_2;
        cost_1_3 = Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_0_2, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_1_4, cost_1_3 - move_cost_1_3)))))))) + move_cost_1_3;
        cost_1_4 = Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_0_3, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_1_5, cost_1_4 - move_cost_1_4)))))))) + move_cost_1_4;
        cost_1_5 = Math.min(cost_0_6, Math.min(cost_0_5, Math.min(cost_0_4, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_2_5, Math.min(cost_2_6, Math.min(cost_1_6, cost_1_5 - move_cost_1_5)))))))) + move_cost_1_5;
        cost_2_1 = Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_1_0, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_2_2, cost_2_1 - move_cost_2_1)))))))) + move_cost_2_1;
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_2_5 = Math.min(cost_1_6, Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_3_6, Math.min(cost_2_6, cost_2_5 - move_cost_2_5)))))))) + move_cost_2_5;
        cost_3_1 = Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_2_0, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_3_2, cost_3_1 - move_cost_3_1)))))))) + move_cost_3_1;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_3_5 = Math.min(cost_2_6, Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_4_6, Math.min(cost_3_6, cost_3_5 - move_cost_3_5)))))))) + move_cost_3_5;
        cost_4_1 = Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_3_0, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_4_2, cost_4_1 - move_cost_4_1)))))))) + move_cost_4_1;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;
        cost_4_5 = Math.min(cost_3_6, Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_5_6, Math.min(cost_4_6, cost_4_5 - move_cost_4_5)))))))) + move_cost_4_5;
        cost_5_1 = Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_4_0, Math.min(cost_5_0, Math.min(cost_6_0, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_5_2, cost_5_1 - move_cost_5_1)))))))) + move_cost_5_1;
        cost_5_2 = Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_6_1, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_5_3, cost_5_2 - move_cost_5_2)))))))) + move_cost_5_2;
        cost_5_3 = Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_6_2, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_5_4, cost_5_3 - move_cost_5_3)))))))) + move_cost_5_3;
        cost_5_4 = Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_6_3, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_5_5, cost_5_4 - move_cost_5_4)))))))) + move_cost_5_4;
        cost_5_5 = Math.min(cost_4_6, Math.min(cost_4_5, Math.min(cost_4_4, Math.min(cost_5_4, Math.min(cost_6_4, Math.min(cost_6_5, Math.min(cost_6_6, Math.min(cost_5_6, cost_5_5 - move_cost_5_5)))))))) + move_cost_5_5;

        // iteration 3
        cost_2_2 = Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_1_1, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_2_3, cost_2_2 - move_cost_2_2)))))))) + move_cost_2_2;
        cost_2_3 = Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_1_2, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_2_4, cost_2_3 - move_cost_2_3)))))))) + move_cost_2_3;
        cost_2_4 = Math.min(cost_1_5, Math.min(cost_1_4, Math.min(cost_1_3, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_3_4, Math.min(cost_3_5, Math.min(cost_2_5, cost_2_4 - move_cost_2_4)))))))) + move_cost_2_4;
        cost_3_2 = Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_2_1, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_3_3, cost_3_2 - move_cost_3_2)))))))) + move_cost_3_2;
        cost_3_3 = Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_2_2, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_3_4, cost_3_3 - move_cost_3_3)))))))) + move_cost_3_3;
        cost_3_4 = Math.min(cost_2_5, Math.min(cost_2_4, Math.min(cost_2_3, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_4_4, Math.min(cost_4_5, Math.min(cost_3_5, cost_3_4 - move_cost_3_4)))))))) + move_cost_3_4;
        cost_4_2 = Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_3_1, Math.min(cost_4_1, Math.min(cost_5_1, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_4_3, cost_4_2 - move_cost_4_2)))))))) + move_cost_4_2;
        cost_4_3 = Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_3_2, Math.min(cost_4_2, Math.min(cost_5_2, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_4_4, cost_4_3 - move_cost_4_3)))))))) + move_cost_4_3;
        cost_4_4 = Math.min(cost_3_5, Math.min(cost_3_4, Math.min(cost_3_3, Math.min(cost_4_3, Math.min(cost_5_3, Math.min(cost_5_4, Math.min(cost_5_5, Math.min(cost_4_5, cost_4_4 - move_cost_4_4)))))))) + move_cost_4_4;

        // DETERMINING MIN COST DIRECTION
        Direction ret = Direction.CENTER;
        double minCost = Double.MAX_VALUE;

        if (cost_3_4 < minCost) {
            minCost = cost_3_4;
            ret = Direction.EAST;
        }
        if (cost_4_4 < minCost) {
            minCost = cost_4_4;
            ret = Direction.NORTHEAST;
        }
        if (cost_4_3 < minCost) {
            minCost = cost_4_3;
            ret = Direction.NORTH;
        }
        if (cost_4_2 < minCost) {
            minCost = cost_4_2;
            ret = Direction.NORTHWEST;
        }
        if (cost_3_2 < minCost) {
            minCost = cost_3_2;
            ret = Direction.WEST;
        }
        if (cost_2_2 < minCost) {
            minCost = cost_2_2;
            ret = Direction.SOUTHWEST;
        }
        if (cost_2_3 < minCost) {
            minCost = cost_2_3;
            ret = Direction.SOUTH;
        }
        if (cost_2_4 < minCost) {
            ret = Direction.SOUTHEAST;
        }
        return minCost > 1073741824 ? Direction.CENTER : ret;
    }
}