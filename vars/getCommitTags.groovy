// vars/getCommitTags.groovy


def call() {
    def commitMsg = sh(
        script: "git log -1 --pretty=%B",
        returnStdout: true
    ).trim()

    def commitTags = []

    commitMsg.split("\n").each { line ->
        if (line.toLowerCase().startsWith("test-tag:")) {
            def value = line.split(":", 2)[1].trim()
            if (value) {
                commitTags.addAll(value.split())
            }
        }
    }

    return commitTags as Set
}
