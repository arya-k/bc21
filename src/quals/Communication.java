package quals;
public class Communication {
    public enum Label {
        EXPLORE, HIDE, CURRENTLY_DEFENDING, SCOUT, ATTACKING, OUR_EC, EC_UPDATE, ENEMY_EC, NEUTRAL_EC, DANGER_INFO
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
        flag ^= 2406342;
        flag--;
        int[] data = new int[3];
        Label label;
        int acc;
        if (flag % 16777216 == 0) {
            label = Label.EXPLORE;
        } else if (flag % 16777216 == 8388608) {
            label = Label.HIDE;
        } else if (flag % 16777216 == 4194304) {
            label = Label.CURRENTLY_DEFENDING;
        } else if (flag % 2097152 == 1048576) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 32768 == 16384) {
            label = Label.ATTACKING;
            acc = flag / 32768;
            data[0] = acc % 256;
            acc = acc / 256;
            data[1] = acc % 2;
        } else if (flag % 1024 == 512) {
            label = Label.OUR_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 64 == 32) {
            label = Label.EC_UPDATE;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 64 == 16) {
            label = Label.ENEMY_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 64 == 48) {
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 32 == 8) {
            label = Label.DANGER_INFO;
            acc = flag / 32;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 32;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case EXPLORE:
                return 2406342 ^ (1 + (0) * 16777216 + 0);
            case HIDE:
                return 2406342 ^ (1 + (0) * 16777216 + 8388608);
            case CURRENTLY_DEFENDING:
                return 2406342 ^ (1 + (0) * 16777216 + 4194304);
            case SCOUT:
                return 2406342 ^ (1 + (message.data[0] * 1) * 2097152 + 1048576);
            case ATTACKING:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 256) * 32768 + 16384);
            case OUR_EC:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 512);
            case EC_UPDATE:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 32);
            case ENEMY_EC:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 16);
            case NEUTRAL_EC:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 48);
            case DANGER_INFO:
                return 2406342 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 32 + 8);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

