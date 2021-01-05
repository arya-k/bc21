package bot;

import battlecode.common.*;

@SuppressWarnings("unused")
public strictfp class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        Robot robot = null;
        try {
            Robot.init(rc);
            switch (rc.getType()) {
                case ENLIGHTENMENT_CENTER: robot = new EnlightenmentCenter(); break;
                case POLITICIAN:           robot = new Politician();          break;
                case SLANDERER:            robot = new Slanderer();           break;
                case MUCKRAKER:            robot = new Muckraker();           break;
            }
            robot.onAwake();
        } catch (Exception e) {
            System.out.println("Exception in " + rc.getType());
            e.printStackTrace();
        }

        while (true) {
            try {
                while(true) {
                    robot.onUpdate();
                }
            } catch (Exception e) {
                System.out.println("Exception in " + rc.getType());
                e.printStackTrace();
            }
        }
    }
}