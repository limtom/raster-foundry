package com.rasterfoundry.common

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import net.spy.memcached.ClientMode

import scala.concurrent.duration._

object Config {
  private val config = ConfigFactory.load()

  object awsbatch {
    private val awsBatchConfig = config.getConfig("awsbatch")
    val jobQueue = awsBatchConfig.getString("jobQueue")
    val ingestJobQueue = awsBatchConfig.getString("ingestJobQueue")

    val ingestJobName = awsBatchConfig.getString("ingestJobName")
    val importJobName = awsBatchConfig.getString("importJobName")
    val exportJobName = awsBatchConfig.getString("exportJobName")
    val aoiUpdateJobName = awsBatchConfig.getString("aoiUpdateJobName")

    val environment = awsBatchConfig.getString("environment")
  }

  object memcached {
    private lazy val memcachedConfig = config.getConfig("memcached")

    lazy val host: String =
      memcachedConfig.getString("host")

    lazy val port: Int =
      memcachedConfig.getInt("port")

    lazy val clientMode: ClientMode =
      if (memcachedConfig.getBoolean("dynamicClientMode")) {
        ClientMode.Dynamic
      } else {
        ClientMode.Static
      }

    lazy val timeout: Long = memcachedConfig.getLong("timeout")

    lazy val threads: Int =
      memcachedConfig.getInt("threads")

    lazy val enabled: Boolean =
      memcachedConfig.getBoolean("enabled")

    lazy val keySize: Int =
      memcachedConfig.getInt("keySize")

    lazy val localCacheEnabled: Boolean =
      memcachedConfig.getBoolean("localCache.enabled")

    lazy val localCacheSize: Int =
      memcachedConfig.getInt("localCache.size")

    object layerAttributes {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("layerAttributes.enabled")
    }

    object layerTile {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("layerTile.enabled")
    }

    object tool {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("tool.enabled")
    }

  }

  object geotrellis {
    private lazy val geotrellisConfig = config.getConfig("geotrellis")

    lazy val postgresAttributeStoreThreads: Int =
      geotrellisConfig.getInt("attributeStore.postgres.threads")

    lazy val postgresAttributeStoreTimeout: FiniteDuration =
      FiniteDuration(
        geotrellisConfig.getDuration("attributeStore.postgres.timeout").toNanos,
        TimeUnit.NANOSECONDS)
  }

  object awslambda {
    private val awsLambdaConfig = config.getConfig("awslambda")

    val environment = awsLambdaConfig.getString("environment")
  }

  object auth0 {
    private val auth0Config = config.getConfig("auth0")

    lazy val systemRefreshToken = auth0Config.getString("systemRefreshToken")
  }

}
