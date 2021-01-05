package bot;

public class Communication {
    public enum Label {
        TWO_COORDS, ONE_COORD, ONE_COORD_TWO, HELLO, GOODBYE
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
        flag--;
        int[] data = new int[2];
        Label label;
        int acc;
        if (flag % 4096 == 0) {
            label = Label.TWO_COORDS;
            acc = flag / 4096;
            data[0] = acc % 64;
            acc = acc / 64;
            data[1] = acc % 64;
        } else if (flag % 262144 == 2048) {
            label = Label.ONE_COORD;
            acc = flag / 262144;
            data[0] = acc % 64;
        } else if (flag % 262144 == 1024) {
            label = Label.ONE_COORD_TWO;
            acc = flag / 262144;
            data[0] = acc % 64;
        } else if (flag % 16777216 == 3072) {
            label = Label.HELLO;
        } else if (flag % 16777216 == 512) {
            label = Label.GOODBYE;
        } else {
            throw new RuntimeException("Attempting to decode an invalid flag");
        }
        return new Message(label, data);
    }

    public static int encode(Message message) {
        switch (message.label) {
            case TWO_COORDS:
                return 1 + (message.data[0] * 1 + message.data[1] * 64) * 4096 + 0;
            case ONE_COORD:
                return 1 + (message.data[0] * 1) * 262144 + 2048;
            case ONE_COORD_TWO:
                return 1 + (message.data[0] * 1) * 262144 + 1024;
            case HELLO:
                return 1 + (0) * 16777216 + 3072;
            case GOODBYE:
                return 1 + (0) * 16777216 + 512;
        }
        throw new RuntimeException("Attempting to encode an invalid message");
    }
}

