"""Merge sort implementation.

Merge sort is a stable, divide-and-conquer sorting algorithm with a
guaranteed time complexity of O(n log n) and space complexity of O(n).
"""


def merge_sort(items):
    """Return a new sorted list containing the elements of ``items``.

    The input list is left unmodified.

    Args:
        items: A list of comparable elements.

    Returns:
        A new list with the elements sorted in ascending order.
    """
    if len(items) <= 1:
        return list(items)

    mid = len(items) // 2
    left = merge_sort(items[:mid])
    right = merge_sort(items[mid:])
    return _merge(left, right)


def _merge(left, right):
    """Merge two already-sorted lists into a single sorted list."""
    merged = []
    i = j = 0

    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            merged.append(left[i])
            i += 1
        else:
            merged.append(right[j])
            j += 1

    merged.extend(left[i:])
    merged.extend(right[j:])
    return merged


if __name__ == "__main__":
    sample = [38, 27, 43, 3, 9, 82, 10]
    print("Unsorted:", sample)
    print("Sorted:  ", merge_sort(sample))
