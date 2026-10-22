"""Utility for summing the even numbers in a list."""


def sum_even_numbers(numbers):
    """Return the sum of the even numbers in ``numbers``.

    >>> sum_even_numbers([1, 2, 3, 4, 5, 6])
    12
    >>> sum_even_numbers([-4, -3, 0, 7])
    -4
    >>> sum_even_numbers([1, 3, 5])
    0
    >>> sum_even_numbers([])
    0
    """
    return sum(n for n in numbers if n % 2 == 0)


if __name__ == "__main__":
    import doctest

    doctest.testmod(verbose=True)
