package quals.utils;

import battlecode.common.RobotType;
import quals.Communication;

public class UnitBuild {
    public RobotType type;
    public double significance;
    public int priority = 0;
    public int minInfluence = -1;
    public Communication.Message message;

    public UnitBuild(RobotType type, Communication.Message message, double significance, int minInfluence) {
        this.type = type;
        this.significance = significance;
        this.message = message;
        this.minInfluence = minInfluence;
    }
}
