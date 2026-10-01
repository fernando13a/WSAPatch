package com.ironmind.app.domain.model

/**
 * The unit the user sees and enters weights in. All weights are stored canonically in kilograms;
 * this only affects display and input, so switching never mutates the underlying data.
 */
enum class WeightUnit { KG, LB }
