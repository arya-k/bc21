package good_bot;
public class Communication {
    public enum Label {
        FORM_WALL, ENEMY_EC, EXPAND, NEUTRAL_EC, ATTACK_LOC, CAPTURE_NEUTRAL_EC, SAFE_DIR_EDGE, SCOUT, LATCH, DEFEND, HIDE, EXPLORE, EXPLODE
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
        flag ^= 6929415;
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
            label = Label.ENEMY_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 64 == 8) {
            label = Label.EXPAND;
            acc = flag / 64;
            data[0] = acc % 4096;
            acc = acc / 4096;
            data[1] = acc % 64;
        } else if (flag % 64 == 24) {
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 1024 == 4) {
            label = Label.ATTACK_LOC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 20) {
            label = Label.CAPTURE_NEUTRAL_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 4096 == 12) {
            label = Label.SAFE_DIR_EDGE;
            acc = flag / 4096;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 8;
            acc = acc / 8;
            data[2] = acc % 64;
        } else if (flag % 2097152 == 28) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 2) {
            label = Label.LATCH;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 18) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 10) {
            label = Label.HIDE;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 16777216 == 26) {
            label = Label.EXPLORE;
        } else if (flag % 16777216 == 6) {
            label = Label.EXPLODE;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case FORM_WALL:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144) * 32 + 0);
            case ENEMY_EC:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 16);
            case EXPAND:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 4096) * 64 + 8);
            case NEUTRAL_EC:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 24);
            case ATTACK_LOC:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 4);
            case CAPTURE_NEUTRAL_EC:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 20);
            case SAFE_DIR_EDGE:
                return 6929415 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 12);
            case SCOUT:
                return 6929415 ^ (1 + (message.data[0] * 1) * 2097152 + 28);
            case LATCH:
                return 6929415 ^ (1 + (message.data[0] * 1) * 2097152 + 2);
            case DEFEND:
                return 6929415 ^ (1 + (message.data[0] * 1) * 2097152 + 18);
            case HIDE:
                return 6929415 ^ (1 + (message.data[0] * 1) * 2097152 + 10);
            case EXPLORE:
                return 6929415 ^ (1 + (0) * 16777216 + 26);
            case EXPLODE:
                return 6929415 ^ (1 + (0) * 16777216 + 6);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

