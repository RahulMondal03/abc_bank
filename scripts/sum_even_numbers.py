"""Utility for summing the even numbers in a sequence."""

import math
from numbers import Real


def _to_integer(value, index):
    """Return ``value`` as an ``int``, or raise explaining why it is not one.

    Raises:
        TypeError: If ``value`` is not a real number.
        ValueError: If ``value`` is a number but has no exact integer value.
    """
    if isinstance(value, bool) or not isinstance(value, Real):
        raise TypeError(
            f"item at index {index} is not a real number: {value!r}"
        )
    if not math.isfinite(value):
        raise ValueError(
            f"item at index {index} is not a finite number: {value!r}"
        )
    if value != int(value):
        raise ValueError(
            f"item at index {index} is not an integer: {value!r}"
        )
    return int(value)


def sum_even_numbers(numbers, skip_invalid=False):
    """Return the sum of the even numbers in ``numbers``.

    Values that are numerically integral count even when they are not
    typed as ``int`` (``4.0`` is even). Anything else is invalid: strings,
    ``None``, booleans, ``nan``/``inf``, and fractional values such as
    ``2.5``.

    Args:
        numbers: An iterable of numbers. Strings and bytes are rejected
            outright rather than iterated character by character.
        skip_invalid: If ``True``, ignore invalid items instead of
            raising. Defaults to ``False``, so bad input fails loudly.

    Returns:
        The sum of the even values as an ``int``, or ``0`` when there are
        none.

    Raises:
        TypeError: If ``numbers`` is not a non-string iterable, or (when
            ``skip_invalid`` is ``False``) if an item is not a real number.
        ValueError: If ``skip_invalid`` is ``False`` and an item is a
            number with no exact integer value.
    """
    if isinstance(numbers, (str, bytes, bytearray)):
        raise TypeError(
            f"expected an iterable of numbers, got {type(numbers).__name__}"
        )
    try:
        items = enumerate(numbers)
    except TypeError:
        raise TypeError(
            f"expected an iterable of numbers, got {type(numbers).__name__}"
        ) from None

    total = 0
    for index, value in items:
        try:
            number = _to_integer(value, index)
        except (TypeError, ValueError):
            if skip_invalid:
                continue
            raise
        if number % 2 == 0:
            total += number
    return total


if __name__ == "__main__":
    print(sum_even_numbers([1, 2, 3, 4, 5, 6]))
    print(sum_even_numbers([2, "x", 4.5, 6], skip_invalid=True))
