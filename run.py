#!/usr/bin/env python3

import argparse
import itertools
import multiprocessing
from collections import defaultdict
from dataclasses import dataclass
from random import shuffle
from typing import List

from tabulate import tabulate
from tqdm.auto import tqdm
from trueskill import Rating, rate_1vs1


### Type definitions ###
@dataclass
class Pairing:
    a: str  # player A
    b: str  # player B
    m: str  # map


@dataclass
class Result:
    a: str  # player A
    b: str  # player B
    m: str  # map
    w: str  # either A, B, or T (for tie)
    f: str  # save file


### Globals ###
PLAYERS = None
MAPS = None
VERBOSE = False


### Info gathering ###
def get_players() -> List[str]:
    """ Returns a list of all players we can use """
    global PLAYERS
    if PLAYERS is None:
        # TODO: this
        PLAYERS = ["bot", "sprint", "seeding"]
    return PLAYERS


def get_maps() -> List[str]:
    """ Returns a list of all maps that we can play on """
    global MAPS
    if MAPS is None:
        # TODO: this
        MAPS = ["star", "square", "triangle"]
    return MAPS


### Running games ###
def tournament_pairings(teamA, teamB, maps, games_per_match) -> List[Pairing]:
    """
    All possible pairings of a, b, and m on a given map, where a != b.
    Note that "a plays b" and "b plays a" are considered separate cases.
    games_per_match determines how many games are played for each pairing.
    """

    return [
        Pairing(a, b, m)
        for a, b, m, _ in itertools.product(teamA, teamB, maps, range(games_per_match))
        if a != b  # don't run against yourself
    ]


def run_match(pairing: Pairing) -> Result:
    """ Runs the match, returning the result. """
    # TODO: this
    if VERBOSE:
        tqdm.write("Verbose enabled!")
    return Result(pairing.a, pairing.b, pairing.m, "A", "/dev/null")


def run_matches(teamA, teamB, maps, games_per_match=1, stats=True) -> List[Result]:
    """ Runs all the matches, returning results. Order is NOT guaranteed. """
    pairings = tournament_pairings(teamA, teamB, maps, games_per_match)

    # TODO: maybe find the server jar here?

    cores = multiprocessing.cpu_count()
    print("\033[93m" + f"Running {len(pairings)} matches on {cores} cores." + "\033[0m")

    results = []
    with tqdm(total=len(pairings)) as progress:
        with multiprocessing.Pool(processes=cores) as p:
            for result in p.imap_unordered(run_match, pairings):
                progress.update()
                results.append(result)

    print()
    if stats:
        print_stats(teamA, teamB, maps, results)


### Final Stats ###
def print_winrate_matrix(teamA, teamB, results: List[Result]):
    """
    Prints a matrix of win rates between one team and other teams...
    """
    wins = defaultdict(lambda: defaultdict(int))
    totals = defaultdict(lambda: defaultdict(int))

    for result in results:
        totals[result.a][result.b] += 1
        totals[result.b][result.a] += 1
        if result.w == "A":
            wins[result.a][result.b] += 1
        elif result.w == "B":
            wins[result.b][result.a] += 1

    data = [teamB.copy()]
    for a in teamA:
        data.append(
            [a]
            + [
                f"{100 * wins[a][b] / totals[a][b]:.2f}%" if totals[a][b] else "-"
                for b in teamB
            ]
        )
    print(tabulate(data, headers="firstrow", tablefmt="presto"))


def print_ratings(results: List[Result]):
    """ Calculates trueskill ratings for each player """
    ratings = defaultdict(Rating)

    shuffle(results)
    for result in results:
        a, b = result.a, result.b

        ratings[a], ratings[b] = {
            "T": rate_1vs1(ratings[a], ratings[b], drawn=True),
            "A": rate_1vs1(ratings[a], ratings[b]),
            "B": reversed(rate_1vs1(ratings[b], ratings[a])),
        }[
            result.w
        ]  # poor man's switch statement

    data = [["bot", "rating", "stdev"]]
    for player in sorted(ratings.keys(), key=lambda p: -ratings[p].mu):
        data.append(
            [player, f"{ratings[player].mu:.2f}", f"{ratings[player].sigma:.2f}"]
        )

    print(tabulate(data, headers="firstrow", tablefmt="presto"))


def print_winrate_by_map(bot, maps, results: List[Result]):
    """ Computes the winrate of the bot based on the map """
    wins = defaultdict(int)
    totals = defaultdict(int)

    for result in results:
        if bot not in (result.a, result.b):
            continue

        totals[result.m] += 1
        if (result.w == "A" and bot == result.a) or (
            result.w == "B" and bot == result.b
        ):
            wins[result.m] += 1

    data = [["map", "winrate"]]
    for map in maps:
        data.append(
            [map, f"{100 * wins[map] / totals[map]:.2f}%" if totals[map] else "-"]
        )

    print(tabulate(data, headers="firstrow", tablefmt="presto"))


def print_stats(teamA, teamB, maps, results):
    """ Prints the stats in various digestible formats """

    if len(teamA) == 1 or len(teamB) == 1:
        bot = teamA[0] if len(teamA) == 1 else teamB[0]

        print("\033[93m" + f"--- Win Rate of {bot} by Map ---" + "\033[0m\n")
        print_winrate_by_map(bot, maps, results)
        print()

    print("\033[93m" + "--- Trueskill Ratings ---" + "\033[0m\n")
    print_ratings(results)
    print()

    print("\033[93m" + "--- Win Rate Matrix ---" + "\033[0m\n")
    print_winrate_matrix(teamA, teamB, results)
    print()


### Argument Parsing + Main functionality ###
def main():
    ALL_PLAYERS = get_players()
    ALL_MAPS = get_maps()

    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", help="print game logs", action="store_true")
    parser.add_argument(
        "-m",
        "--maps",
        help="Which map(s) to use",
        nargs="+",
        choices=ALL_MAPS,
        default=ALL_MAPS,
    )
    parser.add_argument(
        "-a",
        "--teamA",
        help="Which bot(s) can play as team A",
        nargs="+",
        choices=ALL_PLAYERS,
        default=ALL_PLAYERS,
    )
    parser.add_argument(
        "-b",
        "--teamB",
        help="Which bot(s) can play as team B",
        nargs="+",
        choices=ALL_PLAYERS,
        default=ALL_PLAYERS,
    )
    parser.add_argument(
        "-n",
        "--gamesPerMatch",
        help="Number of games played at each pairing",
        type=int,
        default=1,
    )
    args = parser.parse_args()

    # Set the verbose flag
    global VERBOSE
    VERBOSE = args.verbose

    print(f"teamA: {args.teamA}\nteamB: {args.teamB}\nmaps: {args.maps}\n")
    run_matches(args.teamA, args.teamB, args.maps, args.gamesPerMatch)


if __name__ == "__main__":
    main()
