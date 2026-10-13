package com.example.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "configs",
    indices = [
        Index(value = ["normalizedHash"], unique = true),
        Index(value = ["qualityScore"]),
        Index(value = ["testStatus"]),
        Index(value = ["protocol"]),
        Index(value = ["countryCode"]),
        Index(value = ["isBypassNationalNet"]),
        Index(value = ["isHalfPriceDomestic"])
    ]
)
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedHash: String,
    val remarks: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val cipher: String? = null,
    val network: String = "tcp",
    val security: String = "none",
    val sni: String? = null,
    val path: String? = null,
    val flow: String? = null,
    val rawUri: String,
    val countryCode: String = "XX",
    val countryName: String = "نامشخص",
    val countryFlag: String = "🌐",
    val isBypassNationalNet: Boolean = false,
    val isHalfPriceDomestic: Boolean = false,
    val pingMs: Long = -1,
    val testStatus: String = "UNTESTED",
    val qualityScore: Int = 0,
    val lastTestTime: Long = 0,
    val lastSuccessTime: Long = 0,
    val lastErrorMessage: String? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "config_sources",
    indices = [Index(value = ["url"], unique = true)]
)
data class ConfigSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isUserCustom: Boolean = false,
    val lastFetchTime: Long = 0,
    val lastFetchStatus: String = "آماده دریافت",
    val configCount: Int = 0,
    val description: String = ""
)

@Entity(
    tableName = "config_source_cross_ref",
    primaryKeys = ["configId", "sourceId"],
    indices = [
        Index(value = ["configId"]),
        Index(value = ["sourceId"])
    ]
)
data class ConfigSourceCrossRef(
    val configId: Long,
    val sourceId: Long
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val networkType: String,
    val statusPersian: String,
    val isTunnelActive: Boolean,
    val detailsJson: String
)

@Entity(
    tableName = "proxies",
    indices = [Index(value = ["host", "port", "type"], unique = true)]
)
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val host: String,
    val port: Int,
    val type: String = "SOCKS5", // SOCKS5, HTTP, HTTPS, MTPROTO, SOCKS4
    val secretOrAuth: String? = null,
    val countryCode: String = "XX",
    val countryName: String = "نامشخص",
    val countryFlag: String = "🌐",
    val locationCity: String = "",
    val pingMs: Long = -1,
    val isHealthy: Boolean = false,
    val isDomestic: Boolean = false,
    val sourceName: String = "عمومی",
    val lastChecked: Long = 0
)
