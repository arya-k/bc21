package bot;

public class Communication {
    public enum Label {
        EXPLORE, LATCH, ATTACK, DEFEND
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
        flag ^= 9232565;
        flag--;
        int[] data = new int[1];
        Label label;
        int acc;
        switch (flag % 2097152) {
            case 0:
                label = Label.EXPLORE;
                acc = flag / 2097152;
                data[0] = acc % 8;
                break;
            case 1048576:
                label = Label.LATCH;
                acc = flag / 2097152;
                data[0] = acc % 8;
                break;
            case 524288:
                label = Label.ATTACK;
                acc = flag / 2097152;
                data[0] = acc % 8;
                break;
            case 1572864:
                label = Label.DEFEND;
                break;
            default:
                throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case EXPLORE:
                return 9232565 ^ (1 + (message.data[0] * 1) * 2097152 + 0);
            case LATCH:
                return 9232565 ^ (1 + (message.data[0] * 1) * 2097152 + 1048576);
            case ATTACK:
                return 9232565 ^ (1 + (message.data[0] * 1) * 2097152 + 524288);
            case DEFEND:
                return 9232565 ^ (1 + (0) * 2097152 + 1572864);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

