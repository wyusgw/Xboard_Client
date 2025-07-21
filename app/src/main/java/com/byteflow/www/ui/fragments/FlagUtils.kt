package com.byteflow.www.ui.fragments

import com.byteflow.www.models.ClashProxy

object FlagUtils {
    fun countryCodeToFlagEmoji(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "\uD83C\uDFF3\uFE0F"
        val first = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
    fun guessCountryCode(node: ClashProxy?): String? {
        if (node == null) return null
        val name = node.name.uppercase()
        return when {
            name.contains("香港") || name.contains("HK") -> "HK"
            name.contains("台湾") || name.contains("TW") -> "TW"
            name.contains("新加坡") || name.contains("SG") -> "SG"
            name.contains("日本") || name.contains("JP") -> "JP"
            name.contains("美国") || name.contains("US") -> "US"
            name.contains("韩国") || name.contains("KR") -> "KR"
            name.contains("马来西亚") || name.contains("MY") -> "MY"
            name.contains("泰国") || name.contains("TH") -> "TH"
            name.contains("菲律宾") || name.contains("PH") -> "PH"
            name.contains("越南") || name.contains("VN") -> "VN"
            name.contains("印尼") || name.contains("ID") -> "ID"
            name.contains("英国") || name.contains("UK") -> "GB"
            name.contains("德国") || name.contains("DE") -> "DE"
            name.contains("法国") || name.contains("FR") -> "FR"
            name.contains("土耳其") || name.contains("TR") -> "TR"
            name.contains("巴西") || name.contains("BR") -> "BR"
            name.contains("阿根廷") || name.contains("AR") -> "AR"
            name.contains("硬编码") -> null
            else -> null
        }
    }
} 