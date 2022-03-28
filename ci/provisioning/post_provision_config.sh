#!/bin/bash

set -eux

export PS4='+ ${HOSTNAME%%.*}:${BASH_SOURCE:+$BASH_SOURCE:}$LINENO:${FUNCNAME:+$FUNCNAME():} '

rm -f ci_key*
ssh-keygen -N "" -f ci_key
cat << "EOF" > ci_key_ssh_config
host wolf-*
    CheckHostIp no
    StrictHostKeyChecking no
    UserKnownHostsFile /dev/null
    LogLevel error
EOF

# shellcheck disable=SC1091
source ci/provisioning/post_provision_config_common_functions.sh

: "${DISTRO:=EL_7}"
DSL_REPO_var="DAOS_STACK_${DISTRO}_LOCAL_REPO"
DSG_REPO_var="DAOS_STACK_${DISTRO}_GROUP_REPO"
DSA_REPO_var="DAOS_STACK_${DISTRO}_APPSTREAM_REPO"
: "${DAOS_STACK_EL_7_LOCAL_REPO:=}"
: "${DAOS_STACK_EL_7_GROUP_REPO:=}"
: "${DAOS_STACK_EL_7_APPSTREAM_REPO:=}"
: "${CONFIG_POWER_ONLY:=}"
: "${INST_RPMS:=}"
: "${INST_REPOS:=}"
: "${GPG_KEY_URLS:=}"
: "${REPOSITORY_URL:=}"
: "${JENKINS_URL:=}"

retry_cmd 300 clush -B -S -l root -w "$NODESTRING" -c ci_key* --dest=/tmp/

retry_cmd 2400 clush -B -S -l root -w "$NODESTRING" \
           "MY_UID=$(id -u)
           CONFIG_POWER_ONLY=$CONFIG_POWER_ONLY
           INST_REPOS=\"$INST_REPOS\"
           INST_RPMS=\$(eval echo $INST_RPMS)
           GPG_KEY_URLS=\"$GPG_KEY_URLS\"
           REPOSITORY_URL=\"$REPOSITORY_URL\"
           JENKINS_URL=\"$JENKINS_URL\"
           DAOS_STACK_LOCAL_REPO=\"${!DSL_REPO_var}\"
           DAOS_STACK_GROUP_REPO=\"${!DSG_REPO_var:-}\"
           DAOS_STACK_EL_8_APPSTREAM_REPO=\"${!DSA_REPO_var:-}\"
           DISTRO=\"$DISTRO\"
           DAOS_STACK_RETRY_DELAY_SECONDS=\"${DAOS_STACK_RETRY_DELAY_SECONDS}\"
           DAOS_STACK_RETRY_COUNT=\"${DAOS_STACK_RETRY_COUNT}\"
           BUILD_URL=\"${BUILD_URL}\"
           STAGE_NAME=\"${STAGE_NAME}\"
           OPERATIONS_EMAIL=\"${OPERATIONS_EMAIL}\"
           $(cat ci/provisioning/post_provision_config_common_functions.sh)
           $(cat ci/provisioning/post_provision_config_nodes_"${DISTRO}".sh)
           $(cat ci/provisioning/post_provision_config_nodes.sh)"
