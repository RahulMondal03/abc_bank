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

    def test_integral_floats_count(self):
        self.assertEqual(sum_even_numbers([2.0, 3.0, 4]), 6)

    def test_accepts_any_iterable(self):
        self.assertEqual(sum_even_numbers(iter([2, 3, 4])), 6)
        self.assertEqual(sum_even_numbers(range(1, 7)), 12)


class ValidationTest(unittest.TestCase):

    def test_non_numeric_item_raises_type_error(self):
        with self.assertRaises(TypeError) as ctx:
            sum_even_numbers([2, "4", 6])
        self.assertIn("index 1", str(ctx.exception))

    def test_none_item_raises_type_error(self):
        with self.assertRaises(TypeError):
            sum_even_numbers([2, None])

    def test_bool_item_raises_type_error(self):
        with self.assertRaises(TypeError):
            sum_even_numbers([True])

    def test_fractional_value_raises_value_error(self):
        with self.assertRaises(ValueError) as ctx:
            sum_even_numbers([2, 4.5])
        self.assertIn("index 1", str(ctx.exception))

    def test_non_finite_values_raise_value_error(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                sum_even_numbers([2, value])

    def test_string_argument_rejected(self):
        with self.assertRaises(TypeError):
            sum_even_numbers("2468")

    def test_non_iterable_argument_rejected(self):
        with self.assertRaises(TypeError):
            sum_even_numbers(42)

    def test_skip_invalid_ignores_bad_items(self):
        self.assertEqual(
            sum_even_numbers([2, "x", None, 4.5, float("nan"), 6],
                             skip_invalid=True),
            8,
        )

    def test_skip_invalid_does_not_excuse_bad_argument(self):
        with self.assertRaises(TypeError):
            sum_even_numbers("2468", skip_invalid=True)


if __name__ == "__main__":
    unittest.main()
