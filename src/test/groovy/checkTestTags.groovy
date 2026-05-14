/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

package com.daos.pipeline

import static org.junit.jupiter.api.Assertions.*
import spock.lang.Specification

class TagTemplateSpec extends Specification {

    static final String stage_name = 'Functional on Leap 15'

    private Script loadScript(String script, Map extraBinding = [:]) {
        Binding binding = new Binding()

        // override bindings as required for a specific test
        extraBinding.each { k, v ->
            binding.setVariable(k, v)
        }

        GroovyShell shell = new GroovyShell(binding)
        return shell.parse(new File("vars/${script}.groovy"))
    }

    private Script loadPragmasToEnv() {
        Closure pragmasToMapWrap = { String commit_message ->
            Script pragmasToMap = loadScript('pragmasToMap')
            return pragmasToMap.call(commit_message)
        }

        Map extraBinding = [
            pragmasToMap: pragmasToMapWrap,
            env: [:]
        ]

        return loadScript('pragmasToEnv', extraBinding)
    }

    private Script loadParseStageInfo(Map extraBinding) {
        extraBinding.distroVersion = { String distro ->
            return 'XXA'
        }
        extraBinding.cachedCommitPragma = { String a, String b ->
            return 'XXB'
        }
        extraBinding.paramsValue = { String a, String b ->
            return 'XXC'
        }
        extraBinding.error = { String a ->}
        extraBinding.getPragmaSuffix = {-> return 'XXD' }

        Closure getFunctionalStageTagsWrap = { ->
            Script getFunctionalStageTags = loadScript('getFunctionalStageTags', [
                env: extraBinding.env,
            ])
            return getFunctionalStageTags.call()
        }
        extraBinding.getFunctionalStageTags = getFunctionalStageTagsWrap
        extraBinding.startedByTimer = {-> return false }
        extraBinding.branchTypeRE = { String a -> return 'XXE' }

        Closure getPragmaSuffixWrap = { ->
            Script getPragmaSuffix = loadScript('getPragmaSuffix', [
                env: extraBinding.env,
            ])
            return getPragmaSuffix.call()
        }
        Closure envToPragmasWrap = { ->
            Script envToPragmas = loadScript('envToPragmas', [
                env: extraBinding.env,
            ])
            return envToPragmas.call()
        }
        Closure commitPragmaWrap = {String a, String b ->
            Script commitPragma = loadScript('commitPragma', [
                env: extraBinding.env,
                envToPragmas: envToPragmasWrap
            ])
            return commitPragma.call(a, b)
        }
        extraBinding.getFunctionalTags = { Map kwargs ->
            Script getFunctionalTags = loadScript('getFunctionalTags', [
                getPragmaSuffix: getPragmaSuffixWrap,
                getFunctionalStageTags: getFunctionalStageTagsWrap,
                startedByUser: {-> return false },
                startedByUpstream: {-> return false },
                startedByTimer: {-> return false },
                commitPragma: commitPragmaWrap,
                getSkippedTests: {-> return [] }
            ])
            return getFunctionalTags.call(kwargs)
        }
        extraBinding.getFunctionalArgs = { LinkedHashMap a -> return [:] }
        return loadScript('parseStageInfo', extraBinding)
    }

    /**
     * Replaces successive occurrences of the placeholder with successive tag values.
     * This mirrors the behavior required by the example: each @stages.tag@ is consumed
     * in order and replaced by the corresponding tags[i].value.
     */
    private String expandTagTemplate(List<Map> tags) {
        String cm = '''\
                Test commit\n'''
        tags.each { tag ->
            cm += """\

                ${tag.tag}: ${tag.value}"""
        }
        // print('Running test with commit:\n' + cm)
        // assign Map to env. var to serialize it
        Script pragmasToEnv = loadPragmasToEnv()
        String tmp_pragmas = pragmasToEnv.call(cm.stripIndent())
        Map env = [
            STAGE_NAME: stage_name,
            UNIT_TEST: 'true',
            pragmas: tmp_pragmas,
            COMMIT_MESSAGE: cm.stripIndent(),
            RELEASE_BRANCH: 'master'
        ]
        Script parseStageInfo = loadParseStageInfo([env: env])
        Map info = parseStageInfo.call()
        // print(info)
        return info['test_tag']
    }

    def "expand tag_template by replacing @stages.tag@ with tag values in order"() {
        given: "input tags and tag_template as in the example"
        def tags = [
            [tag: 'Test-tag', value: 'line1'],
            [tag: 'Test-tag', value: 'line2'],
            [tag: 'Test-tag', value: 'line3'],
            [tag: 'Test-tag', value: 'line4'],
        ]
        def tagTemplate = 'line1,vm line2,vm line3,vm line4,vm'

        def result = expandTagTemplate(tags)
        assertEquals(result, tagTemplate)
    }
}
