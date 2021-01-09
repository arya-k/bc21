
import itertools as it

def cost(args):
    x,y = args
    return x**2 + y**2

all_chunks = sorted(it.product(range(-7,8), repeat=2), key=cost)[1:]


code = "MapLocation m = Robot.rc.getLocation();\n"
for dx, dy in all_chunks:
    code += f"if (!visited(m.translate({dx*8}, {dy*8}))) return m.translate({dx*8}, {dy*8});\n"
code += "\nreturn null;"
print(code)
