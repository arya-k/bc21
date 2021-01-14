package refactor;
public class Communication {
    public enum Label {
        ENEMY_EC, NEUTRAL_EC, ATTACK_LOC, CAPTURE_NEUTRAL_EC, SAFE_DIR_EDGE, SCOUT, DEFEND, EXPLORE, FLEE, EXPLODE, HIDE, STOP_PRODUCING_MUCKRAKERS
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
        flag ^= 8809664;
        flag--;
        int[] data = new int[3];
        Label label;
        int acc;
        if (flag % 64 == 0) {
            label = Label.ENEMY_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 64 == 32) {
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 1024 == 16) {
            label = Label.ATTACK_LOC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 48) {
            label = Label.CAPTURE_NEUTRAL_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 4096 == 8) {
            label = Label.SAFE_DIR_EDGE;
            acc = flag / 4096;
            data[0] = acc % 8;
            acc = acc / 8;
            data[1] = acc % 8;
            acc = acc / 8;
            data[2] = acc % 64;
        } else if (flag % 2097152 == 40) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 24) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 16777216 == 56) {
            label = Label.EXPLORE;
        } else if (flag % 16777216 == 4) {
            label = Label.FLEE;
        } else if (flag % 16777216 == 36) {
            label = Label.EXPLODE;
        } else if (flag % 16777216 == 20) {
            label = Label.HIDE;
        } else if (flag % 16777216 == 52) {
            label = Label.STOP_PRODUCING_MUCKRAKERS;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case ENEMY_EC:
                return 8809664 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 0);
            case NEUTRAL_EC:
                return 8809664 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 32);
            case ATTACK_LOC:
                return 8809664 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 16);
            case CAPTURE_NEUTRAL_EC:
                return 8809664 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 48);
            case SAFE_DIR_EDGE:
                return 8809664 ^ (1 + (message.data[0] * 1 + message.data[1] * 8 + message.data[2] * 64) * 4096 + 8);
            case SCOUT:
                return 8809664 ^ (1 + (message.data[0] * 1) * 2097152 + 40);
            case DEFEND:
                return 8809664 ^ (1 + (message.data[0] * 1) * 2097152 + 24);
            case EXPLORE:
                return 8809664 ^ (1 + (0) * 16777216 + 56);
            case FLEE:
                return 8809664 ^ (1 + (0) * 16777216 + 4);
            case EXPLODE:
                return 8809664 ^ (1 + (0) * 16777216 + 36);
            case HIDE:
                return 8809664 ^ (1 + (0) * 16777216 + 20);
            case STOP_PRODUCING_MUCKRAKERS:
                return 8809664 ^ (1 + (0) * 16777216 + 52);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

