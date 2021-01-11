"""

Generates a GOTO with unrolled loops, of a specified size...

"""

# TODO: see if iteratively adding directions is better

NAV_GRID_SIZE = 5
NAV_ITERATIONS = 2

HALF_SIZE = NAV_GRID_SIZE // 2
DIRS = {
    "EAST": (0, 1),
    "NORTHEAST": (1, 1),
    "NORTH": (1, 0),
    "NORTHWEST": (1, -1),
    "WEST": (0, -1),
    "SOUTHWEST": (-1, -1),
    "SOUTH": (-1, 0),
    "SOUTHEAST": (-1, 1),
}

ORDINALS = {
    "NORTH": 0,
    "NORTHEAST": 1,
    "EAST": 2,
    "SOUTHEAST": 3,
    "SOUTH": 4,
    "SOUTHWEST": 5,
    "WEST": 6,
    "NORTHWEST": 7,
    "CENTER": 8,
}


def grid_locations():
    """ Generator to get all indices in the grid """
    for y in range(NAV_GRID_SIZE):
        for x in range(NAV_GRID_SIZE):
            yield y, x


def adjacent(y, x):
    """ Generator to get all neighbors within the bounds of the nav grid """
    for dy, dx in DIRS.values():
        if 0 <= y + dy < NAV_GRID_SIZE and 0 <= x + dx < NAV_GRID_SIZE:
            yield y + dy, x + dx


code = f"""
private static Direction goTo{NAV_GRID_SIZE}(MapLocation target, int danger) throws GameActionException {{
    /* AUTOGENERATED with `nav.py`, with params NAV_GRID_SIZE={NAV_GRID_SIZE}, NAV_ITERATIONS={NAV_ITERATIONS} */

    RobotController rc_ = rc; // move into local scope
"""

# Initializing cost and movement costs.

code += """
    // POPULATE COSTS AND MOVEMENT COSTS"""

for i, (y, x) in enumerate(grid_locations()):
    dy, dx = y - HALF_SIZE, x - HALF_SIZE

    is_center = (dx == 0) and (dy == 0)
    init = "" if i else "MapLocation "

    if is_center:  # special case: the center
        code += f"""
            {init}tile = rc_.getLocation();
            double cost_{y}_{x} = tile.distanceSquaredTo(target);
            double move_cost_{y}_{x} = 1 / rc_.sensePassability(tile);"""
    else:
        code += f"""
            {init}tile = rc_.getLocation().translate({dx}, {dy});
            double cost_{y}_{x} = tile.distanceSquaredTo(target);
            double move_cost_{y}_{x} = Double.MAX_VALUE;
            if (!rc_.onTheMap(tile) || rc_.isLocationOccupied(tile))
                cost_{y}_{x} = Double.MAX_VALUE;
            else
                move_cost_{y}_{x} = 1 / rc_.sensePassability(tile);"""


# Iterative cost determination

for i in range(NAV_ITERATIONS):
    code += f"\n    // iteration {i+1}\n"
    for y, x in grid_locations():
        # tighten the circle of updated grid items...
        if not (
            (i <= y < NAV_GRID_SIZE - i) and (i <= x < NAV_GRID_SIZE - i)
        ):
            continue
        neighbors = list(adjacent(y, x))
        min_expr = f"Math.min(cost_{neighbors[0][0]}_{neighbors[0][1]}, cost_{y}_{x} - move_cost_{y}_{x})"
        for adj_y, adj_x in neighbors[1:]:
            min_expr = f"Math.min(cost_{adj_y}_{adj_x}, {min_expr})"

        code += f"    cost_{y}_{x} = {min_expr} + move_cost_{y}_{x};\n"

# minimum direction

code += f"""
    // DETERMINING MIN COST DIRECTION
    Direction ret = Direction.CENTER;
    double minCost = cost_{HALF_SIZE}_{HALF_SIZE};
"""

for i, (name, (dx, dy)) in enumerate(DIRS.items()):
    costString = f"cost_{HALF_SIZE + dx}_{HALF_SIZE + dy}"
    minExpr = "" if i == 7 else f"minCost = {costString};"

    code += f"""
    if ({costString} < minCost && (danger & {1 << ORDINALS[name]}) == 0) {{
        {minExpr}ret = Direction.{name};
    }}"""

code += f"""
    return ret;
}}"""

print(code)
