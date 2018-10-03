// vars/sconsBuild.groovy

import com.intel.checkoutScm

def call(Map config) {

    def c = new com.intel.checkoutScm()
    c.checkoutScmWithSubmodules()

    sh '''if git show -s --format=%B | grep "^Skip-build: true"; then
              exit 0
          fi
          scons -c
          # scons -c is not perfect so get out the big hammer
          rm -rf _build.external install build
          SCONS_ARGS="--update-prereq=all --build-deps=yes USE_INSTALLED=all install"
          # the config cache is unreliable so always force a reconfig
          # with "--config=force"
          if ! scons --config=force $SCONS_ARGS; then
              echo "$SCONS_ARGS failed"
              rc=\${PIPESTATUS[0]}
              cat config.log || true
              exit \$rc
          fi'''
}
