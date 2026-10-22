"""Utility for summing the even numbers in a list."""


def sum_even_numbers(numbers):
    """Return the sum of the even numbers in ``numbers``.

    Every element must be an integer. Booleans are rejected even though
    they subclass ``int``, as are floats such as ``4.0``.

    >>> sum_even_numbers([1, 2, 3, 4, 5, 6])
    12
    >>> sum_even_numbers([-4, -3, 0, 7])
    -4
    >>> sum_even_numbers([1, 3, 5])
    0
    >>> sum_even_numbers([])
    0
    >>> sum_even_numbers([2, 4.0])
    Traceback (most recent call last):
        ...
    TypeError: element at index 1 is float, expected int: 4.0
    >>> sum_even_numbers([2, "4"])
    Traceback (most recent call last):
        ...
    TypeError: element at index 1 is str, expected int: '4'
    >>> sum_even_numbers([True, 2])
    Traceback (most recent call last):
        ...
    TypeError: element at index 0 is bool, expected int: True
    >>> sum_even_numbers(None)
    Traceback (most recent call last):
        ...
    TypeError: numbers must be a list or tuple, got NoneType
    """
    if not isinstance(numbers, (list, tuple)):
        raise TypeError(
            f"numbers must be a list or tuple, got {type(numbers).__name__}"
        )

    for index, n in enumerate(numbers):
        if not isinstance(n, int) or isinstance(n, bool):
            raise TypeError(
                f"element at index {index} is {type(n).__name__}, "
                f"expected int: {n!r}"
            )

    return sum(n for n in numbers if n % 2 == 0)


if __name__ == "__main__":
    import doctest

    doctest.testmod(verbose=True)
