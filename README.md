# pipeline-lib

Shared library for daos-stack Jenkins Pipelines

This is a shared library used with the Jenkinsfiles for the various
projects in daos-stack.

Jenkinsfiles are limited in how large they can be and Declarative pipelines
are also limited in what they can do.

This pipeline-lib is meant to run in a Jenkins Groovy Sandbox and is
intended to be specific to the daos-stack project.

Operations that are not allowed in a Jenkins Groovy Sandbox should be put
in a global pipeline library that is local to the Jenkins operation.

These include things like canceling Jenkins jobs for superceeded commits in
pull requests.

## Required Trusted Global Library Support

The [trusted-pipeline-lib]<https://github.com/daos-stack/trusted-pipeline-lib>
or a fork of it is required to provide both some useful generic building
scripts, and also for using Jenkins groovy methods that are blocked by
the Jenkins sandbox.

That library README.md file provides documentation of those routines.

## Optional System Global Library Support

The use of  the system pipeline library is intended to be optional.

The methods that reference it will test for it to be present and will not
fail the basic building procedure if it is not present.

The methods used by the pipeline-lib are either CI system specific, or need
to be customized by the local site using an additional locally maintained
pipeline library.

We will try to keep the interface to these routines stable, but may need
to change them to optimize the CI system.

### Optional Global variables

#### coverityToolDownloadSystem

The coverityToolDownloadSystem needs to be provided if you are doing
your own Coverity analysis on daos-stack builds.

Your routine will need to map the 'project' name to the your Coverity
project and add the access credentials to pull down the tool kit, and then
unpack it into the 'tool_path' specified directory.

This step takes a map of with these member names:

##### project

Coverity Project name for the daos-stack project.  This is only used
for downloading the coverity tools, so can be any project name that
you have a coverity credential ID for, so your routine can ignore this
parameter.

##### tool_path

This is the directory that the tool should be installed in.

It should return 0 if successful.

#### provisionNodesSystem

This is method for provisioning a set of nodes for your local CI system.
As we improve our CI system this may change a bit.

This method is used to install an operating system on a set of test nodes
for a CI test.

The current implementation takes a Map variable containing the following
members.

##### arch

The architecture to use.  Default is 'x86_64', currently the only one that
Jenkins is building.

##### distro

A common encoding of the distribution type and version.

The default is "centos7"

Currently supported is "centos7", "centos8", "leap15"
Future support may be for "sles15", "ubuntu20", and potentially names
with "rc" and point release suffixes.  The "rc" would be for future release
candidate, and the point release would be to test for an older point release
than the current point release.

##### NODELIST

This is a string containing a comma separated list of the nodes available for
CI use.  There may be more nodes provided in this list than the CI test
requested depending on current node allocation system.

This NODELIST is also provided as a NODELIST environment variable.

##### node_count

This is a number containing the actual count of nodes that should be
provisioned out of the NODELIST parameter above.

##### profile

This is a profile to use for provisioning.  The default and currently only
profile being used is "daos_ci".

The profile indicates which options should be installed for a distribution.

##### power_only

This optional parameter is set to true to just do a reboot the nodes
with a power cycle operation.

##### timeout

Timeout to wait for provisioning to complete.  The default that we use is
30 minutes.

If timeout is less than or equal to 0, it means that the routine should
not wait for provisioning to complete.  This would require that the CI
have a way to determine when provisioning is completed and we are not yet
using that feature, but may in the future.

##### inst_repos

Optional DAOS stack repositories that should be configured.

##### inst_rpms

Optional Additional RPMs that should be installed.

#### publishToRepositorySystem

This is a method for for saving aritfacts to a local repository such as
Nexus-oss edition.

As we improve our CI system, this may change a bit.

Your routine can map these values as needed for your repository layout.

Some of the member names below come from recomendations for Artifactory
use.

The current implementation takes a Map variable containing the following
members.

##### arch

The architecture to use.  Default is 'x86_64', currently the only one that
Jenkins is building.

##### maturity

Maturity level: eg: 'stable'|'dev'|'test'.  The default is 'test' which
is intended for a temporary test repository used to test this method.

##### product

The name of the product.

##### repo_dir

The directory to post the artifacts from.

##### tech

This is the same as the distro for node provisioning, such as "centos7".

##### test

This is for testing this method.  The intent is to create a temporary
repository and then delete it.  Default is false.

##### type

Type of repository.  Default is "hosted".

##### download_dir

Optional download directory.  If present, download the artifacts after the
upload to validate.

The publishToRepositorySystem step should download the artifacts back to
this directory and fail the step if the contents differ.
