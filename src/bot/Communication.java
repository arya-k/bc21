package bot;

public class Communication {
    public enum Label {
        SCOUT, DANGER_DIR, SAFE_DIR_EDGE, LATCH, ATTACK, DEFEND, HIDE, WALL_GAP, FORM_WALL
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
        flag ^= 12887516;
        flag--;
        int[] data = new int[4];
        Label label;
        int acc;
        switch (flag % 32) {
            case 0:
                label = Label.SCOUT;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 16:
                label = Label.DANGER_DIR;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 8:
                label = Label.SAFE_DIR_EDGE;
                acc = flag / 32;
                data[0] = acc % 8;
                acc = acc / 8;
                data[1] = acc % 8;
                acc = acc / 8;
                data[2] = acc % 64;
                break;
            case 24:
                label = Label.LATCH;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 4:
                label = Label.ATTACK;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 20:
                label = Label.DEFEND;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 12:
                label = Label.HIDE;
                acc = flag / 32;
                data[0] = acc % 8;
                break;
            case 28:
                label = Label.WALL_GAP;
                acc = flag / 32;
                data[0] = acc % 128;
                acc = acc / 128;
                data[1] = acc % 128;
                break;
            case 2:
                label = Label.FORM_WALL;
                acc = flag / 32;
                data[0] = acc % 128;
                acc = acc / 128;
                data[1] = acc % 128;
                acc = acc / 128;
                data[2] = acc % 16;
                acc = acc / 16;
                data[3] = acc % 2;
                break;
            default:
                throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case SCOUT:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 0);
            case DANGER_DIR:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 16);
            case SAFE_DIR_EDGE:
                return 12887516 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 32 + 8);
            case LATCH:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 24);
            case ATTACK:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 4);
            case DEFEND:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 20);
            case HIDE:
                return 12887516 ^ (1 + (message.data[0] * 1) * 32 + 12);
            case WALL_GAP:
                return 12887516 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 32 + 28);
            case FORM_WALL:
                return 12887516 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144) * 32 + 2);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

