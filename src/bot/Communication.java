package bot;
public class Communication {
    public enum Label {
        FORM_WALL, NEUTRAL_EC, WALL_GAP, CAPTURE_NEUTRAL_EC, SAFE_DIR_EDGE, EXPAND, SCOUT, DANGER_DIR, LATCH, ATTACK, DEFEND, HIDE
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
        flag ^= 5167041;
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
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 1024 == 8) {
            label = Label.WALL_GAP;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 24) {
            label = Label.CAPTURE_NEUTRAL_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 4096 == 4) {
            label = Label.SAFE_DIR_EDGE;
            acc = flag / 4096;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 8;
            acc = acc / 8;
            data[2] = acc % 64;
        } else if (flag % 32768 == 20) {
            label = Label.EXPAND;
            acc = flag / 32768;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 64;
        } else if (flag % 2097152 == 12) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 28) {
            label = Label.DANGER_DIR;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 2) {
            label = Label.LATCH;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 18) {
            label = Label.ATTACK;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 10) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 26) {
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
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144) * 32 + 0);
            case NEUTRAL_EC:
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 16);
            case WALL_GAP:
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 8);
            case CAPTURE_NEUTRAL_EC:
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 24);
            case SAFE_DIR_EDGE:
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 4);
            case EXPAND:
                return 5167041 ^ (1 + (message.data[0] * 1 + message.data[1] * 8) * 32768 + 20);
            case SCOUT:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 12);
            case DANGER_DIR:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 28);
            case LATCH:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 2);
            case ATTACK:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 18);
            case DEFEND:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 10);
            case HIDE:
                return 5167041 ^ (1 + (message.data[0] * 1) * 2097152 + 26);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

