package seeding;
public class Communication {
    public enum Label {
        EXPLORE, FLEE, CURRENTLY_DEFENDING, FINAL_FRONTIER, SCOUT, DEFEND, SAFE_DIR, SCOUT_LOCATION, CAPTURE_NEUTRAL_EC, ENEMY_EC, NEUTRAL_EC, ATTACK_LOC
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
        flag ^= 14717098;
        flag--;
        int[] data = new int[4];
        Label label;
        int acc;
        if (flag % 16777216 == 0) {
            label = Label.EXPLORE;
        } else if (flag % 16777216 == 8388608) {
            label = Label.FLEE;
        } else if (flag % 16777216 == 4194304) {
            label = Label.CURRENTLY_DEFENDING;
        } else if (flag % 16777216 == 12582912) {
            label = Label.FINAL_FRONTIER;
        } else if (flag % 2097152 == 1048576) {
            label = Label.SCOUT;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 524288) {
            label = Label.DEFEND;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 2097152 == 1572864) {
            label = Label.SAFE_DIR;
            acc = flag / 2097152;
            data[0] = acc % 8;
        } else if (flag % 1024 == 512) {
            label = Label.SCOUT_LOCATION;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 256) {
            label = Label.CAPTURE_NEUTRAL_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 64 == 32) {
            label = Label.ENEMY_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 64 == 16) {
            label = Label.NEUTRAL_EC;
            acc = flag / 64;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
        } else if (flag % 2 == 1) {
            label = Label.ATTACK_LOC;
            acc = flag / 2;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 256;
            acc = acc / 256;
            data[3] = acc % 2;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case EXPLORE:
                return 14717098 ^ (1 + (0) * 16777216 + 0);
            case FLEE:
                return 14717098 ^ (1 + (0) * 16777216 + 8388608);
            case CURRENTLY_DEFENDING:
                return 14717098 ^ (1 + (0) * 16777216 + 4194304);
            case FINAL_FRONTIER:
                return 14717098 ^ (1 + (0) * 16777216 + 12582912);
            case SCOUT:
                return 14717098 ^ (1 + (message.data[0] * 1) * 2097152 + 1048576);
            case DEFEND:
                return 14717098 ^ (1 + (message.data[0] * 1) * 2097152 + 524288);
            case SAFE_DIR:
                return 14717098 ^ (1 + (message.data[0] * 1) * 2097152 + 1572864);
            case SCOUT_LOCATION:
                return 14717098 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 512);
            case CAPTURE_NEUTRAL_EC:
                return 14717098 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 256);
            case ENEMY_EC:
                return 14717098 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 32);
            case NEUTRAL_EC:
                return 14717098 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 16);
            case ATTACK_LOC:
                return 14717098 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 4194304) * 2 + 1);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

