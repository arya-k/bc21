package quals.utils;

public class UnitBuildDPQueue {
    UnitBuildQueue[] queues;
    public int index;
    public int levels;

    public UnitBuildDPQueue(int levels) {
        this.levels = levels;
        this.queues = new UnitBuildQueue[levels];
        for (int i = 0; i < levels; i++) {
            this.queues[i] = new UnitBuildQueue();
        }
        this.index = levels;
    }

    public void clear() {
        this.queues = new UnitBuildQueue[this.levels];
        for (int i = 0; i < levels; i++) {
            this.queues[i] = new UnitBuildQueue();
        }
        this.index = levels;
    }

    public void push(UnitBuild item, int level) {
        item.priority = level;
        queues[level].push(item);
        if (level < index)
            index = level;
    }

    public UnitBuild pop() {
        UnitBuild item = queues[index].pop();
        while (index != levels && queues[index].isEmpty()) {
            index++;
        }
        return item;
    }

    public UnitBuild peek() {
        return queues[index].peek();
    }

    public boolean isEmpty() {
        return index == levels;
    }

}
