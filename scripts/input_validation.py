"""Reusable input validation helpers.

``InputValidator`` groups small, composable checks that either return the
validated (and possibly normalised) value or raise an exception with a clear
message naming the offending input. Errors follow Python conventions:

* ``TypeError`` when a value has the wrong type.
* ``ValueError`` when a value has the right type but an unacceptable value
  (empty, out of range, not finite, not integral, unparsable text).

Example::

    from input_validation import InputValidator as V

    age = V.require_in_range(V.require_integer(raw_age, "age"), 0, 150, "age")
    numbers = V.parse_numbers(sys.argv[1:])
"""

import math
from collections.abc import Iterable, Sized
from numbers import Real


class InputValidator:
    """Collection of static validation helpers. Not meant to be instantiated."""

    def __init__(self):
        raise TypeError("InputValidator only provides static methods")

    # ------------------------------------------------------------------ types

    @staticmethod
    def require_not_none(value, name="value"):
        """Return ``value``, or raise ``TypeError`` if it is ``None``."""
        if value is None:
            raise TypeError(f"{name} must not be None")
        return value

    @staticmethod
    def require_type(value, expected, name="value"):
        """Return ``value`` if it is an instance of ``expected``.

        ``expected`` may be a type or a tuple of types, as for ``isinstance``.
        """
        if not isinstance(value, expected):
            if isinstance(expected, tuple):
                wanted = " or ".join(t.__name__ for t in expected)
            else:
                wanted = expected.__name__
            raise TypeError(
                f"{name} must be {wanted}, got {type(value).__name__}: {value!r}"
            )
        return value

    @staticmethod
    def require_iterable(value, name="value"):
        """Return ``value`` if it is an iterable that is not a string/bytes.

        Strings are rejected so that ``"123"`` is not silently treated as a
        sequence of characters.
        """
        if isinstance(value, (str, bytes, bytearray)) or not isinstance(
            value, Iterable
        ):
            raise TypeError(
                f"{name} must be a non-string iterable, "
                f"got {type(value).__name__}"
            )
        return value

    # -------------------------------------------------------------- emptiness

    @staticmethod
    def require_non_empty(value, name="value", strip=True):
        """Return ``value`` if it is not empty.

        Strings are considered empty when blank (after ``strip()`` unless
        ``strip`` is ``False``); other sized values when ``len(value) == 0``.
        Returns the stripped string for string input.
        """
        InputValidator.require_not_none(value, name)
        if isinstance(value, str):
            text = value.strip() if strip else value
            if not text:
                raise ValueError(f"{name} must not be empty")
            return text
        if not isinstance(value, Sized):
            raise TypeError(
                f"{name} must be a string or sized collection, "
                f"got {type(value).__name__}"
            )
        if len(value) == 0:
            raise ValueError(f"{name} must not be empty")
        return value

    # ---------------------------------------------------------------- numbers

    @staticmethod
    def require_number(value, name="value", allow_non_finite=False):
        """Return ``value`` if it is a real number (``bool`` is rejected).

        ``nan`` and infinities are rejected unless ``allow_non_finite``.
        """
        if isinstance(value, bool) or not isinstance(value, Real):
            raise TypeError(f"{name} is not a real number: {value!r}")
        if not allow_non_finite and not math.isfinite(value):
            raise ValueError(f"{name} is not a finite number: {value!r}")
        return value

    @staticmethod
    def require_integer(value, name="value"):
        """Return ``value`` as an ``int`` if it is numerically integral.

        Integral floats such as ``4.0`` are accepted and converted; ``4.5``,
        non-finite values, strings and booleans are rejected.
        """
        InputValidator.require_number(value, name)
        if value != int(value):
            raise ValueError(f"{name} is not an integer: {value!r}")
        return int(value)

    @staticmethod
    def require_in_range(value, minimum=None, maximum=None, name="value",
                         inclusive=True):
        """Return ``value`` if it lies within ``[minimum, maximum]``.

        Either bound may be ``None`` to leave that side open. With
        ``inclusive=False`` the bounds themselves are rejected.
        """
        InputValidator.require_number(value, name)
        if minimum is not None and maximum is not None and minimum > maximum:
            raise ValueError(
                f"invalid range for {name}: minimum {minimum!r} "
                f"exceeds maximum {maximum!r}"
            )
        low_ok = minimum is None or (
            value >= minimum if inclusive else value > minimum
        )
        high_ok = maximum is None or (
            value <= maximum if inclusive else value < maximum
        )
        if not (low_ok and high_ok):
            left = "[" if inclusive else "("
            right = "]" if inclusive else ")"
            low = "-inf" if minimum is None else repr(minimum)
            high = "inf" if maximum is None else repr(maximum)
            raise ValueError(
                f"{name} must be in {left}{low}, {high}{right}, got {value!r}"
            )
        return value

    # ------------------------------------------------------------ text input

    @staticmethod
    def parse_number(text, name="value"):
        """Parse ``text`` (e.g. a command-line argument) into a number.

        Returns an ``int`` for integer literals and a ``float`` otherwise.
        Blank text, unparsable text, ``nan`` and infinities are rejected.
        """
        InputValidator.require_type(text, str, name)
        stripped = InputValidator.require_non_empty(text, name)
        try:
            return int(stripped)
        except ValueError:
            pass
        try:
            number = float(stripped)
        except ValueError:
            raise ValueError(f"{name} is not a number: {text!r}") from None
        return InputValidator.require_number(number, name)

    @staticmethod
    def parse_numbers(texts, name="argument"):
        """Parse every item of ``texts`` with :meth:`parse_number`.

        Error messages identify the failing item by its position.
        """
        InputValidator.require_iterable(texts, name + "s")
        return [
            InputValidator.parse_number(text, f"{name} {index}")
            for index, text in enumerate(texts, start=1)
        ]
