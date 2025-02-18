package sprint_plus;

public class UnitBuildDPQueue {
    UnitBuildQueue[] queues;
    int index;
    int levels;

    public UnitBuildDPQueue(int levels) {
        this.levels = levels;
        this.queues = new UnitBuildQueue[levels];
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
