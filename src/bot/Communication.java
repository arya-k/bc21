package bot;

public class Communication {
    public enum Label {
        EXPLORE, DANGER_DIR, SAFE_DIR_EDGE, LATCH, ATTACK, DEFEND
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
        flag ^= 14808312;
        flag--;
        int[] data = new int[3];
        Label label;
        int acc;
        switch (flag % 4096) {
            case 0:
                label = Label.EXPLORE;
                acc = flag / 4096;
                data[0] = acc % 8;
                break;
            case 2048:
                label = Label.DANGER_DIR;
                acc = flag / 4096;
                data[0] = acc % 8;
                break;
            case 1024:
                label = Label.SAFE_DIR_EDGE;
                acc = flag / 4096;
                data[0] = acc % 8;
                acc = acc / 8;
                data[1] = acc % 8;
                acc = acc / 8;
                data[2] = acc % 64;
                break;
            case 3072:
                label = Label.LATCH;
                acc = flag / 4096;
                data[0] = acc % 8;
                break;
            case 512:
                label = Label.ATTACK;
                acc = flag / 4096;
                data[0] = acc % 8;
                break;
            case 2560:
                label = Label.DEFEND;
                acc = flag / 4096;
                data[0] = acc % 8;
                break;
            default:
                throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case EXPLORE:
                return 14808312 ^ (1 + (message.data[0] * 1) * 4096 + 0);
            case DANGER_DIR:
                return 14808312 ^ (1 + (message.data[0] * 1) * 4096 + 2048);
            case SAFE_DIR_EDGE:
                return 14808312 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 1024);
            case LATCH:
                return 14808312 ^ (1 + (message.data[0] * 1) * 4096 + 3072);
            case ATTACK:
                return 14808312 ^ (1 + (message.data[0] * 1) * 4096 + 512);
            case DEFEND:
                return 14808312 ^ (1 + (message.data[0] * 1) * 4096 + 2560);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

