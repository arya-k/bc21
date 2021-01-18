"""

Generate translations up to a certain r^2, smallest r^2 first.

"""

R_SQUARED = 9


def grid_locations():
    """ Generator to get all indices in the grid """
    for y in range(-R_SQUARED, R_SQUARED):
        for x in range(-R_SQUARED, R_SQUARED):
            if 0 < x ** 2 + y ** 2 <= R_SQUARED:
                yield y, x

def dist(args):
    x, y = args
    return x**2 + y**2

locations = sorted(grid_locations(), key = dist)

code = str(locations).replace('(','{').replace('[','{').replace(')','}').replace(']','}')
print(code)