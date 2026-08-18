"""Tests for :mod:`sum_even_numbers`."""

import unittest

from sum_even_numbers import sum_even_numbers


class SumEvenNumbersTest(unittest.TestCase):

    def test_mixed_values(self):
        self.assertEqual(sum_even_numbers([1, 2, 3, 4, 5, 6]), 12)

    def test_empty_list(self):
        self.assertEqual(sum_even_numbers([]), 0)

    def test_no_even_numbers(self):
        self.assertEqual(sum_even_numbers([1, 3, 5]), 0)

    def test_negative_and_zero(self):
        self.assertEqual(sum_even_numbers([-4, -3, 0, 7]), -4)

    def test_floats(self):
        self.assertEqual(sum_even_numbers([2.0, 2.5, 4]), 6.0)

    def test_non_numeric_raises(self):
        with self.assertRaises(TypeError):
            sum_even_numbers([1, "2"])

    def test_bool_raises(self):
        with self.assertRaises(TypeError):
            sum_even_numbers([True])


if __name__ == "__main__":
    unittest.main()
