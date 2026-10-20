"""Insertion sort implementation.

Usage:
    python insertionsort.py 5 2 9 1 5 6
"""

import sys


def insertion_sort(items):
    """Return a new list containing ``items`` sorted in ascending order.

    The input is not modified. Runs in O(n^2) time in the worst case and
    O(n) when the input is already sorted. The sort is stable.
    """
    result = list(items)
    for i in range(1, len(result)):
        key = result[i]
        j = i - 1
        while j >= 0 and result[j] > key:
            result[j + 1] = result[j]
            j -= 1
        result[j + 1] = key
    return result


def main(argv):
    if len(argv) < 2:
        print(__doc__.strip())
        return 1
    try:
        numbers = [float(x) if "." in x else int(x) for x in argv[1:]]
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(" ".join(str(n) for n in insertion_sort(numbers)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
