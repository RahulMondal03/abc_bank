def sum_of_evens(numbers):
    """Return the sum of the even numbers in a list."""
    return sum(n for n in numbers if n % 2 == 0)
