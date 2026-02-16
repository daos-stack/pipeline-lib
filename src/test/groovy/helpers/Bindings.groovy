/*
 * (C) Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

package helpers

import groovy.lang.Binding

class Bindings {

    static void commonBindings(Binding binding) {
        binding.setVariable("sh", { Map m ->
            if (m.returnStdout) {
                return ""
            }
            return 0
        })

        binding.setVariable("timeout", { Map m, Closure c ->
            c()
        })

        binding.setVariable("stash", { Map m -> })
        binding.setVariable("writeYaml", { Map m -> })
        binding.setVariable("httpRequest", { Map m -> })
        binding.setVariable("fileOperations", { List l -> })
        binding.setVariable("fileCopyOperation", { Map m -> [:] })
        binding.setVariable("catchError", { Map m, Closure c -> c() })
        binding.setVariable("error", { String s -> })
        binding.setVariable("echo", { String s -> })
    }
}
