class Command:
    def __init__(self, name, bit_list):
        self.header_len = None
        self.name = name
        self.bit_list = bit_list


########################################
### LIST OF COMMANDS (EDIT HERE)    ####
########################################

commands = [
    Command("SCOUT", [3]),
    Command("EXPLORE", []),
    Command("SAFE_DIR_EDGE", [3, 3, 6]),
    Command("FLEE", []),
    Command("DEFEND", [3]),
    Command("ENEMY_EC", [7, 7, 4]),
    Command("NEUTRAL_EC", [7, 7, 4]),
    Command("EXPLODE", []),
    Command("ATTACK_LOC", [7, 7]),
    Command("HIDE", [3]),
    Command("CAPTURE_NEUTRAL_EC", [7, 7]),
    Command("STOP_PRODUCING_MUCKRAKERS", []),
]

#########################################
#########################################
#########################################

BITS = 24

import random

MASK = random.randint(5, 2 ** 24)


def bit_mirror(x, bits):
    j = 0
    result = 0
    for i in range(bits - 1, -1, -1):
        result |= ((x >> j) & 1) << i
        j += 1
    return result


for c in commands:
    c.header_len = BITS - sum(c.bit_list)
commands.sort(key=lambda command: command.header_len)

header_name = {}
name_header = {}
name_header_len = {}

next_acc = -1
cur_len = commands[0].header_len
for c in commands:
    if c.header_len < cur_len:
        next_acc >>= cur_len - c.header_len
        cur_len = c.header_len
    next_acc += 1
    c.header = bit_mirror(next_acc, cur_len)
    header_name[c.header] = c.name
    name_header[c.name] = c.header
    name_header_len[c.name] = c.header_len

for key in name_header.keys():
    print(f"{key} -> {name_header[key]}, {name_header_len[key]}")

encode_blocks = []
decode_blocks = []
for c in commands:
    entries = len(c.bit_list)
    passed = [0]
    for i in range(entries - 1):
        passed.append(passed[-1] + c.bit_list[i])
    sum_expr = " + ".join(
        f"message.data[{i}] * {2 ** passed[i]}" for i in range(entries)
    )
    encode_block = f"""
            case {c.name}:
                return {MASK} ^ (1 + ({sum_expr if sum_expr else 0}) * {2 ** c.header_len} + {c.header});
    """
    encode_blocks.append(encode_block)

    inner_exprs = [
        [
            f"data[{i}] = acc % {2 ** c.bit_list[i]};",
            f"acc = acc / {2 ** c.bit_list[i]};",
        ]
        for i in range(entries)
    ]
    inner_exprs = [x for l in inner_exprs for x in l][:-1]
    inner_expr = "\n            ".join(inner_exprs)
    decode_block = f"""        
if (flag % {2 ** c.header_len} == {c.header}) {{
            label = Label.{c.name};
            {f"acc = flag / {2 ** c.header_len};" if inner_exprs else ""}
            {inner_expr}
        }}
    """
    decode_block = "\n".join(s for s in decode_block.splitlines() if s.strip())
    decode_blocks.append(decode_block.strip())

encode = "\n".join(encode_blocks)
encode = "\n".join(s for s in encode.splitlines() if s.strip())
decode = " else ".join(decode_blocks)

code = f"""package refactor;
public class Communication {{
    public enum Label {{
        {", ".join(c.name for c in commands)}
    }}
    public static class Message {{
        Label label;
        int[] data;
        public Message(Label label, int[] data) {{
            this.label = label;
            this.data = data;
        }}
    }}
    public static Message decode(int flag) {{
        flag ^= {MASK};
        flag--;
        int[] data = new int[{max(len(c.bit_list) for c in commands)}];
        Label label;
        int acc;
        {decode} else {{
            throw new RuntimeException("Attempting to decode an invalid flag");
        }}
        return new Message(label, data);
    }}
    public static int encode(Message message) {{
        switch (message.label) {{
{encode}
        }}
        throw new RuntimeException("Attempting to encode an invalid message");
    }}
}}
"""

import os

dir_path = os.path.dirname(os.path.realpath(__file__))
with open(os.path.join(dir_path, "..", "Communication.java"), "w") as f:
    print(code, file=f)
