/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uchuhimo.konf.source

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.properties.PropertiesProvider
import com.uchuhimo.konf.tempFileOf
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import spark.Service
import java.net.URL
import java.util.concurrent.TimeUnit

object DefaultLoadersSpec : SubjectSpek<DefaultLoaders>({
    subject {
        Config {
            addSpec(DefaultLoadersConfig)
        }.withSourceFrom
    }

    val item = DefaultLoadersConfig.type

    given("a loader") {
        on("load from system environment") {
            val config = subject.env()
            it("should return a config which contains value from system environment") {
                assertThat(config[item], equalTo("env"))
            }
        }
        on("load from system properties") {
            System.setProperty(DefaultLoadersConfig.qualify("type"), "system")
            val config = subject.systemProperties()
            it("should return a config which contains value from system properties") {
                assertThat(config[item], equalTo("system"))
            }
        }
        on("dispatch loader based on extension") {
            it("should return the corresponding loader") {
                assertThat(subject.dispatchExtension("conf"), equalTo(subject.hocon))
                assertThat(subject.dispatchExtension("json"), equalTo(subject.json))
                assertThat(subject.dispatchExtension("properties"), equalTo(subject.properties))
                assertThat(subject.dispatchExtension("toml"), equalTo(subject.toml))
                assertThat(subject.dispatchExtension("xml"), equalTo(subject.xml))
                assertThat(subject.dispatchExtension("yml"), equalTo(subject.yaml))
                assertThat(subject.dispatchExtension("yaml"), equalTo(subject.yaml))
            }
            it("should throw UnsupportedExtensionException when the extension is unsupported") {
                assertThat({ subject.dispatchExtension("txt") }, throws<UnsupportedExtensionException>())
            }
            it("should return the corresponding loader when the extension is registered") {
                DefaultLoaders.registerExtension("txt", PropertiesProvider)
                assertThat(subject.dispatchExtension("txt"), equalTo(subject.properties))
            }
        }
        on("load from URL") {
            val service = Service.ignite()
            service.port(0)
            service.get("/source.properties") { _, _ -> propertiesContent }
            service.awaitInitialization()
            val config = subject.url(URL("http://localhost:${service.port()}/source.properties"))
            it("should load as auto-detected URL format") {
                assertThat(config[item], equalTo("properties"))
            }
            service.stop()
        }
        on("load from URL string") {
            val service = Service.ignite()
            service.port(0)
            service.get("/source.properties") { _, _ -> propertiesContent }
            service.awaitInitialization()
            val config = subject.url("http://localhost:${service.port()}/source.properties")
            it("should load as auto-detected URL format") {
                assertThat(config[item], equalTo("properties"))
            }
            service.stop()
        }
        on("load from file") {
            val config = subject.file(tempFileOf(propertiesContent, suffix = ".properties"))
            it("should load as auto-detected file format") {
                assertThat(config[item], equalTo("properties"))
            }
        }
        on("load from file path") {
            val file = tempFileOf(propertiesContent, suffix = ".properties")
            val config = subject.file(file.path)
            it("should load as auto-detected file format") {
                assertThat(config[item], equalTo("properties"))
            }
        }
        on("load from watched file") {
            newSingleThreadContext("context").use { context ->
                val file = tempFileOf(propertiesContent, suffix = ".properties")
                val config = subject.watchFile(file, 1, context = context)
                val originalValue = config[item]
                file.writeText(propertiesContent.replace("properties", "newValue"))
                runBlocking(context) {
                    delay(1, TimeUnit.SECONDS)
                }
                val newValue = config[item]
                it("should load as auto-detected file format") {
                    assertThat(originalValue, equalTo("properties"))
                }
                it("should load new value when file has been changed") {
                    assertThat(newValue, equalTo("newValue"))
                }
            }
        }
        on("load from watched file path") {
            newSingleThreadContext("context").use { context ->
                val file = tempFileOf(propertiesContent, suffix = ".properties")
                val config = subject.watchFile(file.path, 1, context = context)
                val originalValue = config[item]
                file.writeText(propertiesContent.replace("properties", "newValue"))
                runBlocking(context) {
                    delay(1, TimeUnit.SECONDS)
                }
                val newValue = config[item]
                it("should load as auto-detected file format") {
                    assertThat(originalValue, equalTo("properties"))
                }
                it("should load new value when file has been changed") {
                    assertThat(newValue, equalTo("newValue"))
                }
            }
        }
        on("load from watched URL") {
            newSingleThreadContext("context").use { context ->
                var content = propertiesContent
                val service = Service.ignite()
                service.port(0)
                service.get("/source.properties") { _, _ -> content }
                service.awaitInitialization()
                val url = "http://localhost:${service.port()}/source.properties"
                val config = subject.watchUrl(URL(url), context = context)
                val originalValue = config[item]
                content = propertiesContent.replace("properties", "newValue")
                runBlocking(context) {
                    delay(5, TimeUnit.SECONDS)
                }
                val newValue = config[item]
                it("should load as auto-detected URL format") {
                    assertThat(originalValue, equalTo("properties"))
                }
                it("should load new value after URL content has been changed") {
                    assertThat(newValue, equalTo("newValue"))
                }
            }
        }
        on("load from watched URL string") {
            newSingleThreadContext("context").use { context ->
                var content = propertiesContent
                val service = Service.ignite()
                service.port(0)
                service.get("/source.properties") { _, _ -> content }
                service.awaitInitialization()
                val url = "http://localhost:${service.port()}/source.properties"
                val config = subject.watchUrl(url, context = context)
                val originalValue = config[item]
                content = propertiesContent.replace("properties", "newValue")
                runBlocking(context) {
                    delay(5, TimeUnit.SECONDS)
                }
                val newValue = config[item]
                it("should load as auto-detected URL format") {
                    assertThat(originalValue, equalTo("properties"))
                }
                it("should load new value after URL content has been changed") {
                    assertThat(newValue, equalTo("newValue"))
                }
            }
        }
    }
    on("load from multiple sources") {
        val afterLoadEnv = subject.env()
        System.setProperty(DefaultLoadersConfig.qualify("type"), "system")
        val afterLoadSystemProperties = afterLoadEnv.withSourceFrom
            .systemProperties()
        val afterLoadHocon = afterLoadSystemProperties.withSourceFrom
            .hocon.string(hoconContent)
        val afterLoadJson = afterLoadHocon.withSourceFrom
            .json.string(jsonContent)
        val afterLoadProperties = afterLoadJson.withSourceFrom
            .properties.string(propertiesContent)
        val afterLoadToml = afterLoadProperties.withSourceFrom
            .toml.string(tomlContent)
        val afterLoadXml = afterLoadToml.withSourceFrom
            .xml.string(xmlContent)
        val afterLoadYaml = afterLoadXml.withSourceFrom
            .yaml.string(yamlContent)
        val afterLoadFlat = afterLoadYaml.withSourceFrom
            .map.flat(mapOf("source.test.type" to "flat"))
        val afterLoadKv = afterLoadFlat.withSourceFrom.map
            .kv(mapOf("source.test.type" to "kv"))
        val afterLoadHierarchical = afterLoadKv.withSourceFrom.map
            .hierarchical(
                mapOf("source" to
                    mapOf("test" to
                        mapOf("type" to "hierarchical"))))
        it("should load the corresponding value in each layer") {
            assertThat(afterLoadEnv[item], equalTo("env"))
            assertThat(afterLoadSystemProperties[item], equalTo("system"))
            assertThat(afterLoadHocon[item], equalTo("conf"))
            assertThat(afterLoadJson[item], equalTo("json"))
            assertThat(afterLoadProperties[item], equalTo("properties"))
            assertThat(afterLoadToml[item], equalTo("toml"))
            assertThat(afterLoadXml[item], equalTo("xml"))
            assertThat(afterLoadYaml[item], equalTo("yaml"))
            assertThat(afterLoadFlat[item], equalTo("flat"))
            assertThat(afterLoadKv[item], equalTo("kv"))
            assertThat(afterLoadHierarchical[item], equalTo("hierarchical"))
        }
    }
})

private object DefaultLoadersConfig : ConfigSpec("source.test") {
    val type by required<String>()
}

private const val hoconContent = "source.test.type = conf"

private const val jsonContent = """
{
  "source": {
    "test": {
      "type": "json"
    }
  }
}
"""

private const val propertiesContent = "source.test.type = properties"

private const val tomlContent = """
[source.test]
type = "toml"
"""

private val xmlContent = """
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property>
        <name>source.test.type</name>
        <value>xml</value>
    </property>
</configuration>
""".trim()

private const val yamlContent = """
source:
    test:
        type: yaml
"""
