from sum_of_evens import sum_of_evens


def test_mixed():
    assert sum_of_evens([1, 2, 3, 4, 5, 6]) == 12


def test_no_evens():
    assert sum_of_evens([1, 3, 5]) == 0


def test_empty():
    assert sum_of_evens([]) == 0


def test_negative():
    assert sum_of_evens([-4, -3, 2]) == -2
