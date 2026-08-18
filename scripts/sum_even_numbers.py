"""Utility for summing the even numbers in a sequence."""

from numbers import Real


def sum_even_numbers(numbers):
    """Return the sum of the even numbers in ``numbers``.

    Args:
        numbers: An iterable of numbers. Integers and floats with no
            fractional part (e.g. ``4.0``) are eligible; odd values and
            non-integral floats (e.g. ``2.5``) are ignored.

    Returns:
        The sum of the even values, or ``0`` when there are none.

    Raises:
        TypeError: If any item is not a real number.
    """
    total = 0
    for value in numbers:
        if isinstance(value, bool) or not isinstance(value, Real):
            raise TypeError(f"expected a real number, got {value!r}")
        if value % 2 == 0:
            total += value
    return total


if __name__ == "__main__":
    print(sum_even_numbers([1, 2, 3, 4, 5, 6]))
