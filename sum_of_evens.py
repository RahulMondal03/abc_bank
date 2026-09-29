def sum_of_evens(numbers):
    """Return the sum of the even integers in a list.

    Raises:
        TypeError: if the input is not a list/tuple, or if any element
            is not an integer (booleans are rejected too).
    """
    if not isinstance(numbers, (list, tuple)):
        raise TypeError(f"expected a list of integers, got {type(numbers).__name__}")
    for n in numbers:
        if isinstance(n, bool) or not isinstance(n, int):
            raise TypeError(f"all elements must be integers, got {n!r} ({type(n).__name__})")
    return sum(n for n in numbers if n % 2 == 0)
