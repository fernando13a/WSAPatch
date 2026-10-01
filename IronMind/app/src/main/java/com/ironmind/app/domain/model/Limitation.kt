package com.ironmind.app.domain.model

/**
 * A joint the athlete wants a generated routine to go easy on. Deliberately a short fixed list
 * rather than free text: the exclusion runs in Kotlin against exercise names, where a typo or an
 * unexpected phrasing would silently exclude nothing — and a small on-device model can't be relied
 * on to honour "no knee stuff" either.
 */
enum class Limitation {
    KNEE,
    SHOULDER,
    LOWER_BACK,
}
