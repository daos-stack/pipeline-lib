/*
 * (C) Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

package helpers

class Asserts {

    static void assertIsSubset(Map subset, Map set) {
        assert subset.every { k, v -> set[k] == v } : subset + ' is not a subset of ' + set
    }
}
