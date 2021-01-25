package seeding;
public class Communication {
    public enum Label {
        EXPLORE, FLEE, CURRENTLY_DEFENDING, FINAL_FRONTIER, BUFF, SCOUT, DEFEND, SAFE_DIR, ATTACKING, SCOUT_LOCATION, OUR_EC, ATTACK_LOC, CAPTURE_NEUTRAL_EC, ENEMY_EC, NEUTRAL_EC, DANGER_INFO
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
        flag ^= 826052;
        flag--;
        int[] data = new int[5];
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
        } else if (flag % 16777216 == 2097152) {
            label = Label.BUFF;
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
        } else if (flag % 32768 == 16384) {
            label = Label.ATTACKING;
            acc = flag / 32768;
            data[0] = acc % 256;
            acc = acc / 256;
            data[1] = acc % 2;
        } else if (flag % 1024 == 512) {
            label = Label.SCOUT_LOCATION;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 256) {
            label = Label.OUR_EC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 768) {
            label = Label.ATTACK_LOC;
            acc = flag / 1024;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
        } else if (flag % 1024 == 128) {
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
        } else if (flag % 16 == 8) {
            label = Label.DANGER_INFO;
            acc = flag / 16;
            data[0] = acc % 128;
            acc = acc / 128;
            data[1] = acc % 128;
            acc = acc / 128;
            data[2] = acc % 16;
            acc = acc / 16;
            data[3] = acc % 2;
            acc = acc / 2;
            data[4] = acc % 2;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }
    public static int encode(Message message) {
        switch (message.label) {
            case EXPLORE:
                return 826052 ^ (1 + (0) * 16777216 + 0);
            case FLEE:
                return 826052 ^ (1 + (0) * 16777216 + 8388608);
            case CURRENTLY_DEFENDING:
                return 826052 ^ (1 + (0) * 16777216 + 4194304);
            case FINAL_FRONTIER:
                return 826052 ^ (1 + (0) * 16777216 + 12582912);
            case BUFF:
                return 826052 ^ (1 + (0) * 16777216 + 2097152);
            case SCOUT:
                return 826052 ^ (1 + (message.data[0] * 1) * 2097152 + 1048576);
            case DEFEND:
                return 826052 ^ (1 + (message.data[0] * 1) * 2097152 + 524288);
            case SAFE_DIR:
                return 826052 ^ (1 + (message.data[0] * 1) * 2097152 + 1572864);
            case ATTACKING:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 256) * 32768 + 16384);
            case SCOUT_LOCATION:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 512);
            case OUR_EC:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 256);
            case ATTACK_LOC:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 768);
            case CAPTURE_NEUTRAL_EC:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128) * 1024 + 128);
            case ENEMY_EC:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 32);
            case NEUTRAL_EC:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384) * 64 + 16);
            case DANGER_INFO:
                return 826052 ^ (1 + (message.data[0] * 1 + message.data[1] * 128 + message.data[2] * 16384 + message.data[3] * 262144 + message.data[4] * 524288) * 16 + 8);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

