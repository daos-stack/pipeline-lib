// vars/runTestFunctionalV1.groovy

/**
 * runTestFunctionalV1.groovy
 *
 * runTestFunctionalV1 pipeline step
 *
 */

def call(Map config = [:]) {
  /**
   * runTestFunctionalV1 step method
   *
   * @param config Map of parameters passed
   * @return None
   *
   * config['stashes'] Stashes from the build to unstash
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   * config['pragma_suffix'] The Test-tag pragma suffix
   * config['test_tag'] The test tags to run
   * config['ftest_arg'] An argument to ftest.sh
   * config['test_rpms'] Testing using RPMs, true/false
   *
   * config['context'] Context name for SCM to identify the specific stage to
   *                   update status for.
   *                   Default is 'test/' + env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['description']  Description to report for SCM status.
   *                        Default env.STAGE_NAME.
   */

    def test_rpms = 'false'
    if (config['test_rpms'] == "true") {
        test_rpms = 'true'
    }
    def functional_test_script = '''test_tag=$(git show -s --format=%%B | sed -ne "/^Test-tag%s:/s/^.*: *//p")
                                    if [ -z "$test_tag" ]; then
                                        test_tag=%s
                                    fi
                                    tnodes=$(echo $NODELIST | cut -d ',' -f 1-%d)
                                    first_node=${NODELIST%%%%,*}
                                    clush -B -S -o '-i ci_key' -l root -w "${first_node}" "set -ex
                                      systemctl start nfs-server.service
                                      mkdir -p /export/share
                                      chown jenkins /export/share
                                      echo \\"/export/share ${NODELIST//,/(rw,no_root_squash) }(rw,no_root_squash)\\" > /etc/exports
                                      exportfs -ra"
                                    clush -B -S -o '-i ci_key' -l root -w "${tnodes}" \
                                      "set -ex
                                       for i in 0 1; do
                                           if [ -e /sys/class/net/ib\\\$i ]; then
                                               if ! ifconfig ib\\\$i | grep \"inet \"; then
                                                 {
                                                   echo \"Found interface ib\\\$i down after reboot on \\\$HOSTNAME\"
                                                   systemctl status
                                                   systemctl --failed
                                                   journalctl -n 500
                                                   ifconfig ib\\\$i
                                                   cat /sys/class/net/ib\\\$i/mode
                                                   ifup ib\\\$i
                                                 } | mail -s \"Interface found down after reboot\" $OPERATIONS_EMAIL
                                               fi
                                           fi
                                       done
                                       if ! grep /mnt/share /proc/mounts; then
                                           mkdir -p /mnt/share
                                           mount $first_node:/export/share /mnt/share
                                       fi
                                       if ''' + test_rpms + '''; then
                                           # remove the install/ dir to be sure we're testing from RPMs
                                           rm -rf install/
                                       fi"
                                    trap 'clush -B -S -o "-i ci_key" -l root -w "${tnodes}" \
                                          "set -x; umount /mnt/share"' EXIT
                                    # set DAOS_TARGET_OVERSUBSCRIBE env here
                                    export DAOS_TARGET_OVERSUBSCRIBE=1
                                    rm -rf install/lib/daos/TESTING/ftest/avocado ./*_results.xml
                                    mkdir -p install/lib/daos/TESTING/ftest/avocado/job-results
                                    ftest_arg="%s"
                                    if ''' + test_rpms + '''; then
                                        ssh -i ci_key -l jenkins "${first_node}" "set -ex
                                          DAOS_TEST_SHARED_DIR=\\$(mktemp -d -p /mnt/share/)
                                          trap \\"rm -rf \\$DAOS_TEST_SHARED_DIR\\" EXIT
                                          export DAOS_TEST_SHARED_DIR
                                          export TEST_RPMS=true
                                          export REMOTE_ACCT=jenkins
                                          /usr/lib/daos/TESTING/ftest/ftest.sh \\"$test_tag\\" \\"$tnodes\\" \\"$ftest_arg\\""
                                        # now collect up the logs and store them like non-RPM test does
                                        mkdir -p install/lib/daos/TESTING/
                                        # scp doesn't copy symlinks, it resolves them
                                        ssh -i ci_key -l jenkins "${first_node}" tar -C /var/tmp/ -czf - ftest | tar -C install/lib/daos/TESTING/ -xzf -
                                    else
                                        ./ftest.sh "$test_tag" "$tnodes" "$ftest_arg"
                                    fi'''

    config['script'] = String.format(functional_test_script,
                                     config['pragma_suffix'],
                                     config['test_tag'],
                                     config['node_count'],
                                     config['ftest_arg'])
    config['junit_files'] = "install/lib/daos/TESTING/ftest/avocado/job-results/job-*/*.xml install/lib/daos/TESTING/ftest/*_results.xml"
    config['failure_artifacts'] = 'Functional'

    if (test_rpms == 'true' && config['stashes']){
        // we don't need (and might not even have) stashes if testing
        // from RPMs
        config.remove('stashes')
    }

    config.remove('pragma_suffix')
    config.remove('test_tag')
    config.remove('ftest_arg')
    config.remove('node_count')
    config.remove('test_rpms')

    runTest(config)

}