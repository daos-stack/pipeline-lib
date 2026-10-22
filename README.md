# pipeline-lib

Shared library for daos-stack Jenkins Pipelines

This is a shared library used with the Jenkinsfile in the various
projects in daos-stack.

## Dependency package candidate validation

[`packageBuildingPipelineDAOSTest`](vars/packageBuildingPipelineDAOSTest.groovy)
uses the same mandatory validation/publication policy for every caller. There is
no opt-in. Other packaging entry points are not migrated.

The pipeline builds and archives all configured RPM/SRPM and DEB candidates,
including repository metadata, before starting DAOS **master, release/2.6, and
release/2.8** validation. Per-distro build success never uploads stable packages.
Caller distro lists, test tags, and `skip-build` settings remain unchanged.
`skip-build: false` rebuilds DAOS; the Go toolchain caller retains this setting.
Prebuilt consumers run compatible functional coverage without claiming source
build, unit, NLT, or RPM coverage that the downstream pipeline skips.

Parameter selection is discovered from each test branch's Jenkinsfile. Master
and release/2.8 use modern stage booleans; release/2.6 uses legacy parameters.
Both the hardware parent and every discovered hardware/cluster-box child are
explicitly disabled. Synthetic commits also contain hardware skip pragmas.
Before its top-level `pipeline` executes, each synthetic Jenkinsfile verifies
the upstream job/build cause and the exact pinned repository parameter. Branch
indexing, webhooks, timers, manual/default launches, and unrelated upstream
builds return before allocating an agent or evaluating hardware-enabled
downstream defaults. Removing timer triggers alone would not provide this
protection.
Fault-injection testing remains excluded. VM stages are selected only for
compatible candidates: EL9/Leap15/Ubuntu20.04 on modern branches and
EL8/EL9/Leap15/Ubuntu20.04 on release/2.6. SLES is not inferred from Leap packages;
Ubuntu rolling is not treated as Ubuntu20.04. A configuration with no compatible
VM distro on any required branch fails closed. Legacy CentOS7 and rolling DEB
artifacts are retained and published with the configured candidate set, but no
unsupported downstream stage is invented to claim coverage for them.

Every downstream receives `CI_PR_REPOS=project@upstream-branch:build-number`,
including PR candidates, so build images and test nodes resolve this build's
archived candidates rather than stable/latest packages. Additional `PR-repos`
cannot shadow this candidate. All branch builds use `propagate: false`; all
completed results are collected. Explicit upstream cancellation is rethrown.
Missing, skipped, aborted, unstarted, empty-test, or unverifiable required
coverage cannot authorize publication. Required DAOS build stages must succeed,
even when a test-failure override is requested.

The temporary downstream Jenkinsfile archives source revisions, repository
selection, and stage results. JUnit's exported enclosing stage names and executed
test counts are checked as well: a green pipeline with no matching tests is not
sufficient. Go validation sets `GOTOOLCHAIN=local` and checks the installed
compiler's candidate RPM identity, package digest, and version inside each
DAOS SCons build container. Legacy mock build logs are checked for downloaded
Go toolchains. Compiler evidence is archived as `candidate-compiler-*.txt`.
Go validation also checks the completed downstream console for Go toolchain
download messages, including Docker image-build output where the pipeline's
environment does not control Dockerfile `RUN` commands. A detected download or
unavailable console blocks publication and cannot be manually overridden.
Ubuntu candidates require the caller's prebuilt-DAOS mode: the inspected
branches have no enabled Ubuntu source/package rebuild path, so a caller
requesting Ubuntu plus DAOS rebuild fails explicitly rather than claiming
unsupported rebuild coverage.

### Publication and manual approval

Automatic stable publication requires all three validations to succeed.
Completed test `FAILURE`/`UNSTABLE` results with complete execution evidence may
instead reach a **separate, post-test** Jenkins input. The wait is executor-free,
expires after 24 hours, and requires a nonblank justification and an identified
authorized approver. Denial, cancellation, expiry, or a blank justification
publishes nothing. Approved publication does not turn failed validation green.
PRs and branches other than the caller's `publish_branch` never publish and
never receive a publication-override prompt.

Manual override is disabled unless a Jenkins administrator configures
`DAOS_DEPENDENCY_APPROVERS` as a nonempty comma-separated list of authorized
Jenkins user/group IDs. The pipeline passes that policy to the input step's
`submitter` restriction and records `submitterParameter` as the approval actor.
Jenkins administrators retain Jenkins' normal input permissions. The actual
authorized IDs/group membership and the 24-hour policy must be verified by the
site administrator before enabling the live override; this repository does not
assume that a GitHub team name is a Jenkins authorization group.

Complete candidate trees are frozen in archived tar files with SHA-256
manifests. The build is kept to retain candidates and evidence through testing,
approval, and controller restart. Integrity is checked before approval and
again in a fresh publication directory. Publication uses only the current
build's archived bytes, not surviving build workspaces or a rebuild. The
existing repository publishers and their repository locks are reused; RPM and
both DEB routes are retained, including the captured DEB distribution codename.
Missing site publisher configuration is an error, not a successful no-op.

Concurrent builds of the same upstream job queue for the entire pipeline,
including approval, so an older waiting build cannot publish after a newer
build of that job. Validation branches are build-unique. A product publication
lock additionally serializes this pipeline's publication attempts; the existing
publishers retain their repository-level locks.

`dependency-validation.json` is archived after every branch, approval change,
and repository attempt. It records upstream/candidate/source identity,
downstream URLs/results, selected coverage, test counts, approval actor/reason,
and per-distro publication status. Multi-repository upload is **not atomic**:
some repositories or files may have been uploaded when another upload fails.
Interrupted/in-progress or failed uploads must be treated as potentially partial.
No existing package is deleted to roll back. Any upload retry must reuse the
same frozen candidate set and recheck its hashes; rebuilding creates a new
candidate that requires new validation and, if necessary, a new approval.

Temporary SRE-4139 draft wiring in this repository's own Jenkinsfile pins
`pipeline-lib@ryon-jensen/sre-4139-candidate-validation` and unconditionally
disables its entire `DAOS Build and Test` integration matrix before any matrix
cell can create a legacy downstream branch. Local/JUnit checks remain enabled.
This is draft-only wiring, not the final landing configuration: remove the
self-library pin and review restoration of the matrix separately before landing.
The Go draft exercises the new guarded VM-only candidate-validation path.

Local Groovy/JUnit tests cover policy, parameter dialects, workflow gating,
and exact archive restoration. Jenkins Declarative/CPS validation, actual
downstream execution, mock/compiler logs, authorization, and real repository
uploads still require separately authorized CI validation before rollout.

A Jenkinsfile is limited in how large they can be and Declarative pipelines
are also limited in what they can do.

This pipeline-lib is meant to run in a Jenkins Groovy Sandbox and is
intended to be specific to the daos-stack project.

Operations that are not allowed in a Jenkins Groovy Sandbox should be put
in a global pipeline library that is local to the Jenkins operation.

These include things like canceling Jenkins jobs for superseded commits in
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

The default is "el8"

Currently supported is "el8", "el9", and "leap15"
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

This is a method for for saving artifacts to a local repository such as
Nexus-oss edition.

As we improve our CI system, this may change a bit.

Your routine can map these values as needed for your repository layout.

Some of the member names below come from recommendations for Artifactory
use.

The current implementation takes a Map variable containing the following
members.

##### publishToRepository parameter arch

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

## Unit tests

Requirements:

- JDK

### Install Gradle

It is easier if we ensure everyone uses the same version of Gradle, including in CI.

**Note**: We decided not to use `gradlew` to avoid committing a binary blob to the repository.

From the main directory, run:

```sh
./gradle-init.sh
```

If it succeeds, you will find a `gradle` symbolic link in the main directory, which you can use like a normal Gradle installation.

**Note**: You can provide a custom location of the Gradle distribution by setting the `GRADLE_URL` environment variable.

### Gradle commands

The [build.gradle](build.gradle) file makes use of Gradle plugins and dependency repositories and it can access them at various points when executing any of the Gradle commands. By default it uses standard public repositories. If necessary, custom repositories can be provided by setting the `GRADLE_PLUGINS_URL` and `MAVEN_CENTRAL_URL` environment variables.

#### Run unit tests

From the main directory, run:

```bash
./gradle test
```

#### Apply formatting

**Note**: Not all Groovy files are covered yet. You are very welcome to add more coverage. See [build.gradle](build.gradle).

```sh
./gradle spotlessApply
```
