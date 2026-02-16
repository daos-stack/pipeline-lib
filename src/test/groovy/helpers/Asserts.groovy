package helpers

class Asserts {

    static void assertIsSubset(Map subset, Map set) {
        assert subset.every { k, v -> set[k] == v } : subset + ' is not a subset of ' + set
    }
}
