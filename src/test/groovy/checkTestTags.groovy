/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

package com.daos.pipeline

import spock.lang.Specification

class TagTemplateSpec extends Specification {

    def "expand tag_template by replacing @stages.tag@ with tag values in order"() {
        given: "input tags and tag_template as in the example"
        def tags = [
            [tag: 'Test-tag', value: 'line1'],
            [tag: 'Test-tag', value: 'line2'],
            [tag: 'Test-tag', value: 'line3'],
            [tag: 'Test-tag', value: 'line4'],
        ]
        def tagTemplate = 'line1,@stages.tag@ line2,@stages.tag@ ' +
            'line3,@stages.tag@ line4,@stages.tag@'

        when: "we expand the template by replacing each @stages.tag@ with the next tag.value"
        def result = expandTagTemplate(tagTemplate, tags)

        then: "the result matches the expected expansion"
        result == 'line1,line1 line2,line2 line3,line3 line4,line4'
    }

    /**
     * Replaces successive occurrences of the placeholder with successive tag values.
     * This mirrors the behavior required by the example: each @stages.tag@ is consumed
     * in order and replaced by the corresponding tags[i].value.
     */
    private String expandTagTemplate(String template, List<Map> tags) {
        def placeholder = '@stages.tag@'
        def sb = new StringBuilder(template)
        int searchFrom = 0
        tags.each { t ->
            int idx = sb.indexOf(placeholder, searchFrom)
            if (idx == -1) {
                // no more placeholders; stop replacing
                return sb.toString()
            }
            sb.replace(idx, idx + placeholder.length(), t.value)
            // move searchFrom past the inserted value to avoid re-matching inside it
            searchFrom = idx + t.value.length()
        }
        return sb.toString()
    }
}
