import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class UnitTest {

	private Object loadScriptWithMocks(Map extraConfig = [:]) {

		def binding = new Binding()

		// ---- ENV ----
		binding.setVariable("env", [
			NODELIST        : "node1",
			STAGE_NAME      : "el9-gcc",
			SSH_KEY_ARGS    : ""
		])

		// ---- PIPELINE STEP MOCKS ----

		binding.setVariable("sh", { Map m ->
			if (m.returnStdout) {
				return ""
			}
			return 0
		})

		binding.setVariable("timeout", { Map m, Closure c ->
			c()
		})

		binding.setVariable("stash", { Map m -> })
		binding.setVariable("writeYaml", { Map m -> })
		binding.setVariable("httpRequest", { Map m -> })
		binding.setVariable("fileOperations", { List l -> })
		binding.setVariable("fileCopyOperation", { Map m -> [:] })
		binding.setVariable("catchError", { Map m, Closure c -> c() })
		binding.setVariable("error", { String s -> })
		binding.setVariable("echo", { String s -> })

		// ---- INTERNAL LIBRARY STEPS ----

		binding.setVariable("provisionNodes", { Map m ->
			[:]
		})

		binding.setVariable("runTest", { Map m ->
			[result_code: 0]
		})

		binding.setVariable("parseStageInfo", { Map m ->
			[
				compiler        : "gcc",
				node_count      : 1,
				target          : "el9",
				distro_version  : "9",
				ci_target       : "el9",
				build_type      : "",
				NLT             : false
			]
		})

		binding.setVariable("durationSeconds", { Long l -> 5 })
		binding.setVariable("sanitizedStageName", { -> "el9-gcc" })
		binding.setVariable("checkJunitFiles", { Map m -> "SUCCESS" })

		// pozwala nadpisać mocki w konkretnym teście
		extraConfig.each { k, v ->
			binding.setVariable(k, v)
		}

		def shell = new GroovyShell(binding)
		return shell.parse(new File("vars/unitTest.groovy"))
	}

	@Test
	void "call passes correct arguments to provisionNodes"() {

		def capturedProvisionArgs = null

		def script = loadScriptWithMocks([
			provisionNodes: { Map m ->
				capturedProvisionArgs = m
				return [:]
			},
			runTest: { Map cfg -> [result_code: 0] }
		])

		script.call([
			inst_rpms: "pkg1 pkg2"
		])

		assertNotNull(capturedProvisionArgs)
		assertEquals("node1", capturedProvisionArgs.NODELIST)
		assertEquals(1, capturedProvisionArgs.node_count)
		assertEquals("el9", capturedProvisionArgs.distro)   // image_version
		assertEquals("pkg1 pkg2", capturedProvisionArgs.inst_rpms)
	}

	@Test
	void "call builds correct p map for runTest"() {

		def capturedP = null

		def script = loadScriptWithMocks([
			runTest: { Map cfg ->
				capturedP = cfg
				return [result_code: 0]
			}
		])

		script.call([
			unstash_opt: true
		])

		assertNotNull(capturedP)

		assertEquals("test/el9-gcc", capturedP.context)
		assertEquals("unit-test-*memcheck.xml", capturedP.valgrind_pattern)
		assertEquals("ci/unit/test_main.sh", capturedP.script.split()[-1])
		assert ["el9-gcc-tests", "el9-gcc-build-vars", "el9-gcc-opt-tar"] == capturedP.stashes

		assertTrue(capturedP.ignore_failure)
		assertFalse(capturedP.notify_result)
	}

	@Test
	void "call uses provided image_version when building target_stash"() {

		def capturedP = null

		def script = loadScriptWithMocks([
			runTest: { Map cfg ->
				capturedP = cfg
				return [result_code: 0]
			}
		])

		script.call([
			image_version: "el9.7"
		])

		assertNotNull(capturedP)
		assertEquals(
			["el9-gcc-tests", "el9-gcc-build-vars", "el9-gcc-install"],
			capturedP.stashes.collect { it.toString() }
		)
	}

	@Test
	void "call uses correct timeout parameters"() {

		def capturedTimeout = null

		def script = loadScriptWithMocks([
			timeout: { Map m, Closure c ->
				capturedTimeout = m
				c()
			},
			runTest: { Map cfg -> [result_code: 0] }
		])

		script.call([
			timeout_time: 60,
			timeout_unit: "MINUTES"
		])

		assertNotNull(capturedTimeout)
		assertEquals(60, capturedTimeout.time)
		assertEquals("MINUTES", capturedTimeout.unit)
	}

	@Test
	void "call returns correct runData"() {

		def script = loadScriptWithMocks([
			runTest: { Map cfg -> [result_code: 0] },
			afterTest: { Map cfg, Map run -> run }
		])

		def result = script.call([:])

		assertEquals(0, result.result_code)
		assertEquals(5, result.unittest_time)  // durationSeconds mock
	}

	@Test
	void "call computes default image_version correctly"() {

		def capturedProvisionArgs = null

		def script = loadScriptWithMocks([
			provisionNodes: { Map m ->
				capturedProvisionArgs = m
				return [:]
			}
		])

		script.call([:])

		assertNotNull(capturedProvisionArgs)
		assertEquals("el9", capturedProvisionArgs.distro)  // image_version
	}

	@Test
	void "call uses provided image_version when given"() {

		def capturedProvisionArgs = null

		def script = loadScriptWithMocks([
			provisionNodes: { Map m ->
				capturedProvisionArgs = m
				return [:]
			}
		])

		script.call([
			image_version: "el9.7"
		])

		assertNotNull(capturedProvisionArgs)
		assertEquals("el9.7", capturedProvisionArgs.distro)
	}

}