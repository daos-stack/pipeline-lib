// src/com/intel/checkoutScm.groovy

package com.intel

def checkoutScmWithSubmodules() {

    checkout([$class: 'GitSCM',
        branches: scm.branches,
        doGenerateSubmoduleConfigurations: false,
        extensions: [[$class: 'SubmoduleOption',
            disableSubmodules: false,
            parentCredentials: true,
            recursiveSubmodules: true,
            reference: '',
            trackingSubmodules: false
        ]],
        submoduleCfg: [],
        userRemoteConfigs: scm.userRemoteConfigs
    ])
}
