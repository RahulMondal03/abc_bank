"""Tests for :mod:`input_validation`."""

import unittest

from input_validation import InputValidator as V


class InstantiationTest(unittest.TestCase):

    def test_cannot_instantiate(self):
        with self.assertRaises(TypeError):
            V()


class TypeChecksTest(unittest.TestCase):

    def test_require_not_none(self):
        self.assertEqual(V.require_not_none(0), 0)
        with self.assertRaises(TypeError) as ctx:
            V.require_not_none(None, "amount")
        self.assertIn("amount", str(ctx.exception))

    def test_require_type_single(self):
        self.assertEqual(V.require_type("x", str), "x")
        with self.assertRaises(TypeError) as ctx:
            V.require_type(5, str, "name")
        self.assertIn("name must be str, got int", str(ctx.exception))

    def test_require_type_tuple(self):
        self.assertEqual(V.require_type(2.5, (int, float)), 2.5)
        with self.assertRaises(TypeError) as ctx:
            V.require_type("2", (int, float))
        self.assertIn("int or float", str(ctx.exception))

    def test_require_iterable_accepts_collections(self):
        for value in ([], (1,), {1}, range(3), iter([1])):
            with self.subTest(value=value):
                self.assertIs(V.require_iterable(value), value)

    def test_require_iterable_rejects_strings_and_scalars(self):
        for value in ("abc", b"abc", bytearray(b"a"), 42, None):
            with self.subTest(value=value), self.assertRaises(TypeError):
                V.require_iterable(value)


class NonEmptyTest(unittest.TestCase):

    def test_string_is_stripped(self):
        self.assertEqual(V.require_non_empty("  hi  "), "hi")

    def test_strip_can_be_disabled(self):
        self.assertEqual(V.require_non_empty("  ", strip=False), "  ")

    def test_blank_strings_rejected(self):
        for value in ("", "   ", "\t\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                V.require_non_empty(value, "username")

    def test_collections(self):
        self.assertEqual(V.require_non_empty([1]), [1])
        for value in ([], {}, (), set()):
            with self.subTest(value=value), self.assertRaises(ValueError):
                V.require_non_empty(value)

    def test_none_and_unsized_rejected(self):
        with self.assertRaises(TypeError):
            V.require_non_empty(None)
        with self.assertRaises(TypeError):
            V.require_non_empty(5)


class NumberTest(unittest.TestCase):

    def test_require_number_accepts_reals(self):
        for value in (0, -3, 2.5, 10**20):
            with self.subTest(value=value):
                self.assertEqual(V.require_number(value), value)

    def test_require_number_rejects_non_numbers_and_bools(self):
        for value in ("1", None, True, False, [1], 1j):
            with self.subTest(value=value), self.assertRaises(TypeError):
                V.require_number(value)

    def test_require_number_non_finite(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    V.require_number(value)
                self.assertIs(V.require_number(value, allow_non_finite=True),
                              value)

    def test_require_integer(self):
        self.assertEqual(V.require_integer(4), 4)
        result = V.require_integer(4.0)
        self.assertEqual(result, 4)
        self.assertIsInstance(result, int)
        with self.assertRaises(ValueError):
            V.require_integer(4.5)
        with self.assertRaises(TypeError):
            V.require_integer("4")
        with self.assertRaises(ValueError):
            V.require_integer(float("inf"))


class RangeTest(unittest.TestCase):

    def test_inclusive_bounds(self):
        self.assertEqual(V.require_in_range(0, 0, 10), 0)
        self.assertEqual(V.require_in_range(10, 0, 10), 10)
        with self.assertRaises(ValueError) as ctx:
            V.require_in_range(11, 0, 10, "age")
        self.assertIn("age must be in [0, 10], got 11", str(ctx.exception))

    def test_exclusive_bounds(self):
        self.assertEqual(V.require_in_range(5, 0, 10, inclusive=False), 5)
        for value in (0, 10):
            with self.subTest(value=value), self.assertRaises(ValueError):
                V.require_in_range(value, 0, 10, inclusive=False)

    def test_open_ended(self):
        self.assertEqual(V.require_in_range(-1e9, maximum=0), -1e9)
        self.assertEqual(V.require_in_range(1e9, minimum=0), 1e9)
        with self.assertRaises(ValueError):
            V.require_in_range(-0.01, minimum=0, name="amount")

    def test_invalid_range_rejected(self):
        with self.assertRaises(ValueError):
            V.require_in_range(5, 10, 0)

    def test_non_number_rejected(self):
        with self.assertRaises(TypeError):
            V.require_in_range("5", 0, 10)


class ParseTest(unittest.TestCase):

    def test_parse_int_and_float(self):
        self.assertEqual(V.parse_number("42"), 42)
        self.assertIsInstance(V.parse_number("42"), int)
        self.assertEqual(V.parse_number("-3"), -3)
        self.assertEqual(V.parse_number(" 2.5 "), 2.5)
        self.assertEqual(V.parse_number("1e3"), 1000.0)

    def test_parse_rejects_bad_text(self):
        for text in ("", "  ", "abc", "1.2.3", "nan", "inf"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                V.parse_number(text)

    def test_parse_rejects_non_string(self):
        with self.assertRaises(TypeError):
            V.parse_number(5)

    def test_parse_numbers_reports_position(self):
        self.assertEqual(V.parse_numbers(["3", "1.5", "-2"]), [3, 1.5, -2])
        with self.assertRaises(ValueError) as ctx:
            V.parse_numbers(["1", "two", "3"])
        self.assertIn("argument 2", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
