from collections import defaultdict
import itertools as it


def cost(x, y):
    return x ** 2 + y ** 2


# all possible chunks to visit...
all_chunks = list(it.product(range(-7, 8), repeat=2))


# sort chunks by how far away they are from the current location.
by_dist = defaultdict(list)
for x, y in all_chunks:
    by_dist[cost(x, y)].append((x, y))
del by_dist[0]


chunk_string = (
    str([by_dist[k] for k in sorted(by_dist.keys())])
    .replace("[", "{")
    .replace("(", "{")
    .replace(")", "}")
    .replace("]", "}")
)

code = f"""
    public static MapLocation nearestUnexploredLocation() {{
        int RAND = (int) (Math.random() * 1024);

        MapLocation m = Robot.rc.getLocation();
        int cx = ((m.x - Robot.initLoc.x) / 4) + 15;
        int cy = ((m.y - Robot.initLoc.y) / 4) + 15;
"""

for i, dist in enumerate(sorted(by_dist.keys())):
    chunks = by_dist[dist]
    code += f"\n        // r^2 = {dist}\n"
    for j in range(len(chunks)):
        x_str = f"CHUNKS[{i}][({str(j) + '+' if j else ''}RAND) % {len(chunks)}][0]"
        y_str = f"CHUNKS[{i}][({str(j) + '+' if j else ''}RAND) % {len(chunks)}][1]"
        code += f"""        if (!visited(cx+{x_str},cy+{y_str}))
            return m.translate(4*{x_str}, 4*{y_str});
"""


code += f"""
        return null;
}}

    private static final int[][][] CHUNKS = {chunk_string};"""

print(code)
