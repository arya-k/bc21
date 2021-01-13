package refactor;

import battlecode.common.GameActionException;

import static refactor.EnlightenmentCenter.influenceMinimum;
import static refactor.Robot.rc;

public class BidController {

    static State state = State.KeepUp;

    static int prevTeamVotes = 0;
    static int prevBid = 2;
    static int lostInARow = 0;
    static boolean lostLast = false;

    public void update() throws GameActionException {
        // information upkeep
        lostLast = rc.getRoundNum() != 0 && rc.getTeamVotes() == prevTeamVotes;
        if (prevBid != 0) {
            lostInARow = lostLast ? lostInARow + 1 : 0;
        }

        // state transitioning
        transition();
    }

    public void bid() throws GameActionException {
        // make the next bid
        int bid = state.suggestBid();
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

        if (lostInARow >= 4 && rc.getRoundNum() < 1500) {
            // take a break
            bidlessBreak = 50;
            state = State.OnABreak;
            return;
        }

        boolean beAggressive = (
                rc.getRoundNum() > 2500 ||
                (rc.getInfluence() > 3 * influenceMinimum()) ||
                (1.0 * rc.getTeamVotes() / rc.getRoundNum() < 0.2 && rc.getRoundNum() > 700)
        );
        state = beAggressive ? State.ScaleUp : State.KeepUp;
    }

    private enum State {
        OnABreak,
        KeepUp {
            int suggestBid() {
                int bid = prevBid;
                if (lostLast)
                    bid += (int) (Math.random() * 4) + 1;
                return Math.min(bid, maxBid());
            }
        },
        ScaleUp {
            @Override
            int suggestBid() {
                int bid = prevBid;
                if (lostLast)
                    bid = (bid * 3) / 2;
                return Math.min(bid, maxBid());
            }
        };

        int suggestBid() {
            return 0;
        }
    }

    /* Helper Functions */
    static int maxBid() {
        return Math.max(Math.min(rc.getInfluence() - 2*influenceMinimum(), rc.getInfluence() / 5), 0);
    }
}
