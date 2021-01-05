package bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Nav {
    // Constants:
    static int NAV_GRID_SIZE = 5;
    static int NAV_ITERATIONS = 2;

    static int INITIAL_COST_MULTIPLIER = 10;
    static int PASSABILITY_SCALE_FACTOR = 10;

    static int[][] OFFSETS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    // Variables:
    static RobotController rc_;
    static MapLocation goalPos = null;

    public static void init(RobotController rc) {
        Nav.rc_ = rc;

        // TODO: initialize map edge predictions?
        // TODO: pathfinding grid for higher order calculations?
    }

    /**
     * Run at the start of onUpdate to update navigation variables,
     * like the location of map edges.
     *
     * @return whether a move was made.
     */
    static boolean tick() throws GameActionException {
        // TODO: higher level planning
        // TODO: check if we can see that the target is off the map
        
        Direction dir = goTo(goalPos);
        if (dir != null && rc_.canMove(dir)) {
            rc_.move(dir);
            return true;
        }
        return false;
    }

    /**
     * Sets the navigation goal
     *
     * @param goal the map location to aim to head towards.
     */
    static void setGoal(MapLocation goal) {
        goalPos = goal;
    }

    /**
     * Given the direction of the target, attempts to move towards
     * it using an iterative algorithm that approximates the best moves.
     * This will be unrolled / heavily optimized.
     *
     * @param target the location of the target that we are moving towards.
     * @return the direction to move in, null if this method fails.
     */
    private static Direction goTo(MapLocation target) throws GameActionException {
        RobotController rc = rc_; // move into local scope

        double[][] costs = new double[NAV_GRID_SIZE][NAV_GRID_SIZE];
        double[][] movement_costs = new double[NAV_GRID_SIZE][NAV_GRID_SIZE];

        for (int i = 0; i < NAV_GRID_SIZE; i++) {
            for (int j = 0; j < NAV_GRID_SIZE; j++) {
                int dy = i - (NAV_GRID_SIZE / 2);
                int dx = j - (NAV_GRID_SIZE / 2);

                MapLocation tile = rc.getLocation().translate(dx, dy);
                costs[i][j] = tile.distanceSquaredTo(target) * INITIAL_COST_MULTIPLIER;

                if (!rc.onTheMap(tile) || rc.isLocationOccupied(tile))
                    costs[i][j] = movement_costs[i][j] = Double.MAX_VALUE;
                else // NOTE: as long as this is always below min(sensor_r^2) we don't need to catch this!
                    movement_costs[i][j] = PASSABILITY_SCALE_FACTOR / rc.sensePassability(tile);
            }
        }

        for (int iter = 0; iter < NAV_ITERATIONS; iter++) {
            for (int y = 0; y < NAV_GRID_SIZE; y++) {
                for (int x = 0; x < NAV_GRID_SIZE; x++) {
                    for (int[] offset : OFFSETS) {
                        int _x = x + offset[0];
                        int _y = y + offset[1];
                        if (_x < 0 || _y < 0 || _x >= NAV_GRID_SIZE || _y >= NAV_GRID_SIZE) continue;

                        //Update the cost
                        costs[y][x] = Math.min(costs[_y][_x] + movement_costs[y][x], costs[y][x]);
                    }
                }
            }
        }

        // Return the minimum direction to move in
        double minCost = Double.MAX_VALUE;
        int c = (NAV_GRID_SIZE / 2);
        Direction ret = null;
        for (Direction dir : Direction.allDirections()) {
            double cost = costs[c + dir.dy][c + dir.dx];
            if (cost < minCost && cost < 1 << 20) {
                minCost = cost;
                ret = dir;
            }
        }

        return ret;
    }

}
