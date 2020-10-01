/*
package no.nav.helse

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer

class GCS {

    val gcs: KGenericContainer = KGenericContainer("fsouza/fake-gcs-server")
        .withExposedPorts(4443)
        .withClasspathResourceMapping("data", "/data", BindMode.READ_WRITE)
        .withCreateContainerCmdModifier {
            it.withEntrypoint("/bin/fake-gcs-server", "-data", "/data", "-scheme", "http")
        }

    init {
        gcs.start()
    }

    fun stop() = gcs.stop()
    fun host(): String = gcs.host
    fun port(): Int = gcs.firstMappedPort
}

class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)
*/
