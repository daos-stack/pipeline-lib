#!/usr/bin/env bash

set +x

readonly pragma_suffix="$1"
readonly default_test_tag="$2"
readonly node_count="$3"
readonly ftest_arg="$4"
readonly test_rpms="$5"

if [ "$#" -ne 5 ]; then
    echo "Usage: $0 PRAGMA_SUFFIX TEST_TAG NODE_COUNT FTEST_ARG TEST_RPMS" >&2
    exit 2
fi

collect_functional_artifacts() {
    local remote_host="$1"
    local destination="install/lib/daos/TESTING"
    local max_attempts="${ARTIFACT_RETRY_COUNT:-3}"
    local retry_delay="${ARTIFACT_RETRY_DELAY_SECONDS:-10}"
    local attempt=1

    while [ "$attempt" -le "$max_attempts" ]; do
        echo "Collecting functional test artifacts: attempt ${attempt}/${max_attempts}"

        # Remove files left by an incomplete extraction.
        rm -rf "${destination}/ftest"
        mkdir -p "$destination"

        if (
            set -o pipefail

            # scp resolves symbolic links, so stream the archive over SSH.
            ssh -i ci_key -l jenkins "$remote_host" \
                tar -C /var/tmp/ -czf - ftest |
                tar -C "$destination" -xzf -
        ); then
            echo "Functional test artifacts collected successfully"
            return 0
        fi

        echo "Artifact collection attempt ${attempt}/${max_attempts} failed" >&2

        if [ "$attempt" -lt "$max_attempts" ]; then
            echo "Retrying in ${retry_delay} seconds"
            sleep "$retry_delay"
        fi

        attempt=$((attempt + 1))
    done

    echo "Failed to collect functional test artifacts after ${max_attempts} attempts" >&2
    return 1
}

test_tag=$(
    git show -s --format=%B |
        sed -ne "/^Test-tag${pragma_suffix}:/s/^.*: *//p"
)

if [ -z "$test_tag" ]; then
    test_tag="$default_test_tag"
fi

tnodes=$(echo "$NODELIST" | cut -d ',' -f "1-${node_count}")
first_node=${NODELIST%%,*}

clush -B -S -o '-i ci_key' -l root -w "$first_node" \
    "set -ex
     systemctl start nfs-server.service
     mkdir -p /export/share
     chown jenkins /export/share
     echo \"/export/share ${NODELIST//,/(rw,no_root_squash) }(rw,no_root_squash)\" > /etc/exports
     exportfs -ra"

clush -B -S -o '-i ci_key' -l root -w "$tnodes" \
    "set -ex
     for i in 0 1; do
         if [ -e /sys/class/net/ib\$i ]; then
             if ! ifconfig ib\$i | grep \"inet \"; then
                 {
                     echo \"Found interface ib\$i down after reboot on \$HOSTNAME\"
                     systemctl status
                     systemctl --failed
                     journalctl -n 500
                     ifconfig ib\$i
                     cat /sys/class/net/ib\$i/mode
                     ifup ib\$i
                 } | mail -s \"Interface found down after reboot\" \"$OPERATIONS_EMAIL\"
             fi
         fi
     done

     if ! grep /mnt/share /proc/mounts; then
         mkdir -p /mnt/share
         mount $first_node:/export/share /mnt/share
     fi

     if $test_rpms; then
         # Remove install/ to ensure that the tests use RPM packages.
         rm -rf install/
     fi"

trap 'clush -B -S -o "-i ci_key" -l root -w "$tnodes" \
    "set -x; umount /mnt/share"' EXIT

export DAOS_TARGET_OVERSUBSCRIBE=1

rm -rf install/lib/daos/TESTING/ftest/avocado ./*_results.xml
mkdir -p install/lib/daos/TESTING/ftest/avocado/job-results

if "$test_rpms"; then
    ssh -i ci_key -l jenkins "$first_node" \
        "set -ex
         DAOS_TEST_SHARED_DIR=\$(mktemp -d -p /mnt/share/)
         trap \"rm -rf \$DAOS_TEST_SHARED_DIR\" EXIT
         export DAOS_TEST_SHARED_DIR
         export TEST_RPMS=true
         export REMOTE_ACCT=jenkins
         /usr/lib/daos/TESTING/ftest/ftest.sh \"$test_tag\" \"$tnodes\" \"$ftest_arg\""

    collect_functional_artifacts "$first_node"
else
    ./ftest.sh "$test_tag" "$tnodes" "$ftest_arg"
fi
