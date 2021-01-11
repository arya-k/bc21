package bot;
public class Communication {
    public enum Label {
        FORM_WALL, EXPAND, NEUTRAL_EC, ENEMY_EC, ATTACK_LOC, DEFEND_LOC, WALL_GAP, CAPTURE_NEUTRAL_EC, SAFE_DIR_EDGE, SCOUT, LATCH, ATTACK, DEFEND, HIDE
    }
    public static class Message {
        Label label;
        int[] data;
        public Message(Label label, int[] data) {
            this.label = label;
            this.data = data;
        }
    }
    public static Message decode(int flag) {
<<<<<<< Updated upstream
        flag ^= 11963391;
=======
        flag ^= 7428416;
>>>>>>> Stashed changes
        flag--;
        int[] data = new int[4];
        Label label;
        int acc;
        if (flag % 32 == 0) {
            label = Label.FORM_WALL;
            acc = flag / 32;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
            acc = acc / 16;
            data[3] = acc % 2;
        } else if (flag % 64 == 16) {
            label = Label.EXPAND;
            acc = flag / 64;
            data[0] = acc % 4096;
            acc = acc / 4096;
            data[1] = acc % 64;
        } else if (flag % 64 == 8) {
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 1024 == 24) {
            label = Label.ENEMY_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 4) {
            label = Label.ATTACK_LOC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 20) {
            label = Label.DEFEND_LOC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 12) {
            label = Label.WALL_GAP;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 28) {
            label = Label.CAPTURE_NEUTRAL_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 4096 == 2) {
            label = Label.SAFE_DIR_EDGE;
            acc = flag / 4096;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 8;
            acc = acc / 8;
            data[2] = acc % 64;
        } else if (flag % 2097152 == 18) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 10) {
            label = Label.LATCH;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 26) {
            label = Label.ATTACK;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 6) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 22) {
            label = Label.HIDE;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case FORM_WALL:
<<<<<<< Updated upstream
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144) * 32 + 0);
            case EXPAND:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 4096) * 64 + 16);
            case NEUTRAL_EC:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 8);
            case ENEMY_EC:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 24);
            case ATTACK_LOC:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 4);
            case DEFEND_LOC:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 20);
            case WALL_GAP:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 12);
            case CAPTURE_NEUTRAL_EC:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 28);
            case SAFE_DIR_EDGE:
                return 11963391 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 2);
            case SCOUT:
                return 11963391 ^ (1 + (message.data[0] * 1) * 2097152 + 18);
            case LATCH:
                return 11963391 ^ (1 + (message.data[0] * 1) * 2097152 + 10);
            case ATTACK:
                return 11963391 ^ (1 + (message.data[0] * 1) * 2097152 + 26);
            case DEFEND:
                return 11963391 ^ (1 + (message.data[0] * 1) * 2097152 + 6);
            case HIDE:
                return 11963391 ^ (1 + (message.data[0] * 1) * 2097152 + 22);
=======
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144) * 32 + 0);
            case EXPAND:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 4096) * 64 + 16);
            case NEUTRAL_EC:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 8);
            case ENEMY_EC:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 24);
            case ATTACK_LOC:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 4);
            case WALL_GAP:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 20);
            case CAPTURE_NEUTRAL_EC:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 12);
            case SAFE_DIR_EDGE:
                return 7428416 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 28);
            case SCOUT:
                return 7428416 ^ (1 + (message.data[0] * 1) * 2097152 + 2);
            case LATCH:
                return 7428416 ^ (1 + (message.data[0] * 1) * 2097152 + 18);
            case ATTACK:
                return 7428416 ^ (1 + (message.data[0] * 1) * 2097152 + 10);
            case DEFEND:
                return 7428416 ^ (1 + (message.data[0] * 1) * 2097152 + 26);
            case HIDE:
                return 7428416 ^ (1 + (message.data[0] * 1) * 2097152 + 6);
>>>>>>> Stashed changes
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

