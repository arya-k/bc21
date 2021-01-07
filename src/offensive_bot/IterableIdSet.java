package bot;

public class IterableIdSet {
    private int size = 0;
    private StringBuilder keys = new StringBuilder();

    private String idToStr(int id) {
        return "^" + (char)(id % 256) + (char)((id >> 8) % 256) + (char)((id >> 16) % 256) + (char)((id >> 24) % 256);
    }
    private int ixToId(int i) {
        return ((int) keys.charAt(i)) + (((int) keys.charAt(i + 1)) << 8) + (((int) keys.charAt(i + 2)) << 16) + (((int) keys.charAt(i + 3)) << 24);
    }

    public void add(int id) {
        String key = idToStr(id);
        if (keys.indexOf(key) == -1) {
            keys.append(key);
            size++;
        }
    }

    public void remove(int id) {
        String key = idToStr(id);
        int index;
        if ((index = keys.indexOf(key)) != -1) {
            keys.delete(index, index+5);
            size--;
        }
    }

    public boolean contains(int id) {
        return keys.indexOf(idToStr(id)) != -1;
    }

    public void clear() {
        keys = new StringBuilder();
        size = 0;
    }

    public int[] getKeys() {
        int[] ids = new int[size];
        for (int i = 0; i < size; i++) {
            ids[i] = ixToId(i*5 + 1);
        }
        return ids;
    }

    public void replace(String newSet) {
        keys.replace(0, keys.length(), newSet);
        size = newSet.length() / 5;
    }
}


