package bot;
public class Communication {
    public enum Label {
        WALL_GAP, SAFE_DIR_EDGE, SCOUT, DANGER_DIR, LATCH, ATTACK, DEFEND, HIDE
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
        flag ^= 213405;
        flag--;
        int[] data = new int[3];
        Label label;
        int acc;
        if (flag % 1024 == 0) {
            label = Label.WALL_GAP;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 4096 == 512) {
            label = Label.SAFE_DIR_EDGE;
            acc = flag / 4096;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 8;
            acc = acc / 8;
            data[2] = acc % 64;
        } else if (flag % 2097152 == 256) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 768) {
            label = Label.DANGER_DIR;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 128) {
            label = Label.LATCH;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 640) {
            label = Label.ATTACK;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 384) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 896) {
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
            case WALL_GAP:
                return 213405 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 0);
            case SAFE_DIR_EDGE:
                return 213405 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 512);
            case SCOUT:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 256);
            case DANGER_DIR:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 768);
            case LATCH:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 128);
            case ATTACK:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 640);
            case DEFEND:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 384);
            case HIDE:
                return 213405 ^ (1 + (message.data[0] * 1) * 2097152 + 896);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

