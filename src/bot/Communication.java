package bot;

public class Communication {
    public enum Label {
        ONE_COORD, ONE_COORD_TWO, TWO_COORDS, HELLO, GOODBYE
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
        flag ^= 3745752
        flag--;
        int[] data = new int[2];
        Label label;
        int acc;
        switch (flag % 4096) {
            case 0:
                label = Label.ONE_COORD;
                acc = flag / 4096;
                data[0] = acc % 64;
                break;
            case 2048:
                label = Label.ONE_COORD_TWO;
                acc = flag / 4096;
                data[0] = acc % 64;
                break;
            case 1024:
                label = Label.TWO_COORDS;
                acc = flag / 4096;
                data[0] = acc % 64;
                acc = acc / 64;
                data[1] = acc % 64;
                break;
            case 3072:
                label = Label.HELLO;
                break;
            case 512:
                label = Label.GOODBYE;
                break;
            default:
                throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case ONE_COORD:
                return 3745752 ^ (1 + (message.data[0] * 1) * 4096 + 0);
            case ONE_COORD_TWO:
                return 3745752 ^ (1 + (message.data[0] * 1) * 4096 + 2048);
            case TWO_COORDS:
                return 3745752 ^ (1 + (message.data[0] * 1 + message.data[1] * 64) * 4096 + 1024);
            case HELLO:
                return 3745752 ^ (1 + (0) * 4096 + 3072);
            case GOODBYE:
                return 3745752 ^ (1 + (0) * 4096 + 512);
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

