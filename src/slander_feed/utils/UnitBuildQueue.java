package slander_feed.utils;

public class UnitBuildQueue {
    UnitBuild[] elements = new UnitBuild[64];
    int mask = 63;
    public int head;
    public int tail;

    public UnitBuildQueue(){

    }

    public void push(UnitBuild e) {
        elements[tail & mask] = e;
        if (tail++ - head == mask)
            doubleCapacity();
    }

    public int size() {
        return tail - head;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public UnitBuild pop() {
        return elements[head++ & mask];
    }

    public UnitBuild peek() {
        return elements[head & mask];
    }

    private void doubleCapacity() {
        head &= mask;
        tail &= mask;
        assert head == tail;
        int p = head;
        int n = elements.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");
        UnitBuild[] a = (UnitBuild[]) new Object[newCapacity];
        System.arraycopy(elements, p, a, 0, r);
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
        mask = elements.length - 1;
    }
}
