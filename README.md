# pipeline-lib

Shared library for daos-stack Jenkins Pipelines

This is a shared library used with the Jenkinsfiles for the various
projects in daos-stack.

Jenkinsfiles are limited in how large they can be and Declarative pipelines
are also limited in what they can do.

## Optional System Library Support

This pipeline-lib is meant to run in a Jenkins Groovy Sandbox.

Operations that are not allowed in a Jenkins Groovy Sandbox should be put
in a trusted system pipeline library that is local to the Jenkins operation.

These include things like canceling Jenkins jobs for superceeded commits in
pull requests.

The use of a trusted system pipeline library is intended to be optional.

The methods that reference it will test for it to be present and will not
fail the basic building procedure if it is not present.

Some methods used by the pipeline-lib are either CI system specific, or need
to be customized by the local site using an additional locally maintained
pipeline library.

This includes things like notifying the specific git repository being used
for buiding the daos-stack projects.

The use of this locally maintained library is also intended to be optional.

### cancelPreviousBuildsSystem

This is a method that can be implemented to cancel builds when there is
a newer commit for a pull request.

There is fallback code in pipeline-lib that would need some routines
whitelisted.  If that code is moved to a trusted system groovy library
than white listing is not needed.

### coverityToolDownloadSystem

The coverityToolDownloadSystem needs to be provided if you are doing
your own Coverity analysis on daos-stack builds.

Your routine will need to map the 'project' name to the your Coverity
project and add the access credentials to pull down the tool kit, and then
unpack it into the 'tool_path' specified directory.

This method takes a Map variable containing two parameters.

~~~groovy
   config['project']        // Coverity Project Name from daos-stack
                            // project.
   config['tool_path']      // Directory to install tool in.
~~~

It should return 0 if successful.

### provisionNodesSystem

This is method for provisioning a set of nodes for your local CI system.
As we improve our CI system this may change a bit.

This method is used to install an operating system on a set of test nodes
for a CI test.

The current implementation takes a Map variable containing the following
members.

~~~groovy
   config['arch']       // Architecture to use.  Default 'x86_64'
   config['distro']     // Distribution to use.  Default 'el7'
   config['NODELIST']   // Comma separated list of nodes available.
   config['node_count'] // Optional lower number of nodes to provision.
   config['profile']    // Profile to use.  Default 'daos_ci'.
   config['power_only'] // Only power cycle the nodes, do not provision.
   config['timeout']    // Timeout in minutes.  Default 30.
   config['inst_repos'] // DAOS stack repositories that should be configured.
   config['inst_rpms']  // DAOS stack RPMs that should be installed.
   // if timeout is <= 0, then will not wait for provisioning.
   // if reboot_only is specified, the nodes will be rebooted and the
   // provisioning information ignored.
~~~

### publishToRepositorySystem

This is a method for for saving aritfacts to a local repository such as
Nexus-oss edition.

Your routine can map these values as needed for your repository.

This takes a Map variable containing the following members.

~~~groovy
   config['arch']         // Architecture, default 'x86_64'.
   config['maturity']     // Maturity level: eg: 'stable'|'dev'|'test'.
                          // Default 'test'.
   config['product']      // Name of product.
   config['repo_dir']     // Directory to post artifacts from.
   config['tech']         // Distro/version code for reposiory eg: 'el7'
   config['test']         // Test by creating a temporary repo and then
                          // deleting it.
                          // default false.  Used to unit test this step.
   config['type']         // Type of repository.  Default 'hosted'.
   config['download_dir'] // If present, download the artifacts after the
                          // upload to validate.
                          // The publishToRepositorySystem step should download
                          // the artifacts back to this directory and fail the
                          // step if the contents differ.
~~~

### scmNotifySystem

This should be provided for notifications to your SCM system.
It should take the same parameters as the githubNotify pipeline step.

You will need to provide your routine with the credentials needed to
notify your SCM system.
