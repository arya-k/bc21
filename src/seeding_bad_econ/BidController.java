package seeding_bad_econ;

import battlecode.common.GameActionException;

import static seeding_bad_econ.QueueController.influenceMinimum;
import static seeding_bad_econ.Robot.rc;

public class BidController {

    static State state = State.KeepUp;

    static int prevTeamVotes = 0;
    static int prevBid = 2;
    static int lostInARow = 0;
    static int wonInARow = 0;
    static double proportionNeeded = 0.5;
    static int winsNeeded = 751;
    static boolean lostLast = false;

    public void update() throws GameActionException {
        // information upkeep
        lostLast = rc.getRoundNum() != 0 && rc.getTeamVotes() == prevTeamVotes;

        if (prevBid != 0) {
            lostInARow = lostLast ? lostInARow + 1 : 0;
        }
        wonInARow = lostLast ? 0 : wonInARow + 1;

        winsNeeded = 751 - rc.getTeamVotes();
        proportionNeeded = winsNeeded / (1500.0 - rc.getRoundNum());

        // state transitioning
        transition();
    }

    public void bid() throws GameActionException {
        // make the next bid
        int bid = Math.min(rc.getInfluence(), state.suggestBid());
        prevBid = bid;
        prevTeamVotes = rc.getTeamVotes();
        if (bid != 0) {
            rc.bid(bid);
        }
    }

    static int bidlessBreak = 0;

    void transition() {
        if (state == State.OnABreak) {
            if (--bidlessBreak > 0)
                return;
        }

        if (lostInARow >= 5 && proportionNeeded < 0.6) {
            // take a break
            bidlessBreak = 25;
            state = State.OnABreak;
            lostInARow = 0;
            return;
        }

        if (proportionNeeded > 1.0 || proportionNeeded <= 0)
            state = State.GiveUp;
        else if (rc.getRoundNum() > 1350)
            state = State.Endgame;
        else if (rc.getInfluence() > 6 * influenceMinimum() || proportionNeeded > 0.8)
            state = State.ScaleUp;
        else
            state = State.KeepUp;
    }

    private enum State {
        OnABreak, GiveUp,
        KeepUp {
            int suggestBid() {
                int bid = Math.max(prevBid, 2);
                if (lostLast)
                    bid += (int) (Math.random() * 2) + 1;
                else if (wonInARow >= 4 && proportionNeeded < 0.55)
                    bid /= 2;
                return Math.min(bid, maxBid());
            }
        },
        ScaleUp {
            @Override
            int suggestBid() {
                int bid = Math.max(prevBid, 2);
                if (lostLast)
                    bid = (Math.max(bid, 2) * 3) / 2;
                else if (wonInARow >= 4 && proportionNeeded < 0.55)
                    bid /= 2;
                return Math.min(bid, maxBid());
            }
        },
        Endgame {
            @Override
            int suggestBid() {
                int predictedInf = rc.getInfluence() +
                        (int) (7745 - 2.0 * Math.pow(rc.getRoundNum(), 1.5) / 15);
                if (Math.random() < proportionNeeded + 0.1)
                    return Math.min(maxBid(), predictedInf / winsNeeded);
                else
                    return 0;
            }
        };

        int suggestBid() {
            return 0;
        }
    }

    /* Helper Functions */
    static int maxBid() {
        int round = rc.getRoundNum();
        int factor = round < 300 ? 15 : 7;
        if (round < 1450)
            return Math.max(Math.min(rc.getInfluence() - 2 * influenceMinimum(), rc.getInfluence() / factor), 0);
        return rc.getInfluence();
    }
}
