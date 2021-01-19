import math
import random
import argparse
import os
import textwrap
import itertools as it
import inspect
import sys
from collections import defaultdict
from dataclasses import dataclass

from tabulate import tabulate
from strategy import Strategy


### Type definitions ###
@dataclass
class Player:
    strategy: type  # the strategy
    name: str  # the name of the strategy
    args: list  # a list of parameters


@dataclass
class Result:
    a: str  # player A
    b: str  # player B
    winnner: str  # either A, B, or TIE
    a_pct: float  # A's win percentage
    b_pct: float  # B's win percentage


### Wrapper to manage a group of strategy objects ###
class Manager:
    def __init__(self, player, ecs, wealth_factors=None):
        self.player = player
        self.ecs = [player.strategy(*player.args) for _ in range(ecs)]
        self.infs = [150 for _ in range(ecs)]
        self.wealth_factors = (
            wealth_factors
            if wealth_factors is not None
            else [DEFAULT_WEALTH for _ in range(ecs)]
        )
        self.round = 0
        self.votes = 0

    def tick(self):
        max_bid = 0
        max_bidder = -1
        for e in range(len(self.ecs)):
            bid = self.get_bid(e)
            if bid > max_bid:
                max_bid = bid
                max_bidder = e
        self.last_bid = max_bid
        self.last_bidder = max_bidder
        return self.last_bid

    def get_bid(self, i):
        bid = self.ecs[i].bid(self.round, self.infs[i], self.votes)
        bid = max(min(bid, self.infs[i]), 0)
        return max(min(bid, self.infs[i]), 0)

    def won_last(self, won):
        if won:
            self.infs[self.last_bidder] -= self.last_bid
            self.votes += 1
        elif self.last_bid != 0:
            self.infs[self.last_bidder] -= math.ceil(self.last_bid / 2)

    def next_round(self):
        self.round += 1
        for e, wf in enumerate(self.wealth_factors):
            # an approximate of how much influence you gain from slanderers
            self.infs[e] += int((wf * self.infs[e] / (1 - wf)) * 0.025)
            # base influence gain
            self.infs[e] += math.ceil(0.2 * math.sqrt(self.round))


### Constants for wealth calculation ###
DEFAULT_WEALTH = 0.15
MAX_WEALTH = 0.3
SIGMA = MAX_WEALTH / 10

### Constants for designating a winner ###
A = "A"
B = "B"
TIE = "TIE"


def run_game(
    player_a, player_b, a_wealth_mu=DEFAULT_WEALTH, b_wealth_mu=DEFAULT_WEALTH, ecs=1
):
    def random_wealth(mu):
        return max(min(random.gauss(mu, SIGMA), MAX_WEALTH), 0)

    a = Manager(
        player_a, ecs, wealth_factors=[random_wealth(a_wealth_mu) for _ in range(ecs)]
    )
    b = Manager(
        player_b, ecs, wealth_factors=[random_wealth(b_wealth_mu) for _ in range(ecs)]
    )
    a_votes = 0
    b_votes = 0
    for r in range(1500):
        # get the bids
        a_bid = a.tick()
        b_bid = b.tick()
        # notify the winner
        a.won_last(a_bid > b_bid)
        b.won_last(b_bid > a_bid)
        if a_bid > b_bid:
            a_votes += 1
        elif b_bid > a_bid:
            b_votes += 1
        # proceed to the next round
        a.next_round()
        b.next_round()
    if a_votes > b_votes:
        return A
    elif b_votes > a_votes:
        return B
    else:
        return TIE


def linspace(a, b, n):
    if n < 2:
        return b
    diff = (b - a) / (n - 1)
    for i in range(n):
        yield diff * i + a


def run_match(player_a, player_b):
    MIN_MU = 0.12
    MAX_MU = MAX_WEALTH - MIN_MU
    STEPS = 3
    RUNS_EACH = 3
    EC_VALS = (2, 3, 4)

    print(f"Starting {player_a.name} vs. {player_b.name}")

    a_wins = 0
    b_wins = 0
    games = 0
    for a_wealth_mu in linspace(MIN_MU, MAX_MU, STEPS):
        for b_wealth_mu in linspace(MIN_MU, MAX_MU, STEPS):
            for ecs in EC_VALS:
                for run in range(RUNS_EACH):
                    winner = run_game(
                        player_a,
                        player_b,
                        a_wealth_mu=a_wealth_mu,
                        b_wealth_mu=b_wealth_mu,
                        ecs=ecs,
                    )
                    if winner == A:
                        a_wins += 1
                    elif winner == B:
                        b_wins += 1
                    games += 1

    winner = TIE
    if a_wins > b_wins:
        winner = A
    elif b_wins > a_wins:
        winner = B
    a_pct, b_pct = (a_wins / games, b_wins / games)

    return Result(player_a.name, player_b.name, winner, a_pct, b_pct)


def run_matches(players):
    results = [run_match(a, b) for a, b in it.combinations(players, 2)]
    print_stats(players, results)


### Statistics ###
def print_stats(players, results):
    totals = defaultdict(lambda: defaultdict(float))
    for r in results:
        totals[r.a][r.b] = r.a_pct
        totals[r.b][r.a] = r.b_pct

    data = [[p.name for p in players]]
    for a in players:
        data.append(
            [a.name]
            + [
                f"{100 * totals[a.name][b.name]:.2f}%" if a.name != b.name else "-"
                for b in players
            ]
        )
    print()
    print(tabulate(data, headers="firstrow", tablefmt="presto"))


### Argument Parsing ###
def all_player_names():
    dir_path = os.path.dirname(os.path.realpath(__file__))
    return [
        f[:-3]
        for f in os.listdir(dir_path)
        if os.path.isfile(os.path.join(dir_path, f))
        and f.endswith(".py")
        and not __file__.endswith(f)
    ]


def names_to_players(default_names, param_names):
    class_strs = it.chain(
        zip(default_names, it.repeat(False)), zip(param_names, it.repeat(True))
    )
    players = []
    for s, params in class_strs:
        module = __import__(s)
        class_members = inspect.getmembers(
            sys.modules[module.__name__], inspect.isclass
        )
        strategy = None
        for member_name, member_type in class_members:
            if Strategy in member_type.mro() and (
                strategy is None or member_name != Strategy.__name__
            ):
                strategy = member_type

        assert strategy != None, f"{s}.py must define a class that extends Strategy"

        assert "DEFAULT" in dir(module), f"{s}.py must define a DEFAULT params list"
        assert "PARAMS" in dir(module), f"{s}.py must define a PARAMS list"

        if params:
            for param_list in it.product(*module.PARAMS):
                name = f"{s} ({str(list(param_list))[1:-1]})" if param_list else s
                players.append(Player(strategy, name, param_list))
        else:
            players.append(Player(strategy, s, module.DEFAULT))
    return players


def main():
    ALL_PLAYER_NAMES = all_player_names()

    parser = argparse.ArgumentParser(
        description="""
        A Bidding Simulator for MIT Battlecode 2021. See constant.py for an example strategy implementation.
        """
    )
    parser.add_argument(
        "-d",
        "--default_players",
        help="Which players to use only the default initialization of",
        nargs="+",
        choices=ALL_PLAYER_NAMES,
        default=[],
    )
    parser.add_argument(
        "-p",
        "--param_players",
        help="Which players to use all possible variations of",
        nargs="+",
        choices=ALL_PLAYER_NAMES,
        default=[],
    )
    args = parser.parse_args()

    players = names_to_players(args.default_players, args.param_players)
    run_matches(players)


if __name__ == "__main__":
    main()
