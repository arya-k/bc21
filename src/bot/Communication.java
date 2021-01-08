package bot;

public class Communication {
    public enum Label {
        SCOUT, DANGER_DIR, SAFE_DIR_EDGE, LATCH, ATTACK, DEFEND, HIDE, WALL_GAP
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
        flag ^= 497798;
        flag--;
        int[] data = new int[3];
        Label label;
        int acc;
        switch (flag % 1024) {
            case 0:
                label = Label.SCOUT;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 512:
                label = Label.DANGER_DIR;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 256:
                label = Label.SAFE_DIR_EDGE;
                acc = flag / 1024;
                data[0] = acc % 8;
                acc = acc / 8;
                data[1] = acc % 8;
                acc = acc / 8;
                data[2] = acc % 64;
                break;
            case 768:
                label = Label.LATCH;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 128:
                label = Label.ATTACK;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 640:
                label = Label.DEFEND;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 384:
                label = Label.HIDE;
                acc = flag / 1024;
                data[0] = acc % 8;
                break;
            case 896:
                label = Label.WALL_GAP;
                acc = flag / 1024;
                data[0] = acc % 128;
                acc = acc / 128;
                data[1] = acc % 128;
                break;
            default:
                throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case SCOUT:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 0);
            case DANGER_DIR:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 512);
            case SAFE_DIR_EDGE:
                return 497798 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 1024 + 256);
            case LATCH:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 768);
            case ATTACK:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 128);
            case DEFEND:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 640);
            case HIDE:
                return 497798 ^ (1 + (message.data[0] * 1) * 1024 + 384);
            case WALL_GAP:
                return 497798 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 896);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

