/*
 * Copyright 2026 Hewlett Packard Enterprise Development LP
 *
 * SPDX-License-Identifier: BSD-2-Clause-Patent
 */

import static org.junit.jupiter.api.Assertions.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.jupiter.api.Test

class TestPackageCandidateArtifacts {

    private Map workspace() {
        Path root = Path.of('build', 'candidate-artifacts-' + UUID.randomUUID()).toAbsolutePath()
        Files.createDirectories(root)
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.parseClass('''
            package org.jenkinsci.plugins.workflow.steps
            class FlowInterruptedException extends Exception {}
        ''')
        Map context = [path: root, archived: root.resolve('archive'), current: root]
        Files.createDirectories(context.archived)
        Map bindings = [
            env: [JOB_NAME: 'dependency/master', BUILD_NUMBER: '17'],
            error: { String message -> throw new IllegalStateException(message) },
            sh: { Map command ->
                if (command.script.startsWith('find artifacts/') && command.script.contains('-exec rpm')) {
                    return 'candidate-1-1.x86_64|0123456789abcdef\n'
                }
                if (command.script.startsWith('. /etc/os-release')) {
                    return 'focal'
                }
                Process process = new ProcessBuilder('bash', '-c', command.script)
                    .directory(context.current.toFile()).redirectErrorStream(true).start()
                String output = process.inputStream.text
                int status = process.waitFor()
                if (status != 0) {
                    throw new IllegalStateException("Shell failed (${status}): ${output}")
                }
                command.returnStdout ? output : ''
            },
            readFile: { String name -> context.current.resolve(name).toFile().text },
            writeFile: { Map command -> context.current.resolve(command.file).toFile().text = command.text },
            specific: { String number -> number },
            archiveArtifacts: { Map command ->
                Files.list(context.current.resolve('candidates')).withCloseable { stream ->
                    stream.forEach { path ->
                        Files.copy(path, context.archived.resolve(path.fileName),
                            StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            },
            copyArtifacts: { Map command ->
                assertEquals('17', command.selector)
                Files.createDirectories(context.current.resolve('candidates'))
                Files.list(context.archived).withCloseable { stream ->
                    stream.forEach { path ->
                        Files.copy(path, context.current.resolve('candidates').resolve(path.fileName))
                    }
                }
            }
        ]
        context.script = new GroovyShell(loader, new Binding(bindings))
            .parse(new File('vars/packageCandidateValidation.groovy'))
        return context
    }

    private void removeWorkspace(Map context) {
        Files.walk(context.path).withCloseable { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private void candidate(Map context, String distro) {
        Path tree = context.path.resolve('artifacts').resolve(distro)
        Files.createDirectories(tree)
        boolean rpm = context.script.repositories()[distro].format == 'yum'
        tree.resolve(rpm ? 'candidate-1.rpm' : 'candidate_1.deb').toFile().text = 'tested candidate bytes'
        tree.resolve(rpm ? 'candidate-1.src.rpm' : 'candidate_1.dsc').toFile().text = 'source bytes'
        Path metadata = tree.resolve(rpm ? 'repodata/repomd.xml' : 'Packages.gz')
        Files.createDirectories(metadata.parent)
        metadata.toFile().text = 'tested repository metadata'
    }

    @Test
    void 'all RPM and DEB distro trees restore original archived bytes rather than mutable build workspaces'() {
        Map context = workspace()
        try {
            Map state = [candidates: [:]]
            context.script.repositories().keySet().each { distro ->
                candidate(context, distro)
                state.candidates[distro] = context.script.archiveCandidate(distro)
            }
            assertEquals('focal', state.candidates['ubuntu20.04'].distribution)
            context.path.resolve('artifacts/el8/candidate-1.rpm').toFile().text = 'changed after testing'
            context.current = Files.createDirectory(context.path.resolve('restored'))
            context.script.restore(state)
            context.script.repositories().keySet().each { distro ->
                String name = context.script.repositories()[distro].format == 'yum' ?
                    'candidate-1.rpm' : 'candidate_1.deb'
                assertEquals('tested candidate bytes',
                    context.current.resolve("artifacts/${distro}/${name}").toFile().text)
            }
        } finally {
            removeWorkspace(context)
        }
    }

    @Test
    void 'archive tampering fails the original recorded digest before extracting packages'() {
        Map context = workspace()
        try {
            candidate(context, 'el8')
            Map frozen = context.script.archiveCandidate('el8')
            context.archived.resolve('el8.tar').toFile().append('tampered')
            context.current = Files.createDirectory(context.path.resolve('restore-corrupt'))
            assertThrows(IllegalStateException) {
                context.script.restore([candidates: [el8: frozen]])
            }
            assertFalse(Files.exists(context.current.resolve('artifacts/el8/candidate-1.rpm')))
        } finally {
            removeWorkspace(context)
        }
    }

    @Test
    void 'missing source metadata or packages and symlink inputs cannot become candidates'() {
        ['source', 'metadata', 'package', 'symlink'].each { failure ->
            Map context = workspace()
            try {
                candidate(context, 'el8')
                Path tree = context.path.resolve('artifacts/el8')
                switch (failure) {
                case 'source':
                    Files.delete(tree.resolve('candidate-1.src.rpm'))
                    break
                case 'metadata':
                    Files.delete(tree.resolve('repodata/repomd.xml'))
                    break
                case 'package':
                    Files.delete(tree.resolve('candidate-1.src.rpm'))
                    Files.delete(tree.resolve('candidate-1.rpm'))
                    break
                case 'symlink':
                    Files.createSymbolicLink(tree.resolve('alias.rpm'), Path.of('candidate-1.rpm'))
                    break
                }
                assertThrows(IllegalStateException) { context.script.archiveCandidate('el8') }
                assertTrue(Files.list(context.archived).withCloseable { it.count() } == 0)
            } finally {
                removeWorkspace(context)
            }
        }
    }
}
