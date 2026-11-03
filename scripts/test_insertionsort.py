"""Tests for :mod:`insertionsort`."""

import contextlib
import io
import unittest

from insertionsort import insertion_sort, main


class InsertionSortTest(unittest.TestCase):

    def test_sorts(self):
        self.assertEqual(insertion_sort([5, 2, 9, 1, 5, 6]), [1, 2, 5, 5, 6, 9])

    def test_empty_and_single(self):
        self.assertEqual(insertion_sort([]), [])
        self.assertEqual(insertion_sort([7]), [7])

    def test_does_not_modify_input(self):
        data = [3, 1, 2]
        insertion_sort(data)
        self.assertEqual(data, [3, 1, 2])


class MainTest(unittest.TestCase):

    def run_main(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main(["insertionsort.py", *args])
        return code, out.getvalue(), err.getvalue()

    def test_sorts_arguments(self):
        code, out, _ = self.run_main("3", "-1", "2.5")
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "-1 2.5 3")

    def test_no_arguments_prints_usage(self):
        code, out, _ = self.run_main()
        self.assertEqual(code, 1)
        self.assertIn("Usage", out)

    def test_invalid_argument_reports_error(self):
        for bad in ("abc", "", "nan"):
            with self.subTest(bad=bad):
                code, out, err = self.run_main("1", bad)
                self.assertEqual(code, 1)
                self.assertEqual(out, "")
                self.assertIn("argument 2", err)


if __name__ == "__main__":
    unittest.main()
